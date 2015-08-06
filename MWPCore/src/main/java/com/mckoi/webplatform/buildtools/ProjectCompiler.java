/**
 * com.mckoi.webplatform.buildtools.ProjectCompiler  Apr 14, 2011
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

import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.util.StyledPrintUtil;
import com.mckoi.util.StyledPrintWriter;
import com.mckoi.webplatform.BuildError;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.buildtools.MWPJavaFileManager.MWPJavaFileObject;
import com.mckoi.webplatform.impl.PlatformContextImpl;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.*;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;

/**
 * An object that can compile a project in a Mckoi Web Platform file
 * repository.
 *
 * @author Tobias Downer
 */

public class ProjectCompiler {

  /**
   * The repository name (the account name).
   */
  private final String repository_id;

  /**
   * The backed file repository.
   */
  private final FileRepository repository;

  /**
   * The base path of the project.
   */
  private final String base_path;

  /**
   * The ClassLoader to use.
   */
  private final ClassLoader class_loader;

  /**
   * The directory where the source files are located from.
   */
  private final List<String> source_dirs;

  /**
   * The directory to output classes to.
   */
  private String output_dir;

  /**
   * The input source files. Each entry is an absolute reference to a file.
   */
  private List<String> source_files;

  /**
   * The class path (the list of directories or .jar files that contain the
   * classes we compile against.
   */
  private final List<String> class_path;

  /**
   * The set of all file names touched during the last compile process.
   */
  private final Set<String> touched_files;


  /**
   * Constructor.
   */
  public ProjectCompiler(String repository_id,
                         FileRepository repository, String base_path,
                         ClassLoader class_loader) {
    if (!base_path.endsWith("/")) {
      throw new IllegalArgumentException("base_path must end with '/'");
    }
    this.repository_id = repository_id;
    this.repository = repository;
    this.base_path = base_path;
    this.class_loader = class_loader;
    this.class_path = new ArrayList();
    this.source_dirs = new ArrayList();
    this.touched_files = new HashSet();
  }

  /**
   * Returns the base path of the project.
   */
  public String getBasePath() {
    return base_path;
  }

  /**
   * Sets the location of compiled java source files relative to the base
   * path.
   */
  public void setOutputDirectory(String path) {
    output_dir = base_path + path;
  }

  /**
   * Adds the location of a source directory relative to the base path.
   */
  public void addSourceDirectory(String path) {
    source_dirs.add(base_path + path);
  }

  /**
   * Sets the input source files. Each string entry of the list must be an
   * absolute reference to a file in the file repository.
   */
  public void setInputSourceFiles(List<String> source_files) {
    this.source_files = source_files;
  }

  /**
   * Adds a directory or .jar file to the class-path the compiler uses to
   * resolve references. The location must be an absolute reference to a
   * resource in the file repository. If it's a directory entry, the location
   * string must end with '/', otherwise '.jar'.
   */
  public void addToClassPath(String location) {
    class_path.add(location);
  }

  /**
   * Adds a set of directory or .jar files to the class-path the compiler uses
   * to resolve references. The location must be an absolute reference to a
   * resource in the file repository. If it's a directory entry, the location
   * string must end with '/', otherwise '.jar'.
   */
  public void addToClassPath(Iterable<String> locations) {
    for (String location : locations) {
      class_path.add(location);
    }
  }

  /**
   * Removes an entry from the class-path.
   */
  public void removeFromClassPath(String location) {
    class_path.remove(location);
  }

  /**
   * Returns the list of entries in the class-path.
   */
  public List<String> getClassPath() {
    return Collections.unmodifiableList(class_path);
  }

  /**
   * Returns the set of all absolute file names that were written to during
   * the last call to 'compile'.
   */
  public Set<String> getAllTouchedFiles() {
    return touched_files;
  }

