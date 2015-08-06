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

import com.mckoi.mwpui.ServerCommand;
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.mwpui.ServerCommandContextImpl;
import com.mckoi.process.ByteArrayProcessMessage;
import com.mckoi.process.ProcessInstance;
import com.mckoi.process.ProcessMessage;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import org.mozilla.javascript.*;

/**
 * This object stores all the shared state used to interface between the
 * native node and JavaScript systems.
 *
 * @author Tobias Downer
 */
public class JSProcessSharedState {

  /**
   * The ServerCommand this state is attached with.
   */
  private final ServerCommand server_command;

  /**
   * Shared command context.
   */
  private ServerCommandContext ctx;

  /**
   * Shared node source loader (for source code load and compile caching).
   */
  private NodeSourceLoader node_source_loader;

  /**
   * The list of IO events currently queued.
   */
  private final LinkedList<IOEvent> io_queue = new LinkedList();

  /**
   * The nodejs process._tickCallback function that is called immediately after
   * an external function continuation.
   */
  private BaseFunction _tickCallback;

  /**
   * The TickInfo object.
   */
  private ScriptableObject tick_info;

  /**
   * Maps timed event callbacks.
   */
  private Map<Integer, ScriptableObject> timed_map = new HashMap();

  
  public JSProcessSharedState(ServerCommand server_command) {
    this.server_command = server_command;
  }
  


  /**
   * Sets the server command context.
   * 
   * @param ctx 
   */
  public void setServerCommandContext(ServerCommandContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Sets the node source code loader and compiler.
   * 
   * @param node_source_loader 
   */
  public void setNodeSourceLoader(NodeSourceLoader node_source_loader) {
    this.node_source_loader = node_source_loader;
  }

  /**
   * Sets the _tickCallback function.
   * @param callback
   */
  public void setTickCallback(BaseFunction callback) {
    this._tickCallback = callback;
  }
  
  /**
   * Sets the tick_info object. References the index and length of the current
   * tick queue.
   * 
   * @param tick_info 
   */
  public void setTickInfo(ScriptableObject tick_info) {
    this.tick_info = tick_info;
  }
  
  /**
   * Returns the ServerCommandContext object.
   * @return 
   */
  public ServerCommandContext getServerCommandContext() {
    return ctx;
  }

  /**
   * Returns the NodeSourceLoader object.
   * @return 
   */
  public NodeSourceLoader getNodeSourceLoader() {
    return node_source_loader;
  }

  /**
   * Returns the process _tickCallback function.
   * @return 
   */
  public BaseFunction getTickCallback() {
    return _tickCallback;
  }

  /**
   * Returns the process tick_info variable.
   * @return 
   */
  public ScriptableObject getTickInfo() {
    return tick_info;
  }

  /**
   * Posts an IOEvent to the IO queue. This will cause the 'oncomplete' function
   * to be called with the 'complete_args' arguments immediately after all
   * ticks are completed.
   * 
   * @param oncomplete
   * @param complete_args 
   */
  void postToIOQueue(Scriptable thisObj,
                      BaseFunction oncomplete, Object[] complete_args) {
    IOEvent event = new IOEvent(thisObj, oncomplete, complete_args);
    io_queue.add(event);
  }

  /**
   * Pop the first IO event that's pending.
   * @return returns null if no pending events, or the oldest IOEvent that was
   *   posted.
   */
  public IOEvent popIOQueueEvent() {
    return io_queue.isEmpty() ? null : io_queue.removeFirst();
  }

  /**
   * Post a timer object to the queue.
   * 
   * @param timer_ob
   * @param msec 
   */
  public void postTimedCallback(ScriptableObject timer_ob, long msec) {
    
    ServerCommandContextImpl raw_ctx = (ServerCommandContextImpl) ctx;
    
    ProcessInstance process_instance = ctx.getProcessInstance();
    // Schedule a null message callback,
    ProcessMessage msg = ByteArrayProcessMessage.nullMessage();
    int call_id = process_instance.scheduleCallback(msec, msg);
    // Map the timed object,
    timed_map.put(call_id, timer_ob);
    raw_ctx.addCallIdCallback(call_id, server_command);
  }

  /**
   * Pulls a timer object from the map given the call_id.
   * 
   * @param call_id
   * @return 
   */
  public ScriptableObject pullTimerObject(int call_id) {
    ScriptableObject timer_ob = timed_map.remove(call_id);
    return timer_ob;
  }

  /**
   * Returns true if there are referenced pending timed objects scheduled.
   * 
   * @return 
   */
  public boolean hasRefedTimedCallbacksPending() {
    if (timed_map.isEmpty()) {
      return false;
    }
    // Return false if all existing timers are unrefed,
    for (Entry<Integer, ScriptableObject> timer : timed_map.entrySet()) {
      ScriptableObject script_ob = timer.getValue();
      Object ref_val = script_ob.get("_ref");
      if (ref_val == null || Undefined.instance.equals(ref_val)) {
        return true;
      }
      if (ref_val.toString().equals("REF")) {
        return true;
      }
      // Otherwise must be UNREF
    }
    // We checked all the objects and they are all unref, so there's no more
    // pending timers.
    return false;
  }

}
