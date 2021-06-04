/**
 * 
 */
package com.elsevier.reaxys.xml.utils;

import java.io.File;
import java.net.URL;
import java.nio.file.attribute.FileTime;
import java.security.CodeSource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Namespace containing global definitions like version numbers
 * 
 * @author BROEKF
 *
 */
public final class ReaxysDefs {
	
	private static final String UNKNOWN_VERSION = "unknown";
	private static final String UNKNOWN_DATE = "19700101.0000 UTC";


	/**
	 * Prevent instantiation
	 */
	private ReaxysDefs() {
		
	}
	
	/**
	 * Gets the jar file which contains the given compiled class
	 * @param cls The Class to find the jar for
	 * @return The jar File
	 */
	public static File getJarFile(Class<?> cls) {
		CodeSource source = cls.getProtectionDomain().getCodeSource();
		if (source == null) { 
			return null;
		}
		URL url = source.getLocation();
		if (url == null) { 
			return null;
		}
		String path = url.getFile();
		File res = new File(path);
		if (res.isDirectory()) { 
			return null;
		}
		return res.getAbsoluteFile();
	}
	
	/**
	 * Gets the Manifest from the given jar File
	 * 
	 * @param jarFile
	 *            The jar File to get the Manifest from
	 * @return the Manifest or null if not found
	 */
	private static Manifest getManifest(File jarFile) {
		try {
			JarFile jar = new JarFile(jarFile);
			try {
				Manifest mf = jar.getManifest();
				if (mf == null) {
					return null;
				}
				return mf;
			} finally {
				jar.close();
			}
		} catch (Exception e) {
			return null;
		}
	}
	
/**
 * Gets the last modified time from an entry in the jar file
 * @param jarEntry the path to the entry in the jar file
 * @return the last modified Date or null in case of error
 */
	private static Date getLastModified(String jarEntry) {
		Date lastModified = null;
		try {
			JarFile jar = new JarFile(getJarFile(ReaxysDefs.class));
			try {
				FileTime lastmod = jar.getEntry(jarEntry).getLastModifiedTime();
				lastModified = new Date(lastmod.toMillis());
			} finally {
				jar.close();
			}
		} catch (Exception e) {
			// Do nothing
		}
		return lastModified;
	}
	
	/**
	 * Gets the Bundle version string from the jar Manifest
	 * 
	 * @return the version String
	 */
	public static String getVersion() {
		
		File jarFile = getJarFile(ReaxysDefs.class);
		if (jarFile == null) {
			return UNKNOWN_VERSION;
		}
		Manifest manifest = getManifest(jarFile);
		if (manifest == null) {
			return UNKNOWN_VERSION;
		}
		String version = manifest.getMainAttributes().getValue("Bundle-Version");
		if (version == null) {
			version = UNKNOWN_VERSION;
		} else { 
			// Only get the major number from the version string
			version = version.split("[MS:]")[0];
		}
		return version;
	}
	
	/**
	 * Gets the datetime string of the build date of the jar from the Manifest
	 * 
	 * @return the datetime string in YYYYMMDD.HHmm format
	 */
	public static String getBuildDateTime() {
		
		String dateString = UNKNOWN_DATE;
		
		Date lastModifiedDate = getLastModified("plugin.xml");
		
		if (lastModifiedDate !=null) {
			// Date format for version string (and use UTC for the time zone)
			SimpleDateFormat versionDateFormat = new SimpleDateFormat("yyyyMMdd.HHmm", new Locale("en","UK"));
			versionDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

			dateString = versionDateFormat.format(lastModifiedDate);
		}
		
		return dateString;
	}
	
	/**
	 * Gets a string composed of svn revision and datetime of build from manifest
	 * 
	 * @return String in format ##### (YYYYMMdd.HHmm)
	 */
	public static String getDateVersionString() {
		return getVersion() + " (" + getBuildDateTime() +")";
	}
}
