/**
 * com.mckoi.mwpui.CommandDatabase  May 4, 2012
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

import com.mckoi.apihelper.JOptSimpleUtils;
import com.mckoi.lib.joptsimple.OptionException;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.lib.joptsimple.OptionSet;
import com.mckoi.process.ProcessInputMessage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * A map of some default server commands implemented in Java.
 *
 * @author Tobias Downer
 */

public class CommandDatabase {

  /**
   * The standard console commands.
   */
  private final static HashMap<String, Class> standard_programs;



  static {
    standard_programs = new HashMap();

    // set command,
    standard_programs.put("set", SetCommand.class);

    // jvmfree command,
    standard_programs.put("jvm", JVMCommand.class);

    // wait command,
    standard_programs.put("wait", WaitCommand.class);


//    // upload command,
//    standard_programs.put("upload", UploadCommand.class);
//    // download command,
//    standard_programs.put("download", DownloadCommand.class);



    // Show logging information,
    standard_programs.put("log", SCommandLogReport.class);

    // 'ogon' builder
    standard_programs.put("ogon", SCommandOgon.class);

    // 'viewer' command,
    standard_programs.put("viewer", SCommandViewer.class);

    // 'edit' command,
    standard_programs.put("edit", SCommandEdit.class);
    
    // 'mwp' SU admin command,
    standard_programs.put("mwp", SCommandMWP.class);

    // 'process' command,
    standard_programs.put("process", SCommandProcess.class);

    // 'console' command,
    standard_programs.put("console", SCommandConsole.class);

    // 'sync' command,
    standard_programs.put("sync", SCommandFileSync.class);
    

  }

