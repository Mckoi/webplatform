/**
 * com.mckoi.process.ProcessResultNotifier  Mar 4, 2012
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

import java.util.concurrent.locks.ReentrantLock;

/**
 * A notification closure used to express a function to perform when a message
 * is available to be consumed on a connection.
 *
 * @author Tobias Downer
 */

public abstract class ProcessResultNotifier {

  /**
   * Reentrant lock that is used whenever 'notifyMessages' is called and when
   * the notifier is initialized. This ensures it's impossible to notify of
   * incoming messages before initialization.
   */
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Initializes this notifier against the given consumer. If the context times
   * out then the method in the given CleanupHandler must be called to clean
   * up the process service. The CleanupHandler is used to detatch this
   * notifier from the system because there's no interest in the message(s)
   * anymore.
   * 
   * @param cleanup_handler
   */
  public abstract void init(CleanupHandler cleanup_handler);

  /**
   * Notifies that messages are available to be consumed, or the status of the
   * channel has changed such that messages can no longer be consumed (an
   * IO Error or time out has occurred).
   * 
   * @param status
   */
  public abstract void notifyMessages(Status status);

  /**
   * Lock used before 'notifyMessages' is called, and during initialization.
   * This lock ensures it's impossible for a 'notifyMessages' event to occur
   * before the context of the notifier has been fully initialized.
   */
  public final void lock() {
    lock.lock();
  }
  public final void unlock() {
    lock.unlock();
  }


  /**
   * A CleanupHandler that doesn't do anything.
   */
  public static final CleanupHandler NOOP_CLEANUP_HANDLER =
                                                         new CleanupHandler() {
    @Override
    public void detach() {
      // No operation
    }
  };

  /**
   * The CleanupHandler has a single method that we use to detach the notifier
   * from the system. This is necessary when some user-code event happens that
   * makes it so we are no longer interested in being notified.
   */
  public static interface CleanupHandler {
    
    /**
     * Detaches the notifier from the system. After this returns, the notifier
     * will no longer be used by the system to notify messages for the events
     * it was attached to.
     */
    void detach();

  }

  /**
   * Status events when a message is consumed.
   */
  public static enum Status {
    MESSAGES_WAITING,
    TIMEOUT,
    IO_ERROR
  }

}
