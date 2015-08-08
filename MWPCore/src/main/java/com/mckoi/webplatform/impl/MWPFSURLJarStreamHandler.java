/**
 * com.mckoi.webplatform.impl.MWPFSURLJarStreamHandler  Nov 5, 2012
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2012  Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this program.  If not, see ( http://www.gnu.org/licenses/ ) or
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 *
 * Change Log:
 *
 *
 */

package com.mckoi.webplatform.impl;

import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.util.HttpUtils;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.Permission;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * Provides an alternative 'jar:' protocol implementation that handles 'mwpfs'
 * resources efficiently.
 *
 * @author Tobias Downer
 */

public class MWPFSURLJarStreamHandler extends URLStreamHandler {

  /**
   * A cache for jar files.
   */
  private final Map<URL, CachedJarFile> jar_file_cache = new HashMap<>();



  @Override
  protected URLConnection openConnection(URL u) throws IOException {
    return new JarURLConnection(u);
  }

  class JarURLConnection extends java.net.JarURLConnection {

    private final String entry_name;
    private final URL jar_file_url;
    private final URLConnection jar_connection;

    // If we successfully connected to a JarEntry,
    private JarFile jfile;
    private JarEntry jar_entry = null;


    public JarURLConnection(URL url) throws IOException {
      super(url);
      
      // NOTE: 'entry_name' may be null if no entry given (eg, 'jar:[url]!/')
      this.entry_name = getEntryName();
      this.jar_file_url = getJarFileURL();
      
      this.jar_connection = jar_file_url.openConnection();

//      System.err.println("Original URL = " + url);
//      System.err.println("ENTRY NAME = " + entry_name);
//      System.err.println("jar_file_url = " + jar_file_url);
      
    }

    /**
     * Get the FileInfo given a FileName.
     */
    private FileInfo getFileInfo(FileName fname) {
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      FileRepository fs = ctx.getFileRepository(fname.getRepositoryId());
      return fs.getFileInfo(fname.getPathFile());
    }

    /**
     * Fetches the JarFile representing the given url. This may have to make
     * a temporary file if the .jar file has to be downloaded.
     */
    private JarFile fetchJarFile(URL jar_file_url) throws IOException {

      long time_now = System.currentTimeMillis();

      CachedJarFile cached_jfile;
      JarFile jar_file = null;
      synchronized (jar_file_cache) {
        // If the cache is over 150 elements then we remove the 25 oldest
        // entries,
        if (jar_file_cache.size() > 150) {
          // Sort by the 'last_checked_timestamp' key,
          SortedMap<Long, CachedJarFile> sm = new TreeMap<>();
          for (CachedJarFile fj : jar_file_cache.values()) {
            sm.put(fj.last_checked_timestamp, fj);
          }
          // Remove the oldest 25 entries,
          int i = 0;
          for (CachedJarFile cfj : sm.values()) {
            jar_file_cache.remove(cfj.url_ref);
            ++i;
            if (i >= 25) {
              break;
            }
          }
        }

        // Look in the cache for this jar file,
        cached_jfile = jar_file_cache.get(jar_file_url);
        if (cached_jfile != null) {
          // Try and get the JarFile. If it's not available then remove this
          // entry (it was GC'd).
          jar_file = cached_jfile.getJarFile();
          if (jar_file == null) {
            jar_file_cache.remove(jar_file_url);
            cached_jfile = null;
          }
        }
      }

      // If we have a cached element,
      if (cached_jfile != null &&
          cached_jfile.last_checked_timestamp + (4 * 1000) < time_now) {
        // Is it invalidated?
        String proto = jar_file_url.getProtocol();
        long mod_timestamp;
        if (proto.equals("file")) {
          File f = new File(HttpUtils.decodeURLFileName(jar_file_url));
          mod_timestamp = f.lastModified();
        }
        else if (proto.equals("mwpfs")) {
          FileInfo fi = getFileInfo(
                    new FileName(HttpUtils.decodeURLFileName(jar_file_url)));
          mod_timestamp = fi.getLastModified();
        }
        else {
          throw new IOException(
            "The URL protocol '" + proto + "' is currently not supported as a 'jar:' target in the Mckoi Web Platform. URL = " + getURL());
        }
        if (mod_timestamp != cached_jfile.mod_timestamp) {
          cached_jfile = null;
          // Clean this out of the cache,
          synchronized (jar_file_cache) {
            jar_file_cache.remove(jar_file_url);
          }
        }
        else {
          cached_jfile.last_checked_timestamp = time_now;
        }
      }

      // Re validate?
      if (cached_jfile == null) {
        String proto = jar_file_url.getProtocol();
        long mod_timestamp;
        char ref_type;
        if (proto.equals("file")) {
          File f = new File(HttpUtils.decodeURLFileName(jar_file_url));
          mod_timestamp = f.lastModified();
          jar_file = new NotCloseableJarFile(f);
          ref_type = 'S'; // Soft reference,
        }
        else if (proto.equals("mwpfs")) {
          FileName fname = new FileName(
                              HttpUtils.decodeURLFileName(jar_file_url));
          FileInfo fi = getFileInfo(fname);
          mod_timestamp = fi.getLastModified();
          String archive_name = fi.getAbsoluteName();
          jar_file = new DataFileJarFile(
                           fname, fi.getDataFile(), false, ZipFile.OPEN_READ);
          // If the repository id is a temporary ref then make it a weak
          // reference,
          if (archive_name.startsWith("/$")) {
            ref_type = 'W'; // Weak reference,
          }
          else {
            ref_type = 'S'; // Soft reference,
          }
        }
        else {
          throw new IOException(
                    "Don't know how to turn into a JarFile: " + jar_file_url);
        }
        cached_jfile =
                new CachedJarFile(jar_file_url, ref_type,
                                  time_now, mod_timestamp, jar_file);
        synchronized (jar_file_cache) {
          jar_file_cache.put(jar_file_url, cached_jfile);
        }
      }

      return jar_file;

    }

