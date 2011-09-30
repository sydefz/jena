/**
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

package com.hp.hpl.jena.sparql.expr.aggregate;

import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.sparql.engine.binding.Binding ;
import com.hp.hpl.jena.sparql.expr.Expr ;
import com.hp.hpl.jena.sparql.expr.NodeValue ;
import com.hp.hpl.jena.sparql.function.FunctionEnv ;
import com.hp.hpl.jena.sparql.sse.writers.WriterExpr ;
import com.hp.hpl.jena.sparql.util.ExprUtils ;

public class AggSampleDistinct extends AggregatorBase
{
    // ---- Sample(DISTINCT expr)
    private final Expr expr ;

    public AggSampleDistinct(Expr expr) { this.expr = expr ; } 
    public Aggregator copy(Expr expr) { return new AggSampleDistinct(expr) ; }

    @Override
    public String toString() { return "SAMPLE(DISTINCT "+ExprUtils.fmtSPARQL(expr)+")" ; }
    @Override
    public String toPrefixString() { return "(sample distinct"+WriterExpr.asString(expr)+")" ; }

    @Override
    public Accumulator createAccumulator()
    { 
        return new AccSampleDistict(expr) ;
    }

    public Expr getExpr() { return expr ; }

    @Override
    public int hashCode()   { return HC_AggSample ^ expr.hashCode() ; }
    @Override
    public boolean equals(Object other)
    {
        if ( this == other ) return true ; 
        if ( ! ( other instanceof AggSampleDistinct ) )
            return false ;
        AggSampleDistinct agg = (AggSampleDistinct)other ;
        return agg.getExpr().equals(getExpr()) ;
    } 

    @Override
    public Node getValueEmpty()     { return null ; } 

    // ---- Accumulator
    private static class AccSampleDistict extends AccumulatorExpr
    {
        // NOT AccumulatorDistinctExpr - avoid "distinct" overheads. 
        // Sample: first evaluation of the expression that is not an error.
        // For sample, DISTINCT is a no-op - this code is picks the last element. 
        private NodeValue sampleSoFar = null ;

        public AccSampleDistict(Expr expr) { super(expr)  ; }

        @Override
        public void accumulate(NodeValue nv, Binding binding, FunctionEnv functionEnv)
        {
            // Last value seen.
            sampleSoFar = nv ;
        }

        @Override
        protected void accumulateError(Binding binding, FunctionEnv functionEnv)
        {}
        
        @Override
        public NodeValue getAccValue()
        { return sampleSoFar ; }
    }
}