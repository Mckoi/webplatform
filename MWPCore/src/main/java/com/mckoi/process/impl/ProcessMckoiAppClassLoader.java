/**
 * com.mckoi.process.impl.ProcessMckoiAppClassLoader  Mar 28, 2012
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

package com.mckoi.process.impl;

import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.DBPathSnapshot;
import com.mckoi.mwpcore.MWPUserClassLoader;
import com.mckoi.webplatform.MckoiDDBWebPermission;
import com.mckoi.webplatform.util.MonotonicTime;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.StringTokenizer;

/**
 * The class loader used to run user processes in an application. This uses
 * the same load priority for classes as the web application class loader,
 * and in fact is pretty much a copy-paste from that class.
 * <p>
 * Unfortunately due to the design of Java's ClassLoader, I don't see a way
 * to generalize the behavior because this and Jetty's classloader have
 * different parents. I suppose we could use Jetty's classloader here but what
 * web app context would we use for it? Seems rather hacky.
 *
 * @author Tobias Downer
 */

public final class ProcessMckoiAppClassLoader extends URLClassLoader {

  /**
   * Permission for adding to a class loader (NOTE: we use the same priv as
   * the web app class loader).
   */
  private static final MckoiDDBWebPermission CLASSLOADER_ADD_CLASSPATH =
                   new MckoiDDBWebPermission("webAppClassLoader.addClasspath");

  /**
   * The system classes that are permitted.
   */
  private final ClassNameValidator allowed_system_classes;

  /**
   * The USER class loader.
   */
  private final MWPUserClassLoader user_classloader;

  /**
   * The version of the web application this class loader is based on (uses
   * the /system/webapps.properties binary version here).
   */
  private final String app_version;

  /**
   * The database snapshot we are using for this class loader. This may be
   * updated at any time.
   */
  private volatile DBPathSnapshot db_snapshot;

  /**
   * The last time the db was checked for this class loader.
   */
  private volatile long last_db_check;

  /**
   * Constructs the class loader using the class loader of this class as the
   * parent.
   */
  ProcessMckoiAppClassLoader(
                MWPUserClassLoader user_classloader,
                String app_version,
                DBPathSnapshot version_snapshot,
                ClassNameValidator allowed_system_classes) throws IOException {
    super(new URL[0], user_classloader);
    
    this.user_classloader = user_classloader;
    this.app_version = app_version;
    this.allowed_system_classes = allowed_system_classes;
    updateDBCheck(version_snapshot);
  }

  /**
   * Returns the user class loader (the parent).
   */
  MWPUserClassLoader getUserClassLoader() {
    return user_classloader;
  }

  /**
   * Returns the application version of this class loader.
   */
  String getAppVersion() {
    return app_version;
  }

  /**
   * Updates the db check timestamp to the current time.
   */
  void updateDBCheck(DBPathSnapshot version_snapshot) {
    this.db_snapshot = version_snapshot;
    last_db_check = MonotonicTime.now();
  }

  /**
   * Returns the last timestamp the db was checked for this class loader.
   */
  long getLastDBCheck() {
    return last_db_check;
  }

  /**
   * Returns the current version snapshot being used in this class loader.
   * This will always return a recent version of the database that contains
   * the application data used by this class loader.
   */
  DBPathSnapshot getDBPathSnapshot() {
    return db_snapshot;
  }

  // -----

  @Override
  protected Class<?> findClass(final String name)
                                               throws ClassNotFoundException {

//    System.out.println("ProcessMckoiAppClassLoader.findClass (" + name + ")");

    try {
      return super.findClass(name);
    }
    catch (ClassNotFoundException ex) {
//      System.out.println("NOOO, We can't find you!!");
//      URL[] urls = super.getURLs();
//      System.out.println("URLS = " + Arrays.asList(urls));
      throw ex;
    }
  }

  @Override
  public URL findResource(final String name) {
    return super.findResource(name);
  }

  @Override
  public Enumeration<URL> findResources(final String name) throws IOException {
    return super.findResources(name);
  }

  // -----

  @Override
  public Class loadClass(String name) throws ClassNotFoundException {
    return loadClass(name, false);
  }

