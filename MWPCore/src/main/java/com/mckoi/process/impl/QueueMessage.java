/**
 * com.mckoi.process.impl.QueueMessage  Apr 23, 2012
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

import com.mckoi.process.ProcessId;
import com.mckoi.process.ProcessServiceAddress;
import com.mckoi.webplatform.util.MonotonicTime;

/**
 * 
 *
 * @author Tobias Downer
 */

class QueueMessage {

  // The canonical name of the destination server if output, or source
  // server if input.
  private final ProcessServiceAddress machine;

  // The message being sent/received.
  private final PMessage message;

  // The timestamp this queue entry was created.
  private final MonotonicTime timestamp;

  // The next and previous messages in the queue,
  private QueueMessage next;
  private QueueMessage previous;

  QueueMessage(ProcessServiceAddress machine, PMessage msg) {
    this.machine = machine;
    this.message = msg;
    this.timestamp = MonotonicTime.now();
  }

  QueueMessage getNext() {
    return next;
  }
  
  QueueMessage getPrevious() {
    return previous;
  }

  void setNext(QueueMessage msg) {
    next = msg;
  }

  void setPrevious(QueueMessage msg) {
    previous = msg;
  }

  PMessage getMessage() {
    return message;
  }
  
  ProcessServiceAddress getMachine() {
    return machine;
  }

  MonotonicTime getQueueTimestamp() {
    return timestamp;
  }

  /**
   * Returns true if the message matches the given process id and call id.
   */
  boolean matches(ProcessId pid, int call_id) {
    return message.matches(pid, call_id);
  }

  /**
   * Returns true if the message matches the given call id.
   */
  boolean matchesCallId(int call_id) {
    return (message.getCallId() == call_id);
  }

}
