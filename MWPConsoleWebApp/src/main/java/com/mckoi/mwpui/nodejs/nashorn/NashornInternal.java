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

import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.scripts.JO;

/**
 * Internal access to a Nashorn Engine.
 * <p>
 * NOTE: Only works on Java1.8v40 (and theoretical future versions).
 *
 * @author Tobias Downer
 */
public class NashornInternal {
  
//  private static final String[] NASHORN_ARGS = new String[] {
//      "--no-java", "--optimistic-types=true"
//  };
  private static final String[] NASHORN_ARGS = new String[] {
      "--no-java=true",
      "--global-per-engine=false",
//      "--optimistic-types=true",
      "--lazy-compilation=true",
//      "--loader-per-compile=false"
  };

  /**
   * Internal Nashorn Context.
   */
  private Context PRIV_nashorn_ctx;

  /**
   * Constructor.
   */
  public NashornInternal() {
  }

  /**
   * Initializes the NashornInternal object.
   */
  public void init() {

    // Incase we call 'init' more than once,
    if (PRIV_nashorn_ctx != null) {
      return;
    }

    // We make a VM static NashornScriptEngine that we use to compile scripts
    // and fork new JavaScript instances.

    NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
    NashornScriptEngine engine =
                (NashornScriptEngine) factory.getScriptEngine(
                        NASHORN_ARGS, NashornJSSystem.class.getClassLoader());

    // Hacky way to return properties from the Nashorn engine,
    final Object[] inner_data = new Object[5];

    // We add some functions to the global object,
    ScriptObjectMirror global_object =
              (ScriptObjectMirror) engine.getBindings(
                                                  ScriptContext.ENGINE_SCOPE);

    // A function that performs startup. We execute this to obtain information
    // about the engine such as the Global and Context object. It seems the
    // only way to get at this information is from code running within the
    // engine.
    global_object.put("engineStartup", new AbstractJSObject() {
      @Override
      public boolean isFunction() {
        return true;
      }
      @Override
      public Object call(Object thiz, Object... args) {
        // Execute under this class security privs.
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
          @Override
          public Object run() {
            inner_data[0] = Context.getContext();
//            inner_data[1] = Context.getGlobal();
            return null;
          }
        });
        return null;
      }
    });

    // Invoke the 'engineStartup' function to get at the priviledged
    // information.
    try {
      engine.invokeFunction("engineStartup", new Object[0]);
    }
    catch (ScriptException | NoSuchMethodException ex) {
      // Oops,
      throw new RuntimeException(ex);
    }

    // Don't leave this function around, just incase,
    global_object.delete("engineStartup");

    PRIV_nashorn_ctx = (Context) inner_data[0];
