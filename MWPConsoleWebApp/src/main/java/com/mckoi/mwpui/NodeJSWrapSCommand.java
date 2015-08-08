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

package com.mckoi.mwpui;

import com.mckoi.apihelper.TextUtils;
import com.mckoi.mwpui.nodejs.GJSNodeSourceLoader;
import com.mckoi.mwpui.nodejs.GJSObject;
import com.mckoi.mwpui.nodejs.GJSProcessSharedState;
import com.mckoi.mwpui.nodejs.GJSSystem;
import com.mckoi.mwpui.nodejs.GJavaScriptException;
import com.mckoi.mwpui.nodejs.IOEvent;
import com.mckoi.mwpui.nodejs.NodeMckoiInternal;
import com.mckoi.mwpui.nodejs.nashorn.NashornJSSystem;
import com.mckoi.mwpui.nodejs.rhino.RhinoJSSystem;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import com.mckoi.process.ProcessInputMessage.Type;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * An SCommand implementation that wraps the Rhino JavaScript engine to
 * support the Node JS base API.
 *
 * @author Tobias Downer
 */

public class NodeJSWrapSCommand extends DefaultSCommand {

  public final static String NODE_SHARED_PROCESS_STATE =
                                            "com.mckoi.NodeSharedProcessState";

  /**
   * Resolves source files.
   */
  private final GJSNodeSourceLoader node_source_loader;

  /**
   * Process state that's shared in the javascript code for this operation.
   */
  private final GJSProcessSharedState shared_process_state;

  /**
   * The Rhino JavaScript engine wrap implementation.
   */
  private GJSSystem js_system;
  private List<String> exec_args_v;
  private List<String> args_v;  

  private boolean startup_called = false;

  private boolean is_active = false;
  
  private boolean enable_benchmarks = false;

  NodeJSWrapSCommand(String reference, FileName lib_path) {
    super(reference);
    node_source_loader = new GJSNodeSourceLoader(lib_path);
    shared_process_state = new GJSProcessSharedState(this);
  }

  /**
   * Attempts to load the Node JS command assuming the command line argument
   * is of the form 'node [.js file] [arguments]'. Resolves the js file against
   * the current directory. Returns null if no script was found.
   * 
   * @param prg_reference
   * @param envs
   * @param sctx
   * @return 
   */
  static NodeJSWrapSCommand loadNodeJSCommand(
                  String reference,
                  EnvironmentVariables envs, ServerCommandContext sctx) {

    String account_name = sctx.getAccountName();

    // PENDING: Read the library path from a system setting maybe?
    FileName lib_path = new FileName("/" + account_name + "/bin/lib/node/");

    return new NodeJSWrapSCommand(reference, lib_path);

  }

  /**
   * Returns true when there are JavaScript continuations pending.
   * @return 
   */
  @Override
  public boolean isActive() {
    return is_active;
  }

  /**
   * Loads a native module id using the node source loader. The source code
   * is compiled and executed. For system modules, this produces a function
   * that is then executed with specific internal objects.
   * 
   * @param native_module_id
   * @return
   * @throws IOException 
   */
  private GJSObject loadNativeModule(String native_module_id) throws IOException {
    String module_source_code =
                node_source_loader.getModuleSource(native_module_id);
    Object compiled_module = node_source_loader.compileModule(
                                native_module_id, module_source_code);
    Object result = js_system.executeCode(compiled_module);
    if (result == null || !(result instanceof GJSObject)) {
      // Not a function, oops!
      throw new IllegalStateException("Node startup is invalid");
    }
    return (GJSObject) result;
  }

  /**
   * Loads and compiles the node startup script.
   */
  private void startupNodeProcess() {

    if (!startup_called) {
      startup_called = true;

      // Create a native object for the process and internal object,
      GJSObject node_process_ob = js_system.newJavaScriptObject("process");
      GJSObject internal_ob = new NodeMckoiInternal();

      try {

        // Set up the process and internal objects as necessary for the Mckoi
        // Web Platform environment.

        Object module_function;

        // Start up node.js,
        module_function = loadNativeModule("mckoi/mckoiinit");
        js_system.callFunction(
                    js_system.getGlobalObject(), (GJSObject) module_function,
                            new Object[] { node_process_ob, internal_ob } );

        module_function = loadNativeModule("sys/nodemin");
        js_system.callFunction(
                    js_system.getGlobalObject(), (GJSObject) module_function,
                            new Object[] { node_process_ob } );

      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }

    }

  }


