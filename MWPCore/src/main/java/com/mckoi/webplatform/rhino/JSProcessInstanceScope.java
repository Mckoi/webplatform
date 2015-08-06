/**
 * com.mckoi.webplatform.rhino.JSProcessInstanceScope  Nov 26, 2012
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

import com.mckoi.process.*;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.*;

/**
 * A ScriptableObject that represents a scope that provides the closure maps
 * required for implementing the 'process.js' JavaScript API.
 *
 * @author Tobias Downer
 */

public abstract class JSProcessInstanceScope extends ScriptableObject {

  /**
   * An association of call_id to Script objects for executing function
   * closures within this scope..
   */
  private Map<Integer, CallableScript> call_id_function_map;

  /**
   * An association of ProcessChannel to Script object that is a closure that
   * should be called when a broadcast message is received.
   */
  private Map<ProcessChannel, CallableScript> broadcast_closures;


  public JSProcessInstanceScope(Scriptable scope, Scriptable prototype) {
    super(scope, prototype);
  }

  public JSProcessInstanceScope() {
    super();
  }

  /**
   * Initialize the native functions.
   */
  public void init() {

    int ro_attr = PERMANENT | READONLY;

    // Define the function invoke callback function,
    defineProperty("_fun_callback",
                  new IFunCallbackFunction(this, null), ro_attr);

    // Define the function for scheduling a callback on the process,
    defineProperty("_timed_callback",
                  new ITimedCallbackFunction(this, null), ro_attr);
    // Define the broadcast listener add/remove functions,
    defineProperty("_broadcast_setlistener",
                  new ISetBroadcastListenerFunction(this, null), ro_attr);
    defineProperty("_broadcast_removelistener",
                  new IRemoveBroadcastListenerFunction(this, null), ro_attr);

    // Returns the process instance for this operation,
    defineProperty("_get_processinstance",
                  new IGetProcessInstanceFunction(this, null), ro_attr);

  }

  @Override
  public String getClassName() {
    return "JSProcessInstanceScope";
  }

  @Override
  public String toString() {
    return "[JSProcessInstanceScope]";
  }

  @Override
  public Object getDefaultValue(Class<?> typeHint) {
    return toString();
  }

  // -----

  /**
   * Returns the closure on the given ProcessChannel.
   */
  public CallableScript getBroadcastClosure(ProcessChannel ch) {
    if (broadcast_closures != null) {
      CallableScript closure = broadcast_closures.get(ch);
      if (closure != null) {
        return closure;
      }
    }
    return null;
  }

  /**
   * Returns the function closure for the function invoke with the given
   * call_id, or null if no functions associated with the id. If a script is
   * returned then the closure is removed from the map.
   */
  public CallableScript getFunctionClosure(int call_id) {
    if (call_id_function_map == null) {
      return null;
    }
    return call_id_function_map.remove(call_id);
  }



  /**
   * Returns the ProcessInstance for this scope.
   */
  protected abstract ProcessInstance getProcessInstance();

  /**
   * Called to notify that the given call_id was associated to a function
   * in this scope.
   */
  protected abstract void notifyMadeCallIdAssociation(int call_id);

  /**
   * Called to notify that this scope is interested in broadcasts from the
   * given channel.
   */
  protected abstract void notifyMadeChannelAssociation(ProcessChannel ch);
  
  /**
   * Called to notify that this scope is no longer interested in broadcasts
   * from the given channel.
   */
  protected abstract void notifyRemovedChannelAssociation(ProcessChannel ch);

  // -----

  /**
   * The instance scope '_fun_callback' sealed function,
   */
  public class IFunCallbackFunction extends BaseFunction {
    
    public IFunCallbackFunction() {
      super();
      sealObject();
    }

    public IFunCallbackFunction(Scriptable scope, Scriptable prototype) {
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

      if (args.length != 2) {
        throw Context.reportRuntimeError("Unexpected number of arguments");
      }

      // Get the arguments,
      int call_id = (Integer) args[0];
      // The script,
      NativeFunction script = (NativeFunction) args[1];

      if (script == null) {
        throw Context.reportRuntimeError("'script' argument can not be null");
      }

      // Make the association,
      if (call_id_function_map == null) {
        call_id_function_map = new HashMap();
      }
      call_id_function_map.put(call_id, new CallableScript(script));

      // Notify,
      notifyMadeCallIdAssociation(call_id);

      // Return undefined,
      return Undefined.instance;

    }

  }

  /**
   * The instance scope '_timed_callback' sealed function,
   */
  public class ITimedCallbackFunction extends BaseFunction {
    
    public ITimedCallbackFunction() {
      super();
      sealObject();
    }

