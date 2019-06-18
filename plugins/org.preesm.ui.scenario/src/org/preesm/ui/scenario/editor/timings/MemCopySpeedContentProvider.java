/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2008 - 2018) :
 *
 * Antoine Morvan <antoine.morvan@insa-rennes.fr> (2017 - 2018)
 * Matthieu Wipliez <matthieu.wipliez@insa-rennes.fr> (2008)
 * Maxime Pelcat <maxime.pelcat@insa-rennes.fr> (2008 - 2013)
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
package org.preesm.ui.scenario.editor.timings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.preesm.model.scenario.MemoryCopySpeedValue;
import org.preesm.model.scenario.Scenario;
import org.preesm.model.scenario.util.ScenarioUserFactory;
import org.preesm.model.slam.Design;
import org.preesm.model.slam.component.Component;

/**
 * Provides the elements contained in the memcopy speeds editor.
 *
 * @author mpelcat
 */
public class MemCopySpeedContentProvider implements IStructuredContentProvider {

  /** The element list. */
  List<Entry<Component, MemoryCopySpeedValue>> elementList = null;

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
   */
  @Override
  public Object[] getElements(final Object inputElement) {

    if (inputElement instanceof Scenario) {
      final Scenario inputScenario = (Scenario) inputElement;

      /**
       * Memcopy speeds are added for all operator types if non present
       */
      final Design design = inputScenario.getDesign();
      for (final Component opDefId : design.getOperatorComponents()) {
        if (!inputScenario.getTimings().getMemTimings().containsKey(opDefId)) {
          final MemoryCopySpeedValue createMemoryCopySpeedValue = ScenarioUserFactory.createMemoryCopySpeedValue();
          inputScenario.getTimings().getMemTimings().put(opDefId, createMemoryCopySpeedValue);
        }
      }

      // Retrieving the memory copy speeds in operator definition order
      final Set<
          Entry<Component, MemoryCopySpeedValue>> entrySet = inputScenario.getTimings().getMemTimings().entrySet();
      this.elementList = new ArrayList<>(entrySet);

      Collections.sort(this.elementList,
          (o1, o2) -> o1.getKey().getVlnv().getName().compareTo(o2.getKey().getVlnv().getName()));
    }
    return this.elementList.toArray();
  }

}
