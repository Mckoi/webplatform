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

/**
 * 
 *
 * @author Tobias Downer
 */

public interface MessageStreamReader {

  /**
   * Reads a sequence of data from the stream and writes it into the
   * given buffer. This method may block if it's part of a blocking
   * implementation. If it doesn't block, it should return -1 if no
   * more data is currently available.
   *
   * @param buf the buffer to contain the read data.
   * @return the actual amount of data put into the buffer or -1 if
   *   no data remaining and it can't block.
   * @throws IOException on IO failure.
   */
  int read(ByteBuffer buf) throws IOException;

}
