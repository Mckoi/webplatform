
(function() {

  var CommandLine = require('mwp/commandline');
  var FileSystem = require('mwp/filesystem');
  var TextFormat = require('mwp/textformat');

  //
  // The initialization function.
  //
  exports.init = function(ctx, vars) {

    // Initial variables,
    vars['p'] = 0;
    vars['path_spec'] = null;

    return CONSOLE_MODE;
  }

  //
  // The command process function.
  //
  exports.process = function(ctx, vars, out) {

    // Screen width and height in fixed size characters,
    var SCREEN_WIDTH = 100;
    var SCREEN_HEIGHT = 25;

    // Get the parth spec variable,
    var path_spec = vars.path_spec;

    // The normalized file name,
    var normal_fn;

    // If no path spec then parse the command line,
    if (path_spec == null) {

      var cl_spec = [
        ['help', '?', '',
        'show help'],
      ];

      // Parse the spec,
      var c = CommandLine.parse('<file to view>', cl_spec);
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
      else if (values.length > 1) {
        out.println(c.getCommandName() + ": too many arguments", "error");
        return STOP;
      }

      // Resolve the path spec,
      var base_fn = new FileSystem.FileName(vars.pwd);
      var cl_pathfile = CommandLine.unescapeQuotes(values[0]);
      normal_fn = base_fn.resolve(new FileSystem.FileName(cl_pathfile));

      // If it's a directory reference,
      if (normal_fn.isDirectory()) {
        out.println(c.getCommandName() +
                    ": not a valid target: " + normal_fn, "error");
        return STOP;
      }

      // Set the target,
      vars.path_spec = normal_fn.toString();

    }
    // Not the initial command, check the command typed,
    else {
      var cline = vars.cline;
      // The quit command,
      if (cline.trim().slice(0, 1) == "q") {
        return STOP;
      }
      // The normalized file name,
      normal_fn = new FileSystem.FileName(vars.path_spec);
    }

    // Get the repository,
    var fs = ctx.getPlatformContext().getFileRepositoryFor(normal_fn);
    if (fs == null) {
      out.println(c.getCommandName() +
                  ": repository id not found: " + normal_fn.getRepositoryId(),
                  "error");
      return STOP;
    }

    // The file info,
    var fi = fs.getFileInfo(normal_fn.getPathFile());
    if (fi == null || !fi.isFile()) {
      out.println(c.getCommandName() + ": not found: " + normal_fn, "error");
      return STOP;
    }

    // Print the content of the file,
    var p = parseInt(vars.p);

    // HACK, use Java Long object here.
    var long_p = Packages.java.lang.Long.parseLong(p);

    // Move the cursor of the data file as appropriate,
    var dfile = fi.getDataFile();
    dfile.position(long_p);
    // Get a reader for the data file,
    var reader = FileSystem.getUTF8Reader(dfile);

    var line_str = '';
    var line_pos = 0;
    var end_reached = false;
    while (line_pos < SCREEN_HEIGHT) {
      var ch = reader.read();
      if (ch == -1) {
        // End of file,
        end_reached = true;
        break;
      }
      // Read characters and output a line
      var ch_str;

      ch_str = String.fromCharCode(ch);

      // Update p,
      p += TextFormat.utf8CodeCountIn(ch);

      if (line_str.length >= SCREEN_WIDTH || ch_str == '\n') {
        out.println(line_str);
        ++line_pos
        if (ch_str == '\n') {
          line_str = '';
        }
        else {
          line_str = ch_str;
        }
      }
      else {
        line_str += ch_str;
      }
    }

    // Are we at the end of the file?
    if (end_reached || reader.read() == -1) {
      if (line_str != '') {
        out.println(line_str);
      }
      return STOP;
    }

    // Otherwise go to a prompt state,
    vars.prompt = "-- More --";
    vars.path_spec = normal_fn.toString();
    vars.p = Packages.java.lang.Long.toString(p);

    return PROMPT;

  }

})();
