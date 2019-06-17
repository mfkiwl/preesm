/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2008 - 2019) :
 *
 * Alexandre Honorat <alexandre.honorat@insa-rennes.fr> (2018)
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2017 - 2019)
 * Daniel Madroñal <daniel.madronal@upm.es> (2018 - 2019)
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
package org.preesm.codegen.xtend.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.preesm.algorithm.mapper.model.MapperDAG;
import org.preesm.algorithm.mapper.model.MapperDAGVertex;
import org.preesm.algorithm.model.AbstractGraph;
import org.preesm.algorithm.model.dag.DAGVertex;
import org.preesm.algorithm.model.dag.DirectedAcyclicGraph;
import org.preesm.codegen.model.CodegenFactory;
import org.preesm.codegen.model.Constant;
import org.preesm.codegen.model.ConstantString;
import org.preesm.codegen.model.PapifyAction;
import org.preesm.commons.model.PreesmCopyTracker;
import org.preesm.model.pisdf.AbstractActor;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.scenario.PapiEvent;
import org.preesm.model.scenario.PapiEventModifier;
import org.preesm.model.scenario.PapifyConfig;
import org.preesm.model.scenario.Scenario;
import org.preesm.model.scenario.ScenarioFactory;

/**
 * The Class PapifyEngine.
 */
public class PapifyEngine {

  /** The PAPIFY_CONFIGURATION constant **/
  static final String PAPIFY_CONFIGURATION   = "papifyConfig";
  static final String PAPIFY_ACTION_NAME     = "papifyActionName";
  static final String PAPIFY_CONFIG_NUMBER   = "papifyConfigNumber";
  static final String PAPIFY_MONITOR_EVENTS  = "papifyMonitorEvents";
  static final String PAPIFY_MONITOR_TIMING  = "papifyMonitorTiming";
  static final String PAPIFY_COMPONENT_NAME  = "papifyComponentName";
  static final String PAPIFY_ACTOR_NAME      = "papifyActorName";
  static final String PAPIFY_CODESET_SIZE    = "papifyCodeSetSize";
  static final String PAPIFY_EVENTSET_NAMES  = "papifyEventSetNames";
  static final String PAPIFY_COUNTER_CONFIGS = "papifyCounterConfigs";

  /** The variables to avoid configuration repetitions **/
  Map<AbstractActor, EMap<String, EList<PapiEvent>>> configSet   = new LinkedHashMap<>();
  PapiEvent                                          timingEvent = ScenarioFactory.eINSTANCE.createPapiEvent();

  /** The PREESM scenario **/
  private final Scenario scenario;

  /** The original DAG **/
  private final MapperDAG dag;

  /**
   * Initialize the PapifyEngine
   *
   * @param dag
   *          the input dag
   * @param scenario
   *          the input scenario
   */
  public PapifyEngine(final MapperDAG dag, final Scenario scenario) {
    this.dag = dag;
    this.scenario = scenario;
  }

