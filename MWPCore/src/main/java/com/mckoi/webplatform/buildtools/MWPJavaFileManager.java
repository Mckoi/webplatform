/**
 * com.mckoi.webplatform.compiler.MWPJavaFileManager  Apr 11, 2011
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2012  Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this program.  If not, see ( http://www.gnu.org/licenses/ ) or
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 *
 * Change Log:
 *
 *
 */

package com.mckoi.webplatform.buildtools;

import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileUtilities;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.anttools.ZipDataFile;
import com.mckoi.webplatform.anttools.ZipEntry;
import com.mckoi.webplatform.util.HttpUtils;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.PrivilegedAction;
import java.util.*;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

/**
 * A javax.tools.JavaFileManager implementation that is backed by a Mckoi
 * Web Platform FileRepository, allowing a Java compilation processes to
 * happen entirely upon the files in a MckoiDDB file system.
 *
 * @author Tobias Downer
 */

public class MWPJavaFileManager implements JavaFileManager {

  /**
   * The repository id.
   */
  private final String repository_id;

  /**
   * The Mckoi file repository that backs this file manager.
   */
  private final FileRepository file_repository;

  /**
   * The class path to search when resolving a package name, where each
   * string represents either a directory (eg. "/lib/shared/classes/") or
   * a zip (jar/war) file (eg. "/lib/shared/lib/json.jar").
   */
  private final List<String> class_path;

  /**
   * The top level class loader that invokes the compiler. This must have the
   * system class loader at the end of the parent hierarchy.
   */
  private final ClassLoader class_loader;

  /**
   * The system classes on the class path that are allowed to be accessed by
   * the calling context.
   */
  private final ClassNameValidator allowed_system_classes;

  /**
   * The default character encoding of the Java files.
   */
  private String char_encoding = "UTF8";

  // The map of package_name to resources in the package for all resources
  // available in the webapp directory.
  private final TreeMap<String, List<URL>> webapp_package_map =
                                                               new TreeMap();
  private final HashSet<String> webapp_symbols_processed = new HashSet();

  /**
   * A map for querying the resources available in the class package
   * represented by the class loader. This is used by the file manager to
   * determine if the class loader can resolve a given resource.
   * <p>
   * The key of the map is the package name, the string list is the list of
   * resources (.class files, etc) in that package.
   */
  private final SortedMap<String, List<URL>> package_dictionary_map;

  /**
   * The location of the source directories in the FileRepository, where the
   * path string does not end with "/".
   */
  private final List<String> source_dirs;

  /**
   * The location of the output files in the FileRepository, where the path
   * string does not end with "/".
   */
  private String output_dir = null;

  /**
   * The set of all files that are touched (written to) by this compile
   * process.
   */
  private final HashSet<String> touched_files = new HashSet();

  /**
   * DEBUG: Displays API calls on System.out.
   */
  private static final boolean SHOW_API_CALLS = false;
  

  /**
   * Constructor.
   *
   * @param repository_id the repository id.
   * @param file_repository the Mckoi Web Platform file repository.
   * @param class_path the list of class libraries in the file repository
   *   used to resolve compiled resources. Note that this class path is in
   *   addition to the classes already loaded into the VM.
   * @param class_loader the class loader used to look up class data to be
   *   resolved against (must resolve the system class loader and the compiled
   *   class path).
   * @param package_directory_map an immutable map of all package resources
   *   exposed from the system API.
   */
  public MWPJavaFileManager(
                     String repository_id,
                     FileRepository file_repository,
                     List<String> class_path,
                     final ClassLoader class_loader,
                     final ClassNameValidator allowed_system_classes,
                     SortedMap<String, List<URL>> package_dictionary_map) {

    this.repository_id = repository_id;
    this.file_repository = file_repository;
    this.class_path = class_path;
    // Create a wrapped class loader (priviledged action)
    this.class_loader = new PrivilegedAction<ClassLoader>() {
      @Override
      public ClassLoader run() {
        return new MWPWrappedClassLoader(class_loader);
      }
    }.run();
    this.allowed_system_classes = allowed_system_classes;
    this.package_dictionary_map = package_dictionary_map;
    this.source_dirs = new ArrayList();
  }

