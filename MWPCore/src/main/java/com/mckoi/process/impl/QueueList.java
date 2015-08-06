/**
 * com.mckoi.process.impl.QueueList  Apr 23, 2012
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
 * 
 *
 * @author Tobias Downer
 */

class QueueList {

  private QueueMessage first = null;
  private QueueMessage last = null;

  QueueMessage getFirst() {
    return first;
  }
  
  QueueMessage getLast() {
    return last;
  }

  void add(QueueMessage msg) {
    // Adds a message to the end of the queue,
    if (first == null) {
      msg.setPrevious(null);
      msg.setNext(null);
      last = msg;
      first = msg;
    }
    else {
      msg.setPrevious(last);
      msg.setNext(null);
      last.setNext(msg);
      last = msg;
    }
  }

  void remove(QueueMessage msg) {
    if (msg.getPrevious() == null) {
      first = msg.getNext();
    }
    if (msg.getNext() == null) {
      last = msg.getPrevious();
    }
    if (msg.getPrevious() != null) {
      msg.getPrevious().setNext(msg.getNext());
    }
    if (msg.getNext() != null) {
      msg.getNext().setPrevious(msg.getPrevious());
    }
  }

  /**
   * Inserts the given message 'msg' after the 'point_msg' message in the list.
   */
  void insertAfter(QueueMessage msg, QueueMessage point_msg) {
    QueueMessage next_msg = point_msg.getNext();
    msg.setPrevious(point_msg);
    msg.setNext(next_msg);
    point_msg.setNext(msg);
    if (next_msg == null) {
      last = msg;
    }
    else {
      next_msg.setPrevious(msg);
    }
  }

  /**
   * Inserts the message at the start of the queue.
   */
  void insertFirst(QueueMessage msg) {
    msg.setPrevious(null);
    msg.setNext(first);
    if (first != null) {
      first.setPrevious(msg);
    }
    else {
      last = msg;
    }
    first = msg;
  }

  

  void clear() {
    first = null;
    last = null;
  }

  /**
   * Sets the queue list to the tail of the queue list starting at the given
   * 'msg'. After this returns, 'msg' will be the first message in the list.
   */
  void setToTail(QueueMessage msg) {
    first = msg;
    QueueMessage prev = msg.getPrevious();
    if (prev != null) {
      prev.setNext(null);
    }
    else {
      msg.setPrevious(null);
    }
  }

  boolean isEmpty() {
    return (first == null);
  }

  /**
   * Note, this iterates through every entry.
   */
  int size() {
    // Counts the entries,
    QueueMessage msg = first;
    int size = 0;
    while (msg != null) {
      msg = msg.getNext();
      ++size;
    }
    return size;
  }

}
