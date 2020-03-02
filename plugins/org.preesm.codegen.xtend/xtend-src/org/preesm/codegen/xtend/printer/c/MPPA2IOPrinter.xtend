/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2013 - 2019) :
 *
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2017 - 2019)
 * Daniel Madroñal [daniel.madronal@upm.es] (2019)
 * Dylan Gageot [gageot.dylan@gmail.com] (2019)
 * Julien Hascoet [jhascoet@kalray.eu] (2016 - 2017)
 * Karol Desnos [karol.desnos@insa-rennes.fr] (2013 - 2017)
 *
 * This software is a computer program whose purpose is to help prototyping
 * parallel applications using dataflow formalism.
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
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
 * knowledge of the CeCILL license and that you accept its terms.
 */
package org.preesm.codegen.xtend.printer.c

import java.io.IOException
import java.io.InputStreamReader
import java.io.StringWriter
import java.net.URL
import java.util.Collection
import java.util.Date
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.List
import java.util.Map
import java.util.Set
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.eclipse.emf.common.util.EList
import org.preesm.codegen.model.Block
import org.preesm.codegen.model.Buffer
import org.preesm.codegen.model.CodeElt
import org.preesm.codegen.model.CoreBlock
import org.preesm.codegen.model.FiniteLoopBlock
import org.preesm.codegen.model.LoopBlock
import org.preesm.codegen.model.PapifyFunctionCall
import org.preesm.codegen.model.PapifyType
import org.preesm.codegen.model.SubBuffer
import org.preesm.codegen.model.Variable
import org.preesm.commons.exceptions.PreesmRuntimeException
import org.preesm.commons.files.PreesmResourcesHelper
import org.preesm.model.pisdf.util.CHeaderUsedLocator
import org.preesm.codegen.model.SharedMemoryCommunication
import org.preesm.codegen.model.Delimiter
import org.preesm.codegen.model.Direction
import org.preesm.codegen.model.PapifyAction

class MPPA2IOPrinter extends MPPA2ClusterPrinter {

	new() {
		// do not generate a main file
		super(true)
	}

	protected int usingPapify = 0;
	protected int sharedOnly = 1;
	protected int distributedOnly = 1;
	protected String peName = "";
	protected Map <String, Integer> coreNameToID = new LinkedHashMap();
	/**
	 * Temporary global var to ignore the automatic suppression of memcpy
	 * whose target and destination are identical.
	 */
	protected boolean IGNORE_USELESS_MEMCPY = true

	protected String local_buffer = "local_buffer"

	protected boolean IS_HIERARCHICAL = false

	protected String scratch_pad_buffer = ""

	protected long local_buffer_size = 0

	override printCoreBlockHeader(CoreBlock block)  {

	this.peName = block.name;

	var String printing = '''
		/**
		 * @file «block.name».c
		 * @generated by «this.class.simpleName»
		 * @date «new Date»
		 */

		/* system includes */
		#include <stdlib.h>
		#include <stdio.h>
		#include <stdint.h>
		#include <mOS_vcore_u.h>
		#include <mppa_noc.h>
		#include <mppa_rpc.h>
		#include <mppa_async.h>
		#include <pthread.h>
		#include <semaphore.h>
		#ifndef __nodeos__
		#include <utask.h>
		#endif

		/* user includes */
		#include "preesm_gen_mppa.h"

		extern void *__wrap_memset(void *s, int c, size_t n);
		extern void *__wrap_memcpy(void *dest, const void *src, size_t n);

		#define memset __wrap_memset
		#define memcpy __wrap_memcpy

		«IF (this.distributedOnly == 0)»
			extern mppa_async_segment_t shared_segment;
		«ENDIF»
		«IF (this.sharedOnly == 0 && this.distributedOnly == 1)»
			extern mppa_async_segment_t distributed_segment[PREESM_NB_CLUSTERS + PREESM_IO_USED];
		«ENDIF»

		/* Scratchpad buffer ptr (will be malloced) */
		char *local_buffer = NULL;
		/* Scratchpad buffer size */
		int local_buffer_size = 0;

	'''
	return printing;

	}