  /**
   * Returns the set of all files written to during the life span of this
   * object.
   */
  public Set<String> getAllTouchedFiles() {
    return touched_files;
  }

  /**
   * Adds a source directory in the FileRepository of the files to be
   * compiled. The path must not end with "/". If the root directory is
   * desired then use a path of "".
   */
  public void addSourceDir(String path) {
    // Remove trailing '/' if necessary
    if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
    source_dirs.add(path);
  }

  /**
   * Sets the output directory in the FileRepository of the files being
   * compiled. The path must not end with "/". If the root directory is
   * desired then use a path of "".
   */
  public void setOutputDir(String path) {
    // Remove trailing '/' if necessary
    if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
    output_dir = path;
  }

  /**
   * Given a package and resource, adds the entry to the package map. The
   * package map is a map of 'package_name' -> 'resource list' entries.
   * Returns 0 if the resource is already found in the map, 1 if the resource
   * was added to a package item.
   */
  private static int addToPackageMap(
                             TreeMap<String, List<URL>> package_map,
                             String package_name, URL resource_name) {
    List<URL> package_list = package_map.get(package_name);
    if (package_list == null) {
      package_list = new ArrayList();
      package_map.put(package_name, package_list);
    }
    if (!package_list.contains(resource_name)) {
      package_list.add(resource_name);
//      System.out.println(package_name + " -> " + resource_name);
      return 1;
    }
    return 0;
  }

//  /**
//   * Returns the location of the directory with the source code.
//   */
//  private String getSourceDir() {
//    return source_dir;
////    // Current implementation is the .java and .class files are located in
////    // the scratch directory.
////    return "/scratch/jsp";
//  }

  /**
   * Returns the location of the directory where the code is generated.
   */
  private String getOutputDir() {
    return output_dir;
//    // Current implementation is the .java and .class files are located in
//    // the scratch directory.
//    return "/scratch/jsp";
  }

  /**
   * Recursive method that searches sub-directories for files.
   */
  private void recurseDirectorySearch(
                    ArrayList<FileInfo> files, String dir, boolean recurse) {
    // The directory content,
    List<FileInfo> content = file_repository.getDirectoryFileInfoList(dir);
    if (content != null) {
      for (FileInfo file : content) {
        files.add(file);
        if (file.isDirectory()) {
          if (recurse) {
            recurseDirectorySearch(files, dir + file, recurse);
          }
        }
      }
    }
  }

  /**
   * Lists all the files of the specified kinds found in the given package
   * of the account's file system.
   */
  private List<JavaFileObject> getFilesFromFileSystem(
          String base_dir, String package_name, Set<Kind> kinds,
          boolean recurse) {

    // Convert the package_name is a directory location
    String package_dir = package_name.replace('.', '/');
    if (package_dir.length() > 0) {
      package_dir = package_dir.concat("/");
    }

    // The absolute path
    String absolute_dir = base_dir + "/" + package_dir;

    // Make a list of all the files,
    ArrayList<FileInfo> all_files = new ArrayList();
    recurseDirectorySearch(all_files, absolute_dir, recurse);
    List<FileInfo> dir_content = all_files;

    ArrayList<JavaFileObject> result_list = new ArrayList();

    // Filter the kinds we are interested in,
    for (FileInfo finfo : dir_content) {
      String file = finfo.getItemName();
      if (!finfo.isDirectory()) {
        if ((file.endsWith(".class") && kinds.contains(Kind.CLASS)) ||
            (file.endsWith(".java") && kinds.contains(Kind.SOURCE)) ||
            (file.endsWith(".html") && kinds.contains(Kind.HTML)) ||
            (kinds.contains(Kind.OTHER)) ) {
          result_list.add(new MWPJavaFileObject(
                           file_repository, finfo, file, char_encoding, true));
        }
      }
    }

    // Return the result,
    return result_list;
  }


