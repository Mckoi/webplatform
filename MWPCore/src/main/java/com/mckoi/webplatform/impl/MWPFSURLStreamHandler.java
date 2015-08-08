/**
 * com.mckoi.webplatform.MWPFSURLStreamHandler  May 12, 2010
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

import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.MWPRuntimeException;
import com.mckoi.webplatform.util.HttpUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.Permission;
import java.text.MessageFormat;
import java.util.*;

/**
 * A stream handler for files in a Mckoi Web Platform file system. This
 * only permits access to files in the file system owned by the current account
 * (as reported by the thread context). This may change in the future.
 * <p>
 * A MWPFS URI will look something like the following;
 * <pre>
 * "mwpfs:/toby/data/asubdir/foobar.txt"
 * </pre>
 * The syntax is;
 * <pre>
 *  mwpfs:/[repository id]/[absolute file or directory reference]
 * </pre>
 * A reference to a directory will always end with a '/'. Otherwise the
 * reference is to a file.
 *
 * @author Tobias Downer
 */

public class MWPFSURLStreamHandler extends URLStreamHandler {

  /**
   * Set to true for verbose output of access to this URI.
   */
  private final static boolean DBG_OUTPUT = false;

  @Override
  protected URLConnection openConnection(URL u) throws IOException {

    // Extract the path from the URL
    // The URL form is, 'mwpfs:/[account]/[file_spec]'
    String fname = HttpUtils.decodeURLFileName(u);
    int delim = fname.indexOf("/", 1);
    if (delim == -1) {
      throw new IOException("Not a MWP URI: " + fname);
    }
    String repository_id = fname.substring(1, delim);

    // Check the thread has the privs to access this file system,
    PlatformContextImpl ctx = new PlatformContextImpl();
    if (!ctx.isRepositoryAccessible(repository_id)) {
      throw new SecurityException(
                             "Not accessible repository id: " + repository_id);
    }

    // Fetch the file repository,
    FileRepository file_system = ctx.getMWPFSURLFileRepository(repository_id);

//    // DEBUGGIN,
//    if (repository_id.startsWith("$") &&
//        (fname.endsWith(".jar") || fname.endsWith(".zip")) ) {
//      new Error(u.toString()).printStackTrace();
//    }

    return new FileRepoURLConnection(u, file_system);

  }



  

  // ------

  private static class FileRepoURLConnection extends URLConnection {

    private final FileRepository file_system;
    
    private DataFile file;
    private String content_type;
    private long last_modified;
    private Map <String, List<String>> fields_map;

    private FileRepoURLConnection(URL u, FileRepository file_system) {
      super(u);
      this.file_system = file_system;
    }

    /**
     * Returns null Permission because we handle our own security when
     * accessing the object.
     */
    @Override
    public Permission getPermission() throws IOException {
      // TODO: Should we make an MWPFS permission?
      return null;
    }

    private boolean internalConnect() {
      if (!connected) {

        String file_name = HttpUtils.decodeURLFileName(url);
        int delim = file_name.indexOf("/", 1);
        file_name = file_name.substring(delim);

        // Not found, return false,
        FileInfo finfo = file_system.getFileInfo(file_name);
        if (finfo == null || finfo.isDirectory()) {
          return false;
        }
        content_type = finfo.getMimeType();
        last_modified = finfo.getLastModified();
        file = finfo.getDataFile();

        connected = true;
      }
      return true;
    }

    private void internalCheckConnect() {
      if (!internalConnect()) {
//        System.out.println("FAILED CONNECT: " + url.getFile());
//        new Error().printStackTrace();
        throw new MWPRuntimeException("Resource not found: {0}", url.getFile());
      }
    }

    private void addField(HashMap <String, List<String>> map,
                          String key, String value) {
      map.put(key, Collections.singletonList(value));
    }

    private void populateFields() {
      if (fields_map == null) {
        internalCheckConnect();
        HashMap<String, List<String>> fields = new HashMap<>();

        long size = file.size();

        addField(fields, "content-length",
                 String.valueOf(size > Integer.MAX_VALUE ? -1 : size));
        addField(fields, "last-modified", String.valueOf(last_modified));
        addField(fields, "date", String.valueOf(last_modified));
        addField(fields, "content-type", content_type);
        fields_map = fields;
      }
    }

    @Override
    public void connect() throws IOException {
      boolean b = internalConnect();
      // If not connected,
      if (!b) {
        throw new IOException(
               MessageFormat.format("File ''{0}'' not found", url.getFile()));
      }
    }

    @Override
    public String getHeaderFieldKey(int n) {
      populateFields();
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getHeaderFieldKey(" + n + ")");
      }
      Iterator<String> iterator = fields_map.keySet().iterator();

      int i = 0;
      while (iterator.hasNext()) {
        String val = iterator.next();
        if (i == n) {
          return val;
        }
        ++i;
      }
      return null;
    }

    @Override
    public String getHeaderField(int n) {
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getHeaderField(" + n + ")");
      }
      return getHeaderField(getHeaderFieldKey(n));
    }

    @Override
    public String getHeaderField(String name) {
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getHeaderField(" + name + ")");
      }
      populateFields();
      return fields_map.get(name).get(0);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getHeaderFields()");
      }
      populateFields();
      return Collections.unmodifiableMap(fields_map);
    }

    @Override
    public String getContentEncoding() {
      return null;
    }

    @Override
    public int getContentLength() {
      internalCheckConnect();
      long size = file.size();
      int ret_sz = size > Integer.MAX_VALUE ? -1 : (int) size;
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getContentLength()");
        System.out.println("  = " + ret_sz);
      }
      return ret_sz;
    }

    @Override
    public String getContentType() {
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getContentType()");
      }
      internalCheckConnect();
      return content_type;
    }

    @Override
    public long getLastModified() {
      internalCheckConnect();
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getLastModified()");
        System.out.println("  = " + last_modified);
      }
      return last_modified;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getInputStream()");
      }
      if (!internalConnect()) {
        throw new IOException(
                       MessageFormat.format("Not found: {0}", url.getFile()));
      }
      return DataFileUtils.asInputStream(file);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      // NOTE: We can't write to a URL because there's no transactional
      //   functionality in the URL API (ie. no commit)
      if (DBG_OUTPUT) {
        System.out.println(getURL() + " getOutputStream()");
      }
      return super.getOutputStream();
    }

  }

}
