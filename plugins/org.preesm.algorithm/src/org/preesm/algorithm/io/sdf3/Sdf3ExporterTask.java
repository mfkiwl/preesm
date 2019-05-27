/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2008 - 2019) :
 *
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2017 - 2019)
 * Clément Guy <clement.guy@insa-rennes.fr> (2014 - 2015)
 * Karol Desnos <karol.desnos@insa-rennes.fr> (2014)
 * Maxime Pelcat <maxime.pelcat@insa-rennes.fr> (2008 - 2012)
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
package org.preesm.algorithm.io.sdf3;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.preesm.algorithm.model.sdf.SDFGraph;
import org.preesm.commons.doc.annotations.Parameter;
import org.preesm.commons.doc.annotations.Port;
import org.preesm.commons.doc.annotations.PreesmTask;
import org.preesm.commons.doc.annotations.Value;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.commons.files.WorkspaceUtils;
import org.preesm.model.scenario.PreesmScenario;
import org.preesm.model.slam.Design;
import org.preesm.workflow.elements.Workflow;
import org.preesm.workflow.implement.AbstractTaskImplementation;
import org.preesm.workflow.implement.AbstractWorkflowNodeImplementation;

/**
 * The Class Sdf3Exporter.
 */
@PreesmTask(id = "org.ietr.preesm.algorithm.exportSdf3Xml.Sdf3Exporter", name = "SDF3 Exporter",
    category = "Graph Exporters",

    inputs = { @Port(name = "SDF", type = SDFGraph.class), @Port(name = "architecture", type = Design.class),
        @Port(name = "scenario", type = PreesmScenario.class) },

    shortDescription = "Export a *.xml file conforming the SDF For Free (SDF3) format.",

    description = "This task generates SDF3 code modeling the given SDF graph. SDF modeling in SDF3 follow the "
        + "specification introduced by Stuijk et al. in [1].\n\n"
        + "Known Limitations: Here is a list of known limitations of the SDF3 importation process: Only SDF"
        + " graphs can be imported, Actors of the SDF cannot be implemented on more than one processor type,"
        + " Timings cannot depend on parameters since SDF3 does not support parameterized SDF.",

    parameters = { @Parameter(name = "path",
        description = "Path of the exported *.xml file. If the specified directory does not exist, it will not "
            + "be created.",
        values = { @Value(name = "path/in/proj/name.xml",
            effect = "Path within the Preesm project containing the workflow where the ”SDF3 Exporter” task is"
                + " instantiated.\n" + "\n"
                + "Exported SDF graph will be named using the string with the xml extension at the end of the "
                + "given path. If a graph with this name already exists in the given path, it will be "
                + "overwritten.\n" + "\n" + "Example: **Code/generated/sdf3/myexport.xml**") }) },

    seeAlso = { "**[1]**: S. Stuijk, M. Geilen, and T. Basten. Sdf3: Sdf for free. In Sixth International Conference "
        + "on Application of Concurrency to System Design (ACSD’06), pages 276–278, June 2006." })
@Deprecated
public class Sdf3ExporterTask extends AbstractTaskImplementation {

  /** The Constant PARAM_PATH. */
  public static final String PARAM_PATH = "path";

  /** The Constant VALUE_PATH_DEFAULT. */
  public static final String VALUE_PATH_DEFAULT = "./Code/SDF3/graph.xml";

  /*
   * (non-Javadoc)
   *
   * @see org.ietr.dftools.workflow.implement.AbstractTaskImplementation#execute(java.util.Map, java.util.Map,
   * org.eclipse.core.runtime.IProgressMonitor, java.lang.String, org.ietr.dftools.workflow.elements.Workflow)
   */
  @Override
  public Map<String, Object> execute(final Map<String, Object> inputs, final Map<String, String> parameters,
      final IProgressMonitor monitor, final String nodeName, final Workflow workflow) {

    // Retrieve the inputs
    final SDFGraph sdf = (SDFGraph) inputs.get(AbstractWorkflowNodeImplementation.KEY_SDF_GRAPH);
    final PreesmScenario scenario = (PreesmScenario) inputs.get(AbstractWorkflowNodeImplementation.KEY_SCENARIO);
    final Design archi = (Design) inputs.get(AbstractWorkflowNodeImplementation.KEY_ARCHITECTURE);
    // Locate the output file
    final String sPath = WorkspaceUtils.getAbsolutePath(parameters.get("path"), workflow.getProjectName());
    final IPath path = new Path(sPath);

    Sdf3ExporterTask.printSDFGraphToSDF3File(sdf, scenario, archi, path);

    return new LinkedHashMap<>();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.ietr.dftools.workflow.implement.AbstractTaskImplementation#getDefaultParameters()
   */
  @Override
  public Map<String, String> getDefaultParameters() {
    final Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put(Sdf3ExporterTask.PARAM_PATH, Sdf3ExporterTask.VALUE_PATH_DEFAULT);
    return parameters;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.ietr.dftools.workflow.implement.AbstractWorkflowNodeImplementation#monitorMessage()
   */
  @Override
  public String monitorMessage() {
    return "Exporting SDF3 File";
  }

  /**
   * Prints the SDF graph to SDF 3 file.
   *
   * @param sdf
   *          the sdf
   * @param scenario
   *          the scenario
   * @param architecture
   *          the architecture
   * @param path
   *          the path
   */
  public static void printSDFGraphToSDF3File(final SDFGraph sdf, final PreesmScenario scenario,
      final Design architecture, IPath path) {
    // Create the exporter
    final Sdf3Printer exporter = new Sdf3Printer(sdf, architecture);

    try {
      if ((path.getFileExtension() == null) || !path.getFileExtension().equals("xml")) {
        path = path.addFileExtension("xml");
      }
      final IWorkspace workspace = ResourcesPlugin.getWorkspace();
      final IFile iFile = workspace.getRoot().getFile(path);
      final File file = new File(iFile.getRawLocation().toOSString());
      final File parentFile = file.getParentFile();
      if (parentFile != null) {
        parentFile.mkdirs();
      }

      // Write the result into the text file
      exporter.write(file);
      workspace.getRoot().touch(null);

    } catch (final CoreException e) {
      throw new PreesmRuntimeException("Could not export SDF", e);
    }
  }
}
