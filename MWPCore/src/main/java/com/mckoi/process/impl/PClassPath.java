/**
 * com.mckoi.process.impl.PClassPath  Mar 28, 2012
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

import java.util.ArrayList;
import java.util.List;

/**
 * A process class path is represented as a set of locations in the database
 * where .java class files are found to support a process.
 *
 * @author Tobias Downer
 */

class PClassPath {

  /**
   * The list of location definitions.
   */
  private final List<String> locations;

  /**
   * Constructor.
   */
  PClassPath() {
    locations = new ArrayList();
  }
  
  /**
   * Adds a unique location definition to this class path. The location must
   * be a mwpfs URL location that references a .jar or directory with class
   * files.
   * <p>
   * Some examples; 'mwpfs:/toby/apps/cms/classes/',
   * 'mwpfs:/admin/syslib/MckoiDDB.jar'
   */
  
  
  

}
