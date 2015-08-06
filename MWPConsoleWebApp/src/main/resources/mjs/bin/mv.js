
(function() {

  var CommandLine = require('mwp/commandline');
  var FileSystem = require('mwp/filesystem');

  //
  // The initialization function.
  //
  exports.init = function(ctx, vars) {
    return CONSOLE_MODE;
  }

  //
  // The command process function.
  //
  exports.process = function(ctx, vars, out) {

    // Inherit from the 'cp' command,
    var CopyCmd = require('./cp');

    var cl_spec = [
      ['', 'r', '',
      'recursively move all sub-directories and file contents from the source path'],
      ['help', '?', '',
      'show help'],
    ];

    // Parse the spec,
    var c = CommandLine.parse('<source file(s)> <destination directory>', cl_spec);
    var cline_ex;
    // Match the input command line against the spec,
    try {
      cline_ex = c.match(vars.cline);

      // Print help,
      if (cline_ex.options.help) {
        c.printHelp(out);
        return STOP;
      }

    }
    catch (e) {
      // Print help if an exception is thrown,
      out.println(e, "error");
      return STOP;
    }

    var options = cline_ex.options;
    var values = cline_ex.values;

    var recurse = options.r;

    // Check the arguments,
    if (values.length < 2) {
      out.println(c.getCommandName() + ": arguments missing", "error");
      c.printHelp(out);
      return STOP;
    }
    if (values.length > 2) {
      out.println(c.getCommandName() + ": too many arguments", "error");
      return STOP;
    }

    // Source and destination strings,
    var source_str = CommandLine.unescapeQuotes(values[0]);
    var dest_str = CommandLine.unescapeQuotes(values[1]);

    // The 'pwd' path.
    var pwd_fn = new FileSystem.FileName(vars.pwd);

    // The move instructions,
    var move_instr;
    try {
      move_instr = CopyCmd.calcMoveInstructions(
                      ctx.getPlatformContext(),
                      source_str, dest_str, pwd_fn, recurse);
    }
    catch (e) {
      out.println(c.getCommandName() + ": " + e, "error");
      return STOP;
    }

    // ----- PERFORM THE COPY OPERATION -----

    // Some values from the move_instr object,
    var to_copy_arr = move_instr.to_copy_arr;
    var src_map = move_instr.src_map;
    var src_fs = move_instr.src_fs;
    var dst_fs = move_instr.dst_fs;

    // The size of the instruction set,
    var sz = to_copy_arr.length;

    // Check no files are being copied over the top of source files being
    // copied,
    if (src_map && move_instr.same_repository) {
      for (var n = 0; n < sz; n += 2) {
        var dest_fn = to_copy_arr[n + 1];
        // If a destination overwrites a source,
        if (src_map[dest_fn.toString()] == true) {
          out.println(c.getCommandName() +
                          ": recursive target on: " + dest_fn, "error");
          return STOP;
        }
      }
    }

  //  //  -- DEBUG --
  //  out.println("MOVE JOB...", "debug");
  //  for (var p = 0; p < sz; p += 2) {
  //    var cmd_or_src = to_copy_arr[p];
  //    out.print(cmd_or_src, "debug");
  //    out.print(" => ");
  //    out.print(to_copy_arr[p + 1], "debug");
  //    out.println();
  //  }



    // Perform the move,
    for (var n = 0; n < sz; n += 2) {
      var s_fn = to_copy_arr[n];
      var d_fn = to_copy_arr[n + 1];
      if (s_fn == ".MKDIR") {
        // Make the directory,
        dst_fs.makeDirectory(d_fn.getPathFile());
      }
      else if (s_fn == ".RMDIR") {
        // Delete the directory,
        try {
          src_fs.removeDirectory(d_fn.getPathFile());
        }
        catch (e) {
          // Ignore any exceptions here (failing to delete directory).
        }
      }
      else {
        // Move
        var dest_pathfile = d_fn.getPathFile();
        var src_pathfile = s_fn.getPathFile();
        var sfi = src_fs.getFileInfo(src_pathfile);
        var dfi = dst_fs.getFileInfo(dest_pathfile);
        // Create destination file if it doesn't exist,'
        if (dfi == null) {
          dst_fs.createFile(dest_pathfile,
                            sfi.getMimeType(), sfi.getLastModified());
          dfi = dst_fs.getFileInfo(dest_pathfile);
        }
        else {
          // Fail if we are overwriting a file,
          out.println(c.getCommandName() +
                ": failed because destination target exists: " + d_fn, "error");
          return STOP;
        }
        // Copy the file,
        dfi.getDataFile().replicateFrom(sfi.getDataFile());
        // Delete the source file,
        src_fs.deleteFile(src_pathfile);
      }
    }

    // Commit the source and destination file repository,
    // Note that the commit order here is none destructive. We commit the
    // destination first which will contain the additions, and the source
    // second which will contain the removals. If the destination fails
    // to commit then nothing changes. If the source fails to commit, the
    // operation will effectively become a none destructive 'copy'
    // operation.

    var cur_repos_id;
    try {
      cur_repos_id = dst_fs.getRepositoryId();
      dst_fs.commit();
      if (!move_instr.same_repository) {
        // Only commit this if it's not the same repository,
        cur_repos_id = src_fs.getRepositoryId();
        src_fs.commit();
      }
    }
    catch (e) {
      out.println(c.getCommandName() +
                  ": commit failed on repository id: " + cur_repos_id, "error");
      out.println(e, "error");
    }

    return STOP;

  }

})();