  /**
   * Returns an instance of ServerCommand for the command with the given name,
   * or returns null if no command found.
   */
  ServerCommand getCommand(ServerCommandContext sctx, String program_name,
                           String scommand_reference) {

    // Assert,
    if (program_name == null ||
        program_name.length() == 0 ||
        program_name.endsWith("/")) {
      return null;
    }

    // Check the standard internal programs,
    Class<ServerCommand> sc_clazz = standard_programs.get(program_name);
    if (sc_clazz != null) {
      try {
        Constructor<ServerCommand> c = sc_clazz.getConstructor(String.class);
        ServerCommand scmd = c.newInstance(scommand_reference);
        return scmd;
      }
      catch (InvocationTargetException e) {
        throw new RuntimeException(e.getTargetException());
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (InstantiationException e) {
        throw new RuntimeException(e);
      }
      catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    return null;

  }

  // ----- Standard console commands -----

  /**
   * 'set' command.
   */
  private static class SetCommand extends DefaultSCommand {

    public SetCommand(String reference) {
      super(reference);
    }

    @Override
    public String process(ServerCommandContext ctx,
                          EnvironmentVariables vars, CommandWriter out) {

      OptionParser parser = new OptionParser();
      parser.acceptsAll(Arrays.asList("?", "help"), "Shows help");
      parser.accepts("debug", "Shows environment variables debugging information");
      OptionSet options;
      try {
        options = parser.parse(JOptSimpleUtils.toArgs(vars.get("cline")));
      }
      catch (OptionException e) {
        out.println("set: " + e.getMessage(), "error");
        return "STOP";
      }
              
      List<String> non_opt_args = options.nonOptionArguments();

      if (options.has("?")) {
        out.println(" set [variable = value]", "info");
        out.println();
        JOptSimpleUtils.printHelpOn(parser, out);
        return "STOP";
      }

      boolean show_debug = options.has("debug");

      if (non_opt_args.size() == 1) {
        // Display the current vars,
        {
          Set<String> keys = vars.keySet();
          for (String k : keys) {
            out.print(k);
            out.print("=");
            out.println(vars.get(k), "info");
          }
        }

        // If we show the debug info,
        if (show_debug) {
          Map<String, String> DBG_map =
                                   ((EnvVarsImpl) vars).getBackedMapDEBUG();
          Set<String> keys = DBG_map.keySet();

          // Sort the keys,
          ArrayList<String> keys_list = new ArrayList(keys.size());
          keys_list.addAll(keys);
          Collections.sort(keys_list);

          for (String k : keys_list) {
            out.println("  " + k + "=" + DBG_map.get(k), "debug");
          }
        }

        return "STOP";
      }

      out.println("PENDING", "error");

      return "STOP";
    }

  }

  /**
   * JVM commands.
   */
  private static class JVMCommand extends DefaultSCommand {

    public JVMCommand(String reference) {
      super(reference);
    }

    @Override
    public String process(ServerCommandContext ctx,
                          EnvironmentVariables vars, CommandWriter out) {

      OptionParser parser = new OptionParser();
      parser.acceptsAll(Arrays.asList("?", "help"), "Shows help");
      OptionSet options;
      try {
        options = parser.parse(JOptSimpleUtils.toArgs(vars.get("cline")));
      }
      catch (OptionException e) {
        out.println("jvm: " + e.getMessage(), "error");
        return "STOP";
      }
      List<String> non_opt_args = options.nonOptionArguments();

      if (options.has("?") || non_opt_args.size() <= 1) {
        out.println(" jvm [free|gc]", "info");
        out.println();
        JOptSimpleUtils.printHelpOn(parser, out);
        return "STOP";
      }

      String cmd = non_opt_args.get(1);

      Runtime r = Runtime.getRuntime();

      if (cmd.equals("free")) {
        long free_memory = r.freeMemory();
        long total_memory = r.totalMemory();
        long max_memory = r.maxMemory();
        long used_memory = total_memory - free_memory;

        out.println("JVM Runtime Information");
        out.println();
        out.print(" Used:  ");
        out.println(
              FormattingUtils.formatShortFileSizeValue(used_memory), "info");
        out.print(" Total: ");
        out.println(
              FormattingUtils.formatShortFileSizeValue(total_memory), "info");
        out.print(" Max:   ");
        out.println(
              FormattingUtils.formatShortFileSizeValue(max_memory), "info");
        out.println();

      }
      else if (cmd.equals("gc")) {
        out.print("Performing garbage collection: ");
        out.flush();
        r.gc();
        out.println("Done", "info");
      }
      else {
        out.println("jvm: unknown command: " + cmd, "error");
      }

      return "STOP";
    }

  }

  private static class WaitCommand extends DefaultSCommand {

    public WaitCommand(String reference) {
      super(reference);
    }

    @Override
    public String process(ServerCommandContext ctx,
                          EnvironmentVariables vars, CommandWriter out) {

      OptionParser parser = new OptionParser();
      parser.acceptsAll(Arrays.asList("?", "help"), "Shows help");
      OptionSet options;
      try {
        options = parser.parse(JOptSimpleUtils.toArgs(vars.get("cline")));
      }
      catch (OptionException e) {
        out.println("wait: " + e.getMessage(), "error");
        return "STOP";
      }
      List<String> non_opt_args = options.nonOptionArguments();

      if (options.has("?") || non_opt_args.size() <= 1) {
        out.println(" wait <milliseconds>", "info");
        out.println();
        JOptSimpleUtils.printHelpOn(parser, out);
        return "STOP";
      }

      long wait_time = Long.parseLong(non_opt_args.get(1));
      long dif = wait_time / 10;

      out.print("Waiting");
      out.flush();

      waitThenCallback(out, dif, 10, ctx);
      return "WAIT";

    }

    /**
     * Waits for the given number of milliseconds then calls back and prints
     * on the console.
     */
    private void waitThenCallback(final CommandWriter out, 
                    final long wait_time, final int i,
                    final ServerCommandContext ctx) {

      scheduleCallback(wait_time, null, ctx, new FunctionClosure() {
        @Override
        public String run(ProcessInputMessage msg) {
          if ((i % 2) == 0) {
            out.print(".");
          }
          else {
            out.print(".", "debug");
          }

          if (i == 0) {
            out.println();
            out.flush();
            return "STOP";
          }
          else {
            out.flush();
            waitThenCallback(out, wait_time, i - 1, ctx);
            return "WAIT";
          }
        }
      });

    }

  }

}
