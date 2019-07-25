package org.preesm.codegen.model.generator2;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.emf.common.util.EList;
import org.preesm.algorithm.mapping.model.Mapping;
import org.preesm.algorithm.memalloc.model.Allocation;
import org.preesm.algorithm.memalloc.model.DistributedBuffer;
import org.preesm.algorithm.memalloc.model.LogicalBuffer;
import org.preesm.algorithm.memalloc.model.PhysicalBuffer;
import org.preesm.algorithm.memalloc.model.util.MemoryAllocationSwitch;
import org.preesm.algorithm.schedule.model.Schedule;
import org.preesm.codegen.model.ActorFunctionCall;
import org.preesm.codegen.model.Block;
import org.preesm.codegen.model.Buffer;
import org.preesm.codegen.model.CodegenFactory;
import org.preesm.codegen.model.CoreBlock;
import org.preesm.codegen.model.SubBuffer;
import org.preesm.codegen.model.Variable;
import org.preesm.codegen.model.util.CodegenModelUserFactory;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.Actor;
import org.preesm.model.pisdf.CHeaderRefinement;
import org.preesm.model.pisdf.ConfigInputPort;
import org.preesm.model.pisdf.Fifo;
import org.preesm.model.pisdf.FunctionPrototype;
import org.preesm.model.pisdf.ISetter;
import org.preesm.model.pisdf.Parameter;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.pisdf.Port;
import org.preesm.model.pisdf.Refinement;
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
      final Schedule schedule, final Mapping mapping, final Allocation memAlloc) {
    return generate(archi, algo, scenario, schedule, mapping, memAlloc, false);
  }

  public static final List<Block> generate(final Design archi, final PiGraph algo, final Scenario scenario,
      final Schedule schedule, final Mapping mapping, final Allocation memAlloc, boolean papify) {
    return new CodegenModelGenerator2(archi, algo, scenario, schedule, mapping, memAlloc, papify).generate();
  }

  private final Design     archi;
  private final PiGraph    algo;
  private final Scenario   scenario;
  private final Schedule   schedule;
  private final Mapping    mapping;
  private final Allocation memAlloc;
  private final boolean    papify;

  private CodegenModelGenerator2(final Design archi, final PiGraph algo, final Scenario scenario,
      final Schedule schedule, final Mapping mapping, final Allocation memAlloc, boolean papify) {
    this.archi = archi;
    this.algo = algo;
    this.scenario = scenario;
    this.schedule = schedule;
    this.mapping = mapping;
    this.memAlloc = memAlloc;
    this.papify = papify;
  }

  /**
   *
   */
  class AllocationVisitor extends MemoryAllocationSwitch<Boolean> {

    private final Deque<Buffer>                                     codegenBufferStack = new LinkedList<>();
    private final Deque<org.preesm.algorithm.memalloc.model.Buffer> allocBufferStack   = new LinkedList<>();

    final Map<org.preesm.algorithm.memalloc.model.Buffer, Buffer> btb            = new LinkedHashMap<>();
    final Map<Port, Variable>                                     portToVariable = new LinkedHashMap<>();

    @Override
    public Boolean caseAllocation(Allocation alloc) {
      alloc.getPhysicalBuffers().forEach(this::doSwitch);
      for (final Entry<Fifo, org.preesm.algorithm.memalloc.model.Buffer> allocations : alloc.getAllocations()) {
        final Fifo fifo = allocations.getKey();
        final org.preesm.algorithm.memalloc.model.Buffer buffer = allocations.getValue();
        final Buffer codegenBuffer = btb.get(buffer);

        codegenBuffer.setName(generateUniqueBufferName(fifo));
        codegenBuffer.setType(fifo.getType());
        codegenBuffer.setTypeSize(scenario.getSimulationInfo().getDataTypeSizeOrDefault(fifo.getType()));
        portToVariable.put(fifo.getTargetPort(), codegenBuffer);
        portToVariable.put(fifo.getSourcePort(), codegenBuffer);
      }
      return true;
    }

    private final Map<String, Long> bufferNames = new LinkedHashMap<>();

    private String generateUniqueBufferName(final Fifo fifo) {
      final String candidate = fifo.getId().replace(".", "_").replace("-", "_");
      long idx;
      String key = candidate;
      if (key.length() > 28) {
        key = key.substring(0, 28);
      }
      if (this.bufferNames.containsKey(key)) {
        idx = this.bufferNames.get(key);
      } else {
        idx = 0;
        this.bufferNames.put(key, idx);
      }

      final String bufferName = key + "__" + idx;
      idx += 1;
      this.bufferNames.put(key, idx);
      return bufferName;
    }

    @Override
    public Boolean caseLogicalBuffer(final LogicalBuffer logicalBuffer) {
      final SubBuffer subBuffer = CodegenFactory.eINSTANCE.createSubBuffer();
      subBuffer.setSize(logicalBuffer.getSize());
      btb.put(logicalBuffer, subBuffer);
      codegenBufferStack.push(subBuffer);
      allocBufferStack.push(logicalBuffer);

      for (org.preesm.algorithm.memalloc.model.Buffer l : logicalBuffer.getChildren()) {
        doSwitch(l);
      }

      codegenBufferStack.pop();
      allocBufferStack.pop();
      return true;
    }

    @Override
    public Boolean casePhysicalBuffer(final PhysicalBuffer phys) {
      final Buffer mainBuffer = CodegenFactory.eINSTANCE.createBuffer();
      mainBuffer.setSize(phys.getSize());
      mainBuffer.setName("Shared_" + phys.getMemory().getHardwareId());
      mainBuffer.setType("char");
      mainBuffer.setTypeSize(1); // char is 1 byte
      btb.put(phys, mainBuffer);
      codegenBufferStack.push(mainBuffer);
      allocBufferStack.push(phys);

      for (org.preesm.algorithm.memalloc.model.Buffer l : phys.getChildren()) {
        doSwitch(l);
      }

      codegenBufferStack.pop();
      allocBufferStack.pop();
      return true;
    }

    @Override
    public Boolean caseDistributedBuffer(final DistributedBuffer object) {
      throw new UnsupportedOperationException();
    }

  }

  private List<Block> generate() {

    final Map<ComponentInstance, CoreBlock> coreBlocks = new LinkedHashMap<>();

    final EList<ComponentInstance> cmps = this.archi.getOperatorComponentInstances();
    for (ComponentInstance cmp : cmps) {
      final CoreBlock createCoreBlock = CodegenModelUserFactory.createCoreBlock(cmp);
      coreBlocks.put(cmp, createCoreBlock);
    }

    final Map<Port, Variable> portToVariable = allocate();

    // iterate in order
    for (final AbstractActor actor : schedule.getActors()) {
      final EList<ComponentInstance> actorMapping = mapping.getMapping(actor);
      final ComponentInstance componentInstance = actorMapping.get(0);
      final CoreBlock coreBlock = coreBlocks.get(componentInstance);

      if (actor instanceof Actor) {
        final Refinement refinement = ((Actor) actor).getRefinement();
        if (refinement instanceof CHeaderRefinement) {
          final FunctionPrototype initPrototype = ((CHeaderRefinement) refinement).getInitPrototype();
          if (initPrototype != null) {
            final ActorFunctionCall init = CodegenModelUserFactory.createActorFunctionCall((Actor) actor, initPrototype,
                portToVariable);
            coreBlock.getInitBlock().getCodeElts().add(init);
          }
          final FunctionPrototype loopPrototype = ((CHeaderRefinement) refinement).getLoopPrototype();
          final ActorFunctionCall loop = CodegenModelUserFactory.createActorFunctionCall((Actor) actor, loopPrototype,
              portToVariable);
          coreBlock.getLoopBlock().getCodeElts().add(loop);
        }
      }
    }

    return new ArrayList<>(coreBlocks.values());

  }

  private Map<Port, Variable> allocate() {
    final AllocationVisitor v = new AllocationVisitor();
    v.doSwitch(memAlloc);
    final Map<Port, Variable> portToVariable = v.portToVariable;

    final EList<AbstractActor> allActors = algo.getAllActors();
    for (final AbstractActor actor : allActors) {
      for (final ConfigInputPort cip : actor.getConfigInputPorts()) {
        final ISetter setter = cip.getIncomingDependency().getSetter();
        if (setter instanceof Parameter) {
          final long evaluate = ((Parameter) setter).getValueExpression().evaluate();
          portToVariable.put(cip, CodegenModelUserFactory.createConstant(cip.getName(), evaluate));
        } else {
          throw new PreesmRuntimeException();
        }
      }
    }
    return portToVariable;
  }

}
