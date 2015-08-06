/**
 * com.mckoi.mwpcore.MWPURLClassLoader  Nov 5, 2012
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

package com.mckoi.mwpcore;

import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.anttools.ZipDataFile;
import com.mckoi.webplatform.anttools.ZipEntry;
import com.mckoi.webplatform.util.HttpUtils;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This is a class loader that works similarly to URLClassLoader, except it
 * only accepts 'file:' and 'mwpfs:' protocol elements.
 *
 * @author Tobias Downer
 */

public class MWPURLClassLoader extends URLClassLoader {

  /**
   * Parent class loader.
   */
  private final ClassLoader parent;

  /**
   * The set of all objects to check against.
   */
  private final ArrayList<ResourceLocation> locations;

  /**
   * A cache of jar files accessed by this class loader.
   */
  private final Map<String, JarFile> jar_file_cache = new HashMap();

  /**
   * A cache of mwpfs jar files accessed by this class loader.
   */
  private final Map<String, ZipDataFile> mwpfs_jar_file_cache = new HashMap();

  /**
   * Constructor.
   */
  public MWPURLClassLoader(ClassLoader parent) {
    super(new URL[0], parent);
    // Don't allow 'null' class loader,
    if (parent == null) throw new NullPointerException();
    this.parent = parent;
    this.locations = new ArrayList();
  }

  /**
   * Adds a URL to the class loader. The URL must either be a 'file:' or
   * 'mwpfs:' protocol. Any other protocol will fail. The file referenced may
   * either be a directory or a jar file.
   */
  @Override
  public void addURL(URL url) {
    String proto = url.getProtocol();
    if (proto.equals("file") || proto.equals("mwpfs")) {
      String location = HttpUtils.decodeURLFileName(url);
      int type = (proto.equals("file")) ? 1 : 3;
      if (location.endsWith(".jar") || location.endsWith(".zip")) {
        type = type + 1;
      }
      locations.add(new ResourceLocation(type, location, url));
    }
    else {
      throw new RuntimeException("Unsupported protocol: " + proto);
    }
  }

  /**
   * Returns the array of urls managed by this class loader.
   */
  @Override
  public URL[] getURLs() {
    int sz = locations.size();
    URL[] arr = new URL[sz];
    for (int i = 0; i < sz; ++i) {
      arr[i] = locations.get(i).url;
    }
    return arr;
  }

  /**
   * Fetches the JarFile for the given location.
   */
  private JarFile getJarFile(String loc) {
    JarFile jfile = jar_file_cache.get(loc);
    if (jfile == null) {
      try {
        File f = new File(loc);
        if (f.exists() && f.isFile()) {
          jfile = new JarFile(f, false);
          jar_file_cache.put(loc, jfile);
        }
      }
      catch (IOException e) {
        return null;
      }
    }
    return jfile;
  }

  /**
   * Fetches the ZipDataFile for the given MWPFS location.
   */
  private ZipDataFile getMWPFSJarFile(String loc) {

    ZipDataFile zfile = mwpfs_jar_file_cache.get(loc);
    if (zfile == null) {

      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      FileName fname = new FileName(loc);
      String repository_id = fname.getRepositoryId();
      FileRepository file_repo = ctx.getFileRepository(repository_id);

      if (file_repo != null) {
        FileInfo fi = file_repo.getFileInfo(fname.getPathFile());
        if (fi != null && fi.isFile()) {
          try {
            DataFile df = fi.getDataFile();
            zfile = new ZipDataFile(df, "UTF-8", true, true);
            mwpfs_jar_file_cache.put(loc, zfile);
          }
          catch (IOException e) {
            // Fall through,
          }
        }
      }
    }
    return zfile;

  }
  
  
  /**
   * Searches for the resource at the given location, or null if not found.
   */
  private ResourceItem searchForResource(String name, ResourceLocation loc) {

    int type = loc.type;
    String location = loc.location;
    if (type == 1) {       // file directory,
      File f = new File(location + name);
      if (f.exists() && f.isFile()) {
        return new FileResourceItem(f);
      }
    }
    else if (type == 2) {  // file jar,
      JarFile jfile = getJarFile(location);
      if (jfile != null) {
        JarEntry entry = jfile.getJarEntry(name);
        if (entry != null) {
          return new JarResourceItem(jfile, entry);
        }
      }
    }
    else if (type == 3) {  // mwpfs directory,
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      FileName fname = new FileName(location);
      fname = fname.concat(new FileName(name));
      String repository_id = fname.getRepositoryId();
      FileRepository file_repo = ctx.getFileRepository(repository_id);
      if (file_repo != null) {
        FileInfo fi = file_repo.getFileInfo(fname.getPathFile());
        if (fi != null && fi.isFile()) {
          return new FileInfoResourceItem(repository_id, fi);
        }
      }
    }
    else if (type == 4) {  // mwpfs jar,
      ZipDataFile zfile = getMWPFSJarFile(location);
      if (zfile != null) {
        ZipEntry entry = zfile.getEntry(name);
        if (entry != null) {
          return new MWPFSJarResourceItem(location, zfile, entry);
        }
      }
    }

    return null;

  }

