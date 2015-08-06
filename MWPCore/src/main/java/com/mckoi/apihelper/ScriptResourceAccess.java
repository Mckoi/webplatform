/**
 * com.mckoi.apihelper.ScriptResourceAccess  Nov 22, 2012
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

package com.mckoi.apihelper;

import java.io.*;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Secure access to the script resources in this project.
 *
 * @author Tobias Downer
 */

public class ScriptResourceAccess {

//  /**
//   * Creates a UTF-8 decoding Reader on the resource with the given name. For
//   * example, given 'mwp/filesystem' will create a Reader for the resource
//   * at 'mjs/bin/lib/mwp/filesystem.js'. If the resource is not found then
//   * returns null.
//   */
//  public static Reader getResourceScriptReader(final String lib_script) {
//
//    // Get the reader for the script,
//    Reader m_reader = AccessController.doPrivileged(
//                                          new PrivilegedAction<Reader>() {
//
//      @Override
//      public Reader run() {
//
//        String resource_name = "/mjs/bin/lib/" + lib_script + ".js";
//
//        // Get the resource,
//        InputStream ins =
//                ScriptResourceAccess.class.getResourceAsStream(resource_name);
//        if (ins != null) {
//          // If we have found it then materialize the content into its own
//          // Java String.
//          try {
//            return new BufferedReader(new InputStreamReader(ins, "UTF-8"));
//          }
//          catch (IOException ex) {
//            throw new RuntimeException(ex);
//          }
//        }
//        return null;
//      }
//
//    });
//
//    return m_reader;
//
//  }

  /**
   * Creates a UTF-8 decoding Reader on the MJS resource file with the given
   * name. For example, given 'bin/lib/mwp/filesystem.js' will create a Reader
   * for the resource at 'mjs/bin/lib/mwp/filesystem.js'. If the resource is
   * not found then returns null.
   * <p>
   * The given ClassLoader is used to search for the resource.
   */
  public static Reader getResourceScriptReader(
                final ClassLoader class_loader, final String resource_name) {

    // Get the reader for the script,
    Reader m_reader = AccessController.doPrivileged(
                                          new PrivilegedAction<Reader>() {

      @Override
      public Reader run() {

        String qual_resource_name = "mjs/" + resource_name;

        // Get the resource,
        InputStream ins = class_loader.getResourceAsStream(qual_resource_name);
        if (ins != null) {
          // If we have found it then materialize the content into its own
          // Java String.
          try {
            return new BufferedReader(new InputStreamReader(ins, "UTF-8"));
          }
          catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        }
        return null;
      }

    });

    return m_reader;

  }

  /**
   * Returns true if the MJS resource file with the given name exists in the
   * given ClassLoader. For example, given 'bin/lib/mwp/filesystem.js' will
   * return true if the resource at 'mjs/bin/lib/mwp/filesystem.js' exists.
   */
  public static boolean doesLibraryScriptExist(
                  final ClassLoader class_loader, final String resource_name) {

    Boolean result = AccessController.doPrivileged(
                                            new PrivilegedAction<Boolean>() {

      @Override
      public Boolean run() {

        String qual_resource_name = "mjs/" + resource_name;

        // Get the resource,
        URL url = class_loader.getResource(qual_resource_name);

        // If we have found it then return true,
        return (url != null);

      }

    });

    return result;

  }

}
