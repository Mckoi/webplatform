/**
 * com.mckoi.process.AppServiceProcessClient  Nov 11, 2012
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
 * A ProcessClient that is available to application services (eg. web
 * platforms) to provide methods for invoking functions on processes, and
 * for consuming messages broadcast on process broadcast channels. This
 * interface provides convenience functions for connecting the process
 * broadcast system to the application server infrastructure (via
 * asynchronous call-backs).
 *
 * @author Tobias Downer
 */

public interface AppServiceProcessClient extends ProcessClient {

  /**
   * Invokes a function on the given process and returns a ProcessResult
   * object that's used to retrieve the result of the function. This is a
   * non-blocking operation that returns immediately. The status of the result
   * can be accessed via the returned ProcessResult.
   * <p>
   * If 'reply_expected' is true, the client will expect a reply to this
   * function call from the given process_id. If false, if a reply is given it
   * will be ignored. This flag allows for optimization of inter-process
   * communication. Setting 'reply_expected' to false will cause this method
   * to return null.
   */
  ProcessResult invokeFunction(
             ProcessId process_id, ProcessMessage msg, boolean reply_expected);

  /**
   * Returns a consumer that is initialized from a previous session value (use
   * 'ChannelConsumer.getSessionState()' to serialize the session state of an
   * existing consumer).
   * <p>
   * Note that this can not allow the access to a process that does not belong
   * to the current account.
   */
  ChannelConsumer getChannelConsumer(ChannelSessionState session_state)
                                            throws ProcessUnavailableException;

  /**
   * Returns a consumer that is initialized to the given process and channel
   * number. The consumer iterator is set to 1 place before the oldest message
   * currently available. Typically this will be set to a message posted no
   * longer than 2 minutes ago.
   * <p>
   * Note that this can not allow the access to a process that does not belong
   * to the current account.
   */
  ChannelConsumer getChannelConsumer(ProcessChannel process_channel)
                                            throws ProcessUnavailableException;

  /**
   * Returns a consumer that is initializes to the given process, channel
   * number and sequence value (represented as a 'long'). If the sequence
   * value is not found then the iterator is set before the oldest message
   * currently available.
   * <p>
   * Note that this can not allow the access to a process that does not belong
   * to the current account.
   */
  ChannelConsumer getChannelConsumer(
                          ProcessChannel process_channel, long sequence_value)
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
   *   ProcessResult result = invokeServersQuery(
   *       ServersQuery.processSummary("toby", "console", null));
   *   ProcessMessage result_msg = (use a ProcessResult 'get' method)
   *   String json_str =
   *       (String) ByteArrayProcessMessage.decodeArgsList(result_msg)[0];
   * </pre>
   * The 'json_str' is a JavaScript object that maps the process server machine
   * id to the result of the query on the process server. The result of the
   * query is formatted specific to the query being run.
   */
  ProcessResult invokeServersQuery(ServersQuery query)
                                            throws ProcessUnavailableException;

}