	override printFiniteLoopBlockHeader(FiniteLoopBlock block2) '''
	«{
	 	IS_HIERARCHICAL = true
	"\t"}»
	{ // Begin the hierarchical actor
«««		«{
«««		var gets = ""
«««		var local_offset = 0L;
«««			/* go through eventual out param first because of foot FiniteLoopBlock */
«««			for(param : block2.outBuffers){
«««				var b = param.container;
«««				var offset = param.offset;
«««				while(b instanceof SubBuffer){
«««					offset += b.offset;
«««					b = b.container;
«««				}
«««				/* put out buffer here */
«««				if(b.name == "Shared"){
«««					gets += "void *" + param.name + " = local_buffer+" + local_offset +";\n";
«««					local_offset += param.typeSize * param.size;
«««				}
«««			}
«««			for(param : block2.inBuffers){
«««				var b = param.container;
«««				var offset = param.offset;
«««				while(b instanceof SubBuffer){
«««					offset += b.offset;
«««					b = b.container;
«««				}
«««				//System.out.print("===> " + b.name + "\n");
«««				if(b.name == "Shared"){
«««					gets += "void *" + param.name + " = local_buffer+" + local_offset +";\n";
«««					gets += "	{\n"
«««					gets += "		if(mppa_async_get(local_buffer + " + local_offset + ",";
«««					gets += " &shared_segment,";
«««					//gets += "	" + b.name + " + " + offset + ",\n";
«««					gets += " /* Shared + */ " + offset + ",";
«««					gets += " " + param.typeSize * param.size + ",";
«««					gets += " NULL) != 0){\n";
«««					gets += "			assert(0 && \"mppa_async_get\\n\");\n";
«««					gets += "		}\n";
«««					gets += "	}\n"
«««					local_offset += param.typeSize * param.size;
«««					//System.out.print("==> " + b.name + " " + param.name + " size " + param.size + " port_name "+ port.getName + "\n");
«««				}
«««			}
«««
«««			gets += "int " + block2.iter.name + ";\n"
«««			if(block2.nbIter > 1 && this.sharedOnly == 0){
«««				gets += "#pragma omp parallel for private(" + block2.iter.name + ")\n"
«««			}
«««			gets += "for(" + block2.iter.name + "=0;" + block2.iter.name +"<" + block2.nbIter + ";" + block2.iter.name + "++) {"
«««
«««			if(local_offset > local_buffer_size)
«««				local_buffer_size = local_offset
«««	gets}»
«««			
			'''

	override printFiniteLoopBlockFooter(FiniteLoopBlock block2) '''
			}// End the for loop
«««		«{
«««				var puts = ""
«««				var local_offset = 0L;
«««				for(param : block2.outBuffers){
«««					var b = param.container
«««					var offset = param.offset
«««					while(b instanceof SubBuffer){
«««						offset += b.offset;
«««						b = b.container;
«««						//System.out.print("Running through all buffer " + b.name + "\n");
«««					}
«««					//System.out.print("===> " + b.name + "\n");
«««					if(b.name == "Shared"){
«««						puts += "	{\n"
«««						puts += "		if(mppa_async_put(local_buffer + " + local_offset + ",";
«««						puts += " &shared_segment,";
«««						puts += " /* Shared + */" + offset + ",";
«««						puts += " " + param.typeSize * param.size + ",";
«««						puts += " NULL) != 0){\n";
«««						puts += "			assert(0 && \"mppa_async_put\\n\");\n";
«««						puts += "		}\n";
«««						puts += "	}\n"
«««						local_offset += param.typeSize * param.size;
«««						//System.out.print("==> " + b.name + " " + param.name + " size " + param.size + " port_name "+ port.getName + "\n");
«««					}
«««				}
«««				if(local_offset > local_buffer_size)
«««					local_buffer_size = local_offset
«««			puts}»
«««		«{
«««			 	IS_HIERARCHICAL = false
«««			""}»
		}
	'''
	override printBufferDefinition(Buffer buffer) {
		var result = '''
		«IF buffer.name == "Shared"»
		//#define Shared ((char*)0x10000000ULL) 	/* Shared buffer in DDR */
		«ELSE»
		«buffer.type» «buffer.name»[«buffer.size»] __attribute__ ((aligned(64))); // «buffer.comment» size:= «buffer.size»*«buffer.type» aligned on data cache line
		int local_memory_size = «buffer.size»;
		«ENDIF»
		'''
		return result;
	}
		override printCoreLoopBlockFooter(LoopBlock block2) '''

				/* commit local changes to the global memory */
				//pthread_barrier_wait(&iter_barrier); /* barrier to make sure all threads have commited data in smem */

				mppa_rpc_barrier(1,2);
				mppa_rpc_barrier(1,2);
			}
			return NULL;
		}
	'''


