/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2018 - 2019) :
 *
 * Alexandre Honorat [alexandre.honorat@insa-rennes.fr] (2018 - 2019)
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2018 - 2019)
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
package org.preesm.algorithm.pisdf.checker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.AbstractVertex;
import org.preesm.model.pisdf.Actor;
import org.preesm.model.pisdf.DataInputPort;
import org.preesm.model.pisdf.DataOutputPort;
import org.preesm.model.pisdf.Fifo;

/**
 * This class aims to select periodic actors on which execute the period checkers (nbff and nblf).
 *
 * @author ahonorat
 */
class HeuristicPeriodicActorSelection {

  protected static Map<Actor, Double> selectActors(final Map<Actor, Long> periodicActors,
      final Set<AbstractActor> originActors, final Map<AbstractActor, Integer> actorsNbVisits, final int rate,
      final Map<AbstractVertex, Long> wcets, final boolean reverse) {
    if (rate == 0 || periodicActors.isEmpty()) {
      return new LinkedHashMap<>();
    }

    Map<AbstractActor, ActorVisit> topoRanks = null;
    if (reverse) {
      topoRanks = HeuristicPeriodicActorSelection.topologicalASAPrankingT(originActors, actorsNbVisits);
    } else {
      topoRanks = HeuristicPeriodicActorSelection.topologicalASAPranking(originActors, actorsNbVisits);
    }
    final Map<Actor, Double> topoRanksPeriodic = new LinkedHashMap<>();
    for (final Entry<Actor, Long> e : periodicActors.entrySet()) {
      final Actor actor = e.getKey();
      if (!topoRanks.containsKey(actor)) {
        continue;
      }
      final long rank = topoRanks.get(actor).rank;
      final long period = e.getValue();
      long wcetMin = wcets.get(actor);
      topoRanksPeriodic.put(actor, (period - wcetMin) / (double) rank);
    }
    final StringBuilder sb = new StringBuilder();
    topoRanksPeriodic.entrySet().forEach(a -> sb.append(a.getKey().getName() + "(" + a.getValue() + ") / "));
    PreesmLogger.getLogger().log(Level.INFO, "Periodic actor ranks: " + sb.toString());

    return HeuristicPeriodicActorSelection.selectFromRate(periodicActors, topoRanksPeriodic, rate);
  }

  private static Map<Actor, Double> selectFromRate(final Map<Actor, Long> periodicActors,
      final Map<Actor, Double> topoRanksPeriodic, final int rate) {
    final int nbPeriodicActors = periodicActors.size();
    final double nActorsToSelect = nbPeriodicActors * (rate / 100.0);
    final int nbActorsToSelect = Math.max((int) Math.ceil(nActorsToSelect), 1);

    final Map<Actor,
        Double> selectedActors = topoRanksPeriodic.entrySet().stream().sorted(Map.Entry.comparingByValue())
            .limit(nbActorsToSelect)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

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
    int       rank    = 0;

    ActorVisit(final int nbMaxVisit, final int rank) {
      this.nbMaxVisit = nbMaxVisit;
      this.rank = rank;
    }

  }

  private static Map<AbstractActor, ActorVisit> topologicalASAPranking(final Set<AbstractActor> sourceActors,
      final Map<AbstractActor, Integer> actorsNbVisits) {
    final Map<AbstractActor, ActorVisit> topoRanks = new LinkedHashMap<>();
    for (final AbstractActor actor : sourceActors) {
      topoRanks.put(actor, new ActorVisit(0, 1));
    }

    final Deque<AbstractActor> toVisit = new ArrayDeque<>(sourceActors);
    while (!toVisit.isEmpty()) {
      final AbstractActor actor = toVisit.removeFirst();
      final int rank = topoRanks.get(actor).rank;
      for (final DataOutputPort sport : actor.getDataOutputPorts()) {
        final Fifo fifo = sport.getOutgoingFifo();
        final DataInputPort tport = fifo.getTargetPort();
        final AbstractActor dest = tport.getContainingActor();
        if (!topoRanks.containsKey(dest)) {
          final ActorVisit av = new ActorVisit(actorsNbVisits.get(dest), rank);
          topoRanks.put(dest, av);
        }
        final ActorVisit av = topoRanks.get(dest);
        av.nbVisit++;
        if (av.nbVisit <= av.nbMaxVisit) {
          av.rank = Math.max(av.rank, rank + 1);
          if (av.nbVisit == av.nbMaxVisit) {
            toVisit.addLast(dest);
          }
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    topoRanks.entrySet().stream().forEach(e -> {
      ActorVisit v = e.getValue();
      sb.append("\n\t" + e.getKey().getName() + ": " + v.rank + " (" + v.nbVisit + " visits on " + v.nbMaxVisit + ")");
    });
    PreesmLogger.getLogger().log(Level.FINE, "Ranks: " + sb.toString());

    return topoRanks;
  }

  private static Map<AbstractActor, ActorVisit> topologicalASAPrankingT(final Set<AbstractActor> sinkActors,
      final Map<AbstractActor, Integer> actorsNbVisits) {
    final Map<AbstractActor, ActorVisit> topoRanks = new LinkedHashMap<>();
    for (final AbstractActor actor : sinkActors) {
      topoRanks.put(actor, new ActorVisit(0, 1));
    }

    final Deque<AbstractActor> toVisit = new ArrayDeque<>(sinkActors);
    while (!toVisit.isEmpty()) {
      final AbstractActor actor = toVisit.removeFirst();
      final int rank = topoRanks.get(actor).rank;
      for (final DataInputPort tport : actor.getDataInputPorts()) {
        final Fifo fifo = tport.getIncomingFifo();
        final DataOutputPort sport = fifo.getSourcePort();
        final AbstractActor dest = sport.getContainingActor();
        if (!topoRanks.containsKey(dest)) {
          final ActorVisit av = new ActorVisit(actorsNbVisits.get(dest), rank);
          topoRanks.put(dest, av);
        }
        final ActorVisit av = topoRanks.get(dest);
        av.nbVisit++;
        if (av.nbVisit <= av.nbMaxVisit) {
          av.rank = Math.max(av.rank, rank + 1);
          if (av.nbVisit == av.nbMaxVisit) {
            toVisit.addLast(dest);
          }
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    topoRanks.entrySet().stream().forEach(e -> {
      ActorVisit v = e.getValue();
      sb.append("\n\t" + e.getKey().getName() + ": " + v.rank + " (" + v.nbVisit + " visits on " + v.nbMaxVisit + ")");
    });
    PreesmLogger.getLogger().log(Level.FINE, "RanksT: " + sb.toString());

    return topoRanks;
  }

}