  @Override
  protected synchronized Class loadClass(String name, boolean resolve)
                                               throws ClassNotFoundException {

    Class c = findLoadedClass(name);

    ClassNotFoundException ex = null;

    // Ask the user class loader if it has the class we're looking for,
    if (c == null) {
      try {
        c = user_classloader.loadClass(name);
      }
      catch (ClassNotFoundException e) {
        ex = e;
      }
    }

    // Otherwise look for the class in this class loader,
    if (c == null) {
      try {
        c = this.findClass(name);
      }
      catch (ClassNotFoundException e) {
        ex = e;
      }
    }

//    boolean tried_parent = false;
//
//    // If there's a parent and it's a system class name, delegate to the
//    // parent first,
//    if (c == null && isSystemClassName(name)) {
//      try {
//        c = user_classloader.loadClass(name);
//      }
//      catch (ClassNotFoundException e) {
//        ex = e;
//      }
//      tried_parent = true;
//    }
//
//    // Otherwise look for the class in this class loader,
//    if (c == null) {
//      try {
//        c = this.findClass(name);
//      }
//      catch (ClassNotFoundException e) {
//        ex = e;
//      }
//    }
//
//    // If we haven't tried the parent, then try it (this happens when it's not
//    // a system class but not found in this loader),
//    if (!tried_parent) {
//      try {
//        c = user_classloader.loadClass(name);
//      }
//      catch (ClassNotFoundException e) {
//        ex = e;
//      }
//    }

    // Class not found,
    if (c == null) {
      throw ex;
    }

    if (resolve) {
      resolveClass(c);
    }

    return c;
    
//    Class c = findLoadedClass(name);
//
//    if (c == null) {
//      // Load from the parent,
//      try {
//        c = user_classloader.loadClass(name);
//      }
//      catch (ClassNotFoundException e) {
//        // Load from here,
//        c = findClass(name);
//      }
//    }
//
//    if (resolve) {
//      resolveClass(c);
//    }
//
//    return c;

  }

  @Override
  public URL getResource(String name) {
//    System.out.println("%%% getResource(" + name + ")");

    URL url = null;

    // Load from the parent if it's a valid system resource
    url = user_classloader.getResource(name);

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


//    boolean tried_parent = false;
//
//    if (isSystemResourcePath(name)) {
//      // Load from the parent if it's a valid system resource
//      url = user_classloader.getResource(name);
//      tried_parent = true;
//    }
//
//    // If not found, load from the local class loader,
//    if (url == null) {
//      // We assume that any resources added to this class loader are allowed
//      // to be accessed by classes loaded here.
//      url = this.findResource(name);
//      // NOTE: Why is it necessary to do this?
//      if (url == null && name.startsWith("/")) {
//        url = this.findResource(name.substring(1));
//      }
//    }
//
//    // Try the parent if we haven't tried it yet,
//    if (!tried_parent) {
//      url = user_classloader.getResource(name);
//    }

    return url;

  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
//    System.out.println("%%% getResources(" + name + ")");

    // Load from the parent if it's a valid system resource
    Enumeration<URL> parent_resources;
    parent_resources = user_classloader.getResources(name);

    // Load from this,
    Enumeration<URL> this_resources = findResources(name);

    // Merge the lists,
    ArrayList<URL> resource_list = new ArrayList();
    if (parent_resources != null) {
      while (parent_resources.hasMoreElements())
                            resource_list.add(parent_resources.nextElement());
    }
    if (this_resources != null) {
      while (this_resources.hasMoreElements())
                            resource_list.add(this_resources.nextElement());
    }

    // Return the enumeration,
    return Collections.enumeration(resource_list);
  }

  @Override
  public PermissionCollection getPermissions(CodeSource cs) {
    return super.getPermissions(cs);
  }

  // ----- Queries -----

  /**
   * Returns true if the given class name is a system class or false if not.
   */
  private boolean isSystemClassName(String name) {
    return allowed_system_classes.isAcceptedClass(name);
  }

  /**
   * Returns true if the given resource name is a system resource and is
   * allowed to be accessed by untrusted clients.
   */
  private boolean isSystemResourcePath(String name) {
    return allowed_system_classes.isAllowedResource(name);
  }

  // -----

  /**
   * Adds a class path string to this class loader, where the string is a
   * comma or semi-colon deliminated list of paths. Each path entry must be a
   * reference into the mwpfs directory of the application's container. For
   * example;
   *  "mwpfs:/admin/apps/mwpadmin/WEB-INF/classes/;mwpfs:/toby/apps/mwpadmin/WEB-INF/lib/mylib.jar"
   * <p>
   * A class path reference may be a directory reference, or a .jar or .zip
   * file.
   */
  void addClassPath(String classPath) throws PException {

//    System.out.println("WebAppCL.addClassPath(" + classPath + ")");

    // Check with the security manager that this is permitted,
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(CLASSLOADER_ADD_CLASSPATH);
    }

    // Add the class paths,
    StringTokenizer tokenizer= new StringTokenizer(classPath, ",;");
    while (tokenizer.hasMoreTokens()) {
      String cp_entry = tokenizer.nextToken();

      try {

        // Get the resource from the context,
        URL classpath_url = new URL(cp_entry);
        // Assert the URL protocol,
        if (!classpath_url.getProtocol().equals("mwpfs")) {
          throw new PException("URL is not MWPFS protocol: " + cp_entry);
        }

        // NOTE: This would be a good opportunity to load a .jar file into the
        //  local file system for some efficiency? The Java system URL class
        //  loader appears to do this by default.

        // Add the URL,
        addURL(classpath_url);

      }
      catch (IOException e) {
        throw new PException("Invalid URL location: " + cp_entry);
      }

    }
  }

}