  /**
   * Populates the TreeMap with all the resources found if the FileRepository
   * at the given directory. Recurses on the sub-directories.
   */
  private void localFindCompilerSymbols(TreeMap map,
                      String package_name, String sdb_dir) throws IOException {
    List<FileInfo> files = file_repository.getDirectoryFileInfoList(sdb_dir);
    for (FileInfo file : files) {
      if (file.isDirectory()) {
        String sub_dir = file.getItemName();
        String sub_package = sub_dir.substring(0, sub_dir.length() - 1);
        // Recurse on a directory
        String child_package = (package_name.equals("")) ?
                        sub_package : package_name + "." + sub_package;
        localFindCompilerSymbols(map, child_package, sdb_dir + sub_dir);
      }
      else {
        String resource_name = file.getAbsoluteName();
        URL resource_url =
             new URL("mwpfs", null, -1, "/" + repository_id + resource_name);
        addToPackageMap(map, package_name, resource_url);
      }
    }
  }

  /**
   * Populates the 'webapp_package_map' object with all Java binary files
   * found at the given location in the file repository. The location arg
   * may either be a path in which case it is treated as a /classes/ like
   * directory (full of .class files), or it points to a .jar file.
   */
  private void populateAppPackageMap(String fs_location) throws IOException {

    // Build a package list from the 'fs_location' which is either a directory
    // or a .jar file.

    // Do we need to build the symbols for this file?
    if (!webapp_symbols_processed.contains(fs_location)) {
      webapp_symbols_processed.add(fs_location);

//      System.out.println("&&&& populateAppPackageMap(" + fs_location + ")");

      // Is it a directory?
      if (fs_location.endsWith("/")) {

        localFindCompilerSymbols(webapp_package_map, "", fs_location);

      }
      // Otherwise must be a .jar file,
      else {
        DataFile df = file_repository.getDataFile(fs_location);
        ZipDataFile zip_file = new ZipDataFile(df, "UTF-8", false, false);

        // The first part of the URL string,
        // eg. 'mwpfs:/admin/workspace/cms.v1/lib/mylib.jar!'
        StringBuilder url_start = new StringBuilder();
        url_start.append("mwpfs:/");
        url_start.append(repository_id);
        url_start.append(fs_location);
        url_start.append("!/");
        String url_start_str = url_start.toString();

        // Iterate through the zip,
        Enumeration<ZipEntry> entries = zip_file.getEntries();
        while (entries.hasMoreElements()) {
          // Get the zip entry,
          ZipEntry zip_entry = entries.nextElement();
          final String item_str = zip_entry.getName();

          // Convert the item string name into a package/file
          String item_package;
          String item_resource_name;
          int d = item_str.lastIndexOf('/');
          if (d == -1) {
            item_package = "";
          }
          else {
            item_package = item_str.substring(0, d).replace('/', '.');
          }
          item_resource_name = item_str.substring(d + 1);

          // Ignore WEB-INF and META-INF
          // Ignore directory entries,

          if (!item_str.startsWith("WEB-INF/") &&
              !item_str.startsWith("META-INF/") &&
              !item_resource_name.equals("")) {

            // Create the jar url for this resource,
            URL jar_url =
                    new URL("jar", null, -1, url_start_str + item_str);

            // Build the 'webapp_package_map' with the local package
            // details,
            addToPackageMap(webapp_package_map,
                            item_package, jar_url);
          }
        }

      }

    }

  }

  /**
   * Given a package name, returns the location of all the resources within
   * the package. Assumes 'populateAppPackageMap' has been called on all
   * the locations to be included.
   */
  private List<URL> getLocalPackageContents(String package_name) {

    // Is the package list cached?
    List<URL> result_list;
    result_list = webapp_package_map.get(package_name);

    if (result_list == null) {
      return new ArrayList();
    }
    else {
      return result_list;
    }

  }

