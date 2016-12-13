/*
 * Mckoi Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2016  Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.mckoi.appcore.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A MessagePackager takes as input the serial sequence of byte arrays from
 * a stream source and puts any completed messages read from the input
 * sequence into a message queue. Messages can then be consumed from the
 * message queue.
 *
 * Each message on the input sequence is delimited by a 4 byte header that
 * only contains the length of the next message.
 *
 * MessagePackager will try to package small messages into a single buffer
 * rather than allocating a byte buffer for each message generated.
 *
 * @author Tobias Downer
 */

public class MessagePackager {

  private static final int DEFAULT_BUFFER_SIZE_MULTIPLIER = 8192;

  private final int buffer_size_multiplier;

  private ByteBuffer buffer;
  private int cur_message_start = 0;
  private int cur_message_length = -1;

  private boolean last_process_input_produced_messages = false;

  protected final Lock QUEUE_LOCK = new ReentrantLock();
  protected final List<Message> message_queue = new LinkedList<>();

  /**
   * Constructs a packager that is only able to create buffers that are
   * multipliers of the given size. Minimum size multiplier is 8 and maximum
   * is 64k.
   */
  public MessagePackager(int buffer_size_multiplier) {
    assert(buffer_size_multiplier > 8 && buffer_size_multiplier < 64 * 1024);
    this.buffer_size_multiplier = buffer_size_multiplier;
    buffer = ByteBuffer.allocate(buffer_size_multiplier);
  }

  /**
   * Constructs a packager with a default buffer size multiplier of 8 KB.
   */
  public MessagePackager() {
    this(DEFAULT_BUFFER_SIZE_MULTIPLIER);
  }

  /**
   * Consumes all messages and returns the set.
   */
  public Collection<Message> consumeAll() {
    QUEUE_LOCK.lock();
    try {
      List<Message> returned_messages = new ArrayList<>(message_queue);
      message_queue.clear();
      return returned_messages;
    }
    finally {
      QUEUE_LOCK.unlock();
    }
  }

  /**
   * Removes and returns the oldest message in the queue that matches the filter.
   * Returns null if no messages in the queue match the filter.
   */
  public Message consumeOldestMessage(MessageFilter filter) {
    QUEUE_LOCK.lock();
    try {
      Iterator<Message> i = message_queue.iterator();
      while (i.hasNext()) {
        Message msg = i.next();
        if (filter.matches(msg)) {
          i.remove();
          return msg;
        }
      }
    }
    finally {
      QUEUE_LOCK.unlock();
    }
    return null;
  }

  /**
   * Removes and returns all messages in the queue that match against the
   * given filter call. The messages are returned in order from oldest to
   * newest message.
   */
  public Collection<Message> consumeMessage(MessageFilter filter) {
    List<Message> returned_messages = new ArrayList<>(4);
    QUEUE_LOCK.lock();
    try {
      Iterator<Message> i = message_queue.iterator();
      while (i.hasNext()) {
        Message msg = i.next();
        if (filter.matches(msg)) {
          returned_messages.add(msg);
          i.remove();
        }
      }
    }
    finally {
      QUEUE_LOCK.unlock();
    }
    return returned_messages;
  }

  /**
   * Adds a message to the queue. This should always be performed under a
   * QUEUE_LOCK.lock().
   */
  protected void addMessageToQueue(Message msg) {
    message_queue.add(msg);
  }

  /**
   * Returns true if the last call to 'processInput' caused messages to
   * be added to the message queue.
   */
  public boolean didProduceMessages() {
    return last_process_input_produced_messages;
  }

  /**
   * Reads the next sequence of bytes from the input stream. Returns false
   * if the end of the underlying stream was reached, true otherwise.
   */
  public boolean processInput(MessageStreamReader messageStream) throws IOException {

    last_process_input_produced_messages = false;

    // Do we know for sure that the buffer will overflow?
    boolean resize_buffer;
    if (cur_message_length > 0) {
      // When we know the length of the next message, resize the buffer if the known
      // length exceeds the buffer limit.
      resize_buffer = cur_message_start + cur_message_length > buffer.limit();
    }
    else {
      // When we don't know the length of the next message, resize if there's less
      // than 8 bytes remaining to fill in the buffer.
      resize_buffer = buffer.remaining() < 8;
    }

    // When resizing the buffer,
    if (resize_buffer) {

      ByteBuffer old_buffer = buffer;

      // Decide how big the new buffer should be,

      int min_new_size;
      // If we know how large this message is,
      if (cur_message_length > 0) {
        // The minimum size of the new buffer should be the current message
        // size.
        min_new_size = cur_message_length + 1;
      }
      // If we don't know how large the next message is,
      else {
        // Make an estimate on a buffer size to allocate for the next message,
        min_new_size = (old_buffer.position() - cur_message_start) + (buffer_size_multiplier / 4);
      }
      // Make sure the new size allocated falls within a multiplier size
      int pg_size = min_new_size / buffer_size_multiplier;
      int new_buffer_size = (pg_size + 1) * buffer_size_multiplier;

      // Allocate the new buffer,
      buffer = ByteBuffer.allocate(new_buffer_size);
      int cur_position = old_buffer.position();
      old_buffer.position(cur_message_start);
      old_buffer.limit(cur_position);
      // Copy the remaining data we have already read into the new buffer,
      buffer.put(old_buffer);
      // The message start will be the first position of the new buffer,
      cur_message_start = 0;
    }

    // Perform the read operation,
//    int max_read = buffer.remaining();
    int act_read = messageStream.read(buffer);
    if (act_read == -1) {
      // End of stream reached,
      return false;
    }
    else if (act_read < 0) {
      throw new IllegalStateException("inputStream.read returned a negative read count");
    }

    // Lock on the queue,
    QUEUE_LOCK.lock();
    try {

      // If there's at least one message header in the buffer,
      while (buffer.position() >= 4) {

        int cur_message_read = buffer.position() - cur_message_start;

        // If the current message length is unknown then query it,
        if (cur_message_length == -1 && cur_message_read >= 4) {
          // Ok, start the message,
          cur_message_length = buffer.getInt(cur_message_start);
        }

        // Did we complete a message?
        if (cur_message_length > 0 && cur_message_read >= cur_message_length) {
          // Ok, we have a completed message,

          // Push the message on to the queue,

          // Mark the current position,
          int cur_position = buffer.position();
          // Remember the limit,
          int cur_limit = buffer.limit();

          // Set the position and limit to encapsulate the message,
          int cur_message_end = cur_message_start + cur_message_length;
          buffer.position(cur_message_start + 4).limit(cur_message_end);
          // Slice out the message data,
          ByteBuffer msg_content = buffer.slice();

          // Reset the position and limit,
          buffer.limit(cur_limit).position(cur_position);

          // Add the message to the message queue,
          Message msg = new Message(msg_content);
          last_process_input_produced_messages = true;
          addMessageToQueue(msg);

          // Reset for the next message,
          cur_message_start = cur_message_end;
          cur_message_length = -1;

        } else {
          // There isn't a complete message available so break the loop,
          break;
        }
      }

    }
    finally {
      QUEUE_LOCK.unlock();
    }

    return true;

  }

}
