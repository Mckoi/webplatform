/**
 * com.mckoi.mwpcore.DBPathSnapshot  Apr 22, 2012
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

package com.mckoi.appcore;

import com.mckoi.odb.ODBRootAddress;
import com.mckoi.odb.ODBSession;

/**
 * An object that represents a snapshot of a path in an ODB database. The
 * intention of this is to allow an application to hold on to an old version
 * of a database for versioning reasons (for example, to support a class
 * loader for an application that has since been updated but we are still
 * actively using the previous version).
 * <p>
 * Note that we don't expose any information from this object in a public
 * interface because we want to ensure transaction materialization only
 * happens in 'DBSessionCache'.
 *
 * @author Tobias Downer
 */

public final class DBPathSnapshot {

  private final ODBSession session;
  private final ODBRootAddress root_address;

  DBPathSnapshot(ODBSession session, ODBRootAddress root_address) {
    this.session = session;
    this.root_address = root_address;
  }

  ODBSession getSession() {
    return session;
  }

  ODBRootAddress getODBRootAddress() {
    return root_address;
  }

}
