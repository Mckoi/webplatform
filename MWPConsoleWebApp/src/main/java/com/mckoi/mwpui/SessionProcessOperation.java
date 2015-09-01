/**
 * com.mckoi.mwpui.SessionProcessOperation  May 2, 2012
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

import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.rhino.KillSignalException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;

/**
 * The user session process that manages state. This process provides a
 * broadcast channel that the client subscribes to and retrieves output from
 * process functions.
 *
 * @author Tobias Downer
 */

public class SessionProcessOperation implements ProcessOperation {

  /**
   * The environment variables object.
   */
  private final Map<String, String> environment;

  /**
   * Temporary objects defined for frames (frame->Object).
   */
  private final Map<String, Object> temp_environment;

  /**
   * Map that associates the frame name with the program stack of ServerCommand
   * objects.
   */
  private final Map<String, List<ServerCommand>> top_programs_map;

  /**
   * The ServerCommandContextImpl.
   */
  private final ServerCommandContextImpl server_command_context =
                                                new ServerCommandContextImpl();

  /**
   * The process will no longer be active after this period of time has passed
   * since the last command was run.
   */
  // PENDING: Make this configurable (maybe via a config setting in the account
  //   directory.
  private static final long NO_ACTIVITY_TIMEOUT_PERIOD = (4 * 60 * 60 * 1000);

  /**
   * The OK message,
   */
  private static final ByteArrayProcessMessage OK_MSG =
                                      ByteArrayProcessMessage.encodeArgs("OK");

  /**
   * Constructor.
   */
  public SessionProcessOperation() {
    environment = new HashMap();
    temp_environment = new HashMap();
    top_programs_map = new HashMap();
  }

  /**
   * Returns an InstanceCWriter for the given frame.
   */
  private InstanceCWriter getCWriter(String frame) {
    InstanceCWriter writer =
                        (InstanceCWriter) temp_environment.get("w." + frame);
    if (writer == null) {
      ProcessInstance instance = server_command_context.getProcessInstance();
      writer = new InstanceCWriter(frame, instance);
      temp_environment.put("w." + frame, writer);
    }
    return writer;
  }

  /**
   * Given a JSON formatted array, returns each item as a string.
   */
  private static List<String> parseJSONArray(String json_string) {
    try {
      JSONTokener t = new JSONTokener(json_string);
      JSONArray arr = (JSONArray) t.nextValue();
      int sz = arr.length();
      List<String> out = new ArrayList(sz);
      for (int i = 0; i < sz; ++i) {
        out.add(arr.getString(i));
      }
      return out;
    }
    catch (JSONException e) {
      throw new SessionProcessException(e);
    }
  }

  /**
   * Refreshes the 'i.lts' environment variable (the last time a command was
   * executed on this process.
   */
  private void refreshLTS() {
    environment.put("i.lts", Long.toHexString(System.currentTimeMillis()));
  }

  /**
   * Invalidates this session. After this call, all further commands to this
   * process will fail.
   */
  private void invalidate() {
    environment.put("i.lts", Long.toHexString(0));
  }

  /**
   * Formats a prompt string.
   */
  private static String formatPrompt(EnvVarsImpl envs) {
    String prompt_code = envs.get("prompt");
    if (prompt_code != null) {
      String pwd = envs.get("pwd");
      if (pwd != null) {
        int p = prompt_code.indexOf("$pwd");
        if (p >= 0) {
          prompt_code = prompt_code.substring(0, p) +
                        pwd +
                        prompt_code.substring(p + 4);
        }
      }
      return prompt_code;
    }
    return "";
  }

  private static void readFrom(
                 Map<String, String> internal_state, StateMap external_state) {
    internal_state.putAll(external_state);
  }

  private static void writeTo(
                 Map<String, String> internal_state, StateMap external_state) {
    external_state.putAll(internal_state);
  }

  private void initFrame(String frame) {
    environment.put(frame + ".g.frame", "console");
  }