  /**
   * Searches the class loader path for the resource with the given name.
   */
  private ResourceItem searchForResource(String name) {

    // Search the locations for the resource with the given name,
    for (ResourceLocation loc : locations) {
      ResourceItem found = searchForResource(name, loc);
      if (found != null) {
        return found;
      }
    }
    return null;

  }

  /**
   * Returns an enumeration of all the resources with the given name.
   */
  private Enumeration<URL> searchForResourceURLs(String name) {

    // Search the locations for the resource with the given name,
    List<URL> urls = new ArrayList();

    for (ResourceLocation loc : locations) {
      ResourceItem found = searchForResource(name, loc);
      if (found != null) {
        try {
          urls.add(found.toURL());
        }
        catch (IOException e) {
          System.err.println("Error searching for resources: " + e.getMessage());
        }
      }
    }

    // Return null if none found, as per the ClassLoader spec,
    if (urls.isEmpty()) {
      return null;
    }
    return Collections.enumeration(urls);

  }



  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
//    System.err.println(" --- findClass(" + name + ")");

    // Convert into a class file name,
    String path = name.replace('.', '/').concat(".class");
    ResourceItem res = searchForResource(path);
    if (res != null) {
      try {
        Class c = defineClass(name, res.toByteBuffer(), (ProtectionDomain) null);
        return c;
      }
      catch (IOException e) {
				throw new ClassNotFoundException(name, e);
      }
    }
    else {
      throw new ClassNotFoundException(name);
    }

  }

  @Override
  public URL findResource(String name) {
//    System.err.println(" --- findResource(" + name + ")");
    ResourceItem res = searchForResource(name);
    if (res != null) {
      try {
        return res.toURL();
      }
      catch (IOException e) {
        return null;
      }
    }
    return null;
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
//    System.err.println(" --- findResources(" + name + ")");
    return searchForResourceURLs(name);
  }

  @Override
  public InputStream getResourceAsStream(String name) {
//    System.err.println(" --- getResourceAsStream(" + name + ")");
    
    InputStream is = null;
    if (parent != null) {
      is = parent.getResourceAsStream(name);
    }
    if (is == null) {
      ResourceItem res = searchForResource(name);
      if (res != null) {
        try {
          is = res.toInputStream();
        }
        catch (IOException e) {
          is = null;
        }
      }
    }
    return is;
  }







  // ----- Resource classes -----
  
  private static class ResourceLocation {

    // Location type;
    //   1 = 'file directory'
    //   2 = 'file jar'
    //   3 = 'mwpfs directory'
    //   4 = 'mwpfs jar'
    private final int type;
    private final String location;
    private final URL url;

    private ResourceLocation(int type, String location, URL url) {
      this.type = type;
      this.location = location;
      this.url = url;
    }

  }

  private static interface ResourceItem {
    
    ByteBuffer toByteBuffer() throws IOException;
    URL toURL() throws IOException;
    InputStream toInputStream() throws IOException;
    
  }


  /**
   * Quick and dirty read of InputStream into a ByteBuffer.
   */
  private static ByteBuffer inputStreamToByteBuffer(InputStream ins, int len)
                                                           throws IOException {
    byte[] buf = new byte[len];
    int pos = 0;
    while (pos < len) {
      int read = ins.read(buf, pos, len - pos);
      if (read == -1) {
        throw new IOException("Unexpected EOF");
      }
      pos += read;
    }
    return ByteBuffer.wrap(buf);
  }

  /**
   * Quick and dirty read of InputStream into a ByteBuffer.
   */
  private static ByteBuffer inputStreamToByteBuffer(InputStream ins)
                                                           throws IOException {
    final int BUF_SIZE = 8192;
    ByteArrayOutputStream outs = new ByteArrayOutputStream(BUF_SIZE);
    byte[] buf = new byte[BUF_SIZE];
    while (true) {
      int read = ins.read(buf, 0, BUF_SIZE);
      // When we reached the end,
      if (read == -1) {
        return ByteBuffer.wrap(outs.toByteArray());
      }
      outs.write(buf, 0, read);
    }
  }


  private static class FileResourceItem implements ResourceItem {

    private final File f;
    
    public FileResourceItem(File f) {
      this.f = f;
    }

    @Override
    public ByteBuffer toByteBuffer() throws IOException {
      InputStream ins = toInputStream();
      int file_len = (int) f.length();
      return inputStreamToByteBuffer(ins, file_len);
    }

    @Override
    public URL toURL() throws IOException {
      return f.toURI().toURL();
    }

    @Override
    public InputStream toInputStream() throws IOException {
      return new FileInputStream(f);
    }

  }

  private static class JarResourceItem implements ResourceItem {

    private final JarFile jfile;
    private final JarEntry entry;

    public JarResourceItem(JarFile jfile, JarEntry entry) {
      this.jfile = jfile;
      this.entry = entry;
    }

    @Override
    public ByteBuffer toByteBuffer() throws IOException {
      InputStream ins = toInputStream();
      int file_len = (int) entry.getSize();
      if (file_len >= 0) {
        return inputStreamToByteBuffer(ins, file_len);
      }
      else {
        return inputStreamToByteBuffer(ins);
      }
    }

    @Override
    public URL toURL() throws IOException {
      String fname = jfile.getName();
      return new URL("jar:file:" + fname + "!/" + entry.getName());
    }

    @Override
    public InputStream toInputStream() throws IOException {
      return jfile.getInputStream(entry);
    }

  }

  private static class FileInfoResourceItem implements ResourceItem {

    private final String repository_id;
    private final FileInfo fi;

    public FileInfoResourceItem(String repository_id, FileInfo fi) {
      this.repository_id = repository_id;
      this.fi = fi;
    }

    @Override
    public ByteBuffer toByteBuffer() throws IOException {
      DataFile df = fi.getDataFile();
      InputStream ins = DataFileUtils.asInputStream(fi.getDataFile());
      int file_len = (int) df.size();
      return inputStreamToByteBuffer(ins, file_len);
    }

    @Override
    public URL toURL() throws IOException {
      return new URL(
              "mwpfs", null, -1, "/" + repository_id + fi.getAbsoluteName());
    }

    @Override
    public InputStream toInputStream() throws IOException {
      return DataFileUtils.asInputStream(fi.getDataFile());
    }

  }

  private static class MWPFSJarResourceItem implements ResourceItem {

    private final String location_name;
    private final ZipDataFile zfile;
    private final ZipEntry entry;

    public MWPFSJarResourceItem(String location_name,
                                ZipDataFile zfile, ZipEntry entry) {
      this.location_name = location_name;
      this.zfile = zfile;
      this.entry = entry;
    }

    @Override
    public ByteBuffer toByteBuffer() throws IOException {
      InputStream ins = toInputStream();
      int file_len = (int) entry.getSize();
      if (file_len >= 0) {
        return inputStreamToByteBuffer(ins, file_len);
      }
      else {
        return inputStreamToByteBuffer(ins);
      }
    }

    @Override
    public URL toURL() throws IOException {
      return new URL("jar:mwpfs:" + location_name + "!/" + entry.getName());
    }

    @Override
    public InputStream toInputStream() throws IOException {
      return zfile.getInputStream(entry);
    }

  }

}
