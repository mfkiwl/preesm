/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2019) :
 *
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2019)
 * Daniel Madroñal [daniel.madronal@upm.es] (2019)
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
package org.preesm.algorithm.synthesis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.eclipse.core.runtime.IProgressMonitor;
import org.preesm.algorithm.mapping.model.Mapping;
import org.preesm.algorithm.memalloc.model.Allocation;
import org.preesm.algorithm.schedule.model.Schedule;
import org.preesm.algorithm.synthesis.communications.ICommunicationInserter;
import org.preesm.algorithm.synthesis.communications.OptimizedCommunicationInserter;
import org.preesm.algorithm.synthesis.evaluation.latency.LatencyCost;
import org.preesm.algorithm.synthesis.evaluation.latency.SimpleLatencyEvaluation;
import org.preesm.algorithm.synthesis.memalloc.IMemoryAllocation;
import org.preesm.algorithm.synthesis.memalloc.LegacyMemoryAllocation;
import org.preesm.algorithm.synthesis.memalloc.SimpleMemoryAllocation;
import org.preesm.algorithm.synthesis.schedule.algos.IScheduler;
import org.preesm.algorithm.synthesis.schedule.algos.LegacyListScheduler;
import org.preesm.algorithm.synthesis.schedule.algos.SimpleScheduler;
import org.preesm.commons.doc.annotations.Port;
import org.preesm.commons.doc.annotations.PreesmTask;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.scenario.Scenario;
import org.preesm.model.slam.Design;
import org.preesm.workflow.elements.Workflow;
import org.preesm.workflow.implement.AbstractTaskImplementation;
import org.preesm.workflow.implement.AbstractWorkflowNodeImplementation;

/**
 *
 * @author anmorvan
 *
 */
@PreesmTask(id = "pisdf-synthesis.simple", name = "Simple Synhtesis", category = "Synhtesis",

    inputs = { @Port(name = "PiMM", type = PiGraph.class), @Port(name = "architecture", type = Design.class),
        @Port(name = "scenario", type = Scenario.class) },
    outputs = { @Port(name = "Schedule", type = Schedule.class), @Port(name = "Mapping", type = Mapping.class),
        @Port(name = "Allocation", type = Allocation.class) })
public class PreesmSynthesisTask extends AbstractTaskImplementation {

  @Override
  public Map<String, Object> execute(final Map<String, Object> inputs, final Map<String, String> parameters,
      final IProgressMonitor monitor, final String nodeName, final Workflow workflow) {

    final PiGraph algorithm = (PiGraph) inputs.get(AbstractWorkflowNodeImplementation.KEY_PI_GRAPH);
    final Design architecture = (Design) inputs.get(AbstractWorkflowNodeImplementation.KEY_ARCHITECTURE);
    final Scenario scenario = (Scenario) inputs.get(AbstractWorkflowNodeImplementation.KEY_SCENARIO);

    final String schedulerName = parameters.get("scheduler").toLowerCase();
    final String allocationName = parameters.get("allocation").toLowerCase();

    final IScheduler scheduler = selectScheduler(schedulerName);
    final IMemoryAllocation alloc = selectAllocation(allocationName);

    PreesmLogger.getLogger().log(Level.INFO, () -> " -- Scheduling - " + schedulerName);
    final SynthesisResult scheduleAndMap = scheduler.scheduleAndMap(algorithm, architecture, scenario);

    PreesmLogger.getLogger().log(Level.INFO, " -- Insert communication");
    final ICommunicationInserter comIns = new OptimizedCommunicationInserter();
    comIns.insertCommunications(algorithm, architecture, scenario, scheduleAndMap.schedule, scheduleAndMap.mapping);

    final LatencyCost evaluate = new SimpleLatencyEvaluation().evaluate(algorithm, architecture, scenario,
        scheduleAndMap);
    PreesmLogger.getLogger().log(Level.INFO, "Simple latency evaluation : " + evaluate.getValue());

    PreesmLogger.getLogger().log(Level.INFO, () -> " -- Allocating Memory - " + allocationName);
    final Allocation memalloc = alloc.allocateMemory(algorithm, architecture, scenario, scheduleAndMap.schedule,
        scheduleAndMap.mapping);

    final Map<String, Object> outputs = new LinkedHashMap<>();
    outputs.put("Schedule", scheduleAndMap.schedule);
    outputs.put("Mapping", scheduleAndMap.mapping);
    outputs.put("Allocation", memalloc);
    return outputs;
  }

  private IScheduler selectScheduler(final String schedulerName) {
    final IScheduler scheduler;
    switch (schedulerName) {
      case "simple":
        scheduler = new SimpleScheduler();
        break;
      case "legacy":
        scheduler = new LegacyListScheduler();
        break;
      default:
        throw new PreesmRuntimeException("unknown scheduler: " + schedulerName);
    }
    return scheduler;
  }

  private IMemoryAllocation selectAllocation(final String allocationName) {
    final IMemoryAllocation alloc;
    switch (allocationName) {
      case "simple":
        alloc = new SimpleMemoryAllocation();
        break;
      case "legacy":
        alloc = new LegacyMemoryAllocation();
        break;

      default:
        throw new PreesmRuntimeException("unknown allocation: " + allocationName);
    }
    return alloc;
  }

  @Override
  public Map<String, String> getDefaultParameters() {
    final Map<String, String> res = new LinkedHashMap<>();
    res.put("scheduler", "simple");
    res.put("allocation", "simple");
    return res;
  }

  @Override
  public String monitorMessage() {
    return "Synthesis";
  }

}
