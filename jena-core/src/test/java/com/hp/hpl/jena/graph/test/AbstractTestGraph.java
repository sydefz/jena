/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.graph.test;

import java.io.FileInputStream ;
import java.io.FileNotFoundException ;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.* ;

import com.hp.hpl.jena.graph.* ;
import com.hp.hpl.jena.mem.TrackingTripleIterator ;
import com.hp.hpl.jena.rdf.model.Model ;
import com.hp.hpl.jena.rdf.model.ModelFactory ;
import com.hp.hpl.jena.rdf.model.impl.ReifierStd ;
import com.hp.hpl.jena.shared.Command ;
import com.hp.hpl.jena.shared.JenaException ;
import com.hp.hpl.jena.util.CollectionFactory ;
import com.hp.hpl.jena.util.iterator.ClosableIterator ;
import com.hp.hpl.jena.util.iterator.ExtendedIterator ;

/**
    AbstractTestGraph provides a bunch of basic tests for something that
    purports to be a Graph. The abstract method getGraph must be overridden
    in subclasses to deliver a Graph of interest. 
 */
public abstract class AbstractTestGraph extends GraphTestBase
{
    public AbstractTestGraph( String name )
    { super( name ); }

    /**
        Returns a Graph to take part in the test. Must be overridden in
        a subclass.
     */
    public abstract Graph getGraph();

     //public Graph getGraph() { return Factory.createGraphMem(); }

    public Graph getGraphWith( String facts )
    {
        Graph g = getGraph();
        graphAdd( g, facts );
        return g;    
    }

    public void testCloseSetsIsClosed()
    {
        Graph g = getGraph();
        assertFalse( "unclosed Graph shouild not be isClosed()", g.isClosed() );
        g.close();
        assertTrue( "closed Graph should be isClosed()", g.isClosed() );
    }

    /**
        This test case was generated by Ian and was caused by GraphMem
        not keeping up with changes to the find interface. 
     */
    public void testFindAndContains()
    {
        Graph g = getGraph();
        Node r = NodeCreateUtils.create( "r" ), s = NodeCreateUtils.create( "s" ), p = NodeCreateUtils.create( "P" );
        g.add( Triple.create( r, p, s ) );
        assertTrue( g.contains( r, p, Node.ANY ) );
        assertEquals( 1, g.find( r, p, Node.ANY ).toList().size() );
    }

    public void testRepeatedSubjectDoesNotConceal()
    {
        Graph g = getGraphWith( "s P o; s Q r" );
        assertTrue( g.contains( triple( "s P o" ) ) );
        assertTrue( g.contains( triple( "s Q r" ) ) );
        assertTrue( g.contains( triple( "?? P o" ) ) );
        assertTrue( g.contains( triple( "?? Q r" ) ) );
        assertTrue( g.contains( triple( "?? P ??" ) ) );
        assertTrue( g.contains( triple( "?? Q ??" ) ) );
    }

    public void testFindByFluidTriple()
    {
        Graph g = getGraphWith( "x y z " );
        Set<Triple> expect = tripleSet( "x y z" );
        assertEquals( expect, g.find( triple( "?? y z" ) ).toSet() );
        assertEquals( expect, g.find( triple( "x ?? z" ) ).toSet() );
        assertEquals( expect, g.find( triple( "x y ??" ) ).toSet() );
    }

    public void testContainsConcrete()
    {
        Graph g = getGraphWith( "s P o; _x _R _y; x S 0" );
        assertTrue( g.contains( triple( "s P o" ) ) );
        assertTrue( g.contains( triple( "_x _R _y" ) ) );
        assertTrue( g.contains( triple( "x S 0" ) ) );
        /* */
        assertFalse( g.contains( triple( "s P Oh" ) ) );
        assertFalse( g.contains( triple( "S P O" ) ) );
        assertFalse( g.contains( triple( "s p o" ) ) );
        assertFalse( g.contains( triple( "_x _r _y" ) ) );
        assertFalse( g.contains( triple( "x S 1" ) ) );
    }

    public void testContainsFluid()
    {
        Graph g = getGraphWith( "x R y; a P b" );
        assertTrue( g.contains( triple( "?? R y" ) ) );
        assertTrue( g.contains( triple( "x ?? y" ) ) );
        assertTrue( g.contains( triple( "x R ??" ) ) );
        assertTrue( g.contains( triple( "?? P b" ) ) );
        assertTrue( g.contains( triple( "a ?? b" ) ) );
        assertTrue( g.contains( triple( "a P ??" ) ) );
        assertTrue( g.contains( triple( "?? R y" ) ) );
        /* */
        assertFalse( g.contains( triple( "?? R b" ) ) );
        assertFalse( g.contains( triple( "a ?? y" ) ) );
        assertFalse( g.contains( triple( "x P ??" ) ) );
        assertFalse( g.contains( triple( "?? R x" ) ) );
        assertFalse( g.contains( triple( "x ?? R" ) ) );
        assertFalse( g.contains( triple( "a S ??" ) ) );
    }

