package org.ietr.preesm.experiment.ui.pimm.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

/**
 * The purpose of this class is to handle the rename refactoring of a file with
 * the ".diagram" extension. In the rename operation, it is assumed that the
 * corresponding file with the ".pi" extension is renamed similarly.
 * 
 * @author kdesnos
 * 
 */
public class RenameDiagram extends RenameParticipant {

	String oldName;
	String newName;

	@Override
	protected boolean initialize(Object element) {
		oldName = ((IFile) element).getName();
		newName = this.getArguments().getNewName();

		// Check that both names end with extension ".diagram"
		// and remove both extensions
		final String extension = ".diagram";
		if (oldName.endsWith(extension) && newName.endsWith(extension)) {
			oldName = oldName.substring(0,
					oldName.length() - extension.length());
			newName = newName.substring(0,
					newName.length() - extension.length());

			return true;

		} else {
			return false;
		}
	}

	@Override
	public String getName() {
		return "Rename .diagram file";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm,
			CheckConditionsContext context) throws OperationCanceledException {
		// Nothing to do here
		return null;
	}

	public Change createPreChange(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {

		// Get the refactored file
		IFile refactored = (IFile) getProcessor().getElements()[0];

		// The regex is a combination of the two following expressions:
		// "(<pi:Diagram.*?name=\")(" + oldName + ")(\".*?>)"
		// "(<businessObjects href=\")("+ oldName + ")(.pi#.*?\"/>)"
		Change change = RefactoringHelper.createChange(
				"(<pi:Diagram.*?name=\"|<businessObjects href=\")(" + oldName
						+ ")(\".*?>|.pi#.*?\"/>)", 2, newName, refactored);

		return change;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {
		// Nothing to do after the file is renamed.
		return null;
	}

}