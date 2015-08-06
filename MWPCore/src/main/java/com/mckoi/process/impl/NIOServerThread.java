/**
 * com.mckoi.process.impl.NIOServerThread  Mar 23, 2012
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 *
 * @author Tobias Downer
 */

class NIOServerThread extends Thread {

  private static final Logger LOG = ProcessServerService.PROCESS_LOG;

  private final InetAddress local_bind_addr;
  private final int local_port;
  private final PEnvironment env;

  private final ReentrantLock selector_lock = new ReentrantLock();

  private Selector selector = null;
  private ServerSocketChannel server_channel = null;
  private volatile boolean is_closed = false;

  private Selector write_selector;
  private NIOWriteSelector nio_write_selector;

  /**
   * Constructor.
   * 
   * The 'local_bind_addr' is the IP address of a local interface that we
   * are to bind the server to. If it's an IPv6 local-link address then it
   * must also have a scope id.
   */
  NIOServerThread(InetAddress local_bind_addr, int local_port,
                  PEnvironment env) {
    this.local_bind_addr = local_bind_addr;
    this.local_port = local_port;
    this.env = env;
  }

  /**
   * Reports all the connections on this thread.
   */
  String report() {
    StringBuilder b = new StringBuilder();
    b.append("NIOServerThread report\n");
    selector_lock.lock();
    try {
      selector.wakeup();
      Set<SelectionKey> keys = selector.keys();
      for (SelectionKey key : keys) {
        NIOConnection conn = (NIOConnection) key.attachment();
        if (conn != null) {
          String s = conn.report();
          b.append(s).append("\n");
        }
      }
    }
    finally {
      selector_lock.unlock();
    }

    return b.toString();
  }

  void finish() {
    try {
      server_channel.close();
      selector.close();
      nio_write_selector.stop();
      write_selector.close();
      is_closed = true;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the PEnvironment object.
   */
  PEnvironment getEnv() {
    return env;
  }
  
  /**
   * Returns the environment thread pool.
   */
  ExecutorService getThreadPool() {
    return env.getThreadPool();
  }

  /**
   * Force close a socket channel, and ignore any IOException exceptions.
   */
  static void forceCloseChannel(SocketChannel sc) {
    // Close the socket and socket channel,
    try {
      sc.socket().close();
    }
    catch (IOException e) { /* ignore */ }
    try {
      sc.close();
    }
    catch (IOException e) { /* ignore */ }
  }

  @Override
  public void run() {

    try {

      // Selector for managing OP_WRITE
      write_selector = Selector.open();
      nio_write_selector = new NIOWriteSelector(write_selector);
      nio_write_selector.start("Server");

      // The general selector for all other IO
      selector = Selector.open();
      server_channel = ServerSocketChannel.open();
      server_channel.socket().bind(
                      new InetSocketAddress(local_bind_addr, local_port));
      server_channel.configureBlocking(false);
      server_channel.register(selector, SelectionKey.OP_ACCEPT);
//        ServerSocket ss = server_channel.socket();
//        ss.setSoTimeout(0);
//        int cur_receive_buf_size = ss.getReceiveBufferSize();
//        if (cur_receive_buf_size < 256 * 1024) {
//          ss.setReceiveBufferSize(256 * 1024);
//        }

      while (true) {
        // Wait until something happens on the selector,
        selector_lock.lock();
        selector_lock.unlock();
        int key_count = selector.select();
        if (key_count > 0) {

          Set<SelectionKey> keys = selector.selectedKeys();
          for (SelectionKey key : keys) {
            try {
              int r_ops = key.readyOps();

              // If it's an accept op from the server socket,
              if ((r_ops & SelectionKey.OP_ACCEPT) != 0) {
                // The incoming socket channel,
                SocketChannel sc = server_channel.accept();

                // Register with this selector,
                sc.configureBlocking(false);
                try {

                  // Make a connection object
                  NIOConnection conn = new NIOConnection(
                                         selector, env, sc, nio_write_selector);
                  // Make the key and attach to the connection,
                  SelectionKey client_key =
                              sc.register(selector, SelectionKey.OP_READ, conn);
                  conn.setSelectionKey(client_key);

                  try {
                    Socket socket = sc.socket();
                    socket.setTcpNoDelay(true);

                    // Do init,
                    getThreadPool().submit(conn.doAccept());

                  }
                  catch (IOException e) {
                    LOG.log(Level.SEVERE,
                            "IOException on socket accept", e);
                    conn.close();
                  }

                }
                catch (ClosedChannelException e) {
                  LOG.log(Level.SEVERE,
                          "Exception registering with selector", e);
                  forceCloseChannel(sc);
                }

              }

              // If it's a read op,
              if ((r_ops & SelectionKey.OP_READ) != 0) {

                // Get the connection,
                NIOConnection conn = (NIOConnection) key.attachment();
                // Read data from channel
                boolean close = conn.readDataFromChannel();

  //              // If the queue was updated, notify listeners of the message
  //              // queue,
  //              if (conn.wasQueueUpdated()) {
  //                getThreadPool().submit(env.handleMessages(conn));
  //              }
                // If the stream closed,
                if (close) {
                  conn.close();
                }

              }

            }
            catch (IOException e) {
              LOG.log(Level.SEVERE, "IOException on socket operation", e);
            }
            catch (CancelledKeyException e) {
              // This seems to be rarely thrown.
              LOG.log(Level.WARNING, "Cancelled Key", e);
            }

          }
          // Clear the keys,
          keys.clear();
        }
      }
    }
    catch (ClosedSelectorException e) {
      if (!is_closed) {
        LOG.log(Level.SEVERE,
                "Process thread terminated unexpectedly", e);
      }
    }
    catch (IOException e) {
      LOG.log(Level.SEVERE,
              "IOException caused process thread to terminate", e);
    }
    finally {
      // Make sure to close the server channel and selector when thread
      // finalizes.
      if (!is_closed) {
        try {
          server_channel.close();
        }
        catch (IOException e) { /* ignore */ }
        try {
          selector.close();
        }
        catch (IOException e) { /* ignore */ }
      }
    }
  }

}
