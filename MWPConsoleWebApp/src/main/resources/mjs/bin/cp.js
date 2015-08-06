
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
  // File to a destination file
  //
  function fileToFile(src_fs, source_fn, dst_fs, destination_fn) {

    // If source is a directory then fail,
    if (source_fn.isDirectory()) {
      throw "source is a directory and destination does not exist (or is a file)";
    }

    // Does the source exist?
    var src_fi = src_fs.getFileInfo(source_fn.getPathFile());
    if (src_fi == null) {
      throw "source target not found: " + source_fn;
    }

    // Check the path we are copying to exists,
    var dst_base = destination_fn.getPath();
    var dst_base_fi = dst_fs.getFileInfo(dst_base.getPathFile());
    if (dst_base_fi == null) {
      throw "destination path not found: " + dst_base;
    }

    // Should be good to copy the file,

    // The instructions of files to copy and destination directories to make,
    var to_copy_arr = [];
    to_copy_arr.push(source_fn);
    to_copy_arr.push(destination_fn);

    // Return the move instructions,
    return {
      to_copy_arr: to_copy_arr
    };

  }

  //
  // Files (possibly wildcards) to a destination directory.
  //
  function filesToDir(src_fs, source_fn, dst_fs, destination_fn, recurse) {

  //  var same_repository =
  //                  src_fs.getRepositoryId().equals(dst_fs.getRepositoryId());

    // The source and destination bases,
    var src_base = source_fn.getPath();
    var dst_base = destination_fn.getPath();

    // If source is a directory reference, then set src_base to the parent
    // directory.
    if (source_fn.isDirectory()) {
      src_base = src_base.asFile().getPath();
    }

    // Check the source and destination base are not the same,
    if (src_base.equals(dst_base)) {
      throw "source and destination targets are the same";
    }

    // Does the destination exist?
    var dest_dir_fi = dst_fs.getFileInfo(destination_fn.getPathFile());
    if (dest_dir_fi == null) {
      throw "destination target does not exist: " + destination_fn;
    }

    // Discover the source files,
    var src_list =
            FileSystem.mergeMatchingFilesAt(src_fs, source_fn, true, true);

    // The instructions of files to copy and destination directories to make,
    var to_copy_arr = [];
    // The source file name map,
    var src_map = {};
    // The number of file system mutations,
    var fs_changes = 0;

    // The Iterator,
    var it = src_list.iterator();

    while (it.hasNext()) {
      var fi = it.next();
      if (fi.isFile()) {
        var src_fn = new FileSystem.FileName(fi.getItemName());
        to_copy_arr.push(src_base.resolve(src_fn));
        to_copy_arr.push(dst_base.resolve(src_fn));
        ++fs_changes;
      }
      // If we recurse and it's a directory,
      if (recurse && fi.isDirectory()) {

        var copyDirRecurse = function(src_path_str) {
          var src_path_fn = new FileSystem.FileName(src_path_str);
          var src_dir_fn = src_base.resolve(src_path_fn);
          var dst_dir_fn = dst_base.resolve(src_path_fn);

          var src_dir_list =
                      src_fs.getDirectoryFileInfoList(src_dir_fn.getPathFile());
          var it = src_dir_list.iterator();
          while (it.hasNext()) {
            var fi = it.next();
            var fi_item_name = new FileSystem.FileName(fi.getItemName());
            if (fi.isFile()) {
              var src_file_name = src_dir_fn.resolve(fi_item_name);
              src_map[src_file_name] = true;
              to_copy_arr.push(src_file_name);
              to_copy_arr.push(dst_dir_fn.resolve(fi_item_name));
              ++fs_changes;
            }
            else if (fi.isDirectory()) {
              var src_subdir_fn = src_dir_fn.resolve(
                              new FileSystem.FileName(fi_item_name.toString()));
              to_copy_arr.push(".MKDIR");
              to_copy_arr.push(dst_dir_fn.resolve(fi_item_name));
              ++fs_changes;
              copyDirRecurse(src_path_str + fi_item_name.toString());
              to_copy_arr.push(".RMDIR");
              to_copy_arr.push(src_subdir_fn);
            }
          }
        };

        to_copy_arr.push(".MKDIR");
        to_copy_arr.push(
                  dst_base.resolve(new FileSystem.FileName(fi.getItemName())));
        ++fs_changes;
        var subdir = fi.getItemName();
        copyDirRecurse(subdir);
        to_copy_arr.push(".RMDIR");
        to_copy_arr.push(src_base.resolve(new FileSystem.FileName(subdir)));

      }
    }

    // If nothing was changed,
    if (!fs_changes) {
      throw "no source match";
    }

    // Return the move instructions,
    return {
      src_map: src_map,
      to_copy_arr: to_copy_arr
    };

  }

  //
  // Calculate instructions for a moving operation.
  //
  exports.calcMoveInstructions = function(
                                  ctx, source_str, dest_str, pwd_fn, recurse) {

    // Resolve the source and destination names,
    var source_fn = pwd_fn.resolve(new FileSystem.FileName(source_str));
    var destination_fn = pwd_fn.resolve(new FileSystem.FileName(dest_str));

    // Destination MUST be an existing sub-directory,
    var dst_repository_id = destination_fn.getRepositoryId();
    var src_repository_id = source_fn.getRepositoryId();

    // True if copying within the same repository,
    var same_repository = (src_repository_id == dst_repository_id);

    // Validate the source and destination repositories,
    var checkRepos = function(id, fs) {
      if (id == null)
        throw "repository id not specified";
      if (fs == null)
        throw "repository id not found: " + id;
    }

    var src_fs, dst_fs;

    if (same_repository) {
      src_fs = dst_fs = ctx.getFileRepository(src_repository_id);
    }
    else {
      src_fs = ctx.getFileRepository(src_repository_id);
      dst_fs = ctx.getFileRepository(dst_repository_id);
      checkRepos(dst_repository_id, dst_fs);
    }
    checkRepos(src_repository_id, src_fs);

    // Work out if this is a file to file copy or file(s) to directory copy,

    var is_file2file = true;
    // Destination is definitely a directory reference (it ends with '/')
    if (destination_fn.isDirectory()) {
      is_file2file = false;
    }
    else {
      // Cast to a directory and see if it exists,
      var dst_as_dir_fn = destination_fn.asDirectory();
      // Otherwise query the fs and determine if we cast to a directory or not,
      var dirtest_fi = dst_fs.getFileInfo(dst_as_dir_fn.getPathFile());
      if (dirtest_fi != null && dirtest_fi.isDirectory()) {
        // Ok, copying to a directory,
        is_file2file = false;
        destination_fn = dst_as_dir_fn;
      }
    }

    // Work out the move instructions,
    var move_instructions;
    if (is_file2file) {
      move_instructions =
              fileToFile(src_fs, source_fn, dst_fs, destination_fn);
    }
    else {
      move_instructions =
              filesToDir(src_fs, source_fn, dst_fs, destination_fn, recurse);
    }

    // Set the source and destination filesystems in the returned map,
    move_instructions.dst_fs = dst_fs;
    move_instructions.src_fs = src_fs;
    move_instructions.same_repository = same_repository;

    return move_instructions;

  }

  //
  // The command process function.
  //
  exports.process = function(ctx, vars, out) {

    var cl_spec = [
      ['', 'r', '',
      'recursively copy all sub-directories and file contents from the source path'],
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
      move_instr = exports.calcMoveInstructions(
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
    var n;

    // Check no files are being copied over the top of source files being
    // copied,
    if (src_map && move_instr.same_repository) {
      for (n = 0; n < sz; n += 2) {
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
  //  out.println("COPY JOB...", "debug");
  //  for (var p = 0; p < sz; p += 2) {
  //    out.print(to_copy_arr[p], "debug");
  //    out.print(" => ");
  //    out.print(to_copy_arr[p + 1], "debug");
  //    out.println();
  //  }

    // Perform the copy,
    for (n = 0; n < sz; n += 2) {
      var s_fn = to_copy_arr[n];
      var d_fn = to_copy_arr[n + 1];
      if (s_fn == ".MKDIR") {
        // Make the directory,
        dst_fs.makeDirectory(d_fn.getPathFile());
      }
      else if (s_fn == ".RMDIR") {
        // NOOP
      }
      else {
        // Copy
        var dest_pathfile = d_fn.getPathFile();
        var sfi = src_fs.getFileInfo(s_fn.getPathFile());
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
      }
    }

    // Commit the destination file repository,
    try {
      dst_fs.commit();
    }
    catch (e) {
      out.println(c.getCommandName() + ": commit failed", "error");
      out.println(e, "error");
    }

    return STOP;

  }

})();
