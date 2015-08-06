/**
 * com.mckoi.mwpui.JSWrapSCommand  Oct 9, 2012
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2012  Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this program.  If not, see ( http://www.gnu.org/licenses/ ) or
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 *
 * Change Log:
 *
 *
 */

package com.mckoi.mwpui;

import com.mckoi.apihelper.ScriptResourceAccess;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import com.mckoi.process.ProcessInputMessage.Type;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.rhino.JSProcessInstanceScope;
import com.mckoi.webplatform.rhino.JSProcessInstanceScope.CallableScript;
import com.mckoi.webplatform.rhino.JSWrapBase;
import com.mckoi.webplatform.rhino.KillSignalException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.mozilla.javascript.*;
import org.mozilla.javascript.commonjs.module.Require;

/**
 * An SCommand implementation that wraps a Rhino JavaScript script. Also
 * implements CommandJS 'Module1.x' spec.
 *
 * @author Tobias Downer
 */

public class JSWrapSCommand extends DefaultSCommand {

  private static final JSEnvironmentScope ENV_SCOPE;

  static {
    JSEnvironmentScope env_scope;
    Context js_ctx = JSWrapBase.generic_context_factory.enterContext();
    try {
      env_scope = new JSEnvironmentScope();

      // Initialize standard stuff and seal it,,
      js_ctx.initStandardObjects(env_scope, true);

      // Add system classes/statics to 'env_scope'
      env_scope.init();

      // Seal the object,
      env_scope.sealObject();

    }
    finally {
      Context.exit();
    }
    ENV_SCOPE = env_scope;
  }

  /**
   * The absolute script module id (eg. '/admin/bin/ls').
   */
  private final FileName script_module_id;

  /**
   * The instance scope object.
   */
  private InstanceScope instance_scope;

  /**
   * The cached JavaScript Scriptable.
   */
  private Scriptable js_server_command;

  /**
   * Constructor.
   */
  JSWrapSCommand(String reference, FileName script_fn) {
    super(reference);
    String script_file_name = script_fn.toString();

    if (script_file_name.endsWith(".mjs")) {
      this.script_module_id = new FileName(
                 script_file_name.substring(0, script_file_name.length() - 4));
    }
    else if (script_file_name.endsWith(".js")) {
      this.script_module_id = new FileName(
                 script_file_name.substring(0, script_file_name.length() - 3));
    }
    else {
      script_module_id = script_fn;
    }
  }

  /**
   * Initialize the instance scope.
   */
  private void initInstanceScope(ServerCommandContext ctx) {
    if (instance_scope == null) {
      instance_scope = new InstanceScope(ctx);
    }
  }

  /**
   * Attempts to load the JS command with the given name (prg_name). This
   * first tries to resolve the name against the account's 'bin/' directory.
   * If unable to, then against the current directory. Returns null if no
   * script was found.
   */
  static JSWrapSCommand loadJSCommand(
                         String reference,
                         String prg_name, EnvironmentVariables envs,
                         ServerCommandContext sctx) {

    PlatformContext ctx = sctx.getPlatformContext();

    // If the program name doesn't contain '/' then try and resolve against
    // the bin directory,
    FileRepository fs;
    FileInfo fi;
    FileName file_name;
    if (!prg_name.contains("/")) {
      file_name = new FileName(
                      "/" + ctx.getAccountName() + "/bin/" + prg_name + ".js");
      fs = ctx.getFileRepositoryFor(file_name);
      if (fs != null) {
        fi = fs.getFileInfo(file_name.getPathFile());
        if (fi != null) {
          return new JSWrapSCommand(reference, file_name);
        }
      }
    }

    // Try and resolve it against the pwd,
    String pwd = envs.get("pwd");
    FileName pwd_fn = new FileName(pwd);

    FileName potential_fname = pwd_fn.resolve(new FileName(prg_name));
    String pfn_str = potential_fname.toString();
    if (!pfn_str.endsWith(".js")) {
      pfn_str = pfn_str + ".js";
    }
    potential_fname = new FileName(pfn_str);

    // Check for class loader resources first,
    String repository_id = potential_fname.getRepositoryId();
    String pathname = potential_fname.getPathFile();

    // If it's the class loader repository,
    // eg. '/.cl/console/bin/lib/mwp/process.js'
    if (repository_id.equals(".cl")) {

      // Cut at the first path,
      int delim = pathname.indexOf('/', 1);
      if (delim != -1) {

        // The first part of the path will be the account name,
        String to_lookup = pathname.substring(delim + 1);

        ClassLoader this_cl = JSWrapSCommand.class.getClassLoader();

        // If the resource found,
        if (ScriptResourceAccess.doesLibraryScriptExist(this_cl, to_lookup)) {
          return new JSWrapSCommand(reference, potential_fname);
        }

      }

    }
    // Try and resolve the script by looking up the repository,
    else {

      fs = ctx.getFileRepository(repository_id);
      if (fs != null) {
        fi = fs.getFileInfo(pathname);
        if (fi != null && fi.isFile()) {
          return new JSWrapSCommand(reference, potential_fname);
        }
      }

    }

    // Not found,
    return null;

  }

