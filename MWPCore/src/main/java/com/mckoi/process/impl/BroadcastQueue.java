/**
 * com.mckoi.process.impl.BroadcastQueue  Apr 26, 2012
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

import com.mckoi.mwpcore.ContextBuilder;
import com.mckoi.process.ProcessResultNotifier;
import com.mckoi.process.ProcessResultNotifier.Status;
import com.mckoi.process.ProcessServiceAddress;
import com.mckoi.webplatform.util.MonotonicTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * A set of notifiers and listeners against a channel and a message queue on a
 * channel. Each notifier has a timestamp associated with it which allows
 * clearing all notifiers older than some amount.
 *
 * @author Tobias Downer
 */

class BroadcastQueue {

  /**
   * The message queue list for this broadcast channel.
   */
  private final QueueList queue_list;

  /**
   * The current maximum sequence value that was stored in the queue (that we
   * know of).
   */
  private volatile long current_max_sequence_num = 0;

  /**
   * The list of notifiers.
   */
  private ProcessResultNotifier[] notifiers;

  /**
   * The list of ContextBuilders.
   */
  private ContextBuilder[] contextifiers;

  /**
   * The list of timestamps when the respective notifier was added to the list.
   */
  private MonotonicTime[] timestamps;

  /**
   * The minimum sequence value we are listening for when the notifier is
   * triggered.
   */
  private long[] min_sequences;

  /**
   * The size (the number of notifiers in this queue).
   */
  private int count;

  /**
   * The number of listener locks we have on this broadcast queue.
   */
  private int listener_lock;

  /**
   * Used for notification scheduling.
   */
  private long scheduled_seq_num;

  /**
   * The currently receiving flag.
   */
  private volatile MonotonicTime connect_time = null;

  /**
   * Used as a timer for sending the broadcast request.
   */
  private volatile MonotonicTime request_expired_ts = null;

  /**
   * The last acknowledged machine address of the broadcasting process.
   */
  private volatile ProcessServiceAddress machine_addr;

  /**
   * Constructor.
   */
  BroadcastQueue(int size) {
    this.queue_list = new QueueList();
    this.count = 0;
    notifiers = new ProcessResultNotifier[size];
    contextifiers = new ContextBuilder[size];
    timestamps = new MonotonicTime[size];
    min_sequences = new long[size];
    listener_lock = 0;
  }

  BroadcastQueue() {
    this(4);
  }

  /**
   * Returns true if this queue is empty (the message queue is empty and there
   * are no notifiers or listeners).
   */
  boolean isEmpty() {
    synchronized (this) {
      return (queue_list.isEmpty() && count == 0 && listener_lock == 0);
    }
  }

  /**
   * Returns the last sequence value in the broadcast queue.
   */
  long getLastSequenceValue() {
    return current_max_sequence_num;
  }

  /**
   * Returns true if the request on this broadcast channel is expired (is was
   * over 4 minutes ago since 'setRequestNotExpired' was called).
   */
  boolean requestExpired() {
    if (request_expired_ts == null) {
      return true;
    }
    return MonotonicTime.millisSince(request_expired_ts) > (4 * 60 * 1000);
  }

  /**
   * Resets the request expiration timestamp.
   */
  void setRequestNotExpired() {
    request_expired_ts = MonotonicTime.now();
  }

  /**
   * Ensures the list arrays can contain the notifiers.
   */
  private void ensureCapacity() {
    int size = timestamps.length;
    if (count == size) {
      // Increase the size,

      // Double if it's small
      if (size < 128) {
        size = size * 2;
      }
      // Increase by not quite a double amount,
      else {
        size = (size * 10) / 7;
      }

      ProcessResultNotifier[] new_notifiers = new ProcessResultNotifier[size];
      ContextBuilder[] new_contextifiers = new ContextBuilder[size];
      MonotonicTime[] new_timestamps = new MonotonicTime[size];
      long[] new_min_sequences = new long[size];
      System.arraycopy(notifiers, 0,
                       new_notifiers, 0, notifiers.length);
      System.arraycopy(contextifiers, 0,
                       new_contextifiers, 0, contextifiers.length);
      System.arraycopy(timestamps, 0,
                       new_timestamps, 0, timestamps.length);
      System.arraycopy(min_sequences, 0,
                       new_min_sequences, 0, min_sequences.length);

      notifiers = new_notifiers;
      contextifiers = new_contextifiers;
      timestamps = new_timestamps;
      min_sequences = new_min_sequences;

    }
  }

