/*
 * (c) Copyright 2011 Epimorphics Ltd.
 * All rights reserved.
 * [See end of file]
 */

package com.hp.hpl.jena.tdb.transaction;

import static com.hp.hpl.jena.tdb.ReadWrite.READ ;
import static com.hp.hpl.jena.tdb.transaction.TransactionManager.TxnPoint.* ;
import static com.hp.hpl.jena.tdb.ReadWrite.WRITE ;
import static com.hp.hpl.jena.tdb.sys.SystemTDB.syslog ;
import static java.lang.String.format ;

import java.util.ArrayList ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Set ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.LinkedBlockingDeque ;
import java.util.concurrent.Semaphore ;

import org.openjena.atlas.lib.Pair ;
import org.openjena.atlas.logging.Log ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.tdb.DatasetGraphTxn ;
import com.hp.hpl.jena.tdb.ReadWrite ;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;

public class TransactionManager
{
    // TODO Don't keep counter, keep lists.
    // TODO Useful logging.
    
    private static Logger log = LoggerFactory.getLogger(TransactionManager.class) ;
    
    private Set<Transaction> activeTransactions = new HashSet<Transaction>() ;
    // Setting this true cause the TransactionManager to keep lists of transactions
    // and what has happened.  Nothing is thrown away, but eventually it will
    // consume too much memory.
    
    // Make a feature of the transaction.
    // Chnage to one list of (txn, state change.).
    private boolean recordHistory = false ;
    
    enum TxnPoint { BEGIN, COMMIT, ABORT, CLOSE, QUEUE, UNQUEUE }
    private List<Pair<Transaction, TxnPoint>> transactionStateTransition ;
    
    private void record(Transaction txn, TxnPoint state)
    {
        if ( ! recordHistory ) return ;
        initRecordingState() ;
        transactionStateTransition.add(new Pair<Transaction, TxnPoint>(txn, state)) ;
    }
    
    // Transactions that have commited (and the journal is written) but haven't
    // writted back to the main database. 
    
    List<Transaction> commitedAwaitingFlush = new ArrayList<Transaction>() ;    
    
    static long transactionId = 1 ;
    
    int activeReaders = 0 ; 
    int activeWriters = 0 ;  // 0 or 1
    
    // Misc stats
    int finishedReaders = 0 ;
    int committedWriters = 0 ;
    int abortedWriters = 0 ;
    
    // Ensure single writer.
    private Semaphore writersWaiting = new Semaphore(1, true) ;
    private BlockingQueue<Transaction> queue = new LinkedBlockingDeque<Transaction>() ;

    private Thread committerThread ;

    private DatasetGraphTDB baseDataset ;
    private Journal journal ;
    
    // TODO Tidy up - more to end-of-file.
    
    /* Various policies:
     * + MRSW : writer locks to write back; blocks until let trhough.  Every reader takes an read lock.
     * + Writers write if free, else queue for a reader or writer to clearup.
     * + Async: there is a thread whose job it is to flush tot he base dataset (with an MRSW lock). 
     */
    
    // Add queue unqueue?
    /*
     * The order of calls is: 
     * 1/ transactionStarts
     * 2/ readerStarts or writerStarts
     * 3/ readerFinishes or writerCommits or writerAborts
     * 4/ transactionFinishes
     * 5/ transactionCloses
     */
    
    private interface TSM
    {
        // Quert unqueue?
        void transactionStarts(Transaction txn) ;
        void transactionFinishes(Transaction txn) ;
        void transactionCloses(Transaction txn) ;
        void readerStarts(Transaction txn) ;
        void readerFinishes(Transaction txn) ;
        void writerStarts(Transaction txn) ;
        void writerCommits(Transaction txn) ;
        void writerAborts(Transaction txn) ;
    }
    
