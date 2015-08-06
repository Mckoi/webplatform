/**
 * com.mckoi.process.impl.StateMapImpl  Nov 1, 2012
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

import com.mckoi.data.DataFile;
import com.mckoi.data.PropertySet;
import com.mckoi.process.StateMap;
import java.util.Map.Entry;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of StateMap.
 *
 * @author Tobias Downer
 */

class StateMapImpl implements StateMap {

  private final Map<String, String> java_map;

  private final ReentrantLock modify_lock = new ReentrantLock();

  /**
   * Constructor.
   */
  StateMapImpl() {
    java_map = new HashMap();
  }

  /**
   * Attempts to serialize the content of the state map to the given DataFile
   * object. Note that a lock must be aquired on this map before this method
   * is called.
   */
  boolean serialize(DataFile df) {

    boolean changed = false;

    // We have the lock, so serialize the map,
    PropertySet pset = new PropertySet(df);
    // Write all the keys
    for (String key : java_map.keySet()) {
      String value = java_map.get(key);
      String s_value = pset.getProperty(key);
      if (s_value == null || !s_value.equals(value)) {
        pset.setProperty(key, value);
        changed = true;
      }
    }
    // All the keys written,
    SortedSet<String> s_keys = pset.keySet();
    List<String> to_remove = new ArrayList();
    for (String key : s_keys) {
      // If the internal map doesn't contain the key, remove it,
      if (!java_map.containsKey(key)) {
        to_remove.add(key);
      }
    }
    // Remove keys,
    for (String key : to_remove) {
      pset.setProperty(key, null);
      changed = true;
    }

    return changed;

  }

  /**
   * Deserializes the given DataFile and writes the content of the map from
   * the data. This assumes this state map is newly initialized and the map
   * is empty.
   */
  void deserialize(DataFile df) {

    // make sure the internal state is cleared,
    java_map.clear();
    // Read all the properties from the data file,
    PropertySet pset = new PropertySet(df);
    // For each stored key,
    SortedSet<String> s_keys = pset.keySet();
    for (String key : s_keys) {
      String value = pset.getProperty(key);
      java_map.put(key, value);
    }

  }

  /**
   * Tries to aquire a lock on this map within the given period of
   * milliseconds. If it's unable to then returns false. If successful then
   * returns true and there will be a lock on this map.
   */
  boolean tryLock(int millis_timeout) throws InterruptedException {
    return modify_lock.tryLock(millis_timeout, TimeUnit.MILLISECONDS);
  }

  // ----- StateMap -----

  @Override
  public void lock() {
    modify_lock.lock();
  }

  @Override
  public void unlock() {
    modify_lock.unlock();
  }

  // ----- Map<String, String> -----

  @Override
  public Collection<String> values() {
    // Don't allow clients to change this,
    return Collections.unmodifiableCollection(java_map.values());
  }

  @Override
  public int size() {
    return java_map.size();
  }

  @Override
  public String remove(Object key) {
    modify_lock.lock();
    try {
      return java_map.remove(key);
    }
    finally {
      modify_lock.unlock();
    }
  }

  @Override
  public void putAll(Map<? extends String, ? extends String> m) {
    modify_lock.lock();
    try {
      java_map.putAll(m);
    }
    finally {
      modify_lock.unlock();
    }
  }

  @Override
  public String put(String key, String value) {
    modify_lock.lock();
    try {
      return java_map.put(key, value);
    }
    finally {
      modify_lock.unlock();
    }
  }

  @Override
  public Set<String> keySet() {
    // Don't allow clients to change this,
    return Collections.unmodifiableSet(java_map.keySet());
  }

  @Override
  public boolean isEmpty() {
    return java_map.isEmpty();
  }

  @Override
  public String get(Object key) {
    return java_map.get(key);
  }

  @Override
  public Set<Entry<String, String>> entrySet() {
    // Don't allow clients to change this,
    return Collections.unmodifiableSet(java_map.entrySet());
  }

  @Override
  public boolean containsValue(Object value) {
    return java_map.containsValue(value);
  }

  @Override
  public boolean containsKey(Object key) {
    return java_map.containsKey(key);
  }

  @Override
  public void clear() {
    modify_lock.lock();
    try {
      java_map.clear();
    }
    finally {
      modify_lock.unlock();
    }
  }

}
