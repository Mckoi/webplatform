/**
 * com.mckoi.process.ProcessInputMessage  Oct 6, 2012
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
 * An object that is consumed from a function queue that represents either a
 * message passed from the client (ProcessClient) to the process via the
 * 'invokeFunction' method, or a broadcast message being listened to, or a
 * reply to a function invoked on another process. If this represents a
 * function invocation, provides a way to reply to the message (either as a
 * successful completion or a failure)
 *
 * @author Tobias Downer
 */

public interface ProcessInputMessage {

  /**
   * The message type, either FUNCTION_INVOKE, BROADCAST, or RETURN.
   */
  Type getType();

  /**
   * Returns the ProcessMessage of the function invoked.
   */
  ProcessMessage getMessage();

  /**
   * If this is a BROADCAST message type, returns a session state string that
   * describes the process channel and the current sequence the channel is
   * at.
   */
  ChannelSessionState getBroadcastSessionState();

  /**
   * If this is a RETURN message type, returns an integer value that is the
   * 'call_id' of the function that originally invoked the function. If this
   * is NOT a RETURN message type, the value could be anything.
   */
  int getCallId();

  /**
   * If this is a 'RETURN_EXCEPTION' message type, returns an object that
   * describes this error.
   */
  ProcessFunctionError getError();


  /**
   * The types enumeration.
   */
  enum Type {

    FUNCTION_INVOKE,
    BROADCAST,
    RETURN,
    RETURN_EXCEPTION,
    TIMED_CALLBACK,

    /**
     * A signal message (ie, a call to 'sendSignal')
     */
    SIGNAL_INVOKE,

  };

}
