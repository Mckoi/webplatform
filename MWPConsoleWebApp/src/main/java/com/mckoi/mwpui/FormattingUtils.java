/**
 * com.mckoi.mwpbase.FormattingUtils  Oct 6, 2011
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

package com.mckoi.mwpui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * General static methods for formatting objects.
 *
 * @author Tobias Downer
 */

public class FormattingUtils {

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
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm:ss'.
   * The time is in the local time zone.
   */
  public static String formatLongDateTimeString(long ts) {
    synchronized (ntz_longdateformat) {
      return ntz_longdateformat.format(new Date(ts));
    }
  }

  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm:ss' in
   * the given time zone.
   */
  public static String formatLongDateTimeString(long ts, TimeZone time_zone) {
    synchronized (tz_longdateformat) {
      tz_longdateformat.setTimeZone(time_zone);
      return tz_longdateformat.format(new Date(ts));
    }
  }


  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm'. The
   * time is in the local time zone.
   */
  public static String formatDateTimeString(long ts) {
    synchronized (ntz_dateformat) {
      return ntz_dateformat.format(new Date(ts));
    }
  }

  /**
   * Given a date time, returns a string formatted as 'yyyy/MM/dd hh:mm' in
   * the given time zone.
   */
  public static String formatDateTimeString(long ts, TimeZone time_zone) {
    synchronized (tz_dateformat) {
      tz_dateformat.setTimeZone(time_zone);
      return tz_dateformat.format(new Date(ts));
    }
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
  public static String formatShortFileSizeValue(long value) {

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

}