  /**
   * A context factory that observes the 'isKilled' flag in the
   * ServerCommandContext. Throws 'KillSignalException' when the kill signal is
   * observed.
   */
  private static class InterruptibleContextFactory
                                     extends JSWrapBase.JSWrapContextFactory {

    private int check_count = 0;
    private final ServerCommandContext context;
    private final long time_start;

    InterruptibleContextFactory(ServerCommandContext context) {
      this.context = context;
      this.time_start = System.currentTimeMillis();
    }

    @Override
    protected Context makeContext() {
      Context c = super.makeContext();
      c.setInstructionObserverThreshold(100000);
      return c;
    }

    @Override
    protected void observeInstructionCount(Context c, int instructionCount) {
      ++check_count;

      // Consume any signals
      if (context.isKilled()) {
        // Thrown kill signal exception,
        throw new KillSignalException();
      }

      // Check for timeout,
      long time_dif = System.currentTimeMillis() - time_start;
      // Interrupt if over 15 minutes old,
      if (time_dif > (15 * 60 * 1000)) {
        throw new CommandTimeoutException("(" + check_count + ")");
      }

      super.observeInstructionCount(c, instructionCount);
    }

  }

  /**
   * Loads and compiles the script.
   */
  private Scriptable loadAndCompileScript(Context js_ctx) {

    if (js_server_command == null) {
      
      instance_scope.setPrototype(ENV_SCOPE);
      instance_scope.setParentScope(null);
      instance_scope.init();

      Scriptable env_scope = js_ctx.newObject(instance_scope);
      // The prototype of this object is the static ENV_SCOPE object that
      // includes the standard JavaScript and Mckoi API functions.
      env_scope.setPrototype(instance_scope);
      env_scope.setParentScope(null);

      String module_id = script_module_id.toString();

      // Make the 'require' function, not sandboxed with no init or exit scripts,
      Require require =
            new Require(js_ctx, env_scope,
                        JSWrapBase.getModuleSourceLoader(), null, null, false);

      js_server_command = require.requireMain(js_ctx, module_id);

    }

    return js_server_command;

  }

  // ----------

  @Override
  public String getIconString() {
    return null;
  }

  @Override
  public String init(ServerCommandContext ctx, EnvironmentVariables vars) {

    // Make sure to initialize the instance scope,
    initInstanceScope(ctx);

    InterruptibleContextFactory jsctx_factory =
                                          new InterruptibleContextFactory(ctx);
    Context js_ctx = jsctx_factory.enterContext();
    try {
      Scriptable env_scope = loadAndCompileScript(js_ctx);
      
      WrapFactory js_wrap = js_ctx.getWrapFactory();
      Object ctx_wrap = js_wrap.wrap(js_ctx, env_scope,
                                     ctx, ServerCommandContext.class);
      Object js_vars = js_wrap.wrap(js_ctx, env_scope,
                                    vars, EnvironmentVariables.class);
      EnvironmentVariablesWrap vars_wrap =
                                   new EnvironmentVariablesWrap(vars, js_vars);

      Function process_fct = JSWrapBase.getFunction(env_scope, "init");

      if (process_fct != null) {
        // Invoke the process function,
        Object return_ob = process_fct.call(js_ctx, env_scope, env_scope,
                                new Object[] { ctx_wrap, vars_wrap });

        // Return the function output,
        return (String) Context.jsToJava(return_ob, String.class);
      }
      // If no 'init' method then default to console mode,
      else {
        return CONSOLE_MODE;
      }
      
    }
    finally {
      Context.exit();
    }

  }

