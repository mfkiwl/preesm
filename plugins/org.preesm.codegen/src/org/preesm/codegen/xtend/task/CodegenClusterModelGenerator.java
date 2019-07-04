package org.preesm.codegen.xtend.task;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.preesm.algorithm.clustering.ClusteringHelper;
import org.preesm.algorithm.mapper.model.MapperDAG;
import org.preesm.codegen.model.Block;
import org.preesm.codegen.model.Buffer;
import org.preesm.codegen.model.ClusterBlock;
import org.preesm.codegen.model.CodegenFactory;
import org.preesm.codegen.model.Constant;
import org.preesm.codegen.model.CoreBlock;
import org.preesm.codegen.model.FiniteLoopBlock;
import org.preesm.codegen.model.FunctionCall;
import org.preesm.codegen.model.IntVar;
import org.preesm.codegen.model.IteratedBuffer;
import org.preesm.codegen.model.LoopBlock;
import org.preesm.codegen.model.PortDirection;
import org.preesm.codegen.model.util.CodegenModelUserFactory;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.model.algorithm.schedule.Schedule;
import org.preesm.model.algorithm.schedule.SequentialActorSchedule;
import org.preesm.model.algorithm.schedule.SequentialHiearchicalSchedule;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.AbstractVertex;
import org.preesm.model.pisdf.Actor;
import org.preesm.model.pisdf.CHeaderRefinement;
import org.preesm.model.pisdf.ConfigInputInterface;
import org.preesm.model.pisdf.ConfigInputPort;
import org.preesm.model.pisdf.DataInputInterface;
import org.preesm.model.pisdf.DataInputPort;
import org.preesm.model.pisdf.DataOutputInterface;
import org.preesm.model.pisdf.DataOutputPort;
import org.preesm.model.pisdf.DataPort;
import org.preesm.model.pisdf.Direction;
import org.preesm.model.pisdf.Fifo;
import org.preesm.model.pisdf.FunctionArgument;
import org.preesm.model.pisdf.ISetter;
import org.preesm.model.pisdf.Parameter;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.pisdf.Port;
import org.preesm.model.pisdf.brv.BRVMethod;
import org.preesm.model.pisdf.brv.PiBRV;
import org.preesm.model.scenario.Scenario;
import org.preesm.model.slam.Design;
import org.preesm.workflow.elements.Workflow;

/**
 * @author dgageot
 *
 */
public class CodegenClusterModelGenerator extends AbstractCodegenModelGenerator {

  final Map<AbstractActor, Schedule> mapper;

  final Map<Fifo, Buffer> internalBufferMap;

  final Map<Fifo, Buffer> externalBufferMap;

  final Map<AbstractActor, IntVar> iterMap;

  Map<AbstractVertex, Long> repVector;

  final PiGraph piAlgo;

  /**
   * @param archi
   *          architecture
   * @param algo
   *          PiGraph algorithm
   * @param scenario
   *          scenario
   * @param workflow
   *          workflow
   * @param mapper
   *          schedule associated with cluster
   */
  public CodegenClusterModelGenerator(final Design archi, final PiGraph algo, final Scenario scenario,
      final Workflow workflow, final Map<AbstractActor, Schedule> mapper) {
    super(archi, new MapperDAG(algo), null, scenario, workflow);
    this.mapper = mapper;
    this.piAlgo = algo;
    this.internalBufferMap = new HashMap<>();
    this.externalBufferMap = new HashMap<>();
    this.iterMap = new HashMap<>();
    this.repVector = null;
  }

  @Override
  public List<Block> generate() {

    // Build CoreBlock
    CoreBlock cb = CodegenModelUserFactory.createCoreBlock();
    cb.setCoreID(0);
    cb.setName(scenario.getSimulationInfo().getMainOperator().getInstanceName());
    cb.setCoreType("x86");

    // Construct codegen model corresponding to cluster in
    // 1st step: Parse every cluster
    for (Entry<AbstractActor, Schedule> clusterSchedule : mapper.entrySet()) {
      this.repVector = PiBRV.compute((PiGraph) clusterSchedule.getKey(), BRVMethod.LCM);
      cb.getLoopBlock().getCodeElts().add(buildClusterBlockRec(clusterSchedule.getValue()));
    }

    cb.getDefinitions().addAll(internalBufferMap.values());
    List<Block> blockList = new LinkedList<>();
    blockList.add(cb);

    return blockList;
  }

