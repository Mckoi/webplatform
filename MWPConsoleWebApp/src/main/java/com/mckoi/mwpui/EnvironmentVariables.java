/**
 * com.mckoi.mwpbase.EnvironmentVariables  Apr 13, 2011
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

import java.util.Set;

/**
 * An interface for accessing the environment variables of a terminal
 * session.
 *
 * @author Tobias Downer
 */

public interface EnvironmentVariables {

  /**
   * Fetches an environment variable. Returns null if the variable isn't set.
   */
  String get(String key);

  /**
   * Changes an environment variable. Returns the value that was replaced or
   * null if no value was replaced.
   */
  String put(String key, String value);

  /**
   * Returns the key set of all keys in the map.
   */
  Set<String> keySet();

  /**
   * Returns true if the map was modified since it was created.
   */
  boolean wasModified();

  /**
   * Sets an object used for temporary state in this frame. Setting the
   * temporary object to null removes it.
   */
  void setTemporaryObject(Object ob);
  
  /**
   * Returns the object for temporary state in this frame, or null if the
   * temporary object is not defined.
   */
  Object getTemporaryObject();

}
