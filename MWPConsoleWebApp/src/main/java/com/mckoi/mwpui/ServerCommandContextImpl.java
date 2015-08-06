/**
 * com.mckoi.mwpui.ServerCommandContextImpl  Nov 26, 2012
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

import com.mckoi.process.ProcessChannel;
import com.mckoi.process.ProcessInstance;
import com.mckoi.process.ProcessUnavailableException;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.util.*;

/**
 * An implementation of ServerCommandContext.
 *
 * @author Tobias Downer
 */

public class ServerCommandContextImpl implements ServerCommandContext {

  private ProcessInstance instance;

  private Map<Integer, ServerCommand> call_id_map;
  private Map<ProcessChannel, Collection<ServerCommand>> channel_callback_map;

  private String[] last_kill_signal = null;

  private boolean is_dormant = true;

  ServerCommandContextImpl() {
  }

  void init(ProcessInstance instance) {
    this.instance = instance;
  }

  @Override
  public PlatformContext getPlatformContext() {
    return PlatformContextFactory.getPlatformContext();
  }

  @Override
  public String getAccountName() {
    return getPlatformContext().getAccountName();
  }

  @Override
  public ProcessInstance getProcessInstance() {
    return instance;
  }

  @Override
  public boolean isKilled() {
    if (last_kill_signal != null) {
      return true;
    }
    // Consume all signals,
    consumeSignals();
    // If it's a kill signal,
    return (last_kill_signal != null);
  }

  /**
   * Returns the last kill signal, or returns null if no kill signals.
   */
  public String[] getLastKillSignal() {
    if (last_kill_signal != null) {
      return last_kill_signal;
    }
    consumeSignals();
    return last_kill_signal;
  }

  @Override
  public void resetKillSignal() {
    last_kill_signal = null;
  }

  /**
   * Consumes any signals waiting on the queue.
   */
  private void consumeSignals() {
    // Consume all signals,
    while (true) {
      String[] sig = instance.consumeSignal();
      if (sig == null) {
        break;
      }
      if (sig[0].equals("kill")) {
        last_kill_signal = sig;
      }
    }
  }

//  /**
//   * Returns true if the server context is currently dormant, false is active.
//   * 
//   * @return 
//   */
//  public boolean isDormant() {
//    return is_dormant;
//  }
//
//  /**
//   * Sets or resets the dormancy flag. When this is false, it's not possible
//   * for this context to switch class loaders on code revision or inactivity.
//   * 
//   * @param flag
//   */
//  public void setDormancyFlag(boolean flag) {
//    is_dormant = flag;
//  }

  /**
   * Removes all dispatcher objects for ServerCommand objects with the given
   * reference,
   */
  void clearCommandsWithReference(String top_ref) {
    if (call_id_map != null) {
      Iterator<Integer> i = call_id_map.keySet().iterator();
      while (i.hasNext()) {
        if (call_id_map.get(i.next()).getReference().equals(top_ref)) {
          i.remove();
        }
      }
    }
    // If there's channel callbacks,
    if (channel_callback_map != null) {
      Iterator<ProcessChannel> i = channel_callback_map.keySet().iterator();
      while (i.hasNext()) {
        final ProcessChannel ch = i.next();
        Collection<ServerCommand> cmds = channel_callback_map.get(ch);
        Iterator<ServerCommand> i2 = cmds.iterator();
        while (i2.hasNext()) {
          ServerCommand cmd = i2.next();
          if (cmd.getReference().equals(top_ref)) {
            i2.remove();
          }
        }
        // If it's empty,
        if (cmds.isEmpty()) {
          // Make sure to remove the channel listener,
          i.remove();
          try {
            instance.getInstanceProcessClient().removeChannelListener(ch);
          }
          catch (ProcessUnavailableException e) {
            // PENDING: Do something with this?
          }
        }
      }
    }
  }

  /**
   * Associates the call_id with the ServerCommand, so when the context
   * receives a message with the given call_id it will be dispatched to the
   * ServerCommand.
   * 
   * @param call_id
   * @param server_command
   */
  public void addCallIdCallback(int call_id, ServerCommand server_command) {

    if (call_id_map == null) {
      call_id_map = new HashMap();
    }
    call_id_map.put(call_id, server_command);

  }

  /**
   * Associates the process channel with the ServerCommand, so when the context
   * receives broadcast messages on the given channel it will be dispatched to
   * the ServerCommand. It is not necessary for implementations of this method
   * to actually establish a listener on the channel.
   */
  void addChannelCallback(ProcessChannel ch, ServerCommand server_command) {

    if (channel_callback_map == null) {
      channel_callback_map = new HashMap();
    }
    Collection<ServerCommand> cmds = channel_callback_map.get(ch);
    if (cmds == null) {
      cmds = new ArrayList(4);
      channel_callback_map.put(ch, cmds);
    }
    cmds.add(server_command);

  }

  /**
   * Removes an association of the process channel with the ServerCommand. It
   * is not necessary for implementations of this method to actually remove any
   * established listeners on the channel.
   */
  void removeChannelCallback(ProcessChannel ch, ServerCommand server_command) {

    if (channel_callback_map != null) {
      Collection<ServerCommand> cmds = channel_callback_map.get(ch);
      Iterator<ServerCommand> i = cmds.iterator();
      while (i.hasNext()) {
        ServerCommand cmd = i.next();
        if (cmd == server_command) {
          i.remove();
        }
      }
      if (cmds.isEmpty()) {
        channel_callback_map.remove(ch);
      }
    }

  }

  /**
   * Returns the ServerCommand associated to the given call_id, or null if
   * no association is given. Removes the map if it's found.
   */
  ServerCommand removeServerCommandForCallId(int call_id) {
    if (call_id_map != null) {
      return call_id_map.remove(call_id);
    }
    return null;
  }

  /**
   * Returns a collection of ServerCommand that are associated with the given
   * process channel.
   */
  Collection<ServerCommand> getServerCommandsForChannel(ProcessChannel ch) {
    if (channel_callback_map != null) {
      Collection<ServerCommand> cmds = channel_callback_map.get(ch);
      if (cmds != null) {
        return cmds;
      }
    }
    return Collections.EMPTY_LIST;
  }

  // -----

}