  /**
   * Returns true if there are listener locks on this queue. This indicates
   * that the broadcast queue must be renewed.
   */
  boolean hasListenerLocks() {
    synchronized (this) {
      return (listener_lock > 0);
    }
  }

  /**
   * Adds a listener lock to this broadcast queue. A broadcast queue with a
   * listener lock will automatically renew itself each maintenance heart
   * beat, so it never expires. When there are no listener locks, the queue
   * is eligible to be removed (if it has no messages or recent notifiers).
   * <p>
   * This is used when a permanent object is listening on events on a
   * broadcast queue.
   */
  void addListenerLock() {
    synchronized (this) {
      ++listener_lock;
    }
  }
  
  /**
   * Removes a listener lock from this broadcast queue. See the description
   * of 'addListenerLock' for details of listener locks.
   */
  void removeListenerLock() {
    synchronized (this) {
      --listener_lock;
    }
  }

  /**
   * Adds a notifier to the list.
   */
  private void addNotifier(
              ProcessResultNotifier notifier, ContextBuilder contextifier,
              long min_sequence) {
    // Make sure there's enough room,
    ensureCapacity();

    notifiers[count] = notifier;
    contextifiers[count] = contextifier;
    timestamps[count] = MonotonicTime.now();
    min_sequences[count] = min_sequence;
    
    ++count;
  }

  /**
   * Remove the given notifier from the list.
   */
  void removeNotifier(ProcessResultNotifier notifier) {

    synchronized (this) {

      int limit = count;
      for (int i = 0; i < limit; ++i) {
        if (notifiers[i] == notifier) {
          System.arraycopy(notifiers, i + 1,
                           notifiers, i, limit - i - 1);
          System.arraycopy(contextifiers, i + 1,
                           contextifiers, i, limit - i - 1);
          System.arraycopy(timestamps, i + 1,
                           timestamps, i, limit - i - 1);
          System.arraycopy(min_sequences, i + 1,
                           min_sequences, i, limit - i - 1);
          --limit;
          --i;
        }
      }
      // Clean out tailing notifiers,
      for (int i = limit; i < count; ++i) {
        notifiers[i] = null;
        contextifiers[i] = null;
      }
      count = limit;

    }

  }

  /**
   * Remove all notifiers that are waiting on messages that are sequentially
   * after the given value.
   */
  private void removeNotifiersSequentiallyAfter(long sequence_num) {
    int limit = count;
    for (int i = 0; i < limit; ++i) {
      if (sequence_num > min_sequences[i]) {
        System.arraycopy(notifiers, i + 1,
                         notifiers, i, limit - i - 1);
        System.arraycopy(contextifiers, i + 1,
                         contextifiers, i, limit - i - 1);
        System.arraycopy(timestamps, i + 1,
                         timestamps, i, limit - i - 1);
        System.arraycopy(min_sequences, i + 1,
                         min_sequences, i, limit - i - 1);
        --limit;
        --i;
      }
    }
    // Clean out tailing notifiers,
    for (int i = limit; i < count; ++i) {
      notifiers[i] = null;
      contextifiers[i] = null;
    }
    count = limit;
  }

  /**
   * Clears all notifiers.
   */
  private void clearNotifiers() {
    int limit = count;
    for (int i = 0; i < limit; ++i) {
      notifiers[i] = null;
      contextifiers[i] = null;
    }
    count = 0;
  }