  /**
   * Execute the JavaScript _tickCallback function to flush any pending tick
   * operations.
   * 
   * @param js_ctx 
   */
  void executeTickCallback() {
    GJSObject tick_callback = shared_process_state.getTickCallback();
    js_system.callFunction(
                  js_system.getGlobalObject(), tick_callback, new Object[0]);
  }

  /**
   * Executes any pending IO operations. After each IO operation,
   * executeTickCallback is called to flush the tick queue.
   * 
   * @param js_ctx 
   */
  void executePendingIOOps() {
    // Process any deferred IO events that are pending now,
    while (true) {
      IOEvent io_event = shared_process_state.popIOQueueEvent();
      if (io_event == null) {
        break;
      }

//      System.out.println("Processing IOEvent: " + io_event);

      GJSObject this_object = (GJSObject) io_event.getThisObject();
      GJSObject on_complete_function = io_event.getOnCompleteFunction();
      Object[] on_complete_args = io_event.getOnCompleteArgs();
      js_system.callFunction(
                        this_object, on_complete_function, on_complete_args);
      // _tickCallback()
      executeTickCallback();

    }
  }

  // ----------

  @Override
  public String getIconString() {
    return null;
  }

  @Override
  public String init(ServerCommandContext ctx, EnvironmentVariables env) {

    // Command line must be 'node (options) [script name] [args]'
    String[] cmd_args = TextUtils.splitCommandLineAndUnquote(env.get("cline"));

    exec_args_v = new ArrayList<>(3);
    args_v = new ArrayList<>(6);

    // Parse out the execute arguments from the node arguments,
    Iterator<String> i = Arrays.asList(cmd_args).iterator();
    args_v.add(i.next());
    boolean is_exec = true;
    while (i.hasNext()) {
      String a = i.next();
      if (is_exec) {
        if (a.startsWith("-")) {
          exec_args_v.add(a);
        }
        else {
          is_exec = false;
        }
      }
      if (!is_exec) {
        args_v.add(a);
      }
    }

    // Parse the options,
    String engine_arg = "";
    for (String exec_arg : exec_args_v) {
      String arg = exec_arg;
      if (arg.startsWith("--engine=") ||
          arg.startsWith("-engine=")) {
        engine_arg = arg.substring(arg.lastIndexOf("=") + 1);
      }
      else if (exec_arg.equals("--benchmark") ||
               exec_arg.equals("-benchmark")) {
        enable_benchmarks = true;
      }
    }
    
    // Determine the JavaScript engine to use. Either Rhino or Nashorn.

    String js_engine_val = "rhino";
    
    // Create the shared state object,
    switch (engine_arg) {
      case "rhino":
        js_engine_val = "rhino";
        break;
      case "nashorn":
        js_engine_val = "nashorn";
        break;
      default:
        // Check for environment variable,
        String js_engine_prop = env.get("jsengine");
        // If there's no jsengine environment variable,
        if (js_engine_prop != null) {
          if (js_engine_prop.equals("rhino") ||
                  js_engine_prop.equals("nashorn")) {
            js_engine_val = js_engine_prop;
          }
          else {
            js_engine_prop = null;
          }
        }
        if (js_engine_prop == null) {
          // If 'jdk.nashorn.api.scripting.NashornScriptEngine' class is
          // available then use nashorn,
          try {
            Class.forName("jdk.nashorn.api.scripting.NashornScriptEngine");
            js_engine_val = "nashorn";
          }
          catch (ClassNotFoundException ex) {
            // Default to Rhino,
            js_engine_val = "rhino";
          }
        }
        break;
    }

    String[] exec_args_v_arr = exec_args_v.toArray(new String[exec_args_v.size()]);
    String[] args_v_arr = args_v.toArray(new String[args_v.size()]);

    switch (js_engine_val) {
      case "rhino":
        js_system = new RhinoJSSystem(exec_args_v_arr, args_v_arr);
        break;
      case "nashorn":
        js_system = new NashornJSSystem(exec_args_v_arr, args_v_arr);
        break;
      default:
        throw new RuntimeException("Unknown JavaScript engine: " + js_engine_val);
    }

    return CONSOLE_MODE;

  }