    /**
        Check that contains respects by-value semantics.
     */
    public void testContainsByValue()
    {
        if (getGraph().getCapabilities().handlesLiteralTyping())
        {
            Graph g1 = getGraphWith( "x P '1'xsd:integer" );
            assertTrue( g1.contains( triple( "x P '01'xsd:int" ) ) );
            //
            Graph g2 = getGraphWith( "x P '1'xsd:int" );
            assertTrue( g2.contains( triple( "x P '1'xsd:integer" ) ) );
            //
            Graph g3 = getGraphWith( "x P '123'xsd:string" );
            assertTrue( g3.contains( triple( "x P '123'" ) ) );
        }
    }

    public void testMatchLanguagedLiteralCaseInsensitive()
    {
        Graph m = graphWith( "a p 'chat'en" );
        if (m.getCapabilities().handlesLiteralTyping())
        {
            Node chaten = node( "'chat'en" ), chatEN = node( "'chat'EN" );
            assertDiffer( chaten, chatEN );
            assertTrue( chaten.sameValueAs( chatEN ) );
            assertEquals( chaten.getIndexingValue(), chatEN.getIndexingValue() );
            assertEquals( 1, m.find( Node.ANY, Node.ANY, chaten ).toList().size() );
            assertEquals( 1, m.find( Node.ANY, Node.ANY, chatEN ).toList().size() );
        }
    }

    public void testMatchBothLanguagedLiteralsCaseInsensitive()
    {
        Graph m = graphWith( "a p 'chat'en; a p 'chat'EN" );
        if (m.getCapabilities().handlesLiteralTyping())
        {
            Node chaten = node( "'chat'en" ), chatEN = node( "'chat'EN" );
            assertDiffer( chaten, chatEN );
            assertTrue( chaten.sameValueAs( chatEN ) );
            assertEquals( chaten.getIndexingValue(), chatEN.getIndexingValue() );
            assertEquals( 2, m.find( Node.ANY, Node.ANY, chaten ).toList().size() );
            assertEquals( 2, m.find( Node.ANY, Node.ANY, chatEN ).toList().size() );
        }
    }

    public void testNoMatchAgainstUnlanguagesLiteral()
    {
        Graph m = graphWith( "a p 'chat'en; a p 'chat'" );
        if (m.getCapabilities().handlesLiteralTyping())
        {
            Node chaten = node( "'chat'en" ), chatEN = node( "'chat'EN" );
            assertDiffer( chaten, chatEN );
            assertTrue( chaten.sameValueAs( chatEN ) );
            assertEquals( chaten.getIndexingValue(), chatEN.getIndexingValue() );
            assertEquals( 1, m.find( Node.ANY, Node.ANY, chaten ).toList().size() );
            assertEquals( 1, m.find( Node.ANY, Node.ANY, chatEN ).toList().size() );        
        }
    }

    /**
        test  isEmpty - moved from the QueryHandler code.
     */
    public void testIsEmpty()
    {
        Graph g = getGraph();
        if (canBeEmpty( g ))
        {
            assertTrue( g.isEmpty() );
            g.add( NodeCreateUtils.createTriple( "S P O" ) );
            assertFalse( g.isEmpty() );
            g.add( NodeCreateUtils.createTriple( "A B C" ) );
            assertFalse( g.isEmpty() );
            g.add( NodeCreateUtils.createTriple( "S P O" ) );
            assertFalse( g.isEmpty() );
            g.delete( NodeCreateUtils.createTriple( "S P O" ) );
            assertFalse( g.isEmpty() );
            g.delete( NodeCreateUtils.createTriple( "A B C" ) );
            assertTrue( g.isEmpty() );
        }
    }


