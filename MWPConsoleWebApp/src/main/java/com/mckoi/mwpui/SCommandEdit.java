/**
 * com.mckoi.mwpui.SCommandEdit  May 9, 2012
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
import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.lib.joptsimple.OptionException;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.lib.joptsimple.OptionSet;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * JavaScript editor.
 *
 * @author Tobias Downer
 */

public class SCommandEdit extends DefaultSCommand {

  /**
   * Max file size limit.
   */
  private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;

  /**
   * Edit parser,
   */
  private static final OptionParser EDIT_PARSER;
  static {
    EDIT_PARSER = new OptionParser();
    EDIT_PARSER.acceptsAll(Arrays.asList("?", "help"), "Show help");
  }


  public SCommandEdit(String reference) {
    super(reference);
  }

  @Override
  public String getIconString() {
    return "edit";
  }

  @Override
  public String init(ServerCommandContext sctx, EnvironmentVariables vars) {
    
    PlatformContext ctx = sctx.getPlatformContext();

    OptionSet options;
    try {
      options = EDIT_PARSER.parse(JOptSimpleUtils.toArgs(vars.get("cline")));
    }
    catch (OptionException e) {
      vars.put("$", "err");
      return ServerCommand.CONSOLE_MODE;
    }

    List<String> non_args = options.nonOptionArguments();

    // Show help,
    if (non_args.size() != 2 || options.has("?")) {
      vars.put("$", "err");
      return ServerCommand.CONSOLE_MODE;
    }

    FileName pwd_fn = new FileName(vars.get("pwd"));
    FileName normal_fn = pwd_fn.resolve(new FileName(non_args.get(1)));

    String path_spec = normal_fn.toString();

    // Is this file already open by another editor process?
    EnvVarsImpl raw_vars = (EnvVarsImpl) vars;
    List<EnvVarsImpl> programs = raw_vars.allPrograms();
    for (EnvVarsImpl prg_vars : programs) {
      // Get the process name,
      String process_str = prg_vars.get("process");
      if (process_str != null && process_str.equals("SCommandEdit")) {
        // This process is an edit command,
        String editing_file = prg_vars.get("file");
        if (editing_file.equals(path_spec)) {
          // With the same file spec - so we need to switch to this,
          vars.put("$", "switch-" + prg_vars.getFrameName());
          return ServerCommand.CONSOLE_MODE;
        }
      }
    }

    // Put the process name of this,
    vars.put("process", "SCommandEdit");
    vars.put("file", path_spec);

    FileRepository fs = ctx.getFileRepositoryFor(normal_fn);
    if (fs == null || normal_fn.isDirectory()) {
      // Invalid repository,
      vars.put("$", "invalidfile");
      return ServerCommand.CONSOLE_MODE;
    }

    FileInfo finfo = fs.getFileInfo(normal_fn.getPathFile());

    // File doesn't exist,
    if (finfo == null) {
      // Check if the sub-dir exists (we are creating a new file here).
      FileInfo subdir_finfo = fs.getFileInfo(normal_fn.getPath().getPathFile());
      // Invalid file if the subdir doesn't exist,
      if (subdir_finfo == null) {
        vars.put("$", "invalidfile");
        return ServerCommand.CONSOLE_MODE;
      }
    }
    // File exists,
    else {
      // Make sure the file isn't too large,
      long file_size = finfo.getDataFile().size();
      if (file_size > (1 * 1024 * 1024)) {
        vars.put("$", "toolarge");
        return ServerCommand.CONSOLE_MODE;
      }
    }

    // Good to go,
    vars.put("$", "ok");
    return ServerCommand.WINDOW_MODE;

  }

