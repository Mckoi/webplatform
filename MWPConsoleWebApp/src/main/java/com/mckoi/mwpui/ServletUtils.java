/**
 * com.mckoi.mwpui.ServletUtils  May 12, 2012
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

package com.mckoi.mwpui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletInputStream;

/**
 * Utility methods for Servlet that process AJAX requests for the admin
 * interface.
 *
 * @author Tobias Downer
 */

public class ServletUtils {

  /**
   * Decodes all the arguments in a request.
   */
  public static Map<String, String> decodeArguments(
                            ServletInputStream servlet_in) throws IOException {

    Map<String, String> map = new HashMap(8);

    InputStreamReader inr = new InputStreamReader(servlet_in, "UTF-8");
    BufferedReader bufr = new BufferedReader(inr, 512);

    StringBuilder key = new StringBuilder();
    StringBuilder val = new StringBuilder();
    StringBuilder sz = new StringBuilder(8);

    int state = 0;
    
    while (true) {
      // Consume
      int ich = bufr.read();
      // EOF,
      if (ich == -1) {
        break;
      }
      char ch = (char) ich;
      if (state > 0) {
        val.append(ch);
        --state;
        if (state == 0) {
          map.put(key.toString(), val.toString());
          key.setLength(0);
          val.setLength(0);
          sz.setLength(0);
        }
      }
      else if (state == 0) {
        // End of key,
        if (ch == '=') {
          state = -1;
        }
        else {
          key.append(ch);
        }
      }
      else if (state == -1) {
        if (ch == '|') {
          state = Integer.parseInt(sz.toString());
          if (state == 0) {
            map.put(key.toString(), val.toString());
            key.setLength(0);
            val.setLength(0);
            sz.setLength(0);
          }
        }
        else {
          sz.append(ch);
        }
      }
    }

    return map;

  }

}
