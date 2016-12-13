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
 
package tests;

import com.mckoi.appcore.messages.Message;
import com.mckoi.appcore.messages.MessagePackager;
import com.mckoi.appcore.messages.MessageStreamReader;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * 
 *
 * @author Tobias Downer
 */

public class MsgPackTest {

  private static void writeTestMessage(
            DataOutputStream dout, int size, Random base_random, List<Integer> size_list)
                                                throws IOException {

    assert(size >= 4);

    size_list.add(size);

    dout.writeInt(size + 4);

    // Populate with seed + random data,
    int seed = Math.abs(base_random.nextInt());
    dout.writeInt(seed);
    size -= 4;
    Random r = new Random(seed);
    for (int i = 0; i < size; ++i) {
      dout.write((byte) r.nextInt());
    }

  }

  private static boolean validateTestMessage(Message msg, int size_check) {

    ByteBuffer b = msg.getByteBuffer();

    if (size_check != b.capacity()) {
      System.out.println("expected capacity mismatch");
      return false;
    }

    int seed = b.getInt();
    Random r = new Random(seed);
    while (b.hasRemaining()) {
      byte by = b.get();
      byte expected = (byte) r.nextInt();
      if (by != expected) {
        System.out.println("expected data mismatch");
        return false;
      }
    }

    return true;
  }



  public static void main(String[] args) {

    // For determinism,
    final Random base_random = new Random(20);

    // Create a test stream of messages,
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    DataOutputStream dout = new DataOutputStream(bout);

    List<Integer> size_list = new ArrayList<>();

    try {

      // Write some test messages,
      for (int i = 1; i < 200; ++i) {
        int v = base_random.nextInt(100);
        if (v < 5) {
          // 5% chance of very large message,
          writeTestMessage(dout, 5000 + base_random.nextInt(7000) + base_random.nextInt(7000), base_random, size_list);
        }
        else if (v < 5 + 16) {
          // 16% chance of large message,
          writeTestMessage(dout, 1000 + base_random.nextInt(3000), base_random, size_list);
        }
        else {
          // Otherwise small message,
          writeTestMessage(dout, 4 + base_random.nextInt(280), base_random, size_list);
        }
      }
      for (int i = 4; i < 64; ++i) {
        writeTestMessage(dout, i, base_random, size_list);
      }
      writeTestMessage(dout, 1990, base_random, size_list);



      // The messages as stream data,
      final byte[] stream_data = bout.toByteArray();
      int stream_data_length = stream_data.length;

      // Parse the messages,
      MessagePackager packager = new MessagePackager(8192);

      MessageStreamReader msg_reader = new MessageStreamReader() {
        int index = 0;
        @Override
        public int read(ByteBuffer buf) {
          int max_read = Math.min(stream_data.length - index, buf.remaining());
          if (max_read == 0) {
            return -1;
          }

          int to_read = max_read;
          if (max_read > 32) {
            int nm = Math.min(4096, max_read);
            to_read = nm;
//            to_read = base_random.nextInt(nm - 16) + 16;
          }

//          System.out.println("read: " + to_read + " of " + max_read);

          buf.put(stream_data, index, to_read);
          index += to_read;
          return to_read;
        }
      };

      while (true) {
        if (!packager.processInput(msg_reader)) {
          break;
        }
      }


      // Consume all the messages we read,
      Collection<Message> messages = packager.consumeAll();
      System.out.println("Messages consumed: " + messages.size());

      int index = 0;
      for (Message msg : messages) {
        if (!validateTestMessage(msg, size_list.get(index))) {
          System.out.println(MessageFormat.format("ERROR: Message {0} is not valid!", index));
        }
        System.out.println(msg.toString());
        ++index;
      }

      System.out.println("All messages validated!");
      System.out.println("Test data size: " + stream_data_length);

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }

  }


}