    public ITimedCallbackFunction(Scriptable scope, Scriptable prototype) {
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

      if (args.length != 2 && args.length != 3) {
        throw Context.reportRuntimeError("Unexpected number of arguments");
      }

      // Get the arguments,
      long time_ms = ((Number) args[0]).longValue();
      // The script,
      NativeFunction script = (NativeFunction) args[1];
      // The ProcessMessage to callback, if any,
      ProcessMessage msg = ByteArrayProcessMessage.nullMessage();
      if (args.length >= 3) {
        if (args[2] != null && args[2] instanceof ProcessMessage) {
          msg = (ProcessMessage) args[2];
        }
      }

      if (script == null) {
        throw Context.reportRuntimeError("'script' argument can not be null");
      }

      // Schedule the callback,
      int call_id = getProcessInstance().scheduleCallback(time_ms, msg);

      // Make the association,
      if (call_id_function_map == null) {
        call_id_function_map = new HashMap();
      }
      call_id_function_map.put(call_id, new CallableScript(script));

      // Notify,
      notifyMadeCallIdAssociation(call_id);

      // Return undefined,
      return Undefined.instance;

    }

  }

  /**
   * The instance scope '_broadcast_addlistener' sealed function,
   */
  public class ISetBroadcastListenerFunction extends BaseFunction {
    
    public ISetBroadcastListenerFunction() {
      super();
      sealObject();
    }

    public ISetBroadcastListenerFunction(
                                     Scriptable scope, Scriptable prototype) {
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

      if (args.length != 2) {
        throw Context.reportRuntimeError("Unexpected number of arguments");
      }

      // Get the arguments,
      ProcessChannel channel = (ProcessChannel) args[0];
      // The script,
      NativeFunction script = (NativeFunction) args[1];

      if (channel == null) {
        throw Context.reportRuntimeError("'channel' argument can not be null");
      }
//      if (script == null) {
//        throw Context.reportRuntimeError("'script' argument can not be null");
//      }
      if (Undefined.instance.equals(script)) {
        script = null;
      }

      // Make the association,
      if (broadcast_closures == null) {
        if (script == null) {
          return Undefined.instance;
        }
        broadcast_closures = new HashMap();
      }
      
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      InstanceProcessClient client = ctx.getInstanceProcessClient();

      CallableScript current_closure = broadcast_closures.get(channel);

      try {

        if (current_closure == null) {
          if (script != null) {
            broadcast_closures.put(channel, new CallableScript(script));
            client.addChannelListener(channel);
            // Notify,
            notifyMadeChannelAssociation(channel);
          }
        }
        else {
          if (script == null) {
            // Notify,
            notifyRemovedChannelAssociation(channel);
            client.removeChannelListener(channel);
            broadcast_closures.remove(channel);
          }
          else {
            throw new IllegalStateException(
                              "Listener already set on " + channel);
          }
        }

        // Return undefined,
        return Undefined.instance;

      }
      catch (ProcessUnavailableException e) {
        throw new JSProcessUnavailableException(e);
      }

    }

  }

  /**
   * The instance scope '_broadcast_removelistener' sealed function,
   */
  public class IRemoveBroadcastListenerFunction extends BaseFunction {
    
    public IRemoveBroadcastListenerFunction() {
      super();
      sealObject();
    }

    public IRemoveBroadcastListenerFunction(
                                     Scriptable scope, Scriptable prototype) {
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

      if (args.length != 2) {
        throw Context.reportRuntimeError("Unexpected number of arguments");
      }

      // Get the arguments,
      ProcessChannel channel = (ProcessChannel) args[0];
      // The script,
      NativeFunction script = (NativeFunction) args[1];

      // If script is 'undefined' then script = null
      if (script != null && script.equals(Undefined.instance)) {
        script = null;
      }

      if (channel == null) {
        throw Context.reportRuntimeError("'channel' argument can not be null");
      }

      // Make sure broadcast_closures is initialized,
      if (broadcast_closures != null) {

        PlatformContext ctx = PlatformContextFactory.getPlatformContext();
        InstanceProcessClient client = ctx.getInstanceProcessClient();

        try {
          // If 'script' isn't null,
          if (script != null) {
            CallableScript closure = broadcast_closures.get(channel);
            if (closure != null) {
              if (closure.script.equals(script)) {
                notifyRemovedChannelAssociation(channel);
                client.removeChannelListener(channel);
                broadcast_closures.remove(channel);
              }
            }
          }
          // If 'script' is null, remove all closures on the channel,
          else {
            CallableScript removed = broadcast_closures.remove(channel);
            if (removed != null) {
              client.removeChannelListener(channel);
              notifyRemovedChannelAssociation(channel);
            }
          }
        }
        catch (ProcessUnavailableException e) {
          throw new JSProcessUnavailableException(e);
        }
      }

      // Return undefined,
      return Undefined.instance;

    }

  }

  /**
   * The instance scope '_get_processinstance' sealed function,
   */
  public class IGetProcessInstanceFunction extends BaseFunction {
    
    public IGetProcessInstanceFunction() {
      super();
      sealObject();
    }

    public IGetProcessInstanceFunction(Scriptable scope, Scriptable prototype) {
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

      return getProcessInstance();

    }

  }

  /**
   * An object that represents an invokable closure.
   */
  public static class CallableScript {

    private final long creation_time;
    private final NativeFunction script;

    public CallableScript(NativeFunction script) {
      this.creation_time = System.currentTimeMillis();
      this.script = script;
    }

    /**
     * Invoke the function closure.
     */
    public Object call(Context js_ctx, Scriptable scope,
                       Scriptable this_obj, Object[] args) {
      return script.call(js_ctx, scope, this_obj, args);
    }

  }

}
