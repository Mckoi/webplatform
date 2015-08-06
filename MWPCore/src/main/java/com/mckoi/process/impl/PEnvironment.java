/**
 * com.mckoi.process.impl.PEnvironment  Mar 23, 2012
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

import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

/**
 * 
 *
 * @author Tobias Downer
 */

interface PEnvironment {

  /**
   * Returns the thread pool.
   */
  ExecutorService getThreadPool();
  
  /**
   * Returns true if the given channel may connect. If returns false, the
   * channel is closed immediately.
   * <p>
   * This method will be invoked from the thread pool returned by
   * 'getThreadPool()'. This means this method may perform complex lookup if
   * needed and it won't block incoming connections. This multi-threaded
   * aspect should be considered when implementing this method (make sure
   * access to shared resources is synchronized).
   */
  boolean channelConnectionValid(SocketChannel sc);

  /**
   * Performs connection initialization. This is called when a connection is
   * established to the process server. This should be used to start any
   * initialization necessary to support the connection.
   * <p>
   * This method will be invoked from the thread pool returned by
   * 'getThreadPool()'. This means this method may perform complex lookup if
   * needed and it won't block incoming connections. This multi-threaded
   * aspect should be considered when implementing this method (make sure
   * access to shared resources is synchronized).
   */
  void initializeConnection(NIOConnection connection);

  /**
   * Notifies the process environment that the connection was closed. This
   * should clean up any environment resources associated with the connection.
   */
  public void connectionClosed(NIOConnection connection);

  /**
   * Notifies that new messages are waiting in the queue on the given
   * connection. It is necessary for implementations of this to consume all
   * the messages waiting on the connection. If all messages are not
   * consumed then it's possible that messages may be missed and never be
   * consumed. Incoming messages are consumed by calling
   * 'connection.consumeAllFromQueue()'.
   * <p>
   * This method will be invoked from the thread pool returned by
   * 'getThreadPool()', and will never be called simultaneously by multiple
   * threads at the same time for the same connection.
   * <p>
   * It should be noted that this method may choose to block, and receiving of
   * messages from other connections will not be interfered by one connection
   * being blocked. However, it's a bad idea for this method to block for a
   * long time because throughput of messages on the connection will be
   * lowered. If a message is needed to be dispatched to user code it should
   * be dispatched on another thread.
   */
  void handleMessages(NIOConnection connection);

}