  /**
   * Clears all notifiers older than the given time.
   */
  void clearNotifiersOlderThan(MonotonicTime timestamp) {

    synchronized (this) {

      int limit = count;
      int end = 0;
      for (int i = 0; i < limit; ++i) {
        if (MonotonicTime.isInPastOf(timestamps[i], timestamp)) {
          end = i + 1;
        }
        else {
          break;
        }
      }
      // Clear,
      if (end > 0) {
        System.arraycopy(notifiers, end,
                         notifiers, 0, limit - end);
        System.arraycopy(contextifiers, end,
                         contextifiers, 0, limit - end);
        System.arraycopy(timestamps, end,
                         timestamps, 0, limit - end);
        System.arraycopy(min_sequences, end,
                         min_sequences, 0, limit - end);
        limit -= end;
        for (int i = limit; i < count; ++i) {
          notifiers[i] = null;
          contextifiers[i] = null;
        }
        count = limit;
      }

    }

  }

//  /**
//   * Returns true if there are currently notifiers waiting on messages with a
//   * sequence value greater than the given.
//   */
//  private boolean areNotifiersFor(long sequence_num) {
//    for (int i = 0; i < count; ++i) {
//      if (sequence_num > min_sequences[i]) {
//        return true;
//      }
//    }
//    return false;
//  }

  /**
   * Returns the connect time when this queue started receiving.
   */
  MonotonicTime getConnectTime() {
    return connect_time;
  }

  /**
   * Sets the 'connect_time' flag on this queue indicating the client is
   * currently receiving input from the process service for this queue with
   * a connection established with the given timestamp.
   */
  void setConnectTime(MonotonicTime connect_time) {
    this.connect_time = connect_time;
  }

  /**
   * Resets the connect time of the queue, forcing a re-acknowledge of this
   * broadcast channel next access.
   */
  void resetConnectTime() {
    this.connect_time = null;
  }

  /**
   * Returns the current remote machine address of the process that is
   * currently broadcasting on this queue. The value is set when a broadcast
   * acknowledge is received from the originating server. Note that this
   * may return null if the acknowledgement has yet been received.
   * 
   * @return 
   */
  ProcessServiceAddress getRemoteMachine() {
    return this.machine_addr;
  }

  /**
   * Sets the current machine address of the process this queue is being
   * broadcast from. This is called when a broadcast request is acknowledged.
   * 
   * @param machine_addr 
   */
  void setRemoteMachine(ProcessServiceAddress machine_addr) {
    this.machine_addr = machine_addr;
  }


  /**
   * Closes this broadcast queue making it not able to be used again and
   * eventually eligible for reclaimation.
   */
  void close() {
    synchronized (this) {
      listener_lock = 0;
    }
  }


  /**
   * Cleans the broadcast queue of all messages older than 2 minutes.
   */
  int cleanExpiredBroadcastMessages() {

    synchronized (this) {

      MonotonicTime two_mins_ago = MonotonicTime.now(-(2 * 60 * 1000));
      int clean_count = 0;

      QueueMessage msg = queue_list.getFirst();
      while (msg != null) {

        MonotonicTime bm_timestamp = msg.getQueueTimestamp();

        // Preserve all the messages sooner than 2 mins ago,
        if (MonotonicTime.isInFutureOf(bm_timestamp, two_mins_ago)) {
          // If count is 0 then there's nothing to clear,
          if (clean_count == 0) {
            return 0;
          }
          // Break the loop,
          break;
        }

        msg = msg.getNext();
        ++clean_count;

      }
      // If 'msg' is null, we hit the end of the queue so clear the entire
      // queue,
      if (msg == null) {
        queue_list.clear();
      }
      else {
        queue_list.setToTail(msg);
      }

      return clean_count;

    }

  }

  private void dispatchMessageNotifiers(
                        List<NotifierAndContext> to_trigger, Status status) {

    // Now do the trigger callback,
    if (to_trigger != null) {
      // We could probably avoid a lot of context switching here if we
      // can group identical contextifiers together and notify messages
      // in each group.
      for (NotifierAndContext t : to_trigger) {
        t.contextifier.enterContext();
        t.notifier.lock();
        try {
          t.notifier.notifyMessages(status);
        }
        finally {
          t.notifier.unlock();
          t.contextifier.exitContext();
        }
      }
    }

  }

