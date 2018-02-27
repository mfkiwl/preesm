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
package org.ietr.preesm.schedule;

import java.util.Hashtable;
import java.util.Map.Entry;
import org.ietr.dftools.algorithm.model.sdf.SDFAbstractVertex;
import org.ietr.dftools.algorithm.model.sdf.SDFGraph;
import org.ietr.dftools.algorithm.model.sdf.SDFInterfaceVertex;
import org.ietr.preesm.throughput.tools.helpers.GraphSimulationHelper;
import org.ietr.preesm.throughput.tools.helpers.Stopwatch;

/**
 * @author hderoui
 *
 *         ASAP scheduler : Ghamarian + Lee + Hamza version
 *
 */
public class ASAPScheduler_SDF {
  private GraphSimulationHelper                                    simulator;  // simulator helper
  private Double                                                   dur1Iter;   // duration of one iteration of a graph
  private Hashtable<Double, Hashtable<SDFAbstractVertex, Integer>> executions; // list of ready executions to finish
  public boolean                                                   live;

  /**
   * Schedule the graph using an ASAP schedule and return the duration of the graph iteration
   * 
   * @param graph
   *          SDF graph contains actors duration
   * @return the duration of a graph iteration
   */
  public double schedule(SDFGraph graph) {
    Stopwatch timer = new Stopwatch();
    timer.start();

    // initialize the simulator
    this.simulator = new GraphSimulationHelper(graph);
    this.dur1Iter = 0.;

    // initialize the 1st elements of the list
    executions = new Hashtable<Double, Hashtable<SDFAbstractVertex, Integer>>();
    this.initialzeList(graph);

    while (!executions.isEmpty()) {
      // pick the execution list with the earliest finish date
      double t = Double.MAX_VALUE;
      for (double keyT : executions.keySet()) {
        if (t >= keyT) {
          t = keyT;
        }
      }

      // update the duration of the iteration
      if (dur1Iter < t) {
        dur1Iter = t;
      }

      // execute the list of executions
      Hashtable<SDFAbstractVertex, Integer> listTExec = executions.get(t);
      executions.remove(t);

      for (Entry<SDFAbstractVertex, Integer> execution : listTExec.entrySet()) {

        // produce n*prod data tokens
        this.simulator.produce(execution.getKey(), execution.getValue());

        // verify the target actors of the executed actor if they are ready to be executed
        for (SDFInterfaceVertex output : execution.getKey().getSinks()) {

          // execute n times the target actor if it is ready
          SDFAbstractVertex targetActor = execution.getKey().getAssociatedEdge(output).getTarget();
          int n = this.simulator.maxExecToCompleteAnIteration(targetActor);

          if (n > 0) {
            // consume N data tokens
            this.simulator.consume(targetActor, n);

            // set the start date
            this.simulator.setStartDate(targetActor, t);

            // set the finish date
            double finishDate = this.simulator.getStartDate(targetActor) + this.simulator.getActorDuration(targetActor);
            this.simulator.setfinishDate(targetActor, finishDate);

            // add the execution to the list
            if (executions.containsKey(finishDate)) {
              Hashtable<SDFAbstractVertex, Integer> listExec = executions.get(finishDate);
              if (listExec.containsKey(targetActor)) {
                int old = listExec.get(targetActor);
                listExec.put(targetActor, (old + n));
              } else {
                listExec.put(targetActor, n);
              }
            } else {
              executions.put(finishDate, new Hashtable<SDFAbstractVertex, Integer>());
              executions.get(finishDate).put(targetActor, n);
            }
          }
        }
      }
    }

    timer.stop();

    // check if the simulation is completed
    if (this.simulator.isIterationCompleted()) {
      live = true;
      System.out.println("Iteration complete !!");
    } else {
      live = false;
      System.err.println("Iteration not complete !!");
    }

    System.out.println("SDF Graph Scheduled in " + timer.toString());
    return dur1Iter;
  }

  /**
   * Initialize the list of ready executions
   * 
   * @param graph
   *          SDF graph
   */
  private void initialzeList(SDFGraph graph) {
    // loop actors
    for (SDFAbstractVertex actor : graph.vertexSet()) {
      // get the max n
      int n = this.simulator.maxExecToCompleteAnIteration(actor);
      // if ready
      if (n > 0) {
        // consume N data tokens
        this.simulator.consume(actor, n);
        // set the start date
        this.simulator.setStartDate(actor, 0.);
        // set the finish date
        double finishDate = this.simulator.getStartDate(actor) + this.simulator.getActorDuration(actor);
        this.simulator.setfinishDate(actor, finishDate);
        // add the execution to the list
        if (executions.containsKey(finishDate)) {
          executions.get(finishDate).put(actor, n);
        } else {
          executions.put(finishDate, new Hashtable<SDFAbstractVertex, Integer>());
          executions.get(finishDate).put(actor, n);
        }
      }
    }
  }

}