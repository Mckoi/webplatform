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

import static com.mckoi.mwpui.NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import org.mozilla.javascript.*;

/**
 *
 * @author Tobias Downer
 */
public class BindingTimerWrap extends NativeObject {

  private static final RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();
  
  @Override
  public String getClassName() {
    return "timer_wrap";
  }

  public BindingTimerWrap init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Object);
    int ro_attr = PERMANENT | READONLY;

    defineProperty("Timer", new TimerClass().init(scope), ro_attr);

    return this;
  }

  // -----

  /**
   * The Timer.now() native function. In the official node.js implementation
   * it appears to return the number of milliseconds since the machine was
   * last booted. In our implementation, we return the VM uptime because
   * computer boot up time is not available.
   */
  public static class TimerNowFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("now");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                          Scriptable thisObj, Object[] args) {
      long vm_uptime = mxBean.getUptime();
      // Is this an optimization at all, I wonder?
      if (vm_uptime < Integer.MAX_VALUE) {
        return (int) vm_uptime;
      }
      return vm_uptime;
    }

  }

  /**
   * Timer start function.
   */
  public static class TimerStartFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("start");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                          Scriptable thisObj, Object[] args) {
      
      long msec = ((Number) Context.jsToJava(args[0], Number.class)).longValue();
      // What's this for? Seems to always be passed in as '0'
      long v2 = ((Number) Context.jsToJava(args[1], Number.class)).longValue();

      if (v2 != 0) {
        throw Context.reportRuntimeError("'repeat' argument not supported");
      }

      JSProcessSharedState pstate =
            (JSProcessSharedState) cx.getThreadLocal(NODE_SHARED_PROCESS_STATE);
      pstate.postTimedCallback((ScriptableObject) thisObj, msec);

//      System.out.println("Timer.start called: msec = " + msec);

      return Boolean.TRUE;

    }

  }

  /**
   * Timer close function.
   */
  public static class TimerCloseFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("close");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                          Scriptable thisObj, Object[] args) {

      // Should we do anything when a timer is closed? We don't have any
      // natively stored information per timer do we?

      return Boolean.TRUE;

    }

  }

  public static class TimerRefFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("ref");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                          Scriptable thisObj, Object[] args) {
      thisObj.put("_ref", thisObj, "REF");
      return Boolean.TRUE;
    }

  }
  
  public static class TimerUnrefFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("unref");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                          Scriptable thisObj, Object[] args) {
      thisObj.put("_ref", thisObj, "UNREF");
      return Boolean.TRUE;
    }

  }

  /**
   * The native Timer class allows posting actions on the timer queue.
   */
  public static class TimerClass extends NativeFunction {
    
    public TimerClass init(Scriptable scope) {
      ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Function);
      int ro_attr = PERMANENT | READONLY;
      int rod_attr = PERMANENT | READONLY | DONTENUM;

      // Static Timer class properties,
      defineProperty("kOnTimeout", 0, ro_attr);
      defineProperty("now", new TimerNowFun().init(scope), ro_attr);

      ScriptableObject timer_prototype =
                                    (ScriptableObject) get("prototype", this);
      timer_prototype.defineProperty(
                          "start", new TimerStartFun().init(scope), ro_attr);
      timer_prototype.defineProperty(
                          "close", new TimerCloseFun().init(scope), ro_attr);
      timer_prototype.defineProperty(
                          "ref", new TimerRefFun().init(scope), ro_attr);
      timer_prototype.defineProperty(
                          "unref", new TimerUnrefFun().init(scope), ro_attr);

      return this;
    }

    @Override
    public String getClassName() {
      return "Timer";
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
      return super.construct(cx, scope, args);
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
  
}