	override printPapifyFunctionCall(PapifyFunctionCall papifyFunctionCall) {
		if(!(papifyFunctionCall.papifyType.equals(PapifyType.CONFIGACTOR))){
			papifyFunctionCall.parameters.remove(papifyFunctionCall.parameters.size-1);
		}
		var printing = '''
			«IF papifyFunctionCall.opening == true»
				#ifdef _PREESM_PAPIFY_MONITOR
			«ENDIF»
			«IF !(papifyFunctionCall.papifyType.equals(PapifyType.CONFIGACTOR))»
				«IF (papifyFunctionCall.papifyType.equals(PapifyType.CONFIGPE)) && this.usingClustering == 1»
					char namingArray[50];
					for(int i = 0; i < PREESM_NB_CORES_IO; i++){
						snprintf(namingArray, 50, "«this.peName»-PE%d", i);
						«papifyFunctionCall.name»(namingArray, «papifyFunctionCall.parameters.get(1).doSwitch», i); // «papifyFunctionCall.actorName»
					}
				«ELSE»
				«papifyFunctionCall.name»(«FOR param : papifyFunctionCall.parameters SEPARATOR ', '»«param.doSwitch»«ENDFOR», __k1_get_cpu_id()/*PE_id*/); // «papifyFunctionCall.actorName»
				«ENDIF»
			«ELSE»
			«papifyFunctionCall.name»(«FOR param : papifyFunctionCall.parameters SEPARATOR ', '»«param.doSwitch»«ENDFOR»); // «papifyFunctionCall.actorName»
			«ENDIF»
			«IF papifyFunctionCall.closing == true»
				#endif
			«ENDIF»
			'''
			return printing;
	}

	override CharSequence generatePreesmHeader(List<String> stdLibFiles) {
	    // 0- without the following class loader initialization, I get the following exception when running as Eclipse
	    // plugin:
	    // org.apache.velocity.exception.VelocityException: The specified class for ResourceManager
	    // (org.apache.velocity.runtime.resource.ResourceManagerImpl) does not implement
	    // org.apache.velocity.runtime.resource.ResourceManager; Velocity is not initialized correctly.
	    val ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
	    Thread.currentThread().setContextClassLoader(CPrinter.classLoader);

	    // 1- init engine
	    val VelocityEngine engine = new VelocityEngine();
	    engine.init();

	    // 2- init context
	    val VelocityContext context = new VelocityContext();
	    val findAllCHeaderFileNamesUsed = CHeaderUsedLocator.findAllCHeaderFileNamesUsed(getEngine.algo)
	    context.put("USER_INCLUDES", findAllCHeaderFileNamesUsed.map["#include \""+ it +"\""].join("\n"));

		var String constants = "#define NB_DESIGN_ELTS "+getEngine.archi.componentInstances.size+"\n";
		constants = constants.concat("#define PREESM_NB_CLUSTERS "+numClusters+"\n");
		constants = constants.concat("#define PREESM_IO_USED " + io_used + " \n");
		if(this.usingPapify == 1){
			constants = constants.concat("\n\n#ifdef _PREESM_PAPIFY_MONITOR\n#include \"eventLib.h\"\n#endif");
		}
	    context.put("CONSTANTS", constants);

		context.put("PREESM_INCLUDES", stdLibFiles.filter[it.endsWith(".h")].map["#include \""+ it +"\""].join("\n"));

	    // 3- init template reader
	    val String templateLocalPath = "templates/c/preesm_gen.h";
	    val URL mainTemplate = PreesmResourcesHelper.instance.resolve(templateLocalPath, this.class);
	    var InputStreamReader reader = null;
	    try {
	      reader = new InputStreamReader(mainTemplate.openStream());
	    } catch (IOException e) {
	      throw new PreesmRuntimeException("Could not locate main template [" + templateLocalPath + "].", e);
	    }

	    // 4- init output writer
	    val StringWriter writer = new StringWriter();

	    engine.evaluate(context, writer, "org.apache.velocity", reader);

	    // 99- set back default class loader
	    Thread.currentThread().setContextClassLoader(oldContextClassLoader);

	    return writer.getBuffer().toString();
	}
	override createSecondaryFiles(List<Block> printerBlocks, Collection<Block> allBlocks) {
		val result = super.createSecondaryFiles(printerBlocks, allBlocks);
		result.remove("cluster_main.c");
		result.remove("host_main.c");
		if (generateMainFile()) {
			result.put("io_main.c", printMainIO(printerBlocks));
		}
		return result
	}