  @Override
  public String process(ServerCommandContext ctx,
                        EnvironmentVariables vars, CommandWriter out) {

    // Make sure to initialize the instance scope,
    initInstanceScope(ctx);

    InterruptibleContextFactory jsctx_factory =
                                          new InterruptibleContextFactory(ctx);
    Context js_ctx = jsctx_factory.enterContext();
    try {
      Scriptable env_scope = loadAndCompileScript(js_ctx);

      // Wrap the objects,

      // Wrap the objects with an appropriate scope,
      WrapFactory js_wrap = js_ctx.getWrapFactory();
      Object ctx_wrap = js_wrap.wrap(js_ctx, env_scope,
                                     ctx, ServerCommandContext.class);
      
      
      Object js_out = js_wrap.wrapAsJavaObject(js_ctx, env_scope,
                                               out, CommandWriter.class);
      CommandWriterWrap out_wrap = new CommandWriterWrap(out, js_out);
      Object js_vars = js_wrap.wrap(js_ctx, env_scope,
                                    vars, EnvironmentVariables.class);
      EnvironmentVariablesWrap vars_wrap =
                                   new EnvironmentVariablesWrap(vars, js_vars);

      Function process_fct = JSWrapBase.getFunction(env_scope, "process");      
      
      if (process_fct == null) {
        throw Context.reportRuntimeError("'process' export not found");
      }

      // Invoke the process function,
      Object return_ob = process_fct.call(js_ctx, env_scope, env_scope,
                               new Object[] { ctx_wrap, vars_wrap, out_wrap });

      if (Undefined.instance.equals(return_ob) || return_ob == null) {
        return "STOP";
      }

      // Return the function output,
      return (String) Context.jsToJava(return_ob, String.class);

    }
    finally {
      Context.exit();
      long end_time = System.currentTimeMillis();
//      System.out.println("Script 'process' time: " + (end_time - time_start));
    }

  }

  @Override
  public String handle(ServerCommandContext ctx, EnvironmentVariables vars,
                       CommandWriter out, ProcessInputMessage input_msg) {

    // Make sure to initialize the instance scope,
    initInstanceScope(ctx);

    InterruptibleContextFactory jsctx_factory =
                                          new InterruptibleContextFactory(ctx);
    Context js_ctx = jsctx_factory.enterContext();
    try {
      Scriptable env_scope = loadAndCompileScript(js_ctx);

      // Wrap the objects,

      // Wrap the objects with an appropriate scope,
      WrapFactory js_wrap = js_ctx.getWrapFactory();

      Object msg_wrap = js_wrap.wrap(js_ctx, env_scope,
                             input_msg.getMessage(), ProcessMessage.class);

      Object return_ob;

      Type type = input_msg.getType();
      // If the type is timed or return,
      if (type == Type.RETURN ||
          type == Type.RETURN_EXCEPTION ||
          type == Type.TIMED_CALLBACK) {

        CallableScript closure =
                      instance_scope.getFunctionClosure(input_msg.getCallId());

        // Invoke the closure,
        if (closure != null) {
          return_ob = closure.call(js_ctx, env_scope, env_scope,
                                   new Object[] { msg_wrap });
        }
        else {
          return_ob = null;
        }

      }
      // If it's a broadcast message,
      else if (type == Type.BROADCAST) {

        // Get the channel session state for the broadcast message,
        ChannelSessionState channel_state = input_msg.getBroadcastSessionState();
        ProcessChannel ch = channel_state.getProcessChannel();
        // The Collection of callable scripts for the given channel (if any)
        CallableScript closure = instance_scope.getBroadcastClosure(ch);

        if (closure != null) {
          // Invoke the closure,
          Object active_ob = closure.call(js_ctx, env_scope, env_scope,
                                          new Object[] { msg_wrap });
        }

        return_ob = null;

      }
      else {
        throw new RuntimeException("Unknown type passed to 'handle'");
      }

      // Handle the return,
      if (return_ob == null || Undefined.instance.equals(return_ob)) {
        return null;
      }

      // Return the result of the handle method,
      return (String) Context.jsToJava(return_ob, String.class);

    }
    finally {
      Context.exit();
    }

  }

  

