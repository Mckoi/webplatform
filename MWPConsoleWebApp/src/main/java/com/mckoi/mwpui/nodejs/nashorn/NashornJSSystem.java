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

import com.mckoi.mwpui.CommandWriter;
import com.mckoi.mwpui.EnvironmentVariables;
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.mwpui.nodejs.AbstractGJSSystem;
import com.mckoi.mwpui.nodejs.GJSNodeSourceLoader;
import com.mckoi.mwpui.nodejs.GJSObject;
import com.mckoi.mwpui.nodejs.GJSProcessSharedState;
import com.mckoi.mwpui.nodejs.GJSRuntime;
import com.mckoi.mwpui.nodejs.GJSStatics;
import com.mckoi.mwpui.nodejs.GJavaScriptException;
import com.mckoi.webplatform.nashorn.NashornInstanceGlobal;
import com.mckoi.webplatform.nashorn.NashornInstanceGlobalFactory;
import java.nio.ByteBuffer;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * An implementation of GJSSystem that uses the Nashorn JavaScript engine to
 * support the NodeJS native libraries.
 * <p>
 * NOTE: Only works on java 1.8u40 and greater.
 *
 * @author Tobias Downer
 */
public class NashornJSSystem extends AbstractGJSSystem {

  /**
   * Property name used to put the proxy of a ByteBuffer on a JavaScript object.
   */
  private static final String BYTEBUFFER_OBJECT_PROPERTY_NAME = "_javabb";
  
  /**
   * Internal Nashorn Context object.
   */
  private static final NashornInstanceGlobalFactory nashorn;

  static {
    nashorn = new NashornInstanceGlobalFactory();
    nashorn.init();
  }




//  private final NashornScriptEngine exec_engine;
  
  private GJSProcessSharedState shared_state;

  private final NashornInstanceGlobal instance_global;
  private final GJSObject global_object;

  public NashornJSSystem(String[] exec_args_v_arr, String[] args_v_arr) {
    super(exec_args_v_arr, args_v_arr);
    // Create a new instance global for this js system,
    instance_global = nashorn.createInstanceGlobal();
    global_object = getGJSFromNative(instance_global.getGlobal());
  }

  @Override
  public String getSystemId() {
    return "nashorn";
  }

  @Override
  public GJSProcessSharedState getSharedState() {
    return shared_state;
  }

  @Override
  public Object compileSourceCode(
              String source_code, String script_filename, int first_line_num) {

    return instance_global.compileSourceCode(
                                source_code, script_filename, first_line_num);
    
  }

  @Override
  public Object executeCode(Object compiled_script) {

    Object result = instance_global.executeCode(compiled_script);
    return wrapAsGJS(result);
    
  }

  @Override
  public GJSObject asJavaScriptArray(Object[] arr) {

    JSObject native_array =
                      instance_global.asJavaScriptArray(wrapArgsAsNashorn(arr));
    return getGJSFromNative(native_array);

  }

  @Override
  public GJSObject newJavaScriptObject() {

    JSObject native_empty_object = instance_global.newJavaScriptObject();
    return getGJSFromNative(native_empty_object);

  }

  @Override
  public GJSObject newJavaScriptObject(String class_name) {

    JSObject native_empty_object =
                            instance_global.newJavaScriptObject(class_name);
    return getGJSFromNative(native_empty_object);

  }

  @Override
  public ByteBuffer getExternalArrayDataOf(GJSObject source) {

    ScriptObjectMirror nashorn_ob =
                            (ScriptObjectMirror) source.internalGetNative();
    // We use the unwrapped nashorn object as a key,
    ScriptObject script_ob = (ScriptObject) instance_global.unwrap(nashorn_ob);

    Object value = script_ob.get(BYTEBUFFER_OBJECT_PROPERTY_NAME);
    if (value == null || Undefined.getUndefined().equals(value)) {
      return null;
    }
    return (ByteBuffer) value;

  }