  /**
   * Dispatches io failure messages to all notifiers on this queue.
   * 
   * @param thread_pool 
   */
  void failAllNotifiers(ExecutorService thread_pool) {

    thread_pool.submit(new Runnable() {
      @Override
      public void run() {

        // The list of notifiers to be triggered,
        List<NotifierAndContext> to_trigger = null;

        // NOTE: synchronized over the BroadcastQueue here. Notification can
        //   only every happen once.

        synchronized (BroadcastQueue.this) {
          // Notifiers to inform,
          if (count > 0) {
            // Assume it's the whole list
            to_trigger = new ArrayList<>(count);
            for (int i = 0; i < count; ++i) {
              to_trigger.add(new NotifierAndContext(
                                            notifiers[i], contextifiers[i]));
            }
            // Clear all notifiers,
            clearNotifiers();
          }
        }

        // Callback,
        dispatchMessageNotifiers(to_trigger, Status.IO_ERROR);
        
      }

    });
    
  }

  private static class NotifierAndContext {
    private final ProcessResultNotifier notifier;
    private final ContextBuilder contextifier;
    NotifierAndContext(
                ProcessResultNotifier notifier, ContextBuilder contextifier) {
      this.notifier = notifier;
      this.contextifier = contextifier;
    }
  }

  /**
   * Dispatches call back events against all notifiers and listeners that are
   * interested in an incoming message with the given sequence value. The call
   * back is dispatched to the given thread pool.
   */
  private void dispatchEvents(ExecutorService thread_pool,
                              long sequence_num) {

//    System.out.println("scheduleNotify(" + sequence_num + ")");

    boolean schedule_task = false;
    // Set up the min sequence number,
    if (scheduled_seq_num == 0) {
      schedule_task = true;
      scheduled_seq_num = sequence_num;
    }
    if (sequence_num < scheduled_seq_num) {
      scheduled_seq_num = sequence_num;
    }

    // If we are to schedule take,
    if (schedule_task) {
      thread_pool.submit(new Runnable() {
        @Override
        public void run() {

          long trigger_min_seq;
          // The list of notifiers to be triggered,
          List<NotifierAndContext> to_trigger = null;

          // NOTE: synchronized over the BroadcastQueue here. Notification can
          //   only every happen once.

          // Populate 'to_trigger' with the notifiers to trigger and remove
          // those notifiers from the list in this broadcast queue.
          synchronized (BroadcastQueue.this) {

            // Notifiers to inform,
            if (count > 0) {
              // Assume it's the whole list
              to_trigger = new ArrayList<>(count);
              trigger_min_seq = scheduled_seq_num;
              for (int i = 0; i < count; ++i) {
                if (trigger_min_seq > min_sequences[i]) {
                  to_trigger.add(new NotifierAndContext(
                                              notifiers[i], contextifiers[i]));
                }
              }
              // If we are triggering all (common case),
              if (to_trigger.size() == count) {
                clearNotifiers();
              }
              // Otherwise,
              else {
                removeNotifiersSequentiallyAfter(trigger_min_seq);
              }
            }
            scheduled_seq_num = 0;
          }

          // Callback,
          dispatchMessageNotifiers(to_trigger, Status.MESSAGES_WAITING);

        }

      });

    }

  }


  // -----

  /**
   * Creates a collection of PMessage from the given queue items.
   * <p>
   * Assumes external synchronization on the 'msg' list.
   */
  private static List<PMessage> broadcastList(QueueMessage msg, int limit) {

    List<PMessage> msgs = new ArrayList<>(Math.min(limit, 16));
    int msgcount = 0;
    while (msgcount < limit && msg != null) {
      msgs.add(msg.getMessage());
      msg = msg.getNext();
      ++msgcount;
    }
    return msgs;

  }

