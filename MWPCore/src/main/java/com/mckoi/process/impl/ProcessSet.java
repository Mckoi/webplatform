/**
 * com.mckoi.process.impl.ProcessSet  Mar 27, 2012
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

package com.mckoi.process.impl;

import com.mckoi.process.ProcessId;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory map of all process_ids managed on the process service. This
 * can be queried quickly to determine if a process is currently active or
 * not. The data in this set is fetched from the database at start-up.
 *
 * @author Tobias Downer
 */

class ProcessSet {

  /**
   * The lock.
   */
  private final Object CHANGE_LOCK = new Object();

  /**
   * The map of all processes to ProcessInstanceImpl objects.
   */
  private final ConcurrentMap<ProcessId, ProcessInstanceImpl> process_map;

  /**
   * Constructor.
   */
  ProcessSet() {
    this.process_map = new ConcurrentHashMap<>();
  }

  /**
   * Attempts to allocate the process against this process set. If the process
   * is already allocated it returns false and nothing changes. Otherwise
   * the process_id is allocated against this process set and returns true.
   */
  boolean setManaged(ProcessId process_id, ProcessInstanceImpl instance) {
    synchronized (CHANGE_LOCK) {
      // Put if absent. If this returns null it means there was no element
      // there before.
      boolean was_set = (process_map.putIfAbsent(process_id, instance) == null);
      if (was_set) {
        instance.preventRemoveLock();
      }
      return was_set;
    }
  }

  /**
   * Sets the instance if the process id is not currently managed. If it is
   * currently managed, returns the instance currently set.
   */
  ProcessInstanceImpl setManagedIfAbsent(
                          ProcessId process_id, ProcessInstanceImpl instance) {
    synchronized (CHANGE_LOCK) {
      ProcessInstanceImpl inst = process_map.putIfAbsent(process_id, instance);
      if (inst == null) {
        inst = instance;
      }
      inst.preventRemoveLock();
      return inst;
    }
  }

  /**
   * Returns the ProcessInstanceImpl for the given process_id, or null if
   * the process isn't being managed.
   */
  ProcessInstanceImpl getInstance(ProcessId process_id) {
    // NOTE, it's important we synchronize here because we don't want to
    //  deal with any inconsistencies in fetching while removing
    synchronized (CHANGE_LOCK) {
      ProcessInstanceImpl inst = process_map.get(process_id);
      if (inst != null) {
        // Set the fetch timestamp so we can't remove recent instances,
        inst.preventRemoveLock();
      }
      return inst;
    }
  }

//  /**
//   * Returns true if the given process is managed by this process set.
//   */
//  boolean isManaged(ProcessId process_id) {
//    synchronized (CHANGE_LOCK) {
//      return process_map.containsKey(process_id);
//    }
//  }

  /**
   * Removes the process from being managed.
   */
  void removeManaged(ProcessId process_id) {
    synchronized (CHANGE_LOCK) {
      process_map.remove(process_id);
    }
  }

  /**
   * Removes all the process from being managed. Returns the number of
   * processes removed.
   */
  int removeAllManaged(Collection<ProcessId> process_ids) {
    int count = 0;
    for (ProcessId process_id : process_ids) {
      ProcessInstanceImpl instance;
      synchronized (CHANGE_LOCK) {
        instance = process_map.get(process_id);
        // Check the instance is disposable,
        if (instance.isDisposable()) {
          process_map.remove(process_id);
          ++count;
        }
      }
    }
    return count;
  }

  /**
   * Removes all the process from being managed that haven't recently been
   * accessed. Returns the number of processes removed.
   */
  int removeAllStaleManaged(Collection<ProcessId> process_ids) {
    int count = 0;
    for (ProcessId process_id : process_ids) {
      ProcessInstanceImpl instance;
      synchronized (CHANGE_LOCK) {
        instance = process_map.get(process_id);
        // If it wasn't recently fetched then we can safely remove it,
        if (instance.isDisposable() &&
            !instance.isPreventRemoveLocked() && instance.isCurrentlyStale()) {
          process_map.remove(process_id);
          ++count;
        }
      }
    }
    return count;
  }

  /**
   * Returns an iterator over all processes currently being managed. This
   * object can be safely accessed concurrently while the process set is being
   * used. However, there is no guarantee if this will show the latest
   * additions to the map.
   */
  Iterator<ProcessId> getAllManaged() {
    return Collections.unmodifiableSet(process_map.keySet()).iterator();
  }


}
