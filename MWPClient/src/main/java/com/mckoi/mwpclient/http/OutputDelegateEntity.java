/*
 * Copyright 2015 Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mckoi.mwpclient.http;

import java.io.IOException;
import java.io.InputStream;
import org.apache.http.entity.AbstractHttpEntity;

/**
 * A binary streamable (none-repeatable) HttpEntity where the method of
 * writing data is to an OutputStream. Implement the 'writeTo' method as
 * a closure to output the entity content.
 *
 * @author Tobias Downer
 */
public abstract class OutputDelegateEntity extends AbstractHttpEntity {

  private final long length;

  public OutputDelegateEntity() {
    this(-1);
  }

  public OutputDelegateEntity(long length) {
    this.length = length;
    super.setContentType("application/octet-stream");
  }

  @Override
  public boolean isRepeatable() {
    return true;
  }

  @Override
  public long getContentLength() {
    return length;
  }

  @Override
  public InputStream getContent()
                          throws IOException, UnsupportedOperationException {
    new Error().printStackTrace(System.err);
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isStreaming() {
    return true;
  }

}