  private void checkFrameString(String frame) {
    boolean invalid_f = false;
    int sz = frame.length();
    for (int i = 0; i < sz; ++i) {
      char ch = frame.charAt(i);
      if (ch < '0' || ch > '9') {
        invalid_f = true;
        break;
      }
    }
    if (invalid_f) {
      throw new SessionProcessException("Invalid frame string: '" + frame + "'");
    }
  }

  
  /**
   * Given a 'prg_name', returns a server command if a command can be
   * associated with it.
   */
  static ServerCommand resolveServerCommand(
          String prg_name, String prg_reference,
          EnvironmentVariables envs, ServerCommandContext sctx) {

    CommandDatabase db = new CommandDatabase();
    ServerCommand command = db.getCommand(sctx, prg_name, prg_reference);
    if (command == null) {
      // Node,
      if (prg_name.equals("node")) {
        return NodeJSWrapSCommand.loadNodeJSCommand(prg_reference, envs, sctx);
      }
      // Otherwise try and load the legacy JavaScript command,
      command = NodeJSWrapSCommand.loadJSCommand(
                                      prg_reference, prg_name, envs, sctx);
      if (command == null) {
        command = JSWrapSCommand.loadJSCommand(
                                      prg_reference, prg_name, envs, sctx);
      }
    }
    return command;

  }

  /**
   * Returns the ServerCommand representing the top program currently on the
   * given frame. If there's no command available then creates a new one.
   */
  static ServerCommand getTopServerCommand(String prg_name, EnvVarsImpl envs,
                            ServerCommandContext sctx) {

    ServerCommand command = envs.getTopServerCommand();
    if (command == null) {
      String reference = envs.createCurrentCmdReference();
      command = resolveServerCommand(prg_name, reference, envs, sctx);
      envs.setTopServerCommand(command);
    }
    return command;

  }

  /**
   * Closes the top program. If a none-null string is returned then we must
   * repeat.
   */
  static String closeTopProgram(
          EnvVarsImpl envs, ServerCommandContextImpl sctx,
          InstanceCWriter p,Map<String, String> exported_map) {
    
    String status_code = null;

    // Get the current command reference,
    String top_ref = envs.createCurrentCmdReference();
    // Clear any references to it from the server command context,
    sctx.clearCommandsWithReference(top_ref);

    // Pop the program,
    envs.popProgram();
    
    // If we've cleared the stack then end the process,
    if (envs.isStackOn("g")) {
      // Close any channels,
      Collection<String> channels = envs.clearChannelsOnFrame();
      PlatformContext ctx = sctx.getPlatformContext();
      InstanceProcessClient instance_client = ctx.getInstanceProcessClient();
      for (String ch : channels) {
        try {
          instance_client.removeChannelListener(new ProcessChannel(ch));
        }
        catch (ProcessUnavailableException e) {
          // PENDING: do something with this?
        }
      }
      // Clear the stack
      envs.clearStack();
      // The close control code,
      p.sendControl('_');
      p.flush();
      return null;
    }

    // Copy the exported values to the parent,
    if (exported_map != null) {
      for (String key : exported_map.keySet()) {
        envs.put(key, exported_map.get(key));
      }
    }

    // Get the parent program,
    String parent_prg = envs.get("prg");
    ServerCommand parent_command = getTopServerCommand(parent_prg, envs, sctx);
    // Is it a ParentServerCommand?
    if (parent_command instanceof ParentServerCommand) {
      // Yes,
      ParentServerCommand cc = (ParentServerCommand) parent_command;
      try {
        status_code = cc.processProgramComplete(sctx, envs, p);
        // Assert, program complete can't return CONTINUE,
        if (status_code.equals("CONTINUE")) {
          throw new SessionProcessException("CONTINUE not allowed here");
        }
      }
      catch (RuntimeException e) {
        status_code = "STOP";
        // Print the exception,
        p.printExtendedError(null, null, e);
      }
      catch (Throwable e) {
        status_code = "STOP";
        // Print the exception,
        p.printExtendedError(null, null, e);
      }
    }

    return status_code;

  }