    class TSM_Base implements TSM
    {
        @Override public void transactionStarts(Transaction txn)    {}
        @Override public void transactionFinishes(Transaction txn)  {}
        @Override public void transactionCloses(Transaction txn)    {}
        @Override public void readerStarts(Transaction txn)         {}
        @Override public void readerFinishes(Transaction txn)       {}
        @Override public void writerStarts(Transaction txn)         {}
        @Override public void writerCommits(Transaction txn)        {}
        @Override public void writerAborts(Transaction txn)         {}
    }
    
    
    class TSM_Stats implements TSM
    {
        @Override
        public void transactionStarts(Transaction txn)
        { 
            activeTransactions.add(txn) ;
        }
        
        @Override
        public void transactionFinishes(Transaction txn)
        { 
            activeTransactions.remove(txn) ;
        }
        
        @Override
        public void transactionCloses(Transaction txn)      { }
        @Override
        public void readerStarts(Transaction txn)           { activeReaders++ ; }
        @Override
        public void readerFinishes(Transaction txn)         { activeReaders-- ; finishedReaders++ ; }
        @Override
        public void writerStarts(Transaction txn)           { activeWriters++ ; }
        @Override
        public void writerCommits(Transaction txn)          { activeWriters-- ; committedWriters++ ; }
        @Override
        public void writerAborts(Transaction txn)           { activeWriters-- ; abortedWriters++ ; }
    }
    
    class TSM_Record extends TSM_Base
    {
        // Later - record on one list the state transition.
        @Override
        public void transactionStarts(Transaction txn)      { record(txn, BEGIN) ; }
        @Override
        public void transactionFinishes(Transaction txn)    { record(txn, CLOSE) ; }
    }
    
    private TSM[] actions = new TSM[] { 
        new TSM_Stats() ,
        (recordHistory ? new TSM_Record() : null ) ,
        // Writer write back policy.
    } ;
    
    public TransactionManager(DatasetGraphTDB dsg)
    {
        this.baseDataset = dsg ; 
        this.journal = Journal.create(dsg.getLocation()) ;
        // LATER
//        Committer c = new Committer() ;
//        this.committerThread = new Thread(c) ;
//        committerThread.setDaemon(true) ;
//        committerThread.start() ;
    }

    private Transaction createTransaction(DatasetGraphTDB dsg, ReadWrite mode, String label)
    {
        Transaction txn = new Transaction(dsg, mode, transactionId++, label, this) ;
        return txn ;
    }

    public DatasetGraphTxn begin(ReadWrite mode)
    {
        return begin(mode, null) ;
    }
    
    public DatasetGraphTxn begin(ReadWrite mode, String label)
    {
        // Not synchronized (else blocking on semaphore will never wake up
        // because Semaphore.release is inside synchronized.
        // Allow only one active writer. 
        if ( mode == ReadWrite.WRITE )
        {
            // Writers take a WRITE permit from the semaphore to ensure there
            // is at most one active writer, else the attempt to start the
            // transaction blocks.
            try { writersWaiting.acquire() ; }
            catch (InterruptedException e)
            { 
                log.error(label, e) ;
                throw new TDBTransactionException(e) ;
            }
        }
        // entry synchronized part
        return begin$(mode, label) ;
    }
        
    synchronized
    private DatasetGraphTxn begin$(ReadWrite mode, String label)
    {
//        // Subs transactions are a new view - commit is only commit to parent transaction.  
//        if ( dsg instanceof DatasetGraphTxn )
//        {
//            throw new TDBException("Already in transactional DatasetGraph") ;
//            // Either:
//            //   error -> implies nested
//            //   create new transaction 
//        }
        
        if ( mode == WRITE && activeWriters > 0 )    // Guard
            throw new TDBTransactionException("Existing active write transaction") ;

        // Even flush queue here.
        
        DatasetGraphTDB dsg = baseDataset ;
        // *** But, if there are pending, committed transactions, use latest.
        if ( ! commitedAwaitingFlush.isEmpty() )
            dsg = commitedAwaitingFlush.get(commitedAwaitingFlush.size()-1).getActiveDataset() ;
        
        Transaction txn = createTransaction(dsg, mode, label) ;
        DatasetGraphTxn dsgTxn = (DatasetGraphTxn)new DatasetBuilderTxn(this).build(txn, mode, dsg) ;
        txn.setActiveDataset(dsgTxn) ;

        // TODO Match with other oepration states
        // Notify everyone we're starting.
        for ( Transactional component : dsgTxn.getTransaction().components() )
            component.begin(dsgTxn.getTransaction()) ;

        noteStartTxn(txn) ;
        log("begin",txn) ;
        return dsgTxn ;
    }

