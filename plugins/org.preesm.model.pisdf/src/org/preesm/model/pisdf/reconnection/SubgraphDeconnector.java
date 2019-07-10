package org.preesm.model.pisdf.reconnection;

import java.util.Collection;
import java.util.function.Supplier;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.Actor;
import org.preesm.model.pisdf.ConfigInputPort;
import org.preesm.model.pisdf.ConfigOutputPort;
import org.preesm.model.pisdf.DataInputPort;
import org.preesm.model.pisdf.DataOutputPort;
import org.preesm.model.pisdf.DataPort;
import org.preesm.model.pisdf.Dependency;
import org.preesm.model.pisdf.Fifo;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.pisdf.PiSDFRefinement;
import org.preesm.model.pisdf.Port;
import org.preesm.model.pisdf.factory.PiMMUserFactory;

/**
 *
 * @author anmorvan
 *
 */
public class SubgraphDeconnector {

  private SubgraphDeconnector() {
    // forbid instantiation
  }

  /**
   *
   */
  public static final void deconnectSubGraphs(final PiGraph graph) {
    for (final PiGraph child : graph.getChildrenGraphs()) {
      deconnect(graph, child);
    }
  }

  /**
   *
   */
  private static final void deconnect(final PiGraph parentGraph, final PiGraph childGraph) {

    final Actor actorToIntroduce;

    final Actor originalActor = SubgraphOriginalActorTracker.getOriginalActor(childGraph);
    if (originalActor == null) {
      // instantiate a new actor on the fly if the PiGraph was introduced during some transformation
      actorToIntroduce = SubgraphDeconnector.instantiateNewActor(parentGraph, childGraph);
    } else {
      actorToIntroduce = originalActor;
    }

    parentGraph.removeActor(childGraph);
    parentGraph.addActor(actorToIntroduce);
  }

  private static Actor instantiateNewActor(final PiGraph parentGraph, final PiGraph childGraph) {
    final Actor actorToIntroduce;
    actorToIntroduce = PiMMUserFactory.instance.createActor();
    final PiSDFRefinement refinment = SubgraphDeconnector.createRefinement(parentGraph, childGraph);
    actorToIntroduce.setName(childGraph.getName());
    actorToIntroduce.setRefinement(refinment);

    copyPorts(actorToIntroduce, childGraph);
    deconnectPiGraph(actorToIntroduce, childGraph);

    return actorToIntroduce;
  }

  private static final void copyPorts(final AbstractActor receiving, final AbstractActor childGraph) {
    for (final DataInputPort dip : childGraph.getDataInputPorts()) {
      final DataInputPort dipCopy = PiMMUserFactory.instance.createDataInputPort();
      dipCopy.setName(dip.getName());
      dipCopy.setAnnotation(dip.getAnnotation());
      dipCopy.setExpression(dip.getExpression().getExpressionAsString());
      receiving.getDataInputPorts().add(dipCopy);
    }

    for (final DataOutputPort dop : childGraph.getDataOutputPorts()) {
      final DataOutputPort dopCopy = PiMMUserFactory.instance.createDataOutputPort();
      dopCopy.setName(dop.getName());
      dopCopy.setAnnotation(dop.getAnnotation());
      dopCopy.setExpression(dop.getExpression().getExpressionAsString());
      receiving.getDataOutputPorts().add(dopCopy);
    }

    for (final ConfigInputPort dip : childGraph.getConfigInputPorts()) {
      final ConfigInputPort dipCopy = PiMMUserFactory.instance.createConfigInputPort();
      dipCopy.setName(dip.getName());
      receiving.getConfigInputPorts().add(dipCopy);
    }

    for (final ConfigOutputPort dip : childGraph.getConfigOutputPorts()) {
      final ConfigOutputPort dipCopy = PiMMUserFactory.instance.createConfigOutputPort();
      dipCopy.setName(dip.getName());
      receiving.getConfigOutputPorts().add(dipCopy);
    }

  }

  static <T extends Port> void copyPortsOfKind(final Collection<T> existingPorts, final Actor receiving,
      final Supplier<T> suplier) {
    for (final T port : existingPorts) {
      final T copy = suplier.get();
      copy.setName(port.getName());
      if (port instanceof DataPort) {
        final DataPort dataPort = (DataPort) port;
        final DataPort dataPortCopy = (DataPort) copy;
        dataPortCopy.setAnnotation(dataPort.getAnnotation());
        dataPortCopy.setExpression(dataPort.getExpression().getExpressionAsString());
      }
    }
  }