  /**
   * Handles the given status code on the top item.
   */
  static void handleCode(String status_code,
          EnvVarsImpl envs, ServerCommandContextImpl sctx,
          InstanceCWriter p) {

    boolean do_status_loop = true;
    while (do_status_loop) {

      Map<String, String> exported_map = null;

      do_status_loop = false;

      boolean must_close = false;
      // If prompt (this will wait for input),
      if (status_code.equals("PROMPT")) {

        // Set the prompt as defined in the envs,
        p.setPrompt(formatPrompt(envs));

        must_close = false;
      }
      else if (status_code.startsWith("STOP")) {
        // Any environment vars to export?
        if (status_code.startsWith("STOP:")) {
          exported_map = new HashMap();
          List<String> exported_vars =
                                parseJSONArray(status_code.substring(5));
          for (String var : exported_vars) {
            exported_map.put(var, envs.get(var));
          }
        }

        must_close = true;
      }
      else if (status_code.startsWith("WAIT")) {
        must_close = false;
      }
      else {
        throw new SessionProcessException(
                          "Unknown return status code: " + status_code);
      }

      // If we must close,
      if (must_close) {
        String ret_status_code = closeTopProgram(
                                          envs, sctx, p, exported_map);

        if (ret_status_code != null) {
          do_status_loop = true;
          status_code = ret_status_code;
        }

      }

    }

  }

  /**
   * The closure for 'genericHandle'
   */
  private static interface SCmdOp {
    String run(EnvVarsImpl envs,
               ServerCommandContextImpl sctx, InstanceCWriter p);
  }
  
  /**
   * Generically processes an operation and handles the return code.
   */
  private static void genericHandle(
          EnvVarsImpl envs, ServerCommandContextImpl sctx, InstanceCWriter p,
          SCmdOp code) {

    boolean continue_loop = true;
    while (continue_loop) {

      continue_loop = false;

      String status_code;

//      envs.put("cline", cmdline);
//
//      // We invoke the program at the top of the frame stack,
//      String prg = envs.get("prg");
//
//      // Get the program,
//      ServerCommand server_command = getTopServerCommand(prg, envs, sctx);
//
//      String status_code;

      try {
        
        status_code = code.run(envs, sctx, p);
        
        if (status_code == null) {
          status_code = "WAIT";
        }
        
//        // Call the program's 'process' method,
//        status_code = server_command.process(sctx, envs, p);
      }
      catch (KillSignalException e) {
        // Reset the kill signal,
        sctx.resetKillSignal();
        // Return 'STOP' status code on error,
        status_code = "STOP";
        // Print the exception,
        p.printExtendedError(null, null, e);
      }
      catch (RuntimeException e) {
        // Return 'STOP' status code on error,
        status_code = "STOP";
        // Print the exception,
        p.printExtendedError(null, null, e);
      }
      catch (Throwable e) {
        // Return 'STOP' status code on error,
        status_code = "STOP";
        // Print the exception,
        p.printExtendedError(null, null, e);
      }

      // Handle the code as appropriate,
      if (status_code.equals("CONTINUE")) {
        // If continue, we loop around,
        continue_loop = true;
      }
      else {
        SessionProcessOperation.handleCode(status_code, envs, sctx, p);
      }

    }

    p.flush();

  }

  /**
   * Processes the current frame stack set up in 'envs' given the input
   * command line. This will output to the given InstanceCWriter.
   */
  static void handle(final String cmdline, final EnvVarsImpl envs,
                     final ServerCommandContextImpl sctx,
                     final InstanceCWriter p) {

    genericHandle(envs, sctx, p, new SCmdOp() {
      @Override
      public String run(
          EnvVarsImpl envs, ServerCommandContextImpl sctx, InstanceCWriter p) {
        envs.put("cline", cmdline);

        // We invoke the program at the top of the frame stack,
        String prg = envs.get("prg");

        // Get the program,
        ServerCommand server_command = getTopServerCommand(prg, envs, sctx);

        // Call the program's 'process' method,
        String status_code = server_command.process(sctx, envs, p);
        return status_code;
      }
    });

  }

