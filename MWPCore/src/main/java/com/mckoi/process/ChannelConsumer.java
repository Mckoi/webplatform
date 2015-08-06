/**
 * com.mckoi.process.ChannelConsumer  Apr 23, 2012
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

import java.util.Collection;

/**
 * This object manages the clients access to the sequence of messages posted
 * to a process's broadcast channel. It is designed such that the iterator
 * at which the client is currently reading messages may be easily
 * serialized to be stored in the database, in the client-side session data
 * (eg. in cookies), or in another process.
 *
 * @author Tobias Downer
 */

public interface ChannelConsumer {

  /**
   * Returns the ProcessChannel that this is a consumer of.
   */
  ProcessChannel getProcessChannel();

  /**
   * Returns the session state string for the current state of this consumer.
   * The session state string is a simple encoded format that includes the
   * process id, the channel number of the current sequence value. This
   * string is intended to be stored in client-side data as a way to manage
   * client state as it progresses through the data in a channel. One way
   * to think of this is as a simple serialization function.
   * <p>
   * This typically will be called after messages have been consumed, and
   * the returned value is used to reinitialize a new ChannelConsumer in
   * subsequent functions. Note that if a session state is fetched and then
   * after, messages on the channel are consumed, initializing a
   * new ChannelConsumer with that state string will include messages that
   * were already consumed.
   */
  ChannelSessionState getSessionState();

  /**
   * Consumes a message from this channel. If there are no messages available
   * on the channel then returns null. This is a non-blocking operation that
   * returns immediately.
   */
  ProcessMessage consume() throws ProcessUnavailableException;

  /**
   * Consumes messages from this channel if there are any available. If there
   * are no messages available, immediately returns an empty list. This is a
   * non-blocking operation.
   */
  Collection<ProcessMessage> consumeFromChannel(int consume_limit)
                                            throws ProcessUnavailableException;

  /**
   * Consumes messages from this channel. Returns immediately with the set of
   * message available to be consumed if there are messages pending. A maximum
   * of 'consume_limit' messages will be consumed on the channel. If there are
   * no messages pending then returns null and instead uses the
   * 'ProcessResultNotifier' closure to notify the client when there are
   * messages available.
   * <p>
   * Note that the notifier may be automatically disposed after 20 minutes if
   * no messages are received on the channel.
   */
  Collection<ProcessMessage> consumeFromChannel(
                        int consume_limit, ProcessResultNotifier notifier)
                                            throws ProcessUnavailableException;

}