  /**
   * Returns the list of all files with the given package name from the
   * VM's class path, and also from the WEB-INF/classes/ directory and the
   * .jar files in the WEB-INF/lib/ directory in the app's context.
   * <p>
   * Note that the VM's class path info is loaded at startup from the
   * command line class path. The application's classes have to be resolved
   * for each process.
   */
  private List<JavaFileObject> getPackageFromClasspath(
                final String package_name, Set<Kind> kinds, boolean recurse)
                                                           throws IOException {

//    System.out.println("??? getPackageFromClasspath " + package_name +
//                       ", " + kinds + ", " + recurse);

    String package_dir = package_name.replace('.', '/');
    if (package_dir.length() > 0) {
      package_dir = package_dir + "/";
    }

    // We don't suppose 'recurse' (yet?)
    if (recurse) {
      throw new UnsupportedOperationException("Recurse not supported.");
    }

    ArrayList<JavaFileObject> result_list = new ArrayList();

    // Load from the JVM system classes first,
    List<URL> items = package_dictionary_map.get(package_name);
    if (items != null) {
      for (URL url : items) {
        String item_name = HttpUtils.decodeURLFileName(url);
        
        // The object file name,
        int d = item_name.lastIndexOf("/") + 1;
        String file_name_only = item_name.substring(d);
        String object_file_name = package_dir + file_name_only;
        boolean accepted = false;

        if ((item_name.endsWith(".class") && kinds.contains(Kind.CLASS))) {

          // Convert to a class name and check it's an accepted class,
          String class_name =
                      file_name_only.substring(0, file_name_only.length() - 6);
          String complete_class_name = package_name + "." + class_name;
          if (allowed_system_classes.isAcceptedClass(complete_class_name)) {
            accepted = true;
          }

        }
        else if
           ( (item_name.endsWith(".java") && kinds.contains(Kind.SOURCE)) ||
             (item_name.endsWith(".html") && kinds.contains(Kind.HTML)) ||
             (!item_name.endsWith("/") && kinds.contains(Kind.OTHER)) ) {

          accepted = true;

        }

        // If the file is accepted,
        if (accepted) {
          // Add to the output result,
          result_list.add(new URLJavaFileObject(
                        object_file_name, url, char_encoding));
//          result_list.add(new ClassLoaderJavaFileObject(
//                        class_loader, object_file_name, url, char_encoding));

        }

      }
    }

    // Make sure to load from the class path (from the code's file repository),
    // This will either be a directory (eg. '/myproject/classes/') or a .jar
    // file location (eg. '/myproject/lib/mylib.jar').
    for (String repository_location : class_path) {
      populateAppPackageMap(repository_location);
    }

    // Get the package from the local map
    items = getLocalPackageContents(package_name);

    // For all the items,
    for (URL url : items) {
      String item_name = HttpUtils.decodeURLFileName(url);
      if ((item_name.endsWith(".class") && kinds.contains(Kind.CLASS)) ||
          (item_name.endsWith(".java") && kinds.contains(Kind.SOURCE)) ||
          (item_name.endsWith(".html") && kinds.contains(Kind.HTML)) ||
          (!item_name.endsWith("/") && kinds.contains(Kind.OTHER)) ) {

        // The object file name,
        int d = item_name.lastIndexOf("/") + 1;
        String object_name = package_dir + item_name.substring(d);

        // Add if it's the kind we are searching for,
        result_list.add(
                new URLJavaFileObject(object_name, url, char_encoding));
//        result_list.add(new ClassLoaderJavaFileObject(
//                        class_loader, object_name, url, char_encoding));

      }
    }

    // Return the found items,
    return result_list;

  }

  /**
   * Adds the contents of list2 to list1, where list2 is a none null list
   * and list1 is a list or null. If list1 is null, it is considered an
   * empty list.
   */
  private static <T> List<T> addAllFiles(List<T> list1, List<T> list2) {
    if (list1 == null) {
      return list2;
    }
    else {
      list1.addAll(list2);
      return list1;
    }
  }


