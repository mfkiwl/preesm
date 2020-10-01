/**
 * Copyright or © or Copr. IETR/INSA - Rennes (2018 - 2020) :
 *
 * Antoine Morvan [antoine.morvan@insa-rennes.fr] (2018 - 2019)
 * Julien Heulot [julien.heulot@insa-rennes.fr] (2020)
 * Karol Desnos [karol.desnos@insa-rennes.fr] (2020)
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
package org.preesm.commons.files;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

/**
 *
 */
public final class URLResolver {

  private static final String readURLInBundleList(final String strUrl, final List<String> bundles) throws IOException {
    final URL url = URLResolver.findFirstInBundleList(strUrl, bundles);
    return URLResolver.readURL(url);
  }

  private static final String readURLInBundleList(final String location, final String... bundles) throws IOException {
    return URLResolver.readURLInBundleList(location, Arrays.asList(bundles));
  }

  public static final String readURL(final String strUrl) throws IOException {
    return URLResolver.readURLInBundleList(strUrl);
  }

  /**
   * Reads the content of the given URL and return it as a String.
   */
  private static final String readURL(final URL url) throws IOException {
    if (url == null) {
      throw new FileNotFoundException();
    }
    return URLHelper.read(url);
  }

  public static final URL findFirst(final String location) {
    return URLResolver.findFirstInBundleList(location);
  }

  private static final URL findFirstInBundleList(final String location, final String... bundles) {
    return URLResolver.findFirstInBundleList(location, Arrays.asList(bundles));
  }

  private static final URL findFirstInBundleList(final String location, final List<String> bundles) {
    return new URLResolver().resolve(location, bundles);
  }

  /**
   * Looks for a Resource stored in the open workspace, in the Plug-in classpath, in a remote location, or in the
   * filesystem.<br/>
   * The lookup order is the following:<br/>
   * <ul>
   * <li>Look for the resource in the projects of the workspace; if bundleList is not empty, search only within the
   * projects whose name is in the list bundleList;</li>
   * <li>Look for the resource in the Eclipse instance loaded plug-ins classpath; if bundleList is not empty, search
   * only within the plug-ins whose name is in the list;</li>
   * <li>Try to initialize a remote URL;</li>
   * <li>Lookup for the resource in the file system.</li>
   * </ul>
   * If none of the above method worked, returns null.
   *
   */
  private final URL resolve(final String location, final List<String> bundleFilterList) {
    if ((location == null) || location.isEmpty()) {
      return null;
    }
    URL resultURL;
    try {
      resultURL = resolveURLFromWorkspace(location, bundleFilterList);
    } catch (final MalformedURLException e) {
      resultURL = null;
    }
    if (resultURL == null) {
      try {
        resultURL = resolveFileSystemURL(location);
      } catch (final MalformedURLException | InvalidPathException e) {
        resultURL = null;
      }
    }
    if (resultURL == null) {
      try {
        resultURL = resolvePlainURL(location);
      } catch (final MalformedURLException e) {
        resultURL = null;
      }
    }
    return resultURL;
  }

  private final URL resolveURLFromWorkspace(final String location, final List<String> bundleFilterList)
      throws MalformedURLException {
    final IWorkspace workspace;
    try {
      workspace = ResourcesPlugin.getWorkspace();
    } catch (final Exception e) {
      return null;
    }
    if (workspace == null) {
      // Eclipse is not running (call from plain Java or JUnit)
      return null;
    }
    final IWorkspaceRoot workspaceRoot = workspace.getRoot();
    final IProject[] projects = workspaceRoot.getProjects();
    final IPath path = new org.eclipse.core.runtime.Path(location);
    try {
      final IFile file = workspaceRoot.getFile(path);
      if (file != null) {
        return file.getRawLocationURI().toURL();
      }
    } catch (final Exception e) {
      // ignore
    }
    final IProject project = Stream.of(projects)
        .filter(p -> bundleFilterList.isEmpty() || bundleFilterList.contains(p.getName())).filter(p -> p.exists(path))
        .findFirst().orElse(null);
    if (project == null) {
      return null;
    }
    final IResource findMember = project.findMember(path);
    return findMember.getLocationURI().toURL();
  }

  private final URL resolvePlainURL(final String resource) throws MalformedURLException {
    return new URL(resource);
  }

  private final URL resolveFileSystemURL(final String fileSystemPath) throws MalformedURLException {
    final Path path = Paths.get(fileSystemPath);
    if (path.toFile().exists()) {
      return path.toUri().toURL();
    }
    return null;
  }
}
