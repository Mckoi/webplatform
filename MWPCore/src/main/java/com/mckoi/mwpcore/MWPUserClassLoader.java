/**
 * com.mckoi.mwpcore.MWPUserClassLoader  Oct 3, 2012
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

import com.mckoi.util.ByteArrayBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * The class loader over which user code is run. This class loader provides
 * access to a global 'system' package/class database that child class loaders
 * can query. All children of this should check loaded classes via this
 * database and pass through those classes to the parent class loader.
 *
 * @author Tobias Downer
 */

public final class MWPUserClassLoader extends URLClassLoader {

  private final ClassLoader parent_cl;
  private final ClassNameValidator system_classes;

  private final URL[] url_jars;
  private final boolean debug_report;

  MWPUserClassLoader(URL[] url_jars,
                     ClassLoader parent,
                     ClassNameValidator system_classes,
                     boolean debug_report) {
    super(url_jars, parent);

    this.url_jars = url_jars;
    this.debug_report = debug_report;

    // Asserts,
    if (parent == null) throw new NullPointerException();
    if (url_jars == null) throw new NullPointerException();

    this.parent_cl = parent;
    this.system_classes = system_classes;

  }

  MWPUserClassLoader(ClassLoader parent,
                     ClassNameValidator system_classes,
                     boolean debug_report) {
    this(new URL[0], parent, system_classes, debug_report);
  }

  MWPUserClassLoader(ClassLoader parent, boolean debug_report) {
    this(parent, null, debug_report);
  }

  /**
   * Returns an array of all .jar files at the given path.
   */
  static URL[] getJarsInPath(File path) throws IOException {
    if (path == null || !path.exists() || !path.isDirectory()) {
      return new URL[0];
    }

    File[] all_files = path.listFiles();

    List<URL> url_list = new ArrayList<>(all_files.length);
    for (File file : all_files) {
      String fname = file.getName();
      if (fname.endsWith(".jar")) {
        // Add the URL to the list,
        url_list.add(file.toURI().toURL());
      }
    }
    return url_list.toArray(new URL[url_list.size()]);
  }

//  /**
//   * Adds all the .jar files at the given path in the local file system to the
//   * class path.
//   */
//  void addAllToClassPath(File path) throws IOException {
//    URL[] all_jars = getJarsInPath(path);
//    for (URL jar : all_jars) {
//      super.addURL(jar);
//    }
//  }

  /**
   * Returns true if the class name is a system class, and therefore should be
   * filtered through to the parent.
   */
  public boolean isSystemClassName(String name) {
    return system_classes.isAcceptedClass(name);
  }

  /**
   * Returns true if the given resource name is a system resource and is
   * allowed to be accessed by untrusted clients.
   */
  private boolean isSystemResourcePath(String name) {
    return system_classes.isAllowedResource(name);
  }

  /**
   * Returns true if the given resource name is a resource from the 'mjs/'
   * directory.
   */
  private boolean isMJSResource(String name) {
    return name.startsWith("mjs/");
  }