  private final Block buildClusterBlockRec(final Schedule schedule) {
    Block outputBlock = null;

    if (schedule instanceof SequentialHiearchicalSchedule) {
      // If it's a sequential schedule i.e. cluster, fill information for ClusterBlock
      outputBlock = buildClusterBlock(schedule);
    } else if (schedule instanceof SequentialActorSchedule) {
      // If it's an actor firing, fill information for FunctionCall
      outputBlock = buildActorCall(schedule);
    }

    return outputBlock;
  }

  private final Block buildClusterBlock(Schedule schedule) {
    Block outputBlock = null;

    // Retrieve cluster actor
    PiGraph cluster = (PiGraph) ((SequentialHiearchicalSchedule) schedule).getAttachedActor();

    // Build and fill ClusterBlock
    ClusterBlock clusterBlock = CodegenFactory.eINSTANCE.createClusterBlock();
    clusterBlock.setName(cluster.getName());
    clusterBlock.setSchedule(ClusteringHelper.printScheduleRec(schedule));

    // If the cluster has to be repeated few times, build a FiniteLoopBlock
    if (schedule.getRepetition() > 1) {
      FiniteLoopBlock flb = CodegenFactory.eINSTANCE.createFiniteLoopBlock();
      IntVar iterator = CodegenFactory.eINSTANCE.createIntVar();
      iterator.setName("index_" + cluster.getName());
      // Register the iteration var for that specific actor/cluster
      iterMap.put(cluster, iterator);
      flb.setIter(iterator);
      flb.setNbIter((int) schedule.getRepetition());
      // Insert ClusterBlock inside FinitLoopBlock
      flb.getCodeElts().add(clusterBlock);
      // Output the FiniteLoopBlock incorporating the ClusterBlock
      outputBlock = flb;
    } else {
      // Output the ClusterBlock
      outputBlock = clusterBlock;
    }

    // Make memory allocation for internal buffer
    buildInternalClusterBuffer(cluster);
    // Make memory allocation for external buffer i.e. fifo that goes outside of the hierarchical actor of the cluster
    buildExternalClusterBuffer(cluster);

    // Call again buildClusterBlockRec to explore and build child
    for (Schedule e : schedule.getChildren()) {
      clusterBlock.getCodeElts().add(buildClusterBlockRec(e));
    }

    return outputBlock;
  }

  private final Block buildActorCall(Schedule schedule) {
    Block outputBlock = null;

    // Retrieve actor to fire
    Actor actor = (Actor) schedule.getActors().get(0);

    // Build FunctionCall
    FunctionCall functionCall = CodegenFactory.eINSTANCE.createFunctionCall();
    functionCall.setActorName(actor.getName());
    LoopBlock callSet = CodegenFactory.eINSTANCE.createLoopBlock();
    callSet.getCodeElts().add(functionCall);

    // If actors has to be repeated few times, build a FiniteLoopBlock
    if (schedule.getRepetition() > 1) {
      FiniteLoopBlock flb = CodegenFactory.eINSTANCE.createFiniteLoopBlock();
      IntVar iterator = CodegenFactory.eINSTANCE.createIntVar();
      iterator.setName("index_" + actor.getName());
      // Register the iteration var for that specific actor/cluster
      iterMap.put(actor, iterator);
      flb.setIter(iterator);
      flb.setNbIter((int) schedule.getRepetition());
      flb.getCodeElts().addAll(callSet.getCodeElts());
      // Output the FiniteLoopBlock incorporating the LoopBlock
      outputBlock = flb;
    } else {
      // Output the LoopBlock
      outputBlock = callSet;
    }

    // Retrieve Refinement from actor
    fillFunctionCallArgument(functionCall, actor);

    return outputBlock;
  }

