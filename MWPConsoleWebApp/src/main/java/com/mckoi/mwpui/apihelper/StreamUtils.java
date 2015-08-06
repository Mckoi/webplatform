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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Various stream utilities.
 *
 * @author Tobias Downer
 */
public class StreamUtils {
  
  /**
   * Reads the entire InputStream and produces a String assuming the input
   * stream is encoded as UTF-8.
   * 
   * @param ins
   * @return
   * @throws IOException 
   */
  public static String stringValueOfUTF8Stream(InputStream ins)
                                                          throws IOException {
    return stringValueOfReader(new InputStreamReader(ins, "UTF-8"));
  }

  /**
   * Consumes all the characters on the given Reader to produce a String.
   * 
   * @param r
   * @return
   * @throws IOException 
   */
  public static String stringValueOfReader(Reader r) throws IOException {
    StringBuilder b = new StringBuilder(4096);
    char[] buf = new char[2048];
    while (true) {
      int act_read = r.read(buf, 0, buf.length);
      if (act_read < 0) {
        break;
      }
      b.append(buf, 0, act_read);
    }

    return b.toString();
  }
  
  
}