  private JavaFileObject createJavaFileObject(
                 Location location, String file_name, boolean for_input) {

    // If location is the class output location,
    if (!for_input ||
         (location == StandardLocation.CLASS_OUTPUT ||
          location == StandardLocation.CLASS_PATH)) {
      String absolute_name = getOutputDir() + "/" + file_name;
      // If it's for input and the file doesn't exist, return null
      FileInfo finfo = file_repository.getFileInfo(absolute_name);
      // If for input,
      if (for_input) {
        // If the file doesn't exist, return null
        if (finfo == null) {
          return null;
        }
        // Otherwise file exists
      }
      // Else if for output,
      else {
        // If the file doesn't exist, create it,
        if (finfo == null) {
          file_repository.createFile(absolute_name,
                              FileUtilities.findMimeType(absolute_name),
                              System.currentTimeMillis());
          finfo = file_repository.getFileInfo(absolute_name);
        }
//        else {
//          // Does exist, so clear the file,
//          finfo.getDataFile().delete();
//          // Update the timestamp,
//          file_repository.touchFile(absolute_name, System.currentTimeMillis());
//        }
        touched_files.add(absolute_name);
      }
      return new MWPJavaFileObject(file_repository,
                               finfo, absolute_name, char_encoding, for_input);
    }
    // Platform class path (load from the classloader)
    else if (location == StandardLocation.PLATFORM_CLASS_PATH) {
      // If for output, return null
      if (!for_input) {
        return null;
      }
      // Is the resource available in the class loader?
      URL url = class_loader.getResource(file_name);
      if (url != null) {
        return new URLJavaFileObject(file_name, url, char_encoding);
//        return new ClassLoaderJavaFileObject(
//                        class_loader, file_name, url, char_encoding);
      }
      else {
        // Not available, return null
        return null;
      }
    }
    // If location is the source code location,
    else if (location == StandardLocation.SOURCE_PATH) {
      // If for output, return null
      if (!for_input) {
        return null;
      }

      // For each source directory (in order) see if the file exists and
      // return if found,
      for (String source_dir : source_dirs) {
        String absolute_name = source_dir + "/" + file_name;
        FileInfo finfo = file_repository.getFileInfo(absolute_name);
        if (finfo != null) {
          return new MWPJavaFileObject(file_repository,
                               finfo, absolute_name, char_encoding, for_input);
        }
      }
      // Not found so return null
      return null;
    }
    else {
      // SOURCE_OUTPUT and ANNOTATION_PROCESSOR_PATH
      throw new UnsupportedOperationException();
    }

  }


  // ----- Implemented from JavaFileManager -----

  @Override
  public void close() throws IOException {
    // No op
  }

  @Override
  public void flush() throws IOException {
    // No op
  }

  @Override
  public ClassLoader getClassLoader(Location location) {
//    System.out.println("??? getClassLoader(" + location + ")");
//    new Error().printStackTrace();

//    return class_loader;
    
    // NODE: This classpath is for plug-ins to handle things like annotation
    // processing. Not sure how this works so returning null for now until I
    // understand the implications.
    return null;
  }

  @Override
  public FileObject getFileForInput(Location location,
                String packageName, String relativeName) throws IOException {

    if (SHOW_API_CALLS) {
      System.out.println("??? getFileForInput(" + location + "," + packageName + "," + relativeName + ")");
    }

    String relative_file_name =
                          packageName.replace(".", "/") + "/" + relativeName;
    return createJavaFileObject(location, relative_file_name, true);

  }

  @Override
  public FileObject getFileForOutput(Location location,
            String packageName, String relativeName, FileObject sibling)
                                                         throws IOException {
    if (SHOW_API_CALLS) {
      System.out.println("??? getFileForOutput(" + location + "," + packageName + "," + relativeName + ")");
    }

    String relative_file_name =
                          packageName.replace(".", "/") + "/" + relativeName;
    return createJavaFileObject(location, relative_file_name, false);

  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location,
                            String className, Kind kind) throws IOException {

    if (SHOW_API_CALLS) {
      System.out.println("??? getJavaFileForInput(" + location + "," + className + "," + kind + ")");
    }

    String relative_file_name =
                          className.replace(".", "/").concat(kind.extension);
    return createJavaFileObject(location, relative_file_name, true);

  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location,
        String className, Kind kind, FileObject sibling) throws IOException {

    if (SHOW_API_CALLS) {
      System.out.println("??? getJavaFileForOutput(" + location + "," + className + "," + kind + ")");
    }

    String relative_file_name =
                          className.replace(".", "/").concat(kind.extension);
    return createJavaFileObject(location, relative_file_name, false);

  }

