/**
 * com.mckoi.process.impl.ProcessInfoImpl  Apr 21, 2012
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

import com.mckoi.process.ProcessServiceAddress;

/**
 * Information about a process (the machine it's running on, the owner, etc).
 *
 * @author Tobias Downer
 */

class ProcessInfoImpl {

  /**
   * The canonical machine address.
   */
  private final ProcessServiceAddress machine;

  /**
   * The AccountApplication of the process.
   */
  private final AccountApplication account_app;
  
  /**
   * The name of the process.
   */
  private final String process_name;

  /**
   * Constructor.
   */
  ProcessInfoImpl(ProcessServiceAddress machine,
              AccountApplication account_app, String process_name) {
    this.machine = machine;
    this.account_app = account_app;
    this.process_name = process_name;
  }

  /**
   * Returns the canonical machine name where this process is run.
   */
  ProcessServiceAddress getMachine() {
    return machine;
  }

  /**
   * Returns the account application of the process.
   */
  AccountApplication getAccountApplication() {
    return account_app;
  }

  /**
   * Returns the process name.
   */
  String getProcessName() {
    return process_name;
  }

}
