/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2018) :
 *
 * Alexandre Honorat <alexandre.honorat@insa-rennes.fr> (2018)
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
package org.preesm.algorithm.pisdf.checker.periods;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.Actor;
import org.preesm.model.pisdf.DataInputPort;
import org.preesm.model.pisdf.DataOutputPort;
import org.preesm.model.pisdf.Fifo;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.scenario.PreesmScenario;

/**
 * This class aims to select periodic actors on which execute the period checkers (nbff and nblf).
 * 
 * @author ahonorat
 */
class HeuristicPeriodicActorSelection {

  static Map<Actor, Long> selectActors(final Map<Actor, Long> periodicActors, final List<Actor> originActors,
      final int rate, final PiGraph graph, final PreesmScenario scenario, boolean reverse) {
    if (rate == 100 || periodicActors.isEmpty()) {
      return periodicActors;
    }
    if (rate == 0) {
      return new HashMap<>();
    }

    Map<AbstractActor, ActorVisit> topoRanks = null;
    if (reverse) {
      topoRanks = topologicalASAPrankingT(originActors, graph);
    } else {
      topoRanks = topologicalASAPranking(originActors, graph);
    }
    final Map<Actor, Double> topoRanksPeriodic = new HashMap<>();
    for (Entry<Actor, Long> e : periodicActors.entrySet()) {
      final Actor actor = e.getKey();
      final long rank = topoRanks.get(actor).rank;
      final long period = e.getValue();
      long wcetMin = Long.MAX_VALUE;
      for (String operatorDefinitionID : scenario.getOperatorDefinitionIds()) {
        final long timing = scenario.getTimingManager().getTimingOrDefault(actor.getName(), operatorDefinitionID)
            .getTime();
        if (timing < wcetMin) {
          wcetMin = timing;
        }
      }
      topoRanksPeriodic.put(actor, (period - wcetMin) / (double) rank);
    }
    final StringBuilder sb = new StringBuilder();
    topoRanksPeriodic.entrySet().forEach(a -> sb.append(a.getKey().getName() + "(" + a.getValue() + ") / "));
    PreesmLogger.getLogger().log(Level.WARNING, "Periodic actor ranks: " + sb.toString());

    return selectFromRate(periodicActors, topoRanksPeriodic, rate);
  }

  private static Map<Actor, Long> selectFromRate(Map<Actor, Long> periodicActors, Map<Actor, Double> topoRanksPeriodic,
      int rate) {
    final int nbPeriodicActors = periodicActors.size();
    final double nActorsToSelect = nbPeriodicActors * (rate / (double) 100.0);
    final int nbActorsToSelect = Math.max((int) Math.ceil(nActorsToSelect), 1);

    Map<Actor,
        Long> selectedActors = periodicActors.entrySet().stream().sorted(Map.Entry.comparingByValue())
            .limit(nbActorsToSelect).collect(
                Collectors.toMap(Map.Entry::getKey, e -> periodicActors.get(e.getKey()), (e1, e2) -> e1, HashMap::new));

    // final Map<Actor, Long> selectedActors = new HashMap<>();
    // for (int i = 0; i < nbActorsToSelect; ++i) {
    // Actor actor = topoRanksPeriodic.firstKey();
    // topoRanksPeriodic.remove(actor);
    // WorkflowLogger.getLogger().log(Level.INFO, "Periodic actor: " + actor.getName());
    // selectedActors.put(actor, periodicActors.get(actor));
    // }
    return selectedActors;
  }

  /**
   * This class helps to create the topological rank.
   * 
   * @author ahonorat
   */
  private static class ActorVisit {
    final int nbMaxVisit;
    int       nbVisit = 0;
    long      rank    = 0;

    ActorVisit(int nbMaxVisit, long rank) {
      this.nbMaxVisit = nbMaxVisit;
      this.rank = rank;
    }

  }

  private static Map<AbstractActor, ActorVisit> topologicalASAPranking(final List<Actor> sourceActors,
      final PiGraph graph) {
    final Map<AbstractActor, ActorVisit> topoRanks = new HashMap<>();
    for (Actor actor : sourceActors) {
      topoRanks.put(actor, new ActorVisit(0, 1L));
    }

    final Deque<AbstractActor> toVisit = new ArrayDeque<>(sourceActors);
    while (!toVisit.isEmpty()) {
      final AbstractActor actor = toVisit.removeFirst();
      final long rank = topoRanks.get(actor).rank;
      for (DataOutputPort sport : actor.getDataOutputPorts()) {
        final Fifo fifo = sport.getOutgoingFifo();
        final DataInputPort tport = fifo.getTargetPort();
        final AbstractActor dest = tport.getContainingActor();
        if (!topoRanks.containsKey(dest)) {
          ActorVisit av = new ActorVisit(dest.getDataInputPorts().size(), rank);
          topoRanks.put(dest, av);
        }
        ActorVisit av = topoRanks.get(dest);
        av.nbVisit++;
        if (av.nbVisit == av.nbMaxVisit) {
          toVisit.addLast(dest);
        }
        System.err.println("Rank: " + rank + " (" + dest.getName() + ")");
      }
    }

    return topoRanks;
  }

  private static Map<AbstractActor, ActorVisit> topologicalASAPrankingT(final List<Actor> sinkActors,
      final PiGraph graph) {
    final Map<AbstractActor, ActorVisit> topoRanks = new HashMap<>();
    for (Actor actor : sinkActors) {
      topoRanks.put(actor, new ActorVisit(0, 1L));
    }

    final Deque<AbstractActor> toVisit = new ArrayDeque<>(sinkActors);
    while (!toVisit.isEmpty()) {
      final AbstractActor actor = toVisit.removeFirst();
      final long rank = topoRanks.get(actor).rank;
      for (DataInputPort tport : actor.getDataInputPorts()) {
        final Fifo fifo = tport.getIncomingFifo();
        final DataOutputPort sport = fifo.getSourcePort();
        final AbstractActor dest = sport.getContainingActor();
        if (!topoRanks.containsKey(dest)) {
          ActorVisit av = new ActorVisit(dest.getDataOutputPorts().size(), rank);
          topoRanks.put(dest, av);
        }
        ActorVisit av = topoRanks.get(dest);
        av.nbVisit++;
        av.rank = Math.max(av.rank, rank + 1L);
        if (av.nbVisit == av.nbMaxVisit) {
          toVisit.addLast(dest);
        }
        System.err.println("RankT: " + rank + " (" + dest.getName() + ")");
      }
    }

    return topoRanks;
  }

}