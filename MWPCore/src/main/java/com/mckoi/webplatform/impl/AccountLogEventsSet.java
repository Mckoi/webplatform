/**
 * com.mckoi.webplatform.AccountLogEventsSet  Jul 27, 2010
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

import com.mckoi.odb.*;
import com.mckoi.webplatform.LogEventsSet;
import com.mckoi.webplatform.LogPageEvent;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An implementation of LogEventsSet for an account's log files.
 *
 * @author Tobias Downer
 */

final class AccountLogEventsSet implements LogEventsSet {

  /**
   * A transaction used to read the information out of the path where the log
   * files are stored.
   */
  private final ODBTransaction transaction;

  /**
   * The name of the log event type (eg. "l.[account_name].http")
   */
  private final String log_event_type;

  /**
   * The first(inclusive) and last(inclusive) time uid in this events set.
   */
  private final String first_time_uid;
  private final String last_time_uid;

  /**
   * The list of entries in the set.
   */
  private ODBList entries_list = null;

  /**
   * The word class.
   */
  private ODBClass word_class;

//  /**
//   * The dictionary.
//   */
//  private ODBList dict_ref = null;



  /**
   * Constructor.
   */
  private AccountLogEventsSet(ODBTransaction transaction,
          String log_event_type, String start_uid, String end_uid) {

    this.transaction = transaction;
    this.log_event_type = log_event_type;

    this.first_time_uid = start_uid;
    this.last_time_uid = end_uid;

  }

  AccountLogEventsSet(ODBTransaction transaction, String log_event_type) {
    this(transaction, log_event_type,
         "00000000000000", "zzzzzzzzzzzzzz");
  }

  private void checkInit() {
    if (entries_list == null) {
//      System.out.println("--checkInit() Looking up: " + log_event_type);
      ODBObject log_root = transaction.getNamedItem("logroot");
      ODBObject log_ob = log_root.getList("logs").getObject(log_event_type);
      if (log_ob != null) {
        ODBList entries = log_ob.getList("entries");
//        System.out.println("Found log ob " + log_event_type);
//        System.out.println("entries.size() = " + entries.size());
        entries = entries.sub(first_time_uid, last_time_uid);
        entries_list = entries;
//        System.out.println("entries_list.size() = " + entries_list.size());
        word_class = transaction.findClass("L.Word");
//        dict_ref = log_ob.getList("dict_ref");
      }
    }
  }

  private LogPageEventImpl getLogPageEvent(long pos, long size, boolean backward) {
    long rpos;
    if (backward) {
      rpos = size - 1 - pos;
    }
    else {
      rpos = pos;
    }
    // Get the log entry,
    ODBObject entry_ob = entries_list.getObject(rpos);

    // Parse the entry,
    String time_uid = entry_ob.getString("time");
    String value = entry_ob.getString("value");

    long timeval = Long.parseLong(time_uid, 32);
    // Parse the value,
    int i;
    int sz = value.length();
    for (i = 0; i < sz; ++i) {
      char c = value.charAt(i);
      if (c == '#' || c == '-' || c == '*' || c == '[') {
        break;
      }
    }

    int entry_count = Integer.parseInt(value.substring(0, i), 32);
    LogPageEventImpl evt = new LogPageEventImpl(timeval, entry_count);
    int n = 0;
    while (entry_count > 0) {
      char c = value.charAt(i);

      if (c == '#') { // NULL
        evt.setValue(n, null);
        ++i;
      }
      else if (c == '-') { // Empty string
        evt.setValue(n, "");
        ++i;
      }
      else if (c == '*') { // Dictionary entry,
        String ref = value.substring(i + 1, i + 1 + 32);
        Reference word_ref = Reference.fromString(ref);
        // Look the word up in the dictionary
        ODBObject word = transaction.getObject(word_class, word_ref);
        evt.setValue(n, word.getString("word"));
        i += 1 + 32;
      }
      else if (c == '[') { // String
        int end_i = value.indexOf(']', i + 1);
        int str_sz = Integer.parseInt(value.substring(i + 1, end_i), 32);
        String val = value.substring(end_i + 1, end_i + 1 + str_sz);
        evt.setValue(n, val);
        i = end_i + 1 + str_sz;
      }

      ++n;
      --entry_count;
    }

    return evt;
  }


  @Override
  public boolean canQueryExactSize() {
    return true;
  }

  @Override
  public long getEstimatedSize() {
    return getExactSize();
  }

  @Override
  public LogPageEventImpl getEvent(long n) {
    return getLogPageEvent(n, Long.MAX_VALUE, false);
  }

  @Override
  public long getExactSize() {
    checkInit();

    if (entries_list == null) {
      return 0;
    }
    else {
      return entries_list.size();
    }
  }

  @Override
  public Iterator<LogPageEvent> iterator() {
    checkInit();

    if (entries_list == null) {
      return new EmptyLPEIterator();
    }
    return new LPEIterator(0, entries_list.size(), false);
  }

  @Override
  public Iterator<LogPageEvent> reverseIterator() {
    checkInit();

    if (entries_list == null) {
      return new EmptyLPEIterator();
    }
    return new LPEIterator(0, entries_list.size(), true);
  }

  @Override
  public LogEventsSet subset(long start_timestamp, long end_timestamp) {
    // Create a 14 byte uid key for the first and last timestamp,
    String v1 = Long.toString(Math.abs(start_timestamp), 32);
    String start_time_uid = SecureRandomUtil.pad(v1, 14);
    String v2 = Long.toString(Math.abs(end_timestamp), 32);
    String end_time_uid = SecureRandomUtil.pad(v2, 14);

    return new AccountLogEventsSet(transaction, log_event_type,
                                   start_time_uid, end_time_uid);
  }



  private static class EmptyLPEIterator implements Iterator<LogPageEvent> {
    @Override
    public boolean hasNext() {
      return false;
    }
    @Override
    public LogPageEvent next() {
      throw new NoSuchElementException();
    }
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }


  private class LPEIterator implements Iterator<LogPageEvent> {

    private long pos = 0;
    private long size = 0;
    private boolean backward = false;

    private LPEIterator(long pos, long size, boolean backward) {
      this.pos = pos;
      this.size = size;
      this.backward = backward;
    }

    @Override
    public boolean hasNext() {
      return (pos < size);
    }

    @Override
    public LogPageEvent next() {
      LogPageEventImpl evt = getLogPageEvent(pos, size, backward);
      ++pos;
      return evt;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

}
