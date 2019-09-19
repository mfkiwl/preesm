/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2019) :
 *
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2019)
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
package org.preesm.codegen.model.generator2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.preesm.algorithm.mapping.model.Mapping;
import org.preesm.algorithm.memalloc.model.Allocation;
import org.preesm.algorithm.memalloc.model.PhysicalBuffer;
import org.preesm.algorithm.schedule.model.CommunicationActor;
import org.preesm.algorithm.schedule.model.Schedule;
import org.preesm.algorithm.synthesis.schedule.iterator.ScheduleAndTopologyIterator;
import org.preesm.codegen.model.ActorFunctionCall;
import org.preesm.codegen.model.Block;
import org.preesm.codegen.model.Buffer;
import org.preesm.codegen.model.Call;
import org.preesm.codegen.model.Constant;
import org.preesm.codegen.model.CoreBlock;
import org.preesm.codegen.model.SpecialCall;
import org.preesm.codegen.model.SpecialType;
import org.preesm.codegen.model.SubBuffer;
import org.preesm.codegen.model.Variable;
import org.preesm.codegen.model.util.CodegenModelUserFactory;
import org.preesm.codegen.model.util.VariableSorter;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.Actor;
import org.preesm.model.pisdf.BroadcastActor;
import org.preesm.model.pisdf.CHeaderRefinement;
import org.preesm.model.pisdf.Fifo;
import org.preesm.model.pisdf.ForkActor;
import org.preesm.model.pisdf.FunctionPrototype;
import org.preesm.model.pisdf.JoinActor;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.pisdf.Port;
import org.preesm.model.pisdf.Refinement;
import org.preesm.model.pisdf.RoundBufferActor;
import org.preesm.model.pisdf.SpecialActor;
import org.preesm.model.pisdf.SrdagActor;
import org.preesm.model.pisdf.UserSpecialActor;
import org.preesm.model.scenario.Scenario;
import org.preesm.model.slam.ComponentInstance;
import org.preesm.model.slam.Design;

/**
 *
 * @author anmorvan
 *
 */
public class CodegenModelGenerator2 {

  public static final List<Block> generate(final Design archi, final PiGraph algo, final Scenario scenario,
      final Schedule schedule, final Mapping mapping, final Allocation memAlloc, final boolean papify) {
    return new CodegenModelGenerator2(archi, algo, scenario, schedule, mapping, memAlloc, papify).generate();
  }

  private final Design     archi;
  private final PiGraph    algo;
  private final Scenario   scenario;
  private final Schedule   schedule;
  private final Mapping    mapping;
  private final Allocation memAlloc;

  private AllocationToCodegenBuffer memoryLinker;

  private final boolean papify;

  private CodegenModelGenerator2(final Design archi, final PiGraph algo, final Scenario scenario,
      final Schedule schedule, final Mapping mapping, final Allocation memAlloc, final boolean papify) {
    this.archi = archi;
    this.algo = algo;
    this.scenario = scenario;
    this.schedule = schedule;
    this.mapping = mapping;
    this.memAlloc = memAlloc;
    this.papify = papify;
  }

  private List<Block> generate() {
    final String msg = "Starting codegen2 with papify set to " + papify;
    PreesmLogger.getLogger().log(Level.FINE, msg);

    final Map<ComponentInstance, CoreBlock> coreBlocks = new LinkedHashMap<>();

    // 0- init blocks
    final EList<ComponentInstance> cmps = this.archi.getOperatorComponentInstances();
    for (final ComponentInstance cmp : cmps) {
      final CoreBlock createCoreBlock = CodegenModelUserFactory.eINSTANCE.createCoreBlock(cmp);
      coreBlocks.put(cmp, createCoreBlock);
    }

    // 1- generate variables (and keep track of them with a linker)
    this.memoryLinker = AllocationToCodegenBuffer.link(memAlloc, scenario, algo);

    // 2- generate code
    generateCode(coreBlocks);

    // sort blocks
    final List<Block> resultList = coreBlocks.entrySet().stream()
        .sorted((e1, e2) -> e1.getKey().getHardwareId() - e2.getKey().getHardwareId()).map(Entry::getValue)
        .collect(Collectors.toList());

    // generate buffer definitions
    generateBuffers(coreBlocks);

    return Collections.unmodifiableList(resultList);
  }

