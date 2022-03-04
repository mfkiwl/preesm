package org.preesm.algorithm.schedule.fpga;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.xtext.xbase.lib.Pair;
import org.jgrapht.alg.cycle.PatonCycleBase;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Variable;
import org.preesm.algorithm.mapper.ui.stats.IStatGenerator;
import org.preesm.algorithm.pisdf.autodelays.AbstractGraph;
import org.preesm.algorithm.pisdf.autodelays.AbstractGraph.FifoAbstraction;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.commons.math.LongFraction;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.AbstractVertex;
import org.preesm.model.pisdf.DataPort;
import org.preesm.model.pisdf.Fifo;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.pisdf.statictools.PiMMHelper;
import org.preesm.model.scenario.Scenario;

/**
 * Class to evaluate buffer sizes thanks to an ADFG abstraction.
 * 
 * @author ahonorat
 */
public class AdfgFpgaFifoEvaluator extends AbstractGenericFpgaFifoEvaluator {

  public static final String FIFO_EVALUATOR_ADFG = "adfgFifoEval";

  protected AdfgFpgaFifoEvaluator() {
    super();
    // forbid instantiation outside package and inherited classed
  }

  @Override
  public Pair<IStatGenerator, Map<Fifo, Long>> performAnalysis(PiGraph flatGraph, Scenario scenario,
      Map<AbstractVertex, Long> brv) {

    // Get all sub graph (connected components) composing the current graph
    final List<List<AbstractActor>> subgraphsWOInterfaces = PiMMHelper.getAllConnectedComponentsWOInterfaces(flatGraph);

    final Map<AbstractActor, ActorNormalizedInfos> mapActorNormalizedInfos = new LinkedHashMap<>();
    // check and set the II for each subgraph
    for (List<AbstractActor> cc : subgraphsWOInterfaces) {
      mapActorNormalizedInfos.putAll(checkAndSetActorNormalizedInfos(cc, scenario, brv));
    }

    // compute the lambda of each actor
    final Map<DataPort, LongFraction> lambdaPerPort = new LinkedHashMap<>();
    final StringBuilder logLambda = new StringBuilder(
        "Lambda of actor ports (in number of tokens between 0 and the rate, the closest to 0 the better):\n");
    mapActorNormalizedInfos.values().forEach(ani -> {
      logLambda.append(String.format("/actor <%s>\n", ani.aa.getName()));

      final String logLambdaPorts = ani.aa.getAllDataPorts().stream().map(dp -> {
        final long rate = dp.getExpression().evaluate();
        final LongFraction lambdaFr = new LongFraction(-rate, ani.oriII).add(1).multiply(rate);
        // final double lambda = rate * (1.0d - rate / ani.oriII);
        lambdaPerPort.put(dp, lambdaFr);
        return String.format(Locale.US, "%s: %4.2e", dp.getName(), lambdaFr.doubleValue());
      }).collect(Collectors.joining(", "));

      logLambda.append(logLambdaPorts + "\n");
    });
    PreesmLogger.getLogger().info(logLambda::toString);

    // TODO compute the fifo sizes thanks to the ARS ILP formulation of ADFG
    // ILP stands for Integer Linear Programming
    // ARS stands for Affine Relation Synthesis
    // ADFG stands for Affine DataFlow Graph (work of Adnan Bouakaz)
    // ojAlgo dependency should be used to create the model because it has dedicated code to ILP,
    // or Choco (but not dedicated to ILP) at last resort.

    // create intermediate graphs
    final DefaultDirectedGraph<AbstractActor, FifoAbstraction> ddg = AbstractGraph.createAbsGraph(flatGraph, brv);
    final DefaultUndirectedGraph<AbstractActor, FifoAbstraction> dug = AbstractGraph.undirectedGraph(ddg);
    final Set<List<FifoAbstraction>> cycles = (new PatonCycleBase<AbstractActor, FifoAbstraction>(dug)).getCycleBasis()
        .getCycles();

    // build model
    // create Maps to retrieve ID of variables (ID in order of addition in the model)
    final ExpressionsBasedModel model = new ExpressionsBasedModel();

    // FifoAbstraction to phi Variable ID
    final Map<FifoAbstraction, Integer> fifoAbsToPhiVariableID = new LinkedHashMap<>();
    for (final FifoAbstraction fifoAbs : dug.edgeSet()) {
      final int index = fifoAbsToPhiVariableID.size();
      final Variable varPhi = new Variable("phi_" + index);
      varPhi.setInteger(true);
      // TODO separate neg. from pos.?
      fifoAbsToPhiVariableID.put(fifoAbs, index);
      model.addVariable(varPhi);
    }

    // add equation for cycles to the model
    for (final List<FifoAbstraction> cycle : cycles) {
      // create cycle equation
    }

    // Fifo to delta/size Variable ID (theta/delay is fixed for us, so not a variable)
    final Map<Fifo, Integer> fifoToSizeVariableID = new LinkedHashMap<>();
    // create size variables/equations
    for (final FifoAbstraction fifoAbs : ddg.edgeSet()) {
      for (final Fifo fifo : fifoAbs.fifos) {
        // create size variable and underflow and overflow expression
      }
    }

    // objective function (minimize buffer sizes + phi)

    // TODO build a schedule using the normalized graph II and each actor offset (computed by the ILP)

    throw new PreesmRuntimeException("This analysis is not yet completely implemented, stopping here.");
  }

}
