/**
 * com.mckoi.apihelper.CommandLine  Oct 6, 2011
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2010  Diehl and Associates, Inc.
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

package com.mckoi.apihelper;

import java.util.ArrayList;

/**
 * An object that encapsulates the information in a command line string.
 *
 * @author Tobias Downer
 */

public class CommandLine {

  /**
   * The original command line string.
   */
  private String cline;

  /**
   * The command line string exploded into an array of strings.
   */
  private String[] args = null;

  /**
   * Constructor.
   */
  public CommandLine(String cline) {
    if (cline == null) {
      cline = "";
    }
    this.cline = cline;
  }

  /**
   * Returns the default expressions of the command line as an array of
   * strings. For example, 'ls -al /system /workspace "/a dir/here/"' returns
   * { "/system", "/workspace", "/a dir/here/" }.
   */
  public String[] getDefaultArgs() {
    String[] args_in = getArgs();
    ArrayList<String> out_args = new ArrayList(args_in.length);

    // Iterate on the list, ignoring the first item which is the program name.
    for (int i = 1; i < args_in.length; ++i) {
      String arg = args_in[i];
      // Ignore switches,
      if (arg.startsWith("-")) {
        // Ignore,
      }
      else {
        if (arg.startsWith("\"") && arg.endsWith("\"")) {
          arg = arg.substring(1, arg.length() - 1);
        }
        out_args.add(arg);
      }
    }
    return out_args.toArray(new String[out_args.size()]);
  }

  /**
   * Returns the command line as a String[] array, where each element of the
   * array represents a word from the original command line. For example,
   * a command line of 'ls -al "/system/a dir/"' parses to the array
   * { "ls", "-al", "\"/system/a dir/\"" }
   */
  public String[] getArgs() {
    if (args == null) {
      args = parseArgs(cline);
    }
    return args.clone();
  }




  private static int consumeWhitespace(Tokener tokener) {
    String s = tokener.str;
    if (s.length() == 0) {
      return -1;
    }
    int count = 0;
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        ++count;
      }
      else {
        break;
      }
    }
    tokener.str = s.substring(count);
    return count;
  }

  private static String consumeArg(
                             Tokener tokener, boolean close_quote_important) {
    String s = tokener.str;
    if (s.length() == 0) {
      return null;
    }
    // If it's a "string"
    if (s.charAt(0) == '\"') {
      int delim = s.indexOf("\"", 1);
      if (delim > 0) {
        tokener.str = s.substring(delim + 1);
        return s.substring(0, delim + 1);
      }
      // If closing quote is not important,
      else if (!close_quote_important) {
        tokener.str = "";
        return s;
      }
    }
    // Otherwise it's just a word so look for the next whitespace or end
    // of string,
    int p = 0;
    for (int i = 0; i < s.length(); ++i) {
      char c = s.charAt(i);
      if (!Character.isWhitespace(c)) {
        ++p;
      }
      else {
        break;
      }
    }
    tokener.str = s.substring(p);
    return s.substring(0, p);
  }

  public static String[] parseArgs(
                               String cline, boolean closing_quote_important) {
    ArrayList<String> args = new ArrayList();
    Tokener tokener = new Tokener();
    tokener.str = cline;

    while (true) {
      int c = consumeWhitespace(tokener);
      if (c == -1) {
        break;
      }
      String arg = consumeArg(tokener, closing_quote_important);
      if (arg == null) {
        break;
      }
      args.add(arg);
    }

    // Convert it to an array,
    return args.toArray(new String[args.size()]);
  }

  public static String[] parseArgs(String cline) {
    return parseArgs(cline, true);
  }

  private static class Tokener {
    String str;
  }

}