    /* Signal a transaction has commited.  The journal has a commit record
     * and a sync to disk. The code here manages the inter-transaction stage
     *  of deciding how to play the changes back to the base data. 
     */ 
    synchronized
    public void notifyCommit(Transaction transaction)
    {
        log("commit", transaction) ;

        // Transaction has done the commitPrepare - can we enact it?
        
        if ( ! activeTransactions.contains(transaction) )
            SystemTDB.errlog.warn("Transaction not active: "+transaction.getTxnId()) ;
        
        noteTxnCommit(transaction) ;
        
        switch ( transaction.getMode() )
        {
            case READ: 
                processDelayedReplayQueue(transaction) ;
                break ;
            case WRITE:
                if ( activeReaders == 0 )
                {
                    // Can commit immediately.
                    // messey - combine with state machine. 
                    processDelayedReplayQueue(transaction) ;
                    enactTransaction(transaction) ;
                    JournalControl.replay(transaction) ;
                }
                else
                {
                    // Can't make permanent at the moment.
                    commitedAwaitingFlush.add(transaction) ;
                    log("Queue commit flush", transaction) ; 
                    queue.add(transaction) ;
                }
                writersWaiting.release() ;
        }
    }

    synchronized
    public void notifyAbort(Transaction transaction)
    {
        log("abort", transaction) ;
        // Transaction has done the abort on all the transactional elements.
        if ( ! activeTransactions.contains(transaction) )
            SystemTDB.errlog.warn("Transaction not active: "+transaction.getTxnId()) ;
        
        noteTxnAbort(transaction) ;
        switch ( transaction.getMode() )
        {
            case READ:
                processDelayedReplayQueue(transaction) ;
                break ;
            case WRITE:
                // Journal cleaned in Transaction.abort.
                abortedWriters ++ ;
                // Still try the queue.
                processDelayedReplayQueue(transaction) ;
                writersWaiting.release() ;
        }
    }
    
    /** The stage in a commit after commiting - make the changes permanent in the base data */ 
    private void enactTransaction(Transaction transaction)
    {
        // Flush the queue first.  Happens in Transaction.commit
        // Really, really do it!
        for ( Transactional x : transaction.components() )
        {
            x.commitEnact(transaction) ;
            x.commitClearup(transaction) ;
        }
    }

    private void processDelayedReplayQueue(Transaction txn)
    {
        // Sync'ed by notifyCommit.
        // If we knew which version of the DB each was looking at, we could reduce more often here.
        // [TxTDB:TODO]
        if ( activeReaders != 0 || activeWriters != 0 )
        {
            if ( queue.size() > 0 )
                if ( log() ) log(format("Pending transactions: R=%d / W=%d", activeReaders, activeWriters), txn) ;
            return ;
        }
//        if ( queue.size() > 1 )
//            System.out.println("\nQuery length: "+queue.size()) ;
        while ( queue.size() > 0 )
        {
            // Currently, replay is replay everything
            // so looping on a per-transaction basis is
            // pointless but harmless.  
            
            try {
                Transaction txn2 = queue.take() ;
                if ( txn2.getMode() == READ )
                    continue ;
                log("Flush delayed commit", txn2) ;
                // This takes a Write lock on the  DSG - this is where it blocks.
                // **** REPLAYS WHOLE JOURNAL
                // **** Related NodeFileTrans: writes at "prepare" 
                enactTransaction(txn2) ;
                commitedAwaitingFlush.remove(txn2) ;
                
                // Drain queue - in fact, everything is done by one "enactTransaction"
                
            } catch (InterruptedException ex)
            { Log.fatal(this, "Interruped!", ex) ; }
        }
        // Whole journal to base database
        JournalControl.replay(txn.getJournal(), baseDataset) ;
    }

