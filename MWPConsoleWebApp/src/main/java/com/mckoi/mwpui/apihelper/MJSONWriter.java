/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mckoi.mwpui.apihelper;

import java.io.IOException;
import java.io.Writer;
import org.json.JSONWriter;

/**
 * An implementation of JSONWriter that supports flush and close operations.
 *
 * @author Tobias Downer
 */
public class MJSONWriter extends JSONWriter {
  
  public MJSONWriter(Writer w) {
    super(w);
  }

  public void flush() throws IOException {
    writer.flush();
  }

  public void close() throws IOException {
    writer.close();
  }
  
}
