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

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utilities for managing logs via the web platform LogSystem.
 *
 * @author Tobias Downer
 */
public class LogUtils {
  
  /**
   * Converts the Throwable into a String containing the stack trace.
   * 
   * @param ex
   * @return 
   */
  public static String stringStackTrace(Throwable ex) {
    StringWriter w = new StringWriter();
    PrintWriter pw = new PrintWriter(w);
    ex.printStackTrace(pw);
    pw.flush();
    return w.toString();
  }
  
}
