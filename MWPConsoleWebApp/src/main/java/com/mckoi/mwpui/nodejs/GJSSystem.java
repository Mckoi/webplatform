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

import com.mckoi.mwpui.CommandWriter;
import com.mckoi.mwpui.EnvironmentVariables;
import com.mckoi.mwpui.ServerCommandContext;
import java.nio.ByteBuffer;

/**
 *
 * @author Tobias Downer
 */
public interface GJSSystem {

  /**
   * Returns a unique identifier string that is the same across all systems
   * that produce compiled code objects that can safely be shared with each
   * other. For example, a Rhino system does not produce compiled code that
   * can be shared with Nashorn. Some JavaScript engine may not permit
   * sharing compiled code across some other boundaries.
   * 
   * @return 
   */
  String getSystemId();

  /**
   * Returns the node exec options arguments list.
   * 
   * @return 
   */
  String[] getExecArgsv();

  /**
   * Returns the node command line (for example; 'node', 'myscript', '-arg')
   * 
   * @return 
   */
  String[] getArgsv();

  /**
   * Returns the GJSProcessSharedState object that contains state shared
   * between the Node internal state and the native code.
   * 
   * @return 
   */
  GJSProcessSharedState getSharedState();

  /**
   * Compiles a code string and produces an engine specific script object that
   * can be executed at any time using the 'executeCode' method. The
   * 'script_filename' and 'first_line_num' arguments are used for stack trace
   * generation.
   * <p>
   * Note that the returned script object may be cached so it should not hold
   * onto any external references (such as to this object).
   * 
   * @param source_code
   * @param script_filename
   * @param first_line_num
   * @return 
   */
  Object compileSourceCode(String source_code,
                                String script_filename, int first_line_num);

  /**
   * Executes the engine specific script object produced by the
   * 'compileSourceCode' method. Returns the result of the executed function
   * as a GJS value.
   * 
   * @param compiled_string
   * @return 
   */
  Object executeCode(Object compiled_string);

  /**
   * Returns a native JavaScript array that contains the values in the given
   * 'arr' argument.
   * 
   * @param arr
   * @return 
   */
  GJSObject asJavaScriptArray(Object[] arr);

  /**
   * Returns a new JavaScript object that's directly backed by a native
   * JavaScript object for this engine.
   * 
   * @return 
   */
  GJSObject newJavaScriptObject();

  /**
   * Returns a new JavaScript object that's directly backed by a native
   * JavaScript object for this engine. The returned object has the [[Class]]
   * property given.
   * 
   * @param class_name
   * @return
   */
  GJSObject newJavaScriptObject(String class_name);

  /**
   * Returns the ByteBuffer of the external array data object associated with
   * this object. Returns null if the object doesn't have an array data
   * ByteBuffer associated with it.
   * 
   * @param buffer
   * @return 
   */
  ByteBuffer getExternalArrayDataOf(GJSObject buffer);

  /**
   * Allocates a ByteBuffer of 'alloc_size' size against the given object and
   * returns the ByteBuffer created.
   * 
   * @param source
   * @param alloc_size
   * @return 
   */
  ByteBuffer allocateExternalArrayDataOf(GJSObject source, int alloc_size);

  // -----

  /**
   * Returns an alternative stack trace format of the given exception if one
   * exists, or null if there's no alternative.
   * 
   * @param ex
   * @return 
   */
  String getAlternativeStackTraceOf(GJavaScriptException ex);

  /**
   * Gets the global object.
   * 
   * @return 
   */
  GJSObject getGlobalObject();

  /**
   * Calls the function represented by the 'function' parameter with the
   * given arguments (arguments must be allowed GJS value types). The
   * 'this_object' member represents the 'this' value when the function is
   * evaluated.
   * 
   * @param this_object
   * @param function
   * @param args
   * @return 
   */
  Object callFunction(
          GJSObject this_object, GJSObject function, Object[] args);

  /**
   * Sets up the JavaScript engine context (this will typically lock various
   * variables to the current thread).
   * 
   * @param ctx
   * @param vars
   * @param out
   * @param node_source_loader
   * @param shared_process_state 
   */
  void setupContext(ServerCommandContext ctx, EnvironmentVariables vars,
                    CommandWriter out, GJSNodeSourceLoader node_source_loader,
                    GJSProcessSharedState shared_process_state);

  /**
   * Release the JavaScript engine context.
   */
  void releaseContext();

}
