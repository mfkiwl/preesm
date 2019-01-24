package org.preesm.ui.pisdf.popup.actions;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.graphiti.ui.editor.DiagramEditorInput;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.preesm.commons.exceptions.PreesmException;
import org.preesm.commons.files.ContainersManager;
import org.preesm.commons.files.PathTools;
import org.preesm.commons.files.WorkspaceUtils;
import org.preesm.commons.logger.PreesmLogger;
import org.preesm.model.pisdf.ConfigInputPort;
import org.preesm.model.pisdf.PiGraph;
import org.preesm.model.pisdf.brv.BRVMethod;
import org.preesm.model.pisdf.serialize.PiParser;
import org.preesm.model.pisdf.serialize.PiWriter;
import org.preesm.model.pisdf.statictools.PiSDFToSingleRate;
import org.preesm.ui.PreesmUIPlugin;
import org.preesm.ui.utils.ErrorWithExceptionDialog;
import org.preesm.ui.utils.FileUtils;

/**
 *
 */
public class PiSDFSRDAGComputePopup extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    final IWorkbenchPage page = PreesmUIPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage();
    final ISelection activeSelection = page.getSelection();

    if (activeSelection instanceof TreeSelection) {
      // in the resource explorer, compute for all selected .pi files
      final TreeSelection selection = (TreeSelection) activeSelection;
      final Iterator<?> iterator = selection.iterator();
      while (iterator.hasNext()) {
        final Object next = iterator.next();
        if (next instanceof IFile) {
          final IFile file = (IFile) next;
          final PiGraph piGraphWithReconnection = PiParser.getPiGraphWithReconnection(file.getFullPath().toString());

          processPiSDF(piGraphWithReconnection, file.getProject());
        }
      }
    } else if (activeSelection instanceof StructuredSelection) {
      // in the PiSDF editor, compute for active graph
      final IEditorPart editor = page.getActiveEditor();
      final DiagramEditorInput input = (DiagramEditorInput) editor.getEditorInput();
      final String uriString = input.getUri().path().replace("/resource", "").replace(".diagram", ".pi");
      final IPath fromPortableString = Path.fromPortableString(uriString);
      final IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(fromPortableString);

      final String string = file.getFullPath().toString();
      final PiGraph piGraphWithReconnection = PiParser.getPiGraphWithReconnection(string);
      processPiSDF(piGraphWithReconnection, file.getProject());
    } else {
      ErrorWithExceptionDialog.errorDialogWithStackTrace("unsupported selection : " + activeSelection.getClass(),
          new UnsupportedOperationException());
    }
    return null;
  }

  private void processPiSDF(final PiGraph pigraph, final IProject iProject) {
    PreesmLogger.getLogger().log(Level.INFO, "Computing Single Rate DAG for " + pigraph.getName());

    final EList<ConfigInputPort> configInputPorts = pigraph.getConfigInputPorts();
    if (!configInputPorts.isEmpty()) {
      PreesmLogger.getLogger().log(Level.WARNING, "Cannot compute the Single Rate DAG of a subgraph");
      return;
    }

    final boolean locallyStatic = pigraph.isLocallyStatic();
    if (!locallyStatic) {
      PreesmLogger.getLogger().log(Level.WARNING, "Cannot compute the Single Rate DAG of a dynamic graph");
      return;
    }

    final PiGraph srdag = PiSDFToSingleRate.compute(pigraph, BRVMethod.LCM);

    final IPath targetFolder = FileUtils.browseFiles(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
        "test", "select test", (Collection<String>) null);
    final IPath inProjectPath = targetFolder.removeFirstSegments(1);
    final String relative = inProjectPath.toString();

    final String sXmlPath = PathTools.getAbsolutePath(relative, iProject.getName());
    IPath xmlPath = new Path(sXmlPath);
    // Get a complete valid path with all folders existing
    try {
      if (xmlPath.getFileExtension() != null) {
        ContainersManager.createMissingFolders(xmlPath.removeFileExtension().removeLastSegments(1));
      } else {
        ContainersManager.createMissingFolders(xmlPath);
        xmlPath = xmlPath.append(srdag.getName() + ".pi");
      }
    } catch (CoreException | IllegalArgumentException e) {
      throw new PreesmException("Path " + sXmlPath + " is not a valid path for export.\n" + e.getMessage());
    }

    final URI uri = URI.createPlatformResourceURI(xmlPath.toString(), true);
    // Get the project
    final String platformString = uri.toPlatformString(true);
    final IFile documentFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(platformString));
    final String osString = documentFile.getLocation().toOSString();
    try (final OutputStream outStream = new FileOutputStream(osString);) {
      // Write the Graph to the OutputStream using the Pi format
      new PiWriter(uri).write(srdag, outStream);
    } catch (IOException e) {
      throw new PreesmException("Could not open outputstream file " + xmlPath.toString());
    }

    WorkspaceUtils.updateWorkspace();
  }
}
