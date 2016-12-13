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

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * A message backed by a byte array.
 *
 * @author Tobias Downer
 */

public class Message {

  private final ByteBuffer b;

  public Message(ByteBuffer buffer) {
    this.b = buffer;
  }

  public ByteBuffer getByteBuffer() {
    return b;
  }

  /**
   * Textual presentation of the message (will not be the complete message).
   */
  public String toString() {
    int MAX_MESSAGE_PRINT_BYTES = 16;
    StringBuilder sb = new StringBuilder();
    sb.append("<Message size = ").append(b.limit()).append(" [ ");
    int sz = Math.min(MAX_MESSAGE_PRINT_BYTES, b.limit());
    for (int i = 0; i < sz; ++i) {
      String hs = Integer.toHexString(b.get(i) & 0x0FF);
      if (hs.length() == 1) sb.append('0');
      sb.append(hs);
    }
    if (b.limit() > MAX_MESSAGE_PRINT_BYTES) {
      sb.append(" ...");
    }
    sb.append(" ]>");
    return sb.toString();
  }

}