  private final void fillFunctionCallArgument(FunctionCall functionCall, Actor actor) {
    // Retrieve Refinement from actor
    if (actor.getRefinement() instanceof CHeaderRefinement) {
      CHeaderRefinement cheader = (CHeaderRefinement) actor.getRefinement();
      // Retrieve function argument
      List<FunctionArgument> arguments = cheader.getLoopPrototype().getArguments();
      // Retrieve function name
      functionCall.setName(cheader.getLoopPrototype().getName());

      // Associate argument with buffer
      for (FunctionArgument a : arguments) {
        // Search for the corresponding port into actor ports list
        Port associatedPort = actor.getAllPorts().stream().filter(x -> x.getName().contentEquals(a.getName()))
            .collect(Collectors.toList()).get(0);

        if (associatedPort instanceof DataPort) {
          addDataPortParameter(functionCall, actor, (DataPort) associatedPort, a);
        } else if (associatedPort instanceof ConfigInputPort) {
          addConfigInputPortParameter(functionCall, actor, (ConfigInputPort) associatedPort, a);
        }
      }
    }
  }

  private final void addDataPortParameter(FunctionCall functionCall, Actor actor, DataPort port, FunctionArgument arg) {
    // Retrieve associated Fifo
    Fifo associatedFifo = null;
    if (arg.getDirection().equals(Direction.IN)) {
      DataInputPort dip = (DataInputPort) port;
      associatedFifo = dip.getIncomingFifo();
    } else {
      DataOutputPort dop = (DataOutputPort) port;
      associatedFifo = dop.getOutgoingFifo();
    }

    // Retrieve associated Buffer
    Buffer associatedBuffer = null;
    if (internalBufferMap.containsKey(associatedFifo)) {
      associatedBuffer = internalBufferMap.get(associatedFifo);
    } else if (externalBufferMap.containsKey(associatedFifo)) {
      associatedBuffer = externalBufferMap.get(associatedFifo);
    } else {
      throw new PreesmRuntimeException("Cannot associate actors FIFO with buffer in Codegen model generation");
    }

    // If there is an iteration to run actor, iterate the buffer
    if (iterMap.containsKey(actor)) {
      IteratedBuffer iteratedBuffer = null;
      iteratedBuffer = CodegenFactory.eINSTANCE.createIteratedBuffer();
      iteratedBuffer.setBuffer(associatedBuffer);
      iteratedBuffer.setIter(iterMap.get(actor));
      iteratedBuffer.setIterSize(port.getExpression().evaluate());
      associatedBuffer = iteratedBuffer;
    }

    // Add parameter to functionCall
    functionCall.addParameter(associatedBuffer,
        (arg.getDirection().equals(Direction.IN) ? PortDirection.INPUT : PortDirection.OUTPUT));
  }

  private final void addConfigInputPortParameter(FunctionCall functionCall, Actor actor, ConfigInputPort port,
      FunctionArgument arg) {
    // Search for origin parameter
    Parameter parameter = getSetterParameterRec(port);

    // Build a constant
    Constant constant = CodegenFactory.eINSTANCE.createConstant();
    constant.setValue(parameter.getExpression().evaluate());

    // Set variable name to argument name
    constant.setName(arg.getName());

    // Add parameter to functionCall
    functionCall.addParameter(constant, PortDirection.INPUT);
  }

  private static Parameter getSetterParameterRec(ConfigInputPort port) {
    ISetter setter = port.getIncomingDependency().getSetter();
    if (setter instanceof ConfigInputInterface) {
      return getSetterParameterRec(
          (ConfigInputPort) ((ConfigInputInterface) port.getIncomingDependency().getSetter()).getGraphPort());
    } else {
      return (Parameter) setter;

    }
  }

  private static final List<Fifo> getInternalClusterFifo(PiGraph cluster) {
    List<Fifo> internalFifo = new LinkedList<>();
    for (Fifo fifo : cluster.getFifos()) {
      if ((fifo.getSource() instanceof DataInputInterface) || (fifo.getTarget() instanceof DataOutputInterface)) {
        // Do nothing
      } else {
        // Add Fifo to internalFifo list
        internalFifo.add(fifo);
      }
    }
    return internalFifo;
  }

