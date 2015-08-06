/**
 * com.mckoi.mwpui.SCommandConsole  May 7, 2012
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
import com.mckoi.process.ProcessId;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.LogSystem;
import com.mckoi.webplatform.PlatformContext;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.json.JSONArray;

/**
 * 
 *
 * @author Tobias Downer
 */

public class SCommandConsole extends DefaultSCommand
                             implements ParentServerCommand {

  public SCommandConsole(String reference) {
    super(reference);
  }

  @Override
  public String getIconString() {
    return "console";
  }

  @Override
  public String init(ServerCommandContext ctx, EnvironmentVariables var) {

    // Signify initial,
    var.put("$", "init");
    var.put("pwd", "/" + ctx.getAccountName());

    // PENDING: These should be loaded from an 'rc' file of some sort.
    var.put("prompt", "$pwd> ");
    var.put("NODE_PATH",
                   "/" + ctx.getAccountName() + "/bin/lib/node_modules/");

    // No need to init,
    return WINDOW_MODE;
  }

  @Override
  public String process(ServerCommandContext ctx,
                        EnvironmentVariables vars, CommandWriter out) {

    // If it's initial call,
    String is = vars.get("$");
    if (is != null) {
      if (is.equals("init")) {
        // Initial call,
        // Announce we logged in,
        out.cls();
        out.println("Mckoi Web Platform", "info");
        out.println();
      }
      vars.put("$", null);
      return "PROMPT";
    }

    EnvVarsImpl env_vars = (EnvVarsImpl) vars;

    // Get the command line,
    String cmdline = vars.get("cline");

    String prg;
    String prg_ex = cmdline.trim();
    // Parse out the program name,
    int delim = prg_ex.indexOf(' ');
    if (delim == -1) {
      delim = prg_ex.indexOf('\t');
    }
    if (delim == -1) {
      prg = prg_ex;
    }
    else {
      prg = prg_ex.substring(0, delim);
    }

    // If no program simply go back to prompt,
    if (prg == null || prg.equals("")) {
      return "PROMPT";
    }

    // Special handling of 'exit'
    if (prg.equals("exit")) {
      return "STOP";
    }

    try {
      // Log,
      PlatformContext pctx = ctx.getPlatformContext();
      LogSystem log_sys = pctx.getLogSystem();
      ProcessId process_id = ctx.getProcessInstance().getId();

      // PENDING; Need a better way to get access to this info,
      Map<String, String> DBG_map =
                                   ((EnvVarsImpl) vars).getBackedMapDEBUG();
      String ipaddr = DBG_map.get("i.ip");
      String user = DBG_map.get("i.user");

      log_sys.log(Level.INFO, "app.console", "{0}@{1} {2} {3}",
                  new String[] { user, ipaddr,
                                 process_id.toString(), cmdline });
      
      // Get the command
      String reference = env_vars.createNextCmdReference();
      ServerCommand server_command =
              SessionProcessOperation.resolveServerCommand(
                                                    prg, reference, vars, ctx);

      // If not found,
      if (server_command == null) {
        out.println(prg + ": not found", "error");
        return "PROMPT";
      }

      // Push a program onto the stack frame,
      env_vars.pushProgram(server_command);
      // Set the env,
      env_vars.put("prg", prg);

      // Initialize,
      String mode;
      try {
        mode = server_command.init(ctx, env_vars);
      }
      catch (Throwable e) {
        // Pop the program if init threw an exception,
        env_vars.popProgram();
        throw e;
      }
      if (mode.equals(ServerCommand.WINDOW_MODE)) {

        // If it's window mode we need to fork the environment vars into
        // a new stack frame.
        InstanceCWriter p = (InstanceCWriter) out;
        final InstanceCWriter old_p = p;
        String title = prg;
        env_vars = env_vars.forkTop(title);
        String icon = server_command.getIconString();
        // Tell the client to create a new frame and return an
        // InstanceCWriter for it,
        p = p.createNewFrame(title, env_vars.getFrameName(), icon);
        // Make sure to flush the originating writer,
        old_p.flush();

        // Dispatch,
        ServerCommandContextImpl sctx_imp = (ServerCommandContextImpl) ctx;
        SessionProcessOperation.handle(cmdline, env_vars, sctx_imp, p);

        return "PROMPT";

      }
      // Console mode,
      else {

        // Ask parent to continue with the program at the top of the stack,
        return "CONTINUE";

      }

    }
    // If there's any errors we catch and display, and return to the prompt,
    catch (Throwable e) {
      out.printExtendedError(null, null, e);
      return "PROMPT";
    }

  }

  @Override
  public String processProgramComplete(ServerCommandContext ctx,
                            EnvironmentVariables vars, CommandWriter out) {

    // When a child program ends, we set the prompt,
    return "PROMPT";

  }

  private static List<FileInfo> findMatchingCompleteList(
                                           List<FileInfo> list, String match) {

    Comparator<Object> c =
                   new com.mckoi.mwpui.apihelper.FileUtils.FileInfoToStringNameComparator();

    int p = Collections.binarySearch(list, match, c);
    if (p < 0) {
      p = -(p + 1);
    }
    List<FileInfo> tail_files = list.subList(p, list.size());
    int end_ind = 0;
    for (FileInfo fi : tail_files) {
      String name = fi.getItemName();
      if (name.startsWith(match)) {
        ++end_ind;
      }
      else {
        break;
      }
    }

    return tail_files.subList(0, end_ind);

  }
  
  @Override
  public String interact(ServerCommandContext ctx, EnvironmentVariables vars,
                         String feature) {

    // Handle tab-completion,

    if (feature == null) return null;

    if (feature.startsWith("tabcomplete ")) {
      final String tab_complete_cmd = feature.substring(12);

      // Look at the last part of the command,
      String[] args = CommandLine.parseArgs(tab_complete_cmd, false);
      if (args.length > 0) {
        String to_complete = "";
        if (!tab_complete_cmd.endsWith(" ") &&
            !tab_complete_cmd.endsWith("\t")) {
          to_complete = args[args.length - 1];
        }

        final String raw_to_complete = to_complete;

        // Find all likely candidates,

        // Remove any quotes from the path,
        if (to_complete.startsWith("\"")) {
          to_complete = to_complete.substring(1);
        }
        if (to_complete.endsWith("\"")) {
          to_complete = to_complete.substring(0, to_complete.length() - 1);
        }

        // The start path (the part of the path that doesn't change,
        String start_path = "";
        String cmd_txt;
        // Need to handle this specially,
        if (to_complete.equals(".") || to_complete.equals("..")) {
          to_complete = to_complete + "/";
        }
        int delim = to_complete.lastIndexOf('/');
        if (delim != -1) {
          start_path = to_complete.substring(0, delim + 1);
        }
        cmd_txt = tab_complete_cmd.substring(
                     0, tab_complete_cmd.length() - raw_to_complete.length());

        // Convert to fname
        FileName fname = new FileName(to_complete);
        FileName pwd_fn = new FileName(vars.get("pwd"));
        fname = pwd_fn.resolve(fname);

        PlatformContext pctx = ctx.getPlatformContext();

        FileRepository fs = pctx.getFileRepositoryFor(fname);
        // Nothing to complete against,
        if (fs == null) {
          return null;
        }

        String complete_path;
        String complete_item;

        // Is it definitely a directory reference?
        if (fname.isDirectory()) {
          // Yes, tab complete against the path,
          complete_path = fname.getPathFile();
          complete_item = "";
          if (start_path.length() > 0 && !start_path.endsWith("/")) {
            start_path = start_path + "/";
          }
        }
        else {
          complete_path = fname.getPath().getPathFile();
          complete_item = fname.getFile().toString();
        }

        // Fetch the path and file list to sort through,
        List<FileInfo> file_list = fs.getFileList(complete_path);
        List<FileInfo> dir_list = fs.getSubDirectoryList(complete_path);

        List<FileInfo> match_file_list =
                           findMatchingCompleteList(file_list, complete_item);
        List<FileInfo> match_dir_list =
                           findMatchingCompleteList(dir_list, complete_item);

        Collection<FileInfo> out =
                 com.mckoi.mwpui.apihelper.FileUtils.mergeFileLists(
                                              match_file_list, match_dir_list);
        
        String output_json;
        JSONArray arr = new JSONArray();
        arr.put(cmd_txt);
        for (FileInfo f : out) {
          String tc = start_path + f.getItemName();
          if (tc.contains(" ")) {
            tc = "\"" + tc + "\"";
          }
          arr.put(tc);
        }
        output_json = arr.toString();

        return "*tabcomplete " + output_json;
      }
      
    }

    return null;

  }

}