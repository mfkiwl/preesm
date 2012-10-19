package org.ietr.preesm.experiment.ui.pimemoc.diagram;

import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IRemoveFeature;
import org.eclipse.graphiti.features.context.IRemoveContext;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.ietr.preesm.experiment.ui.pimemoc.features.CustomDeleteFeature;

/**
 * The purpose of this class is to provide the RemoveFeature for use in the
 * deleteFeature. However, the RemoveFeature must not be available in the
 * PimemocFeatureProvider because we do not want it to appear in the
 * Graphical Interface. Indeed, the "remove" operation simply remove the
 * pictogram from the GUI, but does not remove the corresponding Object from
 * the Business Model. We feel that it might lead to incoherences.
 * 
 * @see http://www.eclipse.org/forums/index.php/mv/msg/234410/720417/#msg_720417
 * @see CustomDeleteFeature
 * @author kdesnos
 * 
 */
public class PimemocFeatureProviderWithRemove extends
		PimemocFeatureProvider {
	/**
	 * Default constructor
	 * 
	 * @param dtp
	 * The Diagram Type Provider
	 */
	public PimemocFeatureProviderWithRemove(IDiagramTypeProvider dtp) {
		super(dtp);
	}
	

	@Override
	public IRemoveFeature getRemoveFeature(IRemoveContext context) {
		return super.getRemoveFeatureEnabled(context);  
	}	
}
