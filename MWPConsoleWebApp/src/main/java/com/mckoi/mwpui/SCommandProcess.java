/**
 * com.mckoi.mwpui.SCommandProcess  Nov 15, 2012
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
import com.mckoi.apihelper.TextUtils;
import com.mckoi.lib.joptsimple.OptionException;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.lib.joptsimple.OptionSet;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.rhino.JSWrapBase;
import java.util.Arrays;
import java.util.List;

/**
 * Server command that executes a process in a given application context.
 * Some examples of valid process commands;
 * <pre>
 *   process testsuite class com.mckoi.mwptestsuite.TestProcess
 *   process ptest1
 * </pre>
 *
 * @author Tobias Downer
 */

public class SCommandProcess extends DefaultSCommand {

  public SCommandProcess(String reference) {
    super(reference);
  }

  @Override
  public String process(ServerCommandContext sctx, EnvironmentVariables vars,
                        final CommandWriter out) {

    PlatformContext ctx = sctx.getPlatformContext();

    // Build a command line parser,
    OptionParser parser = new OptionParser();
    parser.acceptsAll(Arrays.asList("?", "help"),
            "Show help");
    parser.acceptsAll(Arrays.asList("a", "application"),
            "Invoke process in the application context (defaults to the console app)")
          .withRequiredArg().describedAs("app name");
    parser.acceptsAll(Arrays.asList("u", "account"),
            "Invoke process in the account's context (defaults to this account)")
          .withRequiredArg().describedAs("account name");
    parser.accepts("c", "Invoke a Java OperationProcess class");
    parser.accepts("j", "Invoke a JavaScript process (default)");

    // Parse the command line,
    OptionSet options;
    try {
      options = parser.parse(
                    TextUtils.splitCommandLineAndUnquote(vars.get("cline")));
    }
    catch (OptionException e) {
      out.println("process: " + e.getMessage(), "error");
      return "STOP";
    }

    // Print help,
    if (options.has("?")) {
      JOptSimpleUtils.printHelpOn(parser, out);
      return "STOP";
    }

    // Set up vars,
    String type = "js";
    String target;
    String app_name;
    String account_name;

    List<String> non_opt_args = options.nonOptionArguments();
    if (non_opt_args.size() <= 1) {
      out.println("process: no process given", "error");
      return "STOP";
    }
    if (non_opt_args.size() > 2) {
      out.println("process: too many arguments", "error");
      return "STOP";
    }
    target = non_opt_args.get(1);

    app_name = (String) options.valueOf("a");
    account_name = (String) options.valueOf("u");

    if (options.has("j")) {
      type = "js";
    }
    if (options.has("c")) {
      type = "class";
    }

    String return_val = "STOP";

    ProcessId process_id;
    // If it's a JavaScript process,
    if (type.equals("js")) {
      FileName pwd_fn = new FileName(vars.get("pwd"));
      FileName normal_fn = pwd_fn.resolve(new FileName(target));

      FileRepository fs = ctx.getFileRepositoryFor(normal_fn);
      String file = normal_fn.getPathFile();
      FileInfo finfo = fs.getFileInfo(file);
      if (finfo == null || !finfo.isFile()) {
        finfo = fs.getFileInfo(file + ".js");
        if (finfo == null || !finfo.isFile()) {
          finfo = fs.getFileInfo(file + ".mjs");
          if (finfo == null || !finfo.isFile()) {
            out.println("process: not found: " + file, "error");
            return "STOP";
          }
        }
      }

      // Fully qualified,
      String js_script_loc = normal_fn.toString();

      // Create the java script process
      process_id =
              JSWrapBase.createJavaScriptProcess(ctx, app_name, js_script_loc);

    }
    // If it's a Java class process,
    else {

      try {
        // Get the client,
        InstanceProcessClient client = ctx.getInstanceProcessClient();

        // The process object to run,
        String process_name = target;

        // Create a process,
        process_id = client.createProcess(app_name, process_name);
        // Send '.init' message to the process,
        ProcessMessage msg = ByteArrayProcessMessage.encodeArgs(".init", target);
        invokeFunction(process_id, msg, sctx, new FunctionClosure() {
          @Override
          public String run(ProcessInputMessage msg) {
            // Print exception if it's an exception,
            if (msg.getType() == ProcessInputMessage.Type.RETURN_EXCEPTION) {
              out.printExtendedError(null, null, msg.getError());
            }
            // Stop this command,
            return "STOP";
          }
        });

        // Return 'WAIT'
        return_val = "WAIT";

      }
      catch (ProcessUnavailableException e) {
        out.printExtendedError(null, null, e);
        return "STOP";
      }

    }

    // Print details about the process we just invoked,
    out.println("id = (" + process_id.getStringValue() + ")", "info");

    // Get the client,
    InstanceProcessClient client = ctx.getInstanceProcessClient();
    // Listen to channel 0,
    ProcessChannel channel = new ProcessChannel(process_id, 0);
    try {
      client.addChannelListener(channel);
      // Connect the process channel of this process to this frame,
      EnvVarsImpl vars_impl = (EnvVarsImpl) vars;
      vars_impl.connectFrameTo(channel);
    }
    catch (ProcessUnavailableException e) {
      out.println("Unable to add channel listener to: " + channel, "error");
      out.printExtendedError(null, null, e);
    }

    // Return either 'WAIT' or 'STOP' depending on whether we set up the
    // closure or not.
    return return_val;

  }

}