    public void testAGraph()
    {
        String title = this.getClass().getName();
        Graph g = getGraph();
        int baseSize = g.size();
        graphAdd( g, "x R y; p S q; a T b" );
        /* */
        assertContainsAll( title + ": simple graph", g, "x R y; p S q; a T b" );
        assertEquals( title + ": size", baseSize + 3, g.size() );
        graphAdd( g, "spindizzies lift cities; Diracs communicate instantaneously" );
        assertEquals( title + ": size after adding", baseSize + 5, g.size() );
        g.delete( triple( "x R y" ) );
        g.delete( triple( "a T b" ) );
        assertEquals( title + ": size after deleting", baseSize + 3, g.size() );
        assertContainsAll( title + ": modified simple graph", g, "p S q; spindizzies lift cities; Diracs communicate instantaneously" );
        assertOmitsAll( title + ": modified simple graph", g, "x R y; a T b" );
        /* */ 
        ClosableIterator<Triple> it = g.find( Node.ANY, node("lift"), Node.ANY );
        assertTrue( title + ": finds some triple(s)", it.hasNext() );
        assertEquals( title + ": finds a 'lift' triple", triple("spindizzies lift cities"), it.next() );
        assertFalse( title + ": finds exactly one triple", it.hasNext() );
        it.close();
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
        try { th.begin(); th.commit(); } catch (UnsupportedOperationException x) {}
        /* */
        Command cmd = new Command() 
        { @Override
            public Object execute() { return null; } };
            try { th.executeInTransaction( cmd ); } 
            catch (UnsupportedOperationException x) {}
    }

    public void testExecuteInTransactionCatchesThrowable()
    {Graph g = getGraph();
    TransactionHandler th = g.getTransactionHandler();
    if (th.transactionsSupported())
    {
        Command cmd = new Command() 
        { @Override
            public Object execute() throws Error { throw new Error(); } };
            try { th.executeInTransaction( cmd ); } 
            catch (JenaException x) {}
    }
    }

    static final Triple [] tripleArray = tripleArray( "S P O; A R B; X Q Y" );

    static final List<Triple> tripleList = Arrays.asList( tripleArray( "i lt j; p equals q" ) );

    static final Triple [] setTriples = tripleArray
        ( "scissors cut paper; paper wraps stone; stone breaks scissors" );

    static final Set<Triple> tripleSet = CollectionFactory.createHashedSet( Arrays.asList( setTriples ) );

    public void testBulkUpdate()
    {
        Graph g = getGraph();
        Graph items = graphWith( "pigs might fly; dead can dance" );
        int initialSize = g.size();
        /* */
        GraphUtil.add( g,  tripleArray );
        testContains( g, tripleArray );
        testOmits( g, tripleList );
        /* */
        GraphUtil.add( g,  tripleList );
        testContains( g, tripleList );
        testContains( g, tripleArray );
        /* */
        GraphUtil.add( g, tripleSet.iterator() );
        testContains( g, tripleSet.iterator() );
        testContains( g, tripleList );
        testContains( g, tripleArray );
        /* */
        GraphUtil.addInto( g, items );
        testContains( g, items );
        testContains( g, tripleSet.iterator() );
        testContains( g, tripleArray );
        testContains( g, tripleList );
        /* */
        GraphUtil.delete( g, tripleArray );
        testOmits( g, tripleArray );
        testContains( g, tripleList );
        testContains( g, tripleSet.iterator() );
        testContains( g, items );
        /* */
        GraphUtil.delete( g, tripleSet.iterator() );
        testOmits( g, tripleSet.iterator() );
        testOmits( g, tripleArray );
        testContains( g, tripleList );
        testContains( g, items );
        /* */
        GraphUtil.deleteFrom( g, items );
        testOmits( g, tripleSet.iterator() );
        testOmits( g, tripleArray );
        testContains( g, tripleList );
        testOmits( g, items ); 
        /* */
        GraphUtil.delete( g, tripleList );
        assertEquals( "graph has original size", initialSize, g.size() );
    }

    public void testAddWithReificationPreamble()
    {
        Graph g = getGraph();
        xSPO( g );
        assertFalse( g.isEmpty() );    
    }

    protected void xSPOyXYZ( Graph g)
    {
        xSPO( g );
        ReifierStd.reifyAs( g, NodeCreateUtils.create( "y" ), NodeCreateUtils.createTriple( "X Y Z" ) );       
    }

    protected void aABC( Graph g )
    { ReifierStd.reifyAs( g , NodeCreateUtils.create( "a" ), NodeCreateUtils.createTriple( "A B C" ) ); }

    protected void xSPO( Graph g )
    { ReifierStd.reifyAs( g , NodeCreateUtils.create( "x" ), NodeCreateUtils.createTriple( "S P O" ) ); }

    public void testRemove()
    { 
        testRemove( "?? ?? ??", "?? ?? ??" );
        testRemove( "S ?? ??", "S ?? ??" );
        testRemove( "S ?? ??", "?? P ??" );
        testRemove( "S ?? ??", "?? ?? O" );
        testRemove( "?? P ??", "S ?? ??" );
        testRemove( "?? P ??", "?? P ??" );
        testRemove( "?? P ??", "?? ?? O" );
        testRemove( "?? ?? O", "S ?? ??" );
        testRemove( "?? ?? O", "?? P ??" );
        testRemove( "?? ?? O", "?? ?? O" );
    }

