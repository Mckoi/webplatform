
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
    var c = CommandLine.parse('<file(s) to delete>', cl_spec);
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
      out.println(c.getCommandName() + ": no target", "error");
      c.printHelp(out);
      return STOP;
    }

    // Match all the values and find a result set,
    var match_result;
    try{
      match_result =
        FileSystem.findMatchingFileList(ctx.getPlatformContext(),
                                        values, vars.pwd, true, false, true);
    }
    catch (e) {
      out.println(c.getCommandName() + ": " + e, "error");
      return STOP;
    }
    // The common file repository,
    var common_fs = match_result.common_fs;
    // The set of all files,
    var file_list = match_result.file_list;

    var it = file_list.iterator();
    // If nothing to delete,
    if (!it.hasNext()) {
      out.println(c.getCommandName() + ": no match", "error");
      return STOP;
    }

    // Delete all the files,
    var delete_count = FileSystem.deleteAllFiles(common_fs, file_list);

    // Commit the changes,
    try {
      common_fs.commit();
    }
    catch (e) {
      out.println(c.getCommandName() + ": commit failed", "error");
    }

    return STOP;

  }

})();
