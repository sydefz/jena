/******************************************************************
 * File:        BasicBackwardRuleReasoner.java
 * Created by:  Dave Reynolds
 * Created on:  29-Apr-2003
 * 
 * (c) Copyright 2003, Hewlett-Packard Company, all rights reserved.
 * [See end of file]
 * $Id: BasicBackwardRuleReasoner.java,v 1.7 2003-06-02 16:52:30 der Exp $
 *****************************************************************/
package com.hp.hpl.jena.reasoner.rulesys;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.reasoner.*;
import com.hp.hpl.jena.reasoner.rulesys.impl.RuleStore;
import com.hp.hpl.jena.graph.*;
import java.util.*;

/**
 * Reasoner implementation which augments or transforms an RDF graph
 * according to a set of rules. The rules are processed using a
 * tabled backchaining interpreter which is implemented by the
 * relvant InfGraph class. 
 * 
 * @author <a href="mailto:der@hplb.hpl.hp.com">Dave Reynolds</a>
 * @version $Revision: 1.7 $ on $Date: 2003-06-02 16:52:30 $
 */
public class BasicBackwardRuleReasoner implements Reasoner {

    /** The parent reasoner factory which is consulted to answer capability questions */
    protected ReasonerFactory factory;
    
    /** The rules to be used by this instance of the backward engine */
    protected List rules;
    
    /** Indexed, normalized copy of the rule list */
    protected RuleStore ruleStore;
    
    /** A cache set of schema data used in partial binding chains */
    protected Graph schemaGraph;
    
    /** Flag to set whether the inference class should record derivations */
    protected boolean recordDerivations = false;
    
    /** Flag which, if true, enables tracing of rule actions to logger.info */
    boolean traceOn = false;

    /**
     * Constructor. This is the raw version that does not reference a ReasonerFactory
     * and so has no capabilities description. 
     * @param rules a list of Rule instances which defines the ruleset to process
     */
    public BasicBackwardRuleReasoner(List rules) {
        this.rules = rules;
        ruleStore = new RuleStore(rules);
    }

    /**
     * Constructor
     * @param rules a list of Rule instances which defines the ruleset to process
     * @param factory the parent reasoner factory which is consulted to answer capability questions
     */
    public BasicBackwardRuleReasoner(List rules, ReasonerFactory factory) {
        this.rules = rules;
        this.factory = factory;
        ruleStore = new RuleStore(rules);
    }
    
    /**
     * Internal constructor, used to generated a partial binding of a schema
     * to a rule reasoner instance.
     */
    protected BasicBackwardRuleReasoner(BasicBackwardRuleReasoner parent, Graph schemaGraph) {
        rules = parent.rules;
        ruleStore = parent.ruleStore;
        this.schemaGraph = schemaGraph;
        this.factory = parent.factory;
    }

    /**
     * Return a description of the capabilities of this reasoner encoded in
     * RDF. These capabilities may be static or may depend on configuration
     * information supplied at construction time. May be null if there are
     * no useful capabilities registered.
     */
    public Model getCapabilities() {
        if (factory != null) {
            return factory.getCapabilities();
        } else {
            return null;
        }
    }

    /**
     * Determine whether the given property is recognized and treated specially
     * by this reasoner. This is a convenience packaging of a special case of getCapabilities.
     * @param property the property which we want to ask the reasoner about, given as a Node since
     * this is part of the SPI rather than API
     * @return true if the given property is handled specially by the reasoner.
     */
    public boolean supportsProperty(Property property) {
        if (factory == null) return false;
        Model caps = factory.getCapabilities();
        Resource root = caps.getResource(factory.getURI());
        return caps.contains(root, ReasonerRegistry.supportsP, property);
    }
    
    /**
     * Precompute the implications of a schema graph. The statements in the graph
     * will be combined with the data when the final InfGraph is created.
     */
    public Reasoner bindSchema(Graph tbox) throws ReasonerException {
        return new BasicBackwardRuleReasoner(this, tbox);
    }
    
    /**
     * Precompute the implications of a schema Model. The statements in the graph
     * will be combined with the data when the final InfGraph is created.
     */
    public Reasoner bindSchema(Model tbox) throws ReasonerException {
        return new BasicBackwardRuleReasoner(this, tbox.getGraph());
    }
    
    /**
     * Attach the reasoner to a set of RDF data to process.
     * The reasoner may already have been bound to specific rules or ontology
     * axioms (encoded in RDF) through earlier bindRuleset calls.
     * 
     * @param data the RDF data to be processed, some reasoners may restrict
     * the range of RDF which is legal here (e.g. syntactic restrictions in OWL).
     * @return an inference graph through which the data+reasoner can be queried.
     * @throws ReasonerException if the data is ill-formed according to the
     * constraints imposed by this reasoner.
     */
    public InfGraph bind(Graph data) throws ReasonerException {
        BasicBackwardRuleInfGraph graph = new BasicBackwardRuleInfGraph(this, data, ruleStore);
        graph.setDerivationLogging(recordDerivations);
        return graph;
    }
    
    /**
     * Return the this of Rules used by this reasoner
     * @return a List of Rule objects
     */
    public List getRules() {
        return rules;
    } 
   
    /**
     * Switch on/off drivation logging.
     * If set to true then the InfGraph created from the bind operation will start
     * life with recording of derivations switched on. This is currently only of relevance
     * to rule-based reasoners.
     * <p>
     * Default - false.
     */
    public void setDerivationLogging(boolean logOn) {
        recordDerivations = logOn;
    }
    
    /**
     * Set the state of the trace flag. If set to true then rule firings
     * are logged out to the Logger at "INFO" level.
     */
    public void setTraceOn(boolean state) {
        traceOn = state;
    }
    
    /**
     * Set a configuration paramter for the reasoner. In the case of the this
     * reasoner there are no configuration parameters and this method is simply 
     * here to meet the interfaces specification
     * 
     * @param parameterUri the uri identifying the parameter to be changed
     * @param value the new value for the parameter, typically this is a wrapped
     * java object like Boolean or Integer.
     */
    public void setParameter(String parameterUri, Object value) {
        throw new IllegalParameterException(parameterUri);
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

