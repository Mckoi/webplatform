/**
 * com.mckoi.webplatform.impl.JettyMckoiWebAppClassLoader  May 30, 2010
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

import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.MWPUserClassLoader;
import com.mckoi.webplatform.MWPRuntimeException;
import com.mckoi.webplatform.MckoiDDBWebPermission;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.StringTokenizer;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * A class loader for web applications. Note that this pretty much overrides
 * the behavior in every method in Jetty's WebAppClassLoader so that the
 * Mckoi platform is protected from possibly incompatible or insecure (from
 * our perspective) changes made in future Jetty versions.
 *
 * @author Tobias Downer
 */

public final class JettyMckoiWebAppClassLoader extends WebAppClassLoader {

  /**
   * Security permissions for operations on the class path.
   */
  private static final MckoiDDBWebPermission CLASSLOADER_ADD_CLASSPATH =
                   new MckoiDDBWebPermission("webAppClassLoader.addClasspath");
  private static final MckoiDDBWebPermission CLASSLOADER_ADD_JARS =
                   new MckoiDDBWebPermission("webAppClassLoader.addJars");
  private static final MckoiDDBWebPermission CLASSLOADER_DESTROY =
                   new MckoiDDBWebPermission("webAppClassLoader.destroy");
  private static final MckoiDDBWebPermission CLASSLOADER_GET_CONTEXT =
                   new MckoiDDBWebPermission("webAppClassLoader.getContext");
  private static final MckoiDDBWebPermission CLASSLOADER_SET_NAME =
                   new MckoiDDBWebPermission("webAppClassLoader.setName");


  // -----

  /**
   * The parent class loader.
   */
  private final ClassLoader parent;

  /**
   * The system classes that are permitted.
   */
  private final ClassNameValidator allowed_system_classes;
  
  /**
   * The USER class loader.
   */
  private final MWPUserClassLoader user_classloader;

  /**
   * Constructs the class loader using the class loader of this class as the
   * parent.
   */
  JettyMckoiWebAppClassLoader(
                MWPUserClassLoader user_classloader,
                WebAppContext c,
                ClassNameValidator allowed_system_classes) throws IOException {
    super(user_classloader, c);

    this.user_classloader = user_classloader;
    this.parent = getParent();
    this.allowed_system_classes = allowed_system_classes;
  }


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
  @Override
  public void addClassPath(String classPath) throws IOException {

//    System.out.println("WebAppCL.addClassPath(" + classPath + ")");
//    new Error().printStackTrace(System.out);

    // Check with the security manager that this is permitted,
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(CLASSLOADER_ADD_CLASSPATH);
    }

    // Add the class paths,
    StringTokenizer tokenizer= new StringTokenizer(classPath, ",;");
    while (tokenizer.hasMoreTokens()) {
      String cp_entry = tokenizer.nextToken();

      // Get the resource from the context,
      URL classpath_url = new URL(cp_entry);
      // Assert the URL protocol,
      if (!classpath_url.getProtocol().equals("mwpfs")) {
        throw new MWPRuntimeException(
                                   "URL is not MWPFS protocol: {0}", cp_entry);
      }

      // NOTE: This would be a good opportunity to load a .jar file into the
      //  local file system for some efficiency? The Java system URL class
      //  loader appears to do this by default.

      // Add the URL,
      addURL(classpath_url);

    }
  }

  // ----- Overwritten to support loading resources out of jar files -----

  @Override
  protected Class<?> findClass(final String name)
                                               throws ClassNotFoundException {
    // PENDING: Search the sdbdf .jar resources for the name and load the
    //   class with an efficient db access on the .jar file.
//    System.out.println("%%% findClass(" + name + ")");

    // This calls back to the Java system library as of Jetty 7
    return super.findClass(name);
  }

  @Override
  public URL findResource(final String name) {
    // PENDING: Search the sdbdf .jar resources for the name and load the
    //   resource with an efficient db access on the .jar file.
//    System.out.println("%%% findResource(" + name + ")");

    // This calls back to the Java system library as of Jetty 7
    return super.findResource(name);
  }

  @Override
  public Enumeration<URL> findResources(final String name) throws IOException {
    // PENDING: Search the sdbdf .jar resources for the name and load the
    //   resources with an efficient db access on the .jar file.
//    System.out.println("%%% findResources(" + name + ")");

    // This calls back to the Java system library as of Jetty 7
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
    boolean tried_parent = false;

    // If there's a parent and it's a system class name, delegate to the
    // parent first,
    if (c == null && isSystemClassName(name)) {
      try {
        c = user_classloader.loadClass(name);
      }
      catch (ClassNotFoundException e) {
        ex = e;
      }
      tried_parent = true;
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

    // If we haven't tried the parent, then try it (this happens when it's not
    // a system class but not found in this loader),
    if (!tried_parent) {
      try {
        c = user_classloader.loadClass(name);
      }
      catch (ClassNotFoundException e) {
        ex = e;
      }
    }

    // Class not found,
    if (c == null) {
      throw ex;
    }

    if (resolve) {
      resolveClass(c);
    }

    return c;
  }

  // Overwritten because the default implementation of Jetty 6's 'getResource'
  // does not restrict access to resources in a gated environment.
  @Override
  public URL getResource(String name) {
//    System.out.println("%%% getResource(" + name + ")");
    URL url = null;

    boolean tried_parent = false;

    if (isSystemResourcePath(name)) {
      // Load from the parent if it's a valid system resource
      url = user_classloader.getResource(name);
      tried_parent = true;
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

    // Try the parent if we haven't tried it yet,
    if (!tried_parent) {
      url = user_classloader.getResource(name);
    }

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
    // Good as of Jetty 7,
    return super.getPermissions(cs);
  }

  // ------ State modifiers -----

  @Override
  public void addJars(Resource lib) {

    // Check with the security manager that this is permitted,
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(CLASSLOADER_ADD_JARS);
    }

    // Good as of Jetty 7
    super.addJars(lib);
  }

//  @Override
//  public void destroy() {
//
//    // Check with the security manager that this is permitted,
//    SecurityManager sm = System.getSecurityManager();
//    if (sm != null) {
//      sm.checkPermission(CLASSLOADER_DESTROY);
//    }
//
//    // Good as of Jetty 7
//    super.destroy();
//  }
//
//  @Override
//  public ContextHandler getContext() {
//
//    // Check with the security manager that this is permitted,
//    SecurityManager sm = System.getSecurityManager();
//    if (sm != null) {
//      sm.checkPermission(CLASSLOADER_GET_CONTEXT);
//    }
//
//    // Good as of Jetty 7
//    return super.getContext();
//  }

  @Override
  public void setName(String name) {

    // Check with the security manager that this is permitted,
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
      sm.checkPermission(CLASSLOADER_SET_NAME);
    }

    // Good as of Jetty 7
    super.setName(name);
  }

  // ----- Illegal WebAppClassLoader methods -----

//  @Override
//  public boolean isSystemPath(String name) {
//    throw new RuntimeException("Method should not be called.");
//  }
//
//  @Override
//  public boolean isServerPath(String name) {
//    // We would include system classes here we are ok with untrusted code
//    // overwriting, and they would return false.
//    throw new RuntimeException("Method should not be called.");
//  }

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

//  @Override
//  public InputStream getResourceAsStream(String name) {
////    System.out.print("%%% getResourceAsStream(" + name + ") = ");
//    InputStream ins = super.getResourceAsStream(name);
//    return ins;
//  }

//  // The toString method,
//  public String toString() {
//    return super.toString();
//  }

}
