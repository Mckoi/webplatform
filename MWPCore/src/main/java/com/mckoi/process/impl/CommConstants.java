/**
 * com.mckoi.process.impl.CommConstants  Nov 20, 2012
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

/**
 * Communication constants collected together in a single class.
 *
 * @author Tobias Downer
 */

public class CommConstants {

  /**
   * Transmission code for initialization of process.
   */
  public static byte FUNCTION_INIT_CC =         (byte) 1;

  /**
   * Transmission code for a function invoke call, reply expected.
   */
  public static byte CALL_RET_EXPECTED_CC =     (byte) 2;

  /**
   * Transmission code for a function invoke call, reply not expected.
   */
  public static byte CALL_RET_NOT_EXPECTED_CC = (byte) 3;

  /**
   * Transmission code for a broadcast request or broadcast keep-alive signal.
   */
  public static byte BROADCAST_REQUEST_CC =     (byte) 4;

  /**
   * Transmission code for a query of the process information.
   */
  public static byte PROCESS_QUERY =            (byte) 7;

  /**
   * A general Process service query on the data managed by a process service.
   */
  public static byte SERVICE_QUERY =            (byte) 8;

  /**
   * A 'sendSignal' call on a process has a communication code with this.
   */
  public static byte SEND_SIGNAL =              (byte) 9;

  /**
   * Transmission code for a reply to a function invoke.
   */
  public static byte CALL_REPLY_CC =            (byte) 14;

  /**
   * Transmission code for a broadcast message on a process channel.
   */
  public static byte BROADCAST_MESSAGE_CC =     (byte) 15;

  /**
   * Transmission code for a clean up of any resources associated with a
   * function invocation.
   */
  public static byte CALL_CLEANUP_CC =          (byte) 16;

  /**
   * Transmission code for acknowledgment of a broadcast request.
   */
  public static byte ACK_BROADCAST_REQUEST_CC = (byte) 20;

  /**
   * Transmission code for notification that a process has been terminated.
   */
  public static byte NOTIFY_TERMINATED_CC =     (byte) 21;

}
