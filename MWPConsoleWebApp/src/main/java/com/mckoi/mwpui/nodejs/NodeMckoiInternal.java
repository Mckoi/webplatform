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

import com.mckoi.mwpui.EnvironmentVariables;
import com.mckoi.process.ProcessInstance;

/**
 *
 * @author Tobias Downer
 */
public class NodeMckoiInternal extends GJSAbstractObject {

  private final FileDescriptorsMap fd_map = new FileDescriptorsMap();
  
  
  private final NodeNativeMFileSystems node_native_mfilesystems =
                                          new NodeNativeMFileSystems(fd_map);
  private final NodeNativeMfs node_native_mfs = new NodeNativeMfs(this);
  private final NodeNativeFs node_native_fs = new NodeNativeFs(this);

  private final NodeNativeBuffer node_native_buffer = new NodeNativeBuffer();
  private final NodeNativeMwpConsole node_native_mwp_console = new NodeNativeMwpConsole();


  private GJSObject fs_stats_object;
  
  public NodeMckoiInternal() {
    init();
  }

  FileDescriptorsMap getFDMap() {
    return fd_map;
  }

  GJSObject getFsStatsObject() {
    return fs_stats_object;
  }
  
  void setFsStatsObject(GJSObject fs_stats) {
    fs_stats_object = fs_stats;
  }
  

  private void init() {

    try {

//      GJSSystem system = GJSRuntime.system();
//      GJSProcessSharedState shared_state = system.getSharedState();
//      EnvironmentVariables env = shared_state.getEnv();
//
//      // Copy the environment variables into an JavaScript object,
//      GJSObject env_ob = system.newJavaScriptObject();
//      for (String key : env.keySet()) {
//        env_ob.setMember(key, env.get(key));
//      }
//
//      // Command line must be 'node [script name] [args]' for compatibility.
//      String[] args = TextUtils.splitCommandLineAndUnquote(env.get("cline"));
//      // Input arguments,
//      Object[] ob_arr = new Object[args.length];
//      System.arraycopy(args, 0, ob_arr, 0, args.length);
//      GJSObject arg_v = GJSRuntime.system().asJavaScriptArray(ob_arr);




//      setMember("env", env_ob);
//      setMember("argv", arg_v);

      setWrappedFunction("process_setupProcessJS");
      setWrappedFunction("process_runMicrotasks");
      setWrappedFunction("process_cwd");
      setWrappedFunction("process_exposeTickCallback");
      setWrappedFunction("contextify_runInThisContext");
      setMember("natives_object", new NodeNatives());
      setWrappedFunction("buffer_setupBufferJS");

      // Makes i$.smalloc_alloc
      GJSRuntime.setWrappedFunction(
              NodeNativeSmalloc.class, "smalloc", "alloc", this);
      // Makes i$.smalloc_truncate
      GJSRuntime.setWrappedFunction(
              NodeNativeSmalloc.class, "smalloc", "truncate", this);
      // Makes i$.smalloc_sliceOnto
      GJSRuntime.setWrappedFunction(
              NodeNativeSmalloc.class, "smalloc", "sliceOnto", this);

      node_native_mfilesystems.setupFSFunctionsOn(this);
      node_native_mfs.setupFSFunctionsOn(this);
      node_native_fs.setupFSFunctionsOn(this);

      GJSRuntime.setWrappedFunction(
              node_native_mwp_console, "mwpconsole", "write", this);

      GJSRuntime.setWrappedFunction(
              NodeNativeTimerWrap.class, "timer", "now", this);
      GJSRuntime.setWrappedFunction(
              NodeNativeTimerWrap.class, "timer", "start", this);
      GJSRuntime.setWrappedFunction(
              NodeNativeTimerWrap.class, "timer", "close", this);

    }
    catch (NoSuchMethodException ex) {
      throw new GJavaScriptException(ex);
    }

  }

  @Override
  public String getClassName() {
    return "INTERNAL";
  }

  public static Object process_setupProcessJS(Object thiz, Object... args) {
    GJSSystem system = GJSRuntime.system();
    GJSProcessSharedState shared_state = system.getSharedState();
    ProcessInstance process_instance =
                shared_state.getServerCommandContext().getProcessInstance();
    EnvironmentVariables env = shared_state.getEnv();

    // Copy the environment variables into an JavaScript object,
    GJSObject env_ob = system.newJavaScriptObject();
    for (String key : env.keySet()) {
      env_ob.setMember(key, env.get(key));
    }

    GJSObject arg_v =
                  GJSRuntime.system().asJavaScriptArray(system.getArgsv());
    GJSObject exec_arg_v =
                  GJSRuntime.system().asJavaScriptArray(system.getExecArgsv());

    GJSObject process_ob = (GJSObject) args[0];
    process_ob.setMember("argv", arg_v);
    process_ob.setMember("execArgv", exec_arg_v);
    process_ob.setMember("env", env_ob);
    // This sets pid as a string, when most Node programs will expect a
    // number.
    process_ob.setMember("pid", process_instance.getId().getStringValue());
    process_ob.setMember("_mwpJSSystemId", system.getSystemId());

    // Return undefined,
    return GJSStatics.UNDEFINED;

  }
  
  public static Object process_runMicrotasks(Object thiz, Object... args) {
    // Do anything here?
    return GJSStatics.UNDEFINED;
  }
  
  public static Object process_cwd(Object thiz, Object... args) {
    // Wow, ok...
    return GJSRuntime.system().getSharedState().getEnv().get("pwd");
  }

  public static Object process_exposeTickCallback(Object thiz, Object... args) {
    GJSObject _tickCallback = (GJSObject) args[0];
    // Set the _tickCallback shared state,
    GJSProcessSharedState process_state = GJSRuntime.system().getSharedState();
    if (process_state.getTickCallback() == null) {
      // Only set it once.
      process_state.setTickCallback(_tickCallback);
    }
    // Return undefined,
    return GJSStatics.UNDEFINED;
  }

  
  /**
   * Passthrough to setupBufferJS.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object buffer_setupBufferJS(Object thiz, Object... args) {
    return node_native_buffer.setupBufferJS(thiz, args);
  }

  /**
   * 
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public static Object contextify_runInThisContext(Object thiz, Object... args) {

    Object code = args[0];
    GJSObject options = (GJSObject) args[1];

    Object fname_ob = options.getMember("filename");
    String file_name;
    if (fname_ob instanceof CharSequence) {
      file_name = fname_ob.toString();
    }
    else {
      throw new GJavaScriptException("No 'filename' property found");
    }

    // The node source loader,
    GJSSystem gsystem = GJSRuntime.system();
    GJSProcessSharedState pstate = gsystem.getSharedState();
    GJSNodeSourceLoader node_source_loader = pstate.getNodeSourceLoader();

    String module_id = file_name;
    if (file_name.endsWith(".js")) {
      module_id = module_id.substring(0, file_name.length() - 3);
    }
    Object script = node_source_loader.compileModule(module_id, code);

    // Execute the script in the call scope,
    Object exec_result = gsystem.executeCode(script);

//      System.out.println("file: " + file_name);
//      System.out.println("made object: " + exec_result.hashCode());

    // Return the script execution result,
    return exec_result;

    
  }

}