  /**
   * Handles the 'handle' method.
   */
  private static void handleHandle(final ServerCommand server_command,
                    final ProcessInputMessage input_msg,
                    final EnvVarsImpl envs,
                    final ServerCommandContextImpl sctx,
                    final InstanceCWriter p) {

    genericHandle(envs, sctx, p, new SCmdOp() {
      @Override
      public String run(
          EnvVarsImpl envs, ServerCommandContextImpl sctx, InstanceCWriter p) {

        // Call the program's 'handle' method,
        return server_command.handle(sctx, envs, p, input_msg);

      }
    });

  }

  private static void handleInteract(String feature,
                        EnvVarsImpl envs, ServerCommandContextImpl sctx,
                        InstanceCWriter p) {

    // We invoke the program at the top of the frame stack,
    String prg = envs.get("prg");

    // Get the program,
    ServerCommand server_command = getTopServerCommand(prg, envs, sctx);

    // Call the 'interact' method,
    String reply = server_command.interact(sctx, envs, feature);

    // If there's a result,
    if (reply != null) {
      // Flush it back to the client,
      p.sendInteractReply(reply);
      p.flush();
    }

  }

  /**
   * Process a function message and return a reply message. 'input' provides a
   * way to access the input queue in the none destructive way. 'msg' is the
   * message to process.
   */
  private ProcessMessage processMessage(
                              ServerCommandContextImpl sctx, Object[] args) {

    // Get the ProcessInstance for this operation,
    ProcessInstance instance = sctx.getProcessInstance();

    String cmd = (String) args[0];

    // The last command time stamp,
    String last_command_ts = environment.get("i.lts");
    if (last_command_ts == null) {
      refreshLTS();
    }
    else {
      // If this process has timed out or has been invalidated,
      long lts_val = Long.parseLong(last_command_ts, 16);
      // If 'lts' is '0' then 
      if (lts_val == 0 ||
          System.currentTimeMillis() > lts_val + NO_ACTIVITY_TIMEOUT_PERIOD) {

        // Put an error on the broadcast channel,
        String frame;
        SessionProcessException ex = new SessionProcessException(
                 (lts_val == 0) ? "Process invalidated" : "Process timed out");

        if (args.length >= 2) {
          // Get the frame,
          frame = (String) args[2];
          // Write the process invalidated or process timed out exception,
          InstanceCWriter p = getCWriter(frame);
          p.printExtendedError(null, null, ex);
          p.flush();
        }
        throw ex;
      }
    }

    String process_id_str = instance.getId().getStringValue();

    // If it's an init message,
    if (cmd.equals("init")) {
      // Assert,
      if (environment.get("i.user") != null) {
        throw new SessionProcessException("Process already initialized");
      }

      String username = (String) args[1];
      String frame = (String) args[2];
      String ip_addr = (String) args[3];
      checkFrameString(frame);
      environment.put("i.user", username);
      environment.put("i.ip", ip_addr);
      environment.put("i.stack_" + frame, "g");
      // Set initial environment vars (PENDING: load from a user directory?)
      initFrame(frame);

      // Make the envs object,
      EnvVarsImpl envs = new EnvVarsImpl(
                        environment, temp_environment, top_programs_map,
                        process_id_str, frame);

      String reference = envs.createNextCmdReference();
      ServerCommand console_command =
                        resolveServerCommand("console", reference, envs, sctx);

      envs.pushProgram(console_command);
      envs.put("prg", "console");

      InstanceCWriter p = getCWriter(frame);

      // Initialize and process the console,
      console_command.init(sctx, envs);
      // Initial process,
      String status_code = console_command.process(sctx, envs, p);
      if (status_code.equals("PROMPT")) {
        p.setPrompt(formatPrompt(envs));
      }

      // And flush,
      p.flush();

      // Reply to the message and return,
      return ByteArrayProcessMessage.nullMessage();

    }

    // This is a console command,
    else if (cmd.startsWith("@")) {

      // PENDING: IP validation,
      String ip_addr = (String) args[3];

      // Refresh the 'lts' time stamp.
      refreshLTS();

      // The frame,
      String frame = (String) args[2];
      checkFrameString(frame);

      InstanceCWriter p = getCWriter(frame);

//      // DEBUG
//      for (String key : environment.keySet()) {
//        p.println(key + "=" + environment.get(key), "debug");
//      }

      // Make the envs object,
      EnvVarsImpl envs = new EnvVarsImpl(
                        environment, temp_environment, top_programs_map,
                        process_id_str, frame);

      String cmdline = args[1].toString();

      // Handle the frame stack,
      SessionProcessOperation.handle(cmdline, envs, sctx, p);

      // Update the state map with our environment vars,
      instance.getStateMap().putAll(environment);

      // Flush the output,
      p.flush();

    }

    // This is an interact command,
    else if (cmd.equals("#")) {

      // PENDING: IP validation,
      String ip_addr = (String) args[3];

      // Refresh the 'lts' time stamp.
      refreshLTS();

      // The frame,
      String frame = (String) args[2];

      // The interact feature string,
      String feature = (String) args[1];

      InstanceCWriter p = getCWriter(frame);

      // Make the envs object,
      EnvVarsImpl envs = new EnvVarsImpl(
                        environment, temp_environment, top_programs_map,
                        process_id_str, frame);

      // Handle the frame stack,
      SessionProcessOperation.handleInteract(feature, envs, sctx, p);

      p.flush();

    }

    // This is an upload or download command,
    else if (cmd.equals("ul") || cmd.equals("dl")) {

      // PENDING: IP validation,
      String ip_addr = (String) args[3];

      // Refresh the 'lts' time stamp.
      refreshLTS();

      // PENDING: Priv checks on the file,
      // Validate the location,
      String loc = (String) args[1];

      PlatformContext ctx = sctx.getPlatformContext();

      FileName normal_loc_fn = new FileName(loc);
      FileRepository fs = ctx.getFileRepositoryFor(normal_loc_fn);
      FileInfo finfo = fs.getFileInfo(normal_loc_fn.getPathFile());

      if (finfo != null && finfo.isDirectory()) {
        // Return success message
        return ByteArrayProcessMessage.encodeArgs("OK", "$user", loc);
      }
      else {
        throw new SessionProcessException("Invalid location");
      }
    }

    // This is an authentication check,
    else if (cmd.equals("?")) {

      // PENDING: IP validation,
      String ip_addr = (String) args[3];

      // Refresh the 'lts' time stamp.
      refreshLTS();

      // Return success message,
      return ByteArrayProcessMessage.encodeArgs("OK", environment.get("i.user"));
    }

    // Invalidates this process,
    else if (cmd.equals("-")) {

      // PENDING: IP validation,
      String ip_addr = (String) args[3];

      invalidate();

      // Return success message,
      // (All further commands to this process will fail),
      return OK_MSG;

    }

    else {
      throw new SessionProcessException("Unknown command: " + cmd);
    }

    // Send null reply,
    return ByteArrayProcessMessage.nullMessage();
    
  }

