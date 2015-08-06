/**
 * com.mckoi.webplatform.LogPage  Jul 26, 2010
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A LogPage is a set of log lines of a specific type of log event, generated
 * by a machine node. A LogPage may persist in memory for a short while
 * before being flushed out to a more permanent store. A log page is tied to
 * an account.
 *
 * @author Tobias Downer
 */

class LogPage {

  /**
   * The type of event being logged.
   */
  private final String log_type;

  /**
   * This list contains the current contents of the log page.
   */
  private final ArrayList<LogPageEventImpl> entries;

  /**
   * A dictionary of unique words on this page.
   */
  private final HashMap<String, DictionaryId> dictionary = new HashMap();

  /**
   * An id for the dictionary.
   */
  private int dictionary_id = 1;

  /**
   * Constructor.
   */
  LogPage(String log_type) {
    this.log_type = log_type;
    this.entries = new ArrayList(512);
  }

  /**
   * Returns the type of this log entry.
   */
  String getLogType() {
    return log_type;
  }

  /**
   * Returns true if this log page is empty.
   */
  boolean isEmpty() {
    synchronized (entries) {
      return entries.isEmpty();
    }
  }

  /**
   * Returns the number of entries in this log page.
   */
  int getSize() {
    synchronized (entries) {
      return entries.size();
    }
  }

  /**
   * Returns the timestamp of the last event added to this page, or -1 if the
   * page is empty.
   */
  long getLastTimestamp() {
    synchronized (entries) {
      if (!entries.isEmpty()) {
        return entries.get(entries.size() - 1).getTimestamp();
      }
      return -1;
    }
  }

  /**
   * Returns the timestamp of the last event added to this page, or -1 if the
   * page is empty.
   */
  long getFirstTimestamp() {
    synchronized (entries) {
      if (!entries.isEmpty()) {
        return entries.get(0).getTimestamp();
      }
      return -1;
    }
  }

  /**
   * Adds an event to the page.
   */
  void addEvent(LogPageEventImpl event) {
    synchronized (entries) {
      // Intern the strings in the event,
      int sz = event.getValueCount();
      for (int i = 0; i < sz; ++i) {
        String event_value = event.getValue(i);
        DictionaryId dict_id;
        if (event_value == null) {
          dict_id = NULL_DICTIONARY_ID;
        }
        else {
          dict_id = dictionary.get(event_value);
          if (dict_id == null) {
            dict_id = new DictionaryId(dictionary_id, event_value);
            dictionary.put(event_value, dict_id);
            ++dictionary_id;
          }
          // Intern the string against our dictionary
          else {
            event.setValue(i, dict_id.word);
            // Update the counter,
            ++dict_id.entry_count;
          }
        }
        event.setDictionaryId(i, dict_id.id);
      }
      entries.add(event);
    }
  }

  /**
   * Returns the number of times the value at the given dictionary id has been
   * repeated in the page.
   */
  int getWordRepetitions(String dictionary_word) {
    return dictionary.get(dictionary_word).entry_count;
  }

  /**
   * Internal method that adds the event without do any interning of words.
   * Not suitable for concurrent access.
   */
  void internalAddEvent(LogPageEventImpl event) {
    entries.add(event);
  }

  /**
   * Returns the event list. Note that the returned object is not protected
   * from concurrent events.
   */
  List<LogPageEventImpl> getEvents() {
    return entries;
  }


  private static DictionaryId NULL_DICTIONARY_ID = new DictionaryId(0, null);

  /**
   * The dictionary id + value (a silly hack to intern the strings).
   */
  private static class DictionaryId {
    private final int id;
    private final String word;
    private int entry_count = 1;
    DictionaryId(int dictionary_id, String word) {
      this.id = dictionary_id;
      this.word = word;
    }
  }

}
