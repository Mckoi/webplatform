/**
 * com.mckoi.process.impl.NIOConnection  Mar 23, 2012
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

import com.mckoi.process.ProcessServiceAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encapsulates a connection to a process server from a client on the server
 * side.
 *
 * @author Tobias Downer
 */

public class NIOConnection {

  private static final Logger LOG = ProcessServerService.PROCESS_LOG;

  private final Selector selector;
  private final PEnvironment process_env;
  private final SocketChannel sc;

  private final AtomicInteger dispatch_sem = new AtomicInteger(0);

  private final ByteBuffer read_buffer = ByteBuffer.allocate(65536);
  private PMessage partial_msg;

  private final Object message_queue_lock = new Object();
  private PMessage message_queue_head = null;
  private PMessage message_queue_last = null;
  private int message_queue_count;

  private volatile Long state_long = null;
  private volatile ProcessServiceAddress st_machine_addr = null;

  private volatile boolean is_valid;

  private final Object BUFFER_WRITE_LOCK = new Object();
  private final Object WAIT_WRITE_LOCK = new Object();
  private boolean ready_to_write = true;
  private final ByteBuffer write_buffer;
  private final NIOWriteSelector write_selector;

  private SelectionKey selection_key;

//  volatile long DBG_last_ready_ts;

  /**
   * Constructor.
   */
  NIOConnection(Selector selector,
                PEnvironment process_env, SocketChannel sc,
                NIOWriteSelector write_selector) {
    this.selector = selector;
    this.process_env = process_env;
    this.sc = sc;
    this.write_selector = write_selector;

    this.write_buffer = ByteBuffer.allocate(65536);
    
    this.is_valid = true;
  }

  /**
   * Returns true only if this connection is valid (open and communicating).
   * This intention of this flag is to indicate to any resources connected with
   * this connection that it can be reclaimed.
   */
  boolean isValid() {
    return is_valid;
  }

  /**
   * Reports information about this connection.
   */
  String report() {
    StringBuilder b = new StringBuilder();
    int queue_count = 0;
    synchronized (message_queue_lock) {
      PMessage msg = message_queue_head;
      while (msg != null) {
        ++queue_count;
        msg = msg.next;
      }
    }

    // The report,
    b.append("Connection from ").append(sc.socket().getInetAddress());
    b.append(" queue size = ").append(queue_count);

    return b.toString();
  }

  /**
   * Returns the socket channel.
   */
  public SocketChannel getChannel() {
    return sc;
  }

  /**
   * Sets the selection key used by this connection.
   */
  void setSelectionKey(SelectionKey selection_key) {
    this.selection_key = selection_key;
  }

  /**
   * Close the connection.
   */
  public void close() {
    try {

      // Cancel the key if the attachment is this,
      selection_key.cancel();
//      Set<SelectionKey> keys = selector.keys();
//      for (SelectionKey key : keys) {
//        if (key.attachment() == this) {
//          key.cancel();
//        }
//      }
      // Deregister from the write selector,
      try {
        write_selector.deregister(this);
      }
      catch (IOException e) {
        LOG.log(Level.SEVERE, "IO exception when closing", e);
      }
      // Close the channel
      NIOServerThread.forceCloseChannel(sc);
      // Tell the environment we closed,
      process_env.connectionClosed(this);

    }
    finally {
      is_valid = false;
    }
  }

  /**
   * Callback from NIOWriteSelector that notifies this connection can now
   * accept writes.
   */
  void notifyWriteReady() {
    synchronized (WAIT_WRITE_LOCK) {
      ready_to_write = true;
      WAIT_WRITE_LOCK.notifyAll();
    }
  }

