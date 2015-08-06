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
package com.mckoi.mwpui.nodejs.nashorn;

import com.mckoi.mwpui.nodejs.GJSObject;
import java.io.PrintStream;
import java.util.Set;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.ScriptUtils;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * An implementation of GJSObject that wraps a native Nashorn JSObject. This
 * object handles Nashorn's Object, Array and Function types.
 * <p>
 * The simplest way to interface with JavaScript native objects is to wrap
 * them with this to use in the GJS* classes.
 *
 * @author Tobias Downer
 */
public class NashornGJSObjectWrap implements GJSObject {

  protected final NashornJSSystem system;
  protected final JSObject native_ob;

  public NashornGJSObjectWrap(NashornJSSystem system, JSObject native_ob) {
    this.system = system;
    this.native_ob = native_ob;
  }

  @Override
  public String getClassName() {
    return native_ob.getClassName();
  }

  @Override
  public boolean isFunction() {
//    System.out.println("(1) isFunction called!");
    return native_ob.isFunction();
  }

  @Override
  public boolean isArray() {
    return native_ob.isArray();
  }

  @Override
  public Object call(Object thiz, Object... args) {
    try {
      Object result = native_ob.call(system.wrapAsNashorn(thiz),
                                     system.wrapArgsAsNashorn(args));
      return system.wrapAsGJS(result);
    }
    catch (NashornException ex) {
      throw system.wrappedGJSException(ex);
    }
  }

  @Override
  public Object newObject(Object... args) {
    try {
      Object result = native_ob.newObject(system.wrapArgsAsNashorn(args));
      return system.wrapAsGJS(result);
    }
    catch (NashornException ex) {
      throw system.wrappedGJSException(ex);
    }
  }

  @Override
  public GJSObject getPrototype() {
//    System.out.println("^^^ getPrototype");
    Object proto = native_ob.getMember("prototype");
    if (proto != null) {
      JSObject jsob = null;
      if (proto instanceof ScriptObject) {
        jsob = ScriptUtils.wrap((ScriptObject) proto);
      }
      if (proto instanceof JSObject) {
        jsob = (JSObject) proto;
      }
      if (jsob != null) {
        return (GJSObject) system.wrapAsGJS(jsob);
      }
    }
    throw new IllegalStateException("Native 'prototype' object is invalid");

  }

  @Override
  public boolean hasPrototype() {
    return native_ob.hasMember("prototype");
  }

  @Override
  public boolean hasMember(String name) {
    boolean result = native_ob.hasMember(name);
//    System.out.println("hasMember: " + name + " = " + result);
    return result;
  }

  @Override
  public Object getMember(String name) {
    Object ob = system.wrapAsGJS(native_ob.getMember(name));
//    System.out.println("(1) getMember: " + name + " = " + ob);
    return ob;
  }

  @Override
  public void setMember(String name, Object value) {
    native_ob.setMember(name, system.wrapAsNashorn(value));
  }

  @Override
  public void setROHiddenMember(String name, Object value) {
    system.setROHiddenMember(native_ob, name, system.wrapAsNashorn(value));
  }

  @Override
  public void removeMember(String name) {
    native_ob.removeMember(name);
  }

  @Override
  public boolean hasSlot(int index) {
    return native_ob.hasSlot(index);
  }

  @Override
  public Object getSlot(int index) {
    return system.wrapAsGJS(native_ob.getSlot(index));
  }

  @Override
  public void setSlot(int index, Object value) {
    native_ob.setSlot(index, system.wrapAsNashorn(value));
  }

  @Override
  public Set<String> keySet() {
    return native_ob.keySet();
  }

  @Override
  public void internalSetNative(Object native_ob) {
    // This should not be called,
    throw new IllegalStateException();
  }

  @Override
  public Object internalGetNative() {
    return native_ob;
  }

  @Override
  public void dump(PrintStream out) {
    out.println("[NashornGJSObjectWrap dump PENDING]");
  }
  
}