  @Override
  public String process(ServerCommandContext ctx,
                        EnvironmentVariables vars, CommandWriter out) {

    // PENDING: Entering text when this returns "WAIT" ends up here.
    //  We need to handle it!

    long time_start = System.currentTimeMillis();

    is_active = false;

    // Initialize the context,
    js_system.setupContext(ctx, vars, out,
                                  node_source_loader, shared_process_state);
    
    try {

      // The NodeJS startup script,
      startupNodeProcess();

      executePendingIOOps();

      // If we are waiting for timer messages or keyboard events then return
      // "WAIT", otherwise return "STOP" indicating the command is finished.
      if (shared_process_state.hasRefedTimedCallbacksPending()) {
        // NOTE: This prevents this object being moved to a different class
        //   loader under normal conditions.
        is_active = true;
        return "WAIT";
      }
      else {
        return "STOP";
      }

    }
    catch (GJavaScriptException ex) {
      String title = ex.getMessage();
      Object title_block = js_system.getAlternativeStackTraceOf(ex);
      Object extend_block = ex;
      out.printExtendedError(title, title_block, extend_block);
      return "STOP";
    }
    finally {

      // Initialize the context,
      js_system.releaseContext();

      long end_time = System.currentTimeMillis();
      if (enable_benchmarks) {
        out.print("Script 'process' time: ");
        out.println(
                TextUtils.formatTimeFrame((end_time - time_start) * 1000000),
                "info");
      }

    }

  }

  @Override
  public String handle(ServerCommandContext ctx, EnvironmentVariables vars,
                       CommandWriter out, ProcessInputMessage input_msg) {

    long time_start = System.currentTimeMillis();

    is_active = false;

    // Initialize the context,
    js_system.setupContext(ctx, vars, out,
                                  node_source_loader, shared_process_state);

    try {

      Type type = input_msg.getType();
      // If the type is timed or return,
      if (type == Type.RETURN ||
          type == Type.RETURN_EXCEPTION ||
          type == Type.TIMED_CALLBACK) {

        // Fetch the call_id
        int call_id = input_msg.getCallId();

        // Pull a timer object for this call_id,
        GJSObject timer_ob = shared_process_state.pullTimerObject(call_id);
        if (timer_ob != null) {
          Object callback = timer_ob.getSlot(0);
          if (callback != null && callback instanceof GJSObject) {
            GJSObject onTimeoutCallback = (GJSObject) callback;
            // Call the closure,
            js_system.callFunction(
                              timer_ob, onTimeoutCallback, new Object[0]);
            // Process queued messages,
            executePendingIOOps();
          }
          else {
            out.println("Timer callback not found", "error");
            // Report this problem?
          }
        }

      }

      if (shared_process_state.hasRefedTimedCallbacksPending()) {
        // NOTE: This prevents this object being moved to a different class
        //   loader under normal conditions.
        is_active = true;
        return "WAIT";
      }
      else {
        return "STOP";
      }

    }
    catch (GJavaScriptException ex) {
      String title = ex.getMessage();
      Object title_block = js_system.getAlternativeStackTraceOf(ex);
      Object extend_block = ex;
      out.printExtendedError(title, title_block, extend_block);
      return "STOP";
    }
    finally {

      // Initialize the context,
      js_system.releaseContext();

      long end_time = System.currentTimeMillis();
      if (enable_benchmarks) {
        out.print("Script 'handle' time: ");
        out.println(
                TextUtils.formatTimeFrame((end_time - time_start) * 1000000),
                "info");
      }

    }

  }

  // -----

}