  @Override
  public ByteBuffer allocateExternalArrayDataOf(
                                            GJSObject source, int alloc_size) {

    ScriptObjectMirror nashorn_ob =
                            (ScriptObjectMirror) source.internalGetNative();
    // Allocate the buffer,
    ByteBuffer bb = ByteBuffer.allocate(alloc_size);
    // We use the unwrapped nashorn object as a key,
    ScriptObject script_ob = (ScriptObject) instance_global.unwrap(nashorn_ob);

    // The ByteBuffer property should be read only,
    int ro_flags = Property.NOT_WRITABLE |
                   Property.NOT_ENUMERABLE |
                   Property.NOT_CONFIGURABLE;

    script_ob.addOwnProperty(BYTEBUFFER_OBJECT_PROPERTY_NAME, ro_flags, bb);
    nashorn_ob.setIndexedPropertiesToExternalArrayData(bb);
    return bb;
  }

  @Override
  public String getAlternativeStackTraceOf(GJavaScriptException ex) {
    NashornException nex = (NashornException) ex.internalGetException();
    if (nex != null) {
      StringBuilder buf = new StringBuilder();
      StackTraceElement[] stack_trace = nex.getStackTrace();
      boolean first = true;
      for (StackTraceElement ste : stack_trace) {
        if (ECMAErrors.isScriptFrame(ste)) {
          if (!first) {
            buf.append("\n");
          }
          
          // Extract the method name,
          // NOTE: This could change and will need updating if it does,

          String ANON_PREFIX = CompilerConstants.ANON_FUNCTION_PREFIX.symbolName();
          String encoded_method_name = ste.getMethodName();
          String js_name_to_report = "";
          int delim = encoded_method_name.lastIndexOf("$");
          if (delim >= 0) {
            String js_method = encoded_method_name.substring(delim + 1);
            if (!js_method.startsWith(ANON_PREFIX) && js_method.length() > 0) {
              js_name_to_report = js_method + " ";
            }
          }
          
          buf.append("\tat ");
          buf.append(js_name_to_report);
          buf.append("(");
          buf.append(ste.getFileName());
          buf.append(':');
          buf.append(ste.getLineNumber());
          buf.append(")");
          first = false;
        }
      }
      
      return buf.toString();
    }
    return null;
  }

  @Override
  public GJSObject getGlobalObject() {
    return this.global_object;
  }

  @Override
  public Object callFunction(
                    GJSObject this_object, GJSObject function, Object[] args) {
    return function.call(this_object, args);
  }

  @Override
  public void setupContext(
                  ServerCommandContext ctx, EnvironmentVariables vars,
                  CommandWriter out, GJSNodeSourceLoader node_source_loader,
                  GJSProcessSharedState shared_process_state) {

    shared_process_state.setNodeSourceLoader(node_source_loader);
    shared_process_state.setServerCommandContext(ctx);
    shared_process_state.setEnv(vars);
    shared_process_state.setCommandWriter(out);

    this.shared_state = shared_process_state;
    GJSRuntime.registerSystem(this);
    
  }

  @Override
  public void releaseContext() {
    GJSRuntime.deregisterSystem();
  }

  // -----

  private JSObject convertGJSToNashorn(GJSObject gjs_object) {

    // Assert,
    if (gjs_object instanceof NashornGJSObjectWrap) {
      throw new IllegalStateException();
    }

    // Return it wrapped,
    NashornJSObjectWrap js_ob = new NashornJSObjectWrap(this, gjs_object);
    return js_ob;

  }

  private GJSObject getGJSFromNative(JSObject native_ob) {
    // If it's already wrapped,
    if (native_ob instanceof NashornJSObjectWrap) {
      return ((NashornJSObjectWrap) native_ob).internalGetGJSObject();
    }
    return new NashornGJSObjectWrap(this, native_ob);
  }
  
