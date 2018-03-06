/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2017 - 2018) :
 *
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2018)
 * Hamza Deroui <hamza.deroui@insa-rennes.fr> (2017)
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
package org.ietr.preesm.throughput.tools.transformers;

import java.util.ArrayList;
import org.ietr.dftools.algorithm.model.sdf.SDFEdge;
import org.ietr.dftools.algorithm.model.sdf.SDFGraph;
import org.ietr.dftools.algorithm.model.sdf.types.SDFIntEdgePropertyType;
import org.ietr.preesm.throughput.tools.helpers.Stopwatch;

/**
 * @author hderoui
 *
 *         This class implements srSDF conversions algorithms : srSDF to HSDF graph and DAG.
 */
public abstract class SrSDFTransformer {

  /**
   * Converts a srSDF graph to an HSDF graph
   * 
   * @param srSDF
   *          graph
   * @return HSDF graph
   */
  public static SDFGraph convertToHSDF(SDFGraph srSDF) {
    Stopwatch timer = new Stopwatch();
    timer.start();

    // clone the srSDF
    SDFGraph hsdf_graph = srSDF.clone();
    hsdf_graph.setName(srSDF.getName() + "_HSDF");

    // for each edge set cons=prod=1 and delay=delay/prod
    for (SDFEdge edge : hsdf_graph.edgeSet()) {
      int delay = edge.getDelay().intValue() / edge.getProd().intValue();
      edge.setProd(new SDFIntEdgePropertyType(1));
      edge.setCons(new SDFIntEdgePropertyType(1));
      edge.setDelay(new SDFIntEdgePropertyType(delay));
    }

    timer.stop();
    System.out.println("SrSDF graph converted to HSDF graph in " + timer.toString());
    return hsdf_graph;
  }

  /**
   * Converts a srSDF graph to a DAG graph
   * 
   * @param srSDF
   *          graph
   * @return DAG
   */
  public static SDFGraph convertToDAG(SDFGraph srSDF) {
    Stopwatch timer = new Stopwatch();
    timer.start();

    // clone the srSDF
    SDFGraph dag = srSDF.clone();
    dag.setName(srSDF.getName() + "_DAG");

    // save the list of edges
    ArrayList<SDFEdge> edgeList = new ArrayList<SDFEdge>(dag.edgeSet().size());
    for (SDFEdge edge : dag.edgeSet()) {
      edgeList.add(edge);
    }

    // remove edges with delays
    for (SDFEdge edge : edgeList) {
      if (edge.getDelay().intValue() != 0) {
        dag.removeEdge(edge);
      }
    }

    timer.stop();
    System.out.println("SrSDF graph converted to DAG in " + timer.toString());
    return dag;
  }

}
