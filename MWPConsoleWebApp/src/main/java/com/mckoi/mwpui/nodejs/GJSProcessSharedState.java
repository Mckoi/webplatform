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

import com.mckoi.mwpui.*;
import com.mckoi.process.ByteArrayProcessMessage;
import com.mckoi.process.ProcessInstance;
import com.mckoi.process.ProcessMessage;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author Tobias Downer
 */
public class GJSProcessSharedState {

  /**
   * The ServerCommand this state is attached with.
   */
  private final ServerCommand server_command;

  /**
   * The Environment object.
   */
  private EnvironmentVariables env;
  
  /**
   * The console output writer.
   */
  private CommandWriter out;
  
  /**
   * Shared command context.
   */
  private ServerCommandContext ctx;

  /**
   * Shared node source loader (for source code load and compile caching).
   */
  private GJSNodeSourceLoader node_source_loader;

  /**
   * The list of IO events currently queued.
   */
  private final LinkedList<IOEvent> io_queue = new LinkedList();

  /**
   * The nodejs process._tickCallback function that is called immediately after
   * an external function continuation.
   */
  private GJSObject _tickCallback;

  /**
   * The TickInfo object.
   */
  private GJSObject tick_info;

  /**
   * Maps timed event callbacks.
   */
  private Map<Integer, GJSObject> timed_map = new HashMap();

  
  public GJSProcessSharedState(ServerCommand server_command) {
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
   * Sets the EnvironmentVariables object.
   * 
   * @param env 
   */
  public void setEnv(EnvironmentVariables env) {
    this.env = env;
  }
  
  /**
   * Sets the CommandWriter object.
   * 
   * @param out 
   */
  public void setCommandWriter(CommandWriter out) {
    this.out = out;
  }

  /**
   * Sets the node source code loader and compiler.
   * 
   * @param node_source_loader 
   */
  public void setNodeSourceLoader(GJSNodeSourceLoader node_source_loader) {
    this.node_source_loader = node_source_loader;
  }

  /**
   * Sets the _tickCallback function.
   * @param callback
   */
  public void setTickCallback(GJSObject callback) {
    this._tickCallback = callback;
  }
  
  /**
   * Sets the tick_info object. References the index and length of the current
   * tick queue.
   * 
   * @param tick_info 
   */
  public void setTickInfo(GJSObject tick_info) {
    this.tick_info = tick_info;
  }
  
  /**
   * Returns the shared state ServerCommandContext object.
   * @return 
   */
  public ServerCommandContext getServerCommandContext() {
    return ctx;
  }

  /**
   * Returns the shared state EnvironmentVariables object.
   * @return 
   */
  public EnvironmentVariables getEnv() {
    return env;
  }

  /**
   * Returns the shared state CommandWriter object.
   * @return 
   */
  public CommandWriter getCommandWriter() {
    return out;
  }

  /**
   * Returns the GJSNodeSourceLoader object.
   * @return 
   */
  public GJSNodeSourceLoader getNodeSourceLoader() {
    return node_source_loader;
  }

  /**
   * Returns the process _tickCallback function.
   * @return 
   */
  public GJSObject getTickCallback() {
    return _tickCallback;
  }

  /**
   * Returns the process tick_info variable.
   * @return 
   */
  public GJSObject getTickInfo() {
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
  void postToIOQueue(Object thisObj,
                      GJSObject oncomplete, Object[] complete_args) {
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
  public void postTimedCallback(GJSObject timer_ob, long msec) {
    
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
  public GJSObject pullTimerObject(int call_id) {
    GJSObject timer_ob = timed_map.remove(call_id);
    return timer_ob;
  }

  /**
   * Returns true if there are referenced pending timed objects scheduled.
   * 
   * @return 
   */
  public boolean hasRefedTimedCallbacksPending() {
//    System.out.println("hasReferTimerCallbacksPending() called");
    if (timed_map.isEmpty()) {
//      System.out.println("  NOPE; timed_map.isEmpty()");
      return false;
    }
    // Return false if all existing timers are unrefed,
    for (Entry<Integer, GJSObject> timer : timed_map.entrySet()) {
      GJSObject script_ob = timer.getValue();
//      System.out.println(" Checking: " + script_ob);
      if (script_ob.hasMember("_ref")) {
//        System.out.println(" yep, hasMember('_ref')!");
        Object ref_val = script_ob.getMember("_ref");
//        System.out.println("  here it is: " + ref_val);
        if (ref_val == null || GJSStatics.UNDEFINED.equals(ref_val)) {
          return true;
        }
        if (ref_val.toString().equals("REF")) {
          return true;
        }
      }
      // If it doesn't have a _ref member then assume it's referenced,
      else {
        return true;
      }
      // Otherwise must be UNREF
    }
    // We checked all the objects and they are all unref, so there's no more
    // pending timers.
//    System.out.println("  NOPE; none refered");
    return false;
  }

}
