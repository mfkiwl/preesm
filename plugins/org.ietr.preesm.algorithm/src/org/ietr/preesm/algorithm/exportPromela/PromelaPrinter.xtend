/*********************************************************
 * Copyright or © or Copr. IETR/INSA: Maxime Pelcat, Jean-François Nezan,
 * Karol Desnos
 * 
 * [mpelcat,jnezan,kdesnos,jheulot]@insa-rennes.fr
 * 
 * This software is a computer program whose purpose is to prototype
 * parallel applications.
 * 
 * This software is governed by the CeCILL-C license under French law and
 * abiding by the rules of distribution of free software.  You can  use, 
 * modify and/ or redistribute the software under the terms of the CeCILL-C
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". 
 * 
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability. 
 * 
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or 
 * data to be ensured and,  more generally, to use and operate it in the 
 * same conditions as regards security. 
 * 
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 *********************************************************/
package org.ietr.preesm.algorithm.exportPromela

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.List
import org.eclipse.xtend.lib.annotations.Accessors
import org.ietr.dftools.algorithm.model.sdf.SDFAbstractVertex
import org.ietr.dftools.algorithm.model.sdf.SDFEdge
import org.ietr.dftools.algorithm.model.sdf.SDFGraph
import org.ietr.dftools.algorithm.model.sdf.types.SDFIntEdgePropertyType

/**
 * Printer used to generate a Promela program as specified in : <br>
 * <br>
 * <code>Marc Geilen, Twan Basten, and Sander Stuijk. 2005. Minimising buffer 
 * requirements of synchronous dataflow graphs with model checking. In 
 * Proceedings of the 42nd annual Design Automation Conference (DAC '05). ACM, 
 * New York, NY, USA, 819-824. DOI=10.1145/1065579.1065796 
 * http://doi.acm.org/10.1145/1065579.1065796 </code>
 * 
 * @author kdesnos
 * 
 */
class PromelaPrinter {

	/**
	 * The {@link SDFGraph} printed by the current instance of {@link 
	 * SDFPrinter}.
	 */
	@Accessors
	val SDFGraph sdf

	/**
	 * List of the edges of the graph.
	 * This list has a constant order, which is needed to make sure that the 
	 * index used to access a FIFO in the generated code will always be the
	 * same. 
	 */
	 @Accessors
	 val List<SDFEdge> fifos
	 
	new(SDFGraph sdf) {
		this.sdf = sdf
		this.fifos = sdf.edgeSet.toList
	}

	/**
	 * Computes the Greatest Common Divisor of two numbers.
	 */
	static def int gcd(int a, int b) {
		if(b == 0) return a
		return gcd(b, a % b)
	}

	/** 
	 * Write the result of a call to the {@link #print()} method in the given {@link File} 
	 * 
	 * @param file the File where to print the code. 
	 */
	def write(File file) {
		try {
			val writer = new FileWriter(file);
			writer.write(this.print().toString);

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main method to print the {@link SDFGraph} in the Promela format.
	 * 
	 * @return the {@link CharSequence} containing the PML representation of 
	 * the graph.
	 */
	def print() '''
		#define UPDATE(c) if :: ch[c]>sz[c] -> sz[c] = ch[c] :: else fi
		#define PRODUCE(c,n) ch[c] = ch[c] + n; UPDATE(c)
		#define CONSUME(c,n) ch[c] = ch[c] - n
		#define WAIT(c,n) ch[c]>=n
		#define BOUND «fifos.fold(0,[
			res, fifo | res + fifo.prod.intValue 
			+ fifo.cons.intValue - gcd(fifo.prod.intValue,fifo.cons.intValue) 
			+ (fifo.delay?:new SDFIntEdgePropertyType(0)).intValue % gcd(fifo.prod.intValue,fifo.cons.intValue) 
		])» // Initialized with the sum of prod + cons - gcd(prod,cons) + delay % gcd(prod,cons)
		#define SUM «FOR i : 0 .. fifos.size - 1 SEPARATOR " + "»ch[«i»]«ENDFOR»
		#define t (SUM>BOUND)
		
		ltl P1 { <> (SUM>BOUND) }
		
		int ch[«fifos.size»]; int sz[«fifos.size»];
		
		«FOR actor : sdf.vertexSet»
			«actor.print»
		«ENDFOR»
		
		init {
			atomic {
				«FOR actor : sdf.vertexSet»
				run «actor.name»();
				«ENDFOR»
			}
		}		
	'''

	/**
	 * Print an {@link SDFAbstractVertex} of the graph.
	 * 
	 * @param actor
	 * 	the printed {@link SDFAbstractVertex}.
	 * 
	 * @return the {@link CharSequence} containing the PML code for the
	 * actor and its ports in the Promela format.
	 */
	def print(SDFAbstractVertex actor) '''
		proctype «actor.name»(){
			do
			:: atomic {
				«FOR input : sdf.incomingEdgesOf(actor)»
					WAIT(«fifos.indexOf(input)», «input.cons.intValue») ->
				«ENDFOR»
				«FOR input : sdf.incomingEdgesOf(actor)»
					CONSUME(«fifos.indexOf(input)», «input.cons.intValue»);
				«ENDFOR»
				«FOR output : sdf.outgoingEdgesOf(actor)»
					PRODUCE(«fifos.indexOf(output)», «output.prod.intValue»);
				«ENDFOR»
			}
			od
		}
	'''

}