    public void testRemove( String findRemove, String findCheck )
    {
        Graph g = getGraphWith( "S P O" );
        ExtendedIterator<Triple> it = g.find( NodeCreateUtils.createTriple( findRemove ) );
        try 
        {
            it.next(); it.remove(); it.close();
            assertEquals( "remove with " + findRemove + ":", 0, g.size() );
            assertFalse( g.contains( NodeCreateUtils.createTriple( findCheck ) ) );
        }
        catch (UnsupportedOperationException e) {
            it.close();
            assertFalse( g.getCapabilities().iteratorRemoveAllowed() ); 
        }
        it.close();
    }

    public void testHasCapabilities()
    {
        Graph g = getGraph();
        Capabilities c = g.getCapabilities();
        boolean sa = c.sizeAccurate();
        boolean aaSome = c.addAllowed();
        boolean aaAll = c.addAllowed( true );
        boolean daSome = c.deleteAllowed();
        boolean daAll = c.deleteAllowed( true );
        boolean cbe = c.canBeEmpty();
    }

    public void testFind()
    {
        Graph g = getGraph();
        graphAdd( g, "S P O" );
        assertDiffer( new HashSet<Triple>(), g.find( Node.ANY, Node.ANY, Node.ANY ).toSet() );
        assertDiffer( new HashSet<Triple>(), g.find( Triple.ANY ).toSet() );
    }

    protected boolean canBeEmpty( Graph g )
    { return g.isEmpty(); }

    public void testEventRegister()
    {
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        assertSame( gem, gem.register( new RecordingListener() ) );
    }

    /**
        Test that we can safely unregister a listener that isn't registered.
     */
    public void testEventUnregister()
    {
        getGraph().getEventManager().unregister( L );
    }

    /**
        Handy triple for test purposes.
     */
    protected Triple SPO = NodeCreateUtils.createTriple( "S P O" );
    protected RecordingListener L = new RecordingListener();

    /**
        Utility: get a graph, register L with its manager, return the graph.
     */
    protected Graph getAndRegister( GraphListener gl )
    {
        Graph g = getGraph();
        g.getEventManager().register( gl );
        return g;
    }

    public void testAddTriple()
    {
        Graph g = getAndRegister( L );
        g.add( SPO );
        L.assertHas( new Object[] {"add", g, SPO} );
    }

    public void testDeleteTriple()
    {        
        Graph g = getAndRegister( L );
        g.delete( SPO );
        L.assertHas( new Object[] { "delete", g, SPO} );
    }

    public void testListSubjects()
    {
        Set<Node> emptySubjects = listSubjects( getGraphWith( "" ) );
        Graph g = getGraphWith( "x P y; y Q z" );
        assertEquals( nodeSet( "x y" ), remove( listSubjects( g ), emptySubjects ) );
        g.delete( triple( "x P y" ) );
        assertEquals( nodeSet( "y" ), remove( listSubjects( g ), emptySubjects ) );
    }

    protected Set<Node> listSubjects( Graph g )
    {
        return GraphUtil.listSubjects( g, Node.ANY, Node.ANY ).toSet();
    }

    public void testListPredicates()
    {
        Set<Node> emptyPredicates = listPredicates( getGraphWith( "" ) );
        Graph g = getGraphWith( "x P y; y Q z" );
        assertEquals( nodeSet( "P Q" ), remove( listPredicates( g ), emptyPredicates ) );
        g.delete( triple( "x P y" ) );
        assertEquals( nodeSet( "Q" ), remove( listPredicates( g ), emptyPredicates ) );
    }

    protected Set<Node> listPredicates( Graph g )
    {
        return GraphUtil.listPredicates( g, Node.ANY, Node.ANY ).toSet();
    }    

    public void testListObjects()
    {
        Set<Node> emptyObjects = listObjects( getGraphWith( "" ) );
        Graph g = getGraphWith( "x P y; y Q z" );
        assertEquals( nodeSet( "y z" ), remove( listObjects( g ), emptyObjects ) );
        g.delete( triple( "x P y" ) );
        assertEquals( nodeSet( "z" ), remove( listObjects( g ), emptyObjects ) );
    }

    protected Set<Node> listObjects( Graph g )
    {
        return GraphUtil.listObjects( g, Node.ANY, Node.ANY ).toSet();
    }