	override String printMainIO(List<Block> printerBlocks) '''
		/**
		 * @file io_main.c
		 * @generated by «this.class.simpleName»
		 * @date «new Date»
		 *
		 */
		/*
		 * Copyright (C) 2016 Kalray SA.
		 *
		 * All rights reserved.
		 */

		#include <stdio.h>
		#include <stdlib.h>
		#include "mppa_boot_args.h"
		#include <mppa_power.h>
		#include <assert.h>
		#include "mppa_bsp.h"
		#include <utask.h>
		#include <pcie_queue.h>
		#include <mppa_rpc.h>
		#include <mppa_remote.h>
		#include <mppa_async.h>
		#include <HAL/hal/board/boot_args.h>
		#include "preesm_gen_mppa.h"
		«IF (this.distributedOnly == 0)»
		/* Shared Segment ID */
		mppa_async_segment_t shared_segment;
		«ENDIF»
		«IF (this.sharedOnly == 0 && this.distributedOnly == 1)»
		mppa_async_segment_t distributed_segment[PREESM_NB_CLUSTERS + PREESM_IO_USED];
		extern int local_memory_size;
		«ENDIF»

		static utask_t t;
		static mppadesc_t pcie_fd = 0;
		/* extern reference of generated code */
		«FOR io : printerBlocks.toSet»
			«IF (io instanceof CoreBlock)»
				extern void *computationTask_«io.name»(void *arg);
			«ENDIF»
		«ENDFOR»
		/* extern reference of shared memories */
		«FOR io : printerBlocks.toSet»
			«IF (io instanceof CoreBlock)»
				extern char *«io.name»;
			«ENDIF»
		«ENDFOR»

		int
		main(int argc __attribute__ ((unused)), char *argv[] __attribute__ ((unused)))
		{
			int id;
			int j;
			int ret ;

			if(__k1_spawn_type() == __MPPA_PCI_SPAWN){
				#if 1
				long long *ptr = (void*)(uintptr_t)Shared;
				long long i;
				for(i=0;i<(long long)((1<<30ULL)/sizeof(long long));i++)
				{
					ptr[i] = -1LL;
				}
				__builtin_k1_wpurge();
				__builtin_k1_fence();
				mOS_dinval();
				#endif
			}

			if (__k1_spawn_type() == __MPPA_PCI_SPAWN) {
				pcie_fd = pcie_open(0);
					ret = pcie_queue_init(pcie_fd);
					assert(ret == 0);
			}

			if(mppa_rpc_server_init(	3 /* rm where to run server */,
									0 /* offset ddr */,
									PREESM_NB_CLUSTERS /* nb_cluster to serve*/) != 0){
				assert(0 && "mppa_rpc_server_init\n");
			}
			if(mppa_async_server_init() != 0){
				assert(0 && "mppa_async_server_init\n");
			}
			if(mppa_remote_server_init(pcie_fd, PREESM_NB_CLUSTERS) != 0){
				assert(0 && "mppa_remote_server_init\n");
			}
			if (mppa_remote_server_enable_scall() != 0){
				assert(0 && "mppa_remote_server_enable_scall\n");
			}
			if(utask_start_pe(&t, (void*)mppa_rpc_server_start, NULL, 3) != 0){
				assert(0 && "utask_create\n");
			}

			«IF this.usingPapify == 1»
				#ifdef _PREESM_PAPIFY_MONITOR
				event_init();
				#endif
			«ENDIF»
			«IF (this.distributedOnly == 0)»
				if(mppa_async_segment_create(&shared_segment, SHARED_SEGMENT_ID, (void*)(uintptr_t)Shared, 1024*1024*1024, 0, 0, NULL) != 0){
					assert(0 && "mppa_async_segment_create\n");
				}
			«ENDIF»
			«IF (this.sharedOnly == 0 && this.distributedOnly == 1)»
				«FOR io : printerBlocks.toSet»
					«IF (io instanceof CoreBlock)»
						if(mppa_async_segment_create(&distributed_segment[PREESM_NB_CLUSTERS], INTERCC_BASE_SEGMENT_ID+PREESM_NB_CLUSTERS, (void*)&«io.name», local_memory_size, 0, 0, NULL) != 0){
							assert(0 && "mppa_async_segment_create\n");
						}
					«ENDIF»
				«ENDFOR»
			«ENDIF»
			for( j = 0 ; j < PREESM_NB_CLUSTERS ; j++ ) {

				char elf_name[30];
				sprintf(elf_name, "cluster%d_bin", j);
				id = mppa_power_base_spawn(j, elf_name, NULL, NULL, MPPA_POWER_SHUFFLING_ENABLED);
				if (id < 0)
					return -2;
			}
			// init comm
			communicationInit();
			mppa_rpc_barrier(1, 2);
			«IF (this.sharedOnly == 0 && this.distributedOnly == 1)»
				int i;
				for(i = 0; i < PREESM_NB_CLUSTERS; i++){
					if(mppa_async_segment_clone(&distributed_segment[i], INTERCC_BASE_SEGMENT_ID+i, NULL, 0, NULL) != 0){
						assert(0 && "mppa_async_segment_clone\n");
					}
				}
				mppa_rpc_barrier(1, 2);
			«ENDIF»
			computationTask_IO(NULL);
			int err;
			for( j = 0 ; j < PREESM_NB_CLUSTERS ; j++ ) {
			    mppa_power_base_waitpid (j, &err, 0);
			}

			«IF this.usingPapify == 1»
				#ifdef _PREESM_PAPIFY_MONITOR
				event_destroy();
				#endif
			«ENDIF»
			if (__k1_spawn_type() == __MPPA_PCI_SPAWN) {
				pcie_queue_barrier(pcie_fd, 0, &ret);
				pcie_queue_exit(pcie_fd, ret, NULL);
			}
			return 0;
		}

	'''
	override printPapifyActionParam(PapifyAction action) '''&«action.name»'''

