
/**
 * Simple upload files UI
 */ 

(function() {

  var CL = require('mwp/joptsimple');
  var FileSystem = require('mwp/filesystem');

  /**
   * The initialization function.
   */
  exports.init = function(ctx, vars) {
    return CONSOLE_MODE;
  }

  /**
   * The command process function.
   */
  exports.process = function(ctx, vars, out) {

    var parser = new CL.OptionParser();
    parser.acceptsAll( ['help', '?'],
                      'Show this help');

    // Parse the command line,
    var options;
    try {
      options = parser.parse( vars.cline );
    }
    catch (e) {
      out.println("upload: " + e, "error");
      return STOP;
    }

    // If has the 'help' option,
    if (options.has('help')) {
      out.println(" upload [destination path]", "info");
      out.println();
      parser.printHelpOn(out);
      return STOP;
    }

    var non_args = options.nonOptionArguments();

    var target = ".";
    if (non_args.length == 2) {
      target = non_args[1];
    }
    else if (non_args.length > 2) {
      out.println(non_args[0] + ": too many arguments", "error");
      return STOP;
    }

    var pwd_ob = new FileSystem.FileName(vars.pwd).asDirectory();
    // The path as a file object,
    var file_ob = new FileSystem.FileName(target);
    // Resolve the file ob against the pwd and make sure it's a directory
    // reference,
    var normal_dir = pwd_ob.resolve(file_ob).asDirectory();

    // Check the file name is valid
    if (!normal_dir.isValid()) {
      // No, report error,
      out.println(non_args[0] + ": invalid name: " + normal_dir, "error");
      return STOP;
    }

    var plat_ctx = ctx.getPlatformContext();

    var fs = plat_ctx.getFileRepositoryFor(normal_dir);
    var qual_loc;
    if (fs != null) {
      var finfo = fs.getFileInfo(normal_dir.getPathFile());
      if (finfo != null) {
        qual_loc = normal_dir.toString();
      }
    }

    if (qual_loc == null) {
      // Not a directory,
      out.println(non_args[0] + ": not a valid location", "error");
      return STOP;
    }

    // Run client side script for upload ui,
    out.runScript("js/cmdlineapps.js", "MWPUpload", qual_loc);

    return STOP;

  }

})();