    /**
        Answer a set with all the elements of <code>A</code> except those
        in <code>B</code>.
     */
    private <T> Set<T> remove( Set<T> A, Set<T> B )
    {
        Set<T> result = new HashSet<T>( A );
        result.removeAll(  B  );        
        return result;
    }

    /**
         Ensure that triples removed by calling .remove() on the iterator returned by
         a find() will generate deletion notifications.
     */
    public void testEventDeleteByFind()
    {
        Graph g = getAndRegister( L );
        if (g.getCapabilities().iteratorRemoveAllowed())
        {
            Triple toRemove = triple( "remove this triple" );
            g.add( toRemove );
            ExtendedIterator<Triple> rtr = g.find( toRemove );
            assertTrue( "ensure a(t least) one triple", rtr.hasNext() );
            rtr.next(); rtr.remove(); rtr.close();
            L.assertHas( new Object[] { "add", g, toRemove, "delete", g, toRemove} );
        }
    }

    public void testTwoListeners()
    {
        RecordingListener L1 = new RecordingListener();
        RecordingListener L2 = new RecordingListener();
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        gem.register( L1 ).register( L2 );
        g.add( SPO );
        L2.assertHas( new Object[] {"add", g, SPO} );
        L1.assertHas( new Object[] {"add", g, SPO} );
    }

    public void testUnregisterWorks()
    {
        Graph g = getGraph();
        GraphEventManager gem = g.getEventManager();
        gem.register( L ).unregister( L );
        g.add( SPO );
        L.assertHas( new Object[] {} );
    }

    public void testRegisterTwice()
    {
        Graph g = getAndRegister( L );
        g.getEventManager().register( L );
        g.add( SPO );
        L.assertHas( new Object[] {"add", g, SPO, "add", g, SPO} );
    }

    public void testUnregisterOnce()
    {
        Graph g = getAndRegister( L );
        g.getEventManager().register( L ).unregister( L );
        g.delete( SPO );
        L.assertHas( new Object[] {"delete", g, SPO} );
    }

    public void testBulkAddArrayEvent()
    {
        Graph g = getAndRegister( L );
        Triple [] triples = tripleArray( "x R y; a P b" );
        GraphUtil.add(g, triples );
        L.assertHas( new Object[] {"add[]", g, triples} );
    }

    public void testBulkAddList()
    {
        Graph g = getAndRegister( L );
        List<Triple> elems = Arrays.asList( tripleArray( "bells ring loudly; pigs might fly" ) );
        GraphUtil.add(g, elems) ;
        L.assertHas( new Object[] {"addList", g, elems} );
    }

    public void testBulkDeleteArray()
    {
        Graph g = getAndRegister( L );
        Triple [] triples = tripleArray( "x R y; a P b" );
        GraphUtil.delete( g, triples );
        L.assertHas( new Object[] {"delete[]", g, triples} );
    }

    public void testBulkDeleteList()
    {
        Graph g = getAndRegister( L );
        List<Triple> elems = Arrays.asList( tripleArray( "bells ring loudly; pigs might fly" ) );
        GraphUtil.delete( g, elems );
        L.assertHas( new Object[] {"deleteList", g, elems} );
    }

    public void testBulkAddIterator()
    {
        Graph g = getAndRegister( L ); 
        Triple [] triples = tripleArray( "I wrote this; you read that; I wrote this" );
        GraphUtil.add(g, asIterator( triples ) );
        L.assertHas( new Object[] {"addIterator", g, Arrays.asList( triples )} );
    }

    public void testBulkDeleteIterator()
    {
        Graph g = getAndRegister( L );
        Triple [] triples = tripleArray( "I wrote this; you read that; I wrote this" );
        GraphUtil.delete( g, asIterator( triples ) );
        L.assertHas( new Object[] {"deleteIterator", g, Arrays.asList( triples )} );
    }

    public Iterator<Triple> asIterator( Triple [] triples )
    { return Arrays.asList( triples ).iterator(); }

    public void testBulkAddGraph()
    {
        Graph g = getAndRegister( L );
        Graph triples = graphWith( "this type graph; I type slowly" );
        GraphUtil.addInto( g, triples );
        L.assertHas( new Object[] {"addGraph", g, triples} );
    }

    public void testBulkDeleteGraph()
    {        
        Graph g = getAndRegister( L );
        Graph triples = graphWith( "this type graph; I type slowly" );
        GraphUtil.deleteFrom( g, triples );
        L.assertHas( new Object[] {"deleteGraph", g, triples} );
    }

    public void testGeneralEvent()
    {
        Graph g = getAndRegister( L );
        Object value = new int[]{};
        g.getEventManager().notifyEvent( g, value );
        L.assertHas( new Object[] { "someEvent", g, value } );
    }