  @Override
  public String process(ServerCommandContext sctx,
                        EnvironmentVariables vars, CommandWriter out) {

    PlatformContext ctx = sctx.getPlatformContext();

    // The qualified file name,
    String qual_file = vars.get("file");

    // Get the '$' var,
    String s = vars.get("$");
    if (s != null) {
      // Clear the init flag,
      vars.put("$", null);

      if (s.equals("err")) {
        out.println(" syntax: edit [file]", "info");
        out.println();
        JOptSimpleUtils.printHelpOn(EDIT_PARSER, out);
        return "STOP";
      }
      if (s.equals("invalidfile")) {
        out.println("edit: invalid file: " + qual_file, "error");
        return "STOP";
      }
      if (s.equals("toolarge")) {
        out.println("edit: file too large to edit: " + qual_file, "error");
        return "STOP";
      }
      if (s.startsWith("switch-")) {
        String frame_val = s.substring(7);
        out.switchToFrame(frame_val);
        return "STOP";
      }

      // If init was ok then we are in WINDOW_MODE,
      if (s.equals("ok")) {
        // Tell the client to start the editor,
        out.runScript("js/editor.js", "MckoiEditor", "");
      }

    }

    else {

      // Get the command line,
      String cline = vars.get("cline");
      // Clear the 'cline'
      vars.put("cline", "");

      // Handle save states,
      if (cline.startsWith("sv_")) {

        SaveState save = (SaveState) vars.getTemporaryObject();

        if (cline.startsWith("sv_init ")) {  // Init,
          if (save != null) {
            sendError(out, "Can't save because internal save state is invalid.");
            vars.setTemporaryObject(null);
          }
          else {
            CommandLine cl = new CommandLine(cline);
            String[] args = cl.getDefaultArgs();
            String mime_type = args[1];
            int expected_size = Integer.parseInt(args[2]);
            save = new SaveState(mime_type, expected_size);
            vars.setTemporaryObject(save);
          }
        }

        else if (cline.startsWith("sv_put ")) {  // Put,
          if (save == null) {
            sendError(out, "Invalid save state.");
          }
          else if (save.getCurrentSize() > MAX_FILE_SIZE) {
            sendError(out, "Can't save, max size limit reached.");
            vars.setTemporaryObject(null);
          }
          else {
            save.write(cline.substring(7));
          }
        }

        else if (cline.startsWith("sv_fput ")) {  // Final Put,
          if (save == null) {
            sendError(out, "Invalid save state.");
          }
          else if (save.getCurrentSize() > MAX_FILE_SIZE) {
            sendError(out, "Can't save, max size limit reached.");
            vars.setTemporaryObject(null);
          }
          else {
            save.write(cline.substring(8));
            // Reset the save state,
            vars.setTemporaryObject(null);

            String file_mime_type = save.mime_type;

            FileName normal_fn = new FileName(qual_file);
            FileRepository fs = ctx.getFileRepositoryFor(normal_fn);
            FileInfo finfo = fs.getFileInfo(normal_fn.getPathFile());

//            FileRepository filesystem = ctx.getFileRepository();
//            FileInfo finfo = filesystem.getFileInfo(qual_file);

            try {

              // Create the file if it doesn't exist
              if (finfo == null) {
                String path_file = normal_fn.getPathFile();
                fs.createFile(path_file, file_mime_type,
                              System.currentTimeMillis());
                finfo = fs.getFileInfo(path_file);
              }
              else {
                finfo.setLastModified(System.currentTimeMillis());
                finfo.setMimeType(file_mime_type);
              }
              DataFile dfile = finfo.getDataFile();
              OutputStream outs =
                           DataFileUtils.asSimpleDifferenceOutputStream(dfile);
              outs.write(save.bout.toByteArray());
              outs.flush();
              outs.close();

              // Commit,
              fs.commit();

              // Send save confirmation,
              out.sendGeneral("cs-");
              
            }
            catch (IOException e) {
              // Shouldn't be possible,
              sendError(out, "IO Error.");
            }
            catch (CommitFaultException ex) {
              sendError(out,
                        "Failed to save because concurrent modification.");
            }
            
          }
        }
        return "WAIT";
      }

      // Clear temporary state,
      vars.setTemporaryObject(null);

      // Initialization,
      if (cline.equals("init")) {
        
        FileName normal_fn = new FileName(qual_file);
        FileRepository fs = ctx.getFileRepositoryFor(normal_fn);
        FileInfo finfo = fs.getFileInfo(normal_fn.getPathFile());

        // Try and load editor configuration,
        FileRepository config_fs;
        if (!fs.getRepositoryId().equals(ctx.getAccountName())) {
          config_fs = ctx.getFileRepository();
        }
        else {
          config_fs = fs;
        }
        DataFile prefs_df = config_fs.getDataFile("/config/editor/prefs.conf");
        Properties prefs = EDITOR_DEFAULT_PREFS;
        if (prefs_df != null) {
          try {
            prefs = new Properties();
            prefs.load(DataFileUtils.asInputStream(prefs_df));
          }
          catch (IOException e) {
            prefs = EDITOR_DEFAULT_PREFS;
          }
        }

        String mime_type;

        if (finfo == null) {
          // No,
          // Deduce the mime type from the name,
          mime_type = "plain/text";
        }
        else {
          // Yes, exists,
          mime_type = finfo.getMimeType();
        }

        // Get the Code Mirror theme,
        String def_theme = prefs.getProperty("cm2theme", "dusk");

        out.sendGeneral("ini");             // Init
        out.sendGeneral("th-" + def_theme); // Set Default Theme,
        out.sendGeneral("st-" + qual_file); // Set title (file name)
        out.sendGeneral("sm-" + mime_type); // Set mime type of the file,
        out.sendGeneral("cc-");             // Clear content
        if (finfo != null) {
          DataFile dfile = finfo.getDataFile();
          sendFileContent(out, DataFileUtils.asInputStream(dfile), dfile.size());
        }
                                            // Add to content,
        out.sendGeneral("lc-");             // Load complete,
        out.sendGeneral("ena");             // Enable
        
        return "WAIT";
      }

      // Close,
      else if (cline.equals("close")) {
        return "STOP";
      }


    }

    return "WAIT";
  }

