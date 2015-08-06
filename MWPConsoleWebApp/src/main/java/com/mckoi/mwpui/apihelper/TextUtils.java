/**
 * com.mckoi.mwpui.jsapi.TextUtils  Oct 8, 2012
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

package com.mckoi.mwpui.apihelper;

import com.mckoi.mwpui.CommandLine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.*;
import java.util.*;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * An API that provides access into the java.text API.
 *
 * @author Tobias Downer
 */

public class TextUtils {

  /**
   * Given an English page of text, returns an array of strings representing
   * the same text broken into lines where the line boundary is at the 'width'
   * character. Double line separators are considered paragraph breaks.
   */
  public static String[] splitIntoLines(
                             String text, int width, int first_line_position) {

    int line_position = first_line_position;
    
    List<String> lines = new ArrayList();

    StringReader r = new StringReader(text);
    BufferedReader br = new BufferedReader(r);

    StringBuilder para = new StringBuilder();
    try {
      while (true) {
        boolean eof = false;
        while (true) {
          String line = br.readLine();
          if (line == null) {
            eof = true;
            break;
          }
          line = line.trim();
          if (line.length() == 0) {
            // Paragraph end!
            break;
          }
          para.append(" ");
          para.append(line);
        }
        String para_str = para.toString().trim();
        
        // If there's something to process,
        if (para_str.length() > 0) {
          BreakIterator break_it = BreakIterator.getLineInstance(Locale.US);
          break_it.setText(para_str);
          int last_boundary = 0;
          while (true) {
            int b = break_it.next();
            if (b == BreakIterator.DONE) {
              String line = para_str.substring(last_boundary);
              if (line.length() > 0) {
                lines.add(line);
              }
              break;
            }
            CharacterIterator cit = break_it.getText();
            int line_end = b;
            while (true) {
              char c = cit.previous();
              if (c == ' ' || c == '\t' || c == '\n') {
                --line_end;
              }
              else {
                break;
              }
            }
            cit.setIndex(b);
            if (line_end - last_boundary > (width - line_position)) {
              line_position = 0;
              int prev_boundary = break_it.previous();
              String line;
              if (prev_boundary > last_boundary) {
                line = para_str.substring(last_boundary, prev_boundary).trim();
                last_boundary = prev_boundary;
              }
              else {
                line = para_str.substring(last_boundary, b).trim();
                last_boundary = b;
                break_it.next();
              }
              if (line.length() > 0) {
                lines.add(line);
              }
            }
          }
          lines.add("");
        }
        if (eof) {
          break;
        }
        para.setLength(0);
      }

      // Remove any trailing empty characters,
      int last_index = lines.size() - 1;
      if (lines.get(last_index).equals("")) {
        lines.remove(last_index);
      }

      // Convert to an array and return
      return lines.toArray(new String[lines.size()]);

    }
    catch (IOException e) {
      // Should never happen,
      throw new RuntimeException(e);
    }

  }

  /**
   * Splits a command line string into a set of strings where the first string
   * in the array is the command that was run. Some examples;
   * <p>
   * <pre>
   * 'set a=b'            => { 'set', 'a=b' }
   * 'set a = b'          => { 'set', 'a', '=', 'b' }
   * 'echo "Hello World"' => { 'echo', 'Hello World' }
   * 'ls -al /bin/'       => { 'ls', '-al', '/bin/' }
   * 'ls -al "/bin/"'     => { 'ls', '-al', '/bin/' }
   * </pre>
   */
  public static String[] splitCommandLine(String command_line) {
    String[] args = CommandLine.parseArgs(command_line);
    return args;
  }

  // Date/time formatting,

  /**
   * A Simple date format instance.
   */

  private final static DateFormat ntz_dateformat =
                                  new SimpleDateFormat("yyyy/MM/dd HH:mm");
  private final static DateFormat tz_dateformat =
                                  new SimpleDateFormat("yyyy/MM/dd HH:mm");

  private final static DateFormat ntz_longdateformat =
                                  new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
  private final static DateFormat tz_longdateformat =
                                  new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");


  /**
   * Given a date time value, returns a string formatted with the given
   * formatter in the time zone.
   */
  public static String formatTimestampWith(
                           long ts, TimeZone timezone, DateFormat formatter) {
    synchronized (formatter) {
      if (timezone != null) {
        formatter.setTimeZone(timezone);
      }
      return formatter.format(new Date(ts));
    }
  }

  /**
   * Parses the given long date time string with the given format and time
   * zone, and returns a time value representing the time, or null if the
   * string could not be parsed.
   */
  public static Long parseDateTimeStringWith(
                           String str, TimeZone timezone, DateFormat parser) {
    synchronized (parser) {
      if (timezone != null) {
        parser.setTimeZone(timezone);
      }
      try {
        Date date = parser.parse(str);
        if (date != null) {
          return date.getTime();
        }
      }
      catch (ParseException e) {
        // Fall through
      }
      return null;
    }
  }
  

  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm:ss'.
   * The time is in the local time zone.
   */
  public static String formatLongDateTimeString(long ts) {
    return formatTimestampWith(ts, null, ntz_longdateformat);
  }