    public void testRemoveAllEvent()
    {
        Graph g = getAndRegister( L );
        g.clear();
        L.assertHas( new Object[] { "someEvent", g, GraphEvents.removeAll } );        
    }

    public void testRemoveSomeEvent()
    {
        Graph g = getAndRegister( L );
        Node S = node( "S" ), P = node( "??" ), O = node( "??" );
        g.remove( S, P, O );
        Object event = GraphEvents.remove( S, P, O );
        L.assertHas( new Object[] { "someEvent", g, event } );        
    }

    /**
     * Test that nodes can be found in all triple positions.
     * However, testing for literals in subject positions is suppressed
     * at present to avoid problems with InfGraphs which try to prevent
     * such constructs leaking out to the RDF layer.
     */
    public void testContainsNode()
    {
        Graph g = getGraph();
        graphAdd( g, "a P b; _c _Q _d; a 11 12" );
        assertTrue( containsNode( g, node( "a" ) ) );
        assertTrue( containsNode( g, node( "P" ) ) );
        assertTrue( containsNode( g, node( "b" ) ) );
        assertTrue( containsNode( g, node( "_c" ) ) );
        assertTrue( containsNode( g, node( "_Q" ) ) );
        assertTrue( containsNode( g, node( "_d" ) ) );
        //        assertTrue( qh.containsNode( node( "10" ) ) );
        assertTrue( containsNode( g, node( "11" ) ) );
        assertTrue( containsNode( g, node( "12" ) ) );
        /* */
        assertFalse( containsNode( g, node( "x" ) ) );
        assertFalse( containsNode( g, node( "_y" ) ) );
        assertFalse( containsNode( g, node( "99" ) ) );
    }



    private boolean containsNode(Graph g, Node node)
    {
        return GraphUtil.containsNode(g, node) ;
    }

    public void testSubjectsFor()
    {
        // First get the answer from the empty graph (not empty for an inf graph)
        Graph b = getGraphWith( "" );
        Set<Node> B = GraphUtil.listSubjects( b, Node.ANY, Node.ANY ).toSet();
        
        Graph g = getGraphWith( "a P b; a Q c; a P d; b P x; c Q y" );
        
        testSubjects( g, B, Node.ANY, Node.ANY, node("a"), node("b"), node("c") );
        testSubjects( g, B, node( "P" ), Node.ANY, node("a"), node("b"));
        testSubjects( g, B, node( "Q" ), node( "c" ), node("a") );
        testSubjects( g, B, node( "Q" ), node( "y" ), node("c") );
        testSubjects( g, B, node( "Q" ), node( "a" ));
        testSubjects( g, B, node( "Q" ), node( "z" ));
    }

    protected void testSubjects( Graph g, Collection<Node> exclude, Node p, Node o, Node... expected )
    {
        List<Node> R = GraphUtil.listSubjects( g, p, o ).toList();
        R.removeAll(exclude) ;
        assertSameUnordered(R, exclude, expected) ;
    }    

    // Same - except for order
    private void assertSameUnordered(List<Node> x1, Collection<Node>exclude, Node[] expected)
    {
        List<Node> x = new ArrayList<Node>() ;
        x.addAll(x1) ;
        x.removeAll(exclude) ;
        
        assertEquals(expected.length, x.size()) ;
        Set<Node> X = new HashSet<Node>() ;
        X.addAll(x) ;

        Set<Node> R = new HashSet<Node>() ;
        R.addAll(Arrays.asList(expected)) ;

        assertEquals( R, X);

    }

    public void testListSubjectsNoRemove()
    {
        Graph g = getGraphWith( "a P b; b Q c; c R a" );
        Iterator<Node> it = GraphUtil.listSubjects( g, Node.ANY, Node.ANY );
        it.next();
        try { it.remove(); fail( "listSubjects for " + g.getClass() + " should not support .remove()" ); }
        catch (UnsupportedOperationException e) { pass(); }
    }

    public void testObjectsFor()
    {
        // First get the answer from the empty graph (not empty for an inf graph)
        Graph b = getGraphWith( "" );
        Set<Node> B = GraphUtil.listObjects( b, Node.ANY, Node.ANY ).toSet();

        Graph g = getGraphWith( "b P a; c Q a; d P a; x P b; y Q c" );
        testObjects( g, B, Node.ANY, Node.ANY, node("a"), node("b"), node("c") );
        testObjects( g, B, Node.ANY, node( "P" ), node("a"), node("b") );
        testObjects( g, B, node( "c" ), node( "Q" ), node("a") );
        testObjects( g, B, node( "y" ), node( "Q" ), node("c") );
        testObjects( g, B, node( "a" ), node( "Q" ));
        testObjects( g, B, node( "z" ), node( "Q" ));
    }    