  @Override
  public boolean handleOption(String current, Iterator<String> remaining) {
    if (current.equals("-encoding")) {
      if (remaining.hasNext()) {
        String encoding_type = remaining.next();
        char_encoding = encoding_type;
        return true;
      }
      else {
        return false;
      }
    }

    if (SHOW_API_CALLS) {
      System.out.println("??? handleOption(" + current + "," + remaining + ")");
    }
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasLocation(Location location) {
    if (SHOW_API_CALLS) {
      System.out.println("JavaFileManager.hasLocation = " + location);
    }

    if (location == StandardLocation.SOURCE_PATH ||
        location == StandardLocation.CLASS_OUTPUT ||
        location == StandardLocation.CLASS_PATH ||
        location == StandardLocation.PLATFORM_CLASS_PATH
        ) {
      return true;
    }

    return false;
  }

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {

    BaseJavaFileObject base_jfo = (BaseJavaFileObject) file;
    String infer_name = base_jfo.inferBinaryName();

    if (SHOW_API_CALLS) {
//    System.out.println("??? inferBinaryName(" + location + "," + file + ") = " + infer_name);
    }

    return infer_name;
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    if (SHOW_API_CALLS) {
      System.out.println("??? isSameFile(" + a + "," + b + ")");
    }

    return ((BaseJavaFileObject) a).getAbsoluteName().equals(
                                 ((BaseJavaFileObject) b).getAbsoluteName());
  }

  @Override
  public Iterable<JavaFileObject> list(
              Location location, String packageName, Set<Kind> kinds,
              boolean recurse) throws IOException {

    if (SHOW_API_CALLS) {
      System.out.println("??? list(" + location + ", " + packageName + ", " + kinds + ", " + recurse + ")");
    }

    List<JavaFileObject> package_items = null;

    // The platform class path,
    if (location == StandardLocation.PLATFORM_CLASS_PATH) {

      // Load from the class path
      package_items = getPackageFromClasspath(packageName, kinds, recurse);

    }

    // The user class path,
    else if (location == StandardLocation.CLASS_PATH) {
      // Resolve the package,

      // Load from the class path
      package_items = getPackageFromClasspath(packageName, kinds, recurse);

//      // If the package is the JSP load area,
//      String jsp_compile_package = Constants.JSP_PACKAGE_NAME;
//      if (packageName.equals(jsp_compile_package)) {

        // This package is resolved against the scratch directory,
        List<JavaFileObject> files_list =
                getFilesFromFileSystem(getOutputDir(),
                                       packageName, kinds, recurse);
        package_items = addAllFiles(package_items, files_list);
//      }
    }

    else if (location == StandardLocation.SOURCE_PATH) {
//      // If the package is the JSP load area,
//      String jsp_compile_package = Constants.JSP_PACKAGE_NAME;
//      if (packageName.equals(jsp_compile_package)) {
        for (String source_dir : source_dirs) {
          // This package is resolved against the scratch directory,
          List<JavaFileObject> files_list =
                  getFilesFromFileSystem(source_dir,
                                         packageName, kinds, recurse);
          package_items = addAllFiles(package_items, files_list);
        }
//      }
    }

    else {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    // Empty list, return null,
    if (package_items == null) {
      return new ArrayList();
    }
    else {
      return package_items;
    }
  }

  @Override
  public int isSupportedOption(String option) {
    if (SHOW_API_CALLS) {
      System.out.println("??? isSupportedOption(" + option + ")");
    }
    return -1;  // TODO?
  }


  
  

  private static abstract class BaseJavaFileObject implements JavaFileObject {

    final String absolute_name;
    final String name;
    private final String char_encoding;
    private String inferred_name = null;

    BaseJavaFileObject(String file_name, String char_encoding) {
      this.absolute_name = file_name;
      this.char_encoding = char_encoding;
      int d = file_name.lastIndexOf("/");
      if (d == -1) {
        d = 0;
      }
      this.name = file_name.substring(d + 1);
    }

    // -----

    @Override
    public Modifier getAccessLevel() {
      return null;
    }

    @Override
    public Kind getKind() {
      if (absolute_name.endsWith(".class")) {
        return Kind.CLASS;
      }
      else if(absolute_name.endsWith(".java")) {
        return Kind.SOURCE;
      }
      else if (absolute_name.endsWith(".html")) {
        return Kind.HTML;
      }
      else {
        return Kind.OTHER;
      }
    }

    @Override
    public NestingKind getNestingKind() {
      return null;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
//      System.out.println("??? isNameCompatible(" + simpleName + ", " + kind + ")");

      if (!kind.equals(Kind.OTHER)) {
        String resolved_fname = simpleName + kind.extension;
        return (resolved_fname.equals(name));
      }
      else {
        // OTHER will only match OTHER kinds,
        if (kind.equals(getKind())) {
          // This is a hacky test but no better way to do it.
          return name.startsWith(simpleName);
        }
      }

      return false;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors)
                                                           throws IOException {
//      System.out.println("@@@ getCharContent(" + absolute_name + ")");

//      long file_size = getSize();
//      // Sanity check on file size,
//      // Larger than 5MB Java files anyone?
//      if (file_size > 5000000) {
//        throw new IOException("File too large: " + absolute_name);
//      }
      StringBuilder str = new StringBuilder(16384);
      Reader content_reader = openReader(true);
      char[] buf = new char[4096];
      int last_read;
      while ((last_read = content_reader.read(buf, 0, buf.length)) >= 0) {
        str.append(buf, 0, last_read);
      }

      // Return the string,
      return str.toString();
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
      return new InputStreamReader(openInputStream(), char_encoding);
    }

    @Override
    public Writer openWriter() throws IOException {
      return new OutputStreamWriter(openOutputStream(), char_encoding);
    }

    @Override
    public URI toUri() {
//      System.out.println("@@@ toUri(" + absolute_name + ")");
      try {
        return new URI(absolute_name);
      }
      catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

//    protected abstract long getSize();

    private String getAbsoluteName() {
      return absolute_name;
    }

    private String inferBinaryName() {
      if (inferred_name == null) {
        String infer_name = getAbsoluteName();
        int p = infer_name.lastIndexOf(".");
        inferred_name = infer_name.substring(0, p).replace("/", ".");
      }
      return inferred_name;
    }

    @Override
    public String toString() {
      return absolute_name;
    }

  }

  /**
   * A resource object that can be located by a URL.
   */
  private static class URLJavaFileObject extends BaseJavaFileObject {

    private final URL resource_url;

    URLJavaFileObject(String absolute_resource_name,
                      URL resource_url,
                      String char_encoding) {
      super(absolute_resource_name, char_encoding);
      this.resource_url = resource_url;
    }

    private URL getObjectResource() {
      return resource_url;
    }

//    @Override
//    public long getSize() {
//      try {
//        URLConnection c = getObjectResource().openConnection();
//        return c.getContentLength();
//      }
//      catch (IOException e) {
//        throw new RuntimeException(e);
//      }
//    }

    @Override
    public long getLastModified() {
      try {
        URLConnection c = getObjectResource().openConnection();
        return c.getLastModified();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public InputStream openInputStream() throws IOException {
//      System.out.println("@@@ (2) openInputStream(" + absolute_name + ")");
      try {
        URLConnection c = getObjectResource().openConnection();
        return c.getInputStream();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }

  }

  /**
   * A resource object that can be located from the given class loader.
   */
  private static class ClassLoaderJavaFileObject extends BaseJavaFileObject {

    private final ClassLoader class_loader;
    private final URL resource_url;
    private Long cached_last_modified = null;

    ClassLoaderJavaFileObject(ClassLoader class_loader,
                              String absolute_resource_name,
                              URL resource_url,
                              String char_encoding) {
      super(absolute_resource_name, char_encoding);
      this.resource_url = resource_url;
      this.class_loader = class_loader;
    }

    private URL getObjectResource() {
      return resource_url;
    }

    private void populateCache() {
      // Note that this is quite bad because it may cause a URL resource to
      //   be materialized (for example; a 'jar:mwpfs' resource will need to
      //   be copied into the temp directory).
      if (cached_last_modified == null) {
        try {
          URLConnection c = getObjectResource().openConnection();
          cached_last_modified = c.getLastModified();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

//    @Override
//    public long getSize() {
//      System.out.println("@@@ SLOW getSize() on " + absolute_name);
//      populateCache();
//      return cached_size;
//    }

    @Override
    public long getLastModified() {
      System.out.println("@@@ SLOW getLastModified() on " + absolute_name);
      populateCache();
      return cached_last_modified;
    }

    @Override
    public InputStream openInputStream() throws IOException {
//      System.out.println("@@@ (2) openInputStream(" + absolute_name + ")");
      return class_loader.getResourceAsStream(absolute_name);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }

  }


  public static class MWPJavaFileObject extends BaseJavaFileObject {

    private final FileRepository file_repository;
    private final FileInfo file_info;
    private final boolean for_input;

    public MWPJavaFileObject(FileRepository file_repository,
                    FileInfo file_info, String file_name,
                    String char_encoding, boolean for_input) {
      super(file_name, char_encoding);
      if (file_info == null) {
        throw new NullPointerException();
      }
      this.file_repository = file_repository;
      this.file_info = file_info;
      this.for_input = for_input;
    }

//    @Override
//    public long getSize() {
//      return file_info.getDataFile().size();
//    }

    @Override
    public long getLastModified() {
      return file_info.getLastModified();
    }

    @Override
    public InputStream openInputStream() throws IOException {
//      System.out.println("@@@ openInputStream(" + absolute_name + ")");
      return DataFileUtils.asInputStream(file_info.getDataFile());
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
//      System.out.println("@@@ openOutputStream(" + absolute_name + ")");

      if (for_input) throw new IOException("Read only");

      // If the file contains data we clear it of data and set the timestamp,
      file_repository.touchFile(absolute_name, System.currentTimeMillis());
      DataFile df = file_info.getDataFile();
      return DataFileUtils.asSimpleDifferenceOutputStream(df);
    }

    @Override
    public boolean delete() {
//      System.out.println("@@@ delete(" + absolute_name + ")");
      if (for_input) throw new RuntimeException("Read only");
      file_repository.deleteFile(absolute_name);
      return true;
    }

  }

  
  /**
   * We wrap a ClassLoader here for the MWPJavaFileManager so that the
   * JDK can't do something bad with the secure app class loader that
   * probably will be passed into this class. This is also to fix a problem
   * where the JDK in Java 1.7 will close a class loader given it which is
   * really bad if you give it the secure class loader.
   */
  private static class MWPWrappedClassLoader extends ClassLoader {

    private final ClassLoader parent;

    public MWPWrappedClassLoader(ClassLoader parent) {
      super(parent);
      this.parent = parent;
    }

    @Override
    public void clearAssertionStatus() {
//      System.out.println("JCL: clearAssertionStatus");
      super.clearAssertionStatus();
    }

    @Override
    public URL getResource(String name) {
      URL url = super.getResource(name);
//      System.out.println("JCL: getResource(" + name + ") = " + url);
      return url;
    }

    @Override
    public InputStream getResourceAsStream(String name) {

      // NOTE: The ClassLoader API is quite bad! The 'parent' is called here
      //   because the default implementation uses 'getResource',
      InputStream is = parent.getResourceAsStream(name);

//      System.out.println("JCL: getResourceAsStream(" + name + ") = " + is);

      return is;
//      return super.getResourceAsStream(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
//      System.out.println("JCL: getResources(" + name + ")");
      return super.getResources(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
//      System.out.println("JCL: loadClass(" + name + ")");
      return super.loadClass(name);
    }

    @Override
    public void setClassAssertionStatus(String className, boolean enabled) {
//      System.out.println("JCL: setClassAssertionStatus(" + className + ", " + enabled + ")");
      super.setClassAssertionStatus(className, enabled);
    }

    @Override
    public void setDefaultAssertionStatus(boolean enabled) {
//      System.out.println("JCL: setDefaultAssertionStatus(" + enabled + ")");
      super.setDefaultAssertionStatus(enabled);
    }

    @Override
    public void setPackageAssertionStatus(String packageName, boolean enabled) {
//      System.out.println("JCL: setPackageAssertionStatus(" + packageName + ", " + enabled + ")");
      super.setPackageAssertionStatus(packageName, enabled);
    }

  }

}
