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
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Implementation of MessageStreamReader that reads messages from a
 * blocking InputStream.
 *
 * @author Tobias Downer
 */

public class BlockingIOMessageStream implements MessageStreamReader {

  private final InputStream input_stream;

  public BlockingIOMessageStream(InputStream inputStream) {
    this.input_stream = inputStream;
  }

  @Override
  public int read(ByteBuffer buf) throws IOException {
    int max_read = buf.remaining();
    return input_stream.read(buf.array(), buf.arrayOffset(), max_read);
  }
}
