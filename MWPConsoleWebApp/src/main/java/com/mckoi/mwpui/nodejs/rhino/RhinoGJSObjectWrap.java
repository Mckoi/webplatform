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

package com.mckoi.mwpui.nodejs.rhino;

import com.mckoi.mwpui.nodejs.GJSObject;
import java.io.PrintStream;
import java.util.Set;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * An implementation of GJSObject that wraps a native Rhino Scriptable. This
 * object handles Rhino's Object, Array and Function types.
 * <p>
 * The simplest way to interface with JavaScript native objects is to wrap
 * them with this to use in the GJS* classes.
 *
 * @author Tobias Downer
 */
public class RhinoGJSObjectWrap implements GJSObject {

  protected final RhinoJSSystem system;
  protected final Scriptable native_ob;

  public RhinoGJSObjectWrap(RhinoJSSystem system, Scriptable native_ob) {
    this.system = system;
    this.native_ob = native_ob;
  }
  
  @Override
  public String getClassName() {
    return native_ob.getClassName();
  }

  @Override
  public boolean isFunction() {
    return native_ob instanceof BaseFunction;
  }

  @Override
  public boolean isArray() {
    return ScriptRuntime.isArrayObject(native_ob);
  }

  @Override
  public Object call(Object thiz, Object... args) {
    try {
      BaseFunction fun = (BaseFunction) native_ob;
      Context cx = system.getContext();
      Object v = system.wrapAsGJS(
                    fun.call(cx, system.getGlobalNative(),
                             (Scriptable) system.wrapAsRhino(thiz),
                             system.wrapArgsAsRhino(args)));
      return v;
    }
    catch (RhinoException ex) {
      throw system.wrappedGJSException(ex);
    }
  }

  @Override
  public Object newObject(Object... args) {
    try {
      BaseFunction fun = (BaseFunction) native_ob;
      Context cx = system.getContext();
      Object v = system.wrapAsGJS(
                    fun.construct(cx, system.getGlobalNative(),
                                      system.wrapArgsAsRhino(args)));
      return v;
    }
    catch (RhinoException ex) {
      throw system.wrappedGJSException(ex);
    }
  }

  @Override
  public GJSObject getPrototype() {
    Object proto = native_ob.get("prototype", native_ob);
    if (proto != null && proto instanceof Scriptable) {
      return (GJSObject) system.wrapAsGJS(proto);
    }
    else {
      throw new IllegalStateException("Native 'prototype' object is invalid");
    }
  }

  @Override
  public boolean hasPrototype() {
    return native_ob.has("prototype", native_ob);
  }

  @Override
  public boolean hasMember(String name) {
    return native_ob.has(name, native_ob);
  }

  @Override
  public Object getMember(String name) {
    return system.wrapAsGJS(native_ob.get(name, native_ob));
  }

  @Override
  public void setMember(String name, Object value) {
    native_ob.put(name, native_ob, system.wrapAsRhino(value));
  }

  @Override
  public void setROHiddenMember(String name, Object value) {
    if (native_ob instanceof ScriptableObject) {
      ScriptableObject so = (ScriptableObject) native_ob;
      so.put(name, native_ob, system.wrapAsRhino(value));
      int ro_attr = ScriptableObject.DONTENUM |
                    ScriptableObject.READONLY |
                    ScriptableObject.PERMANENT;
      so.defineProperty(name, system.wrapAsRhino(value), ro_attr);
    }
    else {
      setMember(name, value);
    }
  }

  @Override
  public void removeMember(String name) {
    native_ob.delete(name);
  }

  @Override
  public boolean hasSlot(int index) {
    return native_ob.has(index, native_ob);
  }

  @Override
  public Object getSlot(int index) {
    return system.wrapAsGJS(native_ob.get(index, native_ob));
  }

  @Override
  public void setSlot(int index, Object value) {
    native_ob.put(index, native_ob, system.wrapAsRhino(value));
  }

  @Override
  public Set<String> keySet() {
    return new RhinoIdSet(native_ob.getIds());
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
    out.println("[RhinoGJSObjectWrap dump PENDING]");
  }
  
}