    synchronized
    public void notifyClose(Transaction txn)
    {
        log("close", txn) ;
        noteTxnClose(txn) ;
        
        if ( txn.getState() == TxnState.ACTIVE )
        {
            String x = txn.getBaseDataset().getLocation().getDirectoryPath() ;
            syslog.warn("close: Transaction not commited or aborted: Transaction: "+txn.getTxnId()+" @ "+x) ;
            txn.abort() ;
            return ;
        }
    }
        
    // TODO Collapse these.
    private void noteStartTxn(Transaction transaction)
    {
        switch (transaction.getMode())
        {
            case READ : readerStarts(transaction) ; break ;
            case WRITE : writerStarts(transaction) ; break ;
        }
        transactionStarts(transaction) ;
    }

    private void noteTxnCommit(Transaction transaction)
    {
        switch (transaction.getMode())
        {
            case READ : readerFinishes(transaction) ; break ;
            case WRITE : writerCommits(transaction) ; break ;
        }
        transactionFinishes(transaction) ;
    }
    
    private void noteTxnAbort(Transaction transaction)
    {
        switch (transaction.getMode())
        {
            case READ : readerFinishes(transaction) ; break ;
            case WRITE : writerAborts(transaction) ; break ;
        }
        transactionFinishes(transaction) ;
    }
    
    private void noteTxnClose(Transaction transaction)
    {
        transactionCloses(transaction) ;
    }
    
    // ---- Recording
    
    /** Get recording state */
    public boolean recording()              { return recordHistory ; }
    /** Set recording on or off */
    public void recording(boolean flag)
    {
        recordHistory = flag ;
        if ( recordHistory )
            initRecordingState() ;
    }
    /** Clear all recording state - does not clear stats */ 
    public void clearRecordingState()
    {
        initRecordingState() ;
        transactionStateTransition.clear() ;
    }
    
    private void initRecordingState()
    {
        if ( transactionStateTransition == null )
            transactionStateTransition = new ArrayList<Pair<Transaction, TxnPoint>>() ;
    }
    
//    public List<Transaction> getBeginTransactionRecord() { return transactionBegin ; }
//    public List<Transaction> getEndTransactionRecord() { return transactionEnd ; }

    // ---- Recording

    
    public Journal getJournal()
    {
        return journal ;
    }

    private boolean log()
    {
        return syslog.isDebugEnabled() || log.isDebugEnabled() ;
    }
    
    private void log(String msg, Transaction txn)
    {
        if ( ! log() )
            return ;
        if ( syslog.isDebugEnabled() )
            syslog.debug(txn.getLabel()+": "+msg) ;
        else
            log.debug(txn.getLabel()+": "+msg) ;
    }

    synchronized
    public SysTxnState state()
    { 
        return new SysTxnState(this) ;
    }
    
    // LATER.
    class Committer implements Runnable
    {
        @Override
        public void run()
        {
            for(;;)
            {
                // Wait until the reader count goes to zero.
                
                // This wakes up for every transation but maybe 
                // able to play several transactions at once (later).
                try {
                    Transaction txn = queue.take() ;
                    // This takes a Write lock on the  DSG - this is where it blocks.
                    JournalControl.replay(txn) ;
                    synchronized(TransactionManager.this)
                    {
                        commitedAwaitingFlush.remove(txn) ;
                    }
                } catch (InterruptedException ex)
                { Log.fatal(this, "Interruped!", ex) ; }
            }
        }
        
    }
    
    private void transactionStarts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.transactionStarts(txn) ;
    }

    private void transactionFinishes(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.transactionFinishes(txn) ;
    }
    
    private void transactionCloses(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.transactionCloses(txn) ;
    }
    
    private void readerStarts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.readerStarts(txn) ;
    }
    
    private void readerFinishes(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.readerFinishes(txn) ;
    }

    private void writerStarts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.writerStarts(txn) ;
    }

    private void writerCommits(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.writerCommits(txn) ;
    }

    private void writerAborts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.writerAborts(txn) ;
    }
}

/*
 * (c) Copyright 2011 Epimorphics Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */