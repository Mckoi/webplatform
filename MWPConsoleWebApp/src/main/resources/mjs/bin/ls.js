
(function() {

  var CommandLine = require('mwp/commandline');
  var FileSystem = require('mwp/filesystem');
  var TextFormat = require('mwp/textformat');

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
      ['order', 'o', ':(alpha|date)',
      'order output alphabetically or by date (default: alpha)'],
      ['', 'd', '',
      'show only directories'],
      ['', 'f', '',
      'show only files'],
      ['', 's', '',
      'display names only'],
      ['mime', 'm', '',
      'show file mime types'],
      ['help', '?', '',
      'show help'],
    ];

    // Parse the spec,
    var c = CommandLine.parse('<files or dirs or wildcard>', cl_spec);
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

    var show_dirs = true, show_files = true;
    // Only show dirs,
    if (options.d) {
      show_files = false;
    }
    // Only show files,
    if (options.f) {
      show_dirs = false;
    }
    // Nothing to show,
    if (!show_files && !show_dirs) {
      out.println(c.getCommandName() + ": invalid options", "error");
      return STOP;
    }

    var simple_output = options.s;
    var show_mime = options.m;

    // The location to list,
    var path_str;

    var values = cline_ex.values;
    // If no values, use current directory,
    path_str =
          ((values.length == 0) ? '.' : CommandLine.unescapeQuotes(values[0]));

    // The path as a file object,
    var file_ob = new FileSystem.FileName(path_str);
    // The current 'pwd' as a file object,
    var pwd_ob = new FileSystem.FileName(vars.pwd).asDirectory();
    // Resolve the file ob against the pwd,
    var normal_file = pwd_ob.resolve(file_ob);

    // Check the file name is valid
    if (!normal_file.isValid()) {
      // No, report error,
      out.println(c.getCommandName() + ": invalid file name: " +
                  normal_file, "error");
      return STOP;
    }

    var fs = ctx.getPlatformContext().getFileRepositoryFor(normal_file);
    if (fs == null) {
      // No file system for this file,
      var repository_id = normal_file.getRepositoryId();
      if (repository_id == null) {
        out.println(c.getCommandName() + ": path must include repository id: " +
                    normal_file, "error");
      }
      else {
        out.println(c.getCommandName() + ": invalid repository id: " +
                    repository_id, "error");
      }
      return STOP;
    }

    // Get the file name part,
    var file_str = normal_file.getFile().toString();
    var fi = null;
    var wild_cards = null;
    // If it contains wild cards,
    if (file_str.indexOf('*') != -1 || file_str.indexOf('?') != -1) {
      fi = fs.getFileInfo(normal_file.getPath().getPathFile());
      wild_cards = file_str;
    }
    else {
      // Doesn't contain wild cards, so see if it resolves to a file or dir,
      if (!normal_file.isDirectory()) {
        // Maybe a file or directory, lets check,
        fi = fs.getFileInfo(normal_file.getPathFile());
        if (fi == null) {
          // No, so check if it's a directory,
          var dir_normal_file = normal_file.asDirectory();
          fi = fs.getFileInfo(dir_normal_file.getPathFile());
          if (fi != null) {
            // Yes, directory!
            normal_file = dir_normal_file;
          }
        }
      }
      // It's definitely a directory,
      else {
        fi = fs.getFileInfo(normal_file.getPathFile());
      }
    }

    // File not found...
    if (!fi) {
      out.println(c.getCommandName() + ": not found: " + normal_file, "error");
      return STOP;
    }

    // The file_list object,
    var dir_list;
    var file_list;

    // if no wildcards,
    if (!wild_cards) {
      // If the FileInfo is a directory,
      if (fi.isDirectory()) {
        if (show_dirs) dir_list = FileSystem.getDirectoryList(fs, normal_file);
        if (show_files) file_list = FileSystem.getFileList(fs, normal_file);
      }
      // Otherwise a single file,
      else {
        if (show_dirs) dir_list = FileSystem.asFileInfoList( [] );
        if (show_files) file_list = FileSystem.asFileInfoList( [fi] );
  //    Sort is not necessary because the list will only have 1 item,
  //      FileSystem.sortFileInfoListBy(file_list, 'name');
      }
    }
    // Wildcards,
    else {
      // Get the directory list,
      var path_of = normal_file.getPath();
      if (show_dirs) dir_list = FileSystem.getDirectoryList(fs, path_of);
      if (show_files) file_list = FileSystem.getFileList(fs, path_of);
      // Filter lists,
      if (show_dirs)
        dir_list = FileSystem.filterByWildCardExpr(dir_list, wild_cards);
      if (show_files)
        file_list = FileSystem.filterByWildCardExpr(file_list, wild_cards);
    }

    // Order spec,
    var order_spec = options.o;
    var sort_by;
    if (order_spec) {
      var sw = function(str1, str2) {
        return str1.slice(0, str2.length) == str2;
      }

      if (sw('alpha', order_spec)) {
        // Default is alpha,
      }
      else if (sw('date', order_spec)) {
        sort_by = 'timestamp';
      }
      else if (sw('mime', order_spec)) {
        sort_by = 'mime';
      }
      else if (sw('size', order_spec)) {
        sort_by = 'size';
      }
      else {
        out.println(c.getCommandName() +
                    ": invalid order spec: " + order_spec, "error");
        return STOP;
      }
    }

    // If we change sort order,
    if (sort_by) {
      if (show_dirs)
        dir_list = FileSystem.sortFileInfoListBy(dir_list, sort_by);
      if (show_files)
        file_list = FileSystem.sortFileInfoListBy(file_list, sort_by);
    }

    // ---- DISPLAY ----

    if (simple_output) {

      if (dir_list) {
        // Sub-directories,
        it = dir_list.iterator();
        while (it.hasNext()) {
          out.println(it.next().getItemName(), "lscmd_dir");
        }
      }
      if (file_list) {
        // Files,
        it = file_list.iterator();
        while (it.hasNext()) {
          var file_item = it.next();
          out.print(file_item.getItemName(), "lscmd_file");
          if (show_mime) out.print(" <" + file_item.getMimeType() + ">");
          out.println();
        }
      }

    }
    else {
      var printFileInfo = function(file, out) {
        var name = String(file.getItemName());

        var file_size = file.getDataFile().size();
        var size_ob = TextFormat.formatHumanDataSizeValue(file_size);

        // Format the date string,
        var str_ob = TextFormat.formatDateTimeString(file.getLastModified());

        out.print(str_ob);
        out.print(" " + TextFormat.rightAlign(size_ob, 12) + " ");
        out.print(name, "lscmd_file");
        if (show_mime) {
          var pad2_amount = Math.max(1, 24 - name.length);
          out.print(TextFormat.pad(pad2_amount));
          out.print("<" + file.getMimeType() + ">");
        }
        out.println();

        return file_size;
      };

      var dir_count = 0;
      var file_count = 0;

      out.println(" List: " + normal_file);
      out.println();

      // Count the size of the files,
      var file_size_total = 0;
      var it;

      if (dir_list) {
        // Sub-directories,
        it = dir_list.iterator();
        while (it.hasNext()) {
          var dir = it.next();
          var name = dir.getItemName();
          var str_ob =
                    TextFormat.formatDateTimeString(dir.getLastModified());
          out.print(str_ob);
          out.print("              ");
          out.print(name, "lscmd_dir");
          out.println();
          ++dir_count;
        }
      }
      if (file_list) {
        // Files,
        it = file_list.iterator();
        while (it.hasNext()) {
          var file = it.next();
          var file_size = printFileInfo(file, out);
          file_size_total += file_size;
          ++file_count;
        }
      }

      if (file_count == 0 && dir_count == 0) {
        out.println("Empty.");
      }
      else {
        var bytes_display = "";
        if (file_size_total > 0) {
          bytes_display = "  " + file_size_total + " bytes";
        }
        out.println();
        out.println(" " + file_count + " File(s) "
                        + dir_count + " Dir(s) "
                        + bytes_display);
      }

    }

    return STOP;

  }

})();
