/*
 * (c) Copyright 2008 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package dev;

public class Dev
{
    /*
     * Getting into an algebra for *execution*
     * Separate system or extension of algebra?
     *   Extension - but be clear of what's where.
     * What makes it hard to introduce a new operation?
     *   Builder/Writer
     *   Transform engine
     *   2 * evaluations (will become one!)

    + One-sided versions of operators for a sequence.
      Only valid in a sequence.
      Have a "marker" op meaning current working table.
          (seq) (current)
          
          (filter (current) ...)
          (leftJoin (current) other)
    
    + Transform framework :
        Match => Action for a single rewrite?  Need to worry about this?
          Transform collection.
            Move PF to Transforms
            FilterPlacement
            Simply
            Equality filter

    Direct (flow based) execution in main - remove the FilterPlacement and BGP optimizer to an optimize step. 

+ Stages and FilterPlacement.
    invert Stage/StageList
    Formalse the TDB stacking generator approach.
      Or even register stage facctories with accept/create 
  
    StateGenerator (and invert StageLists) 
            
     */
}

/*
 * (c) Copyright 2008 Hewlett-Packard Development Company, LP
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