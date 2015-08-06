
(function() {

  var CommandLine = require('mwp/commandline');
  var FileSystem = require('mwp/filesystem');
  var TextUtils = require('mwp/textformat');

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
    var c = CommandLine.parse('<path>', cl_spec);
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

    // If no values, use current directory,
    var path_str =
          ((values.length == 0) ? '.' : CommandLine.unescapeQuotes(values[0]));

    var normal_fn = new FileSystem.FileName(vars.pwd).resolve(
                                              new FileSystem.FileName(path_str));

    // Get the file repository for this file name,
    var fs = ctx.getPlatformContext().getFileRepositoryFor(normal_fn);
    if (fs == null) {
      out.println(c.getCommandName() + ": repository not found: " + normal_fn, "error");
      return STOP;
    }

    // Does the file exist, or can it be cast?
    var fi = fs.getFileInfo(normal_fn.getPathFile());
    var not_found = false;
    if (fi == null) {
      if (normal_fn.isDirectory()) not_found = true;
      else {
        // Cast to directory and try,
        normal_fn = normal_fn.asDirectory();
        fi = fs.getFileInfo(normal_fn.getPathFile());
        if (fi == null) not_found = true;
      }
    }
    else {
      if (!fi.isDirectory()) not_found = true;
    }

    if (not_found) {
      out.println(c.getCommandName() + ": directory not found: " + normal_fn, "error");
      return STOP;
    }

    out.print("Tree of: ");
    out.println(normal_fn.toString(), "info");
    out.println();

    var dir_count = 0;

    var recursePrint = function(level, dir_fn) {

      ++dir_count;
      level += 2;

      var file_list = FileSystem.getDirectoryList(fs, dir_fn);
      var it = file_list.iterator();
      while (it.hasNext()) {
        var dfi = it.next();
        var dfi_item_name = dfi.getItemName();

        // Work out the combined size of all the files in this directory,
        var subdir_fn = dir_fn.resolve(new FileSystem.FileName(dfi_item_name));
        var sub_file_list = FileSystem.getFileList(fs, subdir_fn);
        // The size of the content of this directory,
        var dir_byte_count = 0;
        var it2 = sub_file_list.iterator();
        while (it2.hasNext()) {
          var sdfi = it2.next();
          dir_byte_count += sdfi.getDataFile().size();
        }

        var sz_str;
        if (dir_byte_count > 0) {
          sz_str = "" + TextUtils.formatHumanDataSizeValue(dir_byte_count);
          out.print(sz_str, "info");
        }
        else {
          sz_str = "";
        }

        out.print(TextUtils.pad(Math.max(1, (level + 8) - sz_str.length)));
        out.println(dfi_item_name);
        out.flush();

        recursePrint(level, subdir_fn);
      }
    };

    out.println(normal_fn.toString());
    recursePrint(0, normal_fn);
    out.println();
    out.print("Found ");
    // Sigh, silly hack here to force integer display of dir_count,
    out.print("" + dir_count, "info");
    out.println(" directories.");

    return STOP;

  }

})();
