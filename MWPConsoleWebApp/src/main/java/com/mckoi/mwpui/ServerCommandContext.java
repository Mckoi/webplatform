/**
 * com.mckoi.mwpui.ServerCommandContext  Nov 26, 2012
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

package com.mckoi.mwpui;

import com.mckoi.process.ProcessInstance;
import com.mckoi.webplatform.PlatformContext;

/**
 * The context of a server command.
 *
 * @author Tobias Downer
 */

public interface ServerCommandContext {

  /**
   * Returns the account name of this context (convenience for
   * 'getPlatformContext().getAccountName()')
   */
  String getAccountName();

  /**
   * The PlatformContext.
   */
  PlatformContext getPlatformContext();

  /**
   * The ProcessInstance in which this context is operating.
   */
  ProcessInstance getProcessInstance();

  /**
   * Returns true if this context has been killed (this means that either a
   * kill signal has been seen on the input message queue, or the context was
   * killed for some other reason). When a process is killed it should
   * terminate the current command. This can be done by throwing a
   * KillSignalException.
   */
  boolean isKilled();

  /**
   * Resets the kill signal, effectively consuming the first kill signal on
   * the input queue. This should only be called in SessionProcessOperation
   * when the top application has been terminated.
   */
  void resetKillSignal();

}