  // -----
  
  // A scope that defines instance specific classes,

  public class InstanceScope extends JSProcessInstanceScope {

    private final ServerCommandContextImpl scommand_context;

    public InstanceScope(Scriptable scope, Scriptable prototype,
                         ServerCommandContext scommand_context) {
      super(scope, prototype);
      this.scommand_context = (ServerCommandContextImpl) scommand_context;
    }

    public InstanceScope(ServerCommandContext scommand_context) {
      super();
      this.scommand_context = (ServerCommandContextImpl) scommand_context;
    }

    @Override
    protected ProcessInstance getProcessInstance() {
      return scommand_context.getProcessInstance();
    }

    @Override
    protected void notifyMadeCallIdAssociation(int call_id) {
      // Add a callback to this command when a reply with the call_id is
      // received.
      scommand_context.addCallIdCallback(call_id, JSWrapSCommand.this);
    }

    @Override
    protected void notifyMadeChannelAssociation(ProcessChannel ch) {
      // Add a callback to this command when message from the given channel is
      // received.
      scommand_context.addChannelCallback(ch, JSWrapSCommand.this);
    }

    @Override
    protected void notifyRemovedChannelAssociation(ProcessChannel ch) {
      // Remove a callback from this command when message from the given
      // channel is received.
      scommand_context.removeChannelCallback(ch, JSWrapSCommand.this);
    }

  }


  // ---------- CommandWriterWrap functions ----------

  public static Object jsToPrintObject(Object obj) {
    if (obj instanceof String) {
      return obj;
    }
    if (obj instanceof Scriptable) {
      return Context.jsToJava(obj, String.class);
    }
    return obj;
  }

  public static String jsToString(Object obj) {
    return (String) Context.jsToJava(obj, String.class);
  }

//  private static Method findJSInvokeMethod(Class<?> cl, String method_name) {
//    try {
//      Class[] paramtype = new Class[] {
//        Context.class, Scriptable.class, Object[].class, Function.class
//      };
//
//      // The script 'define' function,
//      return cl.getMethod(method_name, paramtype);
//    }
//    catch (NoSuchMethodException e) {
//      throw new RuntimeException(e);
//    }
//  }

  
  
  public static Map<String, String> jsToStringStringMap(Object js_obj) {
    Map<String, String> params;
    // If it's a JavaScript object,
    if (js_obj instanceof Scriptable) {
      Scriptable jsob = (Scriptable) js_obj;
      Object[] ids = jsob.getIds();
      params = new HashMap();
      for (Object id : ids) {
        String key = jsToString(id);
        String val = jsToString(jsob.get(key, jsob));
        params.put(key, val);
      }
    }
    else {
      params = (Map<String, String>) js_obj;
    }
    return params;
  }

  private static EvaluatorException invalidArgCount() {
    return Context.reportRuntimeError("Invalid argument count");
  }

  
  /**
   * A JavaScript wrapper object for a EnvironmentVariables.
   */
  public static class EnvironmentVariablesWrap implements Scriptable {

    private EnvironmentVariables vars;
    private Object js_vars;
    private Scriptable parent_scope;
    private Scriptable prototype_object;

    // Zero-param constructor is necessary for 'defineClass'
    public EnvironmentVariablesWrap() {
    }
    
    EnvironmentVariablesWrap(EnvironmentVariables vars, Object js_vars) {
      this.vars = vars;
      this.js_vars = js_vars;
    }
    
    @Override
    public String getClassName() {
      return getClass().getName();
    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
      return toString();
    }

    @Override
    public String toString() {
      return "Wrap:" + vars.toString();
    }

    @Override
    public Object get(String name, Scriptable start) {
      if (name.equals("javaObject")) {
        return js_vars;
      }
      else {
        if (name == null) {
          return Undefined.instance;
        }
        String val = vars.get(name);
        if (val == null) {
          return Undefined.instance;
        }
        else {
          return Context.javaToJS(val, this);
        }
      }
    }

    @Override
    public Object get(int index, Scriptable start) {
      throw Context.reportRuntimeError("Unable to get value by index");
    }