  /**
   * Processes a kill signal from the central loop (not from app code).
   */
  private void processKillSignal(ServerCommandContextImpl sctx) {

    // Get the last kill signal,
    String[] args = sctx.getLastKillSignal();

    // Get the ProcessInstance for this operation,
    ProcessInstance instance = sctx.getProcessInstance();

    String process_id_str = instance.getId().getStringValue();

    String ip_addr = null;
    String frame = null;

    if (args.length >= 3) {
      ip_addr = (String) args[2];
    }
    if (args.length >= 2) {
      frame = (String) args[1];
      checkFrameString(frame);
    }

    // PENDING: IP validation,

    // Refresh the 'lts' time stamp.
    refreshLTS();

    // The signal,
    String signal = (String) args[0];

    // Is there a signal?
    if (signal != null) {
      // If 'kill' then reset the kill signal
      if (signal.equals("kill")) {

        if (frame != null) {
          // Force the top program to STOP,
          // Make the envs object,
          EnvVarsImpl envs = new EnvVarsImpl(
                        environment, temp_environment, top_programs_map,
                        process_id_str, frame);

          InstanceCWriter p = getCWriter(frame);

          // Don't let us STOP the bottom most program on this frame,
          if (!envs.isStackOn("1")) {

            String prompt_string_before = formatPrompt(envs);

            // Force the top program to a stop event,
            SessionProcessOperation.handleCode("STOP", envs, sctx, p);

            // Flush,
            p.print(prompt_string_before);
            p.println("^C", "debug");

          }
          else {
            // Just to give some feedback
            p.println("^C", "debug");
          }

          p.flush();
        }

      }

    }

    // All signals are currently ignored here,

  }

