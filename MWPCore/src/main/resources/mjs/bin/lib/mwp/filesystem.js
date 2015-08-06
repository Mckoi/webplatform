//
// The MWP JavaScript filesystem API
//

(function() {

  // Java access
  var JavaFileName      = Packages.com.mckoi.odb.util.FileName;
  var FileUtils         = Packages.com.mckoi.apihelper.FileUtils;
  var ArrayList         = Packages.java.util.ArrayList;
  var BufferedReader    = Packages.java.io.BufferedReader;
  var InputStreamReader = Packages.java.io.InputStreamReader;
  var DataFileUtils     = Packages.com.mckoi.data.DataFileUtils;


  exports.FileName = JavaFileName;

  //
  // Returns the UTF8 reader for the given DataFile. Returns null if the given
  // data_file is undefined or null.
  //
  exports.getUTF8Reader = function(data_file) {
    if (!data_file) return null;
    return new BufferedReader(new InputStreamReader(
                            DataFileUtils.asInputStream(data_file), "UTF-8" ));
  }

  //
  // Returns a sorted list of FileInfo objects representing all the
  // subdirectories found in the given FileName directory. Returns only the
  // files in the directory.
  //
  exports.getFileList = function(fs, dir_file_name) {
    return FileUtils.getFileList(fs, dir_file_name);
  }

  //
  // Returns a sorted list of FileInfo objects representing all the
  // subdirectories found in the given directory.
  //
  exports.getDirectoryList = function(fs, dir_file_name) {
    return FileUtils.getDirectoryList(fs, dir_file_name);
  }

  //
  // Given array of FileInfo objects, returns as a list appropriate for use in
  // the filter and sort methods in this module.
  //
  exports.asFileInfoList = function(fileinfo_arr) {
    var sz = fileinfo_arr.length;
    var list = new ArrayList(sz);
    for (var i = 0; i < sz; ++i) {
      list.add(fileinfo_arr[i]);
    }
    return list;
  }

  //
  // Filters a FileInfo list that is sorted lexicographically, by a wild card
  // specification. A wild card specification is a string where the '*'
  // character can be substituted for any number of characters, and the '?'
  // character can be substituted for any one character. For example,
  // 'to?.*js'. Returns a sorted Collection of FileInfo objects.
  // 
  exports.filterByWildCardExpr = function(list, wildcard_spec) {
    return FileUtils.filterByWildCardExpr(list, wildcard_spec);
  }

  //
  // Sorts a FileInfo list by either the name, timestamp or mime of the file
  // object. 'name' - the name of the file item, 'timestamp' - the last modified
  // time of the file, or 'mime' - the mime type string. This will fail if the
  // size of the list exceeds 2048 elements. Note that once a list is sorted by
  // timestamp or mime, it will not be able to be filtered by a wild card
  // expression.
  //
  exports.sortFileInfoListBy = function(list, type) {
    if (!type) type = 'timestamp';
    return FileUtils.sortFileInfoListBy(list, type);
  }

  //
  // Merges two file lists to produce a list with no duplicate values and to
  // preserve the order of the lists.
  //
  exports.mergeFileLists = function(file_set1, file_set2) {
    return FileUtils.mergeFileLists(file_set1, file_set2);
  }


  //
  // Merges all the matching files at the given location, including files and/or
  // directories in the search criteria depending on whether 'match_files'
  // and/or 'match_dirs' is true. If 'file_set' is null then returns a new file
  // list.
  //
  exports.mergeMatchingFilesAt =
                  function(fs, location_fn, match_files, match_dirs, file_set) {

    // Get the directory and the wild card spec,
    var path_of = location_fn.getPath();
    var dir_fi = fs.getFileInfo(path_of.getPathFile());
    if (dir_fi == null || !dir_fi.isDirectory()) {
      throw "directory not found: " + path_of;
    }

    var wild_cards = location_fn.getFile().toString();

    // Convenience function that merges a list with the existing list (file_set)
    var mergeWithList = function(list) {
      if (!file_set)
        file_set = list;
      else
        file_set = exports.mergeFileLists(file_set, list);
    };

    // Assuming some sort of wild card expression,
    if (wild_cards.length() > 0) {

      // Closure for merging different types of lists,
      var merge_list = function(fs_function) {
        // Get the content of the directory of the wildcard,
        var list = fs_function(fs, path_of);
        // Filter it by the wild card,
        list = exports.filterByWildCardExpr(list, wild_cards);
        mergeWithList(list);
      }

      // Merge file and/or directory list as specified,
      if (match_files) merge_list( exports.getFileList );
      if (match_dirs) merge_list( exports.getDirectoryList );

    }
    else {
      // If no wild card spec, and we are matching directories, then add the
      // directory to the list,
      if (match_dirs) {
        mergeWithList(exports.asFileInfoList( [dir_fi] ));
      }
    }

    // Return the file_set, or an empty list if nothing merged,
    return file_set ? file_set : exports.asFileInfoList( [] );

  }


  //
  // Returns a sorted (lexicographically) list of FileInfo objects for the
  // list of wildcard specifications given.
  // 
  // PlatformContext ctx  = the platform context.
  // Array values         = the file specs (eg.
  //                          ['/admin/bin/*t', '/admin/system/*'] )
  // String pwd           = the current pwd string.
  // boolean match_files  = true to match files.
  // boolean match_dirs   = true to match directories.
  // boolean common_fs_matters = true if an exception is generated if the list
  //                        produced does not share common file repositories.
  //                        If false, the return item doesn't contain
  //                        'common_fs'.
  //
  // The return object contains the common FileRepository (common_fs) and the
  // list of FileInfo (files_list).
  //
  exports.findMatchingFileList =
              function(ctx, values, pwd,
                        match_files, match_dirs, common_fs_matters) {

    if (typeof match_files == 'undefined') match_files = true;
    if (typeof match_dirs == 'undefined') match_dirs = false;
    if (typeof common_fs_matters == 'undefined') common_fs_matters = true;

    var file_set;

    // The process function,
    // For each normalize file location, this will do a file list and wild card
    // filter on it.
    var proc_fun = function(this_fs, normal_file) {

      file_set = exports.mergeMatchingFilesAt(
                      this_fs, normal_file, match_files, match_dirs, file_set);

    };

    var fs_map =
          exports.processFileLocations(ctx, values, pwd,
                                      common_fs_matters, proc_fun);

    // Get the common file repository from the map,
    var common_fs;
    for (var repository_id in fs_map) {
      common_fs = fs_map[repository_id];
    }

    // Return object,
    return { common_fs: common_fs,
            file_list: file_set };

  }

  //
  // Given a set of locations in the file system, this will resolve the locations
  // and then call the 'process_location(this_fs, normalized_file_name)'
  // function for each. This can be used for all sorts of file set operations.
  //
  exports.processFileLocations =
              function(ctx, values, pwd, common_fs_matters, process_location) {

    var fs_map = {};
    var common_repository_id;

    var CommandLine = require('mwp/commandline');

    // For each directory to make,
    for (var i = 0, sz = values.length; i < sz; ++i) {
      var path_str = CommandLine.unescapeQuotes(values[i]);
      // The path as a file object,
      var file_ob = new exports.FileName(path_str);
      // The current 'pwd' as a file object,
      var pwd_ob = new exports.FileName(pwd).asDirectory();
      // Resolve the file ob against the pwd,
      var normal_loc = pwd_ob.resolve(file_ob);

      // The repository id,
      var repository_id = normal_loc.getRepositoryId();
      var this_fs;
      if (repository_id == null) {
        throw "invalid path";
      }
      if (!common_repository_id) {
        if (common_fs_matters) common_repository_id = repository_id;
        this_fs = fs_map[repository_id];
        if (!this_fs) {
          this_fs = ctx.getFileRepository(repository_id);
          if (this_fs != null) {
            fs_map[repository_id] = this_fs;
          }
          else {
            throw "repository id not found: " + repository_id;
          }
        }
      }
      else if (common_repository_id != repository_id) {
        throw "path does not share a common repository id: " + normal_loc;
      }

      // Call back on the user function to process the normalized file location,
      process_location(this_fs, normal_loc);

    }

    // Return the FileRepository map,
    return fs_map;

  }


  //
  // Deletes all the files given in the FileInfo list. This will not commit
  // the changes made, it is left to the callee to commit the deletes.
  //
  exports.deleteAllFiles = function(fs, file_list) {

    // We need to move all the files into an array list on the chance we are
    // dealing directly with the backed list (deleting files will cause the
    // backed list to change).
    var rm_list = new ArrayList(128);
    rm_list.addAll(file_list);
    var it = rm_list.iterator();
    var count = 0;
    while (it.hasNext()) {
      var fi = it.next();
      fs.deleteFile(fi.getAbsoluteName());
      ++count;
    }

    return count;

  }

})();
