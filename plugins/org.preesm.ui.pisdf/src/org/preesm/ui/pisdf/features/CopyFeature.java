/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2017 - 2020) :
 *
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2017 - 2019)
 * Julien Heulot [julien.heulot@insa-rennes.fr] (2020)
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
package org.preesm.ui.pisdf.features;

import java.util.LinkedList;
import java.util.List;
import org.eclipse.emf.ecore.impl.EObjectImpl;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.ICopyContext;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.ui.features.AbstractCopyFeature;
import org.preesm.model.pisdf.AbstractVertex;
import org.preesm.model.pisdf.Configurable;
import org.preesm.model.pisdf.Delay;
import org.preesm.model.pisdf.PiGraph;

/**
 * Graphiti feature that implements the Copy action for PiMM vertices.
 *
 * @author anmorvan
 *
 */
public class CopyFeature extends AbstractCopyFeature {

  /**
   * Structural class to store the copied elements. The EObject inheritance is used for enabling the Graphiti clipboard
   * (only stores EObjects).
   */
  public static class VertexCopy extends EObjectImpl {
    private int              originalX;
    private int              originalY;
    private PictogramElement originalPictogramElement;
    private Diagram          originalDiagram;
    private Configurable     originalVertex;
    private PiGraph          originalPiGraph;

    public int getOriginalX() {
      return originalX;
    }

    public void setOriginalX(int originalX) {
      this.originalX = originalX;
    }

    public int getOriginalY() {
      return originalY;
    }

    public void setOriginalY(int originalY) {
      this.originalY = originalY;
    }

    public PictogramElement getOriginalPictogramElement() {
      return originalPictogramElement;
    }

    public void setOriginalPictogramElement(PictogramElement originalPictogramElement) {
      this.originalPictogramElement = originalPictogramElement;
    }

    public Diagram getOriginalDiagram() {
      return originalDiagram;
    }

    public void setOriginalDiagram(Diagram originalDiagram) {
      this.originalDiagram = originalDiagram;
    }

    public Configurable getOriginalVertex() {
      return originalVertex;
    }

    public void setOriginalVertex(Configurable originalVertex) {
      this.originalVertex = originalVertex;
    }

    public PiGraph getOriginalPiGraph() {
      return originalPiGraph;
    }

    public void setOriginalPiGraph(PiGraph originalPiGraph) {
      this.originalPiGraph = originalPiGraph;
    }
  }

  public CopyFeature(final IFeatureProvider fp) {
    super(fp);
  }

  @Override
  public void copy(final ICopyContext context) {
    final PictogramElement[] pes = context.getPictogramElements();

    final List<VertexCopy> copies = new LinkedList<>();

    for (final PictogramElement pe : pes) {
      final Object bo = getBusinessObjectForPictogramElement(pe);
      if (bo instanceof AbstractVertex) {
        final VertexCopy vertexCopy = new VertexCopy();
        vertexCopy.setOriginalDiagram(getDiagram());
        vertexCopy.setOriginalPictogramElement(pe);
        vertexCopy.setOriginalPiGraph((PiGraph) getDiagram().getLink().getBusinessObjects().get(0));
        vertexCopy.setOriginalVertex((Configurable) bo);
        vertexCopy.setOriginalX(pe.getGraphicsAlgorithm().getX());
        vertexCopy.setOriginalY(pe.getGraphicsAlgorithm().getY());
        copies.add(vertexCopy);
      }

    }
    // put all business objects to the clipboard
    final VertexCopy[] array = copies.toArray(new VertexCopy[copies.size()]);
    putToClipboard(array);
  }

  @Override
  public boolean canCopy(final ICopyContext context) {
    final PictogramElement[] pes = context.getPictogramElements();
    if ((pes == null) || (pes.length == 0)) { // nothing selected
      return false;
    }
    // return true, if all selected elements are a Vertices (cannot copy edges)
    for (final PictogramElement pe : pes) {
      final Object bo = getBusinessObjectForPictogramElement(pe);
      if (!(bo instanceof AbstractVertex) && !(bo instanceof Delay)) {
        return false;
      }
    }
    return true;
  }

}
