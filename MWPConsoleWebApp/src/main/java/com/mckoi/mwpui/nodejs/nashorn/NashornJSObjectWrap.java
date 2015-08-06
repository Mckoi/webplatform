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
import com.mckoi.mwpui.nodejs.GJavaScriptException;
import java.util.Collection;
import java.util.Set;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;

/**
 * An implementation of JSObject that wraps a GJSObject.
 *
 * @author Tobias Downer
 */
public class NashornJSObjectWrap implements JSObject {

  private final NashornJSSystem system;
  private final GJSObject gjs_ob;

  private GJSObject proto;
  
  public NashornJSObjectWrap(NashornJSSystem system, GJSObject gjs_ob) {
    this.system = system;
    this.gjs_ob = gjs_ob;
  }

  GJSObject internalGetGJSObject() {
    return gjs_ob;
  }

  @Override
  public Object call(Object o, Object... os) {
//    System.out.println("(2) call called!");
////    System.out.println("(2)    this = " + o);
////    System.out.println("(2)    args = " + Arrays.asList(os));
    try {
      return system.wrapAsNashorn( gjs_ob.call(
                    system.wrapAsGJS(o), system.wrapArgsAsGJS(os)));
    }
    catch (GJavaScriptException ex) {
      throw system.wrappedNashornException(ex);
    }
  }

  @Override
  public Object newObject(Object... os) {
//    System.out.println("(2) newObject called!");
////    System.out.println("(2)      args = " + Arrays.asList(os));
    try {
      return system.wrapAsNashorn(gjs_ob.newObject(system.wrapArgsAsGJS(os)));
    }
    catch (GJavaScriptException ex) {
      throw system.wrappedNashornException(ex);
    }
  }

  @Override
  public Object eval(String string) {
//    System.out.println("(2) eval called!");
    throw new UnsupportedOperationException();
  }

  @Override
  public Object getMember(String name) {
    if (name.equals("hasOwnProperty")) {
      return new HasOwnPropertyFunction();
    }
    else if (name.equals("prototype")) {
      if (proto == null) {
        proto = system.newJavaScriptObject();
      }
      return system.wrapAsNashorn(proto);
    }

    Object gjs_val = gjs_ob.getMember(name);
    Object ob = system.wrapAsNashorn(gjs_val);

//    System.out.println("(2) getMember: " + name);
////    System.out.println("(2)            " + name + " = " + gjs_val);
////    System.out.println("(2)     this = " + gjs_ob);

    return ob;
  }

  @Override
  public Object getSlot(int i) {
    return system.wrapAsNashorn(gjs_ob.getSlot(i));
  }

  @Override
  public boolean hasMember(String name) {
    boolean result = gjs_ob.hasMember(name);
//    System.out.println("(2) hasMember: " + name + " = " + result);
    return result;
  }

  @Override
  public boolean hasSlot(int i) {
    return gjs_ob.hasSlot(i);
  }

  @Override
  public void removeMember(String string) {
    gjs_ob.removeMember(string);
  }

  @Override
  public void setMember(String string, Object o) {
    gjs_ob.setMember(string, system.wrapAsGJS(o));
  }

  @Override
  public void setSlot(int i, Object o) {
    gjs_ob.setSlot(i, system.wrapAsGJS(o));
  }

  @Override
  public Set<String> keySet() {
//    System.out.println("(2) keySet called!");
    return gjs_ob.keySet();
  }

  @Override
  public Collection<Object> values() {
//    System.out.println("(2) values called!");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInstance(Object o) {
//    System.out.println("(2) isInstance called!");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInstanceOf(Object o) {
//    System.out.println("(2) isInstanceOf called!");
    throw new UnsupportedOperationException();
  }

  @Override
  public String getClassName() {
//    System.out.println("(2) getClassName called!");
    return gjs_ob.getClassName();
  }

  @Override
  public boolean isFunction() {
//    System.out.println("(2) isFunction called!");
    return gjs_ob.isFunction();
  }

  @Override
  public boolean isStrictFunction() {
//    System.out.println("(2) isStrictFunction called!");
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isArray() {
//    System.out.println("(2) isArray called!");
    return gjs_ob.isArray();
  }

  @Override
  public double toNumber() {
//    System.out.println("(2) toNumber called!");
    throw new UnsupportedOperationException();
  }

  // -----
  
  public class HasOwnPropertyFunction extends AbstractJSObject {

    @Override
    public boolean isFunction() {
      return super.isFunction(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Object call(Object thiz, Object... args) {
      String name = args[0].toString();
      return gjs_ob.hasMember(name);
    }
    
  }

  @Override
  public String toString() {
    return "NashornJSObjectWrap{" + gjs_ob.toString() + '}';
  }

  

}