  /**
   * Processes a broadcast message from another process.
   */
  private void processBroadcastMessage(
                 ServerCommandContext sctx,
                 ChannelSessionState channel_state, ProcessMessage message) {

    // Get the ProcessInstance for this operation,
    ProcessInstance instance = sctx.getProcessInstance();

    // Get the process channel and the message,
    ProcessChannel channel = channel_state.getProcessChannel();

    // Get the frame id for the process channel,
    String frame_id = (String) temp_environment.get("c." + channel.toString());

    if (frame_id == null) {
      // None, so return,
      return;
    }

    // Channel 0 is considered a BroadcastSWriter styled packet,
    if (channel.getChannel() == 0) {

      // Pipe the message to the frame,

      // Convert the packet set into a String,
      StringBuilder packet_content = new StringBuilder();
      try {
        Reader r = new InputStreamReader(message.getMessageStream(), "UTF-8");
        while (true) {
          int c = r.read();
          if (c == -1) {
            break;
          }
          packet_content.append((char) c);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }

      // As a string,
      String packet_str = packet_content.toString();

      InstanceCWriter p = getCWriter(frame_id);

      // Check the packet starts with 'b0|'
      if (packet_str.startsWith("b0|")) {
        p.writePacketBlock(packet_str.substring(3));
      }
      else {
        // Try and decode it instead,
        Object[] args = ByteArrayProcessMessage.decodeArgsList(message);
        boolean first = true;
        for (int i = 0; i < args.length; ++i) {
          if (!first) {
            p.print(", ", "debug");
          }
          p.print(args[i], "debug");
          first = false;
        }
        p.println();
      }

      p.flush();

    }

  }

  /**
   * Processes a handled message. A handled message is a BROADCAST, RETURN or
   * TIMED input message that may need to be dispatched to a ServerCommand
   * that has registered to be associated with these messages.
   */
  private void processHandledMessage(ServerCommandContextImpl ctx,
                                     ProcessInputMessage input_msg) {

    Collection<ServerCommand> to_notify;

    ProcessInputMessage.Type type = input_msg.getType();

    if (type == ProcessInputMessage.Type.RETURN ||
        type == ProcessInputMessage.Type.RETURN_EXCEPTION ||
        type == ProcessInputMessage.Type.TIMED_CALLBACK) {
      // Fetch the call_id
      int call_id = input_msg.getCallId();

      // Find ServerCommand instances that are associated with this,
      ServerCommand cmd = ctx.removeServerCommandForCallId(call_id);
      to_notify = (cmd == null) ?
                            Collections.EMPTY_SET : Collections.singleton(cmd);

    }
    else if (type == ProcessInputMessage.Type.BROADCAST) {
      // Fetch the process channel,
      ProcessChannel process_channel =
                      input_msg.getBroadcastSessionState().getProcessChannel();

      if (process_channel.getChannel() == 0) {
        ChannelSessionState channel_state =
                                          input_msg.getBroadcastSessionState();
        processBroadcastMessage(ctx, channel_state, input_msg.getMessage());
      }

      // Find ServerCommand instances that are associated with this,
      to_notify = ctx.getServerCommandsForChannel(process_channel);

    }
    else {
      throw new RuntimeException("Unknown message type");
    }

    if (to_notify.isEmpty()) {
      // Don't dispatch,
      return;
    }

    // Dispatch to the process,

    // Refresh the 'lts' time stamp.
    refreshLTS();

    // Get the process instance,
    ProcessInstance instance = ctx.getProcessInstance();
    String process_id_str = instance.getId().getStringValue();

    // Callback on all the commands to notify,
    for (ServerCommand cmd : to_notify) {

      // Extract the frame from the reference,
      String ref = cmd.getReference();
      int delim = ref.indexOf("-");
      String frame = ref.substring(0, delim);

      InstanceCWriter p = getCWriter(frame);

      // Make the envs object,
      EnvVarsImpl envs = new EnvVarsImpl(
                        environment, temp_environment, top_programs_map,
                        process_id_str, frame);

      // Handle the 'handle' call,
      SessionProcessOperation.handleHandle(cmd, input_msg, envs, ctx, p);

      // Flush the writer,
      p.flush();

    }

    // Update the state map with our environment vars,
    instance.getStateMap().putAll(environment);

  }


  // -----

  @Override
  public ProcessOperation.Type getType() {
    return ProcessOperation.Type.TRANSIENT;
  }

  @Override
  public boolean isDormant() {
    // Queries all the server commands to see if they are active.
    Collection<List<ServerCommand>> server_commands = top_programs_map.values();
    for (List<ServerCommand> cmds : server_commands) {
      if (cmds != null && !cmds.isEmpty()) {
        for (ServerCommand c : cmds) {
          if (c.isActive()) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Override
  public void suspend(StateMap state) {
    writeTo(environment, state);
  }

  @Override
  public void resume(ProcessInstance instance) {
    readFrom(environment, instance.getStateMap());
  }

  @Override
  public void function(ProcessInstance instance) {

    // Initialize the ServerCommandContext
    server_command_context.init(instance);

    // Loop until no input waiting,
    while (true) {
      // Try and consume any signals,
      if (server_command_context.isKilled()) {

        // Process the kill signal and reset it,
        processKillSignal(server_command_context);
        server_command_context.resetKillSignal();

      }
      else {

        // Try and consume a command from the input queue,
        ProcessInputMessage function_msg = instance.consumeMessage();

        if (function_msg == null) {
          return;
        }

        try {

          // Is it a function message?
          ProcessInputMessage.Type function_msg_type = function_msg.getType();
          if (function_msg_type == ProcessInputMessage.Type.FUNCTION_INVOKE) {
            // Process message and either send a reply or failure,
            try {

              // Decode the arguments from the message,
              ProcessMessage msg = function_msg.getMessage();
              Object[] args = ByteArrayProcessMessage.decodeArgsList(msg, 0);

              ProcessMessage return_msg = processMessage(
                                                server_command_context, args);
              // Send a success reply for the given function message,
              instance.sendReply(function_msg, return_msg);
            }
            catch (KillSignalException e) {
              server_command_context.resetKillSignal();
              instance.sendFailure(function_msg, e);
            }
            catch (Throwable e) {
              instance.sendFailure(function_msg, e);
            }
          }
          // Any other type of message we pass through to 'processHandleMessage'
          else {
            // Dispatch the message to the appropriate place
            processHandledMessage(server_command_context, function_msg);
          }

        }
        finally {
          // Update the state map with our environment vars,
          StateMap state_map = instance.getStateMap();
          state_map.lock();
          try {
            state_map.clear();
            state_map.putAll(environment);
          }
          finally {
            state_map.unlock();
          }
        }
      }

    }

  }

}
