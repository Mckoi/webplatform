/**
 * com.mckoi.webplatform.DDBResourceAccess  Mar 4, 2012
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

package com.mckoi.webplatform;

import com.mckoi.odb.ODBTransaction;
import java.util.Set;

/**
 * An interface that exposes access to the users data via the MckoiDDB
 * data model APIs.
 *
 * @author Tobias Downer
 */

public interface DDBResourceAccess {

  /**
   * Returns a list of paths that are accessible to this user.
   */
  Set<MckoiDDBPath> getAllPaths();

  /**
   * Returns an ODBTransaction object for access to the given path. If the
   * path is not accessible to the user then an exception is generated. Note
   * that the returned ODBTransaction may have other restrictions imposed on
   * it, such as not being allowed to commit.
   */
  ODBTransaction createODBTransaction(String path_name);

}