  /**
   * Immediately flushes the send message buffer.
   * <p>
   * This will block if the buffers are full and the other end of the
   * connection is not responding.
   */
  public void flushSendMessages() throws IOException {

    try {

      synchronized (BUFFER_WRITE_LOCK) {
        write_buffer.flip();

        while (true) {

          int write_try = 0;
          while (write_buffer.hasRemaining()) {
            int count = sc.write(write_buffer);
            // If we didn't write,
            if (count == 0) {
              ++write_try;
              // If we tried to write too many times, break from the loop,
              if (write_try > 2) {
                break;
              }
            }
          }

          // If we wrote everything then break the loop
          if (!write_buffer.hasRemaining()) {
            break;
          }

          // If we get here it means the buffers are full and the client isn't
          // receiving more data and we still have data to send.
          // This means we need to register for OP_WRITE events in a selector
          // that will callback to this connection and try to flush the send
          // buffer.
          // We then need to block this call until we can write,

          synchronized (WAIT_WRITE_LOCK) {
            ready_to_write = false;
            write_selector.register(this);
            try {
              while (!ready_to_write) {
                WAIT_WRITE_LOCK.wait();
              }
            }
            catch (InterruptedException e) {
              // Ignore,
            }
          }

        }
        write_buffer.clear();
      }

    }
    // If this method generates an IOException then we invalidate the
    // connection,
    catch (IOException e) {
      is_valid = false;
      throw e;
    }

  }

  /**
   * Sends the message to the client. Note that this may put the message into
   * an internal buffer. To ensure the message is sent immediately, use
   * 'flushSendMessages'
   * <p>
   * This will block if the buffers are full and the other end of the
   * connection is not responding.
   * <p>
   * Returns true if this call caused a buffer flush.
   */
  public boolean sendFirstMessage(PMessage msg) throws IOException {

    boolean performed_flush = false;

    // Put the message in the local buffer,
    synchronized (BUFFER_WRITE_LOCK) {
      int msg_sz = msg.sizeInBytes();
      if (write_buffer.remaining() < 4) {
        flushSendMessages();
        performed_flush = true;
      }
      write_buffer.putInt(msg_sz);
      ByteBuffer msg_bb = msg.asByteBuffer();
      int rs_lim = msg_bb.limit();
      while (true) {
        int buf_remaining = write_buffer.remaining();
        int msg_remaining = msg_bb.remaining();
        if (msg_remaining == 0) {
          break;
        }
        boolean flush_necessary = false;
        // If the remaining space in the buffer is less than the remaining
        // bytes in the message,
        if (buf_remaining < msg_remaining) {
          // Set the limit to the size of the message we are copying,
          msg_bb.limit(msg_bb.position() + buf_remaining);
          flush_necessary = true;
        }
        // Put the message part,
        write_buffer.put(msg_bb);
        if (flush_necessary) {
          flushSendMessages();
          performed_flush = true;
        }
        // Reset the limit,
        msg_bb.limit(rs_lim);
      }
    }

    return performed_flush;

//    // NOTE: Should we have a pool of byte buffers here so to ease the work
//    //   of the GC?
//    // Create a new byte buffer with the message + message size (as int)
//    int msg_sz = msg.sizeInBytes();
//    ByteBuffer bb = ByteBuffer.allocate(4 + msg_sz);
//    bb.putInt(msg_sz);
//    ByteBuffer msg_bb = msg.asByteBuffer();
//    bb.put(msg_bb);
//    bb.flip();
//    sc.write(bb);
    
  }

  /**
   * Sends all the messages linked in the PMessage chain to the client and
   * flushes the send queue.
   * <p>
   * This will block if the buffers are full and the other end of the
   * connection is not responding.
   */
  public void sendAllMessages(PMessage msg) throws IOException {
    // PENDING; Consolodate these messages into a buffer and flush the buffer
    // when it's full.
    while (msg != null) {
      sendFirstMessage(msg);
      msg = msg.next;
    }
    flushSendMessages();
  }

  /**
   * Returns the state long.
   */
  Long getStateLong() {
    return state_long;
  }

  /**
   * Sets the state long.
   */
  void setStateLong(Long val) {
    state_long = val;
  }

  /**
   * Returns the machine name state value.
   */
  ProcessServiceAddress getStateMachineName() {
    return st_machine_addr;
  }

  /**
   * Sets the machine name state value.
   */
  void setStateMachineName(ProcessServiceAddress machine_addr) {
    st_machine_addr = machine_addr;
  }

