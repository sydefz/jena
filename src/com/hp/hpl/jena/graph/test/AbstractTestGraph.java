/*
  (c) Copyright 2002, Hewlett-Packard Company, all rights reserved.
  [See end of file]
  $Id: AbstractTestGraph.java,v 1.11 2003-07-09 10:15:54 chris-dollin Exp $
*/

package com.hp.hpl.jena.graph.test;

import com.hp.hpl.jena.util.iterator.*;
import com.hp.hpl.jena.graph.*;
import com.hp.hpl.jena.graph.impl.*;
import com.hp.hpl.jena.graph.query.*;
import com.hp.hpl.jena.shared.*;

import java.util.*;

/**
    AbstractTestGraph provides a bunch of basic tests for something that
    purports to be a Graph. The abstract method getGraph must be overridden
    in subclasses to deliver a Graph of interest. 
    
 	@author kers
*/
public abstract class AbstractTestGraph extends GraphTestBase
    {
    public AbstractTestGraph( String name )
        { super( name ); };
        
    /**
        Returns a Graph to take part in the test. Must be overridden in
        a subclass.
    */
    public abstract Graph getGraph();
    
    /**
        This test case was generated by Ian and was caused by GraphMem
        not keeping up with changes to the find interface. 
    */
    public void testFindAndContains()
        {
        Graph g = getGraph();
        Node r = Node.create( "r" ), s = Node.create( "s" ), p = Node.create( "P" );
        g.add( Triple.create( r, p,  s ) );
        assertTrue( g.contains( r, p, Node.ANY ) );
        assertTrue( g.find( r, p, Node.ANY ).hasNext() );
        }
        
    public void testFindByFluidTriple()
        {
        Graph g = getGraph();
        g.add( triple( "x y z ") );
        assertTrue( g.find( triple( "?? y z" ) ).hasNext() );
        assertTrue( g.find( triple( "x ?? z" ) ).hasNext() );
        assertTrue( g.find( triple( "x y ??" ) ).hasNext() );
        }
        
    public void testAGraph()
        {
        String title = this.getClass().getName();
        Graph g = getGraph();
        graphAdd( g, "x R y; p S q; a T b" );
    /* */
        assertContainsAll( title + ": simple graph", g, "x R y; p S q; a T b" );
        assertEquals( title + ": size", g.size(), 3 );
        graphAdd( g, "spindizzies lift cities; Diracs communicate instantaneously" );
        assertEquals( title + ": size after adding", g.size(), 5 );
        g.delete( triple( "x R y" ) );
        g.delete( triple( "a T b" ) );
        assertEquals( title + ": size after deleting", g.size(), 3 );
        assertContainsAll( title + ": modified simple graph", g, "p S q; spindizzies lift cities; Diracs communicate instantaneously" );
        assertOmitsAll( title + ": modified simple graph", g, "x R y; a T b" );
    /* */ 
        ClosableIterator it = g.find( null, node("lift"), null );
        assertTrue( title + ": finds some triple(s)", it.hasNext() );
        assertEquals( title + ": finds a 'lift' triple", triple("spindizzies lift cities"), it.next() );
        assertFalse( title + ": finds exactly one triple", it.hasNext() );
        }

//    public void testStuff()
//        {
////        testAGraph( "StoreMem", new GraphMem() );
////        testAGraph( "StoreMemBySubject", new GraphMem() );
////        String [] empty = new String [] {};
////        Graph g = graphWith( "x R y; p S q; a T b" );
////    /* */
////        assertContainsAll( "simple graph", g, "x R y; p S q; a T b" );
////        graphAdd( g, "spindizzies lift cities; Diracs communicate instantaneously" );
////        g.delete( triple( "x R y" ) );
////        g.delete( triple( "a T b" ) );
////        assertContainsAll( "modified simple graph", g, "p S q; spindizzies lift cities; Diracs communicate instantaneously" );
////        assertOmitsAll( "modified simple graph", g, "x R y; a T b" );
//        }
                                      
    public void testReificationControl()
        {
        Graph g1 = graphWith( "x rdf:subject S" );
        Graph g2 = GraphBase.withReification( g1 );
        assertEquals( "should not hide reification triple", 1, g1.size() );
        assertEquals( "should not hide reification triple", 1, g2.size() );
        g2.add( triple( "x rdf:object O" ) );
        assertEquals( "", 1, g1.size() );
        assertEquals( "", 1, g2.size() );
        }

    /**
        Test that Graphs have transaction support methods, and that if they fail
        on some g they fail because they do not support the operation.
    */
    public void testHasTransactions()
        {
        Graph g = getGraph();
        TransactionHandler th = g.getTransactionHandler();
        th.transactionsSupported();
        try { th.begin(); } catch (UnsupportedOperationException x) {}
        try { th.abort(); } catch (UnsupportedOperationException x) {}
        try { th.commit(); } catch (UnsupportedOperationException x) {}
    /* */
        Command cmd = new Command() 
            { public Object execute() { return null; } };
        try { th.executeInTransaction( cmd ); } 
        catch (UnsupportedOperationException x) {}
        }

    static final Triple [] tripleArray = tripleArray( "S P O; A R B; X Q Y" );

    static final List tripleList = Arrays.asList( tripleArray( "i lt j; p equals q" ) );
        
    static final Triple [] setTriples = tripleArray
        ( "scissors cut paper; paper wraps stone; stone breaks scissors" );
        
    static final Set tripleSet = new HashSet( Arrays.asList( setTriples ) );
                
    public void testBulkUpdate()
        {
        Graph g = getGraph();
        BulkUpdateHandler bu = g.getBulkUpdateHandler();
        Graph items = graphWith( "pigs might fly; dead can dance" );
        int initialSize = g.size();
    /* */
        bu.add( tripleArray );
        testContains( g, tripleArray );
    /* */
        bu.add( tripleList );
        testContains( g, tripleList );
        testContains( g, tripleArray );
    /* */
        bu.add( tripleSet.iterator() );
        testContains( g, tripleSet.iterator() );
        testContains( g, tripleList );
        testContains( g, tripleArray );
    /* */
        bu.add( items );
        testContains( g, items );
        testContains( g, tripleSet.iterator() );
        testContains( g, tripleArray );
        testContains( g, tripleList );
    /* */
        bu.delete( tripleArray );
        testOmits( g, tripleArray );
        testContains( g, tripleList );
        testContains( g, tripleSet.iterator() );
        testContains( g, items );
    /* */
        bu.delete( tripleSet.iterator() );
        testOmits( g, tripleSet.iterator() );
        testOmits( g, tripleArray );
        testContains( g, tripleList );
        testContains( g, items );
    /* */
        bu.delete( items );
        testOmits( g, tripleSet.iterator() );
        testOmits( g, tripleArray );
        testContains( g, tripleList );
        testOmits( g, items ); 
    /* */
        bu.delete( tripleList );
        assertEquals( "graph has original size", initialSize, g.size() );
        }
                    
    public void testHasCapabilities()
        {
        Graph g = getGraph();
        Capabilities c = g.getCapabilities();
        }
        
    public void testFind()
        {
        Graph g = getGraph();
        graphAdd( g, "S P O" );
        assertTrue( g.find( Node.ANY, null, null ).hasNext() );
        assertTrue( g.find( null, Node.ANY, null ).hasNext() );
        }
        
    /**
        test the isEmpty component of a query handler.
    */
    public void testIsEmpty()
        {
        Graph g = getGraph();
        QueryHandler q = g.queryHandler();
        assertTrue( q.isEmpty() );
        g.add( Triple.create( "S P O" ) );
        assertFalse( q.isEmpty() );
        g.add( Triple.create( "A B C" ) );
        assertFalse( q.isEmpty() );
        g.add( Triple.create( "S P O" ) );
        assertFalse( q.isEmpty() );
        g.delete( Triple.create( "S P O" ) );
        assertFalse( q.isEmpty() );
        g.delete( Triple.create( "A B C" ) );
        assertTrue( q.isEmpty() );
        }
        
    
    static class HistoryListener implements GraphListener
        {
        List history = new ArrayList();
        
        public void notifyAdd( Triple t )
            { history.add( "add" ); history.add( t ); }
            
        public void notifyDelete( Triple t )
            { history.add( "delete" ); history.add( t ); }
            
        public void clear()
            { history.clear(); }
            
        public boolean has( Object [] things )
            { return history.equals( Arrays.asList( things ) ); }
            
        }
        
    public void testEventRegister()
        {
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        assertSame( gem, gem.register( new HistoryListener() ) );
        }
        
    public void testEventUnregister()
        {
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        GraphListener L = new HistoryListener();
        gem.register( L );
        gem.unregister( L );
        }
        
    public void testAddTriple()
        {
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        HistoryListener L = new HistoryListener();
        Triple SPO = Triple.create( "S P O" );
        gem.register( L );
        g.add( SPO );
        assertTrue( L.has( new Object[] {"add", SPO} ) );
        }
        
    public void testDeleteTriple()
        {        
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        HistoryListener L = new HistoryListener();
        Triple SPO = Triple.create( "S P O" );
        gem.register( L );
        g.add( SPO );
        L.clear();
        g.delete( SPO );
        assertTrue( L.has( new Object[] { "delete", SPO} ) );
        }
        
    public void testTwoListeners()
        {
        Triple SPO = Triple.create( "S P O" );
        HistoryListener L1 = new HistoryListener();
        HistoryListener L2 = new HistoryListener();
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        gem.register( L1 ).register( L2 );
        g.add( SPO );
        assertTrue( L2.has( new Object[] {"add", SPO} ) );
        assertTrue( L1.has( new Object[] {"add", SPO} ) );
        }
        
    public void testUnregisterWorks()
        {
        Triple SPO = Triple.create( "S P O" );
        HistoryListener L = new HistoryListener();
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        gem.register( L ).unregister( L );
        g.add( SPO );
        assertTrue( L.has( new Object[] {} ) );
        }
    }


/*
    (c) Copyright Hewlett-Packard Company 2003
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions
    are met:

    1. Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above copyright
       notice, this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.

    3. The name of the author may not be used to endorse or promote products
       derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
    IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
    OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
    IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
    INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
    NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
    DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
    THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
    THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/