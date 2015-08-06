/**
 * com.mckoi.mwpui.SCommandMWP  Sep 29, 2012
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

import com.mckoi.appcore.AppCoreAdmin;
import com.mckoi.appcore.CommandProcessor;
import com.mckoi.network.MckoiDDBAccess;
import com.mckoi.network.NetworkAccess;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.SuperUserPlatformContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The 'mwp' administration server command that works very similar to the
 * command line version.
 *
 * @author Tobias Downer
 */

public class SCommandMWP extends DefaultSCommand {

  public SCommandMWP(String reference) {
    super(reference);
  }

  @Override
  public String process(ServerCommandContext ctx,
                        EnvironmentVariables vars, CommandWriter out) {

    try {
      // Get the super user context,
      SuperUserPlatformContext su_ctx =
                          PlatformContextFactory.getSuperUserPlatformContext();

      MckoiDDBAccess mckoi_ddb = su_ctx.getMckoiDDBAccess();
      NetworkAccess network = su_ctx.getNetworkAccess();

//      out.println("Mckoi Web Platform admin tool.", "info");

      // Create the command processor,
      AppCoreAdmin app_core = new AppCoreAdmin(mckoi_ddb, network);
      CommandProcessor cmd_process = new CommandProcessor(app_core);

      CommandLine cline = new CommandLine(vars.get("cline"));
      String[] args = cline.getArgs();

      List<String> in_args = new ArrayList(args.length - 1);
      for (int i = 1; i < args.length; ++i) {
        in_args.add(args[i]);
      }

      // Process the command,
      try {
        cmd_process.processCommand(out, in_args);
      }
      catch (Exception e) {
        out.printExtendedError(null, null, e);
      }

    }
    catch (SecurityException e) {
      out.println("Not permitted to use this command.", "error");
    }

    return "STOP";
  }

}