  /**
   * Compiles the source .java files in the file repository specified by the
   * 'source_files' list and outputs the compiled .class files to the output
   * directory. Returns false if the compile failed.
   * <p>
   * Any compilation errors are added to the 'errors' list.
   */
  public boolean compile(
                     StyledPrintWriter result_out, List<BuildError> errors) {

    String java_encoding = "UTF-8";
    String source_vm = "1.6";
    String target_vm = "1.6";

//    System.out.println("source_file = " + source_file);
//    System.out.println("output_dir = " + output_dir);
//    System.out.println("target_class_name = " + target_class_name);
//    System.out.println("class_loader = " + class_loader);
//
//    System.out.println("java_encoding = " + java_encoding);
//    System.out.println("source_vm = " + source_vm);
//    System.out.println("target_vm = " + target_vm);

    JavaCompiler jvm_java_compiler =
                                 SystemBuildStatics.getSystemJVMJavaCompiler();
    if (jvm_java_compiler == null) {
      // Must be set by a call to 'setJVMJavaCompiler'
      throw new RuntimeException("JVM Compiler has not been loaded");
    }

    int sz = source_files.size();
    JavaFileObject[] source_jfo_files = new JavaFileObject[sz];
    for (int i = 0; i < sz; ++i) {
      String source_file = source_files.get(i);
      source_jfo_files[i] =
          new MWPJavaFileObject(repository, repository.getFileInfo(source_file),
                                source_file, java_encoding, true);
    }

    Writer err_out = StyledPrintUtil.wrapWriter(result_out);

    // Resolve against the web app class loader in the loader hierarchy,
//    while (!(webapp_loader instanceof WebAppClassLoader)) {
//      webapp_loader = webapp_loader.getParent();
//    }

//    // Create the class path hash set,
//    HashSet<String> class_path = new HashSet();
//    String class_path_source = url.getPath();
//    class_path.add(class_path_source);

    ClassLoader webapp_loader = class_loader;
    ClassNameValidator allowed_system_classes =
                                 PlatformContextImpl.getAllowedSystemClasses();

    SortedMap<String, List<URL>> jvm_package_map =
                                   SystemBuildStatics.getSystemJVMPackageMap();

    // Create the file manager,
    MWPJavaFileManager java_file_manager = new MWPJavaFileManager(
                    repository_id, repository,
                    class_path, webapp_loader, allowed_system_classes,
                    jvm_package_map);
    // Set the source and output directory,
    for (String source_dir : source_dirs) {
      java_file_manager.addSourceDir(source_dir);
    }
    java_file_manager.setOutputDir(output_dir);

    DiagnosticCollector<JavaFileObject> diagnostics =
                                    new DiagnosticCollector<JavaFileObject>();

    // Set the compiler options,
    ArrayList<String> compiler_options = new ArrayList();
//    // No annotations for JSPs
//    compiler_options.add("-proc:none");
    compiler_options.add("-source");
    compiler_options.add(source_vm);
    compiler_options.add("-target");
    compiler_options.add(target_vm);
    compiler_options.add("-encoding");
    compiler_options.add(java_encoding);
//    compiler_options.add("-verbose");

    // Create the compilation task,
    JavaCompiler.CompilationTask task = jvm_java_compiler.getTask(
                err_out, java_file_manager, diagnostics,
                compiler_options, null,
                Arrays.asList(source_jfo_files));

    // Close the file manager if necessary
    try {
      java_file_manager.close();
    }
    catch (IOException ex) {
      // ignore IO Exception on close
    }

    // Call the compilation task,
    boolean compile_success = task.call();

    // Add all touched files,
    touched_files.addAll(java_file_manager.getAllTouchedFiles());

    // Any diagnostics,
    for (Diagnostic<? extends JavaFileObject> dm : diagnostics.getDiagnostics()) {

      Diagnostic.Kind error_kind = dm.getKind();
      String build_error_type;
      if (error_kind.equals(Diagnostic.Kind.ERROR)) {
        build_error_type = BuildError.ERROR;
      }
      else if (error_kind.equals(Diagnostic.Kind.MANDATORY_WARNING)) {
        build_error_type = BuildError.WARNING;
      }
      else if (error_kind.equals(Diagnostic.Kind.WARNING)) {
        build_error_type = BuildError.WARNING;
      }
      else if (error_kind.equals(Diagnostic.Kind.NOTE)) {
        build_error_type = BuildError.INFO;
      }
      else if (error_kind.equals(Diagnostic.Kind.OTHER)) {
        build_error_type = BuildError.INFO;
      }
      else {
        build_error_type = BuildError.ERROR;
      }
      // The file location type,
      JavaFileObject dm_source = dm.getSource();
      String file_loc = (dm_source == null) ? null : dm_source.toString();

      BuildError build_error = 
              new BuildError(dm.getMessage(null), null, build_error_type,
                             file_loc,
                             dm.getLineNumber(), dm.getColumnNumber());
      errors.add(build_error);

//      result_out.println(dm.getMessage(null));
//      long line = dm.getLineNumber();
//      if (line >= 0) {
//        result_out.println("Line: " + dm.getLineNumber());
//      }
    }

    return compile_success;

  }

}
