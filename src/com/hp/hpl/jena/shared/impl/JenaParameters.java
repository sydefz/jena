/******************************************************************
 * File:        JenaParameters.java
 * Created by:  Dave Reynolds
 * Created on:  23-Aug-2003
 * 
 * (c) Copyright 2003, Hewlett-Packard Company, all rights reserved.
 * [See end of file]
 * $Id: JenaParameters.java,v 1.1 2003-08-23 12:17:06 der Exp $
 *****************************************************************/
package com.hp.hpl.jena.shared.impl;

/**
 * This class holds global, static, configuration parameters that
 * affect the behaviour of the Jena API. These should not be changed
 * unless you are sure you know what you are doing and even then then
 * should ideally only be changed before any Models are created or processed.
 * <p>
 * These parameters should not be regarded as a stable part of the API. If
 * we find them being used significantly that probably means they should be
 * moved to being model-specific rather than global.
 * </p>
 * @author <a href="mailto:der@hplb.hpl.hp.com">Dave Reynolds</a>
 * @version $Revision: 1.1 $ on $Date: 2003-08-23 12:17:06 $
 */
public class JenaParameters {
    
//  =======================================================================
//  Parameters affected handling of typed literals
    
    /** 
     * <p> Set this flag to true to cause typed literals to be
     * validated as they are created. </p>
     * <p>
     * RDF does not require ill-formed typed literals to be rejected from a graph
     * but rather allows them to be included but marked as distinct from
     * all legally formed typed literals. Jena2 reflects this by optionally
     * delaying validation of literals against datatype type constraints until
     * the first access. </p>
     */
     public static boolean enableEagerLiteralValidation = false;

     /**
      * Set this flag to true to allow language-free, plain literals and xsd:strings
      * containing the same character sequence to test as sameAs.
      * <p>
      * RDF plain literals and typed literals of type xsd:string are distinct, not
      * least because plain literals may have a language tag which typed literals
      * may not. However, in the absence of a languge tag it can be convenient
      * for applications if the java objects representing identical character
      * strings in these two ways test as semantically "sameAs" each other.
      * At the time of writing is unclear if such identification would be sanctioned
      * by the RDF working group. </p> 
      */
     public static boolean enablePlainLiteralSameAsString = true;
     
     /**
      * Set this flag to true to allow unknown literal datatypes to be
      * accepted, if false then such literals will throw an exception when
      * first detected. Note that any datatypes unknown datatypes encountered
      * whilst this flag is 'true' will be automatically registered (as a type 
      * whose value and lexical spaces are identical). Subsequently turning off
      * this flag will not unregister those unknown types already encountered.
      * <p>
      * RDF allows any URI to be used to indicate a datatype. Jena2 allows
      * user defined datatypes to be registered but it is sometimes convenient
      * to be able to process models containing unknown datatypes (e.g. when the
      * application does not need to touch the value form of the literal). However,
      * this behaviour means that common errors, such as using the wrong URI for
      * xsd datatypes, may go unnoticed and throw obscure errors late in processing.
      * Hence, the default is the require unknown datatypes to be registered.
      */
     public static boolean enableSilentAcceptanceOfUnknownDatatypes = true;

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