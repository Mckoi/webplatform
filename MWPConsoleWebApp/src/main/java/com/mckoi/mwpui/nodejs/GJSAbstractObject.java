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

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An abstract implementation of GJSObject with various convenience methods.
 *
 * @author Tobias Downer
 */
public class GJSAbstractObject implements GJSObject {

  /**
   * Map of all members in this object.
   */
  private Map<String, Object> members;

  /**
   * Map of all id members in this object.
   */
  private Map<Integer, Object> id_members;

  /**
   * Internal native object.
   */
  private Object internal_native_ob;

  /**
   * Internal prototype object.
   */
  private GJSObject prototype;

  /**
   * Uses Java reflection to create a member function that invokes a Java
   * method in this object. The Java method invoked must have the function
   * prototype of (Object thiz, Object[] args).
   * 
   * @param function_name 
   * @param dest 
   * @throws java.lang.NoSuchMethodException 
   */
  public void setWrappedFunction(String function_name, GJSObject dest)
                                              throws NoSuchMethodException {

    GJSRuntime.setWrappedFunction(this, function_name, dest);

  }

  /**
   * Uses Java reflection to create a member function that invokes a Java
   * method in this object. The Java method invoked must have the function
   * prototype of (Object thiz, Object[] args).
   * 
   * @param function_name 
   * @throws java.lang.NoSuchMethodException 
   */
  public void setWrappedFunction(String function_name)
                                              throws NoSuchMethodException {
    setWrappedFunction(function_name, this);
  }

  // -----
  @Override
  public String getClassName() {
    return null;
  }

  @Override
  public boolean isFunction() {
    return false;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public Object call(Object thiz, Object... args) {
    // GJSObject is not a function by default,
    throw new GJavaScriptException("not a function");
  }

  @Override
  public Object newObject(Object... args) {
    // Default behaviour is to construct a native object,
    return GJSRuntime.system().newJavaScriptObject();
  }

  @Override
  public GJSObject getPrototype() {
    if (prototype == null) {
      prototype = GJSRuntime.system().newJavaScriptObject();
    }
    return prototype;
  }

  @Override
  public boolean hasPrototype() {
    return prototype != null;
  }
  
  @Override
  public boolean hasMember(String name) {
    if (members == null) {
      return false;
    }
    return members.containsKey(name);
  }

  @Override
  public Object getMember(String name) {
    if (members != null) {
      if (members.containsKey(name)) {
        return members.get(name);
      }
    }
    return GJSStatics.UNDEFINED;
  }

  @Override
  public void setMember(String name, Object value) {
    if (members == null) {
      members = new HashMap<>();
    }
    members.put(name, value);
  }

  @Override
  public void setROHiddenMember(String name, Object value) {
    setMember(name, value);
  }

  @Override
  public void removeMember(String name) {
    if (members != null) {
      members.remove(name);
    }
  }

  @Override
  public Set<String> keySet() {
    return members == null ? Collections.EMPTY_SET : members.keySet();
  }

  @Override
  public boolean hasSlot(int index) {
    if (id_members == null) {
      return false;
    }
    return id_members.containsKey(index);
  }

  @Override
  public Object getSlot(int index) {
    if (id_members != null) {
      if (id_members.containsKey(index)) {
        return id_members.get(index);
      }
    }
    return GJSStatics.UNDEFINED;
  }

  @Override
  public void setSlot(int index, Object value) {
    if (id_members == null) {
      id_members = new HashMap<>();
    }
    id_members.put(index, value);
  }

  @Override
  public void internalSetNative(Object native_ob) {
    this.internal_native_ob = native_ob;
  }

  @Override
  public Object internalGetNative() {
    return internal_native_ob;
  }

  @Override
  public void dump(PrintStream out) {
    System.out.println("  internal_native_ob = " + internal_native_ob);
    System.out.println("  internal_native_ob = " + internal_native_ob.getClass());
    System.out.println("  members = " + members);
    System.out.println("  id_members = " + id_members);
    System.out.println("  prototype = " + prototype);
  }

}
