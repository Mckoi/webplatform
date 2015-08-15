/**
 * com.mckoi.mwpcore.MWPCoreMain  Oct 3, 2012
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
import java.lang.reflect.Method;
import java.util.Map;

/**
 * This is the main invocation point of the Mckoi Web Platform. This performs
 * various system/VM operations such as setting up the ClassLoader hierarchy.
 *
 * @author Tobias Downer
 */

public class MWPCoreMain {


  public static void main(String[] args) {

    // Set Unix style line separator for this JVM,
    System.setProperty("line.separator", "\n");

    // Get the system environment vars,
    Map<String, String> env = System.getenv();

    // The 'java_home' and 'install_path' properties,
    File java_home = new File(env.get("mwp.config.javahome"));
    File install_path = new File(env.get("mwp.config.install"));
    
    // Create a ClassLoader for all our libs in the install_path
    
    File lib_base = new File(install_path, "lib");
    
    // The class loader hierarchy looks like this...
    
    // bootstrap (lib/base/MWPCore.jar)
    //   appserver (lib/base/*.jar, lib/jetty/*.jar)
    //     user libs (lib/user/)
    //       [user code]
    
    // Add the base and application server jar libaries to the system class
    // loader,
    MWPSystemClassLoader system_cl =
                  new MWPSystemClassLoader(MWPCoreMain.class.getClassLoader());
    try {
      // NOTE: The order of this is important. When querying resources in the
      //   system class loader the returned list is ordered by the insert order
      //   here. We want service loaders to prioritize entries in 'base'.
      system_cl.addAllToClassPath(new File(lib_base, "base"));
      system_cl.addAllToClassPath(new File(lib_base, "jetty"));
    }
    catch (IOException e) {
      System.out.println("#Error: " + e.getMessage());
      return;
    }

    // Execute,
    try {

      Class<?> mwp_core_process_class =
            Class.forName("com.mckoi.mwpcore.MWPCoreProcess", true, system_cl);

      Method m = mwp_core_process_class.getDeclaredMethod("main",
                                            new Class<?>[] { String[].class });
      m.invoke(null, new Object[] { args });

      System.exit(0);

    }
    catch (Exception e) {
      e.printStackTrace(System.err);
    }

  }

}
