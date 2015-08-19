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

/**
 * Makes a best attempt at producing a monotonic time function through the
 * Java Virtual Machine. A monotonic time function will not return a value
 * that is less than preceeding calls, with the exception of the time function
 * wrapping on 64-bit overflow.
 * <p>
 * Note that the values returned by the 'now' methods are only valid within
 * the current VM. A time value produced in one VM will have no relation with
 * another time value produced in another VM. Comparing times across VM
 * instances produces undefined results.
 *
 * @author Tobias Downer
 */

public class MonotonicTime {
  
  private static final long MILLISECONDS_IN_NANOSECOND = 1000000;
  
  /**
   * Returns a number representing the number of nanoseconds since some
   * arbitrary point in time (defined when the VM is created).
   * 
   * @return 
   */
  public static long now() {
    return System.nanoTime();
  }
  
  /**
   * Returns a time, in nanoseconds, of the current time with the given number
   * of milliseconds added.
   * 
   * @param milliseconds_diff
   * @return 
   */
  public static long now(long milliseconds_diff) {
    return millisAdd(now(), milliseconds_diff);
  }

  /**
   * Returns the time difference between two recorded times that were generated
   * via 'monotonicNow' in milliseconds. The calculation is
   * abs((time1 - time2) / MILLISECONDS_IN_NANOSECOND)
   * 
   * @param time_nanos1
   * @param time_nanos2
   * @return 
   */
  public static long millisDif(long time_nanos1, long time_nanos2) {
    return Math.abs((time_nanos1 - time_nanos2) / MILLISECONDS_IN_NANOSECOND);
  }

  /**
   * Returns the number of milliseconds between the given time and the current
   * time. 'time_nanos' may be a time in the past of future. Always returns a
   * positive value representing the difference in time.
   * 
   * @param time_nanos
   * @return 
   */
  public static long millisSince(long time_nanos) {
    return millisDif(now(), time_nanos);
  }

  /**
   * Converts 'time_millis' (time period in milliseconds) to nanoseconds,
   * adds that value from 'time_nanos' and returns the result. The
   * calculation is
   * time_nanos + (time_millis * MILLISECONDS_IN_NANOSECOND)
   * 
   * @param time_nanos
   * @param time_millis
   * @return 
   */
  public static long millisAdd(long time_nanos, long time_millis) {
    return time_nanos + (time_millis * MILLISECONDS_IN_NANOSECOND);
  }

  /**
   * Converts 'time_millis' (time period in milliseconds) to nanoseconds,
   * subtracts that value from 'time_nanos' and returns the result. The
   * calculation is
   * time_nanos - (time_millis * MILLISECONDS_IN_NANOSECOND)
   * 
   * @param time_nanos
   * @param time_millis
   * @return 
   */
  public static long millisSubtract(long time_nanos, long time_millis) {
    return millisAdd(time_nanos, -time_millis);
  }

  /**
   * Returns true if the first time represents a time in the past of the second
   * time.
   * 
   * @param time_nanos1
   * @param time_nanos2
   * @return 
   */
  public static boolean isInPastOf(long time_nanos1, long time_nanos2) {
    return time_nanos1 < time_nanos2;
  }

  /**
   * Returns true if the first time represents a time in the future of the
   * second time.
   * 
   * @param time_nanos1
   * @param time_nanos2
   * @return 
   */
  public static boolean isInFutureOf(long time_nanos1, long time_nanos2) {
    return time_nanos1 > time_nanos2;
  }

}
