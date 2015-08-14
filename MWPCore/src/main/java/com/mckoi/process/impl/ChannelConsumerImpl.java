/**
 * com.mckoi.process.impl.ChannelConsumerImpl  Apr 23, 2012
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
import com.mckoi.process.*;
import com.mckoi.process.ProcessResultNotifier.CleanupHandler;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of ChannelConsumer for the ProcessClientService
 * implementation.
 *
 * @author Tobias Downer
 */

class ChannelConsumerImpl implements ChannelConsumer {

  /**
   * The backed ProcessClientService.
   */
  private final ProcessClientService client_service;

  private final String account_name;
  private final ContextBuilder contextifier;
  private final ProcessChannel process_channel;
  private long sequence_value;

  /**
   * Constructor.
   */
  ChannelConsumerImpl(ProcessClientService client_service,
                      String account_name, ContextBuilder contextifier,
                      ProcessChannel process_channel, long sequence_value) {

    this.client_service = client_service;
    this.account_name = account_name;
    this.contextifier = contextifier;
    this.process_channel = process_channel;
    this.sequence_value = sequence_value;

  }

  ChannelConsumerImpl(ProcessClientService process_client,
                      String account_name, ContextBuilder contextifier,
                      ProcessChannel process_channel) {

    this(process_client, account_name, contextifier, process_channel, -1);

  }

  // -----

  @Override
  public ProcessChannel getProcessChannel() {
    return process_channel;
  }

  @Override
  public ChannelSessionState getSessionState() {

    // Encode the session state
    return ChannelSessionState.encodeSessionState(
                                              process_channel, sequence_value);

  }

  @Override
  public ProcessMessage consume() throws ProcessUnavailableException {

    // Consume a single message,
    PMessage msg = client_service.getMessageFromBroadcast(
                                account_name, process_channel, sequence_value);

    // Convert it into a ProcessMessage,
    if (msg == null) {
      return null;
    }
    else {
      // Update to the new sequence value,
      long new_sequence_value = msg.asByteBuffer().getLong(20);
      sequence_value = new_sequence_value;
      // Return as a ProcessMessage,
      return msg.asProcessMessage(36);
    }

  }

  @Override
  public Collection<ProcessMessage> consumeFromChannel(int consume_limit)
                                          throws ProcessUnavailableException {
    Collection<ProcessMessage> list =
              consumeFromChannel(consume_limit, (ProcessResultNotifier) null);
    // If nothing waiting,
    if (list == null) {
      list = Collections.EMPTY_LIST;
    }
    return list;
  }



  @Override
  public Collection<ProcessMessage> consumeFromChannel(
              int consume_limit, final ProcessResultNotifier notifier)
                                          throws ProcessUnavailableException {

    List<PMessage> msg_set;

    // Handle the case when notifier is null
    if (notifier == null) {
      msg_set = client_service.getMessagesFromBroadcast(
                  account_name, contextifier,
                  process_channel, sequence_value, consume_limit, notifier);
      if (msg_set == null) {
        return null;
      }
    }
    else {

      // Lock the notifier during initialization,
      notifier.lock();
      try {
        // Consume messages from the given sequence value. If there's nothing to
        // consume then returns null and the given notifier is called when there
        // are messages available or the timeout is reached.
        msg_set = client_service.getMessagesFromBroadcast(
                    account_name, contextifier,
                    process_channel, sequence_value, consume_limit, notifier);

        // If there was nothing consumed then return null,
        // This indicates the notifier is now listening, so we should initialize
        // it,
        if (msg_set == null) {
          // Initialize the notifier and return,
          notifier.init(new CleanupHandler() {
            @Override
            public void performCleanup() {
              try {
                // On timeout we make sure to remove the notifier from the list,
                removeNotifier(notifier);
              }
              catch (ProcessUnavailableException e) {
                // PENDING: Should we ignore this?
              }
            }
          });
          return null;
        }
      }
      finally {
        notifier.unlock();
      }

    }

    // If we get here, it means there are messages waiting and the 'notifier'
    // object will be ignored.

    // Convert the list into a ProcessMessage set and return that,

    // Return empty Collection
    if (msg_set.isEmpty()) {
      return Collections.EMPTY_LIST;
    }

    // Update the sequence value with the last value consumed,
    int sz = msg_set.size();
    PMessage last_msg = msg_set.get(sz - 1);
    long new_sequence_value = last_msg.asByteBuffer().getLong(20);
    sequence_value = new_sequence_value;

    return new CCList(msg_set);

  }

  private void removeNotifier(ProcessResultNotifier notifier)
                                          throws ProcessUnavailableException {
    client_service.removeNotifier(account_name, process_channel, notifier);
  }

  // -----

  /**
   * Parse the session string into a process id, channel number and sequence
   * value, and return an instantiated ChannelConsumerImpl object.
   */
  static ChannelConsumerImpl fromSessionState(
                 ProcessClientService process_client,
                 String account_name, ContextBuilder contextifier,
                 ChannelSessionState session_state) {

    return new ChannelConsumerImpl(process_client, account_name, contextifier,
                                   session_state.getProcessChannel(),
                                   session_state.getSequenceValue());

  }


  // -----

  private static class CCList extends AbstractList<ProcessMessage> {

    private final List<PMessage> backed;

    private CCList(List<PMessage> pmessage_list) {
      this.backed = pmessage_list;
    }

    @Override
    public ProcessMessage get(int index) {
      return backed.get(index).asProcessMessage(36);
    }

    @Override
    public boolean isEmpty() {
      return backed.isEmpty();
    }

    @Override
    public int size() {
      return backed.size();
    }

  }

}