  private final List<Buffer> buildInternalClusterBuffer(PiGraph cluster) {
    List<Buffer> internalBuffer = new LinkedList<>();

    int i = 0;
    for (Fifo fifo : getInternalClusterFifo(cluster)) {
      // Allocate a buffer for each internalFifo
      Buffer buffer = CodegenFactory.eINSTANCE.createBuffer();
      buffer.setName("mem_" + ((AbstractActor) fifo.getSource()).getName() + "_to_"
          + ((AbstractActor) fifo.getTarget()).getName() + "_" + i++);
      // Fill buffer information by looking at the Fifo
      buffer.setType(fifo.getType());
      buffer.setTypeSize(scenario.getSimulationInfo().getDataTypeSizeOrDefault(fifo.getType()));
      buffer.setSize(fifo.getSourcePort().getExpression().evaluate() * this.repVector.get(fifo.getSource()));
      // Register the buffer to the corresponding Fifo
      internalBufferMap.put(fifo, buffer);
      internalBuffer.add(buffer);
    }

    return internalBuffer;
  }

  private final List<Buffer> buildExternalClusterBuffer(PiGraph cluster) {
    List<Buffer> externalBuffer = new LinkedList<>();

    // Get the list of external Fifo in the current cluster
    List<Fifo> externalFifo = new LinkedList<Fifo>();
    externalFifo.addAll(cluster.getFifos());
    externalFifo.removeAll(getInternalClusterFifo(cluster));

    // For all external Fifo
    for (Fifo fifo : externalFifo) {
      Fifo outsideFifo = null;

      // Determine Fifo direction
      PortDirection direction;
      if (fifo.getSource() instanceof DataInputInterface) {
        outsideFifo = getOutsideIncomingFifo(fifo);
        direction = PortDirection.INPUT;
      } else {
        outsideFifo = getOutsideOutgoingFifo(fifo);
        direction = PortDirection.OUTPUT;
      }

      // Retrieve from map the corresponding parent buffer
      Buffer buffer = null;
      if (internalBufferMap.containsKey(outsideFifo)) {
        buffer = internalBufferMap.get(outsideFifo);
      } else if (externalBufferMap.containsKey(outsideFifo)) {
        buffer = externalBufferMap.get(outsideFifo);
      } else {
        throw new PreesmRuntimeException("Cannot fetch external Fifo (from cluster) in this version of Codegen");
      }

      // If cluster is repeated few times, create an iterated buffer
      if (this.repVector.get(cluster) > 1) {
        IteratedBuffer iteratedBuffer = CodegenFactory.eINSTANCE.createIteratedBuffer();
        // Iterative buffer contains parent buffer
        iteratedBuffer.setBuffer(buffer);
        // Get iterator for this repeated cluster
        iteratedBuffer.setIter(iterMap.get(cluster));
        // Retrieve prod/cons of cluster port
        if (direction.equals(PortDirection.INPUT)) {
          iteratedBuffer.setIterSize(outsideFifo.getTargetPort().getExpression().evaluate());
        } else {
          iteratedBuffer.setIterSize(outsideFifo.getSourcePort().getExpression().evaluate());
        }
        // Output a IteratedBuffer instead of parent Buffer
        buffer = iteratedBuffer;
      }

      // Register external buffer with corresponding fifo
      externalBufferMap.put(fifo, buffer);
      externalBuffer.add(buffer);
    }

    return externalBuffer;
  }

  private Fifo getOutsideOutgoingFifo(Fifo inFifo) {
    AbstractActor targetActor = (AbstractActor) inFifo.getTarget();
    Fifo outFifo = null;
    if (targetActor instanceof DataOutputInterface) {
      outFifo = ((DataOutputPort) ((DataOutputInterface) targetActor).getGraphPort()).getOutgoingFifo();
    }
    return outFifo;
  }

  private Fifo getOutsideIncomingFifo(Fifo inFifo) {
    AbstractActor sourceActor = (AbstractActor) inFifo.getSource();
    Fifo outFifo = null;
    if (sourceActor instanceof DataInputInterface) {
      outFifo = ((DataInputPort) ((DataInputInterface) sourceActor).getGraphPort()).getIncomingFifo();
    }
    return outFifo;
  }

}