    @Override
    public boolean has(String name, Scriptable start) {
      return vars.get(name) != null;
    }

    @Override
    public boolean has(int index, Scriptable start) {
      return false;
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
      vars.put(name, (String) Context.jsToJava(value, String.class));
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
      throw Context.reportRuntimeError("Unable to set value by index");
    }

    @Override
    public void delete(String name) {
      vars.put(name, null);
    }

    @Override
    public void delete(int index) {
      throw Context.reportRuntimeError("Unable to delete value by index");
    }

    @Override
    public Scriptable getPrototype() {
      return prototype_object;
    }

    @Override
    public void setPrototype(Scriptable prototype) {
      this.prototype_object = prototype;
    }

    @Override
    public Scriptable getParentScope() {
      return parent_scope;
    }

    @Override
    public void setParentScope(Scriptable parent) {
      this.parent_scope = parent;
    }

    @Override
    public Object[] getIds() {
      Set<String> keys = vars.keySet();
      return keys.toArray(new String[keys.size()]);
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
      return false;
    }

  }

  /**
   * A JavaScript wrapper object for a CommandWriter.
   */
  public static class CommandWriterWrap extends ScriptableObject {
    
    private CommandWriter cmd_writer;

    // Zero-param constructor is necessary for 'defineClass'
    public CommandWriterWrap() {
    }

    CommandWriterWrap(CommandWriter cmd_writer, Object js_cmd_writer) {
      this.cmd_writer = cmd_writer;

      defineFunctionProperties(
            new String[] {
              "cls", "print", "println", "printException", "flush",
              "createHtml", "createApplicationLink",
              "createCallbackLink", "createInputField",
              "createCallbackButton", "runScript",
              "sendGeneral"
            },
            getClass(),
            ScriptableObject.READONLY | ScriptableObject.PERMANENT);
      defineProperty("javaObject", js_cmd_writer,
                     ScriptableObject.READONLY | ScriptableObject.PERMANENT);

    }

    @Override
    public String getClassName() {
      return getClass().getName();
    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
      return toString();
    }

    @Override
    public String toString() {
      return "Wrap:" + cmd_writer.toString();
    }

    private CommandWriter getCommandWriter() {
      return cmd_writer;
    }

    
    public static Object cls(Context cx, Scriptable thisObj,
                              Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      out.cls();
      return null;
    }

    public static Object print(Context cx, Scriptable thisObj,
                               Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 1) {
        out.print(jsToPrintObject(args[0]));
      }
      else if (args.length == 2) {
        out.print(jsToPrintObject(args[0]), jsToString(args[1]));
      }
      else {
        throw invalidArgCount();
      }
      return null;
    }

