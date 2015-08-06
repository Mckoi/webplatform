/**
 * com.mckoi.webplatform.LogPageEvent  Apr 17, 2011
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

/**
 * A single event stored in a log page. This consists of a timestamp of when
 * the event occurred, and a string containing human comprehensible
 * details of the event.
 *
 * @author Tobias Downer
 */

public interface LogPageEvent {

  /**
   * Returns the timestamp of this event.
   */
  long getTimestamp();

  /**
   * Returns the event as a string.
   */
  String[] getEventValues();

  /**
   * Returns the number of values in this event.
   */
  int getValueCount();

  /**
   * Returns this event as a simple string readable by a human. It's not
   * intended for this to be parsable.
   */
  String asString();

}
