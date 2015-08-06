/**
 * com.mckoi.process.ProcessInstance  Mar 3, 2012
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
 * An object that is used to query and configure the process instance
 * environment and broadcast messages over channels associated with the
 * process instance.
 *
 * @author Tobias Downer
 */

public interface ProcessInstance {

  /**
   * The unique id string for this process. This id is used to locate the
   * process in the system. Note that regardless of the physical hardware
   * that is servicing the process instance the id will remain the same.
   */
  ProcessId getId();

  /**
   * Closes the process removing any resources associated with it from the
   * process server. After this method returns, it will not be possible for
   * the process id to be used again. Note that this will not trigger a
   * 'flush' or 'suspend'.
   */
  void close();

  /**
   * Schedules a timed callback on this process. After 'time_wait_ms'
   * milliseconds has passed the 'function' method of the ProcessOperation
   * will be called with the given message by the process service. The type
   * of the message will be 'Type.TIMED_CALLBACK' and the 'call_id' of the
   * input message will be the same as the value returned from this method.
   * <p>
   * Returns the 'call_id' of the callback message.
   */
  int scheduleCallback(long time_wait_ms, ProcessMessage msg);

  /**
   * Consumes the oldest message on the function queue and returns a
   * ProcessInputMessage object that provides access to the message. If the
   * function queue is empty, returns null.
   * <p>
   * The function queue contains the messages sent to this process instance.
   * A message may either be a function invoked on this instance, or a
   * broadcast message from a process being listened to, or the return message
   * of a function invoked on another process.
   */
  ProcessInputMessage consumeMessage();

  /**
   * Consumes a signal sent to this process and returns a String describing
   * the signal. If there are no signals, returns null.
   * <p>
   * The signal queue should be periodically monitored by the process code
   * and acted on as appropriate. For example, a 'kill' signal might be sent
   * to the process which tells the process to terminate.
   */
  String[] consumeSignal();

  /**
   * Sends a success reply message for the given ProcessInputMessage that was
   * consumed from the queue using the 'consumeMessage()' function. The
   * ProcessInputMessage type must be FUNCTION_INVOKE.
   * <p>
   * This method returns immediately. Only a single reply can be sent for
   * a ProcessInputMessage (either via 'sendReply' or 'sendFailure').
   */
  void sendReply(ProcessInputMessage msg, ProcessMessage reply_msg);

  /**
   * Sends a failure message for the given ProcessInputMessage that was
   * consumed from the queue using the 'consumeMessage()' function. The
   * ProcessInputMessage type must be FUNCTION_INVOKE.
   * <p>
   * This results in the client that initiated the function to throw an
   * exception. This method optionally logs the event to the context log
   * handler. Only a single reply can be sent for this ProcessInputMessage
   * (either via 'sendReply' or 'sendFailure').
   * <p>
   * If 'log' is true then the failure is logged to the context log handler.
   * If 'log' is false then no log happens. Common and expected errors should
   * not be logged.
   */
  void sendFailure(ProcessInputMessage msg, Throwable exception, boolean log);

  /**
   * Sends a failure message for the given ProcessInputMessage that was
   * consumed from the queue using the 'consumeMessage()' function. The
   * ProcessInputMessage type must be FUNCTION_INVOKE.
   * <p>
   * This method is the same as 'sendFailure(msg, exception, true).
   */
  void sendFailure(ProcessInputMessage msg, Throwable exception);

  /**
   * Returns the StateMap object associated with this instance that can be
   * used to store semi-persistent information. The content of this map is
   * periodically flushed to the database and can be used as a recovery
   * mechanism if the process has to be relocated to a new server or the
   * server hosting the process fails.
   */
  StateMap getStateMap();

  /**
   * Returns an InstanceProcessClient object used to communicate with other
   * processes, and listen to broadcast messages from processes.
   */
  InstanceProcessClient getInstanceProcessClient();

  // --- Process Channels ---

  /**
   * Broadcasts a message on the given channel number. This method will return
   * immediately and the system will decide when to send the message to any
   * clients that are listening to the channel. The system may decide to
   * send the message immediately or never (if all the client listeners are
   * timed out). However, all messages broadcast will be stored on the process
   * server or one of more clients for at least 2 minutes.
   * <p>
   * All messages posted to a channel will have a 2 minute expiration. This
   * means if a client doesn't pick up the message within 2 minutes of it
   * being sent to a channel then the message will be lost.
   * <p>
   * A process may send message on up to Integer.MAX_VALUE channels. It's
   * recommended the number of channels used by a process is kept small and
   * if many clients are interested in a specific event, they listen to a
   * single channel number and filter the messages themselves. By this design,
   * the system is able to minimize the amount of data sent over the network
   * (if many clients on a host are listening to a channel, the messages only
   * need to be sent over the network once for all the clients).
   */
  void broadcastMessage(int channel_number, ProcessMessage message);

}
