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

package com.mckoi.mwpui.nodejs.rhino.deprec;

import com.mckoi.apihelper.TextUtils;
import com.mckoi.mwpui.CommandWriter;
import com.mckoi.mwpui.EnvironmentVariables;
import com.mckoi.mwpui.JSWrapSCommand;
import com.mckoi.mwpui.NodeJSWrapSCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.mozilla.javascript.*;
import static org.mozilla.javascript.ScriptableObject.PERMANENT;
import static org.mozilla.javascript.ScriptableObject.READONLY;

import static com.mckoi.mwpui.NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE;

/**
 *
 * @author Tobias Downer
 */
public class ProcessObject extends NativeObject {

  @Override
  public String getClassName() {
    return "process";
  }

  public ProcessObject init(
                Context js_ctx, Scriptable scope,
                EnvironmentVariables env, CommandWriter out) {

    ScriptRuntime.setBuiltinProtoAndParent(
                                    this, scope, TopLevel.Builtins.Object);
    setPrototype(scope);
    setParentScope(null);

    int ro_attr = PERMANENT | READONLY;
    int roh_attr = PERMANENT | READONLY | DONTENUM;

    // Parse out the command line,
    // Command line must be 'node [script name] [args]' for compatibility.
    String[] args = TextUtils.splitCommandLineAndUnquote(env.get("cline"));
    // Input arguments,
    Object[] ob_arr = new Object[args.length];
    System.arraycopy(args, 0, ob_arr, 0, args.length);
    NativeArray arg_v = (NativeArray) js_ctx.newArray(scope, ob_arr);

    // Shared binding functions,
    Map<String, Object> binding_functions = new HashMap();

    // Define the function invoke callback function,

    defineProperty("binding",
         new NodeBindingFunction(env, out, binding_functions).init(scope),
         ro_attr);
    defineProperty("_setupNextTick", 
         new SetupNextTickFunction().init(scope), ro_attr);
    defineProperty("_mckoiAddBinding",
         new MckoiAddBindingFunction(binding_functions).init(scope), roh_attr);

    // Current Working Directory,
    defineProperty("cwd",
                   new CWDFunction(env).init(this), ro_attr);
    
    defineProperty("argv", arg_v, PERMANENT);
    defineProperty("env", new NodeEnvObject(env).init(scope), ro_attr);
    
    defineProperty("moduleLoadList", js_ctx.newArray(scope, 0), ro_attr);

    return this;
  }

  // -----

  /**
   * The process 'binding' function,
   */
  public static class NodeBindingFunction extends RhinoFunction {

    private final EnvironmentVariables env;
    private final CommandWriter out;
    private final Map<String, Object> binding_functions;

    private NativeObject natives;
    private NativeObject buffer;
    private NativeObject smalloc;
    private NativeObject mwpconsole;
    private NativeObject fs;
    private NativeObject timer_wrap;

    public NodeBindingFunction(EnvironmentVariables env, CommandWriter out,
                               Map<String, Object> binding_functions) {
      super();
      this.binding_functions = binding_functions;
      this.env = env;
      this.out = out;
    }

    @Override
    public NodeBindingFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("binding");
      // Create and initialize the 'natives' binding object.
      natives = new BindingNatives().init(scope);
      buffer = new BindingBuffer().init(scope);
      smalloc = new BindingSmalloc().init(scope);
      mwpconsole = new BindingMwpConsole(out).init(scope);
      fs = new BindingFs().init(scope);
      timer_wrap = new BindingTimerWrap().init(scope);
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

      // Create the binding result,
      if (args.length >= 1) {
        String arg = args[0].toString();
        if (arg != null) {
          
          int ro_attr = PERMANENT | READONLY;
          
          if (arg.equals("contextify")) {
            // Defines a ContextifyScript object,
            // ContextifyScript = function(code, options) {
            //    // code = source code
            //    // options = map of various options
            // }

//            String code = "var yarrr = function(code, option) { var code = code; var options = options; };";
//            cx.evaluateString(this, code, "INTERNAL", 1, this);
//            ScriptableObject c = (ScriptableObject) this.get("yarrr");
//            c.defineProperty("runInThisContext",
//                          new RunInThisContextFunction().init(c), ro_attr);
//            
//            ScriptableObject contextify = (ScriptableObject) cx.newObject(this);
//            contextify.defineProperty("ContextifyScript",
//                                  c, ro_attr);
//            return contextify;
            ScriptableObject contextify = (ScriptableObject) cx.newObject(this);
            contextify.defineProperty("ContextifyScript",
                                  new ContextifyScript().init(scope), ro_attr);
            return contextify;
          }
          // The 'natives' object looks up modules,
          else if (arg.equals("natives")) {
            return natives;
          }
          else if (arg.equals("buffer")) {
            return buffer;
          }
          else if (arg.equals("smalloc")) {
            return smalloc;
          }
          // Console stream binding,
          else if (arg.equals("mwpconsole")) {
            return mwpconsole;
          }
          // File system binding,
          else if (arg.equals("fs")) {
            return fs;
          }
          // The timer binding,
          else if (arg.equals("timer_wrap")) {
            return timer_wrap;
          }
          else {
            Object val = binding_functions.get(arg);
            if (val == null) {
              throw Context.reportRuntimeError("Unknown native binding: " + arg);
            }
            return val;
          }
        }
      }

