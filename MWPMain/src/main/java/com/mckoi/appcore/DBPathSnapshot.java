/*
 * Mckoi Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2016  Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