  /**
   * Function to add (or not) everything required for PAPIFY
   */
  private void configurePapifyFunctions(AbstractActor vertex, String info, String name, String actionName) {

    // Variables to check whether an actor has a monitoring configuration
    PapifyConfig papifyConfig = null;
    EMap<String, EList<PapiEvent>> config;
    Set<String> comp;
    List<PapiEvent> events;
    List<PapiEvent> includedEvents = new ArrayList<>();

    boolean configAdded = false;
    String configPosition = "";
    int counterConfigs = 0;

    papifyConfig = this.scenario.getPapifyConfig();

    String message = "Papifying";
    String finalName;

    finalName = info;

    if (finalName != null) {
      config = papifyConfig.getPapifyConfigGroupsActors().get(vertex);
      finalName = PreesmCopyTracker.getOriginalSource(vertex).getVertexPath().substring(info.indexOf('/') + 1);
      finalName = finalName.replace('/', '_');
      if (config != null && !config.keySet().isEmpty()) {
        configPosition = "";
        Map<String,
            String> mapMonitorTiming = this.dag.getVertex(name).getPropertyBean().getValue(PAPIFY_MONITOR_TIMING);
        if (mapMonitorTiming == null) {
          mapMonitorTiming = new LinkedHashMap<>();
        }
        mapMonitorTiming.put(info, "No");
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_MONITOR_TIMING, mapMonitorTiming);
        if (config.containsKey("Timing") && ECollections.indexOf(config.get("Timing"), this.timingEvent, 0) != -1) {
          mapMonitorTiming.put(info, "Yes");
          this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_MONITOR_TIMING, mapMonitorTiming);
        }

        // Check if the current monitoring has already been included
        counterConfigs = 0;
        for (String compNewConfig : config.keySet()) {
          if (!compNewConfig.equals("Timing")) {
            List<PapiEvent> eventSetNew = config.get(compNewConfig);
            configAdded = false;
            for (Entry<AbstractActor, EMap<String, EList<PapiEvent>>> tmp : this.configSet.entrySet()) {
              for (String compConfigTmp : tmp.getValue().keySet()) {
                if (!compConfigTmp.equals("Timing")) {
                  List<PapiEvent> eventSetTmp = tmp.getValue().get(compConfigTmp);
                  if (EcoreUtil.equals(eventSetTmp, eventSetNew)) {
                    configAdded = true;
                    counterConfigs = counterConfigs + 1;
                    if (configPosition.equals("")) {
                      configPosition = Integer.toString(new ArrayList<>(this.configSet.keySet()).indexOf(tmp.getKey()));
                    } else {
                      configPosition = configPosition.concat(",")
                          .concat(Integer.toString(new ArrayList<>(this.configSet.keySet()).indexOf(tmp.getKey())));
                    }
                  }
                }
              }
            }
            if (!configAdded) {
              if (!this.configSet.containsKey(vertex)) {
                this.configSet.put(vertex, ECollections.asEMap(new LinkedHashMap<>()));
              }
              this.configSet.get(vertex).put(compNewConfig, config.get(compNewConfig));
              counterConfigs = counterConfigs + 1;

              if (configPosition.equals("")) {
                configPosition = Integer.toString(new ArrayList<>(this.configSet.keySet()).indexOf(vertex));
              } else {
                configPosition = configPosition.concat(",")
                    .concat(Integer.toString(new ArrayList<>(this.configSet.keySet()).indexOf(vertex)));
              }
            }
          }
        }

        // The variable to store the monitoring
        PapifyAction papifyActionName = CodegenFactory.eINSTANCE.createPapifyAction();
        papifyActionName.setName("papify_actions_".concat(actionName));

        // Set the id associated to the Papify configuration
        // Add the PAPI component name
        ConstantString papifyConfigNumber = CodegenFactory.eINSTANCE.createConstantString();
        papifyConfigNumber.setName("PAPIFY_configs_".concat(name));
        papifyConfigNumber.setValue(configPosition);
        papifyConfigNumber.setComment("PAPIFY actor configs");

        // Get component
        comp = config.keySet();
        String eventNames = "";
        String compNames = "";
        includedEvents.clear();

        // At the beginning there is no monitoring
        Map<String,
            String> mapMonitorEvents = this.dag.getVertex(name).getPropertyBean().getValue(PAPIFY_MONITOR_EVENTS);
        if (mapMonitorEvents == null) {
          mapMonitorEvents = new LinkedHashMap<>();
        }
        mapMonitorEvents.put(info, "No");
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_MONITOR_EVENTS, mapMonitorEvents);
        for (final String key : comp) {
          if (!key.equals("Timing")) {
            events = config.get(key);

            for (PapiEvent singleEvent : events) {
              includedEvents.add(singleEvent);
              // Monitoring events
              mapMonitorEvents.put(info, "Yes");
              this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_MONITOR_EVENTS, mapMonitorEvents);
              if (eventNames.equals("")) {
                eventNames = singleEvent.getName();
              } else {
                eventNames = eventNames.concat("," + singleEvent.getName());
              }
            }
            mapMonitorEvents.put(info, "Yes");
            this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_MONITOR_EVENTS, mapMonitorEvents);
            if (compNames.equals("")) {
              compNames = key;
            } else {
              compNames = compNames.concat("," + key);
            }
          }
        }

        // Add the PAPI component name
        ConstantString componentName = CodegenFactory.eINSTANCE.createConstantString();
        componentName.setName("component_name".concat(name));
        componentName.setValue(compNames);
        componentName.setComment("PAPI component name");

        // Add the actor name
        ConstantString actorName = CodegenFactory.eINSTANCE.createConstantString();
        actorName.setName("actor_name".concat(finalName));
        actorName.setValue(finalName);
        actorName.setComment("Actor name");

        // Add the size of the CodeSet
        Constant codeSetSize = CodegenFactory.eINSTANCE.createConstant();
        codeSetSize.setName("CodeSetSize");
        codeSetSize.setValue(includedEvents.size());

        // Add the names of all the events
        ConstantString eventSetNames = CodegenFactory.eINSTANCE.createConstantString();
        eventSetNames.setName("allEventNames");
        eventSetNames.setValue(eventNames);
        eventSetNames.setComment("Papify events");

        // Add the size of the configs
        Constant numConfigs = CodegenFactory.eINSTANCE.createConstant();
        numConfigs.setName("numConfigs");
        numConfigs.setValue(counterConfigs);

        // Set all the properties to the vertex
        Map<String,
            String> mapPapifyConfiguration = this.dag.getVertex(name).getPropertyBean().getValue(PAPIFY_CONFIGURATION);
        if (mapPapifyConfiguration == null) {
          mapPapifyConfiguration = new LinkedHashMap<>();
        }
        mapPapifyConfiguration.put(info, message);
        Map<String,
            PapifyAction> mapPapifyActionName = this.dag.getVertex(name).getPropertyBean().getValue(PAPIFY_ACTION_NAME);
        if (mapPapifyActionName == null) {
          mapPapifyActionName = new LinkedHashMap<>();
        }
        mapPapifyActionName.put(info, papifyActionName);
        Map<String, ConstantString> mapPapifyComponentName = this.dag.getVertex(name).getPropertyBean()
            .getValue(PAPIFY_COMPONENT_NAME);
        if (mapPapifyComponentName == null) {
          mapPapifyComponentName = new LinkedHashMap<>();
        }
        mapPapifyComponentName.put(info, componentName);
        Map<String,
            ConstantString> mapPapifyActorName = this.dag.getVertex(name).getPropertyBean().getValue(PAPIFY_ACTOR_NAME);
        if (mapPapifyActorName == null) {
          mapPapifyActorName = new LinkedHashMap<>();
        }
        mapPapifyActorName.put(info, actorName);
        Map<String,
            Constant> mapPapifyCodesetSize = this.dag.getVertex(name).getPropertyBean().getValue(PAPIFY_CODESET_SIZE);
        if (mapPapifyCodesetSize == null) {
          mapPapifyCodesetSize = new LinkedHashMap<>();
        }
        mapPapifyCodesetSize.put(info, codeSetSize);
        Map<String, ConstantString> mapPapifyEventsetNames = this.dag.getVertex(name).getPropertyBean()
            .getValue(PAPIFY_EVENTSET_NAMES);
        if (mapPapifyEventsetNames == null) {
          mapPapifyEventsetNames = new LinkedHashMap<>();
        }
        mapPapifyEventsetNames.put(info, eventSetNames);
        Map<String, ConstantString> mapPapifyCofigNumber = this.dag.getVertex(name).getPropertyBean()
            .getValue(PAPIFY_CONFIG_NUMBER);
        if (mapPapifyCofigNumber == null) {
          mapPapifyCofigNumber = new LinkedHashMap<>();
        }
        mapPapifyCofigNumber.put(info, papifyConfigNumber);
        Map<String, Constant> mapPapifyCounterConfigs = this.dag.getVertex(name).getPropertyBean()
            .getValue(PAPIFY_COUNTER_CONFIGS);
        if (mapPapifyCounterConfigs == null) {
          mapPapifyCounterConfigs = new LinkedHashMap<>();
        }
        mapPapifyCounterConfigs.put(info, numConfigs);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_CONFIGURATION, mapPapifyConfiguration);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_ACTION_NAME, mapPapifyActionName);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_COMPONENT_NAME, mapPapifyComponentName);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_ACTOR_NAME, mapPapifyActorName);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_CODESET_SIZE, mapPapifyCodesetSize);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_EVENTSET_NAMES, mapPapifyEventsetNames);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_CONFIG_NUMBER, mapPapifyCofigNumber);
        this.dag.getVertex(name).getPropertyBean().setValue(PAPIFY_COUNTER_CONFIGS, mapPapifyCounterConfigs);
      }
    }
  }

  /**
   * Function to iterate over all the vertex / abstractGraphs
   */
  void configurePapifyFunctionsManager(MapperDAGVertex vertex) {
    // System.out.println(vertex.getInfo()); // Which vertex is entering here?
    if (vertex.getRefinement() instanceof AbstractGraph) {
      org.preesm.model.pisdf.AbstractVertex referencePiVertexA = vertex.getReferencePiVertex();
      org.preesm.model.pisdf.AbstractVertex referencePiVertex = PreesmCopyTracker.getOriginalSource(referencePiVertexA);
      if (referencePiVertex instanceof PiGraph) {
        for (AbstractActor actor : ((PiGraph) referencePiVertex).getAllActors()) {
          String papifyActionNameBuilder = actor.getVertexPath().substring(actor.getVertexPath().indexOf('/') + 1);
          papifyActionNameBuilder = papifyActionNameBuilder.replace('/', '_');
          configurePapifyFunctions(actor, actor.getVertexPath(), vertex.getName(), papifyActionNameBuilder);
        }
      }

    } else {
      org.preesm.model.pisdf.AbstractVertex referencePiVertexA = vertex.getReferencePiVertex();
      org.preesm.model.pisdf.AbstractVertex referencePiVertex = PreesmCopyTracker.getOriginalSource(referencePiVertexA);
      if (referencePiVertex instanceof AbstractActor) {
        configurePapifyFunctions((AbstractActor) referencePiVertex, vertex.getInfo(), vertex.getName(),
            vertex.getName());
      }
    }
  }

  /**
   * Include all the properties required by the CodegenModelGenerator to correctly instrument the generated code
   *
   */
  public DirectedAcyclicGraph generate() {

    // The timing event
    this.timingEvent.setName("Timing");
    this.timingEvent.setDescription("Event to time through PAPI_get_time()");
    this.timingEvent.setIndex(9999);
    List<PapiEventModifier> modifTimingList = new ArrayList<>();
    this.timingEvent.getModifiers().addAll(modifTimingList);

    if (this.scenario.getPapifyConfig() != null) {

      // For each vertex, check the monitoring
      for (final DAGVertex vertex : this.dag.vertexSet()) {
        configurePapifyFunctionsManager((MapperDAGVertex) vertex);
      }
    }

    return this.dag;
  }
}