    protected void testObjects( Graph g, Collection<Node> exclude, Node s, Node p, Node... expected )
    {
        List<Node> X = GraphUtil.listObjects( g, s, p ).toList();
        assertSameUnordered(X, exclude, expected) ;
    }

    public void testPredicatesFor()
    {
        // First get the answer from the empty graph (not empty for an inf graph)
        Graph b = getGraphWith( "" );
        Set<Node> B = GraphUtil.listPredicates( b, Node.ANY, Node.ANY ).toSet();

        Graph g = getGraphWith( "a P b; z P b; c Q d; e R f; g P b; h Q i" );
        testPredicates( g, B, Node.ANY, Node.ANY, node("P"), node("Q"), node("R") );
        testPredicates( g, B, Node.ANY, node( "b" ), node("P") );
        testPredicates( g, B, node( "g" ), Node.ANY, node("P")) ;
        testPredicates( g, B, node( "c" ), node( "d" ), node("Q") );
        testPredicates( g, B, node( "e" ), node( "f" ), node("R") );
        testPredicates( g, B, node( "e" ), node( "a" ));
        testPredicates( g, B, node( "z" ), node( "y" ));
    }    

    protected void testPredicates( Graph g, Collection<Node> exclude, Node s, Node o, Node... expected )
    {
        List<Node> X = GraphUtil.listPredicates( g, s, o ).toList();
        assertSameUnordered(X, exclude, expected) ;
    }

    public void testListObjectsNoRemove()
    {
        Graph g = getGraphWith( "a P b; b Q c; c R a" );
        Iterator<Node> it = GraphUtil.listObjects( g, Node.ANY, Node.ANY );
        it.next();
        try { it.remove(); fail( "listObjects for " + g.getClass() + " should not support .remove()" ); }
        catch (UnsupportedOperationException e) { pass(); }
    }

    public void testListPredicatesNoRemove()
    {
        Graph g = getGraphWith( "a P b; b Q c; c R a" );
        Iterator<Node> it = GraphUtil.listPredicates( g, Node.ANY, Node.ANY );
        it.next();
        try { it.remove(); fail( "listPredicates for " + g.getClass() + " should not support .remove()" ); }
        catch (UnsupportedOperationException e) { pass(); }
    }

    public void testRemoveAll()
    { 
        testRemoveAll( "" );
        testRemoveAll( "a R b" );
        testRemoveAll( "c S d; e:ff GGG hhhh; _i J 27; Ell Em 'en'" );
    }



    public void testRemoveAll( String triples )
    {
        Graph g = getGraph();
        graphAdd( g, triples );
        g.clear();
        assertTrue( g.isEmpty() );
    }

    public void failingTestDoubleRemoveAll() {
        final Graph g = getGraph();
        if (g.getCapabilities().iteratorRemoveAllowed() ) {
            graphAdd(g,"c S d; e:ff GGG hhhh; _i J 27; Ell Em 'en'"  );
            Iterator<Triple> it = new TrackingTripleIterator(g.find(Triple.ANY)){
                @Override
                public void remove() {
                    super.remove(); // removes current
                    g.delete(current); // no-op.
                }
            };
            while (it.hasNext()) {
                it.next();
                it.remove();
            }
            assertTrue( g.isEmpty() );
        }
    }

    public void testGetStatisticsHandler()
    {
        Graph g = getGraph();
        GraphStatisticsHandler h = g.getStatisticsHandler();
        assertSame( h, g.getStatisticsHandler() );
    }

    /**
     	Test cases for RemoveSPO(); each entry is a triple (add, remove, result).
     	<ul>
     	<li>add - the triples to add to the graph to start with
     	<li>remove - the pattern to use in the removal
     	<li>result - the triples that should remain in the graph
     	</ul>
     */
    protected String[][] cases =
    {
        { "x R y", "x R y", "" },
        { "x R y; a P b", "x R y", "a P b" },
        { "x R y; a P b", "?? R y", "a P b" },
        { "x R y; a P b", "x R ??", "a P b" },
        { "x R y; a P b", "x ?? y", "a P b" },      
        { "x R y; a P b", "?? ?? ??", "" },       
        { "x R y; a P b; c P d", "?? P ??", "x R y" },       
        { "x R y; a P b; x S y", "x ?? ??", "a P b" },                 
    };

