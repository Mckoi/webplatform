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
package com.mckoi.webplatform.nashorn;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * A JavaScript instance that has a global object that's isolated from other
 * instances within the Nashorn engine. Changes made to the global in this
 * instance will not be seen in another other instances, however, internally
 * they share the same context.
 * <p>
 * THREAD-SAFETY: This object is not thread-safe, however threads with their
 *   own NashornInstanceGlobal should not cause corruption (unless there's
 *   MT design issues within the Nashorn API).
 *
 * @author Tobias Downer
 */
public interface NashornInstanceGlobal {
  
  /**
   * Returns the Global as a JSObject.
   * 
   * @return 
   */
  JSObject getGlobal();
 
  /**
   * Wraps a ScriptObject as a ScriptObjectMirror using this instance global.
   * 
   * @param script_object
   * @return 
   */
  ScriptObjectMirror wrap(ScriptObject script_object);

  /**
   * Unwraps a JSObject that was wrapped with this global.
   * 
   * @param js_object
   * @return 
   */
  Object unwrap(JSObject js_object);

  /**
   * Compiles the given source code and returns an Object that can later be
   * used to execute the script. The returned object can safely be stored
   * in a cache.
   * 
   * @param source_code
   * @param file_name
   * @param first_line_num
   * @return 
   */
  Object compileSourceCode(
                  String source_code, String file_name, int first_line_num);

  /**
   * Executes compiled code previously returned from the 'compileSourceCode'
   * method.
   * 
   * @param compiled_script an object returned from 'compileSourceCode'
   * @return 
   */
  Object executeCode(Object compiled_script);

  /**
   * Returns the given list of arguments (as Nashorn types) to a native array.
   * 
   * @param arr
   * @return 
   */
  JSObject asJavaScriptArray(Object[] arr);

  /**
   * Returns a JSObject representing an empty object. This is equivalent to
   * the following JavaScript code;
   * <code>
   *   return {};
   * </code>
   * 
   * @return 
   */
  JSObject newJavaScriptObject();

  /**
   * Returns a JSObject representing an empty object with the [[Class]]
   * value returning the given class name string.
   * 
   * @param class_name
   * @return 
   */
  JSObject newJavaScriptObject(String class_name);

}
