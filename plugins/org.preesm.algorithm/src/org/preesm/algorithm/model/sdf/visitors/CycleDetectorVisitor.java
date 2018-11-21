/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2018) :
 *
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2018)
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
package org.preesm.algorithm.model.sdf.visitors;

import java.util.ArrayList;
import java.util.List;
import org.jgrapht.alg.cycle.CycleDetector;
import org.preesm.algorithm.DFToolsAlgoException;
import org.preesm.algorithm.model.sdf.SDFAbstractVertex;
import org.preesm.algorithm.model.sdf.SDFEdge;
import org.preesm.algorithm.model.sdf.SDFGraph;
import org.preesm.algorithm.model.sdf.SDFVertex;
import org.preesm.algorithm.model.visitors.IGraphVisitor;
import org.preesm.algorithm.model.visitors.SDF4JException;

/**
 * Visitor to use to detect cycle in a hierarchical graph.
 *
 * @author jpiat
 */
public class CycleDetectorVisitor implements IGraphVisitor<SDFGraph, SDFVertex, SDFEdge> {

  /** The contains cycles. */
  private final List<SDFGraph> containsCycles = new ArrayList<>();

  /** The has cycle. */
  private boolean hasCycle = true;

  /**
   * Detect cycles in the given graph.
   *
   * @param graph
   *          The graph to visit
   * @return true if the graph has cycles
   */
  public boolean detectCyles(final SDFGraph graph) {
    try {
      graph.accept(this);
    } catch (final SDF4JException e) {
      throw new DFToolsAlgoException("Could not verify graph", e);
    }
    return this.hasCycle;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.ietr.dftools.algorithm.model.visitors.IGraphVisitor#visit(org.ietr.dftools.algorithm.model.AbstractGraph)
   */
  @Override
  public void visit(final SDFGraph sdf) throws SDF4JException {
    boolean currentGraphHasCycle;
    final CycleDetector<SDFAbstractVertex, SDFEdge> detector = new CycleDetector<>(sdf);
    currentGraphHasCycle = detector.detectCycles();
    if (currentGraphHasCycle) {
      this.containsCycles.add(sdf);
    }
    this.hasCycle = this.hasCycle && currentGraphHasCycle;
    for (final SDFAbstractVertex vertex : sdf.vertexSet()) {
      vertex.accept(this);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.ietr.dftools.algorithm.model.visitors.IGraphVisitor#visit(org.ietr.dftools.algorithm.model.AbstractVertex)
   */
  @Override
  public void visit(final SDFVertex sdfVertex) throws SDF4JException {
    if (sdfVertex.getGraphDescription() != null) {
      sdfVertex.getGraphDescription().accept(this);
    }
  }

}
