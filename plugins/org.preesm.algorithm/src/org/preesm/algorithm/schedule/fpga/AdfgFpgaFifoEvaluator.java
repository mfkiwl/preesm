package org.preesm.algorithm.schedule.fpga;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.math3.fraction.BigFraction;
import org.eclipse.xtext.xbase.lib.Pair;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.cycle.PatonCycleBase;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Optimisation.State;
import org.ojalgo.optimisation.Variable;
import org.preesm.algorithm.mapper.ui.stats.IStatGenerator;
import org.preesm.algorithm.pisdf.autodelays.AbstractGraph;
import org.preesm.algorithm.pisdf.autodelays.AbstractGraph.FifoAbstraction;
import org.preesm.commons.exceptions.PreesmRuntimeException;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.commons.math.MathFunctionsHelper;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.AbstractVertex;
import org.preesm.model.pisdf.DataPort;
import org.preesm.model.pisdf.Delay;
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

  public static final String FIFO_EVALUATOR_ADFG     = "adfgFifoEval";
  public static final int    MAX_BIT_LENGTHS_FRACION = 10;

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

    // create intermediate FifoAbstraction graphs
    final DefaultDirectedGraph<AbstractActor, FifoAbstraction> ddg = AbstractGraph.createAbsGraph(flatGraph, brv);
    final DefaultUndirectedGraph<AbstractActor, FifoAbstraction> dug = AbstractGraph.undirectedGraph(ddg);

    // Perform rounding of actors II ratios to avoid overflow in ADFG channel computation
    // TODO perform additional optimization on n/d ratios
    final Deque<FifoAbstraction> workList = new ArrayDeque<>();
    workList.addAll(dug.edgeSet());
    while (!workList.isEmpty()) {
      FifoAbstraction fifoAbs = workList.pop();
      final AbstractActor src = ddg.getEdgeSource(fifoAbs);
      final AbstractActor tgt = ddg.getEdgeTarget(fifoAbs);
      final long srcII = mapActorNormalizedInfos.get(src).oriII;
      final long tgtII = mapActorNormalizedInfos.get(tgt).oriII;
      if (srcII != tgtII) {
        final long min = Math.min(srcII, tgtII);
        final long max = Math.max(srcII, tgtII);
        final long factor = Math.round((double) max / min);
        final long diff = max - min * factor;
        if (diff != 0 && (double) Math.abs(diff) / max < 0.05) {
          final long updatedMin;
          final long updatedMax;
          if (diff < 0 || (double) Math.abs(diff) / min < 0.05) {
            updatedMin = (max + factor - 1) / factor;
            updatedMax = updatedMin * factor;
          } else {
            updatedMin = min;
            updatedMax = max + min - diff;
          }
          final long updatedSrcII = srcII == min ? updatedMin : updatedMax;
          final long updatedTgtII = tgtII == min ? updatedMin : updatedMax;
          updateIIInfo(mapActorNormalizedInfos, src, updatedSrcII);
          updateIIInfo(mapActorNormalizedInfos, tgt, updatedTgtII);
          workList.addAll(dug.edgesOf(src));
          workList.addAll(dug.edgesOf(tgt));
        }
      }
    }

    // Increase actor II for small differences to avoid overflow in ADFG cycle computation
    final List<ActorNormalizedInfos> listInfos = new ArrayList<>(mapActorNormalizedInfos.values());
    Collections.sort(listInfos, new DecreasingActorIIComparator());
    for (int i = 0; i < listInfos.size() - 1; i++) {
      ActorNormalizedInfos current = listInfos.get(i);
      ActorNormalizedInfos next = listInfos.get(i + 1);
      if (current.oriII != next.oriII && current.oriII / next.oriII < 1.01) {
        updateIIInfo(mapActorNormalizedInfos, next.aa, current.oriII);
        listInfos.set(i + 1, mapActorNormalizedInfos.get(next.aa));
      }
    }

    // compute the lambda of each actor
    final Map<DataPort, BigFraction> lambdaPerPort = computeAndLogLambdas(mapActorNormalizedInfos);

    // compute the fifo sizes thanks to the ARS ILP formulation of ADFG
    // ILP stands for Integer Linear Programming
    // ARS stands for Affine Relation Synthesis
    // ADFG stands for Affine DataFlow Graph (work of Adnan Bouakaz)
    // ojAlgo dependency should be used to create the model because it has dedicated code to ILP,
    // or Choco (but not dedicated to ILP) at last resort.

    // build model
    // create Maps to retrieve ID of variables (ID in order of addition in the model)
    final ExpressionsBasedModel model = new ExpressionsBasedModel();

    // FifoAbstraction to phi Variable ID
    final Map<FifoAbstraction, Integer> fifoAbsToPhiVariableID = new LinkedHashMap<>();
    for (final FifoAbstraction fifoAbs : dug.edgeSet()) {
      final int index = fifoAbsToPhiVariableID.size();
      fifoAbsToPhiVariableID.put(fifoAbs, index);
      // we separate neg. from pos. because unsure that ojAlgo handles negative integers
      final Variable varPhiPos = new Variable("phi_pos_" + index);
      varPhiPos.setInteger(true);

      if (fifoAbs.isFullyDelayed()) {
        varPhiPos.lower(0L);
      } else {
        // Add phi to represent delay before token production, production happens only during II cycles.
        final AbstractActor src = ddg.getEdgeSource(fifoAbs);
        final long srcII = mapActorNormalizedInfos.get(src).oriII;
        final long srcET = mapActorNormalizedInfos.get(src).oriET;
        varPhiPos.lower(srcET - srcII);
      }

      model.addVariable(varPhiPos);
      PreesmLogger.getLogger()
          .fine("Created variable " + varPhiPos.getName() + " for fifo abs rep " + fifoAbs.fifos.get(0).getId());
      final Variable varPhiNeg = new Variable("phi_neg_" + index);
      varPhiNeg.setInteger(true);
      varPhiNeg.lower(0L);
      // note that we cannot set an upper limit to both neg and post part, ojAlgo bug?!
      model.addVariable(varPhiNeg);
    }

    // create intermediate AffineRelation graph and cycle lists
    final DefaultDirectedGraph<AbstractActor,
        AffineRelation> ddgAR = buildGraphAR(ddg, dug, mapActorNormalizedInfos, fifoAbsToPhiVariableID);
    final Set<
        GraphPath<AbstractActor, FifoAbstraction>> cyclesGP = new PatonCycleBase<AbstractActor, FifoAbstraction>(dug)
            .getCycleBasis().getCyclesAsGraphPaths();
    final Set<List<AbstractActor>> cyclesAA = new LinkedHashSet<>();
    cyclesGP.forEach(gp -> cyclesAA.add(gp.getVertexList()));

    // add equations for cycles to the model
    for (final List<AbstractActor> cycleAA : cyclesAA) {
      generateCycleConstraint(ddgAR, cycleAA, model);
    }

    // Fifo to delta/size Variable ID (theta/delay is fixed for us, so not a variable)
    // before using this ID, we must offset it by the number of phi variables (twice the number of FAs)
    final Map<Fifo, Integer> fifoToSizeVariableID = new LinkedHashMap<>();
    // create size variables/equations
    for (final FifoAbstraction fa : ddg.edgeSet()) {
      final AbstractActor src = ddg.getEdgeSource(fa);
      final AbstractActor tgt = ddg.getEdgeTarget(fa);
      final AffineRelation ar = ddgAR.getEdge(src, tgt);

      for (final Fifo fifo : fa.fifos) {
        // create size variable and underflow and overflow expression, set objective
        generateChannelConstraint(scenario, model, fifoToSizeVariableID, mapActorNormalizedInfos, lambdaPerPort, fifo,
            ar);
      }
    }

    logModel(model);
    // call objective function (minimize buffer sizes + phi)
    final Result modelResult = model.minimise();
    final StringBuilder sbLogResult = new StringBuilder("-- variable final values: " + model.countVariables() + "\n");
    for (int i = 0; i < model.countVariables(); i++) {
      final Variable v = model.getVariable(i);
      sbLogResult.append("var " + v.getName() + " integer = " + modelResult.get(i) + ";\n");
    }
    PreesmLogger.getLogger().fine(sbLogResult::toString);

    final State modelState = modelResult.getState();
    if (modelState != State.OPTIMAL && !model.getVariables().isEmpty()) {
      throw new PreesmRuntimeException("ILP result was not optimal state: " + modelState
          + ".\n Check consistency or retry with extra delays on feedback FIFO buffers.");
    }

    // fill FIFO sizes map result in number of elements
    final Map<Fifo, Long> computedFifoSizes = new LinkedHashMap<>();
    final int indexOffset = 2 * ddg.edgeSet().size(); // offset for phi
    fifoToSizeVariableID.forEach((k, v) -> {
      final long sizeInElts = modelResult.get((long) v + indexOffset).longValue();
      final long typeSizeBits = scenario.getSimulationInfo().getDataTypeSizeInBit(k.getType());
      computedFifoSizes.put(k, sizeInElts * typeSizeBits);
    });

    // TODO build a schedule using the normalized graph II and each actor offset (computed by the ILP)
    // same ILP as in ADFG but not fixing Tbasis: only fixing all T being greater than 1
    // result will be a period in number of cycles and will be overestimated, seems not useful
    return new Pair<>(null, computedFifoSizes);
  }

  /**
   * Update II Information in ActorNormalizedInfos map
   * 
   * @param mapActorNormalizedInfos
   *          map to be updated
   * @param aa
   *          actor to update
   * @param ii
   *          new II
   */
  private void updateIIInfo(final Map<AbstractActor, ActorNormalizedInfos> mapActorNormalizedInfos,
      final AbstractActor aa, final long ii) {
    ActorNormalizedInfos ori = mapActorNormalizedInfos.get(aa);
    final long updatedET = Math.max(ori.oriET, ii);
    ActorNormalizedInfos updated = new ActorNormalizedInfos(ori.aa, ori.ori, updatedET, ii, ori.brv);
    mapActorNormalizedInfos.put(ori.aa, updated);
  }

  /**
   * Compute and log all lambda (as map per data port). Lambda are symmetrical: upper = lower.
   * 
   * @param mapActorNormalizedInfos
   *          Standard information about actors, used to get II.
   * @return Map of lambda per data port of all actors in the given map.
   */
  protected static Map<DataPort, BigFraction>

      computeAndLogLambdas(final Map<AbstractActor, ActorNormalizedInfos> mapActorNormalizedInfos) {
    final Map<DataPort, BigFraction> lambdaPerPort = new LinkedHashMap<>();
    final StringBuilder logLambda = new StringBuilder(
        "Lambda of actor ports (in number of tokens between 0 and the rate, the closest to 0 the better):\n");

    final List<DataPort> negativeDP = new ArrayList<>();
    mapActorNormalizedInfos.values().forEach(ani -> {
      logLambda.append(String.format("/actor <%s>\n", ani.aa.getName()));

      final String logLambdaPorts = ani.aa.getAllDataPorts().stream().map(dp -> {
        final long rate = dp.getExpression().evaluate();
        final BigFraction lambdaFr = new BigFraction(-rate, ani.oriII).add(1L).multiply(rate);
        lambdaPerPort.put(dp, lambdaFr);
        final double valD = lambdaFr.doubleValue();
        if (valD < 0d) {
          negativeDP.add(dp);
        }
        return String.format(Locale.US, "%s: %4.2e", dp.getName(), valD);
      }).collect(Collectors.joining(", "));

      logLambda.append(logLambdaPorts + "\n");
    });
    PreesmLogger.getLogger().info(logLambda::toString);
    if (!negativeDP.isEmpty()) {
      throw new PreesmRuntimeException(
          "Some lambda were negative which means that they produce more than 1 bit per cycle. "
              + "Please increase the Initiation Interval of corresponding actors in the scenario to fix that..");
    }
    return lambdaPerPort;
  }

  /**
   * Log expressions in the model, and variable domain.
   * 
   * @param model
   *          Model to log (expressions).
   */
  protected static void logModel(final ExpressionsBasedModel model) {
    final StringBuilder sbLogModel = new StringBuilder(
        "Details of ILP model (compatible with GNU MathProg Language Reference).\n");
    sbLogModel.append("-- variable initial domain:\n");
    model.getVariables().stream().forEach(v -> sbLogModel
        .append("var " + v.getName() + " integer >= " + v.getLowerLimit() + ", <= " + v.getUpperLimit() + ";\n"));
    sbLogModel.append("minimize o: ");
    sbLogModel.append(model.getVariables().stream().map(v -> v.getContributionWeight() + "*" + v.getName())
        .collect(Collectors.joining(" + ")));
    sbLogModel.append(";\n-- constraints: " + model.countExpressions() + "\n");
    for (final Expression exp : model.getExpressions()) {
      sbLogModel.append("subject to " + exp.getName() + ": " + exp.getLowerLimit() + " <= ");
      sbLogModel.append(exp.getLinearEntrySet().stream()
          .map(e -> e.getValue().longValue() + "*" + model.getVariable(e.getKey()).getName())
          .collect(Collectors.joining(" + ")) + ";\n");
    }
    PreesmLogger.getLogger().fine(sbLogModel::toString);
  }

  protected static class AffineRelation {
    protected final long    nProd;
    protected final long    dCons;
    protected final int     phiIndex;
    protected final boolean phiNegate;

    protected AffineRelation(final long nProd, final long dCons, final int phiIndex, final boolean phiNegate) {
      this.nProd = nProd;
      this.dCons = dCons;
      this.phiIndex = phiIndex;
      this.phiNegate = phiNegate;
    }

  }

  /**
   * Builds a directed graph with affine relation information. Each edge is doubled (in a direction and in the opposite,
   * even if only one direction is present in the original graph).
   * 
   * @param ddg
   *          Abstract directed simple graph.
   * @param dug
   *          Abstract undirected simple graph.
   * @param mapActorNormalizedInfos
   *          Map of actor general informations, used to get II.
   * @param fifoAbsToPhiVariableID
   *          Map from edges in the undirected graph to the phi variable index in the model.
   * @return Directed simple graph of doubled affine relation (one in each direction).
   */
  protected static DefaultDirectedGraph<AbstractActor, AffineRelation> buildGraphAR(
      final DefaultDirectedGraph<AbstractActor, FifoAbstraction> ddg,
      final DefaultUndirectedGraph<AbstractActor, FifoAbstraction> dug,
      final Map<AbstractActor, ActorNormalizedInfos> mapActorNormalizedInfos,
      final Map<FifoAbstraction, Integer> fifoAbsToPhiVariableID) {
    DefaultDirectedGraph<AbstractActor, AffineRelation> ddgAR = new DefaultDirectedGraph<>(AffineRelation.class);
    for (final AbstractActor aa : ddg.vertexSet()) {
      ddgAR.addVertex(aa);
    }
    for (final FifoAbstraction fa : dug.edgeSet()) {
      final AbstractActor src = ddg.getEdgeSource(fa);
      final AbstractActor tgt = ddg.getEdgeTarget(fa);
      final long srcII = mapActorNormalizedInfos.get(src).oriII;
      final long tgtII = mapActorNormalizedInfos.get(tgt).oriII;
      final long nProd = fa.getProdRate() * tgtII;
      final long dCons = fa.getConsRate() * srcII;
      final long gcd = MathFunctionsHelper.gcd(nProd, dCons);
      final AffineRelation ar = new AffineRelation(nProd / gcd, dCons / gcd, fifoAbsToPhiVariableID.get(fa), false);
      ddgAR.addEdge(src, tgt, ar);
      if (src != tgt) {
        final AffineRelation arReverse = new AffineRelation(ar.dCons, ar.nProd, ar.phiIndex, true);
        ddgAR.addEdge(tgt, src, arReverse);
      }
    }

    return ddgAR;
  }

  /**
   * Fill the model with cycle equations.
   * <p>
   * This method is adapted from the ADFG software. See Adnan Bouakaz thesis p. 70, proposition 2.8 .
   * 
   * @param ddgAR
   *          Directed simple graph of Affine Relation;
   * @param cycleAA
   *          Cycle to consider.
   * @param model
   *          Model where to add the constraint.
   */
  protected static void generateCycleConstraint(final DefaultDirectedGraph<AbstractActor, AffineRelation> ddgAR,
      final List<AbstractActor> cycleAA, final ExpressionsBasedModel model) {
    // cycle is redundant (last == first)
    final int cycleSize = cycleAA.size();
    if (cycleSize <= 2) {
      // since cycle is redundant, then only two actors in it means it is a selfloop
      // phi for self loops is forced to be positive
      if (cycleSize == 2 && cycleAA.get(0) == cycleAA.get(1)) {
        final AffineRelation ar = ddgAR.getEdge(cycleAA.get(0), cycleAA.get(1));
        final int index_2 = ar.phiIndex * 2;
        // TODO force the value oh phi to be II?
        // final Expression expPhiPos = model.addExpression().level(1L);
        // final Variable varPhiPos = model.getVariable(index_2);
        // expPhiPos.set(varPhiPos, 1L);
        final Expression expPhiNeg = model.addExpression().level(0L);
        final Variable varPhiNeg = model.getVariable(index_2 + 1);
        expPhiNeg.set(varPhiNeg, 1L);
        return;
      }
      throw new PreesmRuntimeException("While building model, one cycle could not be considered: "
          + cycleAA.stream().map(AbstractActor::getName).collect(Collectors.joining(" --> ")));
    }
    // the constraint expression must be always equal to 0
    final Expression expression = model.addExpression().level(0L);
    // init arrays storing coefs for memoization
    final AffineRelation[] ars = new AffineRelation[cycleSize - 1];
    // final long[] coefsPhi = new long[cycleSize - 1];
    final BigInteger[] coefsPhi = new BigInteger[cycleSize - 1];
    for (int i = 0; i < coefsPhi.length; ++i) {
      coefsPhi[i] = BigInteger.ONE;
    }
    int nbPhi = 0;
    long mulN = 1;
    long mulD = 1;
    // update all memoized coefs
    // Algorithm is applying required coefficient to all phi at once, which is equivalent to ADFG proposition 2.8
    final Iterator<AbstractActor> aaIterator = cycleAA.iterator();
    AbstractActor dest = aaIterator.next();
    while (aaIterator.hasNext()) {
      final AbstractActor src = dest;
      dest = aaIterator.next();
      final AffineRelation ar = ddgAR.getEdge(src, dest);
      ars[nbPhi] = ar;

      for (int i = 0; i < nbPhi; ++i) {
        coefsPhi[i] = coefsPhi[i].multiply(BigInteger.valueOf(ar.nProd));
      }
      for (int i = nbPhi + 1; i < coefsPhi.length; ++i) {
        coefsPhi[i] = coefsPhi[i].multiply(BigInteger.valueOf(ar.dCons));
      }
      mulN *= ar.nProd;
      mulD *= ar.dCons;
      long g = MathFunctionsHelper.gcd(mulN, mulD);
      mulN /= g;
      mulD /= g;
      BigInteger gb = coefsPhi[0];
      for (int i = 1; i < coefsPhi.length; ++i) {
        gb = gb.gcd(coefsPhi[i]);
      }
      for (int i = 0; i < coefsPhi.length; ++i) {
        coefsPhi[i] = coefsPhi[i].divide(gb);
      }
      ++nbPhi;
    }

    if (mulN != mulD) {
      throw new PreesmRuntimeException("Some cycles do not satisfy consistency Part 1.");
    }
    // create equation
    for (int i = 0; i < ars.length; ++i) {
      final long coefSign = ars[i].phiNegate ? -1L : 1L;
      final int index_2 = ars[i].phiIndex * 2;
      final Variable varPhiPos = model.getVariable(index_2);
      final long coefPhi = coefsPhi[i].longValueExact();
      expression.set(varPhiPos, coefPhi * coefSign);
      final Variable varPhiNeg = model.getVariable(index_2 + 1);
      expression.set(varPhiNeg, coefPhi * (-coefSign));
    }

  }

  /**
   * Fill the model with underflow and overflow equations. Also set the minimization objective.
   * <p>
   * See Adnan Bouakaz thesis p. 72-78.
   * 
   * @param scenario
   *          Scenario used to get FIFO data sizes.
   * @param model
   *          Model to consider.
   * @param fifoToSizeVariableID
   *          Map of fifo to variable index in model (to be updated).
   * @param mapActorNormalizedInfos
   *          Map of actor general informations, used to get II.
   * @param lambdaPerPort
   *          Map of port to lambda to consider.
   * @param fifo
   *          Fifo to consider.
   * @param ar
   *          Affine Relation to consider.
   */
  protected static void generateChannelConstraint(final Scenario scenario, final ExpressionsBasedModel model,
      final Map<Fifo, Integer> fifoToSizeVariableID,
      final Map<AbstractActor, ActorNormalizedInfos> mapActorNormalizedInfos,
      final Map<DataPort, BigFraction> lambdaPerPort, final Fifo fifo, final AffineRelation ar) {
    final int index = fifoToSizeVariableID.size();
    final Variable sizeVar = new Variable("size_" + index);
    PreesmLogger.getLogger().fine(() -> "Created variable " + sizeVar.getName() + " for fifo " + fifo.getId());
    sizeVar.setInteger(true);
    sizeVar.lower(2L); // could be refined to max(prod, cons, delau)
    // ojAlgo seems to bug if we set upper limit above Integer.MAX_VALUE
    model.addVariable(sizeVar);
    fifoToSizeVariableID.put(fifo, index);
    // write objective for data size to be minimized
    // weighted by type size (used only for objective)
    final long typeSizeBits = scenario.getSimulationInfo().getDataTypeSizeInBit(fifo.getType());
    sizeVar.weight(typeSizeBits);
    // write objective for phase to be minimized (weight for positive and negative part should be equal)
    final Variable phi_pos = model.getVariable(ar.phiIndex * 2);
    final Variable phi_neg = model.getVariable(ar.phiIndex * 2 + 1);
    final BigDecimal rawCurrentPhiPosWeight = phi_pos.getContributionWeight();
    final long currentPhiPosWeight = rawCurrentPhiPosWeight != null ? rawCurrentPhiPosWeight.longValue() : 0L;
    final long fifoProdSize = fifo.getSourcePort().getExpression().evaluate();
    long ceilPhiRatio = typeSizeBits * ((fifoProdSize + ar.dCons - 1L) / ar.dCons);
    phi_pos.weight(currentPhiPosWeight + ceilPhiRatio);
    phi_neg.weight(currentPhiPosWeight + ceilPhiRatio);
    // compute delay if any
    final Delay delay = fifo.getDelay();
    long delaySize = 0L;
    if (delay != null) {
      delaySize = delay.getExpression().evaluate();
    }
    // compute coefficients: lambda and others
    final BigFraction lambda_p = lambdaPerPort.get(fifo.getSourcePort());
    final BigFraction lambda_c = lambdaPerPort.get(fifo.getTargetPort());
    final BigFraction lambda_sum = lambda_p.add(lambda_c);
    final AbstractActor src = fifo.getSourcePort().getContainingActor();
    final AbstractActor tgt = fifo.getTargetPort().getContainingActor();
    final long srcII = mapActorNormalizedInfos.get(src).oriII;
    final long tgtII = mapActorNormalizedInfos.get(tgt).oriII;
    final BigFraction a_p = new BigFraction(fifoProdSize, srcII);
    final BigFraction a_c = new BigFraction(fifo.getTargetPort().getExpression().evaluate(), tgtII);
    // get phi variables
    final long coefSign = ar.phiNegate ? -1L : 1L;
    final int index_2 = ar.phiIndex * 2;
    final Variable varPhiPos = model.getVariable(index_2);
    final Variable varPhiNeg = model.getVariable(index_2 + 1);
    final StringBuilder constantsLog = new StringBuilder("n = " + ar.nProd + " d = " + ar.dCons + "\n");
    constantsLog.append("a_p = " + a_p + " a_c = " + a_c + "\n");
    // compute common coefficients
    final BigFraction a_cOverd = a_c.divide(ar.dCons);
    final BigFraction fractionConstant = lambda_sum.add(a_cOverd.multiply(ar.nProd + ar.dCons - 1L));
    // write underflow constraint
    final BigFraction fractionSumConstantU = fractionConstant.subtract(delaySize).multiply(a_cOverd.reciprocal());
    final long sumConstantU = fractionSumConstantU.getNumerator().longValueExact();
    constantsLog.append("ConstantU = " + sumConstantU + "\n");
    final Expression expressionU = model.addExpression().lower(sumConstantU);
    final long coefPhiU = fractionSumConstantU.getDenominator().longValueExact();
    constantsLog.append("CoefPhiU = " + coefPhiU + "\n");
    expressionU.set(varPhiPos, coefPhiU * coefSign);
    expressionU.set(varPhiNeg, coefPhiU * (-coefSign));
    // write overflow constraint
    final BigFraction fractionSumConstantO = fractionConstant.add(delaySize).multiply(a_cOverd.reciprocal());
    final BigFraction fractionCoefSize = a_cOverd.reciprocal();
    final long sumConstantO = ceiling(fractionSumConstantO).longValueExact();
    final long coefPhiO = 1;
    final long coefSize = floor(fractionCoefSize).longValueExact();
    constantsLog.append("ConstantO = " + sumConstantO + "\n");
    constantsLog.append("CoefPhiO = " + coefPhiO + "\n");
    constantsLog.append("CoefSize = " + coefSize + "\n");
    final Expression expressionO = model.addExpression().lower(sumConstantO);
    expressionO.set(varPhiPos, coefPhiO * (-coefSign));
    expressionO.set(varPhiNeg, coefPhiO * (coefSign));
    expressionO.set(sizeVar, coefSize);
    PreesmLogger.getLogger().fine(constantsLog::toString);
  }

  private static BigInteger ceiling(BigFraction frac) {
    return frac.getNumerator().add(frac.getDenominator()).subtract(BigInteger.ONE).divide(frac.getDenominator());
  }

  private static BigInteger floor(BigFraction frac) {
    return frac.getNumerator().divide(frac.getDenominator());
  }

  public static class DecreasingActorIIComparator implements Comparator<ActorNormalizedInfos> {
    @Override
    public int compare(ActorNormalizedInfos arg0, ActorNormalizedInfos arg1) {
      return Long.compare(arg1.oriII, arg0.oriII);
    }
  }
}
