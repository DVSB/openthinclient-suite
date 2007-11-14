/*******************************************************************************
 * openthinclient.org ThinClient suite
 * 
 * Copyright (C) 2004, 2007 levigo holding GmbH. All Rights Reserved.
 * 
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 ******************************************************************************/
package org.openthinclient.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openthinclient.common.model.Realm;
import org.openthinclient.console.nodes.DirectoryEntryNode;
import org.openthinclient.ldap.DirectoryException;
import org.openthinclient.ldap.Util;

/**
 * @author Michael Gold
 */
public class HTTPLdifImportAction {
	private static final Logger logger = Logger
			.getLogger(HTTPLdifImportAction.class);

	private static URL baseURL;

	private final String DEFAULT_FOLDERNAME = "ldif";

	private static boolean enableAsk = true;

	public HTTPLdifImportAction(String hostname) throws MalformedURLException {
		baseURL = new URL("http", hostname, 8080, "/openthinclient/files/"
				+ DEFAULT_FOLDERNAME + "/");
		if (logger.isDebugEnabled())
			logger.debug("Using ldif base url: " + baseURL);
	}

	private void makeNewBaseUrl(String hostname, String foldername)
			throws MalformedURLException {
		baseURL = new URL("http", hostname, 8080, "/openthinclient/files/"
				+ foldername + "/");
		if (logger.isDebugEnabled())
			logger.debug("Using ldif base url: " + baseURL);
	}

	private boolean checkAccess() {
		try {
			URLConnection openConnection = baseURL.openConnection();
			String contentType = openConnection.getContentType();
			return contentType.startsWith("text/plain;");
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * This method returns all filenames of the given url.
	 * 
	 * @return a Set of filenames
	 */
	private static Set<String> getAllFilenames() throws IOException {
		InputStream in = HTTPLdifImportAction.baseURL.openStream();

		int c;
		String input = "";
		while ((c = in.read()) != -1) {
			input = input + ((char) c);
		}
		BufferedReader reader = new BufferedReader(new StringReader(input));
		Set<String> filenames = new HashSet<String>();

		String line = reader.readLine();

		while (line != null) {
			if (line.startsWith("F"))
				filenames.add(line.replace("F", "").trim());
			else {
				// do nothing
			}
			line = reader.readLine();
		}
		return filenames;
	}

	/**
	 * This method returns all Files in given url.
	 * 
	 * @return a Set of Files
	 */
	private Set<File> loadAllLdifFiles(Realm realm) throws IOException {
		Set<String> filenames = getAllFilenames();

		Set<File> files = new HashSet<File>();
		for (String name : filenames) {
			files.add(loadLdifFile(name, realm));
		}
		return files;
	}

	/**
	 * This method returns the File with the given filename.
	 * 
	 * @return File
	 */
	private File loadLdifFile(String filename, Realm realm) throws IOException {
		URL newURL;
		if (filename.endsWith(".ldif"))
			newURL = new URL(baseURL + filename);
		else {
			newURL = new URL(baseURL + filename + ".ldif");
		}

		InputStream in = newURL.openStream();

		int c;
		String input = "";
		while ((c = in.read()) != -1) {
			input = input + ((char) c);
		}
		int hashsum = input.hashCode();

		if (allowedToImport(filename, hashsum, realm) == false) {
			return null;
		}

		if (enableAsk == true) {
			Object notify = DialogDisplayer
					.getDefault()
					.notify(
							new NotifyDescriptor.Confirmation(
									(Messages.getString("HttpLdifImportAction.choose.headline")
											+ "\n\n"
											+ Messages
													.getString("HttpLdifImportAction.choose.question.one")
											+ " "
											+ filename
											+ " "
											+ Messages
													.getString("HttpLdifImportAction.choose.question.two")
											+ " " + realm.getConnectionDescriptor().getBaseDN() + " " + Messages
											.getString("HttpLdifImportAction.choose.question.three")),
									NotifyDescriptor.YES_NO_OPTION));
			if (notify == NotifyDescriptor.NO_OPTION) {
				return null;
			}
		}
		File tempFile = File.createTempFile("tmp", ".ldif");
		setHashsum(filename, hashsum, realm);
		input = input.replaceAll("#%BASEDN%#", Util.idToUpperCase(realm
				.getConnectionDescriptor().getBaseDN()));
		RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
		raf.writeBytes(input);

		return tempFile;
	}

	/**
	 * This method imports all Files of the given url
	 * 
	 * If the needed files aren't in the DEFAULT_FOLDER = ldif, you can use the
	 * parameter foldername.
	 * 
	 * If you set foldername NULL, the DEFAULT_FOLDERNAME will be used.
	 * 
	 * @param LDAPConnectionDescriptor lcd
	 * @param String foldername
	 */
	public void importAllFromURL(final String foldername, final Realm realm)
			throws IOException {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {

				try {
					if (foldername != null) {
						makeNewBaseUrl(realm.getConnectionDescriptor().getHostname(),
								foldername);
					};
					if (checkAccess()) {

						Set<File> tempFiles = loadAllLdifFiles(realm);
						for (File importFile : tempFiles) {
							if (importFile != null) {
								DirectoryEntryNode.importAction(
										realm.getConnectionDescriptor(), importFile);
								importFile.delete();
							}
						}
					} else {
						logger.warn("Can't use url: " + baseURL);
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * This method imports one File of the given url.
	 * 
	 * @param Realm realm
	 * @param String filename
	 */
	public void importOneFromURL(String filename, Realm realm) throws IOException {
		if (checkAccess()) {
			File importFile = loadLdifFile(filename, realm);

			if (importFile != null) {
				if (logger.isDebugEnabled())
					logger.debug("Import follwing file: " + filename + ".ldif");
				DirectoryEntryNode.importAction(realm.getConnectionDescriptor(),
						importFile);
				importFile.delete();
			}
		} else {
			logger.warn("Can't use url: " + baseURL);
		}
	}

	private static void setHashsum(String filename, int hashsum, Realm realm) {
		filename = filename.replace(".ldif", "").trim();
		String path = "invisibleObjects." + filename;
		Long value = new Long(hashsum);
		realm.setValue(path, value.toString());

		try {
			realm.getDirectory().save(realm, "");
		} catch (DirectoryException e1) {
			e1.printStackTrace();
		}
	}

	private boolean allowedToImport(String filename, int hashsum, Realm realm) {
		filename = filename.replace(".ldif", "").trim();
		Long hash = new Long(hashsum);

		String existingHash = realm.getValue("invisibleObjects." + filename);

		if (hash.toString().equals(existingHash))
			return false;

		logger.info("Did not yet import " + filename
				+ " - create new one and import LDIFs!!!");
		return true;
	}

	public static boolean isEnableAsk() {
		return enableAsk;
	}

	public static void setEnableAsk(boolean enableAsk) {
		HTTPLdifImportAction.enableAsk = enableAsk;
	}
}
