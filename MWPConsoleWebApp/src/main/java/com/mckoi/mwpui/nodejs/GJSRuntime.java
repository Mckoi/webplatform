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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime function calls.
 *
 * @author Tobias Downer
 */
public class GJSRuntime {

  /**
   * NOTE: Thread-safe map.
   */
  private static final Map<Long, GJSSystem> system_map =
                                  Collections.synchronizedMap(new HashMap());

  /**
   * The method prototype for wrapped functions.
   */
  private static final Class[] WRAPPED_FUNCTION_PARAMETER_TYPES =
                              new Class[] { Object.class, Object[].class };

  /**
   * Associates the given GJSystem object with the current thread.
   * 
   * @param system 
   */
  public static void registerSystem(GJSSystem system) {
    long thread_id = Thread.currentThread().getId();
    if (system_map.put(thread_id, system) != null) {
      throw new IllegalStateException("GJSSystem already registered with thread");
    }
  }
  
  /**
   * Removes the association of the GJSSystem registered on the current thread.
   */
  public static void deregisterSystem() {
    long thread_id = Thread.currentThread().getId();
    if (system_map.remove(thread_id) == null) {
      throw new IllegalStateException("No GJSSystem registered with thread");
    }
  }
  

  /**
   * Returns the GJSSystem object.
   * 
   * @return 
   */
  public static GJSSystem system() {
    // PENDING; return the GJSSystem for this thread,
    long thread_id = Thread.currentThread().getId();
    return system_map.get(thread_id);
  }
  
  /**
   * Converts a passed in Number object to a 32 bit integer.
   * 
   * @param number
   * @return 
   */
  public static int toInt32(Object number) {
    if (number instanceof Number) {
      return ((Number) number).intValue();
    }
    throw new GJavaScriptException("Unable to convert number of a 32bit int");
  }

  /**
   * Converts a passed in Number object to a 64 bit integer.
   * 
   * @param number
   * @return 
   */
  public static long toInt64(Object number) {
    if (number instanceof Number) {
      return ((Number) number).longValue();
    }
    throw new GJavaScriptException("Unable to convert number of a 64bit int");
  }

  /**
   * Uses Java reflection to create a member function that invokes a Java
   * method in this (java_src) object. The Java method invoked must have the
   * function prototype of (Object thiz, Object[] args).
   * 
   * @param java_src either a Class (for static calls only) or an object.
   * @param pre
   * @param function_name 
   * @param dest 
   */
  public static void setWrappedFunction(
            final Object java_src, String pre,
            final String function_name, GJSObject dest) {

    final Class<?> clazz = java_src instanceof Class ?
                              (Class) java_src : java_src.getClass();
    
    String gen_function_name = pre + "_" + function_name;
    
    // Make a function GJSObject to be called,
    GJSObject function_call = new GJSAbstractFunction(gen_function_name) {
      private Method method = null;
      private Object this_java_ob = null;
      @Override
      public Object call(Object thiz, Object... args) {
        try {
          
          if (method == null) {
            // Use Java reflection to determine the method to call,
            method = clazz.getMethod(
                            function_name, WRAPPED_FUNCTION_PARAMETER_TYPES);
            // It can be either a static or non-static method,
            if (Modifier.isStatic(method.getModifiers())) {
              this_java_ob = null;
            }
            else {
              if (java_src == null) {
                throw new RuntimeException("Expecting static method");
              }
              this_java_ob = java_src;
            }
          }

          // When it's called it invokes the Java method via reflection,
          return method.invoke(this_java_ob, new Object[] { thiz, args });

        }
        catch (InvocationTargetException ex) {
          Throwable target = ex.getTargetException();
          if (target instanceof RuntimeException) {
            throw (RuntimeException) target;
          }
          else if (target instanceof Error) {
            throw (Error) target;
          }
          // For declared exceptions,
          throw new GJavaScriptException(ex);
        }
        catch ( IllegalAccessException |
                IllegalArgumentException |
                NoSuchMethodException ex ) {
          throw new GJavaScriptException(ex);
        }
      }
    };

    // Set the member,
    dest.setMember(gen_function_name, function_call);

  }
  

  /**
   * Uses Java reflection to create a member function that invokes a Java
   * method in this (thiz) object. The Java method invoked must have the
   * function prototype of (Object thiz, Object[] args).
   * 
   * @param thiz
   * @param function_name 
   * @param dest 
   * @throws java.lang.NoSuchMethodException 
   */
  public static void setWrappedFunction(
          Object thiz, String function_name, GJSObject dest)
                                              throws NoSuchMethodException {

    // Use Java reflection to determine the method to call,
    final Method method = thiz.getClass().getMethod(
                            function_name, WRAPPED_FUNCTION_PARAMETER_TYPES);
    // It can be either a static or non-static method,
    final Object this_java_ob;
    if (Modifier.isStatic(method.getModifiers())) {
      this_java_ob = null;
    }
    else {
      this_java_ob = thiz;
    }
    // Make a function GJSObject to be called,
    GJSObject function_call = new GJSAbstractFunction(function_name) {
      @Override
      public Object call(Object thiz, Object... args) {
        try {
          // When it's called it invokes the Java method via reflection,
          return method.invoke(this_java_ob, new Object[] { thiz, args });
        }
        catch (InvocationTargetException ex) {
          Throwable target = ex.getTargetException();
          if (target instanceof RuntimeException) {
            throw (RuntimeException) target;
          }
          else if (target instanceof Error) {
            throw (Error) target;
          }
          // For declared exceptions,
          throw new GJavaScriptException(ex);
        }
        catch ( IllegalAccessException |
                IllegalArgumentException ex) {
          throw new GJavaScriptException(ex);
        }
      }
    };

    // Set the member,
    dest.setMember(function_name, function_call);

  }


}
