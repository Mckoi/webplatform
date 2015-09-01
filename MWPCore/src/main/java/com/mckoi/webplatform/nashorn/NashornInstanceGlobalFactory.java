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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
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
 * An extention to the Nashorn script engine that provides a mechanism where
 * new isolated JavaScript instances can be created via the
 * 'createInstaceGlobal' method.
 * <p>
 * NOTE: This class is experimental. It may only work in some versions of 1.8.
 *   As the Nashorn internal API stabilizes then so use of this object can
 *   move into stable releases.
 *
 * @author Tobias Downer
 */
public class NashornInstanceGlobalFactory {

  private static final String[] DEFAULT_NASHORN_ARGS = new String[] {
      "--no-java=false",
      "--global-per-engine=false",
//      "--lazy-compilation=true",
      "--loader-per-compile=false",
//      "--optimistic-types=false"
  };

  /**
   * The arguments used to initialize the Nashorn script engine.
   */
  private final String[] engine_args;

  /**
   * Internal Nashorn Context.
   */
  private Context PRIV_nashorn_ctx;

  /**
   * Constructor.
   */
  public NashornInstanceGlobalFactory() {
    this(DEFAULT_NASHORN_ARGS);
  }

  /**
   * Constructor that initializes the Nashorn engine with the given startup
   * argument options.
   * 
   * @param nashorn_engine_args 
   */
  NashornInstanceGlobalFactory(String[] nashorn_engine_args) {
    engine_args =
              Arrays.copyOf(nashorn_engine_args, nashorn_engine_args.length);
  }

  /**
   * If true, the Java extention functions are NOT included in the global
   * instances created by this object. False by default. Must be called before
   * the 'init' method is called.
   * 
   * @param flag true to not include the Java extentions in JavaScript.
   */
  public void setNoJava(boolean flag) {
    if (PRIV_nashorn_ctx != null) {
      throw new IllegalStateException("Already initialized");
    }
    if (!engine_args[0].startsWith("--no-java=")) {
      throw new RuntimeException("'engine args' format invalid");
    }
    engine_args[0] = "--no-java=" + Boolean.toString(flag);
  }

  /**
   * Startup function that can access the context from the Nashorn internal
   * Context static.
   */
  private static class EngineStartupFunction extends AbstractJSObject {
    private final Object[] inner_data;
    public EngineStartupFunction(Object[] inner_data) {
      this.inner_data = inner_data;
    }
    @Override
    public boolean isFunction() {
      return true;
    }
    @Override
    public Object call(Object thiz, Object... args) {
      inner_data[0] = Context.getContext();
//      inner_data[1] = Context.getGlobal();
      return null;
    }
  }

  /**
   * Initializes the NashornInternal object.
   * 
   * @param cl the ClassLoader to use, or null for default.
   */
  public void init(final ClassLoader cl) {

    // Incase we call 'init' more than once,
    if (PRIV_nashorn_ctx != null) {
      return;
    }

    // Hacky way to return properties from the Nashorn engine,
    final Object[] inner_data = new Object[5];

    // Run initialization in a privileged security context,
    AccessController.doPrivileged(new PrivilegedAction<Object>() {
      @Override
      public Object run() {

        // We make a VM static NashornScriptEngine that we use to compile
        // scripts and fork new JavaScript instances.

        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        NashornScriptEngine engine;
        if (cl == null) {
          engine = (NashornScriptEngine) factory.getScriptEngine(engine_args);
        }
        else {
          engine = 
              (NashornScriptEngine) factory.getScriptEngine(engine_args, cl);
        }

        // We add some functions to the global object,
        ScriptObjectMirror global_object =
                  (ScriptObjectMirror) engine.getBindings(
                                                  ScriptContext.ENGINE_SCOPE);

        // A function that performs startup. We execute this to obtain
        // information about the engine such as the Global and Context object.
        // It seems the only way to get at this information is from code
        // running within the engine.
        global_object.put("engineStartup",
                          new EngineStartupFunction(inner_data));

        // Invoke the 'engineStartup' function to get at the priviledged
        // information.
        try {
          engine.invokeFunction("engineStartup", new Object[0]);
        }
        catch (ScriptException | NoSuchMethodException ex) {
          // Oops,
          throw new RuntimeException(ex);
        }
        finally {
          // Don't leave this function around, just incase,
          global_object.delete("engineStartup");
        }

        return null;
      }
    });
    
    PRIV_nashorn_ctx = (Context) inner_data[0];
//    base_nashorn_global = (Global) inner_data[1];

  }

  /**
   * Initializes the NashornInternal object.
   */
  public void init() {
    init(null);
  }

  /**
   * Creates a new isolated JavaScript instance with a unique newly
   * initialized Global object.
   * 
   * @return 
   */
  public NashornInstanceGlobal createInstanceGlobal() {
    // Create global under local privilege
    Global global = AccessController.doPrivileged(
                                          new PrivilegedAction<Global>() {
      @Override
      public Global run() {
        return PRIV_nashorn_ctx.createGlobal();
      }
    });
    return new NIInstanceGlobal(global);
  }
  
  
  
  /**
   * An instance global represents a single isolated Global object state.
   */
  public class NIInstanceGlobal implements NashornInstanceGlobal {

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
    private NIInstanceGlobal(Global global) {
      this.PRIV_global = global;
      this.jsobject_global = (JSObject) ScriptObjectMirror.wrap(global, global);
    }

    @Override
    public JSObject getGlobal() {
      return jsobject_global;
    }
    
    @Override
    public ScriptObjectMirror wrap(ScriptObject script_object) {
      return (ScriptObjectMirror) ScriptObjectMirror.wrap(
                                            script_object, PRIV_global);
    }

    @Override
    public Object unwrap(JSObject js_object) {
      Object so = ScriptObjectMirror.unwrap(js_object, PRIV_global);
      if (so == PRIV_global) {
        // Don't expose the privileged internal global object,
        return js_object;
      }
      return so;
    }

    @Override
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

        // Return the compiled script object. This object exposes nothing to
        // the outside world. All that we can do with it is to execute the
        // script.
        return new NICompiledScript(compiled_script);

      }
      finally {
        if (global_changed) {
          Context.setGlobal(old_global);
        }
      }

    }

    @Override
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

    @Override
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

    @Override
    public JSObject newJavaScriptObject() {
      return newJavaScriptObject(null);
    }

    @Override
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