  private static void deconnectPiGraph(final Actor hierarchicalActor, final PiGraph subGraph) {
    SubgraphOriginalActorTracker.untrackOriginalActor(subGraph);

    SubgraphDeconnector.deconnectDataInputPorts(hierarchicalActor, subGraph);
    SubgraphDeconnector.deconnectDataOutputPorts(hierarchicalActor, subGraph);
    SubgraphDeconnector.deconnectConfigInputPorts(hierarchicalActor, subGraph);
    SubgraphDeconnector.deconnectConfigOutputPorts(hierarchicalActor, subGraph);
  }

  private static void deconnectConfigOutputPorts(final Actor hierarchicalActor, final PiGraph subGraph) {
    for (final ConfigOutputPort cop1 : subGraph.getConfigOutputPorts()) {
      for (final ConfigOutputPort cop2 : hierarchicalActor.getConfigOutputPorts()) {
        if (cop1.getName().equals(cop2.getName())) {
          for (final Dependency dep : cop1.getOutgoingDependencies()) {
            cop2.getOutgoingDependencies().add(dep);
            dep.setSetter(cop2);
          }
          break;
        }
      }
    }
  }

  private static void deconnectConfigInputPorts(final Actor hierarchicalActor, final PiGraph subGraph) {
    for (final ConfigInputPort topGraphActorConfigInputPort : subGraph.getConfigInputPorts()) {
      for (final ConfigInputPort subGraphConfigInputPorts : hierarchicalActor.getConfigInputPorts()) {
        if (topGraphActorConfigInputPort.getName().equals(subGraphConfigInputPorts.getName())) {
          final Dependency topGraphDep = topGraphActorConfigInputPort.getIncomingDependency();
          if (topGraphDep != null) {
            subGraphConfigInputPorts.setIncomingDependency(topGraphDep);
            topGraphDep.setGetter(subGraphConfigInputPorts);
          }
          break;
        }
      }
    }
  }

  private static void deconnectDataOutputPorts(final Actor hierarchicalActor, final PiGraph subGraph) {
    for (final DataOutputPort dop1 : subGraph.getDataOutputPorts()) {
      for (final DataOutputPort dop2 : hierarchicalActor.getDataOutputPorts()) {
        if (dop1.getName().equals(dop2.getName())) {
          final Fifo fifo = dop1.getOutgoingFifo();
          if (fifo != null) {
            dop2.setOutgoingFifo(fifo);
            fifo.setSourcePort(dop2);

            dop2.setExpression(dop1.getPortRateExpression().getExpressionAsString());
            dop2.setAnnotation(dop1.getAnnotation());
          }
          break;
        }
      }
    }
  }

  private static void deconnectDataInputPorts(final Actor hierarchicalActor, final PiGraph subGraph) {
    for (final DataInputPort dip1 : subGraph.getDataInputPorts()) {
      for (final DataInputPort dip2 : hierarchicalActor.getDataInputPorts()) {
        if (dip1.getName().equals(dip2.getName())) {
          final Fifo fifo = dip1.getIncomingFifo();
          if (fifo != null) {
            dip2.setIncomingFifo(fifo);
            fifo.setTargetPort(dip2);

            dip2.setExpression(dip1.getPortRateExpression().getExpressionAsString());
            dip2.setAnnotation(dip1.getAnnotation());
          }
          break;
        }
      }
    }
  }

  private static PiSDFRefinement createRefinement(final PiGraph parentGraph, final PiGraph childGraph) {
    final PiSDFRefinement refinment = PiMMUserFactory.instance.createPiSDFRefinement();
    if ((childGraph.getUrl() != null) && !childGraph.getUrl().isEmpty()) {
      refinment.setFilePath(childGraph.getUrl());
    } else {
      final String newUrlString = SubgraphDeconnector.generateRefinementURL(parentGraph, childGraph);
      childGraph.setUrl(newUrlString);
      refinment.setFilePath(newUrlString);
    }
    return refinment;
  }

  private static String generateRefinementURL(final PiGraph parentGraph, final PiGraph childGraph) {
    final String parentURL = parentGraph.getUrl();
    final IPath parentPath = new Path(parentURL);
    final String fileExtension = parentPath.getFileExtension();
    final String baseName = parentPath.removeFileExtension().lastSegment();
    final String newName = baseName + "_" + childGraph.getName() + "." + fileExtension;
    return parentPath.removeLastSegments(1).append(newName).toString();
  }

}
