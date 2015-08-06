/**
 * com.mckoi.webplatform.rhino.JSWrapProcessOperation  Nov 16, 2012
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

package com.mckoi.webplatform.rhino;

import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import com.mckoi.webplatform.rhino.JSProcessInstanceScope.CallableScript;
import org.mozilla.javascript.*;
import org.mozilla.javascript.commonjs.module.Require;

/**
 * A ProcessOperation implementation that wraps a Rhino JavaScript script.
 *
 * @author Tobias Downer
 * @deprecated
 */

public class JSWrapProcessOperation { //implements ProcessOperation {

//  private static final JSEnvironmentScope ENV_SCOPE;
//
//  static {
//    JSEnvironmentScope env_scope;
//    Context js_ctx = JSWrapBase.generic_context_factory.enterContext();
//    try {
//      env_scope = new JSEnvironmentScope();
//
//      // Initialize standard stuff and seal it,,
//      js_ctx.initStandardObjects(env_scope, true);
//
//      // Add system classes/statics to 'env_scope'
//      env_scope.init();
//
//      // Seal the object,
//      env_scope.sealObject();
//
//    }
//    finally {
//      Context.exit();
//    }
//    ENV_SCOPE = env_scope;
//  }
//
//  /**
//   * The absolute script module id (eg. '/admin/bin/process/act').
//   */
//  private FileName script_module_id;  
//
//  /**
//   * The process instance.
//   */
//  private ProcessInstance process_instance;
//
//  /**
//   * The JavaScript instance.
//   */
//  private Scriptable js_process_operation;
//
//  /**
//   * The instance scope.
//   */
//  private JSProcessInstanceScope instance_scope;
//
//  /**
//   * Initializes the instance.
//   */
//  private void initInstance(ProcessInstance instance) {
//    this.process_instance = instance;
//    if (instance_scope == null) {
//      instance_scope = new InstanceScope(instance);
//    }
//  }
//
//  /**
//   * Loads and compiles the script.
//   */
//  private Scriptable loadAndCompileScript(Context js_ctx) {
//
//    if (js_process_operation == null) {
//
//      instance_scope.setPrototype(ENV_SCOPE);
//      instance_scope.setParentScope(null);
//      instance_scope.init();
//
//      Scriptable env_scope = js_ctx.newObject(instance_scope);
//      // The prototype of this object is the static ENV_SCOPE object that
//      // includes the standard JavaScript and Mckoi API functions.
//      env_scope.setPrototype(instance_scope);
//      env_scope.setParentScope(null);
//
//      String module_id = script_module_id.toString();
//
//      // Make the 'require' function, not sandboxed with no init or exit scripts,
//      Require require =
//            new Require(js_ctx, env_scope,
//                        JSWrapBase.getModuleSourceLoader(), null, null, false);
//
//      js_process_operation = require.requireMain(js_ctx, module_id);
//
//    }
//
//    return js_process_operation;
//
//  }  
//
//  /**
//   * A ContextFactory that is interruptible.
//   */
//  private JSWrapBase.JSWrapContextFactory interruptible_context =
//                                        new JSWrapBase.JSWrapContextFactory() {
//
//    private long last_check = -1;
//
//    @Override
//    protected Context makeContext() {
//      Context c = super.makeContext();
//      // Note that Rhino adds an observer to all loops so this does not need
//      // to be a small number to effectively be able to interrupt a running
//      // program.
//      c.setInstructionObserverThreshold(100000);
//      return c;
//    }
//
//    @Override
//    protected void observeInstructionCount(Context c, int instructionCount) {
//
//      // Only consume every 300 ms minimum,
//      long time_now = System.currentTimeMillis();
//      if (time_now > (last_check + 300)) {
//        last_check = time_now;
//        // Consume signals and handle them,
//        handleAnySignals();
//      }
//
//      // PENDING; This should throw an exception when an interrupt message is
//      //   seen.
//
//      super.observeInstructionCount(c, instructionCount);
//    }
//
//  };
//
//  /**
//   * Default signal handler,
//   */
//  private void handleAnySignals() {
//
//    // PENDING: Should we call a 'signal' method or just set flags that the
//    //   use code can optionally look at?
//
//    while (true) {
//      String[] signal = process_instance.consumeSignal();
//      // Finish if no signal consumed,
//      if (signal == null) {
//        return;
//      }
//      // Is this a kill signal?
//      if (signal.length > 0) {
//        if (signal[0].equals("kill")) {
//          throw new KillSignalException();
//        }
//      }
//    }
//
//  }
//
//  /**
//   * Process the 'init' message,
//   */
//  private void processInitMessage(String script_file_name) {
//    if (script_file_name.endsWith(".mjs")) {
//      this.script_module_id = new FileName(
//                 script_file_name.substring(0, script_file_name.length() - 4));
//    }
//    else if (script_file_name.endsWith(".js")) {
//      this.script_module_id = new FileName(
//                 script_file_name.substring(0, script_file_name.length() - 3));
//    }
//    else {
//      script_module_id = new FileName(script_file_name);
//    }
//  }
//
//
//  private boolean callFunction(ProcessInstance instance,
//                               String function_name) {
//
//    Context js_ctx = interruptible_context.enterContext();
//    try {
//      Scriptable env_scope = loadAndCompileScript(js_ctx);
//      // Wrap the args,
//      WrapFactory js_wrap = js_ctx.getWrapFactory();
//      Object instance_wrap = js_wrap.wrap(js_ctx, env_scope,
//                                          instance, ProcessInstance.class);
//      // Get the 'func' export,
//      Function process_fct = JSWrapBase.getFunction(env_scope, function_name);
//      if (process_fct == null) {
//        return false;
//      }
//      // Invoke the 'func' function,
//      process_fct.call(js_ctx, env_scope, env_scope,
//                       new Object[] { instance_wrap });
//    }
//    finally {
//      Context.exit();
//    }
//
//    return true;
//
//  }
//
//  private void callFunctionInvoke(ProcessInstance instance,
//                                  ProcessInputMessage input_msg) {
//
//    Context js_ctx = interruptible_context.enterContext();
//    try {
//      Scriptable env_scope = loadAndCompileScript(js_ctx);
//      // Wrap the args,
//      WrapFactory js_wrap = js_ctx.getWrapFactory();
//      Object instance_wrap = js_wrap.wrap(js_ctx, env_scope,
//                                          instance, ProcessInstance.class);
//      Object msg_wrap = js_wrap.wrap(js_ctx, env_scope,
//                                     input_msg, ProcessInputMessage.class);
//
//      // Get the 'func' export,
//      Function process_fct = JSWrapBase.getFunction(env_scope, "func");
//      if (process_fct != null) {
//        // Invoke the 'func' function,
//        process_fct.call(js_ctx, env_scope, env_scope,
//                         new Object[] { instance_wrap, msg_wrap });
//      }
//    }
//    finally {
//      Context.exit();
//    }
//
//  }
//
//  /**
//   * Calls a function return closure.
//   */
//  private void callReturnClosure(ProcessInputMessage input_msg) {
//
//    int call_id = input_msg.getCallId();
//    // Call any closures we have for this,
//    CallableScript script = instance_scope.getFunctionClosure(call_id);
//    if (script == null) {
//      return;
//    }
//
//    Context js_ctx = interruptible_context.enterContext();
//    try {
//      Scriptable env_scope = loadAndCompileScript(js_ctx);
//      // Wrap the args,
//      WrapFactory js_wrap = js_ctx.getWrapFactory();
//      Object msg_wrap = js_wrap.wrap(js_ctx, env_scope,
//                                     input_msg, ProcessInputMessage.class);
//
//      // Invoke the closure,
//      Object return_value =
//          script.call(js_ctx, env_scope, env_scope, new Object[] { msg_wrap });
//
//    }
//    finally {
//      Context.exit();
//    }
//
//  }
//
//  /**
//   * Call the broadcast closures.
//   */
//  private void callBroadcastClosures(ProcessInputMessage input_msg) {
//
//    // Get the channel session state for the broadcast message,
//    ChannelSessionState channel_state = input_msg.getBroadcastSessionState();
//    ProcessChannel ch = channel_state.getProcessChannel();
//    // The Collection of callable scripts for the given channel (if any)
//    CallableScript closure = instance_scope.getBroadcastClosure(ch);
//
//    if (closure != null) {
//
//      Context js_ctx = interruptible_context.enterContext();
//      try {
//        Scriptable env_scope = loadAndCompileScript(js_ctx);
//        // Wrap the args,
//        WrapFactory js_wrap = js_ctx.getWrapFactory();
//        Object msg_wrap = js_wrap.wrap(js_ctx, env_scope,
//                                       input_msg, ProcessInputMessage.class);
//        // Invoke the closure,
//        closure.call(js_ctx, env_scope, env_scope, new Object[] { msg_wrap });
//
//      }
//      finally {
//        Context.exit();
//      }
//
//    }
//
//  }
//
//
//  // -----------
//
//  @Override
//  public ProcessOperation.Type getType() {
//    return ProcessOperation.Type.TRANSIENT;
//  }
//
//  @Override
//  public void suspend(StateMap state) {
//  }
//
//  @Override
//  public void resume(ProcessInstance instance) {
//
//    initInstance(instance);
//    this.process_instance = instance;
//
//    StateMap state_map = instance.getStateMap();
//    String script_fn_str = state_map.get(".script");
//    if (script_fn_str != null) {
//      script_module_id = new FileName(script_fn_str);
//    }
//
//    // Call the 'resume' function,
//    callFunction(instance, "resume");
//
//  }
//
//  @Override
//  public void function(ProcessInstance instance) {
//
//    initInstance(instance);
//    this.process_instance = instance;
//
//    try {
//      handleAnySignals();
//
//      // If this is the initial message,
//      if (script_module_id == null) {
//
//        // Consume the message,
//        ProcessInputMessage input_msg = instance.consumeMessage();
//        // The input message type,
//        ProcessInputMessage.Type type = input_msg.getType();
//        // If it's an invoke function type,
//        if (type == ProcessInputMessage.Type.FUNCTION_INVOKE) {
//          // Decode the arguments and process the init message,
//          ProcessMessage msg = input_msg.getMessage();
//          Object[] args = ByteArrayProcessMessage.decodeArgsList(msg);
//          // If it's the init message,
//          if (args[0].equals(".init")) {
//            processInitMessage((String) args[1]);
//            instance.getStateMap().put(".script", script_module_id.toString());
//
//            try {
//              // Call the 'init' function in JavaScript,
//              callFunction(instance, "init");
//
//              // Reply success (or failure),
//              instance.sendReply(input_msg,
//                                 ByteArrayProcessMessage.emptyMessage());
//
//            }
//            // If error, return with error message,
//            catch (RuntimeException e) {
//              instance.sendFailure(input_msg, e, true);
//              throw e;
//            }
//          }
//        }
//      }
//
//      // Assert the first message is '.init'
//      if (script_module_id == null) {
//        throw new RuntimeException("Invalid initial process message");
//      }
//
//      // Consume the messages,
//      while (true) {
//        // If it's a function return,
//        ProcessInputMessage input_msg = instance.consumeMessage();
//        // If no more messages to consume,
//        if (input_msg == null) {
//          return;
//        }
//
//        // Otherwise handle the message,
//        ProcessInputMessage.Type type = input_msg.getType();
//        if (type == ProcessInputMessage.Type.FUNCTION_INVOKE) {
//          callFunctionInvoke(instance, input_msg);
//        }
//        if (type == ProcessInputMessage.Type.BROADCAST) {
//          callBroadcastClosures(input_msg);
//        }
//        // Callback messages,
//        else if (type == ProcessInputMessage.Type.RETURN ||
//                 type == ProcessInputMessage.Type.RETURN_EXCEPTION ||
//                 type == ProcessInputMessage.Type.TIMED_CALLBACK) {
//          callReturnClosure(input_msg);
//        }
//      }
//
//    }
//    // If we caught a kill signal exception,
//    catch (KillSignalException e) {
//      // Close the instance
//      process_instance.close();
//      // PENDING: Should we make this behaviour configurable, perhaps by
//      //   calling a JavaScript method to handle it?
//    }
//
//  }
//
//
//
//  // -----------
//
//  // A scope that defines instance specific classes,
//
//  public static class InstanceScope extends JSProcessInstanceScope {
//
//    private final ProcessInstance process_instance;
//
//    public InstanceScope(Scriptable scope, Scriptable prototype,
//                         ProcessInstance process_instance) {
//      super(scope, prototype);
//      this.process_instance = process_instance;
//    }
//
//    public InstanceScope(ProcessInstance process_instance) {
//      super();
//      this.process_instance = process_instance;
//    }
//
//    @Override
//    protected ProcessInstance getProcessInstance() {
//      return process_instance;
//    }
//
//    @Override
//    protected void notifyMadeCallIdAssociation(int call_id) {
//      // No op,
//    }
//
//    @Override
//    protected void notifyMadeChannelAssociation(ProcessChannel ch) {
//      // No op,
//    }
//
//    @Override
//    protected void notifyRemovedChannelAssociation(ProcessChannel ch) {
//      // No op,
//    }
//
//  }
//
//
//  // The Rhino JavaScript engine's environment scope.
//  // This contains any global scope function we wish to define for
//  // process operations.
//
//  public static class JSEnvironmentScope extends ScriptableObject {
//
//    public JSEnvironmentScope(Scriptable scope, Scriptable prototype) {
//      super(scope, prototype);
//    }
//
//    public JSEnvironmentScope() {
//      super();
//    }
//
//    void init() {
//
//    }
//
//    @Override
//    public String getClassName() {
//      return "JSEnvironmentScope";
//    }
//
//    @Override
//    public String toString() {
//      return "[JSEnvironmentScope]";
//    }
//
//    @Override
//    public Object getDefaultValue(Class<?> typeHint) {
//      return toString();
//    }
//
//  }

}
