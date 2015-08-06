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

package com.mckoi.mwpui.nodejs;

import com.mckoi.webplatform.FileRepository;
import java.io.PrintStream;
import java.util.Set;

/**
 * The base class for a generic representation of a JavaScript object to
 * be translated into different JavaScript engines.
 *
 * @author Tobias Downer
 */
public interface GJSObject {

  /**
   * Returns the [[Class]] internal variable string, or null for the default.
   * 
   * @return 
   */
  public String getClassName();

  /**
   * Returns true if this is a function. If it is a function then the 'call'
   * method must be implemented to handle the function operation.
   * 
   * @return 
   */
  public boolean isFunction();

  /**
   * Returns true if this object is an array.
   * 
   * @return 
   */
  public boolean isArray();

  /**
   * If 'isFunction' returns true then this object represents a callable
   * function.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object call(Object thiz, Object... args);
  
  /**
   * This is called when the JavaScript runtime attempts to construct this
   * object (eg. new myfun(args)).
   * 
   * @param args
   * @return 
   */
  public Object newObject(Object... args);

  /**
   * Returns the JavaScript 'prototype' object.
   * 
   * @return 
   */
  public GJSObject getPrototype();

  /**
   * Returns true if this GJSObject defined prototype getters/setters.
   * 
   * @return 
   */
  public boolean hasPrototype();

  /**
   * Returns true if this object defines a member with the given name.
   * 
   * @param name
   * @return 
   */
  public boolean hasMember(String name);

  /**
   * Returns a Java object that represents the given member.
   * 
   * @param name
   * @return either a GJSObject, Number, Boolean, String, or Undefined.
   */
  public Object getMember(String name);

  /**
   * Sets the member value.
   * 
   * @param name
   * @param value either a GJSObject, Number, Boolean, String, or Undefined.
   */
  public void setMember(String name, Object value);

  /**
   * Sets a read-only hidden member on this object. Note that some
   * implementations may not fully implement the read-only and hidden property
   * assignment.
   * 
   * @param name
   * @param value
   */
  public void setROHiddenMember(String name, Object value);

  /**
   * Removes the member value.
   * 
   * @param name 
   */
  public void removeMember(String name);
  
  /**
   * Returns true if this object has the given index slot defined.
   * 
   * @param index
   * @return 
   */
  public boolean hasSlot(int index);
  
  /**
   * Returns the object in the given slot, if any.
   * 
   * @param index
   * @return either a GJSObject, Number, Boolean, String, or Undefined.
   */
  public Object getSlot(int index);
  
  /**
   * Sets the object in the given slot.
   * 
   * @param index
   * @param value either a GJSObject, Number, Boolean, String, or Undefined.
   */
  public void setSlot(int index, Object value);

  /**
   * Returns the set of all member names.
   * 
   * @return 
   */
  public Set<String> keySet();

  // -----

  /**
   * Sets the native object used to represent this GJSObject in the script
   * engine. This should only be used by the script engine implementations.
   * 
   * @param native_ob
   */
  public void internalSetNative(Object native_ob);

  /**
   * Returns the native object used to represent this GJSObject, or null if
   * there doesn't exist a native representation.
   * 
   * @return 
   */
  public Object internalGetNative();

  public void dump(PrintStream out);

}
