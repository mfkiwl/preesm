/*******************************************************************************
 * Copyright or © or Copr. IETR/INSA: Maxime Pelcat, Jean-François Nezan,
 * Karol Desnos, Julien Heulot, Clement Guy
 * 
 * [mpelcat,jnezan,kdesnos,jheulot,cguy]@insa-rennes.fr
 * 
 * This software is a computer program whose purpose is to prototype
 * parallel applications.
 * 
 * This software is governed by the CeCILL-C license under French law and
 * abiding by the rules of distribution of free software.  You can  use, 
 * modify and/ or redistribute the software under the terms of the CeCILL-C
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
 * knowledge of the CeCILL-C license and that you accept its terms.
 ******************************************************************************/

package org.ietr.preesm.ui.pimm.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.ietr.preesm.experiment.model.pimm.Direction;
import org.ietr.preesm.experiment.model.pimm.FunctionParameter;
import org.ietr.preesm.experiment.model.pimm.FunctionPrototype;
import org.ietr.preesm.experiment.model.pimm.PiMMFactory;

/**
 * Utility class containing method to extract prototypes from a C header file.
 * 
 * @author kdesnos
 * 
 */
public class HeaderParser {

	/**
	 * This method parse a C header file and extract a set of function
	 * prototypes from it.
	 * 
	 * @param file
	 *            the {@link IFile} corresponding to the C Header file to parse.
	 * @return The {@link Set} of {@link FunctionPrototype} found in the parsed
	 *         C header file. Returns <code>null</code> if no valid function
	 *         prototype could be found.
	 */
	public static List<FunctionPrototype> parseHeader(IFile file) {
		// Read the file
		List<FunctionPrototype> result = null;
		if (file != null) {
			try {
				// Read the file content
				String fileContent = readFileContent(file);

				// Filter unwanted content
				fileContent = filterHeaderFileContent(fileContent);

				// Identify and isolate prototypes in the remaining code
				List<String> prototypes = extractPrototypeStrings(fileContent);

				// Create the FunctionPrototypes
				result = createFunctionPrototypes(prototypes);
			} catch (CoreException | IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * Given a {@link List} of C function prototypes represented as
	 * {@link String}, this function create the {@link List} of corresponding
	 * {@link FunctionPrototype}.
	 * 
	 * @param prototypes
	 *            {@link List} of C function prototypes, as produced by the
	 *            {@link #extractPrototypeStrings(String)} method.
	 * @return a {@link List} of {@link FunctionPrototype}, or <code>null</code>
	 *         if something went wrong during the parsing.
	 */
	protected static List<FunctionPrototype> createFunctionPrototypes(
			List<String> prototypes) {
		List<FunctionPrototype> result;
		result = new ArrayList<FunctionPrototype>();

		// Unique RegEx to separate the return type, the function name
		// and the list of parameters
		Pattern pattern = Pattern.compile("(.+?)\\s(\\S+?)\\s?\\((.*?)\\)");
		for (String prototypeString : prototypes) {
			System.out.println(prototypeString);
			FunctionPrototype funcProto = PiMMFactory.eINSTANCE
					.createFunctionPrototype();

			// Get the name
			Matcher matcher = pattern.matcher(prototypeString);
			if (matcher.matches()) { // apply the matcher
				funcProto.setName(matcher.group(2));
			}

			// Get the parameters (if any)
			// A new array list must be created because the list
			// returned by Arrays.asList cannot be modified (in
			// particular, no element can be removed from it).
			List<String> parameters = new ArrayList<String>(
					Arrays.asList(matcher.group(3).split("\\s?,\\s?")));
			// Remove empty match (is the function has no parameter)
			parameters.remove("");
			parameters.remove(" ");

			Pattern paramPattern = Pattern
					.compile("(IN|OUT)?([^\\*]+)(\\s\\**)?\\s(\\S+)");
			// Procces parameters one by one
			for (String param : parameters) {
				FunctionParameter fp = PiMMFactory.eINSTANCE
						.createFunctionParameter();
				matcher = paramPattern.matcher(param);
				boolean matched = matcher.matches();
				if (matched) { // Apply the matcher (if possible)
					// Get the parameter name
					fp.setName(matcher.group(4));
					// Get the parameter type
					fp.setType(matcher.group(2));
					// Check the direction (if any)
					if (matcher.group(1) != null) {
						if (matcher.group(1).equals("IN")) {
							fp.setDirection(Direction.IN);

						}
						if (matcher.group(1).equals("OUT")) {
							fp.setDirection(Direction.OUT);
						}
					}
					if (matcher.group(3) == null) {
						fp.setIsConfigurationParameter(true);
					}

					result.add(funcProto);
				}
			}
		}
		return result;
	}

	/**
	 * Separate the {@link String} corresponding to the function prototypes from
	 * the filtered file content.
	 * 
	 * @param fileContent
	 *            the filtered file content provided by
	 *            {@link #filterHeaderFileContent(String)}.
	 * @return the {@link List} of {@link String} corresponding to the function
	 *         prototypes.
	 */
	protected static List<String> extractPrototypeStrings(String fileContent) {
		// The remaining code is a single line containing only C code
		// (enum, struct, prototypes, inline functions, ..)
		Pattern pattern = Pattern.compile("[^;}](.*?\\(.*?\\))[;]");
		Matcher matcher = pattern.matcher(fileContent);
		List<String> prototypes = new ArrayList<String>();
		boolean containsPrototype;
		do {
			containsPrototype = matcher.find();
			if (containsPrototype) {
				prototypes.add(matcher.group(1));
			}
		} while (containsPrototype);
		return prototypes;
	}

	/**
	 * Filter the content of an header file as follows :
	 * <ul>
	 * <li>Filter comments between <code>/* * /</code></li>
	 * <li>Filter comments after //</li>
	 * <li>Filter all pre-processing commands</li>
	 * <li>Replace new lines and multiple spaces with a single space</li>
	 * <li>Make sure there always is a space before and after each group of *
	 * this will ease type identification during prototype identification.</li>
	 * </ul>
	 * 
	 * @param fileContent
	 *            the content to filter as a {@link String}.
	 * @return the filtered content as a {@link String}
	 */
	protected static String filterHeaderFileContent(String fileContent) {
		// Order of the filter is important !
		// Comments must be removed before pre-processing commands and
		// end of lines.

		// Filter comments between /* */
		Pattern pattern = Pattern.compile("(/\\*)(.*?)(\\*/)", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(fileContent);
		fileContent = matcher.replaceAll("");

		// Filter comments after //
		pattern = Pattern.compile("(//)(.*?\\n)", Pattern.DOTALL);
		matcher = pattern.matcher(fileContent);
		fileContent = matcher.replaceAll("");

		// Filter all pre-processing (
		pattern = Pattern.compile(
				"^\\s*#\\s*(([^\\\\]+?)((\\\\$[^\\\\]+?)*?$))",
				Pattern.MULTILINE | Pattern.DOTALL);
		matcher = pattern.matcher(fileContent);
		fileContent = matcher.replaceAll("");

		// Replace new lines and multiple spaces with a single space
		pattern = Pattern.compile("\\s+", Pattern.MULTILINE);
		matcher = pattern.matcher(fileContent);
		fileContent = matcher.replaceAll(" ");

		// Make sure there always is a space before and after each
		// group of * this will ease type identification during
		// prototype identification.
		// 1. remove all spaces around "*"
		pattern = Pattern.compile("\\s?\\*\\s?");
		matcher = pattern.matcher(fileContent);
		fileContent = matcher.replaceAll("*");
		// 2. add space around each groupe of *
		pattern = Pattern.compile(":?\\*+");
		matcher = pattern.matcher(fileContent);
		fileContent = matcher.replaceAll(" $0 ");
		return fileContent;
	}

	/**
	 * Read the content of an {@link IFile} and returns it as a {@link String}.
	 * 
	 * @param file
	 *            the {@link IFile} to read.
	 * @return the content of the {@link IFile} as a {@link String}.
	 * 
	 * @throws CoreException
	 *             {@link IFile#getContents()}
	 * @throws IOException
	 *             {@link InputStream#read()}
	 */
	protected static String readFileContent(IFile file) throws CoreException,
			IOException {
		InputStream is = file.getContents();
		byte buffer[] = new byte[1000];
		int nbRead = 0;
		String fileContent = "";
		do {
			nbRead = is.read(buffer);
			fileContent = fileContent
					+ (new String(buffer)).substring(0, nbRead);
		} while (nbRead == 1000);
		return fileContent;
	}

}