    public static Object println(Context cx, Scriptable thisObj,
                                 Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 0) {
        out.println();
      }
      else if (args.length == 1) {
        out.println(jsToPrintObject(args[0]));
      }
      else if (args.length == 2) {
        out.println(jsToPrintObject(args[0]), jsToString(args[1]));
      }
      else {
        throw invalidArgCount();
      }
      return null;
    }

    public static Object printException(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 1) {
        out.printException((Throwable) args[0]);
      }
      else {
        throw invalidArgCount();
      }
      return null;
    }

    public static Object flush(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      out.flush();
      return null;
    }

    public static Object createHtml(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 1) {
        return out.createHtml(jsToString(args[0]));
      }
      else {
        throw invalidArgCount();
      }
    }

    public static Object createApplicationLink(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 2) {
        String label = jsToString(args[0]);
        String relative_url_query = jsToString(args[1]);
        return out.createApplicationLink(label, relative_url_query);
      }
      else {
        throw invalidArgCount();
      }
    }

    public static Object createCallbackLink(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 2) {
        String label = jsToString(args[0]);
        String callback_command = jsToString(args[1]);
        return out.createCallbackLink(label, callback_command);
      }
      else {
        throw invalidArgCount();
      }
    }

    public static Object createInputField(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      
      if (args.length == 2) {
        String var = (String) Context.jsToJava(args[0], String.class);
        int size = (Integer) Context.jsToJava(args[1], Integer.class);
        return out.createInputField(var, size);
      }
      else if (args.length == 3) {
        String var = (String) Context.jsToJava(args[0], String.class);
        int size = (Integer) Context.jsToJava(args[1], Integer.class);
        int limit = (Integer) Context.jsToJava(args[2], Integer.class);
        return out.createInputField(var, size, limit);
      }
      else {
        throw invalidArgCount();
      }
    }

    public static Object createCallbackButton(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 2) {
        String label = jsToString(args[0]);
        Map<String, String> params = jsToStringStringMap(args[1]);
        return out.createCallbackButton(label, params);
      }
      else {
        throw invalidArgCount();
      }
    }

    public static Object runScript(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 3) {
        String javascript_file = jsToString(args[0]);
        String invocation_function = jsToString(args[1]);
        String command_str = jsToString(args[2]);
        out.runScript(javascript_file, invocation_function, command_str);
      }
      else {
        throw invalidArgCount();
      }
      return null;
    }

    public static Object sendGeneral(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
      CommandWriter out = ((CommandWriterWrap) thisObj).getCommandWriter();
      if (args.length == 1) {
        String message = jsToString(args[0]);
        out.sendGeneral(message);
      }
      else {
        throw invalidArgCount();
      }
      return null;
    }

  }
  
  // The Rhino JavaScript engine's environment scope,
  
  public static class JSEnvironmentScope extends ScriptableObject {

    public JSEnvironmentScope(Scriptable scope, Scriptable prototype) {
      super(scope, prototype);
    }

    public JSEnvironmentScope() {
      super();
    }

    void init() {
      int ro_attr = ScriptableObject.PERMANENT |
                    ScriptableObject.READONLY;
      
      // Define static properties,
      defineProperty("CONSOLE_MODE", ServerCommand.CONSOLE_MODE, ro_attr);
      defineProperty("WINDOW_MODE",  ServerCommand.WINDOW_MODE, ro_attr);

      defineProperty("STOP",         new STOPFunction(this, null), ro_attr);
      defineProperty("PROMPT",       "PROMPT", ro_attr);
      defineProperty("WAIT",         new WAITFunction(this, null), ro_attr);

//      // The script 'define' function,
//      Method define_method = findJSInvokeMethod(getClass(), "defineScript");
//      FunctionObject define_fun =
//                           new FunctionObject("define", define_method, this);
//      defineProperty("define", define_fun, ro_attr);

    }

    @Override
    public String getClassName() {
      return "JSEnvironmentScope";
    }

    @Override
    public String toString() {
      return "[JSEnvironmentScope]";
    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
      return toString();
    }

    // -----

  }

  /**
   * The environment scope 'STOP' sealed function,
   */
  public static class STOPFunction extends BaseFunction {
    
    public STOPFunction() {
      super();
      sealObject();
    }

    public STOPFunction(Scriptable scope, Scriptable prototype) {
      super(scope, prototype);
      sealObject();
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
      throw Context.reportRuntimeError("Construction not allowed");
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

      if (args.length >= 1) {
        // Arg[0] must be an array,
        Object val = args[0];
        if (val == null || val.equals(Undefined.instance)) {
          return "STOP";
        }
        // Handle native array,
        if (val instanceof NativeArray) {
          // Convert it to a JSONArray string format,
          NativeArray arr = (NativeArray) val;
          JSONArray json_arr = new JSONArray();
          for (Object arr_ob : arr) {
            json_arr.put(arr_ob);
          }
          return "STOP:" + json_arr.toString();
        }
        else {
          return "STOP";
        }
      }
      else {
        return "STOP";
      }

    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
      return "STOP";
    }

  }

  /**
   * The environment scope 'WAIT' sealed function,
   */
  public static class WAITFunction extends BaseFunction {

    public WAITFunction() {
      super();
      sealObject();
    }

    public WAITFunction(Scriptable scope, Scriptable prototype) {
      super(scope, prototype);
      sealObject();
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
      throw Context.reportRuntimeError("Construction not allowed");
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                       Scriptable thisObj, Object[] args) {

      if (args.length >= 1) {
        // Arg[0] must be a number,
        int ms_wait_int = ScriptRuntime.toInt32(args[0]);
        return "WAIT:" + ms_wait_int;
      }
      else {
        return "WAIT";
      }

    }

    @Override
    public Object getDefaultValue(Class<?> typeHint) {
      return "WAIT";
    }

  }

}