  /**
   * Puts all the messages in 'in_msgs' into this broadcast queue. If messages
   * were successfully added to the list, the given thread_pool is used to
   * dispatch calls to any notifiers or listeners that might be interested in
   * the messages added.
   */
  void putMessagesInQueue(QueueList in_msgs,
                          ExecutorService thread_pool) {

    synchronized (this) {

      long max_sequence_num = 0;

      QueueMessage msg = in_msgs.getFirst();

      while (msg != null) {
        final QueueMessage next_msg = msg.getNext();

        // The byte buffer of the message we are inserting,
        long sequence_num = msg.getMessage().getSequenceValue();

        // Make sure we update the max sequence number,
        if (sequence_num > max_sequence_num) {
          max_sequence_num = sequence_num;
        }

        // If the queue is empty,
        if (queue_list.isEmpty()) {
          // Just add to end,
          queue_list.add(msg);
        }
        else {
          // Otherwise insert at the correctly sorted position (most likely it
          // will be at the end so we search from the end),
          QueueMessage insert_msg = queue_list.getLast();
          boolean inserted_or_found = false;
          while (insert_msg != null) {
            long in_sequence_num = insert_msg.getMessage().getSequenceValue();
            
            // If we found a message with the same sequence we don't insert
            if (sequence_num == in_sequence_num) {
              inserted_or_found = true;
              break;
            }
            else if (sequence_num > in_sequence_num) {
              // Insert the message after 'insert_msg',
              queue_list.insertAfter(msg, insert_msg);
              inserted_or_found = true;
              break;
            }

            insert_msg = insert_msg.getPrevious();
          }
          // If not inserted or found,
          if (!inserted_or_found) {
            // Insert as the first item,
            queue_list.insertFirst(msg);
          }
        }

        msg = next_msg;
      }

      // Schedule notification on this queue,
      if (max_sequence_num > 0) {
        current_max_sequence_num = max_sequence_num;
        dispatchEvents(thread_pool, max_sequence_num);
      }

    }

  }

  /**
   * Returns the first message on the broadcast queue with a sequence value
   * greater than the one given.
   */
  PMessage getMessageFromBroadcast(long sequence_value) {

    synchronized (this) {

      QueueMessage msg = queue_list.getLast();
      while (msg != null) {
        long in_sequence_num = msg.getMessage().getSequenceValue();
        if (sequence_value >= in_sequence_num) {
          // Go to the next message,
          QueueMessage next_msg = msg.getNext();
          if (next_msg == null) {
            return null;
          }
          return next_msg.getMessage();
        }
        msg = msg.getPrevious();
      }

      // If we reached the end of the list, return the first in the queue or
      // null if no messages in the queue,
      QueueMessage fmsg = queue_list.getFirst();
      if (fmsg == null) {
        return null;
      }
      return fmsg.getMessage();

    }

  }

  /**
   * Returns a list of messages representing all the messages broadcast with
   * a sequence value greater than the one given. No more than 'consume_limit'
   * values will be returned. If there are no message pending, the given
   * 'notifier' will be called as soon as messages are received and this
   * function will return null.
   */
  List<PMessage> getMessagesFromBroadcast(
                long sequence_value, int consume_limit,
                ProcessResultNotifier notifier, ContextBuilder contextifier) {

    synchronized (this) {

      QueueMessage msg = queue_list.getLast();
      // If no last,
      if (msg == null) {
        if (notifier != null) {
          addNotifier(notifier, contextifier, sequence_value);
        }
        return null;
      }
      while (msg != null) {
        long in_sequence_num = msg.getMessage().getSequenceValue();
        if (sequence_value >= in_sequence_num) {
          // Go to the next message,
          QueueMessage next_msg = msg.getNext();
          if (next_msg == null) {
            if (notifier != null) {
              addNotifier(notifier, contextifier, sequence_value);
            }
            return null;
          }
          return broadcastList(next_msg, consume_limit);
        }
        msg = msg.getPrevious();
      }

      // If we reached the end of the list, return the first in the queue or
      // null if no messages in the queue,
      QueueMessage fmsg = queue_list.getFirst();
      return broadcastList(fmsg, consume_limit);

    }

  }


}
