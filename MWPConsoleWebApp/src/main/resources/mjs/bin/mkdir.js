
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
      ['help', '?', '',
      'show help'],
    ];

    // Parse the spec,
    var c = CommandLine.parse('<directory to create>', cl_spec);
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

    // Check the arguments,
    if (values.length == 0) {
      out.println(c.getCommandName() + ": arguments missing", "error");
      c.printHelp(out);
      return STOP;
    }

    // This produces an array of normalized directory locations that are good
    // to make.
    var ok_to_make = [];

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
      if (fi != null) {
        throw "already exists: " + as_file;
      }
      fi = this_fs.getFileInfo(as_path.getPathFile());
      if (fi != null) {
        throw "already exists: " + as_path;
      }

      // Don't add the same directory location twice,
      var path_str = as_path.getPathFile();
      var contains = false;
      for (var i = 0, sz = ok_to_make.length; i < sz; ++i) {
        if (ok_to_make[i].getPathFile() == path_str) {
          contains = true;
          break;
        }
      }
      if (!contains) {
        ok_to_make.push(as_path);
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

    // Make directories,
    if (ok_to_make.length == 0) {
      out.println(c.getCommandName() + ": no targets", "error");
      return STOP;
    }

    for (var i = 0, sz = ok_to_make.length; i < sz; ++i) {
      var nf = ok_to_make[i];
      var fs = fs_map[nf.getRepositoryId()];
      fs.makeDirectory(nf.getPathFile());
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
