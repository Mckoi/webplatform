/**
 * com.mckoi.apihelper.TextUtils  Oct 8, 2012
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

package com.mckoi.apihelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.*;
import java.util.*;

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
      if (last_index >= 0) {
        if (lines.get(last_index).equals("")) {
          lines.remove(last_index);
        }
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
   * Given a quoted string, removes the quotes.
   */
  public static String unquote(String str) {
    if (str != null && str.length() > 1) {
      if (str.startsWith("\"") && str.endsWith("\"")) {
        return str.substring(1, str.length() - 1);
      }
    }
    return str;
  }

  /**
   * Splits a command line string into a set of strings where the first string
   * in the array is the command that was run. Some examples;
   * <p>
   * <pre>
   * 'set a=b'            => { 'set', 'a=b' }
   * 'set a = b'          => { 'set', 'a', '=', 'b' }
   * 'echo "Hello World"' => { 'echo', '"Hello World"' }
   * 'ls -al /bin/'       => { 'ls', '-al', '/bin/' }
   * 'ls -al "/bin/"'     => { 'ls', '-al', '"/bin/"' }
   * </pre>
   */
  public static String[] splitCommandLine(String command_line) {
    String[] args = CommandLine.parseArgs(command_line);
    return args;
  }

  /**
   * Splits a command line string into a set of strings and unescapes any
   * string sequences as appropriate.
   * <p>
   * <pre>
   * 'set a=b'            => { 'set', 'a=b' }
   * 'set a = b'          => { 'set', 'a', '=', 'b' }
   * 'echo "Hello World"' => { 'echo', 'Hello World' }
   * 'ls -al /bin/'       => { 'ls', '-al', '/bin/' }
   * 'ls -al "/bin/"'     => { 'ls', '-al', '/bin/' }
   * </pre>
   */
  public static String[] splitCommandLineAndUnquote(String command_line) {
    String[] args = CommandLine.parseArgs(command_line);
    for (int i = 0, sz = args.length; i < sz; ++i) {
      args[i] = unquote(args[i]);
    }
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


  private static String roundDouble(double dec) {
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
    return dec_str;
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
    b.append(roundDouble(dec));
    b.append(' ');
    b.append(mag);

    return b.toString();

  }

  /**
   * These statics represent some information about how many nanoseconds are
   * in various measures of time.
   */
  private static final int[] TIMESTAMP_FACTORS = new int[] {
    1,         // nanos in nano
    1000000,   // nanos in millis
    1000,      // millis in second
    60,        // seconds in minute
    60,        // minutes in hour
    24,        // hours in day
    7,         // days in week
  };

  private static final String[] TIMESTAMP_FSTRINGS = new String[] {
    "ns ",
    "ms ",
    "sec",
    "min",
    "hr ",
    "day",
    "wk "
  };

  /**
   * Formats a long value representing a number of nanoseconds into a human
   * understandable form (for example, '400 ns ', '50.7 ms ', '15 min', etc.
   */
  public static String formatTimeFrame(final long nanos) {

    if (nanos == 0) {
      return "0 ns ";
    }

    int dom_mag = 0;
    long mag = 1;
    long last_mag = mag;
    for (int f : TIMESTAMP_FACTORS) {
      mag = mag * f;
      if ((nanos / mag) == 0) {
        break;
      }
      last_mag = mag;
      ++dom_mag;
    }

    String s = TIMESTAMP_FSTRINGS[dom_mag - 1];
    double v = ((double) nanos) / last_mag;

    StringBuilder b = new StringBuilder();
    b.append(roundDouble(v));
    b.append(" ");
    b.append(TIMESTAMP_FSTRINGS[dom_mag - 1]);

    return b.toString();

  }
  

  

}