  /**
   * Wraps the given Nashorn Object as a GJSObject.
   * 
   * @param js_object
   * @return 
   */
  final Object wrapAsGJS(Object nashorn_value) {
    if (nashorn_value == null) {
      return null;
    }
    else if (nashorn_value == Undefined.getUndefined() ||
             nashorn_value == Undefined.getEmpty()) {
      return GJSStatics.UNDEFINED;
    }
    else if (nashorn_value instanceof CharSequence) {
      return nashorn_value.toString();
    }
    else if (nashorn_value instanceof Number ||
             nashorn_value instanceof Boolean) {
      return nashorn_value;
    }
    // Wrap ScriptObject,
    if (nashorn_value instanceof ScriptObject) {
      nashorn_value = instance_global.wrap((ScriptObject) nashorn_value);
    }
    if (nashorn_value instanceof JSObject) {
      JSObject nashorn_object = (JSObject) nashorn_value;
      GJSObject gjs_ob = getGJSFromNative(nashorn_object);
      if (gjs_ob == null) {
        gjs_ob = new NashornGJSObjectWrap(this, nashorn_object);
      }
      return gjs_ob;
    }
    else if (nashorn_value instanceof NashornException) {
      return wrappedGJSException((NashornException) nashorn_value);
    }
    else {
      return nashorn_value;
//      throw new GJavaScriptException("Unable to convert Nashorn value: " +
//                                                  (nashorn_value.getClass()));
    }

  }

  final Object[] wrapArgsAsGJS(Object[] nashorn_args) {
    if (nashorn_args.length == 0) {
      return nashorn_args;
    }
    int len = nashorn_args.length;
    Object[] arg2 = new Object[len];
    for (int i = 0; i < len; ++i) {
      arg2[i] = wrapAsGJS(nashorn_args[i]);
    }
    return arg2;
  }  

  final Object wrapAsNashorn(Object gvalue) {
    if (gvalue == null) {
      return null;
    }
    else if (GJSStatics.UNDEFINED.equals(gvalue)) {
      return Undefined.getUndefined();
    }
    else if (gvalue instanceof CharSequence) {
      return gvalue;
    }
    else if (gvalue instanceof Number || gvalue instanceof Boolean) {
      return gvalue;
    }
    else if (gvalue instanceof GJSObject) {
      GJSObject gjs_object = (GJSObject) gvalue;
      // Convert to nashorn object,
      JSObject nashorn_ob = (JSObject) gjs_object.internalGetNative();
      if (nashorn_ob == null) {
        nashorn_ob = convertGJSToNashorn(gjs_object);
        gjs_object.internalSetNative(nashorn_ob);
      }
      return instance_global.unwrap(nashorn_ob);
    }
    else if (gvalue instanceof GJavaScriptException) {
      NashornException ex =
                    wrappedNashornException((GJavaScriptException) gvalue);
      return ex;
    }
    else {
      return gvalue;
//      throw new GJavaScriptException(
//                  "Unable to convert GJS object: " + gvalue.getClass());
    }

  }
  
  final Object[] wrapArgsAsNashorn(Object[] gjs_args) {
    if (gjs_args.length == 0) {
      return gjs_args;
    }
    int len = gjs_args.length;
    Object[] arg2 = new Object[len];
    for (int i = 0; i < len; ++i) {
      arg2[i] = wrapAsNashorn(gjs_args[i]);
    }
    return arg2;
  }

  void setROHiddenMember(JSObject ob, String name, Object value) {
    // We use the unwrapped nashorn object,
    ScriptObject script_ob = (ScriptObject) instance_global.unwrap(ob);
    // Read-only hidden property flags,
    int ro_flags = Property.NOT_WRITABLE |
                   Property.NOT_ENUMERABLE |
                   Property.NOT_CONFIGURABLE;
    script_ob.addOwnProperty(name, ro_flags, value);
  }

  NashornException wrappedNashornException(GJavaScriptException ex) {
    NashornException nashorn_ex =
                              new WrappedNashornException(ex.getMessage(), ex);
    nashorn_ex.setStackTrace(ex.getStackTrace());
    return nashorn_ex;
  }

  GJavaScriptException wrappedGJSException(NashornException ex) {
    GJavaScriptException g_ex = new GJavaScriptException(ex.getMessage());
    g_ex.internalSetException(ex);
    g_ex.setStackTrace(ex.getStackTrace());
    return g_ex;
  }

}
