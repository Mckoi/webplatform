/**
 * com.mckoi.process.impl.FunctionQueue  Nov 19, 2012
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

import com.mckoi.process.ProcessInputMessage;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An object that represents the input messages of the process instance.
 * Designed to be multi-thread safe.
 *
 * @author Tobias Downer
 */

class FunctionQueue {

  /**
   * The function queue lock.
   */
  private final ReentrantLock FUNCTION_QUEUE_LOCK = new ReentrantLock();

  /**
   * The input function message queue.
   */
  private final LinkedList<FunctionQueueItem> function_queue = new LinkedList();

  /**
   * Constructor.
   */
  FunctionQueue() {
    
  }

  /**
   * Locks against the function queue.
   */
  void lock() {
    FUNCTION_QUEUE_LOCK.lock();
  }

  /**
   * Unlocks against the function queue.
   */
  void unlock() {
    FUNCTION_QUEUE_LOCK.unlock();
  }

  /**
   * Pushes a new queue item to the end of the function queue.
   */
  void pushToFunctionQueue(FunctionQueueItem queue_item) {
    // Add it,
    lock();
    try {
      function_queue.add(queue_item);
      queue_item.setIsInQueue();
    }
    finally {
      unlock();
    }
  }

  /**
   * Pushes a new queue item to the start of the function queue.
   */
  void addFirstToFunctionQueue(FunctionQueueItem queue_item) {
    // Add it,
    lock();
    try {
      function_queue.addFirst(queue_item);
      queue_item.setIsInQueue();
    }
    finally {
      unlock();
    }
  }

  /**
   * Returns true if the function queue is empty or a function is currently
   * being executed.
   */
  boolean isFunctionQueueEmpty() {
    lock();
    try {
      return function_queue.isEmpty();
    }
    finally {
      unlock();
    }
  }

  /**
   * Removes the first non-signal item from the function queue, or returns
   * null if the queue is empty.
   */
  FunctionQueueItem removeFirst() {
    lock();
    try {
      // PENDING: remove old signals that are never consumed,
      // Find the first none signal item in the queue,
      Iterator<FunctionQueueItem> iterator = function_queue.iterator();
      while (iterator.hasNext()) {
        FunctionQueueItem item = iterator.next();
        // Consume it if it's not a signal,
        if (item.getType() != ProcessInputMessage.Type.SIGNAL_INVOKE) {
          iterator.remove();
          return item;
        }
      }
      return null;
    }
    finally {
      unlock();
    }
  }

  /**
   * Removes the first signal item from the function queue, or returns null
   * if the queue is empty.
   */
  FunctionQueueItem removeFirstSignal() {
    lock();
    try {
      if (function_queue.isEmpty()) {
        return null;
      }
      else {
        // If the first item matches the input type then consume it,
        FunctionQueueItem first = function_queue.getFirst();
        if (first.getType() == ProcessInputMessage.Type.SIGNAL_INVOKE) {
          return function_queue.removeFirst();
        }
        return null;
      }
    }
    finally {
      unlock();
    }
  }

  /**
   * Cleans the function queue of all elements.
   */
  void clean() {
    lock();
    try {
      function_queue.clear();
    }
    finally {
      unlock();
    }
  }

}
