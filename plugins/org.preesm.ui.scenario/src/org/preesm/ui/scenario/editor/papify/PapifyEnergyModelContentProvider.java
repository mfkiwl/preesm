/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2008 - 2019) :
 *
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2017 - 2019)
 * Daniel Madroñal [daniel.madronal@upm.es] (2019)
 * Matthieu Wipliez [matthieu.wipliez@insa-rennes.fr] (2008)
 * Maxime Pelcat [maxime.pelcat@insa-rennes.fr] (2008 - 2013)
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
package org.preesm.ui.scenario.editor.papify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.preesm.model.scenario.PapiEvent;
import org.preesm.model.scenario.Scenario;
import org.preesm.model.slam.Component;
import org.preesm.model.slam.Design;

/**
 * Provides the elements contained in the static power of the platform editor.
 *
 * @author dmadronal
 */
public class PapifyEnergyModelContentProvider implements IStructuredContentProvider {

  private List<Entry<Component, EMap<PapiEvent, Double>>> elementList = null;

  @Override
  public Object[] getElements(final Object inputElement) {

    if (inputElement instanceof Scenario) {
      final Scenario inputScenario = (Scenario) inputElement;
      final Design design = inputScenario.getDesign();
      final EMap<Component,
          EMap<PapiEvent, Double>> energyModels = inputScenario.getPapifyConfig().getPapifyEnergyKPIModels();

      /**
       * PE models are added for all operator types if non present
       */
      for (final Component opDefId : design.getOperatorComponents()) {
        if (!energyModels.containsKey(opDefId)) {
          inputScenario.getPapifyConfig().addEnergyModel(opDefId);
        }
      }

      /**
       * Retrieving the PE models in operator definition order
       */
      final Set<Entry<Component, EMap<PapiEvent, Double>>> entrySet = inputScenario.getPapifyConfig()
          .getPapifyEnergyKPIModels().entrySet();
      this.elementList = new ArrayList<>(entrySet);

      Collections.sort(this.elementList,
          (o1, o2) -> o1.getKey().getVlnv().getName().compareTo(o2.getKey().getVlnv().getName()));
    }
    return this.elementList.toArray();
  }

}