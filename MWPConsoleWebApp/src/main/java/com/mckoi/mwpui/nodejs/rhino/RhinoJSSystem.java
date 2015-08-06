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

package com.mckoi.mwpui.nodejs.rhino;

import com.mckoi.mwpui.CommandTimeoutException;
import com.mckoi.mwpui.CommandWriter;
import com.mckoi.mwpui.EnvironmentVariables;
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.mwpui.nodejs.*;
import com.mckoi.webplatform.rhino.JSWrapBase;
import com.mckoi.webplatform.rhino.KillSignalException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.mozilla.javascript.*;

/**
 * An implementation of GJSSystem that uses the Rhino JavaScript engine to
 * support the NodeJS native libraries.
 *
 * @author Tobias Downer
 */
public class RhinoJSSystem extends AbstractGJSSystem {

  private static final JSEnvironmentScope ENV_SCOPE;

  static {
    JSEnvironmentScope env_scope;
    Context js_ctx = JSWrapBase.generic_context_factory.enterContext();
    try {
      env_scope = new JSEnvironmentScope();

      // Initialize standard stuff and seal it,,
//      js_ctx.initStandardObjects(env_scope, true);
      js_ctx.initSafeStandardObjects(env_scope, true);

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
   * Key for value we link a Rhino object with GJS.
   */
  private final static String RHINO_GJS_KEY = "mckoi.GJSObject";

  private Context context;
  private GJSProcessSharedState shared_state;
  private GJSObject global_object;

  
  public RhinoJSSystem(String[] exec_args_v_arr, String[] args_v_arr) {
    super(exec_args_v_arr, args_v_arr);
  }

  Context getContext() {
    return context;
  }



  @Override
  public String getSystemId() {
    return "rhino";
  }

  @Override
  public GJSObject getGlobalObject() {
    if (global_object == null) {
      // Create a native global object,
      ScriptableObject global_native_ob = new ClassedNativeObject("global");
      global_native_ob.setParentScope(null);
      global_native_ob.setPrototype(ENV_SCOPE);
      GJSObject ret = new RhinoGJSObjectWrap(this, global_native_ob);
      associateNativeWithGJS(global_native_ob, ret);
      global_object = ret;
    }
    return global_object;

  }

  @Override
  public GJSProcessSharedState getSharedState() {
    return shared_state;
  }

  @Override
  public Object callFunction(
          GJSObject this_object, GJSObject function, Object[] args) {

    try {

      BaseFunction fun = (BaseFunction) toScriptable(function);
      Scriptable this_obj = toScriptable(this_object);
      Object result = fun.call(context, getGlobalNative(),
                                        this_obj, wrapArgsAsRhino(args));
      return wrapAsGJS(result);

    }
    catch (RhinoException ex) {
      throw wrappedGJSException(ex);
    }

  }

//  private final Map<ScriptableObject, GJSObject> nat_assoc_hash = new HashMap();
  private void associateNativeWithGJS(ScriptableObject n, GJSObject g) {
//    nat_assoc_hash.put(n, g);
    n.associateValue(RHINO_GJS_KEY, g);
  }
  private GJSObject getGJSFromNative(ScriptableObject n) {
//    return nat_assoc_hash.get(n);
    return (GJSObject) n.getAssociatedValue(RHINO_GJS_KEY);
  }
  
  
  /**
   * Returns the Scriptable object representing the 'global' object.
   * 
   * @return 
   */
  Scriptable getGlobalNative() {
    Object cur_native = getGlobalObject().internalGetNative();
    if (cur_native == null) {
      // Oops. This means the call order is messed up. 'setNativeObjectFor'
      // method needs to be called with the global object before this is
      // called.
      throw new RuntimeException("Global native object not defined");
    }
    return (Scriptable) cur_native;
  }

  /**
   * Converts or retrieves the native Scriptable object for the given
   * GJSObject.
   * 
   * @param js_object
   * @return 
   */
  final Scriptable toScriptable(GJSObject js_object) {
    Object cur_native = js_object.internalGetNative();
    if (cur_native == null) {
      ScriptableObject native_ob = convertJSToRhino(js_object);
//      ScriptRuntime.setBuiltinProtoAndParent(
//                      native_ob, getGlobalNative(), TopLevel.Builtins.Object);
      js_object.internalSetNative(native_ob);
      return native_ob;
    }
    else {
      return (Scriptable) cur_native;
    }
  }

  /**
   * Wraps the given native Rhino JavaScript object as an object compatible
   * with the GJS layer.
   * 
   * @param rhino_value
   * @return 
   */
  final Object wrapAsGJS(Object rhino_value) {
    if (rhino_value == null || UniqueTag.NULL_VALUE.equals(rhino_value)) {
      return null;
    }
    else if (Undefined.instance.equals(rhino_value) ||
             UniqueTag.NOT_FOUND.equals(rhino_value)) {
      return GJSStatics.UNDEFINED;
    }
    
    else if (rhino_value instanceof CharSequence) {
      return rhino_value.toString();
    }
    else if (rhino_value instanceof Number || rhino_value instanceof Boolean) {
      return rhino_value;
    }
    else if (rhino_value instanceof ScriptableObject) {
      ScriptableObject rhino_scriptable = (ScriptableObject) rhino_value;
      GJSObject gjs_ob = getGJSFromNative(rhino_scriptable);
      if (gjs_ob == null) {
        gjs_ob = new RhinoGJSObjectWrap(this, rhino_scriptable);
        associateNativeWithGJS(rhino_scriptable, gjs_ob);
      }
      return gjs_ob;
    }
    else if (rhino_value instanceof RhinoException) {
      return wrappedGJSException((RhinoException) rhino_value);
    }
    else {
      return rhino_value;
//      throw new GJavaScriptException(
//                  "Unable to convert Rhino object: " + rhino_value.getClass());
    }
  }

  /**
   * Wraps the given arguments (from the Rhino engine) as GJS values and
   * returns the new arguments array.
   * 
   * @param rhino_args
   * @return 
   */
  final Object[] wrapArgsAsGJS(Object[] rhino_args) {
    if (rhino_args.length == 0) {
      return rhino_args;
    }
    int len = rhino_args.length;
    Object[] arg2 = new Object[len];
    for (int i = 0; i < len; ++i) {
      arg2[i] = wrapAsGJS(rhino_args[i]);
    }
    return arg2;
  }

  /**
   * Wraps the given GJS layer JavaScript object as an object compatible with
   * the Rhino JavaScript engine.
   * 
   * @param gvalue
   * @return 
   */
  final Object wrapAsRhino(Object gvalue) {
    if (gvalue == null) {
      return null;
    }
    else if (GJSStatics.UNDEFINED.equals(gvalue)) {
      return Undefined.instance;
    }
    else if (gvalue instanceof CharSequence) {
      return gvalue;
    }
    else if (gvalue instanceof Number || gvalue instanceof Boolean) {
      return gvalue;
    }
    else if (gvalue instanceof GJSObject) {
      GJSObject gjs_object = (GJSObject) gvalue;
      // Convert to rhino object,
      Scriptable rhino_ob = (Scriptable) gjs_object.internalGetNative();
      if (rhino_ob == null) {
        rhino_ob = convertJSToRhino(gjs_object);
//        rhino_ob = new NativeObjectWrapGJS(gjs_object);
        gjs_object.internalSetNative(rhino_ob);
      }
      return rhino_ob;
    }
    else if (gvalue instanceof GJavaScriptException) {
      RhinoException ex = wrappedRhinoException((GJavaScriptException) gvalue);
      return ScriptRuntime.wrapException(ex, getGlobalNative(), context);
    }
    else {
      return gvalue;
//      throw new GJavaScriptException(
//                  "Unable to convert GJS object: " + gvalue.getClass());
    }
  }

  /**
   * Wraps the given GJS argument values to objects appropriate for use in
   * Rhino, and returns the new arguments array.
   * 
   * @param gjs_args
   * @return 
   */
  final Object[] wrapArgsAsRhino(Object[] gjs_args) {
    if (gjs_args.length == 0) {
      return gjs_args;
    }
    int len = gjs_args.length;
    Object[] arg2 = new Object[len];
    for (int i = 0; i < len; ++i) {
      arg2[i] = wrapAsRhino(gjs_args[i]);
    }
    return arg2;
  }

  @Override
  public Object compileSourceCode(String code, String script_filename, int first_line_num) {
    return context.compileString(
                  code, script_filename, first_line_num, RhinoJSSystem.class);
  }

  @Override
  public Object executeCode(Object compiled_string) {
    Script script = (Script) compiled_string;
    Object result = script.exec( getContext(), getGlobalNative() );
    return wrapAsGJS(result);
  }
  
  @Override
  public GJSObject asJavaScriptArray(Object[] arr) {
    ScriptableObject native_array = (ScriptableObject) context.newArray(
                                    getGlobalNative(), wrapArgsAsRhino(arr));
    GJSObject ret = new RhinoGJSObjectWrap(this, native_array);
    associateNativeWithGJS(native_array, ret);
    return ret;
  }

  @Override
  public GJSObject newJavaScriptObject() {
    return newJavaScriptObject(null);
//    ScriptableObject native_ob =
//                (ScriptableObject) context.newObject(getGlobalNative());
//    GJSObject ret = new RhinoGJSObjectWrap(this, native_ob);
//    associateNativeWithGJS(native_ob, ret);
//    return ret;
  }

  @Override
  public GJSObject newJavaScriptObject(String class_name) {
    Scriptable scope = getGlobalNative();
    NativeObject native_ob;
    if (class_name == null) {
      native_ob = new NativeObject();
    }
    else {
      native_ob = new ClassedNativeObject(class_name);
    }
    ScriptRuntime.setBuiltinProtoAndParent(native_ob, scope,
                                              TopLevel.Builtins.Object);
    GJSObject ret = new RhinoGJSObjectWrap(this, native_ob);
    associateNativeWithGJS(native_ob, ret);
    return ret;
  }

  @Override
  public ByteBuffer getExternalArrayDataOf(GJSObject source) {
    // Get the internal Rhino object,
    ScriptableObject rhino_ob = (ScriptableObject) source.internalGetNative();
    ExternalArrayData external_data_arr = rhino_ob.getExternalArrayData();
    // Is there an external data array?
    if (external_data_arr == null) {
      return null;
    }
    // Check the type,
    if (!(external_data_arr instanceof SmallocArrayData)) {
      throw new GJavaScriptException("Invalid external array data type");
    }
    // Good to go,
    SmallocArrayData s_data_arr = (SmallocArrayData) external_data_arr;
    return s_data_arr.getByteBuffer();
  }

  @Override
  public ByteBuffer allocateExternalArrayDataOf(GJSObject source, int alloc_size) {

    // Get the internal Rhino object,
    ScriptableObject rhino_ob = (ScriptableObject) source.internalGetNative();
    if (rhino_ob.getExternalArrayData() != null) {
      throw new GJavaScriptException("Object already has external array data");
    }
    SmallocArrayData external_data_arr = new SmallocArrayData(alloc_size);
    // Set it up to be an array,
    rhino_ob.setExternalArrayData(external_data_arr);
    // Return it,
    return external_data_arr.getByteBuffer();

  }

  @Override
  public String getAlternativeStackTraceOf(GJavaScriptException ex) {
    RhinoException rex = (RhinoException) ex.internalGetException();
    if (rex != null) {
      StringBuilder b = new StringBuilder();
      ScriptStackElement[] script_stack = rex.getScriptStack(250, null);
      for (ScriptStackElement element : script_stack) {
        element.renderV8Style(b);
        b.append('\n');
      }
      return b.toString();
    }
    return null;
  }


  // -----

  /**
   * Returns a Rhino ScriptableObject that represents the given GJSObject.
   * 
   * @param gjs_ob
   * @return 
   */
  ScriptableObject convertJSToRhino(GJSObject gjs_ob) {

    if (gjs_ob instanceof RhinoGJSObjectWrap) {
      throw new IllegalStateException();
    }

    // In Rhino, all functions need to extend BaseFunction,
    if (gjs_ob.isFunction()) {
      ScriptableObject ob = new RhinoGJSGenericFunction(this, gjs_ob);
      associateNativeWithGJS(ob, gjs_ob);
      ScriptRuntime.setBuiltinProtoAndParent(
                            ob, getGlobalNative(), TopLevel.Builtins.Function);
      return ob;
    }
    // All other objects are represented by a NativeObject,
    else {
      ScriptableObject ob = new RhinoGJSGenericObject(this, gjs_ob);
      associateNativeWithGJS(ob, gjs_ob);
      ScriptRuntime.setBuiltinProtoAndParent(
                            ob, getGlobalNative(), TopLevel.Builtins.Object);
      return ob;
    }

  }



  /**
   * Support method for getId and getAllIds.
   * 
   * @param ex
   * @param ids
   * @return 
   */
  private static Object[] mergeSets(Set<String> ex, Object[] ids) {
    if (ex.isEmpty()) {
      return ids;
    }
    if (ids == null) {
      ids = new Object[0];
    }
    List<Object> out = new ArrayList(Arrays.asList(ids));
    for (String id : ex) {
      if (!out.contains(id)) {
        out.add(id);
      }
    }
    return out.toArray();
  }

  /**
   * Wraps a GJavaScriptException as a Rhino JavaScript exception.
   * 
   * @param ex
   * @return 
   */
  private static RhinoException wrappedRhinoException(GJavaScriptException ex) {
    EvaluatorException rhino_ex = Context.reportRuntimeError(ex.getMessage());
    rhino_ex.setStackTrace(ex.getStackTrace());
    return rhino_ex;
  }
  
  /**
   * Wraps a Rhino JavaScript exception as a GJS runtime exception.
   * 
   * @param ex
   * @return 
   */
  GJavaScriptException wrappedGJSException(RhinoException ex) {
    GJavaScriptException g_ex = new GJavaScriptException(ex.getMessage());
    g_ex.internalSetException(ex);
    g_ex.setStackTrace(ex.getStackTrace());
    return g_ex;
  }



  /**
   * Sets up a JavaScript context.
   * 
   * @param ctx
   * @param vars
   * @param out
   * @param node_source_loader
   * @param shared_process_state 
   */
  @Override
  public void setupContext(ServerCommandContext ctx,
                           EnvironmentVariables vars, CommandWriter out,
                           GJSNodeSourceLoader node_source_loader,
                           GJSProcessSharedState shared_process_state) {

    JSWrapBase.JSWrapContextFactory jsctx_factory =
//                                          new JSWrapBase.JSWrapContextFactory();
                                          new InterruptibleContextFactory(ctx);
    Context js_ctx = jsctx_factory.enterContext();

    shared_process_state.setNodeSourceLoader(node_source_loader);
    shared_process_state.setServerCommandContext(ctx);
    shared_process_state.setEnv(vars);
    shared_process_state.setCommandWriter(out);

    this.context = js_ctx;
    this.shared_state = shared_process_state;
    GJSRuntime.registerSystem(this);

  }

  /**
   * Releases the JavaScript context.
   */
  @Override
  public void releaseContext() {
    Context.exit();
    GJSRuntime.deregisterSystem();
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

  // A NativeObject that has the JavaScript [[Class]] property defined.
  
  public static class ClassedNativeObject extends NativeObject {

    private final String class_name;

    public ClassedNativeObject(String class_name) {
      this.class_name = class_name;
    }

    @Override
    public String getClassName() {
      return class_name;
    }

  }
  
  // The Rhino JavaScript engine's environment scope,
  
  private static class JSEnvironmentScope extends ScriptableObject {

    public JSEnvironmentScope(Scriptable scope, Scriptable prototype) {
      super(scope, prototype);
    }

    public JSEnvironmentScope() {
      super();
    }

    void init() {
    }

    @Override
    public Object get(String name, Scriptable start) {
      return super.get(name, start);
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

  }

  /**
   * A Rhino object the wraps a none-function object.
   */
  public final static class RhinoGJSGenericObject extends NativeObject {

    private final RhinoJSSystem system;
    private final GJSObject gjs_ob;
    
    public RhinoGJSGenericObject(RhinoJSSystem system, GJSObject ob) {
      super();
      this.system = system;
      this.gjs_ob = ob;
    }

    @Override
    public String getClassName() {
      // If the gjs object has a non-null class name then return it. Otherwise
      // use the default behaviour of Rhino's NativeObject.
      String gjs_classname = gjs_ob.getClassName();
      if (gjs_classname == null) {
        return super.getClassName();
      }
      return gjs_classname;
    }

    @Override
    public void delete(String name) {
      gjs_ob.removeMember(name);
      super.delete(name);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
      super.put(name, start, value);
    }

    @Override
    public Object get(String name, Scriptable start) {
      Object val = super.get(name, start);
      if (NOT_FOUND.equals(val)) {
        Object v = gjs_ob.getMember(name);
        if (GJSStatics.UNDEFINED.equals(v)) {
          return NOT_FOUND;
        }
        return system.wrapAsRhino(v);
      }
      return val;
    }

    @Override
    public boolean has(String name, Scriptable start) {
      if (!super.has(name, start)) {
        return gjs_ob.hasMember(name);
      }
      return true;
    }

    @Override
    public Object[] getAllIds() {
      return mergeSets(gjs_ob.keySet(), super.getAllIds());
    }

    @Override
    public Object[] getIds() {
      return mergeSets(gjs_ob.keySet(), super.getIds());
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
      super.put(index, start, value);
    }

    @Override
    public Object get(int index, Scriptable start) {
      Object val = super.get(index, start);
      if (NOT_FOUND.equals(val)) {
        Object v = gjs_ob.getSlot(index);
        if (GJSStatics.UNDEFINED.equals(v)) {
          return NOT_FOUND;
        }
        return system.wrapAsRhino(v);
      }
      return val;
    }

    @Override
    public boolean has(int index, Scriptable start) {
      if (!super.has(index, start)) {
        return gjs_ob.hasSlot(index);
      }
      return true;
    }

  }

  /**
   * A Rhino object the wraps an internal GJS function.
   */
  public final static class RhinoGJSGenericFunction extends BaseFunction {

    private final RhinoJSSystem system;
    private final GJSObject gjs_ob;
    
    public RhinoGJSGenericFunction(RhinoJSSystem system, GJSObject ob) {
      super();
      this.system = system;
      this.gjs_ob = ob;
    }

    @Override
    public String getFunctionName() {
      if (gjs_ob instanceof GJSAbstractFunction) {
        GJSAbstractFunction fun = (GJSAbstractFunction) gjs_ob;
        String function_name = fun.getFunctionName();
        if (function_name != null) {
          return function_name;
        }
      }
      return super.getFunctionName(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected boolean hasPrototypeProperty() {
      return true;
    }
    
    @Override
    protected Object getPrototypeProperty() {
      return system.wrapAsRhino(gjs_ob.getPrototype());
    }
    
    @Override
    public String getClassName() {
      // If the gjs object has a non-null class name then return it. Otherwise
      // use the default behaviour of Rhino's NativeObject.
      String gjs_classname = gjs_ob.getClassName();
      if (gjs_classname == null) {
        return super.getClassName();
      }
      return gjs_classname;
    }

    
    @Override
    public void delete(String name) {
      gjs_ob.removeMember(name);
      super.delete(name);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
      super.put(name, start, value);
    }

    @Override
    public Object get(String name, Scriptable start) {
      Object val = super.get(name, start);
      if (NOT_FOUND.equals(val)) {
        Object v = gjs_ob.getMember(name);
        if (GJSStatics.UNDEFINED.equals(v)) {
          return NOT_FOUND;
        }
        return system.wrapAsRhino(v);
      }
      return val;
    }

    @Override
    public boolean has(String name, Scriptable start) {
      if (!super.has(name, start)) {
        return gjs_ob.hasMember(name);
      }
      return true;
    }

    @Override
    public Object[] getAllIds() {
      return mergeSets(gjs_ob.keySet(), super.getAllIds());
    }

    @Override
    public Object[] getIds() {
      return mergeSets(gjs_ob.keySet(), super.getIds());
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
      super.put(index, start, value);
    }

    @Override
    public Object get(int index, Scriptable start) {
      Object val = super.get(index, start);
      if (NOT_FOUND.equals(val)) {
        Object v = gjs_ob.getSlot(index);
        if (GJSStatics.UNDEFINED.equals(v)) {
          return NOT_FOUND;
        }
        return system.wrapAsRhino(v);
      }
      return val;
    }

    @Override
    public boolean has(int index, Scriptable start) {
      if (!super.has(index, start)) {
        return gjs_ob.hasSlot(index);
      }
      return true;
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
      try {
        Object construct_result = gjs_ob.newObject(system.wrapArgsAsGJS(args));
        Scriptable s = (Scriptable) system.wrapAsRhino(construct_result);
        if (s != this) {
          // NOTE: The prototype and parent scope are getting hard coded here
          //   when we might return an object from 'newObject' where we don't
          //   want the prototype and parent scope changed.
          s.setParentScope(getParentScope());
          s.setPrototype(getClassPrototype());
        }
        return s;
      }
      // Wrap any generated exceptions with Rhino exception,
      catch (GJavaScriptException ex) {
        throw wrappedRhinoException(ex);
      }
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
      try {
        Object call_result = gjs_ob.call(
                      system.wrapAsGJS(thisObj), system.wrapArgsAsGJS(args));
        return system.wrapAsRhino(call_result);
      }
      // Wrap any generated exceptions with Rhino exception,
      catch (GJavaScriptException ex) {
        throw wrappedRhinoException(ex);
      }
    }

  }

}
