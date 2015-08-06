/**
 * com.mckoi.mwpbase.SCommandOgon  Oct 13, 2011
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2010  Diehl and Associates, Inc.
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
import com.mckoi.appcore.UserApplicationsSchema;
import com.mckoi.lib.joptsimple.OptionException;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.lib.joptsimple.OptionSet;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.util.StyledPrintWriter;
import com.mckoi.webplatform.BuildError;
import com.mckoi.webplatform.BuildSystem;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The 'ogon' project command line tool.
 *
 * @author Tobias Downer
 */

public class SCommandOgon extends DefaultSCommand {

  public SCommandOgon(String reference) {
    super(reference);
  }

  private void printCompileErrors(CommandWriter out, List<BuildError> errors) {

    out.println();

    // Print the build summary,
    int error_count = 0;
    int warning_count = 0;

    for (BuildError err : errors) {
      String file_source = err.getFile();
      if (file_source == null) file_source = "";
      long line_no = err.getLineNumber();
      String err_type = err.getType();
      String err_str = err.getErrorString();
      Throwable error_th = err.getErrorException();

      String message_type = null;
      boolean extra_space = false;

      if (err_type.equals(BuildError.ERROR)) {
        ++error_count;
        message_type = "error";
        extra_space = true;
      }
      else if (err_type.equals(BuildError.WARNING)) {
        ++warning_count;
        extra_space = true;
      }
      else if (err_type.equals(BuildError.INFO)) {
        extra_space = false;
      }

      // Create the location string,
      String loc_string = file_source + ":" + line_no;

      // Print the error location,
      if (!file_source.equals("") && line_no != -1) {
        out.println(loc_string, message_type);
      }
      if (err_str == null) {
        out.println("(Problem has no error message)", message_type);
      }
      else {
        // HACK: If the location string is part of the error string then we
        //   remove it.
        int len = loc_string.length();
        if (err_str.startsWith(loc_string) && err_str.length() > len + 2) {
          err_str = err_str.substring(len + 2);
        }
        // Print the error.
        out.println(err_str, message_type);
      }
      if (error_th != null) {
        out.printExtendedError(null, null, error_th);
      }

      if (extra_space) {
        out.println();
      }

    }

    if (error_count > 0 || warning_count > 0) {
      out.print("ogon: ");
      out.print(error_count + " error(s) ", "error");
      out.println(warning_count + " warning(s).");
    }

  }
  
  