  /**
   * Parses the given long date time string formatting as 'yyyy/MM/dd hh:mm:ss'
   * assuming the local time zone, and returns a time value representing the
   * time, or null if the string could not be parsed.
   */
  public static Long parseLongDateTimeString(String str) {
    return parseDateTimeStringWith(str, null, ntz_longdateformat);
  }

  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm:ss' in
   * the given time zone.
   */
  public static String formatLongDateTimeString(long ts, TimeZone time_zone) {
    return formatTimestampWith(ts, time_zone, tz_longdateformat);
  }

  /**
   * Parses the given long date time string formatting as 'yyyy/MM/dd hh:mm:ss'
   * assuming the given time zone, and returns a time value representing the
   * time, or null if the string could not be parsed.
   */
  public static Long parseLongDateTimeString(String str, TimeZone time_zone) {
    return parseDateTimeStringWith(str, time_zone, tz_longdateformat);
  }

  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm'. The
   * time is in the local time zone.
   */
  public static String formatDateTimeString(long ts) {
    return formatTimestampWith(ts, null, ntz_dateformat);
  }

  /**
   * Parses the given date time string formatting as 'yyyy/MM/dd hh:mm'
   * assuming the local time zone, and returns a time value representing the
   * time, or null if the string could not be parsed.
   */
  public static Long parseDateTimeString(String str) {
    return parseDateTimeStringWith(str, null, ntz_dateformat);
  }

  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm' in
   * the given time zone.
   */
  public static String formatDateTimeString(long ts, TimeZone time_zone) {
    return formatTimestampWith(ts, time_zone, tz_dateformat);
  }

  /**
   * Parses the given date time string formatting as 'yyyy/MM/dd hh:mm'
   * assuming the given time zone, and returns a time value representing the
   * time, or null if the string could not be parsed.
   */
  public static Long parseDateTimeString(String str, TimeZone time_zone) {
    return parseDateTimeStringWith(str, time_zone, tz_dateformat);
  }

  /**
   * Converts a character to a hex value between 0 and 15.
   * 
   * @param c
   * @return 
   */
  public static int hexCharacterToValue(char c) {
    if (c >= '0' && c <= '9') {
      return ((int) c) - (int) '0';
    }
    if (c >= 'a' && c <= 'f') {
      return (((int) c) - (int) 'a') + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return (((int) c) - (int) 'A') + 10;
    }
    throw new RuntimeException("Not a hexidecimal character");
  }

  /**
   * Returns a number of whitespace characters for padding.
   */
  public static String pad(int size) {
    StringBuilder b = new StringBuilder(size);
    for (int i = 0; i < size; ++i) {
      b.append(' ');
    }
    return b.toString();
  }

  /**
   * Given a long value, returns a string that represents the value in a
   * human understandable short form. For example, 500 becomes "500  ",
   * 42500 becomes "4.25 K", 5500000 becomes "5.5 M", etc.
   */
  public static String formatHumanDataSizeValue(long value) {

    if (value < 0) {
      throw new RuntimeException("Negative value");
    }

    char mag = ' ';
    double dec = (double) value;

    if (value > (1024L * 1024L * 1024L * 1024L * 1024L)) {  // P
      mag = 'P';
      dec = dec / (1024L * 1024L * 1024L * 1024L * 1024L);
    }
    else if (value > (1024L * 1024L * 1024L * 1024L)) {  // T
      mag = 'T';
      dec = dec / (1024L * 1024L * 1024L * 1024L);
    }
    else if (value > (1024L * 1024L * 1024L)) {  // G
      mag = 'G';
      dec = dec / (1024L * 1024L * 1024L);
    }
    else if (value > (1024L * 1024L)) {  // M
      mag = 'M';
      dec = dec / (1024L * 1024L);
    }
    else if (value > (9L * 1024L)) {  // K
      mag = 'K';
      dec = dec / (1024L);
    }

    StringBuilder b = new StringBuilder();

    String dec_str = Double.toString(dec);
    int delim = dec_str.indexOf(".");
    if (delim >= 0) {
      dec_str = dec_str.substring(0, Math.min(delim + 3, dec_str.length()));
    }
    // Remove the .0 or .00 from end
    if (dec_str.endsWith(".0")) {
      dec_str = dec_str.substring(0, dec_str.length() - 2);
    }
    else if (dec_str.endsWith(".00")) {
      dec_str = dec_str.substring(0, dec_str.length() - 3);
    }
    b.append(dec_str);
    b.append(' ');
    b.append(mag);

    return b.toString();

  }

  public static String javaObjectDump(Object ob) {
    StringBuilder b = new StringBuilder();
    b.append("(").append(ob.hashCode()).append(") ");
    if (ob instanceof ScriptableObject) {
      ScriptableObject s = (ScriptableObject) ob;
      Scriptable parent = s.getParentScope();
      Scriptable proto = s.getPrototype();
      Object[] ids = s.getAllIds();
      b.append("ids = ").append(Arrays.asList(ids)).append(" ");
      b.append("parent = ");
      if (parent != null) {
        b.append("(").append(parent.hashCode()).append(") ");
      }
      b.append(parent);
      b.append(" proto = ");
      if (proto != null) {
        b.append("(").append(proto.hashCode()).append(") ");
      }
      b.append(proto);
    }
    else {
      b.append("Class = ").append(ob.getClass());
    }
    return b.toString();
  }
  

}
