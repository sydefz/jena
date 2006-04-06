/*
 	(c) Copyright 2005, 2006 Hewlett-Packard Development Company, LP
 	All rights reserved - see end of file.
 	$Id: TestAssemblerHelp.java,v 1.9 2006-04-06 15:28:14 chris-dollin Exp $
*/

package com.hp.hpl.jena.assembler.test;

import java.util.*;

import com.hp.hpl.jena.assembler.*;
import com.hp.hpl.jena.assembler.assemblers.*;
import com.hp.hpl.jena.assembler.exceptions.NoSpecificTypeException;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.shared.*;
import com.hp.hpl.jena.vocabulary.RDF;

public class TestAssemblerHelp extends AssemblerTestBase
    {
    public TestAssemblerHelp( String name )
        { super( name ); }

    protected Class getAssemblerClass()
        { throw new BrokenException( "TestAssemblers does not need this method" ); }
    
    public void testClosureFootprint()
        {
        Resource root = resourceInModel( "x ja:reasoner y" );
        Statement footprint = root.getModel().createStatement( JA.This, RDF.type, JA.Expanded );
        assertFalse( root.getModel().contains( footprint ) );
        Resource expanded = AssemblerHelp.withFullModel( root );
        assertTrue( expanded.getModel().contains( footprint ) );
        }
    
    public void testFootprintPreventsClosure()
        {
        Resource root = resourceInModel( "x ja:reasoner y; ja:this rdf:type ja:Expanded" );
        Model original = model( "" ).add( root.getModel() );
        Resource expanded = AssemblerHelp.withFullModel( root );
        assertSame( root, expanded );
        assertIsoModels( original, expanded.getModel() );
        }
      
    public void testSpecificType()
        {
        testSpecificType( "ja:Connectable", "x ja:connection _C" );
        testSpecificType( "ja:NamedModel", "x ja:modelName 'name'" );
        testSpecificType( "ja:NamedModel", "x ja:modelName 'name'; x rdf:type irrelevant" );
        testSpecificType( "ja:RDBModel", "x rdf:type ja:RDBModel; x rdf:type ja:Model" );
        }
    
    public void testSpecificTypeFails()
        {
        try
            {
            testSpecificType( "xxx", "x rdf:type ja:Model; x rdf:type ja:PrefixMapping" );
            fail( "should trap multiple types" );
            }
        catch (NoSpecificTypeException e)
            {
            assertEquals( resource( "x" ), e.getRoot() );
            assertEquals( resources( e.getRoot(), "ja:Model ja:PrefixMapping" ), new HashSet( e.getTypes() ) );
            }
        }

    private Set resources( Resource root, String items )
        {
        List L = listOfStrings( items );
        Set result = new HashSet();
        for (int i = 0; i < L.size(); i += 1)
            result.add( resource( root.getModel(), (String) L.get(i) ) );
        return result;
        }

    private void testSpecificType( String expected, String specification )
        { // TODO relies on fullModel, would be nice to remove this dependency
        Resource root = resourceInModel( specification );
        Resource rooted = (Resource) root.inModel( AssemblerHelp.fullModel( root.getModel() ) );
        Resource mst = AssemblerHelp.findSpecificType( rooted );
        assertEquals( resource( root.getModel(), expected ), mst );
        }

    
    public static boolean impIsLoaded = false;
    public static boolean impIsConstructed = false;
    
    public static class Imp extends AssemblerBase
        {
        static { impIsLoaded = true; }

        public Imp()
            { impIsConstructed = true; }
        
        public Object open( Assembler a, Resource root, Mode irrelevant )
            { return null; }
        }
    
    static Model gremlinModel = modelWithStatements( "eh:Wossname ja:assembler 'com.hp.hpl.jena.assembler.test.TestAssemblerHelp$Gremlin'" );
    
    static boolean gremlinInvoked = false;
    
    public static class Gremlin extends AssemblerBase
        {
        public Gremlin()
            { fail( "Gremlin no-argument constructor should not be called" ); }
        
        public Gremlin( Resource root )
            {
            assertEquals( resource( "eh:Wossname" ), root );
            assertIsoModels( gremlinModel, root.getModel() );
            gremlinInvoked = true;
            }
        
        public Object open( Assembler a, Resource root, Mode irrelevant )
            { return null; }
        }
    
    public void testClassAssociation()
        {
        String className = "com.hp.hpl.jena.assembler.test.TestAssemblerHelp$Imp";
        AssemblerGroup group = AssemblerGroup.create();
        Model m = model( "eh:Wossname ja:assembler '" + className + "'" );
        assertEquals( false, impIsLoaded );
        AssemblerHelp.loadClasses( group, m );
        assertEquals( true, impIsLoaded );
        assertEquals( true, impIsConstructed );
        assertEquals( className, group.assemblerFor( resource( "eh:Wossname" ) ).getClass().getName() );
        }    
    
    public void testClassResourceConstructor()
        {
        AssemblerGroup group = AssemblerGroup.create();
        Model m = model( "eh:Wossname ja:assembler 'com.hp.hpl.jena.assembler.test.TestAssemblerHelp$Gremlin'" );
        assertEquals( false, gremlinInvoked );
        AssemblerHelp.loadClasses( group, m );
        assertEquals( true, gremlinInvoked );
        }
    }


/*
 * (c) Copyright 2005, 2006 Hewlett-Packard Development Company, LP
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