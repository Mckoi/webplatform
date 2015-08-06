/**
 * com.mckoi.process.impl.FunctionQueueItem  Oct 8, 2012
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

import com.mckoi.process.ChannelSessionState;
import com.mckoi.process.ProcessFunctionError;
import com.mckoi.process.ProcessInputMessage;
import com.mckoi.process.ProcessMessage;

/**
 * 
 *
 * @author Tobias Downer
 */

class FunctionQueueItem {

  private final int call_id;
  private final ProcessInputMessage.Type type;
  private final ProcessMessage message;
  private final ChannelSessionState broadcast_session_state;
  private final ProcessFunctionError error;
  private final NIOConnection connection;
  private final boolean reply_expected;
  private boolean in_queue = false;

  public FunctionQueueItem(int call_id, ProcessInputMessage.Type type,
                           ProcessMessage message,
                           ChannelSessionState broadcast_session_state,
                           ProcessFunctionError error,
                           NIOConnection connection, boolean reply_expected) {

    // ProcessMessage can't be null
    if (message == null) throw new NullPointerException();

    this.call_id = call_id;
    this.type = type;
    this.message = message;
    this.broadcast_session_state = broadcast_session_state;
    this.error = error;
    this.connection = connection;
    this.reply_expected = reply_expected;
  }

  int getCallId() {
    return call_id;
  }

  ProcessInputMessage.Type getType() {
    return type;
  }

  ProcessMessage getMessage() {
    return message;
  }

  NIOConnection getConnection() {
    return connection;
  }

  boolean getReplyExpected() {
    return reply_expected;
  }

  boolean isInQueue() {
    return in_queue;
  }

  void setIsInQueue() {
    in_queue = true;
  }

  ChannelSessionState getBroadcastSessionState() {
    return broadcast_session_state;
  }

  ProcessFunctionError getError() {
    return error;
  }

}