	override printSharedMemoryCommunication(SharedMemoryCommunication communication) '''
		«communication.direction.toString.toLowerCase»«communication.delimiter.toString.toLowerCase.toFirstUpper»(«IF (communication.
			direction == Direction::SEND && communication.delimiter == Delimiter::START) ||
			(communication.direction == Direction::RECEIVE && communication.delimiter == Delimiter::END)»«{
			var coreID = if (communication.direction == Direction::SEND) {
					coreNameToID.get(communication.receiveStart.coreContainer.name)
					//communication.receiveStart.coreContainer.coreID
				} else {
					coreNameToID.get(communication.sendStart.coreContainer.name)
					//communication.sendStart.coreContainer.coreID
				}
			var ret = coreID
			ret
		}»«ENDIF»); // «communication.sendStart.coreContainer.name» > «communication.receiveStart.coreContainer.name»: «communication.
			data.doSwitch»
	'''
	override preProcessing(List<Block> printerBlocks, Collection<Block> allBlocks){
		var Set<String> coresNames = new LinkedHashSet<String>();
		for (cluster : allBlocks){
			if (cluster instanceof CoreBlock) {
				coresNames.add(cluster.name);
			}
		}
		for (cluster : allBlocks){
			if (cluster instanceof CoreBlock) {
				if(!cluster.loopBlock.codeElts.empty){
					if(cluster.coreType.equals("MPPA2Cluster")){
						numClusters = numClusters + 1;
						clusterToSync = cluster.coreID;
					}
					else if(cluster.coreType.equals("MPPA2IO")){
						io_used = 1;
					}
					coreNameToID.put(cluster.name, coreNameToID.size);
					for(CodeElt codeElt : cluster.loopBlock.codeElts){
						if(codeElt instanceof PapifyFunctionCall){
							this.usingPapify = 1;
						} else if(codeElt instanceof FiniteLoopBlock){
							this.usingClustering = 1;
						}
					}
	       		 	var EList<Variable> definitions = cluster.getDefinitions();
	       		 	var EList<Variable> declarations = cluster.getDeclarations();
	       		 	for(Variable variable : definitions){
	       		 		if(variable instanceof Buffer){
	       		 			if(variable.name.equals("Shared")){
								this.distributedOnly = 0;
	       		 			}else if(coresNames.contains(variable.name)){
								this.sharedOnly = 0;
	       		 			}
	       		 		}
	       		 	}
	       		 	for(Variable variable : declarations){
	       		 		if(variable instanceof Buffer){
	       		 			if(variable.name.equals("Shared")){
								this.distributedOnly = 0;
	       		 			}else if(coresNames.contains(variable.name)){
								this.sharedOnly = 0;
	       		 			}
	       		 		}
	       		 	}
       		 	}
			}
		}
		local_buffer_size = 0;
	}
}