    /**
     	Test that remove(s, p, o) works, in the presence of inferencing graphs that
     	mean emptyness isn't available. This is why we go round the houses and
     	test that expected ~= initialContent + addedStuff - removed - initialContent.
     */
    public void testRemoveSPO()
    {
        for (int i = 0; i < cases.length; i += 1)
            for (int j = 0; j < 3; j += 1)
            {
                Graph content = getGraph();
                Graph baseContent = copy( content );
                graphAdd( content, cases[i][0] );
                Triple remove = triple( cases[i][1] );
                Graph expected = graphWith( cases[i][2] );
                content.remove( remove.getSubject(), remove.getPredicate(), remove.getObject() );
                Graph finalContent = remove( copy( content ), baseContent );
                assertIsomorphic( cases[i][1], expected, finalContent );
            }
    }

    /** testIsomorphism from file data 
     * @throws FileNotFoundException */
    public void testIsomorphismFile() throws URISyntaxException, MalformedURLException {
        testIsomorphismXMLFile(1,true);
        testIsomorphismXMLFile(2,true);
        testIsomorphismXMLFile(3,true);
        testIsomorphismXMLFile(4,true);
        testIsomorphismXMLFile(5,false);
        testIsomorphismXMLFile(6,false);
        testIsomorphismNTripleFile(7,true);
        testIsomorphismNTripleFile(8,false);

    }
    private void testIsomorphismNTripleFile(int i, boolean result) throws URISyntaxException, MalformedURLException {
        testIsomorphismFile(i,"N-TRIPLE","nt",result);
    }

    private void testIsomorphismXMLFile(int i, boolean result) throws URISyntaxException, MalformedURLException {
        testIsomorphismFile(i,"RDF/XML","rdf",result);

    }

    private InputStream getInputStream( int n, int n2, String suffix) throws URISyntaxException, MalformedURLException
    {
    	String urlStr = String.format( "regression/testModelEquals/%s-%s.%s", n, n2, suffix);
    	return AbstractTestGraph.class.getClassLoader().getResourceAsStream(  urlStr );
    }
    
    private void testIsomorphismFile(int n, String lang, String suffix, boolean result) throws URISyntaxException, MalformedURLException {

        Graph g1 = getGraph();
        Graph g2 = getGraph();
        Model m1 = ModelFactory.createModelForGraph(g1);
        Model m2 = ModelFactory.createModelForGraph(g2);

        m1.read(
                getInputStream(n, 1, suffix),
                "http://www.example.org/",lang);
        m2.read(
                getInputStream(n, 2, suffix),
                "http://www.example.org/",lang);

        boolean rslt = g1.isIsomorphicWith(g2) == result;
        if (!rslt) {
            System.out.println("g1:");
            m1.write(System.out, "N-TRIPLE");
            System.out.println("g2:");
            m2.write(System.out, "N-TRIPLE");
        }
        assertTrue("Isomorphism test failed",rslt);
    }

    protected void add( Graph toUpdate, Graph toAdd )
    {
        GraphUtil.addInto( toUpdate, toAdd) ;
    }

    protected Graph remove( Graph toUpdate, Graph toRemove )
    {
        GraphUtil.deleteFrom(toUpdate, toRemove) ;
        return toUpdate;
    }


    protected Graph copy( Graph g )
    {
        Graph result = Factory.createDefaultGraph();
        GraphUtil.addInto(result, g) ;
        return result;
    }

    protected Graph getClosed()
    {
        Graph result = getGraph();
        result.close();
        return result;
    }

    //    public void testClosedDelete()
    //        {
    //        try { getClosed().delete( triple( "x R y" ) ); fail( "delete when closed" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }
    //        
    //    public void testClosedAdd()
    //        {
    //        try { getClosed().add( triple( "x R y" ) ); fail( "add when closed" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }
    //        
    //    public void testClosedContainsTriple()
    //        {
    //        try { getClosed().contains( triple( "x R y" ) ); fail( "contains[triple] when closed" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }
    //        
    //    public void testClosedContainsSPO()
    //        {
    //        Node a = Node.ANY;
    //        try { getClosed().contains( a, a, a ); fail( "contains[SPO] when closed" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }
    //        
    //    public void testClosedFindTriple()
    //        {
    //        try { getClosed().find( triple( "x R y" ) ); fail( "find [triple] when closed" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }
    //        
    //    public void testClosedFindSPO()
    //        {
    //        Node a = Node.ANY;
    //        try { getClosed().find( a, a, a ); fail( "find[SPO] when closed" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }
    //        
    //    public void testClosedSize()
    //        {
    //        try { getClosed().size(); fail( "size when closed (" + this.getClass() + ")" ); }
    //        catch (ClosedException c) { /* as required */ }
    //        }

}
