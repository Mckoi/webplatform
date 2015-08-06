/**
 * com.mckoi.process.ProcessClient  Mar 3, 2012
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

package com.mckoi.process;

/**
 * An interface that enables a client to connect to a process. Connections
 * are managed via connection id values that are exposed through
 * implementations of ProcessConnection. This interface also allows processes
 * to be invoked.
 *
 * @author Tobias Downer
 */

public interface ProcessClient {

  /**
   * Creates a process instance from this user's file repository and returns a
   * process id that is used to communicate with the process. When this
   * returns the system will have allocated the resources necessary to support
   * the process.
   * <p>
   * After a process is started the process id value may safely be put in the
   * user's session data (ie. in a cookie). The process id may also be stored
   * in the database or in another process state. While it's fine to make the
   * process id public, the security implications of doing so need to be
   * considered. For example, if the process is a chat room then it may be
   * necessary to perform additional authentication by the process function.
   * <p>
   * A process may not be invoked for the following reasons; 1) The account
   * is not permitted to invoke the given process. 2) The process class
   * does not exist. 3) The account has reached a limit in the number of
   * processes it is allowed to invoke.
   */
  ProcessId createProcess(String webapp_name, String process_class)
                                            throws ProcessUnavailableException;

  /**
   * Returns information about the process instance represented by the given
   * process id. Returns null if the process instance is not valid or the
   * process does not belong to the account requesting it.
   */
  ProcessInfo getProcessInfo(ProcessId process_id)
                                            throws ProcessUnavailableException;

  /**
   * Sends a signal string to the process with the given id. A signal is a
   * one way message sent to a process. This method returns immediately and
   * there is no feedback on whether a signal was successfully sent or not. If
   * a signal fails to be sent (for example, because the process server where
   * the process is located is not operating) no exception will be generated
   * by the system.
   * <p>
   * The purpose of this mechanism is to send process control codes to a
   * process. For example, a 'kill' signal might cause a process to terminate.
   * Keep in mind that it is up to the process to choose to respect a signal.
   * A process may not read the signal queue, or may choose to ignore a
   * signal.
   * <p>
   * Note that 'invokeFunction' can be used to send one-way messages to a
   * process, and is recommended for sending messages that are part of normal
   * operation of the process interaction. Signals are intended for
   * implementing process admin-interface features.
   */
  void sendSignal(ProcessId process_id, String[] signal);

}