  /**
   * Dispatch handle message calls. This attempts to consolidate multiple
   * dispatches.
   */
  private void dispatchHandleMessages() {
    // This ensures there can only ever be 1 thread at a time handling
    // messages on this connection,
    
    // Return if already processing,
    if (dispatch_sem.getAndIncrement() > 0) {
      return;
    }

    // Dispatch event handler on the thread pool,
    ExecutorService thread_pool = process_env.getThreadPool();
    thread_pool.submit(new Runnable() {
      @Override
      public void run() {
        while (true) {
          // Reset dispatch_sem
          dispatch_sem.set(1);
          
          // Process the messages on the queue,
          try {
            process_env.handleMessages(NIOConnection.this);
          }
          catch (Throwable e) {
            // Report the exception,
            LOG.log(Level.SEVERE, "Error during message dispatch", e);
            // Reset 'dispatch_sem' and return
            dispatch_sem.set(0);
            return;
          }

          // If no new messages were dispatched while handling then return
          if (dispatch_sem.compareAndSet(1, 0)) {
            return;
          }
          // Otherwise loop and handle any other messages,
        }
      }
    });
    
  }

  /**
   * Returns true if the message queue is full.
   */
  private boolean isMessageQueueFull() {
    synchronized (message_queue_lock) {
      return false;
    }
  }

  /**
   * Puts a message in the queue. Returns true if the message was added to
   * the queue, or false if the queue is full.
   */
  boolean putInQueue(PMessage msg) {
//    // Check the message,
//    msg.debugCheck();
    synchronized (message_queue_lock) {
      if (message_queue_last == null) {
        message_queue_last = msg;
        message_queue_head = msg;
      }
      else {
        message_queue_last.next = msg;
        message_queue_last = msg;
      }
      ++message_queue_count;
      return true;
    }
  }

//  /**
//   * Consume a message from the start of the queue. Returns null if the queue
//   * is empty.
//   */
//  public PMessage consumeFromQueue() {
//    synchronized (message_queue_lock) {
//      if (message_queue_head == null) {
//        return null;
//      }
//      else {
//        PMessage head = message_queue_head;
//        message_queue_head = message_queue_head.next;
//        if (message_queue_head == null) {
//          message_queue_last = null;
//        }
//        --message_queue_count;
//        return head;
//      }
//    }
//  }

  /**
   * Consumes all the messages currently sitting in the queue and returns a
   * Collection object that can access them.
   */
  public Collection<PMessage> consumeAllFromQueue() {
    PMessage first;
    int count;
    synchronized (message_queue_lock) {
      // Store the linked list head and size,
      first = message_queue_head;
      count = message_queue_count;
      // Reset the message queue,
      message_queue_count = 0;
      message_queue_head = null;
      message_queue_last = null;
    }

    // Return the Collections object,
    if (first == null) {
      return Collections.emptySet();
    }
    return new MessageSet(first, count);
  }

  /**
   * Returns the current size of the queue.
   */
  public int getMessageQueueSize() {
    synchronized (message_queue_lock) {
      return message_queue_count;
    }
  }

  /**
   * Changes the buffer so that only the data from the tail_pos position to
   * buffer.position() remains in the buffer. The tail data is copied to the
   * start of the array.
   */
  private void tailBuffer(int tail_pos) {

    // tail the whole buffer,
    if (tail_pos == 0) {
      return;
    }
    // tail at end of buffer
    if (tail_pos == read_buffer.limit()) {
      read_buffer.clear();
      return;
    }

    // Remove from the head of the buffer,
    final byte[] buffer_arr = read_buffer.array();
    final int end_sz = read_buffer.position() - tail_pos;
    System.arraycopy(buffer_arr, tail_pos,
                     buffer_arr, 0,
                     end_sz);
    read_buffer.position(end_sz);
  }

