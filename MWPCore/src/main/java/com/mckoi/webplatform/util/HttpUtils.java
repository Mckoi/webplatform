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

package com.mckoi.webplatform.util;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

/**
 * Various http related utilities.
 *
 * @author Tobias Downer
 */
public class HttpUtils {

  /**
   * Decodes a URL encoded string. Replaces '%20' and '+' with spaces.
   * 
   * @param file
   * @return
   */
  public static String decodeURLFileName(URL file) {
    try {
      return URLDecoder.decode(file.getFile(), "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      // Should not be possible...
      throw new RuntimeException(ex);
    }
  }

}