  private void generateBuffers(final Map<ComponentInstance, CoreBlock> coreBlocks) {
    final List<Buffer> buffers = this.memoryLinker.getCodegenBuffers();
    for (Buffer buffer : buffers) {
      generateBuffer(coreBlocks, buffer);
    }
  }

  private void generateBuffer(final Map<ComponentInstance, CoreBlock> coreBlocks, final Buffer mainBuffer) {
    final org.preesm.algorithm.memalloc.model.Buffer key = this.memoryLinker.getAllocationBuffer(mainBuffer);
    final PhysicalBuffer memoryBankObj = key.getBank();
    final String memoryBank = memoryBankObj.getMemoryBank().getInstanceName();

    // Identify the corresponding operator block.
    // (also find out if the Buffer is local (i.e. not shared between
    // several CoreBlock)
    CoreBlock correspondingOperatorBlock = null;
    final boolean isLocal;
    final String correspondingOperatorID;

    if (memoryBank.equalsIgnoreCase("shared_mem")) {
      // If the memory bank is shared, let the main operator
      // declare the Buffer.
      correspondingOperatorID = this.scenario.getSimulationInfo().getMainOperator().getInstanceName();
      isLocal = false;

      // Check that the main operator block exists.
      CoreBlock mainOperatorBlock = null;
      for (final Entry<ComponentInstance, CoreBlock> componentEntry : coreBlocks.entrySet()) {
        if (componentEntry.getKey().getInstanceName().equals(correspondingOperatorID)) {
          mainOperatorBlock = componentEntry.getValue();
        }
      }

      // If the main operator does not exist
      if (mainOperatorBlock == null) {
        // Create it
        mainOperatorBlock = CodegenModelUserFactory.eINSTANCE.createCoreBlock(null);
        final ComponentInstance componentInstance = this.archi.getComponentInstance(correspondingOperatorID);
        mainOperatorBlock.setName(componentInstance.getInstanceName());
        mainOperatorBlock.setCoreType(componentInstance.getComponent().getVlnv().getName());
        coreBlocks.put(componentInstance, mainOperatorBlock);
      }

    } else {
      // else, the operator corresponding to the memory bank will
      // do the work
      correspondingOperatorID = memoryBank;
      isLocal = true;
    }

    // Find the block
    for (final Entry<ComponentInstance, CoreBlock> componentEntry : coreBlocks.entrySet()) {
      if (componentEntry.getKey().getInstanceName().equals(correspondingOperatorID)) {
        correspondingOperatorBlock = componentEntry.getValue();
      }
    }
    // Recursively set the creator for the current Buffer and all its
    // subBuffer
    recursiveSetBufferCreator(mainBuffer, correspondingOperatorBlock, isLocal);
    sortDefinitions(correspondingOperatorBlock);
  }

  private void sortDefinitions(CoreBlock correspondingOperatorBlock) {
    if (correspondingOperatorBlock != null) {
      final EList<Variable> definitions = correspondingOperatorBlock.getDefinitions();
      ECollections.sort(definitions, new VariableSorter());
    }
  }

  private void recursiveSetBufferCreator(final Variable variable, final CoreBlock correspondingOperatorBlock,
      final boolean isLocal) {
    // Set the creator for the current buffer
    variable.reaffectCreator(correspondingOperatorBlock);
    if (variable instanceof Buffer) {
      final Buffer buffer = (Buffer) variable;
      buffer.setLocal(isLocal);
      // Do the same recursively for all its children subbuffers
      for (final SubBuffer subBuffer : buffer.getChildrens()) {
        recursiveSetBufferCreator(subBuffer, correspondingOperatorBlock, isLocal);
      }
    }
  }

  private void generateCode(final Map<ComponentInstance, CoreBlock> coreBlocks) {
    // iterate in order

    final List<AbstractActor> actors = new ScheduleAndTopologyIterator(this.schedule).getOrderedList();
    for (final AbstractActor actor : actors) {
      final EList<ComponentInstance> actorMapping = this.mapping.getMapping(actor);
      final ComponentInstance componentInstance = actorMapping.get(0);
      final CoreBlock coreBlock = coreBlocks.get(componentInstance);

      if (actor instanceof Actor) {
        generateActorFiring((Actor) actor, this.memoryLinker.getPortToVariableMap(), coreBlock);
      } else if (actor instanceof UserSpecialActor) {
        generateSpecialActor((UserSpecialActor) actor, this.memoryLinker.getPortToVariableMap(), coreBlock);
      } else if (actor instanceof SrdagActor) {
        // TODO handle init/end
        // generateFifoCall((SrdagActor) actor, coreBlock);
        throw new PreesmRuntimeException("Unsupported actor [" + actor + "]");
      } else if (actor instanceof CommunicationActor) {
        // TODO handle send/receive + enabler triggers
        throw new PreesmRuntimeException("Unsupported actor [" + actor + "]");
      } else {
        throw new PreesmRuntimeException("Unsupported actor [" + actor + "]");
      }
    }
  }