//    base_nashorn_global = (Global) inner_data[1];

  }


  /**
   * Creates a new isolated JavaScript instance with a unique newly
   * initialized Global object.
   * 
   * @return 
   */
  public InstanceGlobal createInstanceGlobal() {
    Global global = PRIV_nashorn_ctx.createGlobal();
    return new InstanceGlobal(global);
  }
  
  
  
  /**
   * An instance global represents a single isolated Global object state.
   */
  public class InstanceGlobal {

    /**
     * The privileged global object (don't let user code access this).
     */
    private final Global PRIV_global;

    /**
     * The JSObject representation of the global (ScriptObjectMirror wrap).
     */
    private final JSObject jsobject_global;

    /**
     * Constructor.
     * 
     * @param global 
     */
    private InstanceGlobal(Global global) {
      this.PRIV_global = global;
      this.jsobject_global = (JSObject) ScriptObjectMirror.wrap(global, global);
    }

    /**
     * Returns the Global as a JSObject.
     * 
     * @return 
     */
    public JSObject getGlobal() {
      return jsobject_global;
    }
    
    /**
     * Wraps a ScriptObject as a ScriptObjectMirror.
     * 
     * @param script_object
     * @return 
     */
    public ScriptObjectMirror wrap(ScriptObject script_object) {
      return (ScriptObjectMirror) ScriptObjectMirror.wrap(
                                            script_object, PRIV_global);
    }

    /**
     * Unwraps a JSObject.
     * 
     * @param js_object
     * @return 
     */
    public Object unwrap(JSObject js_object) {
      Object so = ScriptObjectMirror.unwrap(js_object, PRIV_global);
      if (so == PRIV_global) {
        // Don't expose the privileged internal global object,
        return js_object;
      }
      return so;
    }

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
    public NICompiledScript compileSourceCode(
                  String source_code, String file_name, int first_line_num) {

      Global old_global = Context.getGlobal();
      boolean global_changed = (old_global != PRIV_global);
      if (global_changed) {
        Context.setGlobal(PRIV_global);
      }
      try {

        // Nashorn appears to do some internal caching using this object. We
        // should make sure it's never exposed to the outside world.
        Source src = Source.sourceFor(file_name, source_code);

        // Compile the script.
        // ISSUE: Compiling the code is a synchronous operation inside the
        //   Nashorn context. For multi-threaded script compilation we would
        //   need to create a pool of Nashorn contexts to handle it.
        Context.MultiGlobalCompiledScript compiled_script =
                                            PRIV_nashorn_ctx.compileScript(src);

        // Return the compiled script object. This object exposes nothing to the
        // outside world. All that we can do with it is to execute the script.
        return new NICompiledScript(compiled_script);

      }
      finally {
        if (global_changed) {
          Context.setGlobal(old_global);
        }
      }

    }

    /**
     * Executes compiled code previously returned from the 'compileSourceCode'
     * method.
     * 
     * @param ni_compiled_script an object returned from 'compileSourceCode'
     * @return 
     */
    public Object executeCode(Object ni_compiled_script) {

      Global old_global = Context.getGlobal();
      boolean global_changed = (old_global != PRIV_global);
      if (global_changed) {
        Context.setGlobal(PRIV_global);
      }
      try {

        // Cast.
        NICompiledScript compiled_script = (NICompiledScript) ni_compiled_script;

        // Turn the MultiGlobalCompiledScript into a function and execute it
        // within this instance global.
        Object result = ScriptRuntime.apply(
                compiled_script.mgcs.getFunction(PRIV_global), PRIV_global);

        // The result with be either a ScriptObject or an object value type, so
        // we'll need to wrap it.
        return ScriptObjectMirror.wrap(result, PRIV_global);

      }
      finally {
        if (global_changed) {
          Context.setGlobal(old_global);
        }
      }

    }

    /**
     * Returns the given list of arguments (as Nashorn types) to a native array.
     * 
     * @param arr
     * @return 
     */
    public JSObject asJavaScriptArray(Object[] arr) {

      Global old_global = Context.getGlobal();
      boolean global_changed = (old_global != PRIV_global);
      if (global_changed) {
        Context.setGlobal(PRIV_global);
      }
      try {

        Object[] unwrapped_arr = new Object[arr.length];
        for (int i = 0; i < arr.length; ++i) {
          unwrapped_arr[i] = ScriptObjectMirror.unwrap(arr[i], PRIV_global);
        }
        ScriptObject array =
                        (ScriptObject) PRIV_global.wrapAsObject(unwrapped_arr);

        // Return the array wrapped,
        return wrap(array);

      }
      finally {
        if (global_changed) {
          Context.setGlobal(old_global);
        }
      }

    }

    /**
     * Returns a JSObject representing an empty object. This is equivalent to
     * the following JavaScript code;
     * <code>
     *   return {};
     * </code>
     * 
     * @return 
     */
    public JSObject newJavaScriptObject() {
      return newJavaScriptObject(null);
    }

    /**
     * Returns a JSObject representing an empty object with the [[Class]]
     * value returning the given class name string.
     * 
     * @param class_name
     * @return 
     */
    public JSObject newJavaScriptObject(String class_name) {
      Global old_global = Context.getGlobal();
      boolean global_changed = (old_global != PRIV_global);
      if (global_changed) {
        Context.setGlobal(PRIV_global);
      }
      try {

        ScriptObject new_object;
        if (class_name == null) {
          new_object = PRIV_global.newObject();
        }
        else {
          ScriptObject object_prototype = Global.objectPrototype();
          new_object = new ClassedJO(
                    class_name, object_prototype, ClassedJO.getInitialMap());
        }
        return wrap(new_object);

      }
      finally {
        if (global_changed) {
          Context.setGlobal(old_global);
        }
      }
    }

  }

  /**
   * Stores the internal state of some compiled JavaScript code.
   */
  public final class NICompiledScript {
    private final Context.MultiGlobalCompiledScript mgcs;
    private NICompiledScript(Context.MultiGlobalCompiledScript mgcs) {
      this.mgcs = mgcs;
    }
  }

  /**
   * An empty JavaScript object with a class name.
   */
  private static class ClassedJO extends JO {
    private final String class_name;
    public ClassedJO(String class_name, ScriptObject proto, PropertyMap map) {
      super(proto, map);
      this.class_name = class_name;
    }
    @Override
    public String getClassName() {
      return class_name;
    }
  }

}
