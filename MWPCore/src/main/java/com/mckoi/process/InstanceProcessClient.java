/**
 * com.mckoi.process.InstanceProcessClient  Nov 12, 2012
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
 * A ProcessClient implementation that provides methods to invoke functions
 * on processes, and listen to broadcast messages from processes. This
 * interface is provided to process instances.
 *
 * @author Tobias Downer
 */

public interface InstanceProcessClient extends ProcessClient {

  /**
   * Invokes a function on the given process and when a result is returned
   * by the process, pushes the resulting ProcessMessage onto the function
   * queue. The result can be obtained by calling 'consumeMessage' on the
   * ProcessInstance.
   * <p>
   * Returns a 'call_id' value that refers to the future reply to this
   * message. If a message is consumed with the same 'call_id' then it was the
   * result of this function.
   * <p>
   * If 'reply_expected' is true, the client will expect a reply to this
   * function call from the given process_id. If false, if a reply is given it
   * will be ignored. This flag allows for optimization of inter-process
   * communication. Setting 'reply_expected' to false will cause this method to
   * return a call_id of -1.
   * <p>
   * This is a non-blocking operation that returns immediately.
   */
  int invokeFunction(
             ProcessId process_id, ProcessMessage msg, boolean reply_expected);

  /**
   * Sets up a listener for all new broadcast messages from the given
   * ProcessChannel and sends the messages to the function queue that can be
   * accessed by calling 'consumeMessage' on the ProcessInstance.
   * <p>
   * This is a non-blocking operation that returns immediately.
   */
  void addChannelListener(ProcessChannel process_channel)
                                            throws ProcessUnavailableException;

  /**
   * Sets up a listener for all new broadcast messages from the given
   * session state string. The session state string can be obtained from
   * the ProcessInputMessage object. This is useful for establishing a
   * listener from a serialization of a channel.
   * <p>
   * This is a non-blocking operation that returns immediately.
   */
  void addChannelListener(ChannelSessionState session_state)
                                            throws ProcessUnavailableException;

  /**
   * Removes any listeners set for the given broadcast channel using the
   * 'addChannelListener' method. After this returns, no new messages for this
   * broadcast channel will be available on the function queue.
   */
  void removeChannelListener(ProcessChannel process_channel)
                                            throws ProcessUnavailableException;

  // -----

  /**
   * Queries all the process servers and generates a map that provides a
   * summary of all the information discovered regarding the query. The
   * query is represented as a ProcessMessage and the result of the servers
   * query is represented by a JSON formatted string.
   * <p>
   * The following is a demonstration of using this command to perform a
   * "process summary" query on the process servers and retrieving the
   * result;
   * <pre>
   *   int result_call_id = invokeServersQuery(
   *       ServersQuery.processSummary("toby", "console", null));
   * 
   *  ....
   *    In the ProcessOperation 'function' method;
   *  ....
   * 
   *   ProcessMessage result_msg = (from the ProcessInputMessage)
   *   String json_str =
   *       (String) ByteArrayProcessMessage.decodeArgsList(result_msg)[0];
   * </pre>
   * The 'json_str' is a JavaScript object that maps the process server machine
   * id to the result of the query on the process server. The result of the
   * query is formatted specific to the query being run.
   */
  int invokeServersQuery(ServersQuery query);

}
