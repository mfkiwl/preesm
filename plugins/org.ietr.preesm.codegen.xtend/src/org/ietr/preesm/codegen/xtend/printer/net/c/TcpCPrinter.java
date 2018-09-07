/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2018) :
 *
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2018)
 * Antoine Morvan <antoine.morvan.pro@gmail.com> (2018)
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
package org.ietr.preesm.codegen.xtend.printer.net.c;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.ietr.preesm.codegen.model.codegen.Block;
import org.ietr.preesm.codegen.model.codegen.CallBlock;
import org.ietr.preesm.codegen.model.codegen.CoreBlock;
import org.ietr.preesm.codegen.model.codegen.Delimiter;
import org.ietr.preesm.codegen.model.codegen.Direction;
import org.ietr.preesm.codegen.model.codegen.LoopBlock;
import org.ietr.preesm.codegen.model.codegen.SharedMemoryCommunication;
import org.ietr.preesm.codegen.model.codegen.Variable;
import org.ietr.preesm.codegen.xtend.CodegenPlugin;
import org.ietr.preesm.codegen.xtend.printer.c.CPrinter;
import org.ietr.preesm.codegen.xtend.task.CodegenException;
import org.ietr.preesm.utils.files.URLResolver;

/**
 *
 * @author anmorvan
 *
 */
public class TcpCPrinter extends CPrinter {

  @Override
  public CharSequence printDeclarationsHeader(List<Variable> list) {
    return "";
  }

  @Override
  public CharSequence printCoreInitBlockHeader(CallBlock callBlock) {
    final int coreID = ((CoreBlock) callBlock.eContainer()).getCoreID();
    StringBuilder ff = new StringBuilder();
    ff.append("#include \"socketcom.h\"\n");
    ff.append("void *computationThread_Core");
    ff.append(coreID);
    ff.append("(void *arg) {\n");

    ff.append("  int* socketFileDescriptors = (int*)arg;\n");
    ff.append("  int processingElementID = socketFileDescriptors[" + this.getEngine().getCodeBlocks().size() + "];\n");

    // ff.append(" int* socketFileDescriptors = (int*)arg;\n");

    ff.append("  \n" + "#ifdef _PREESM_TCP_DEBUG_\n" + "  printf(\"[TCP-DEBUG] Core" + coreID + " READY\\n\");\n"
        + "#endif\n\n");
    return ff.toString();
  }

  @Override
  public CharSequence printCoreLoopBlockHeader(LoopBlock block2) {
    final CoreBlock eContainer = (CoreBlock) block2.eContainer();

    final int coreID = eContainer.getCoreID();
    StringBuilder res = new StringBuilder();
    res.append("  int iterationCount = 0;\n");
    res.append("  while(1){\n");
    res.append("    iterationCount++;\n");
    res.append("#ifdef _PREESM_TCP_DEBUG_\n" + "    printf(\"[TCP-DEBUG] Core" + coreID
        + " iteration #%d - at barrier\\n\",iterationCount);\n" + "#endif\n");
    res.append("    preesm_barrier(socketFileDescriptors, " + coreID + ", " + this.getEngine().getCodeBlocks().size()
        + ");\n");
    res.append("#ifdef _PREESM_TCP_DEBUG_\n" + "    printf(\"[TCP-DEBUG] Core" + coreID
        + " iteration #%d - barrier passed\\n\",iterationCount);\n" + "#endif\n    \n    ");
    return res.toString();
  }

  @Override
  public CharSequence printSharedMemoryCommunication(SharedMemoryCommunication communication) {
    final StringBuilder functionCallBuilder = new StringBuilder("preesm_");

    final Direction direction = communication.getDirection();
    final int to = communication.getReceiveEnd().getCoreContainer().getCoreID();
    final int from = communication.getSendStart().getCoreContainer().getCoreID();
    switch (direction) {
      case SEND:
        functionCallBuilder.append("send_");
        break;
      case RECEIVE:
        functionCallBuilder.append("receive_");
        break;
      default:
        throw new UnsupportedOperationException("Unsupported [" + direction + "] communication direction.");
    }

    final Delimiter delimiter = communication.getDelimiter();
    switch (delimiter) {
      case START:
        functionCallBuilder.append("start");
        break;
      case END:
        functionCallBuilder.append("end");
        break;
      default:
        throw new UnsupportedOperationException("Unsupported [" + direction + "] communication direction.");
    }
    final int size = communication.getData().getSize();

    final String dataAddress = communication.getData().getName();

    functionCallBuilder.append("(" + from + ", " + to + ", socketFileDescriptors, " + dataAddress + ", " + size + ", \""
        + dataAddress + " " + size + "\"" + ");\n");

    return functionCallBuilder.toString();
  }

  @Override
  public String printMain(final List<Block> printerBlocks) {
    // 0- without the following class loader initialization, I get the following exception when running as Eclipse
    // plugin:
    // org.apache.velocity.exception.VelocityException: The specified class for ResourceManager
    // (org.apache.velocity.runtime.resource.ResourceManagerImpl) does not implement
    // org.apache.velocity.runtime.resource.ResourceManager; Velocity is not initialized correctly.
    final ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(TcpCPrinter.class.getClassLoader());

    // 1- init engine
    final VelocityEngine engine = new VelocityEngine();
    engine.init();

    // 2- init context
    final VelocityContext context = new VelocityContext();
    context.put("PREESM_DATE", new Date().toString());
    context.put("PREESM_PRINTER", this.getClass().getSimpleName());
    context.put("PREESM_NBTHREADS", printerBlocks.size());

    final List<String> threadFunctionNames = IntStream.range(0, printerBlocks.size())
        .mapToObj(i -> String.format("computationThread_Core%d", i)).collect(Collectors.toList());

    context.put("PREESM_THREAD_FUNCTIONS_DECLS",
        "void* " + String.join("(void *arg);\nvoid* ", threadFunctionNames) + "(void *arg);\n");

    context.put("PREESM_THREAD_FUNCTIONS", "&" + String.join(",&", threadFunctionNames));

    // 3- init template reader
    final String templateLocalURL = "templates/tcp/main.c";
    final URL mainTemplate = URLResolver.findFirstInPluginList(templateLocalURL, CodegenPlugin.BUNDLE_ID);
    InputStreamReader reader = null;
    try {
      reader = new InputStreamReader(mainTemplate.openStream());
    } catch (IOException e) {
      throw new CodegenException("Could not locate main template [" + templateLocalURL + "].", e);
    }

    // 4- init output writer
    StringWriter writer = new StringWriter();

    engine.evaluate(context, writer, "org.apache.velocity", reader);

    // 99- set back default class loader
    Thread.currentThread().setContextClassLoader(oldContextClassLoader);

    return writer.getBuffer().toString();
  }
}