  private void generateSpecialActor(final SpecialActor actor, final Map<Port, Variable> portToVariable,
      final CoreBlock operatorBlock) {
    final SpecialCall specialCall = CodegenModelUserFactory.eINSTANCE.createSpecialCall();
    specialCall.setName(actor.getName());

    final Fifo uniqueFifo;
    final Buffer lastBuffer;
    if (actor instanceof JoinActor) {
      specialCall.setType(SpecialType.JOIN);
      uniqueFifo = actor.getDataOutputPorts().get(0).getFifo();
      lastBuffer = this.memoryLinker.getCodegenBuffer(memAlloc.getFifoAllocations().get(uniqueFifo).getSourceBuffer());
    } else if (actor instanceof ForkActor) {
      specialCall.setType(SpecialType.FORK);
      uniqueFifo = actor.getDataInputPorts().get(0).getFifo();
      lastBuffer = this.memoryLinker.getCodegenBuffer(memAlloc.getFifoAllocations().get(uniqueFifo).getTargetBuffer());
    } else if (actor instanceof BroadcastActor) {
      specialCall.setType(SpecialType.BROADCAST);
      uniqueFifo = actor.getDataInputPorts().get(0).getFifo();
      lastBuffer = this.memoryLinker.getCodegenBuffer(memAlloc.getFifoAllocations().get(uniqueFifo).getTargetBuffer());
    } else if (actor instanceof RoundBufferActor) {
      specialCall.setType(SpecialType.ROUND_BUFFER);
      uniqueFifo = actor.getDataInputPorts().get(0).getFifo();
      lastBuffer = this.memoryLinker.getCodegenBuffer(memAlloc.getFifoAllocations().get(uniqueFifo).getTargetBuffer());
    } else {
      throw new PreesmRuntimeException("special actor " + actor + " has an unknown special type");
    }

    // Add it to the specialCall
    if (actor instanceof JoinActor) {
      specialCall.addOutputBuffer(lastBuffer);
      actor.getDataInputPorts().stream().map(port -> ((Buffer) portToVariable.get(port)))
          .forEach(specialCall::addInputBuffer);
    } else {
      specialCall.addInputBuffer(lastBuffer);
      actor.getDataOutputPorts().stream().map(port -> ((Buffer) portToVariable.get(port)))
          .forEach(specialCall::addOutputBuffer);
    }

    operatorBlock.getLoopBlock().getCodeElts().add(specialCall);

    registerCallVariableToCoreBlock(operatorBlock, specialCall);
  }

  protected void registerCallVariableToCoreBlock(final CoreBlock operatorBlock, final Call call) {
    // Register the core Block as a user of the function variable
    for (final Variable var : call.getParameters()) {
      // Currently, constants do not need to be declared nor
      // have creator since their value is directly used.
      // Consequently the used block can also be declared as the creator
      if (var instanceof Constant) {
        var.reaffectCreator(operatorBlock);
      }
      var.getUsers().add(operatorBlock);
    }
  }

  private void generateActorFiring(final Actor actor, final Map<Port, Variable> portToVariable,
      final CoreBlock coreBlock) {
    final Refinement refinement = actor.getRefinement();
    if (refinement instanceof CHeaderRefinement) {
      final FunctionPrototype initPrototype = ((CHeaderRefinement) refinement).getInitPrototype();
      if (initPrototype != null) {
        final ActorFunctionCall init = CodegenModelUserFactory.eINSTANCE.createActorFunctionCall(actor, initPrototype,
            portToVariable);
        coreBlock.getInitBlock().getCodeElts().add(init);
      }
      final FunctionPrototype loopPrototype = ((CHeaderRefinement) refinement).getLoopPrototype();
      final ActorFunctionCall loop = CodegenModelUserFactory.eINSTANCE.createActorFunctionCall(actor, loopPrototype,
          portToVariable);
      coreBlock.getLoopBlock().getCodeElts().add(loop);
    }
  }
}
