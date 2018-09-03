/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2008 - 2018) :
 *
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2017 - 2018)
 * Clément Guy <clement.guy@insa-rennes.fr> (2014)
 * Matthieu Wipliez <matthieu.wipliez@insa-rennes.fr> (2008)
 * Maxime Pelcat <maxime.pelcat@insa-rennes.fr> (2008 - 2012)
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
package org.ietr.preesm.mapper.algo.fast;

import java.util.ArrayList;
import java.util.List;
import org.ietr.dftools.architecture.slam.Design;
import org.ietr.dftools.workflow.WorkflowException;
import org.ietr.preesm.core.scenario.PreesmScenario;
import org.ietr.preesm.mapper.abc.AbcType;
import org.ietr.preesm.mapper.abc.IAbc;
import org.ietr.preesm.mapper.abc.edgescheduling.EdgeSchedType;
import org.ietr.preesm.mapper.abc.impl.latency.InfiniteHomogeneousAbc;
import org.ietr.preesm.mapper.abc.taskscheduling.TopologicalTaskSched;
import org.ietr.preesm.mapper.algo.list.InitialLists;
import org.ietr.preesm.mapper.model.MapperDAG;
import org.ietr.preesm.mapper.params.AbcParameters;
import org.ietr.preesm.mapper.params.FastAlgoParameters;

// TODO: Auto-generated Javadoc
/**
 * Population for genetic algorithm generated by FAST algorithm.
 *
 * @author pmenuet
 */
public class FastPopulation {

  /** The population num. */
  // Number of individual in the population
  private int populationNum;

  /** The population. */
  // List of mapperDAG constituting the population
  private List<MapperDAG> population;

  /** The abc params. */
  // Simulator used to make this population
  private AbcParameters abcParams;

  /** The archi. */
  // architecture used to make this population
  private Design archi;

  // private IScenario scenario;

  /**
   * Constructors.
   */

  public FastPopulation() {
    super();

  }

  /**
   * Instantiates a new fast population.
   *
   * @param populationNum
   *          the population num
   * @param abcParams
   *          the abc params
   * @param archi
   *          the archi
   * @param scenario
   *          the scenario
   */
  public FastPopulation(final int populationNum, final AbcParameters abcParams, final Design archi,
      final PreesmScenario scenario) {
    super();
    this.populationNum = populationNum;
    this.abcParams = abcParams;
    this.archi = archi;
    this.population = new ArrayList<>();
    // this.scenario = scenario;
  }

  /**
   * Instantiates a new fast population.
   *
   * @param populationNum
   *          the population num
   * @param population
   *          the population
   * @param abcParams
   *          the abc params
   * @param archi
   *          the archi
   */
  public FastPopulation(final int populationNum, final List<MapperDAG> population, final AbcParameters abcParams,
      final Design archi) {
    super();
    this.populationNum = populationNum;
    this.population = population;
    this.abcParams = abcParams;
    this.archi = archi;
  }

  /**
   * Getters and setters.
   *
   * @return the archi
   */

  public Design getArchi() {
    return this.archi;
  }

  /**
   * Sets the archi.
   *
   * @param archi
   *          the new archi
   */
  public void setArchi(final Design archi) {
    this.archi = archi;
  }

  /**
   * Gets the population num.
   *
   * @return the population num
   */
  public int getPopulationNum() {
    return this.populationNum;
  }

  /**
   * Sets the population num.
   *
   * @param populationNum
   *          the new population num
   */
  public void setPopulationNum(final int populationNum) {
    this.populationNum = populationNum;
  }

  /**
   * Gets the population.
   *
   * @return the population
   */
  public List<MapperDAG> getPopulation() {
    return this.population;
  }

  /**
   * Sets the population.
   *
   * @param population
   *          the new population
   */
  public void setPopulation(final List<MapperDAG> population) {
    this.population = population;
  }

  /**
   * constructPopulation = run the fast algorithm as many times it is necessary to make the population.
   *
   * @param dag
   *          the dag
   * @param scenario
   *          the scenario
   * @param fastParams
   *          the fast params
   * @throws WorkflowException
   *           the workflow exception
   */
  public void constructPopulation(final MapperDAG dag, final PreesmScenario scenario,
      final FastAlgoParameters fastParams) throws WorkflowException {

    // create the population
    final List<MapperDAG> temp = new ArrayList<>();

    // PopulationNum times
    for (int i = 0; i < getPopulationNum(); i++) {

      MapperDAG tempdag = null;
      tempdag = dag.clone();

      // perform the initialization
      final IAbc simu = new InfiniteHomogeneousAbc(
          new AbcParameters(AbcType.InfiniteHomogeneous, EdgeSchedType.Simple, false), tempdag, getArchi(), scenario);
      final InitialLists initialLists = new InitialLists();
      initialLists.constructInitialLists(tempdag, simu);

      final TopologicalTaskSched taskSched = new TopologicalTaskSched(simu.getTotalOrder());
      simu.resetDAG();

      // perform the fast algo
      final FastAlgorithm algorithm = new FastAlgorithm(initialLists, scenario);
      tempdag = algorithm
          .map("population", this.abcParams, fastParams, tempdag, this.archi, false, true, false, null, taskSched)
          .clone();
      temp.add(tempdag.clone());

    }
    this.population.addAll(temp);
  }
}
