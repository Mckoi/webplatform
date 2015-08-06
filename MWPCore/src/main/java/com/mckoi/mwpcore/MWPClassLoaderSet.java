/**
 * com.mckoi.mwpcore.MWPClassLoaderSet  Oct 3, 2012
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

import java.net.URL;

/**
 * Exposes information about the class loaders in the Mckoi Web Platform.
 *
 * @author Tobias Downer
 */

public class MWPClassLoaderSet {

  private final MWPSystemClassLoader system_cl;
  private final URL[] user_jars;

  MWPClassLoaderSet(MWPSystemClassLoader system_cl, URL[] user_jars) {
    this.system_cl = system_cl;
    this.user_jars = user_jars;
  }

  /**
   * The MWP SYSTEM class loader.
   */
  public MWPSystemClassLoader getSystemClassLoader() {
    return system_cl;
  }

  /**
   * Creates a MWP USER class loader.
   */
  public MWPUserClassLoader createUserClassLoader(
                     ClassNameValidator system_classes, boolean debug_report) {

    // Return the class loader,
    return new MWPUserClassLoader(user_jars, system_cl,
                                  system_classes, debug_report);

  }

}
