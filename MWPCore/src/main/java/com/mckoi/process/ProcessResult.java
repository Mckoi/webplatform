/**
 * com.mckoi.process.ProcessResult  May 5, 2012
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
 * Encapsulates the functionality for retrieving the result of a process
 * function.
 *
 * @author Tobias Downer
 */

public interface ProcessResult {

  /**
   * Non blocking function that returns the result message, or null if the
   * result isn't available yet. If this method returns null then the given
   * notifier is used as a callback for when the result is available. If the
   * result is not received within 4 minutes then the notifier is timed out.
   * <p>
   * The callback happens from a dispatcher thread. Code that is executed in
   * the callback should be kept to a minimum.
   */
  ProcessInputMessage getResult(ProcessResultNotifier notifier);

  /**
   * Non blocking function that returns the result message, or null if the
   * result is not available yet. Unlike 'getResult(ProcessResultNotifier)'
   * there is no notification mechanism with this.
   */
  ProcessInputMessage getResult();

  /**
   * Blocks until the result is received, then returns the message. If the
   * process does not respond then this will block until 'timeout'
   * milliseconds has passed, then throw a timeout exception. If the thread is
   * interrupted then throws 'InterruptedException'.
   */
  ProcessInputMessage blockUntilResult(long timeout)
                          throws ResultTimeoutException, InterruptedException;

  /**
   * Returns the unique call_id for this result.
   */
  int getCallId();

}
