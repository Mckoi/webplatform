/**
 * com.mckoi.webplatform.LogPageEventImpl  Jul 26, 2010
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

package com.mckoi.webplatform.impl;

import com.mckoi.webplatform.LogPageEvent;

/**
 * A single event stored in a log page. This consists of a timestamp of when
 * the event occurred, and a string containing human comprehensible
 * details of the event.
 *
 * @author Tobias Downer
 */

class LogPageEventImpl implements LogPageEvent {

  /**
   * The event timestamp.
   */
  private final long timestamp;

  /**
   * The event strings.
   */
  private final String[] event_vals;

  /**
   * The dictionary ids on the page for the values.
   */
  private final int[] dictionary_vals;

  /**
   * Constructs the event.
   */
  LogPageEventImpl(long timestamp, int array_size) {
    this.timestamp = timestamp;
    this.event_vals = new String[array_size];
    this.dictionary_vals = new int[array_size];
  }

  /**
   * Returns the timestamp of this event.
   */
  @Override
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the event as a string.
   */
  @Override
  public String[] getEventValues() {
    return event_vals.clone();
  }

  /**
   * Returns the number of values in this event.
   */
  @Override
  public int getValueCount() {
    return event_vals.length;
  }

  /**
   * Sets the nth value in this event.
   */
  void setValue(int n, String val) {
    event_vals[n] = val;
  }

  /**
   * Sets the dictionary id for the value in this event.
   */
  void setDictionaryId(int n, int dict_id) {
    dictionary_vals[n] = dict_id;
  }

  /**
   * Returns the nth value in this event.
   */
  public String getValue(int n) {
    return event_vals[n];
  }

  /**
   * Returns the nth dictionary value in this event.
   */
  int getDictionaryId(int n) {
    return dictionary_vals[n];
  }

  /**
   * Returns this event as a simple string readable by a human. It's not
   * intended for this to be parsable.
   */
  @Override
  public String asString() {
    StringBuilder b = new StringBuilder();
    for (String val : event_vals) {
      b.append("\"");
      b.append(val != null ? val : "");
      b.append("\" ");
    }
    return b.toString();
  }

}
