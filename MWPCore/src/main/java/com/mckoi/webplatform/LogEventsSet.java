/**
 * com.mckoi.webplatform.LogEventsSet  Jul 27, 2010
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

package com.mckoi.webplatform;

import java.util.Iterator;

/**
 * A LogEventsSet provides a way to read the unfiltered content of a log file.
 * The individual events of a log can be traversed from the start forward, or
 * from the end backwards. The log pages can also be traversed forward and
 * backwards. This class also provides a time-frame query allowing a subset
 * of the log file to be efficiently traversed.
 * <p>
 * LogEventsSet does not have event filtering capabilities. Log events are
 * unstructured. Filtering of log events must be done either by using an
 * index or online while traversing.
 *
 * @author Tobias Downer
 */

public interface LogEventsSet {

  /**
   * Returns true if this log events set implementation can determine
   * exactly the number of entries in this set. If this returns true then
   * 'getExactSize()' can be used. If this returns false, only
   * 'getEstimatedSize()' can be used to guess the size of the set.
   */
  boolean canQueryExactSize();

  /**
   * Returns the estimated number of entries in this event set if an accurate
   * count can not be obtained.
   */
  long getEstimatedSize();

  /**
   * Returns the number of entries in this event set if an accurate count of
   * the number of entries in this log set is allowed.
   */
  long getExactSize();

  /**
   * Returns the log event at index n of the set of entries if an accurate
   * count is allowed.
   */
  LogPageEvent getEvent(long n);

  /**
   * Returns an iterator over the events in the log whose position starts at
   * the first element in the log and calling 'next' will traverse forward
   * through the set of events.
   */
  Iterator<LogPageEvent> iterator();

  /**
   * Returns an iterator over the events in the log whose position starts at
   * the last element in the log and calling 'next' will traverse backwards
   * through the set of events.
   */
  Iterator<LogPageEvent> reverseIterator();

  /**
   * Returns a subset of this LogEventsSet between the timestamps given. The
   * returned LogEventsSet may not have completely accurate precision and may
   * include entries before and after the requested time frame.
   */
  LogEventsSet subset(long start_timestamp, long end_timestamp);

}
