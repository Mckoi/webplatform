/**
 * com.mckoi.mwpbase.SCommandViewer  Mar 6, 2012
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

import com.mckoi.webplatform.DDBResourceAccess;
import com.mckoi.webplatform.MckoiDDBPath;
import com.mckoi.webplatform.PlatformContext;
import java.util.Set;

/**
 * A command that brings up the database viewer in a new browser tab.
 *
 * @author Tobias Downer
 */

public class SCommandViewer extends DefaultSCommand {

  public SCommandViewer(String reference) {
    super(reference);
  }

  @Override
  public String process(ServerCommandContext sctx,
                        EnvironmentVariables vars, CommandWriter out) {

    PlatformContext ctx = sctx.getPlatformContext();

    // Print the message,
    out.println("Database Viewer", "info");
    out.println();

    DDBResourceAccess mckoi_access = ctx.getDDBResourceAccess();
    Set<MckoiDDBPath> all_paths = mckoi_access.getAllPaths();
    if (!all_paths.isEmpty()) {
      out.println("Click on the database below to open viewer.");
      for (MckoiDDBPath path : all_paths) {
        String consens_fun = path.getConsensusFunction();
        String path_name = path.getPathName();
        String db_type = " ";
        if (consens_fun.equals("com.mckoi.odb.ObjectDatabase")) {
          db_type = "ODB ";
        }
        out.print(" ");
        out.println(out.createApplicationLink(db_type + path_name,
                                              "DBBrowser/" + path_name + "/"));
      }
    }
    else {
      out.println("No databases available.", "info");
    }

    out.println();

    return "STOP";
  }

}
