/**
 * com.mckoi.mwpui.DefaultSCommand  Oct 19, 2012
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

import com.mckoi.process.InstanceProcessClient;
import com.mckoi.process.ProcessId;
import com.mckoi.process.ProcessInputMessage;
import com.mckoi.process.ProcessInstance;
import com.mckoi.process.ProcessMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of ServerCommand that assumes CONSOLE_MODE and
 * provides no interact features.
 *
 * @author Tobias Downer
 */

public abstract class DefaultSCommand implements ServerCommand {

  private final String reference;
  private Map<Integer, FunctionClosure> call_id_map = null;
  
  public DefaultSCommand(String reference) {
    this.reference = reference;
  }

  /**
   * Adds a call id map entry.
   */
  private void addCallIdMap(int call_id, FunctionClosure function_closure) {
    if (call_id_map == null) {
      call_id_map = new HashMap();
    }
    call_id_map.put(call_id, function_closure);
  }

  /**
   * Invokes a function on the given process id and calls the given
   * FunctionClosure when a reply is received. If a reply is never received
   * then the closure will never be called.
   */
  public void invokeFunction(ProcessId process_id, ProcessMessage msg,
                ServerCommandContext sctx, FunctionClosure function_closure) {

    InstanceProcessClient client =
            sctx.getProcessInstance().getInstanceProcessClient();

    boolean reply_expected = (function_closure != null);
    int call_id = client.invokeFunction(process_id, msg, reply_expected);
    if (reply_expected) {
      addCallIdMap(call_id, function_closure);
      ServerCommandContextImpl sctx_impl = (ServerCommandContextImpl) sctx;
      sctx_impl.addCallIdCallback(call_id, this);
    }

  }

  /**
   * Schedules a callback message after the given number of milliseconds has
   * passed.
   */
  public void scheduleCallback(long time_wait_ms, ProcessMessage msg,
                ServerCommandContext sctx, FunctionClosure function_closure) {

    ProcessInstance instance = sctx.getProcessInstance();
    int call_id = instance.scheduleCallback(time_wait_ms, msg);
    addCallIdMap(call_id, function_closure);
    ServerCommandContextImpl sctx_impl = (ServerCommandContextImpl) sctx;
    sctx_impl.addCallIdCallback(call_id, this);

  }

  @Override
  public String getReference() {
    return reference;
  }

  @Override
  public String getIconString() {
    return null;
  }

  @Override
  public String init(ServerCommandContext ctx, EnvironmentVariables var) {
    return CONSOLE_MODE;
  }

  @Override
  public String interact(ServerCommandContext ctx, EnvironmentVariables vars,
                         String feature) {
    return null;
  }

  @Override
  public String handle(ServerCommandContext ctx, EnvironmentVariables vars,
                       CommandWriter out, ProcessInputMessage input_message) {

    // Route return messages,
    ProcessInputMessage.Type type = input_message.getType();
    if (call_id_map != null &&
            ( type == ProcessInputMessage.Type.RETURN ||
              type == ProcessInputMessage.Type.RETURN_EXCEPTION ||
              type == ProcessInputMessage.Type.TIMED_CALLBACK ) ) {
      int in_call_id = input_message.getCallId();
      FunctionClosure closure = call_id_map.remove(in_call_id);
      if (closure != null) {
        return closure.run(input_message);
      }
    }

    return null;
  }

  @Override
  public boolean isActive() {
    return false;
  }

}
