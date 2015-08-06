
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

    var cl_spec = [
      ['', 'r', '',
      'recursively remove sub-directories and any files contained within (use with care!)'],
      ['help', '?', '',
      'show help'],
    ];

    // Parse the spec,
    var c = CommandLine.parse('<directory to remove>', cl_spec);
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
    if (values.length == 0) {
      out.println(c.getCommandName() + ": arguments missing", "error");
      c.printHelp(out);
      return STOP;
    }

    // This produces an array of normalized directory locations that are good
    // to remove.
    var to_remove_arr = [];
    var to_remove_count = 0;

    var process_fun = function(this_fs, normalized_loc) {

      // Make sure the name doesn't contain any strange characters,
      var str = '' + normalized_loc.toString();
      if (str.indexOf('?') != -1 || str.indexOf('*') != -1) {
        throw "invalid characters in directory: " + normalized_loc;
      }

      // Does the file exist?
      var as_file = normalized_loc.asFile();
      var as_path = normalized_loc.asDirectory();
      var fi = this_fs.getFileInfo(as_file.getPathFile());
      if (fi != null && fi.isFile()) {
        throw "target is a file: " + as_file;
      }
      // Check if it's a directory,
      if (fi == null) {
        fi = this_fs.getFileInfo(as_path.getPathFile());
        if (fi == null) {
          throw "not found: " + as_path;
        }
      }

      // Check the directory is empty,
      var abs_pathfile = as_path.getPathFile().toString()
      var dir_contents = this_fs.getDirectoryFileInfoList(abs_pathfile);

      // If not empty,
      if (!dir_contents.isEmpty()) {
        // Report error if we aren't recursing,
        if (!recurse) {
          throw "directory is not empty: " + as_path;
        }
      }

      // Add the FileInfo object to the map,
      if (recurse) {
        // Otherwise, recurse on sub-directories first,
        var recurseAdd = function(cur_path) {
          var cur_path_str = cur_path.getPathFile();
          var dir_list = this_fs.getSubDirectoryList(cur_path_str);
          var it = dir_list.iterator();
          while (it.hasNext()) {
            var fi = it.next();
            var subdir_fn = new FileSystem.FileName(fi.getItemName());
            recurseAdd(cur_path.resolve(subdir_fn));
          }
          // Add this,
          to_remove_arr.push(cur_path.toString());
          ++to_remove_count;
        }
        recurseAdd(as_path);
      }
      else {
        to_remove_arr.push(as_path.toString());
        ++to_remove_count;
      }

    };

    var fs_map;
    try {
      fs_map = FileSystem.processFileLocations(ctx.getPlatformContext(),
                                            values, vars.pwd, true, process_fun);
    }
    catch (e) {
      out.println(c.getCommandName() + ": " + e, "error");
      return STOP;
    }

    // For each directory name in the remove map,
    for (var i = 0, sz = to_remove_arr.length; i < sz; ++i) {
      var nf = new FileSystem.FileName(to_remove_arr[i]);
      var fs = fs_map[nf.getRepositoryId()];

      var abs_path_str = nf.getPathFile();
      // If we are on recurse,
      if (recurse) {
        // Delete all the files in the directory before deleting the directory,
        var file_list = fs.getFileList(abs_path_str);
        FileSystem.deleteAllFiles(fs, file_list);
      }
      // Delete the directory,
      fs.removeDirectory(abs_path_str);
    }

    // Commit the changes,
    for (var repository_id in fs_map) {
      try {
        fs_map[repository_id].commit();
      }
      catch (e) {
        out.println(c.getCommandName() +
                ": commit failed on repository id: " + repository_id, "error");
      }
    }

    return STOP;

  }

})();
