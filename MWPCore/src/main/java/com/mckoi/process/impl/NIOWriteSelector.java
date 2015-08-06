/**
 * com.mckoi.process.impl.NIOWriteSelector  Apr 18, 2012
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

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An NIO Selector that we register connections with that we need to be
 * notified when they are able to receive writes. Uses OP_WRITE.
 * <p>
 * This starts a thread that will sit on the selector.
 *
 * @author Tobias Downer
 */

public class NIOWriteSelector {

  private static final Logger LOG = ProcessServerService.PROCESS_LOG;

  /**
   * The selector.
   */
  private final Selector selector;

  /**
   * A reentrant lock used when registering / removing keys with the selector.
   */
  private final ReentrantLock selector_lock = new ReentrantLock();

  /**
   * Set to true when the selector is terminated.
   */
  private volatile boolean terminated = false;

  /**
   * The map.
   */
  private final Map<NIOConnection, SelectionKey> managed_keys = new HashMap();

  private Thread thread;

  /**
   * Constructor.
   */
  NIOWriteSelector(Selector selector) {
    this.selector = selector;
  }
  
  /**
   * Registers a connection with this selector. The connection is notified
   * when it's possible to write on the connection.
   * <p>
   * (Thread Safe)
   */
  void register(NIOConnection c) throws IOException {
    selector_lock.lock();
    try {
      selector.wakeup();
      // Register
      synchronized (managed_keys) {
        // If it's already managed?
        if (managed_keys.get(c) == null) {
          SelectionKey select_key =
                   c.getChannel().register(selector, SelectionKey.OP_WRITE, c);
          managed_keys.put(c, select_key);
//          System.out.println("REGISTERED");
        }
      }
    }
    finally {
      selector_lock.unlock();
    }
  }

  /**
   * Removes the key for the given connection from this selector if it is
   * registered. If it's not registered, does nothing.
   * <p>
   * (Thread Safe)
   */
  void deregister(NIOConnection c) throws IOException {
    selector_lock.lock();
    try {
      selector.wakeup();
      SelectionKey select_key;
      synchronized (managed_keys) {
        select_key = managed_keys.remove(c);
        if (select_key != null) {
          select_key.cancel();
        }
        selector.selectNow();
      }
    }
    finally {
      selector_lock.unlock();
    }
  }

  /**
   * Starts this selector on its own thread.
   */
  void start(String name) {
    if (terminated == true || thread != null) {
      throw new RuntimeException("Already started");
    }
    thread = new Thread(selector_loop, "NIOWriteSelector " + name);
    thread.start();
  }
  
  /**
   * Stops this selector and invalidates this object.
   */
  void stop() {
    // Terminates the thread,
    terminated = true;
    selector.wakeup();
    thread = null;
  }

  /**
   * The thread loop.
   */
  private Runnable selector_loop = new Runnable() {
    @Override
    public void run() {
      try {
        while (!terminated) {
          // Block until we have something to read,
          selector_lock.lock();
          selector_lock.unlock();

//          long DBG_record_ts = System.currentTimeMillis();

          int key_count = selector.select();
          
          if (key_count > 0) {
            Set<SelectionKey> selected_keys = selector.selectedKeys();

            // The connections to notify,
            List<NIOConnection> connections =
                                         new ArrayList(selected_keys.size());

            // First cancel the keys,
            synchronized (managed_keys) {
              for (SelectionKey key : selected_keys) {
                // Get the attachment,
                NIOConnection c = (NIOConnection) key.attachment();
                // Cancel the key,
                key.cancel();
                managed_keys.remove(c);                
                // Add the connection to notify,
                connections.add(c);
              }

              // Clear the selected keys set,
              selected_keys.clear();

              // Flush the selector (this will remove all the cancelled keys
              // from the selector)
              selector.selectNow();
//              System.out.println("REMOVED");
            }

            // Notify the connections,
            for (NIOConnection c : connections) {
              c.notifyWriteReady();
//              System.out.println("NOTIFIED");
            }
            
//              // Cancel this key,
//              key.cancel();
//              // Cancel the key after notify and remove from the managed map,
//              // NOTE: The order here is important. We must remove the
//              //  connection from 'managed_keys' map before we notify the 
//              //  connection that writes are possible
//              synchronized (managed_keys) {
//                managed_keys.remove(c);                
//              }
//              // Notify the connection that writes are now possible
//              c.notifyWriteReady();
//              System.out.println("OP_WRITE");
//              System.out.println("Finished OP_WRITE for connection");
//            }

          }

        }
      }
      catch (Throwable e) {
        LOG.log(Level.SEVERE,
             "NIOWriteSelector thread stopped unexpectedly from exception", e);
        e.printStackTrace(System.err);
      }
    }
  };

}
