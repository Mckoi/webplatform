/**
 * com.mckoi.webplatform.impl.RepositoryInfo  Oct 19, 2012
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

/**
 * Information about a file repository (such as the path it's on, and the name
 * if the file system root).
 *
 * @author Tobias Downer
 */

class RepositoryInfo {

  /**
   * The repository id.
   */
  private final String repository_id;
  
  /**
   * The path the repository is on (eg. 'ufsadmin').
   */
  private final String path_name;
  
  /**
   * The named root item of this repository (eg. 'accountfs').
   */
  private final String named_root;

  /**
   * Constructor.
   */
  RepositoryInfo(String repository_id, String path_name, String named_root) {
    this.repository_id = repository_id;
    this.path_name = path_name;
    this.named_root = named_root;
  }

  /**
   * Returns the repository id.
   */
  String getRepositoryId() {
    return repository_id;
  }

  /**
   * Returns the path name.
   */
  String getPathName() {
    return path_name;
  }

  /**
   * Returns the named root of the file system in the ObjectDatabase.
   */
  String getNamedRoot() {
    return named_root;
  }

}