  /**
   * Returns true if the given resource name is a reference to META-INF
   * resource directory.
   */
  private boolean isMetaInfResourcePath(String name) {
    return name.startsWith("META-INF/");
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {

    if (debug_report) {
      System.out.println("DEBUG: findClass: " + name);
      System.out.println("DEBUG: url_jars = " + Arrays.asList(url_jars));
    }

    try {

      // NOTE; Rhino classes are defined under the user class loader.
      //   This is a bit of a ClassLoader hack to ensure our library Rhino code
      //   is loaded in the same class loader as the Rhino .jar (the rhino jar
      //   is added to the user class loader).

      // PENDING: Hard-coded package dependence.

      if (name.startsWith("com.mckoi.webplatform.rhino.")) {

        // The resource needs to be fetched under a privileged action because
        // the Java API will not allow access to resources that the code source
        // doesn't have read access to.
        // Got to love Java verbosity sometimes....

        try {
          return AccessController.doPrivileged(
                                      new PrivilegedExceptionAction<Class<?>>() {
            @Override
            public Class<?> run() throws Exception {
              // Convert the class name to a class name file,
              String path = name.replace('.', '/').concat(".class");
              InputStream ins = parent_cl.getResourceAsStream(path);
              // If not found,
              if (ins == null) {
                throw new ClassNotFoundException(name);
              }
              // Otherwise define the class from the data in the .class file,
              ByteArrayBuilder b = new ByteArrayBuilder();
              b.fillFully(ins);
              return defineClass(name, b.getBuffer(), 0, b.length());
            }
          });
        }
        catch (PrivilegedActionException ex) {
          Exception inner_ex = ex.getException();
          if (inner_ex instanceof ClassNotFoundException) {
            throw (ClassNotFoundException) inner_ex;
          }
          throw new RuntimeException(inner_ex);
        }
      }

      else {
        return super.findClass(name);
      }

    }
    catch (ClassNotFoundException ex) {
      if (debug_report) {
        System.out.println("DEBUG: Class Not Found!");
      }
      throw ex;
    }

  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
                                                throws ClassNotFoundException {

    // Has it been loaded already?
    Class c = findLoadedClass(name);

    ClassNotFoundException ex= null;

    // Look for the class in the parent if it's a system class,
    if (c == null) {
      // If it's a system class,
      if (isSystemClassName(name)) {
        // Go to the parent,
        try {
          c = parent_cl.loadClass(name);
        }
        catch (ClassNotFoundException e) {
          ex = e;
        }
      }
    }

    // Otherwise look for it in this loader,
    if (c == null) {
      try {
        c = findClass(name);
      }
      catch (ClassNotFoundException e) {
        ex = e;
      }
    }

    // Class is not a system class and in parent, or is it found in this
    // loader, therefore throw 'ClassNotFoundException'
    if (c == null) {
      throw ex;
    }

    // If we need to resolve it,
    if (resolve) {
      resolveClass(c);
    }

    return c;

  }

  @Override
  public URL getResource(String name) {

    URL url = null;

    if (isSystemResourcePath(name) || isMetaInfResourcePath(name)) {
      // Load from the parent if it's a valid system resource
      url = parent_cl.getResource(name);
    }

    // If not found, load from the local class loader,
    if (url == null) {
      // We assume that any resources added to this class loader are allowed
      // to be accessed by classes loaded here.
      url = this.findResource(name);
      // NOTE: Why is it necessary to do this?
      if (url == null && name.startsWith("/")) {
        url = this.findResource(name.substring(1));
      }
    }

    // Go to the parent for 'mjs' resource (this is another hard-coded
    // dependence I don't like to do),
    if (url == null && isMJSResource(name)) {
      url = parent_cl.getResource(name);
    }

    return url;

  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {

    // Load from the parent if it's a valid system resource
    Enumeration<URL> parent_resources = null;

    if (isSystemResourcePath(name) || isMetaInfResourcePath(name)) {
      parent_resources = parent_cl.getResources(name);
    }

    // Load from this,
    Enumeration<URL> this_resources = findResources(name);

    Enumeration<URL> parent2_resources = null;
    if (isMJSResource(name)) {
      parent2_resources = parent_cl.getResources(name);
    }

    // Merge the lists,
    ArrayList<URL> resource_list = new ArrayList<>();
    if (parent_resources != null) {
      while (parent_resources.hasMoreElements())
                        resource_list.add(parent_resources.nextElement());
    }
    if (this_resources != null) {
      while (this_resources.hasMoreElements())
                        resource_list.add(this_resources.nextElement());
    }
    if (parent2_resources != null) {
      while (parent2_resources.hasMoreElements())
                        resource_list.add(parent2_resources.nextElement());
    }

    // Return the enumeration,
    return Collections.enumeration(resource_list);

  }

}
