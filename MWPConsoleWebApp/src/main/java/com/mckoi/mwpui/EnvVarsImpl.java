/**
 * com.mckoi.mwpui.EnvVarsImpl  May 4, 2012
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
import java.util.*;

/**
 * A wrapper around the state object.
 *
 * @author Tobias Downer
 */

public class EnvVarsImpl implements EnvironmentVariables {

  private final String frame;
  // The stack frame name,
  private final String stack_frame;
  // The global environment
  private final Map<String, String> envs;
  // The temporary environment
  private final Map<String, Object> temp_envs;
  // A temporary map of the top ServerCommand objects for each frame,
  private final Map<String, List<ServerCommand>> temp_top_commands;
  // The current program stack,
  private final List<String> program_stack;

  // The unique process id string,
  private final String process_id_str;

  // The current id,
  private String id;
  // Key pre string,
  private String pre_id;

  private boolean was_changed;



  public EnvVarsImpl(Map<String, String> envs,
                     Map<String, Object> temp_envs,
                     Map<String, List<ServerCommand>> temp_top_commands,
                     String process_id_str,
                     String frame) {
    this.frame = frame;
    this.envs = envs;
    this.temp_envs = temp_envs;
    this.temp_top_commands = temp_top_commands;
    this.process_id_str = process_id_str;
    this.stack_frame = "i.stack_" + frame;
    this.program_stack = new ArrayList();
    String stack_str = envs.get(stack_frame);
    String[] items = stack_str.split(",");
    program_stack.addAll(Arrays.asList(items));
    this.pre_id = frame + ".";
    id = items[items.length - 1];
    was_changed = false;
  }

  /**
   * Returns the list of all stack frames managed by the given input vars.
   */
  public List<EnvVarsImpl> allPrograms() {
    List<EnvVarsImpl> out = new ArrayList(24);
    Set<String> keys = envs.keySet();
    for (String key : keys) {
      if (key.startsWith("i.stack_")) {
        String frame_val = key.substring(8);
        out.add(
          new EnvVarsImpl(envs, temp_envs, temp_top_commands,
                          process_id_str, frame_val));
      }
    }
    return out;
  }

  /**
   * Returns true if the stack level for this frame is on the given level name.
   */
  boolean isStackOn(String stack_level_name) {
    String levels = envs.get(stack_frame);
    if (levels.equals(stack_level_name) ||
        levels.endsWith("," + stack_level_name)) {
      return true;
    }
    return false;
  }

  /**
   * Clears the current frame stack.
   */
  void clearStack() {
    while (program_stack.size() > 0) {
      removeTopStackLevel();
    }
    envs.remove(stack_frame);
  }

  /**
   * Clears all the process channels on this frame and returns the list.
   */
  Collection<String> clearChannelsOnFrame() {
    // The output,
    List<String> out = new ArrayList();
    // All the environment keys,
    Set<String> keys = temp_envs.keySet();
    for (String key : keys) {
      if (key.startsWith("c.")) {
        String frame_id = (String) temp_envs.get(key);
        // If the channel is connected to this frame then add it to 'out'
        if (frame_id.equals(frame)) {
          out.add(key.substring(2));
        }
      }
    }
    // Remove the keys,
    for (String k : out) {
      temp_envs.remove("c." + k);
    }
    // Return the channels to close,
    return out;
  }

  /**
   * Returns the backed map for debugging purposes.
   */
  public Map<String, String> getBackedMapDEBUG() {
    return Collections.unmodifiableMap(envs);
  }

  /**
   * Connects the frame represented by this environment vars to the given
   * process channel.
   */
  public void connectFrameTo(ProcessChannel channel) {
    temp_envs.put("c." + channel.toString(), frame);
  }

  /**
   * Returns the frame connected to the given process channel, or null if no
   * channel connected to it.
   */
  public String getFrameConnectedTo(ProcessChannel channel) {
    return (String) temp_envs.get("c." + channel.toString());
  }

  /**
   * Returns the current ServerCommand that was pushed to this stack frame, or
   * null if it's not available.
   */
  public ServerCommand getTopServerCommand() {
    List<ServerCommand> cmds = temp_top_commands.get(frame);
    if (cmds == null || cmds.isEmpty()) {
      return null;
    }
    else {
      return cmds.get(cmds.size() - 1);
    }
  }

  /**
   * Sets the current ServerCommand that represents the program at the top of
   * the stack frame.
   */
  void setTopServerCommand(ServerCommand comm) {
    List<ServerCommand> cmds = temp_top_commands.get(frame);
    if (cmds == null) {
      cmds = new ArrayList();
      temp_top_commands.put(frame, cmds);
    }
    cmds.add(comm);
  }

  /**
   * Pushes a new program onto the current stack frame.
   */
  public void pushProgram(ServerCommand program_instance) {
    String stack_str = envs.get(stack_frame);
    String rid = Integer.toString(stack_str.length());
    program_stack.add(rid);
    stack_str = stack_str + "," + rid;
    id = rid;
    pre_id = frame + ".";
    envs.put(stack_frame, stack_str);

    // Put on the 'temp_top_commands' map,
    setTopServerCommand(program_instance);
//    temp_top_commands.put(frame, program_instance);

  }