    @Override
    public void connect() throws IOException {
      if (!connected) {
        // Fetch the jar file from the cache,
        jfile = fetchJarFile(jar_file_url);
        // If jar file not found,
        if (jfile == null) {
          throw new FileNotFoundException(jar_file_url.toString());
        }
        
        if (entry_name != null) {
          jar_entry = jfile.getJarEntry(entry_name);
          if (jar_entry == null) {
            // The entry is not found in this jar file,
            throw new FileNotFoundException(url.toString());
          }
        }

        // Ok, success!
        connected = true;
      }
    }

    @Override
    public JarFile getJarFile() throws IOException {
      connect();
      return jfile;
    }

    @Override
    public JarEntry getJarEntry() throws IOException {
      connect();
      return jar_entry;
    }

    @Override
    public int getContentLength() {
      try {
        connect();
        // If no item,
        if (jar_entry == null) {
          return jar_connection.getContentLength();
        }
        else {
          return (int) Math.min(jar_entry.getSize(), Integer.MAX_VALUE);
        }
      }
      catch (IOException e) {
      }
      return -1;
    }

    @Override
    public String getContentType() {
      try {
        connect();
        if (jar_entry == null) {
          return "x-java/jar";
        }
        else {
          String content_type = guessContentTypeFromName(entry_name);
          return content_type;
        }
      }
      catch (IOException e) {
      }
      return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      connect();

      if (entry_name == null) {
        throw new IOException("No entry specified");
      }
      else {
        // Return the input stream,
        return jfile.getInputStream(jar_entry);
      }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return super.getOutputStream();
    }

    @Override
    public Object getContent() throws IOException {
      connect();
      if (entry_name == null) {
        return jfile;
      }
      else {
        return super.getContent();
      }
    }


    @Override
    public Permission getPermission() throws IOException {
      return jar_connection.getPermission();
    }

    @Override
    public String getHeaderFieldKey(int n) {
      return jar_connection.getHeaderFieldKey(n);
    }

    @Override
    public String getHeaderField(int n) {
      return jar_connection.getHeaderField(n);
    }

    @Override
    public String getHeaderField(String name) {
      return jar_connection.getHeaderField(name);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
      return jar_connection.getHeaderFields();
    }

    @Override
    public String getContentEncoding() {
      return jar_connection.getContentEncoding();
    }

    @Override
    public long getLastModified() {
      // Is this correct? Should we get the last modified from the jar entry?
      return jar_connection.getLastModified();
    }

    @Override
    public String getRequestProperty(String key) {
      return jar_connection.getRequestProperty(key);
    }

    @Override
    public void setRequestProperty(String key, String value) {
      jar_connection.setRequestProperty(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
      jar_connection.addRequestProperty(key, value);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
      return jar_connection.getRequestProperties();
    }

    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction) {
      jar_connection.setAllowUserInteraction(allowuserinteraction);
    }

    @Override
    public boolean getAllowUserInteraction() {
      return jar_connection.getAllowUserInteraction();
    }


    @Override
    public boolean getDefaultUseCaches() {
      return jar_connection.getDefaultUseCaches();
    }

    @Override
    public long getIfModifiedSince() {
      return jar_connection.getIfModifiedSince();
    }

    @Override
    public boolean getUseCaches() {
      return jar_connection.getUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultusecaches) {
      jar_connection.setDefaultUseCaches(defaultusecaches);
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {
      jar_connection.setIfModifiedSince(ifmodifiedsince);
    }

    @Override
    public void setUseCaches(boolean usecaches) {
      jar_connection.setUseCaches(usecaches);
    }

  }

  private static class CachedJarFile {

    // A URL reference to the jar file,
    private final URL url_ref;
    private volatile long last_checked_timestamp;
    private final long mod_timestamp;
    private final Reference<JarFile> jfile_ref;

    CachedJarFile(URL url_ref,
              char ref_type,
              long last_checked_timestamp, long mod_timestamp, JarFile jfile) {
      this.url_ref = url_ref;
      this.last_checked_timestamp = last_checked_timestamp;
      this.mod_timestamp = mod_timestamp;
      if (ref_type == 'W') {
        this.jfile_ref = new WeakReference<>(jfile);
      }
      else {
        this.jfile_ref = new SoftReference<>(jfile);
      }
    }

    JarFile getJarFile() {
      return jfile_ref.get();
    }

  }

  private static class NotCloseableJarFile extends JarFile {

    public NotCloseableJarFile(File file) throws IOException {
      super(file);
    }


    @Override
    public void close() throws IOException {
      // We don't let user-code close this,
    }

    /**
     * The real close method used by the cache.
     */
    void trueClose() throws IOException {
      super.close();
    }

    /**
     * When this is finalized we make sure to close it.
     */
    @Override
    protected void finalize() throws IOException {
      super.finalize();
      trueClose();
    }

  }


}