  @Override
  public String process(ServerCommandContext sctx,
                        EnvironmentVariables vars, CommandWriter out) {

    PlatformContext ctx = sctx.getPlatformContext();

    OptionParser parser = new OptionParser();
    parser.acceptsAll(Arrays.asList("?", "help"), "Show the help");

    OptionSet options;
    String[] args;
    try {

      options = parser.parse(JOptSimpleUtils.toArgs(vars.get("cline")));
      
      List<String> non_opts = options.nonOptionArguments();

      // Extract the command,
      int args_sz = non_opts.size();
      args = new String[args_sz];
      for (int i = 0; i < args_sz; ++i) {
        args[i] = non_opts.get(i);
      }

    }
    catch (OptionException e) {
      out.println("ogon: " + e.getMessage(), "error");
      return "STOP";
    }

    // Create a new project,
    if (args.length == 4 &&
            args[1].equals("create") && args[2].equals("project")) {

      String path_str = args[3];
      FileName pwd_fn = new FileName(vars.get("pwd"));
      FileName normal_fn = new FileName(path_str);
      normal_fn = pwd_fn.resolve(normal_fn);

      FileRepository repository = ctx.getFileRepositoryFor(normal_fn);
      if (repository == null) {
        out.println("ogon: Invalid repository id: " + normal_fn, "error");
        return "STOP";
      }

      // Resolve the project path argument,
      String project_path = normal_fn.asDirectory().getPathFile();
      // Get session specific objects,
      BuildSystem build_sys = ctx.getBuildSystem();

      out.println("ogon: Creating default project at: " + project_path);
      out.println();

      // Create the web application project,
//      StringWriter str_out = new StringWriter();
//      PrintWriter build_out = new PrintWriter(str_out);

      boolean success = build_sys.createWebApplicationProject(
                                          repository, project_path, out);

//      build_out.flush();
//      out.println(str_out.toString());

      if (success) {
        try {
          repository.commit();
        }
        catch (CommitFaultException e) {
          out.print("ogon: ");
          out.println("Commit failed: " + e.getMessage(), "error");
        }
      }

      out.println();

      return "STOP";
    }

    // Build the JSP files,
    if (args.length == 3 && args[1].equals("jsp")) {

      long start_time = System.currentTimeMillis();

      final String target_str = args[2];

      FileRepository repository = ctx.getFileRepository();
      String rep_id = repository.getRepositoryId();

      FileName project_loc_fname = null;

      // See if this is an application name,
      Map<String, FileName> type_loc_map =
          UserApplicationsSchema.getUserAppLocationMap(repository, target_str);

      if (type_loc_map != null) {
        // Look for a src/ directory on one of the locations for the
        // application,
        for (String type : type_loc_map.keySet()) {
          // The main location,
          FileName loc_fname = type_loc_map.get(type);
          if (loc_fname != null) {
            if (!loc_fname.getRepositoryId().equals(rep_id)) {
              out.println(
                    "WARNING: Ignoring application location because the " +
                    "repository id is not in the host account: " + loc_fname,
                    "info");
            }
            else {
              project_loc_fname = loc_fname;
              break;
            }
          }
        }
      }

      if (project_loc_fname == null) {
        String path_str = target_str;
        FileName pwd_fn = new FileName(vars.get("pwd"));
        FileName normal_fn = new FileName(path_str);
        project_loc_fname = pwd_fn.resolve(normal_fn);
      }

      if (!rep_id.equals(project_loc_fname.getRepositoryId())) {
        repository = ctx.getFileRepositoryFor(project_loc_fname);
      }
      if (repository == null) {
        out.println("ogon: Invalid repository id: " + project_loc_fname, "error");
        return "STOP";
      }

      // Resolve the project path argument,
      String project_path = project_loc_fname.asDirectory().getPathFile();
      // Get session specific objects,
      BuildSystem build_sys = ctx.getBuildSystem();

      out.print("ogon: Building JSP pages at: ");
      out.println(project_path, "info");
      out.flush();

      ArrayList<BuildError> errors = new ArrayList();

      // Build the web application project,
      boolean success = build_sys.buildWebApplicationJSPPages(
                                        repository, project_path, out, errors);

      if (success) {
        boolean reload_webapp = build_sys.reloadWebApplicationAt(
                                          repository, project_path, out);
      }

      // Print any compiler errors,
      printCompileErrors(out, errors);

      // Try and commit if success,
      if (success) {
        out.flush();
        try {
          repository.commit();
        }
        catch (CommitFaultException e) {
          out.print("ogon: ");
          out.println("Commit failed: " + e.getMessage(), "error");
          success = false;
        }
      }

      if (!success) {
        out.print("ogon: ");
        out.println("Build failed.", "error");
      }
      else {
        out.print("ogon: ");
        out.println("Completed in " +
                  (System.currentTimeMillis() - start_time) + "ms.", "info");
      }

      return "STOP";

    }

    // Build a web application,
    if (args.length == 3 && args[1].equals("build")) {

      long start_time = System.currentTimeMillis();

      String app_name = args[2];

      FileRepository repository = ctx.getFileRepository();
      String rep_id = repository.getRepositoryId();

      FileName project_loc_fname = null;

      // See if this is an application name,
      Map<String, FileName> type_loc_map =
            UserApplicationsSchema.getUserAppLocationMap(repository, app_name);

      if (type_loc_map != null) {
        // Look for a src/ directory on one of the locations for the
        // application,
        for (String type : type_loc_map.keySet()) {
          // The main location,
          FileName loc_fname = type_loc_map.get(type);
          if (loc_fname != null) {
            if (!loc_fname.getRepositoryId().equals(rep_id)) {
              out.println(
                    "WARNING: Ignoring application location because the " +
                    "repository id is not in the host account: " + loc_fname,
                    "info");
            }
            else {
              FileName pos_fname = loc_fname.resolve(FileName.PREVIOUS_DIR);
              // Look for src directory,
              FileName src_loc_fname = pos_fname.resolve(new FileName("src/"));
              FileInfo src_fi =
                      repository.getFileInfo(src_loc_fname.getPathFile());
              // If ../src exists then use this build,
              if (src_fi != null) {
                project_loc_fname = pos_fname;
                break;
              }
            }
          }
        }
      }

      if (project_loc_fname == null) {
        String path_str = args[2];
        FileName pwd_fn = new FileName(vars.get("pwd"));
        FileName normal_fn = new FileName(path_str);
        project_loc_fname = pwd_fn.resolve(normal_fn);
      }

      if (!rep_id.equals(project_loc_fname.getRepositoryId())) {
        repository = ctx.getFileRepositoryFor(project_loc_fname);
      }
      if (repository == null) {
        out.println("ogon: Invalid repository id: " + project_loc_fname, "error");
        return "STOP";
      }

      // Resolve the project path argument,
      String project_path = project_loc_fname.asDirectory().getPathFile();
      // Get session specific objects,
      BuildSystem build_sys = ctx.getBuildSystem();

      out.print("ogon: Building project at: ");
      out.println(project_path, "info");
      out.flush();

      ArrayList<BuildError> errors = new ArrayList();

      // Build the web application project,
      boolean success = build_sys.buildWebApplicationProject(
                                 repository, project_path, out, errors);

      if (success) {
        boolean reload_webapp = build_sys.reloadWebApplicationAt(
                                          repository, project_path, out);
      }

      // Print any compiler errors,
      printCompileErrors(out, errors);

      // Try and commit if success,
      if (success) {
        out.flush();
        try {
          repository.commit();
        }
        catch (CommitFaultException e) {
          out.print("ogon: ");
          out.println("Commit failed: " + e.getMessage(), "error");
          success = false;
        }
      }

      if (!success) {
        out.print("ogon: ");
        out.println("Build failed.", "error");
      }
      else {
        out.print("ogon: ");
        out.println("Completed in " +
                  (System.currentTimeMillis() - start_time) + "ms.", "info");
      }

      return "STOP";

    }

    // Refresh the webapps.properties file,
    if (args.length == 2 && args[1].equals("refreshwebapps")) {

      BuildSystem build_sys = ctx.getBuildSystem();

      FileRepository repository = ctx.getFileRepository();

//      StringWriter so = new StringWriter();
//      PrintWriter error_out = new PrintWriter(so);

      boolean b = build_sys.buildWebApplicationConfig(repository, out);
//      error_out.flush();

      // If it failed,
      if (!b) {
        out.print("ogon: ");
        out.println("'refreshwebapps' failed.", "error");
//        out.println(so.toString(), "error");
      }
      // If it was successful,
      else {
        try {
          repository.commit();
        }
        catch (CommitFaultException e) {
          out.print("ogon: ");
          out.println("Commit failed: " + e.getMessage(), "error");
        }
      }

      // Stop after complete,
      return "STOP";
    }

    out.println("ogon syntax", "info");
    out.println();
    out.println(" ogon create project [directory]", "info");
    out.println("   Creates a new project at the given directory in the user's file system.");
    out.println(" ogon build [app name or directory]", "info");
    out.println("   Builds a project at the given directory in the user's file system.");
    out.println(" ogon jsp [app name or directory]", "info");
    out.println("   Builds the JSP files in the web application's build location.");
    out.println(" ogon refreshwebapps", "info");
    out.println("   Refreshes /system/webapps.pset");
    out.println();

    // Stop after complete,
    return "STOP";

  }

}