  /**
   * Removes the top entry of the stack and any variables stored for it.
   */
  private void removeTopStackLevel() {
    // Remove the last entry,
    String stack_levels = envs.get(stack_frame);
    int delim = stack_levels.lastIndexOf(",");
    if (delim == -1) {
      delim = stack_levels.length();
    }

    String remove_id = program_stack.remove(program_stack.size() - 1);
    envs.put(stack_frame, stack_levels.substring(0, delim));
    if (program_stack.size() > 0) {
      id = program_stack.get(program_stack.size() - 1);
    }
    else {
      id = null;
    }

    // Remove the entries,
    Iterator<String> i = envs.keySet().iterator();
    String id_key = pre_id + remove_id + ".";
    while (i.hasNext()) {
      String key = i.next();
      if (key.startsWith(id_key)) {
        i.remove();
      }
    }

    // Remove the 'temp_top_commands' entry,
    List<ServerCommand> cmds = temp_top_commands.get(frame);
    if (cmds != null && !cmds.isEmpty()) {
      cmds.remove(cmds.size() - 1);
    }

  }
  
  /**
   * Pops the last program from the current stack frame. Will not pop the
   * global frame ('g').
   */
  public void popProgram() {
    String stack_levels = envs.get(stack_frame);
    int delim = stack_levels.lastIndexOf(",");
    if (delim == -1) {
      throw new RuntimeException("Program stack is at first entry");
    }
    removeTopStackLevel();
  }

  /**
   * Forks this frame into a new one using the top environment vars as the
   * 'global' vars for the new frame. Also pops the top entry.
   */
  public EnvVarsImpl forkTop(String frame_type) {

    // Create a new frame id,
    int frame_val = -1;
    for (int i = 1; i < 64; ++i) {
      if (envs.get(i + ".g.frame") == null) {
        frame_val = i;
        break;
      }
    }
    if (frame_val == -1) {
      throw new RuntimeException("Frame maximum reached");
    }

    // Set up the new frame,
    String frame_str = Integer.toString(frame_val);
    envs.put("i.stack_" + frame_val, "g,1");
    envs.put(frame_str + ".g.frame", frame_type);
    EnvVarsImpl new_envs =
        new EnvVarsImpl(envs, temp_envs, temp_top_commands,
                        process_id_str, frame_str);

    // Move the entries,
    String top_id = program_stack.get(program_stack.size() - 1);
    Iterator<String> i = envs.keySet().iterator();
    String id_key = pre_id + top_id + ".";
    Map<String, String> move_map = new HashMap();
    while (i.hasNext()) {
      String key = i.next();
      if (key.startsWith(id_key)) {
        String key_part = key.substring(id_key.length());
        move_map.put(frame_str + ".1." + key_part, envs.get(key));
      }
    }
    // Move the environment vars,
    for (String key : move_map.keySet()) {
      envs.put(key, move_map.get(key));
    }

    // Pop,
    popProgram();
    
    // Return
    return new_envs;
  }

  /**
   * Returns the name of the current frame.
   */
  public String getFrameName() {
    return frame;
  }

  /**
   * Returns a unique reference string for the current top stack program.
   */
  String createCurrentCmdReference() {
    return frame + "-" + id;
  }

  /**
   * Returns a unique reference string for the program that would be the next
   * reference.
   */
  String createNextCmdReference() {
    String stack_str = envs.get(stack_frame);
    int next_id = stack_str.length();
    return frame + "-" + next_id;
  }

  // -----

  @Override
  public String get(String key) {
    if (key.equals("user")) {
      return envs.get("i.user");
    }
    // Search through the stack,
    int sz = program_stack.size();
    for (int i = sz - 1; i >= 0; --i) {

      String stack_item = program_stack.get(i);
      stack_item = pre_id + stack_item;
      String head = stack_item;

      String v = envs.get(head + "." + key);
      if (v != null) {
        return v;
      }
    }
    return null;
  }

  @Override
  public String put(String key, String value) {
    // Don't allow change to 'user'
    if (key.equals("user")) {
      return envs.get("i.user");
    }
    String qualified_key = pre_id + id + "." + key;
    String old_val;
    if (value == null) {
      old_val = envs.remove(qualified_key);
    }
    else {
      old_val = envs.put(qualified_key, value);
    }
    was_changed = true;
    // Make sure to return any value we are overwriting.
    if (old_val == null) {
      int sz = program_stack.size();
      for (int i = sz - 2; i >= 0; --i) {

        String stack_item = program_stack.get(i);
        stack_item = pre_id + stack_item;
        String head = stack_item;

        String v = envs.get(head + "." + key);
        if (v != null) {
          return v;
        }
      }
    }
    return old_val;
  }

  @Override
  public Set<String> keySet() {
    // Return only the keys that start with "g."
    Set<String> keys = new HashSet();
    keys.add("user");
    for (String k : envs.keySet()) {
      if (k.startsWith(pre_id)) {
        k = k.substring(pre_id.length());
        int delim = k.indexOf(".");
        String kv = k.substring(0, delim);
        if (program_stack.contains(kv)) {
          keys.add(k.substring(delim + 1));
        }
      }
    }
    return keys;
  }

  @Override
  public boolean wasModified() {
    return was_changed;
  }

  @Override
  public void setTemporaryObject(Object ob) {
    temp_envs.put("t." + frame, ob);
  }

  @Override
  public Object getTemporaryObject() {
    return temp_envs.get("t." + frame);
  }

}