      // Return undefined,
      return Undefined.instance;

    }

  }

  public static class MckoiAddBindingFunction extends RhinoFunction {
    
    private final Map<String, Object> binding_functions;

    private MckoiAddBindingFunction(Map<String, Object> binding_functions) {
      this.binding_functions = binding_functions;
    }

    @Override
    public MckoiAddBindingFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("_mckoiaddbinding");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {
      // If the custom map doesn't contain the key then put the object.
      String key = args[0].toString();
      if (!binding_functions.containsKey(key)) {
        binding_functions.put(key, args[1]);
        return Boolean.TRUE;
      }
      else {
        return Boolean.FALSE;
      }
    }

  }

  /**
   * The ContextifyScript function that parses javascript. This is a function
   * that must be constructed. eg;
   * </pre>
   *   var cs_instance = new ContextifyScript(code, options);
   *   cs_instance.runInThisContext();
   * </pre>
   */
  public static class ContextifyScript extends NativeFunction {

    public ContextifyScript init(Scriptable scope) {
      ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Function);
      return this;
    }

    @Override
    public String getClassName() {
      return "ContextifyScript";
    }

    /**
     * The function constructor call.
     * 
     * @param cx
     * @param scope
     * @param thisObj
     * @param args
     * @return 
     */
    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

      // Defines ContextifyScript functions in the function instance,
      ScriptableObject instance = (ScriptableObject) thisObj;
      int ro_attr = READONLY | PERMANENT;

      // Set the constructor arguments are properties of the instance,
      if (args.length >= 2) {
        instance.defineProperty("code", args[0], ro_attr);
        instance.defineProperty("options", args[1], ro_attr);
      }

      // Define the functions,
      instance.defineProperty("runInThisContext",
                          new RunInThisContextFunction().init(this), ro_attr);

      return thisObj;
    }

    @Override
    protected int getLanguageVersion() {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

    @Override
    protected int getParamCount() {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

    @Override
    protected int getParamAndVarCount() {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

    @Override
    protected String getParamOrVarName(int index) {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

  }

  /**
   * A function of a ContextifyScript instance which runs the javascript code
   * stored in the object.
   */
  public static class RunInThisContextFunction extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("runInThisContext");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {
      // javascript example;
      //   new ContextifyScript().runInThisContext();

      Object code = JSWrapSCommand.jsToString(thisObj.get("code", thisObj));
      Scriptable options = (Scriptable) thisObj.get("options", thisObj);

      Object fname_ob = options.get("filename", thisObj);
      String file_name;
      if (fname_ob instanceof CharSequence) {
        file_name = fname_ob.toString();
      }
      else {
        throw Context.reportRuntimeError("No 'filename' property found");
      }

      // The node source loader,
      JSProcessSharedState pstate =
                (JSProcessSharedState) cx.getThreadLocal(NODE_SHARED_PROCESS_STATE);
      NodeSourceLoader node_source_loader = pstate.getNodeSourceLoader();

      String module_id = file_name;
      if (file_name.endsWith(".js")) {
        module_id = module_id.substring(0, file_name.length() - 3);
      }
      Script script = node_source_loader.compileModule(cx, module_id, code);
      
      // Execute the script in the call scope,
      Object exec_result = script.exec(cx, scope);
      
      // Return the script execution result,
      return exec_result;
      
    }
  }

  /**
   * The process '_setupNextTick' function,
   */
  public static class SetupNextTickFunction extends RhinoFunction {

    @Override
    public SetupNextTickFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("_setupNextTick");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

      ScriptableObject tick_info = (ScriptableObject) args[0];
      BaseFunction _tickCallback = (BaseFunction) args[1];
      ScriptableObject _runMicrotasks = (ScriptableObject) args[2];

      // Set up 'tick_info' object,
      tick_info.put(0, tick_info, new Integer(0));
      tick_info.put(1, tick_info, new Integer(0));

      // Set up 'runMicrotasks' function,
      _runMicrotasks.defineProperty(
                    "runMicrotasks", new RunMicrotasksFunction().init(scope),
                    READONLY | PERMANENT);

      // Set the _tickCallback shared state,
      JSProcessSharedState process_state =
                      (JSProcessSharedState) cx.getThreadLocal(
                                NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE);
      if (process_state.getTickCallback() == null) {
        // Only set it once.
        process_state.setTickCallback(_tickCallback);
      }

      // Return undefined,
      return Undefined.instance;

    }

  }

  public static class RunMicrotasksFunction extends RhinoFunction {


    @Override
    public RunMicrotasksFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("runMicrotasks");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

      // PENDING: This seems to be called every event loop,
//      System.out.println("runMicrotasks called!");

      return Boolean.TRUE;

    }

  }

  /**
   * CWD is a function that returns the current working directory.
   */
  public static class CWDFunction extends RhinoFunction {

    private final EnvironmentVariables env;

    private CWDFunction(EnvironmentVariables env) {
      this.env = env;
    }

    @Override
    public CWDFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("cwd");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

//      System.err.println("DEBUGGING: cwd returns /admin/v2bin");
//      return "/admin/v2bin";
      return env.get("pwd");

    }

  }

  /**
   * An implementation of nodejs process.env (access to environment variables)
   */
  public static class NodeEnvObject extends RhinoAbstractMap {

    private final EnvironmentVariables env;

    private NodeEnvObject(EnvironmentVariables env) {
      this.env = env;
    }

    @Override
    public String getClassName() {
      return "env";
    }

    @Override
    protected Object getKey(String id) {
      return env.get(id);
    }

    @Override
    protected boolean hasKey(String id) {
      return env.get(id) != null;
    }

    @Override
    protected Set<String> keysArray() {
      return env.keySet();
    }

    @Override
    protected void deleteKey(String id) {
      env.put(id, null);
    }

    @Override
    protected void putKeyValue(String id, Object val) {
      env.put(id, JSWrapSCommand.jsToString(val));
    }

  }

}
