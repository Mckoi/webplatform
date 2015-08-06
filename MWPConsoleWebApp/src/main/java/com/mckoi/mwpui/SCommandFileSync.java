/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mckoi.mwpui;

import com.mckoi.apihelper.JOptSimpleUtils;
import com.mckoi.lib.joptsimple.OptionException;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.lib.joptsimple.OptionSet;
import com.mckoi.mwpui.apihelper.TextUtils;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.util.DirectorySynchronizer;
import com.mckoi.odb.util.DirectorySynchronizerFeedback;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A console command that uses the MckoiDDB ODB file synchronizer class to
 * synchronize files across different locations.
 *
 * @author Tobias Downer
 */
public class SCommandFileSync extends DefaultSCommand {

  public SCommandFileSync(String reference) {
    super(reference);
  }

  @Override
  public String process(ServerCommandContext sctx,
                        EnvironmentVariables vars, CommandWriter out) {

    PlatformContext ctx = sctx.getPlatformContext();

    OptionParser parser = new OptionParser();
    parser.acceptsAll(Arrays.asList("?", "help"), "Show the help");
    parser.acceptsAll(Arrays.asList("delete"), "Delete the files in the destination");

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
      out.println("sync: " + e.getMessage(), "error");
      return "STOP";
    }

    // Need a source and destination,
    if (options.has("help") || args.length != 3) {
      showHelp(parser, out);
      // Stop after complete,
      return "STOP";
    }

    boolean delete_in_dest = options.has("delete");
    
    FileName pwd_fn = new FileName(vars.get("pwd"));
    
    String source_location = args[1];
    String dest_location = args[2];

    // Check the source and destination repositories exist,
    FileName source = pwd_fn.resolve(new FileName(source_location));
    FileName destination = pwd_fn.resolve(new FileName(dest_location));
    
    FileRepository src_fs = ctx.getFileRepositoryFor(source);
    FileRepository dst_fs = ctx.getFileRepositoryFor(destination);

    if (src_fs == null) {
      out.print("sync: source repository is not accessible: ", "error");
      out.println(source_location, "error");
      return "STOP";
    }
    if (dst_fs == null) {
      out.print("sync: destination repository is not accessible: ", "error");
      out.println(dest_location, "error");
      return "STOP";
    }

    out.print(source.toString());
    out.print(" -> ", "info");
    out.println(destination.toString());
    out.println();

    // The object notified of changes,
    FSyncDSFeedback fb = new FSyncDSFeedback(out);

    // Create the synchronizer,
    DirectorySynchronizer synchronizer =
                          DirectorySynchronizer.getMckoiToMckoiSynchronizer(
            fb,
            src_fs, source.getPathFile(),
            dst_fs, destination.getPathFile());

    synchronizer.setDeleteFilesFlag(delete_in_dest);

    try {
      long update_count = synchronizer.synchronize();

      // If there were updates made then commit on the destination,
      if (update_count > 0) {
        dst_fs.commit();
      }
      else {
        out.println("No updates.");
      }

      out.println();
      out.print("Finished processing ");
      out.print(fb.total_files_processed, "info");
      out.println(" files.");
      out.print("Total size: ");
      out.println(TextUtils.formatHumanDataSizeValue(
                                    fb.running_data_size), "info");

    }
    catch (CommitFaultException ex) {
      out.print("sync: commit failed: ", "error");
      out.println(ex.getMessage(), "error");
      out.printExtendedError(null, null, ex);
      return "STOP";
    }
    catch (IOException ex) {
      out.print("sync: IO Error: ", "error");
      out.println(ex.getMessage(), "error");
      out.printExtendedError(null, null, ex);
      return "STOP";
    }
    
    return "STOP";

  }

  /**
   * Displays help for this command.
   * 
   * @param parser
   * @param out 
   */
  private void showHelp(OptionParser parser, CommandWriter out) {
    out.println("sync [source directory] [destination directory]", "info");
    out.println();
    out.println(" --delete   Delete files/directories in the destination that don't", "info");
    out.println("            exist in the source.", "info");
    out.println(" --help     Show the help.", "info");
  }

  /**
   * A synchronizer feedback object used here.
   */
  private static class FSyncDSFeedback implements DirectorySynchronizerFeedback {

    int total_files_processed = 0;
    int total_skipped = 0;
    long running_data_size = 0;
    private final CommandWriter out;

    public FSyncDSFeedback(CommandWriter writer) {
      this.out = writer;
    }

    @Override
    public void reportFileSynchronized(
                          String sync_type, String file_name, long file_size) {
      ++total_files_processed;
      running_data_size += file_size;
      if (sync_type.equals(SKIPPED)) {
        ++total_skipped;
        return;
      }
      else if (sync_type.equals(WRITTEN)) {
        out.print("WRITE: ");
      }
      else if (sync_type.equals(TOUCHED)) {
        out.print("TOUCH: ");
      }
      else if (sync_type.equals(DELETED)) {
        out.print("DELETE: ");
      }
      if (file_size > 0) {
        String size_str = TextUtils.formatHumanDataSizeValue(file_size).trim();
        String pad = TextUtils.pad(Math.max(0, 10 - size_str.length()));
        out.print(" ");
        out.print(pad);
        out.print(size_str);
        out.print(" ");
      }
      else {
        out.print(TextUtils.pad(10 + 2));
      }
      out.print(file_name, "info");
      out.println();
    }

    @Override
    public void reportDirectoryRemoved(String directory_name) {
      out.print("RMDIR: ");
      out.println(directory_name, "info");
    }

  }

}
