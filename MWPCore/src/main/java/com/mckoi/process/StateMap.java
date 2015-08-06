/**
 * com.mckoi.process.StateMap  Nov 1, 2012
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

package com.mckoi.process;

import java.util.Map;

/**
 * A string key/value map used to represent the semi-persistent state of a
 * process instance. This object stores its key/value items on the Java
 * heap, and the process system periodically serializes the information in this
 * map. If the process service crashes, the process instance may recover from
 * the last state check-point when the service is restarted.
 * <p>
 * All map operations that modify the state of the map are atomic operations
 * (the system will not flush the state mid mutation operation). If a group of
 * modifications are necessary while guaranteeing a flush does not happen in
 * the middle, the 'lock' and 'unlock' methods can be used. While a state
 * map is under a 'lock', any flush operations will briefly wait for the lock
 * to be released, and if not released, the flush will fail.
 *
 * @author Tobias Downer
 */

public interface StateMap extends Map<String, String> {

  /**
   * Locks this map preventing the process system from serializing the content
   * of the map to the database. The 'unlock' method should be called to
   * release the lock. Locks can be stacked - the process system will only
   * flush when no locks are currently held.
   * <p>
   * This will block if the process system is currently serializing this
   * state map.
   */
  void lock();

  /**
   * Unlocks this map allowing the process system to serialize the content of
   * the map to the database. If 'unlock' is called and no locks are currently
   * being held on this state map then an exception is thrown.
   */
  void unlock();

}
