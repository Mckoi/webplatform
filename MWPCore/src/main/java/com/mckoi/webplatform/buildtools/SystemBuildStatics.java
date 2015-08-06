/**
 * com.mckoi.webplatform.buildtools.SystemBuildStatics  Apr 12, 2011
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

package com.mckoi.webplatform.buildtools;

import com.mckoi.webplatform.util.HttpUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.tools.JavaCompiler;

/**
 * Static methods for managing system properties of the build system.
 *
 * @author Tobias Downer
 */

public class SystemBuildStatics {

  /**
   * The JVM JavaCompiler object.
   */
  private static JavaCompiler jvm_java_compiler = null;

  /**
   * The database of all JVM packages and the file objects they contain. This
   * database is built once.
   */
  private static SortedMap<String, List<URL>> jvm_package_map = null;




  /**
   * Returns the dictionary of all package and resource names in the system
   * JVM class loader. The returned object is immutable.
   */
  public static SortedMap<String, List<URL>> getSystemJVMPackageMap() {
    return jvm_package_map;
  }

  /**
   * Returns the Java Compiler installed in the system.
   */
  public static JavaCompiler getSystemJVMJavaCompiler() {
    return jvm_java_compiler;
  }




  private static int addToPackageMap(
                             TreeMap<String, List<URL>> package_map,
                             String package_name, URL resource_name) {
    List<URL> package_list = package_map.get(package_name);
    if (package_list == null) {
      package_list = new ArrayList();
      package_map.put(package_name, package_list);
    }
    if (!package_list.contains(resource_name)) {
      package_list.add(resource_name);
//      System.out.println(package_name + " -> " + resource_name);
      return 1;
    }
    return 0;
  }



  private static int findCompilerSymbols(
          TreeMap<String, List<URL>> package_map,
          String package_name, File directory) throws IOException {
    int count = 0;
    File[] files = directory.listFiles();
    for (int i = 0; i < files.length; ++i) {
      File f = files[i];
      if (f.isDirectory()) {
        String child_pname =
                package_name.equals("") ? f.getName() :
                                          package_name + "." + f.getName();
        count += findCompilerSymbols(package_map, child_pname, f);
      }
      else {
        count += addToPackageMap(package_map, package_name, f.toURI().toURL());
      }
    }
    return count;
  }



  private static int findCompilerSymbols(
          TreeMap<String, List<URL>> package_map, String local_file_name)
                                                           throws IOException {
    int count = 0;
    File file = new File(local_file_name);

    if (file.exists()) {
      if (local_file_name.endsWith(".jar")) {
        
        // The first part of the URL string,
        // eg. 'file:/mckoiddb/lib/MckoiDDB-1.3.jar!'
        StringBuilder url_start = new StringBuilder();
        url_start.append(file.toURI().toURL().toExternalForm());
        url_start.append("!/");
        String url_start_str = url_start.toString();

        // Load the .jar file,
        JarFile jar_file = new JarFile(file);
        Enumeration<JarEntry> entries = jar_file.entries();
        while (entries.hasMoreElements()) {
          JarEntry jar_entry = entries.nextElement();
          final String name = jar_entry.getName();
          // Split into package/resource name
          int p = name.lastIndexOf('/');
          if (p >= 0) {
            String package_name = name.substring(0, p);
            String resource_name = name.substring(p + 1);
            package_name = package_name.replace("/", ".");

            if (!name.startsWith("META-INF/") &&
                !name.startsWith("WEB-INF/") &&
                !resource_name.equals("")) {
              
              // Create the jar url for this resource,
              URL jar_url =
                    new URL("jar", null, -1, url_start_str + name);

              count += addToPackageMap(package_map, package_name, jar_url);

            }
          }

        }
      }
      else if (file.isDirectory()) {
        // Load the directory
        count += findCompilerSymbols(package_map, "", file);
      }
      else {
        System.err.println("Ignored: " + local_file_name);
//        throw new RuntimeException(
//                   "Invalid reference on boot class path: " + local_file_name);
      }
    }

    return count;

  }

  /**
   * Builds and caches all the symbols from the system class loader. This
   * must be run under a security manager that allows access to the system
   * files. May only be called once.
   */
  public static void buildSystemCompilerSymbols() {

    if (jvm_package_map != null) {
      throw new RuntimeException("Compiler symbols already built");
    }
    TreeMap<String, List<URL>> package_map = new TreeMap();

    // From the system class loader,
    String files = System.getProperty("sun.boot.class.path");

    // Split
    String[] file_list = files.split(File.pathSeparator);

    try {
      int count = 0;

      // Process the lists,
      for (String file : file_list) {
        count += findCompilerSymbols(package_map, file);
      }

      // Find symbols from the following class loader and its parents,
      ClassLoader sys_cl = SystemBuildStatics.class.getClassLoader();

      while (sys_cl != null) {

        URLClassLoader url_sys_cl = (URLClassLoader) sys_cl;
        URL[] urls = url_sys_cl.getURLs();

        for (URL url : urls) {
          // Turn the url into a file,
          if (url.getProtocol().equals("file")) {
            count += findCompilerSymbols(
                              package_map, HttpUtils.decodeURLFileName(url));
          }
          else {
            throw new RuntimeException("URL protocol unknown: " + url);
          }

        }

        sys_cl = sys_cl.getParent();
      }

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Compact the package lists and make the dictionary immutable,
    for (String package_name : package_map.keySet()) {
      List<URL> items = package_map.get(package_name);
      ArrayList<URL> compact_items = new ArrayList(items.size());
      compact_items.addAll(items);
      package_map.put(package_name,
                      Collections.unmodifiableList(compact_items));
    }

    // Set and make the map immutable,
    jvm_package_map = Collections.unmodifiableSortedMap(package_map);

//    int total = 0;
//    int c = 0;
//    for (String package_name : jvm_package_map.keySet()) {
//      List<String> items = jvm_package_map.get(package_name);
//      int count = items.size();
//      System.out.println(package_name + " (" + items.size() + " resources)");
//      total = total + count;
//      ++c;
//    }
//
//    System.out.println("Avg: " + (total / c));

  }

  /**
   * Sets the JVM compiler. May only be set once.
   */
  public static void setJVMJavaCompiler(JavaCompiler java_compiler) {
    // Set by the initialization procedure,
    if (jvm_java_compiler == null) {
      jvm_java_compiler = java_compiler;
    }
    else {
      throw new RuntimeException("JVM Java Compiler already set");
    }
  }

}