  /**
   * Send the file to the client.
   */
  private void sendFileContent(CommandWriter out, InputStream in, long size) {
    try {
      InputStreamReader r = new InputStreamReader(in, "UTF-8");
      // Send in 8k chuncks
      char[] buf = new char[(int) Math.min(8192, size)];
      while (true) {
        int rcount = r.read(buf, 0, buf.length);
        if (rcount == -1) {
          // Done,
          break;
        }

        out.sendGeneral("ac-" + new String(buf, 0, rcount));
      }
    }
    catch (IOException e) {
      // Send error
      out.sendGeneral("err" + e.getMessage());
    }
  }

  /**
   * Sends an error message to the client.
   */
  private void sendError(CommandWriter out, String error_msg) {
    out.sendGeneral("err" + error_msg);
  }

  // -----
  
  // Default preferences,
  private final static Properties EDITOR_DEFAULT_PREFS;
  static {
    EDITOR_DEFAULT_PREFS = new Properties();
  }
  
  // -----
  
  private static class SaveState {

    private final String mime_type;
    private final int expected_size;
    private final ByteArrayOutputStream bout;
    private Writer w;

    SaveState(String mime_type, int expected_size) {
      this.mime_type = mime_type;
      this.expected_size = expected_size;
      this.bout = new ByteArrayOutputStream(expected_size + 128);
    }

    int getCurrentSize() {
      return bout.size();
    }

    void write(String block) {
      try {
        if (w == null) {
          w = new OutputStreamWriter(bout, "UTF-8");
        }
        w.write(block);
        w.flush();
      }
      catch (IOException e) {
        // Shouldn't be possible,
        throw new RuntimeException(e);
      }
    }

  }
  
}
