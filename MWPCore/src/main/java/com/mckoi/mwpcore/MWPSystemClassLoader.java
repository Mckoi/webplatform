/**
 * com.mckoi.mwpcore.MWPSystemClassLoader  Oct 3, 2012
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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * The SYSTEM class loader includes the system support libraries (application
 * server, etc). The parent of this class loader is the bootstrap class
 * loader.
 * <p>
 * Note that this class loader allows for .jars added in the class path to
 * override classes defined in the system class loader. This could allow
 * changing important parts of the API if not careful.
 *
 * @author Tobias Downer
 */

public final class MWPSystemClassLoader extends URLClassLoader {

  private final ClassLoader parent_cl;

  MWPSystemClassLoader(ClassLoader parent) {
    super(new URL[0], parent);
    this.parent_cl = parent;
  }
  
  /**
   * Adds all the .jar files at the given path in the local file system to the
   * class path.
   */
  void addAllToClassPath(File path) throws IOException {
    URL[] all_jars = MWPUserClassLoader.getJarsInPath(path);
    for (URL jar : all_jars) {
      super.addURL(jar);
    }
  }


  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
                                                throws ClassNotFoundException {

    // This class and MWPCoreMain must fall through to the parent,
    if (name.equals(MWPSystemClassLoader.class.getName()) ||
        name.equals(MWPCoreMain.class.getName())) {

      Class c = parent_cl.loadClass(name);
      if (resolve) {
        resolveClass(c);
      }
      return c;

    }

    try {

//      System.out.println("load: " + name);

      // Try and load from this class loader before going to the parent,

      // Has it been loaded already?
      Class c = findLoadedClass(name);

      if (c == null) {
        // Try and find it here,
        try {
          c = findClass(name);
        }
        catch (ClassNotFoundException e) {
          // If not found here, try the parent,
          c = parent_cl.loadClass(name);
        }
      }

      // If we need to resolve it,
      if (resolve) {
        resolveClass(c);
      }

      return c;

    }
    catch (ClassNotFoundException e) {
//      System.out.println(" *** (1) FAILED: " + name);
      throw e;
    }
    catch (NoClassDefFoundError e) {
//      System.out.println(" *** (2) FAILED: " + name);
      throw e;
    }

  }

}