  /**
   * Reads data from the channel and fills the buffer. Returns true on end
   * of stream, false otherwise.
   */
  boolean readDataFromChannel() {

    boolean was_queue_updated = false;

    try {

      // Read the buffer content,
      int read_count = sc.read(read_buffer);
//      System.out.println("READ = " + read_count);
      if (read_count == 0) {
        return false;
      }
      // If End of stream,
      if (read_count < 0) {
        // Invalidate the connection,
        is_valid = false;
        return true;
      }

      // If the message queue is full, we return
      if (isMessageQueueFull()) {
        return false;
      }

      int pos = read_buffer.position();
      int msg_pos = 0;
      
      // Are the consuming into a partial message?
      if (partial_msg != null) {
//        System.out.println("ADDING TO PARTIAL");
        // If the buffer is full or it contains enough to satisfy the message
        // request, we write to the partial,
        int remaining_to_write = partial_msg.remainingToWrite();
        if (read_buffer.remaining() == 0 || pos >= remaining_to_write) {
          // Write the buffer data
          int to_write = Math.min(remaining_to_write, read_buffer.limit());
          partial_msg.write(read_buffer, 0, to_write);

          msg_pos = to_write;
          // Add to the queue if the message is complete,
          if (partial_msg.remainingToWrite() == 0) {
            putInQueue(partial_msg);
            was_queue_updated = true;
            partial_msg = null;
          }

        }
        else {
          // Here it means the buffer still has room but what is available
          // can't satisfy the partial,
          return false;
        }
      }

      // Is there at least one complete command in the buffer?
      // Not even a message header is available yet,
      while (pos - msg_pos >= 4) {

        int msg_size = read_buffer.getInt(msg_pos);

//        System.out.println("msg_size = " + msg_size);
//        System.out.println("msg_pos = " + msg_pos);
//        System.out.println("pos = " + pos);
        
        if (msg_size <= 0) {
          throw new IOException("msg_size <= 0");
        }
        if (msg_size > 65536) {
          throw new IOException("msg_size > 65536");
        }
        
        if (msg_size <= pos - msg_pos - 4) {
          // Yes! We have at least one complete message in the buffer, so
          // put it in the queue,

          byte[] buf = new byte[msg_size];

          final int rpos = read_buffer.position();
          read_buffer.position(msg_pos + 4);
          read_buffer.get(buf, 0, msg_size);
          read_buffer.position(rpos);

          PMessage msg = new PMessage(buf);
//          System.out.println("DBG: Put message in queue: " + msg.getCallId());
          putInQueue(msg);
          was_queue_updated = true;

          msg_pos += (msg_size + 4);

        }
        else {
          // Not a complete message yet so break,
          break;
        }
      }

      // If the position moved,
      if (msg_pos > 0) {
        // Remove the consumed data,
        tailBuffer(msg_pos);
      }

      // If the buffer is full we notify a partial message,
      else if (read_buffer.remaining() == 0) {
        // Buffer full, so make a partial message,
        int msg_size = read_buffer.getInt(0);
        byte[] buf = new byte[msg_size];
        partial_msg = new PMessage(buf);
        partial_msg.write(read_buffer, 4, read_buffer.limit() - 4);
        read_buffer.clear();
      }

      // Notify new messages if queue changed,
      if (was_queue_updated) {
        dispatchHandleMessages();
      }

      return false;

    }
    catch (IOException e) {
      LOG.log(Level.SEVERE, "Error when reading stream", e);
      // Invalidate the connection,
      is_valid = false;
      return true;
    }
  }
  
  /**
   * Returns the runnable to execute when a connection is accepted. The
   * returned runnable will validate this connection against the process
   * environment object.
   */
  public Runnable doAccept() {
    return accept_runnable;
  }

  // -----

  /**
   * Close the connection if the IP isn't valid.
   */
  private final Runnable accept_runnable = new Runnable() {
    @Override
    public void run() {
      try {
        // Check the connection is valid
        if (!process_env.channelConnectionValid(sc)) {
          // If the channel isn't valid, close the connection
          close();
        }
        else {
          // Initialize the connection if connection allowed,
          process_env.initializeConnection(NIOConnection.this);
        }
      }
      catch (Throwable e) {
        // Report message and close the connection,
        LOG.log(Level.SEVERE, "Error when accepting socket channel", e);
        close();
      }
    }
  };

  /**
   * Message set provides an iterator for traversing a set of messages consumed
   * from the message queue.
   */
  private static class MessageSet extends AbstractSet<PMessage> {

    private final PMessage first;
    private final int count;

    MessageSet(PMessage first, int count) {
      this.first = first;
      this.count = count;
    }

    @Override
    public Iterator<PMessage> iterator() {
      // An iterator that traverses through the set,
      return new Iterator<PMessage>() {

        private PMessage pos = first;

        @Override
        public boolean hasNext() {
          return (pos != null);
        }

        @Override
        public PMessage next() {
          PMessage r = pos;
          pos = pos.next;
          return r;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
        
      };
    }

    @Override
    public int size() {
      return count;
    }

  }

}
