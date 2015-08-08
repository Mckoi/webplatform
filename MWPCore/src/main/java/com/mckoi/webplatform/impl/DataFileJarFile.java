/**
 * com.mckoi.webplatform.impl.DataFileJarFile  Nov 5, 2012
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
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.anttools.ZipDataFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Returns a java.util.jar.JarFile that wraps a DataFile. We pretty much
 * have to override every public method to implement this because this Java
 * API class isn't designed very well. We also need to link all DataFileJarFile
 * objects with an empty zip file in the local file system.
 * <p>
 * There's a number of quite nasty hacks needed to implement this class. As
 * well as the reference to an empty.zip file, also DataFileJarFile will not
 * return the manifest file via the 'getJarEntry' method. The reason for this
 * is that the standard JVM implementation uses a SharedSecret back-door to
 * allow the class loader to query the class-path property in the manifest
 * file, which gets added to the class loader search path.
 * <p>
 * By denying access to the manifest jar entry, the class loader will not load
 * the class path URLs into the search list.
 *
 * @author Tobias Downer
 */

public class DataFileJarFile extends JarFile {

  /**
   * HORRIBLE HORRIBLE HACK to get around the issue that the API forces you
   * to give java.util.jar.JarFile a zip file to open.
   * 
   * Note that in Java 1.7 we can make a virtual File to work around this
   * problem altogether.
   */
  private static File EMPTY_ZIP = null;
  public static void setEmptyZipLocation(File empty_zip_location) {
    if (EMPTY_ZIP == null) {
      EMPTY_ZIP = empty_zip_location;
    }
  }
  

  private SoftReference<Manifest> manifest_ref;

  private final FileName file_name;
  private final ZipDataFile zip_data_file;
  private final int mode;
  private final boolean verify;

  public DataFileJarFile(FileName file_name, DataFile file,
                         boolean verify, int mode) throws IOException {
    super(EMPTY_ZIP, false, OPEN_READ);

    // The real zip data file,
    this.file_name = file_name;
    this.zip_data_file = new ZipDataFile(file, "UTF-8", true, true);

    this.verify = verify;
    this.mode = mode;

  }

  @Override
  public Enumeration<JarEntry> entries() {
    final Enumeration<com.mckoi.webplatform.anttools.ZipEntry> entries =
                                                    zip_data_file.getEntries();
    return new Enumeration<JarEntry>() {
      @Override
      public boolean hasMoreElements() {
        return entries.hasMoreElements();
      }
      @Override
      public JarEntry nextElement() {
        return new DataFileJarFileEntry(entries.nextElement());
      }
    };
  }

  @Override
  public ZipEntry getEntry(String name) {
    com.mckoi.webplatform.anttools.ZipEntry entry =
                                                  zip_data_file.getEntry(name);
    if (entry == null) {
      return null;
    }
    else {
      return new DataFileJarFileEntry(entry);
    }
  }

  @Override
  public InputStream getInputStream(ZipEntry ze) throws IOException {
    DataFileJarFileEntry je = (DataFileJarFileEntry) ze;
    return zip_data_file.getInputStream(je.backed_ze);
  }

  /**
   * Get the Jar entry, including the manifest file.
   * 
   * @param name
   * @return 
   */
  public JarEntry realGetJarEntry(String name) {
    return (JarEntry) getEntry(name);
  }

  @Override
  public JarEntry getJarEntry(String name) {
    // PENDING: We have to disable manifest access here because the Sun JVM is
    //   sneaky and it has a shared secret access to the underlying JarFile
    //   which it uses to query the empty zip file.
    //
    // The only way I can think of hacking around this is by making a temporary
    //   zip file in the system temp directory and copying the manifest into
    //   it.
    if (name.equals(MANIFEST_NAME)) {
      return null;
    }

    return realGetJarEntry(name);
  }

  @Override
  public Manifest getManifest() throws IOException {
    Manifest man = manifest_ref != null ? manifest_ref.get() : null;

    if (man == null) {
      // NOTE: we use 'realGetJarEntry' here to work around the JVM manifest
      //   access hack.
      JarEntry man_entry = realGetJarEntry(MANIFEST_NAME);

      if (man_entry != null) {
        man = new Manifest(getInputStream(man_entry));
        manifest_ref = new SoftReference<>(man);
      }
    }
    return man;
  }

  @Override
  public void close() throws IOException {
    zip_data_file.close();
  }

  @Override
  public String getName() {
    return file_name.toString();
  }

  @Override
  public int size() {
    return zip_data_file.getEntriesCount();
  }


//  protected void finalize() {
//    System.out.println("DataFileJarFile finalized: " + file_name);
//  }



  /**
   * JarEntry implementation.
   */
  private class DataFileJarFileEntry extends JarEntry {

    private com.mckoi.webplatform.anttools.ZipEntry backed_ze;

    DataFileJarFileEntry(com.mckoi.webplatform.anttools.ZipEntry ze) {
      super(ze);
      backed_ze = ze;
    }

    @Override
    public Attributes getAttributes() throws IOException {
      Manifest man = DataFileJarFile.this.getManifest();
      if (man != null) {
        return man.getAttributes(getName());
      }
      else {
        return null;
      }
    }

  }


}
