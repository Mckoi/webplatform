
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
    var c = CommandLine.parse('<directory>', cl_spec);
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

    var pwd_ob = new FileSystem.FileName(vars.pwd).asDirectory();

    // Print the current pwd,
    if (values.length == 0) {
      out.println(pwd_ob);
      return STOP;
    }
    else if (values.length != 1) {
      // No location,
      out.println(c.getCommandName() + ": too many arguments", "error");
      return STOP;
    }

    // Resolve the path given,
    var path_str = CommandLine.unescapeQuotes(values[0]);

    // The path as a file object,
    var file_ob = new FileSystem.FileName(path_str);
    // Resolve the file ob against the pwd and make sure it's a directory
    // reference,
    var normal_dir = pwd_ob.resolve(file_ob).asDirectory();

    // Check the file name is valid
    if (!normal_dir.isValid()) {
      // No, report error,
      out.println(c.getCommandName() + ": invalid name: " +
                  normal_dir, "error");
      return STOP;
    }

    var repository_id = normal_dir.getRepositoryId();
    // If there's a repository specified,
    if (repository_id != null) {

      // If the context doesn't return a file repository then the repository id
      // is not found (either invalid, or inaccessible).
      var fs = ctx.getPlatformContext().getFileRepositoryFor(normal_dir);
      if (!fs) {
        out.println(c.getCommandName() + ": repository id not found: " +
                    repository_id, "error");
        return STOP;
      }

      // The file info,
      var fi = fs.getFileInfo(normal_dir.getPathFile());
      // If it doesn't exist,
      if (fi == null) {
        out.println(c.getCommandName() + ": directory not found: " +
                    normal_dir, "error");
        return STOP;
      }

      // Set the pwd variable,
      vars.pwd = normal_dir.asFile().toString();

    }
    // No repository specified, so set pwd to root,
    else {

      // Set the pwd variable,
      vars.pwd = "/";
      // Stop and export 'pwd'
      return STOP(['pwd']);

    }

    // Stop and export 'pwd'
    return STOP(['pwd']);

  }

})();
