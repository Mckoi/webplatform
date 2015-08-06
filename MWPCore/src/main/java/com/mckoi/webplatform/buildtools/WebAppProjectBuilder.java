/**
 * com.mckoi.webplatform.buildtools.WebAppProjectBuilder  Apr 12, 2011
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
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.util.StyledPrintUtil;
import com.mckoi.util.StyledPrintWriter;
import com.mckoi.webplatform.BuildError;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.impl.PlatformContextImpl;
import com.mckoi.webplatform.jasper.servlet.MckoiJasperOptions;
import com.mckoi.webplatform.util.HttpUtils;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagInfo;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JarResource;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.servlet.JspCServletContext;
import org.apache.jasper.servlet.JspServletWrapper;

/**
 * Provides a simple project builder mechanism that compiles the Java files
 * in a project and generates a binary (Java bytecode) version in a build
 * directory. The project source code directory is arranged in a similar way
 * to a Maven project. A typical layout of the project directory follows;
 * <p>
 * <pre>
 *   project/
 *
 *     src/
 *       main/
 *         java/
 *           com/myproject/MyServlet.java
 *         webapp/
 *           WEB-INF/
 *             web.xml
 *           index.html
 *       test/
 *
 *     lib/
 *       mylib.jar
 *
 *     build/
 *       WEB-INF/
 *         web.xml
 *         classes/
 *           com/myproject/MyServlet.class
 *         lib/
 *           mylib.jar
 *       index.html
 * </pre>
 * <p>
 * This build tool is significantly different to Maven however. It will not
 * build to a JAR/WAR file, rather leave the build directory exploded.
 * <p>
 * In addition, there is currently no way to customize the project directory
 * structure, or does it provide a library dependency mechanism.
 *
 * @author Tobias Downer
 */

public class WebAppProjectBuilder {

  /**
   * The XML page for the initial project. Defines a single Servlet called
   * 'Hello' -> myproject.HelloServlet.
   */
  private static final String EXAMPLE_WEB_XML =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
    "<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n" +
    "    <servlet>\n" +
    "        <servlet-name>Hello</servlet-name>\n" +
    "        <servlet-class>myproject.HelloServlet</servlet-class>\n" +
    "    </servlet>\n" +
    "    <servlet-mapping>\n" +
    "        <servlet-name>Hello</servlet-name>\n" +
    "        <url-pattern>/Hello</url-pattern>\n" +
    "    </servlet-mapping>\n" +
    "    <session-config>\n" +
    "        <session-timeout>\n" +
    "            30\n" +
    "        </session-timeout>\n" +
    "    </session-config>\n" +
    "</web-app>\n" +
    "";

  /**
   * The simple 'HelloServlet' example Servlet.
   */
  private static final String EXAMPLE_HELLO_SERVLET =
    "package myproject;\n" +
    "\n" +
    "import java.io.IOException;\n" +
    "import java.io.PrintWriter;\n" +
    "import javax.servlet.ServletException;\n" +
    "import javax.servlet.http.Cookie;\n" +
    "import javax.servlet.http.HttpServlet;\n" +
    "import javax.servlet.http.HttpServletRequest;\n" +
    "import javax.servlet.http.HttpServletResponse;\n" +
    "\n" +
    "/**\n" +
    " * Hello World Servlet\n" +
    " */\n" +
    "\n" +
    "public class HelloServlet extends HttpServlet {\n" +
    "\n" +
    "  protected void processRequest(HttpServletRequest request, HttpServletResponse response)\n" +
    "  throws ServletException, IOException {\n" +
    "\n" +
    "    response.setContentType(\"text/html;charset=UTF-8\");\n" +
    "    PrintWriter out = response.getWriter();\n" +
    "    try {\n" +
    "      out.println(\"<h1>Hello Servlet!</h1>\");\n" +
    "    }\n" +
    "    finally {\n" +
    "      out.close();\n" +
    "    }\n" +
    "\n" +
    "  }\n" +
    "\n" +
    "  /**\n" +
    "   * Handles the HTTP <code>GET</code> method.\n" +
    "   * @param request servlet request\n" +
    "   * @param response servlet response\n" +
    "   * @throws ServletException if a servlet-specific error occurs\n" +
    "   * @throws IOException if an I/O error occurs\n" +
    "   */\n" +
    "  @Override\n" +
    "  protected void doGet(HttpServletRequest request, HttpServletResponse response)\n" +
    "  throws ServletException, IOException {\n" +
    "    processRequest(request, response);\n" +
    "  }\n" +
    "\n" +
    "  /**\n" +
    "   * Handles the HTTP <code>POST</code> method.\n" +
    "   * @param request servlet request\n" +
    "   * @param response servlet response\n" +
    "   * @throws ServletException if a servlet-specific error occurs\n" +
    "   * @throws IOException if an I/O error occurs\n" +
    "   */\n" +
    "  @Override\n" +
    "  protected void doPost(HttpServletRequest request, HttpServletResponse response)\n" +
    "  throws ServletException, IOException {\n" +
    "    processRequest(request, response);\n" +
    "  }\n" +
    "\n" +
    "  /**\n" +
    "   * Returns a short description of the servlet.\n" +
    "   * @return a String containing servlet description\n" +
    "   */\n" +
    "  @Override\n" +
    "  public String getServletInfo() {\n" +
    "    return \"Hello Servlet\";\n" +
    "  }\n" +
    "\n" +
    "}\n";

  private static void writeFile(FileRepository repository,
                   String file_name, String file_content,
                   StyledPrintWriter result_out) throws IOException {

    String mime_type;

    if (file_name.endsWith(".java")) {
      mime_type = "text/x-java";
    }
    else if(file_name.endsWith(".xml")) {
      mime_type = "application/xml";
    }
    else {
      throw new RuntimeException("Unknown file type: " + file_name);
    }

    // Does the file already exist?
    DataFile df = repository.getDataFile(file_name);
    if (df == null) {
      // Create the file,
      repository.createFile(file_name, mime_type, System.currentTimeMillis());
      df = repository.getDataFile(file_name);
      Writer w = new OutputStreamWriter(
                                    DataFileUtils.asOutputStream(df), "UTF8");
      w.append(file_content);
      w.flush();
    }
    // File exists,
    else {
      result_out.println("File " + file_name + " already exists.");
    }
  }

  /**
   * Recursive method that searches the given path in the repository for
   * all files with 'file_exts' extension (eg. ".java"), and adds them to the
   * list.
   */
  private static void recurseAddSource(String[] file_exts, List<String> list,
                                     FileRepository repository, String path) {

    {
      // For all the files,
      List<FileInfo> file_list = repository.getFileList(path);
      if (file_list != null) {
        for (FileInfo file : file_list) {
          // Add to the output list if it's a java file,
          // PENDING: Check mime type?
          String file_name = file.getItemName();
          for (String file_ext : file_exts) {
            if (file_name.endsWith(file_ext)) {
              list.add(file.getAbsoluteName());
            }
          }
        }
      }
    }

    {
      // Recurse over the sub-directories,
      List<FileInfo> dir_list = repository.getSubDirectoryList(path);
      if (dir_list != null) {
        for (FileInfo dir : dir_list) {
          recurseAddSource(file_exts, list, repository, dir.getAbsoluteName());
        }
      }
    }

  }

  /**
   * Recursively copies all the files and folders to the destination path.
   */
  private static void copyDirectory(FileRepository repository,
                                    String from_path, String to_path,
                                    Set<String> touched_files) {
    {
      // For all the files,
      List<FileInfo> file_list = repository.getFileList(from_path);
      if (file_list != null) {
        for (FileInfo src_file : file_list) {
          // The file name of the source and destination file of the copy,
          String item_name = src_file.getItemName();
          String dest_file = to_path + item_name;

          // Add to the touched file set,
          touched_files.add(dest_file);

          boolean do_copy;

          FileInfo dest_file_info = repository.getFileInfo(dest_file);
          // If the destination file exists,
          if (dest_file_info != null) {
            // Only do the copy if it's different,
            long src_last_modified = src_file.getLastModified();
            long src_sz = src_file.getDataFile().size();
            long dest_last_modified = dest_file_info.getLastModified();
            long dest_sz = dest_file_info.getDataFile().size();
            // If the size and last_modified timestamp are the same, we don't
            // bother to copy,
            if (src_last_modified == dest_last_modified &&
                src_sz == dest_sz) {
              do_copy = false;
            }
            // Otherwise delete the destination file and perform the copy,
            else {
              // PENDING: Do a difference copy?
              repository.deleteFile(dest_file);
              do_copy = true;
            }
          }
          // Destination file doesn't exist so we copy this,
          else {
            do_copy = true;
          }

          if (do_copy) {
            repository.copyFile(from_path + item_name, dest_file);
          }
        }
      }
    }

    {
      // Recurse this function over the sub-directories,
      List<FileInfo> dir_list = repository.getSubDirectoryList(from_path);
      if (dir_list != null) {
        for (FileInfo dir : dir_list) {
          String item_name = dir.getItemName();
          copyDirectory(repository,
                        from_path + item_name, to_path + item_name,
                        touched_files);
        }
      }
    }
  }

  /**
   * For all files in the given directory and sub-directories, if the file
   * is not in the 'touched_files' collection then it is deleted.
   */
  private static void deleteUntouchedFiles(
          FileRepository repository, String path, Set<String> touched_files) {

    {
      // For all the files,
      List<FileInfo> file_list = repository.getFileList(path);
      if (file_list != null) {
        ArrayList<String> to_delete = new ArrayList();
        for (FileInfo file : file_list) {
          // If the touched set doesn't contain this file,
          String ab_name = file.getAbsoluteName();
          if (!touched_files.contains(ab_name)) {
            // Delete it,
            to_delete.add(ab_name);
          }
        }

        // The list of files to delete,
        for (String ab_name : to_delete) {
          repository.deleteFile(ab_name);
        }
      }
    }

    {
      // Recurse over the sub-directories,
      List<FileInfo> dir_list = repository.getSubDirectoryList(path);
      if (dir_list != null) {
        for (FileInfo dir : dir_list) {
          deleteUntouchedFiles(
                            repository, dir.getAbsoluteName(), touched_files);
        }
      }
    }

  }

  /**
   * Convenience for handling a build exception.
   */
  private static void exceptionHelper(Exception e,
          JspCServletContext context,
          String relative_jsp_name,
          List<BuildError> errors) {

//    e.printStackTrace(result_out);
    String filename;
    try {
      URL resource = context.getResource(relative_jsp_name);
      if (resource == null) {
        filename = relative_jsp_name;
      }
      else {
        filename = resource.getPath();
      }
    }
    catch (MalformedURLException ex) {
      filename = relative_jsp_name;
    }
    errors.add(new BuildError(e.getMessage(), e, BuildError.ERROR,
                              filename, -1, -1));
  }


//  /**
//   * Compiles a .jsp file into a .java file and writes it to the given
//   * destination path in the file repository.
//   *
//   * @param jsp_name the name of the JSP file (eg. "index.jsp").
//   * @param jsp_file_name is the absolute name of the .jsp file in the file
//   *   repository.
//   * @param destination_path is the location to write the generated .java
//   *   file(s).
//   * @param result_out used to output debugging information.
//   * @param errors any build errors are put here.
//   * @returns true if the generation was successful.
//   */
//  public static boolean generateJavaFromJsp(
//          JSPState jsp_state,
//          FileRepository repository,
//          String relative_jsp_name,
//          String destination_path,
//          PrintWriter result_out, List<BuildError> errors) {
//
////    System.out.println("Compiling:");
////    System.out.println("  " + relative_jsp_name);
////    System.out.println("> " + destination_path);
//
//    JspCServletContext context = jsp_state.context;
//
//    MWPJspCompilationContext clctxt = new MWPJspCompilationContext(
//            relative_jsp_name, false, jsp_state, context,
//            null, jsp_state.rctxt );
//
//    // Set the output directory. We work it out from the java file returned
//    // by the context.
//    String java_path = clctxt.getJavaPath();
//    String package_path =
//                      java_path.substring(0, java_path.lastIndexOf("/") + 1);
//
//    // Sets the output directory,
//    clctxt.setOutputDir(destination_path + package_path);
//
//    // Create a new compiler instance,
//    ProjectJSPCompiler compiler = new ProjectJSPCompiler(repository);
//    compiler.init(clctxt, null);
//
//    try {
//      compiler.compile(false, true);
//    }
//    catch (FileNotFoundException e) {
//      exceptionHelper(e, context, relative_jsp_name, result_out, errors);
//      return false;
//    }
//    catch (JasperException e) {
//      exceptionHelper(e, context, relative_jsp_name, result_out, errors);
//      return false;
//    }
//    catch (Exception e) {
//      exceptionHelper(e, context, relative_jsp_name, result_out, errors);
//      return false;
//    }
//
//    // Otherwise compile successful,
//    return true;
//
//  }



  /**
   * Creates a new project directory structure at the given path with a very
   * basic web application.
   * <p>
   * Returns true if the project was successfully created, false if the
   * project could not be created.
   * The repository must be committed for any changes to be made permanent.
   */
  public static boolean createProject(
                   FileRepository repository, String project_path,
                   StyledPrintWriter result_out) {

    if (!project_path.endsWith("/")) {
      project_path = project_path + "/";
    }

    try {

      result_out.println("Building project directory");

      repository.makeDirectory(project_path + "src/main/java/");
      repository.makeDirectory(project_path + "src/main/java/myproject/");
      repository.makeDirectory(project_path + "src/main/webapp/");
      repository.makeDirectory(project_path + "src/main/webapp/WEB-INF/");
      repository.makeDirectory(project_path + "src/test/");
      repository.makeDirectory(project_path + "lib/");

      result_out.println("Adding simple web application");

      // Create and add the HelloServlet.java file to the project directory
      String hello_servlet_file =
              project_path + "src/main/java/myproject/HelloServlet.java";
      writeFile(repository, hello_servlet_file, EXAMPLE_HELLO_SERVLET,
                result_out);
      // Create the default web.xml file,
      String web_xml_file =
              project_path + "src/main/webapp/WEB-INF/web.xml";
      writeFile(repository, web_xml_file, EXAMPLE_WEB_XML,
                result_out);

      // Done
      return true;

    }
    catch (IOException e) {
      result_out.println("IO Exception: " + e.getMessage());
      result_out.printException(e);
      return false;
    }

  }

  /**
   * Builds an entire project from the source files. This must be run under a
   * security manager that permits the operations needed for the compilation
   * process.
   * <p>
   * Returns true if the project was successfully built, false otherwise.
   * The repository must be committed for any changes to be made permanent.
   */
  public static boolean buildProject(
                   final FileRepository repository,
                   String project_path,
                   StyledPrintWriter result_out, List<BuildError> errors) {

    // The list of JSP pages generated during the build,
    ArrayList<String> generated_jsp_pages = new ArrayList();

    return buildProjectImpl(repository, project_path,
                            result_out, errors,
                            generated_jsp_pages,
                            "build/",              // target_build_path
                            "src/main/java/",      // src_java_path
                            "src/main/webapp/",    // src_webapp_path
                            true                   // compile_src_java
            );

  }

  /**
   * Builds the JSP files in a project's 'binary' directory. This is necessary
   * to get JSP binaries in an uploaded web application.
   * <p>
   * Returns true if the project's JSP files were successfully built, false
   * otherwise. The repository must be committed for any changes to be made
   * permanent.
   */
  public static boolean buildProjectJSP(
                   final FileRepository repository,
                   String project_path,
                   StyledPrintWriter result_out, List<BuildError> errors) {

    // The list of JSP pages generated during the build,
    ArrayList<String> generated_jsp_pages = new ArrayList();

    return buildProjectImpl(repository, project_path,
                            result_out, errors,
                            generated_jsp_pages,
                            "",                  // target_build_path
                            null,                // src_java_path
                            "",                  // src_webapp_path
                            false                // compile_src_java
            );

  }

  /**
   * Returns false if 'src_path' is null or the directory exists, false
   * if the directory exists.
   */
  private static boolean directoryExists(
            FileRepository repository, String project_path, String src_path) {

    if (src_path == null) {
      return true;
    }
    FileInfo file_info = repository.getFileInfo(project_path + src_path);
    return (file_info != null && file_info.isDirectory());

  }

  /**
   * Builds a project from the source files. This must be run under a security
   * manager that permits the operations needed for the compilation process.
   * <p>
   * 'target_build_path' is the target directory for the compiled files (eg.
   * build/). 'src_java_path' is the location of the Java code for the
   * project (eg. src/main/java/). 'src_webapp_path' is the location of the
   * web application source files (eg. src/main/webapp/).
   * <p>
   * Returns true if the project was successfully built, false otherwise.
   * The repository must be committed for any changes to be made permanent.
   */
  private static boolean buildProjectImpl(
                   final FileRepository repository,
                   String project_path,
                   StyledPrintWriter result_out, List<BuildError> errors,
                   List<String> generated_jsp_pages,
                   String target_build_path,
                   String src_java_path,
                   String src_webapp_path,
                   boolean compile_src_java) {

    if (!project_path.endsWith("/")) {
      project_path = project_path + "/";
    }

    // Check the src directories exist,
    if (!directoryExists(repository, project_path, src_java_path) ||
        !directoryExists(repository, project_path, src_webapp_path)) {
      result_out.println("Source directory not found", "error");
      return false;
    }

    // If same source and target
    boolean same_src_and_target = src_webapp_path.equals(target_build_path);

    // The set of all touched files,
    HashSet<String> all_touched_files = new HashSet();

    // The jsp-web.xml file contains references to the jsp files,
    StringBuilder jsp_web_xml = new StringBuilder();
    jsp_web_xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    jsp_web_xml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
    StringBuilder jsp_web_mappings = new StringBuilder();

    // Assign a MWPFS repository id for this repository,
    String repository_id =
                 AccessController.doPrivileged(new PrivilegedAction<String>() {
      @Override
      public String run() {
        PlatformContextImpl mckoi_context = new PlatformContextImpl();
        return mckoi_context.assignMWPFSURLFileRepository(repository);
      }
    });


    // Make a temporary class loader for this app,
    // The data that this class loader will show will include all the files
    // we are currently compiling,
    ClassLoader context_cl =
           PlatformContextFactory.getPlatformContext().getContextClassLoader();
    TemporaryAppClassLoader temp_app_cl =
            new TemporaryAppClassLoader(context_cl, repository_id, repository);
    temp_app_cl.addClassPathDir(project_path + target_build_path + "WEB-INF/classes/");
    temp_app_cl.addClassPathLibs(project_path + target_build_path + "WEB-INF/lib/");


    // Create the project compiler,
    ProjectCompiler compiler = new ProjectCompiler(
                         repository_id, repository, project_path, temp_app_cl);

    // The source and output directory for the .java and .class files,
    if (compile_src_java) {
      compiler.addSourceDirectory(src_java_path);
    }
    // The output directory
    compiler.setOutputDirectory(target_build_path + "WEB-INF/classes/");

    // Create a list of items to compile,
    ArrayList<String> to_compile = new ArrayList();
    if (compile_src_java) {
      recurseAddSource(JAVA_EXT, to_compile, repository,
                       project_path + src_java_path);
    }

    // Set the class path
    ArrayList<String> cp = new ArrayList();
    // Add .jar files in the '/lib' dir and the WAR 'WEB-INF/lib' dir to the
    // compiler class path.
    recurseAddSource(JAR_EXT, cp, repository,
                     project_path + "lib/");
    recurseAddSource(JAR_EXT, cp, repository,
                     project_path + src_webapp_path + "WEB-INF/lib/");
    compiler.addToClassPath(cp);

    boolean compile_success;
    if (!to_compile.isEmpty()) {
      // Compile the code,
      compiler.setInputSourceFiles(to_compile);
      compile_success = compiler.compile(result_out, errors);
    }
    else {
      // No sources to compile results in success,
      compile_success = true;
    }

    // If successful, now compile the JSP stuff,
    if (!compile_success) {
      return false;
    }

    // Copy the content of the webapp folder,
    if (!same_src_and_target) {
      copyDirectory(repository, project_path + src_webapp_path,
                            project_path + target_build_path,
                    all_touched_files);
      // Copy the libraries,
      copyDirectory(repository, project_path + "lib/",
                            project_path + target_build_path + "WEB-INF/lib/",
                    all_touched_files);
    }

    // Clear the 'to_compile' list,
    to_compile.clear();

    // Find and copy all the jsp pages into the build directory,
    // Turn any .jsp and .jspx files into .java files to be compiled with the
    // rest of the project.

    String build_path = project_path + target_build_path;

    // The URL to the document base,
    URL docbase_uri;
    try {
      docbase_uri = new URL(
              "mwpfs", null, -1, "/" + repository_id + build_path);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    // Wrap a print writer,
    PrintWriter pw = new PrintWriter(StyledPrintUtil.wrapWriter(result_out));
    JspCServletContext context =
                       new CompilerServletContext(repository, pw, docbase_uri);

    MckoiJasperOptions jsp_state = new MckoiJasperOptions(null, context);
    jsp_state.setupForPrecompilation();
    // Create a runtime context,
    JspRuntimeContext rctxt = new JspRuntimeContext(context, jsp_state);


    ArrayList<String> jsp_pages = new ArrayList();
    recurseAddSource(JSP_EXT, jsp_pages, repository, build_path);

    if (!jsp_pages.isEmpty()) {

      // Ignore any jsp pages in the WEB-INF directory (these don't need to
      // be compiled but they may still be used as includes, etc).
      Iterator<String> i = jsp_pages.iterator();
      while (i.hasNext()) {
        String jsp_filename = i.next();
        String relative = jsp_filename.substring(build_path.length());
        // If the file starts with WEB-INF then remove it from the list,
        if (relative.startsWith("WEB-INF/")) {
          i.remove();
        }
        else {
          generated_jsp_pages.add(relative);
        }
      }

      // The destination path of compiled JSP pages as .java files,
      String jsp_dest_path = project_path + target_build_path + "WEB-INF/classes/";




      for (String jsp_filename : generated_jsp_pages) {

        // The jsp file name relative to the doc base,
        final String relative_jsp_name = "/" + jsp_filename;

        MWPJspCompilationContext clctxt = new MWPJspCompilationContext(
                temp_app_cl,
                relative_jsp_name, jsp_state, context,
                null, rctxt );

        // Set the output directory. We work it out from the java file returned
        // by the context.
        String java_path = clctxt.getJavaPath();
        String package_path =
                          java_path.substring(0, java_path.lastIndexOf("/") + 1);

        // Sets the output directory,
        clctxt.setOutputDir(jsp_dest_path + package_path);

        // Create a new compiler instance,
        ProjectJSPCompiler jsp_compiler = new ProjectJSPCompiler(repository);
        clctxt.setJspCompiler(jsp_compiler);
        jsp_compiler.init(clctxt, null);

        boolean jsp_compile_success = false;
        try {
          jsp_compiler.compile(false, true);
          jsp_compile_success = true;
        }
        catch (FileNotFoundException e) {
          exceptionHelper(e, context, relative_jsp_name, errors);
          String servlet_file_name = clctxt.getServletJavaFileName();
          repository.deleteFile(servlet_file_name);
//          return false;
        }
        catch (JasperException e) {
          exceptionHelper(e, context, relative_jsp_name, errors);
          String servlet_file_name = clctxt.getServletJavaFileName();
          repository.deleteFile(servlet_file_name);
//          return false;
        }
        catch (Exception e) {
          result_out.printException(e);
          exceptionHelper(e, context, relative_jsp_name, errors);
          return false;
        }

        // Update the touched files set,
        all_touched_files.addAll(jsp_compiler.getTouchedFiles());

        if (jsp_compile_success) {

          String servlet_name = clctxt.getServletClassName();
          String servlet_class =
                          clctxt.getServletPackageName() + "." + servlet_name;

          // Add a servlet entry to the jsp-web.xml file,
          jsp_web_xml.append("  <servlet>\n");
          jsp_web_xml.append("    <servlet-name>");
          jsp_web_xml.append(servlet_name);
          jsp_web_xml.append("</servlet-name>\n");
          jsp_web_xml.append("    <servlet-class>");
          jsp_web_xml.append(servlet_class);
          jsp_web_xml.append("</servlet-class>\n");
          jsp_web_xml.append("  </servlet>\n");

          // Servlet mappings,
          jsp_web_mappings.append("  <servlet-mapping>\n");
          jsp_web_mappings.append("    <servlet-name>");
          jsp_web_mappings.append(servlet_name);
          jsp_web_mappings.append("</servlet-name>\n");
          jsp_web_mappings.append("    <url-pattern>");
          jsp_web_mappings.append(relative_jsp_name);
          jsp_web_mappings.append("</url-pattern>\n");
          jsp_web_mappings.append("  </servlet-mapping>\n");

        }

      }

      // The purpose of this is to compile any generated JSP pages which are
      // generated in the WEB-INF/classes/ sub-directory.
      recurseAddSource(JAVA_EXT, to_compile, repository,
                       project_path + target_build_path + "WEB-INF/classes/");
      // The purpose of this is to compile any generated JSP pages which are
      // generated in the WEB-INF/classes/ sub-directory.
      compiler.addSourceDirectory(target_build_path + "WEB-INF/classes/");

      if (!to_compile.isEmpty()) {
        // Compile the JSP code,
        compiler.setInputSourceFiles(to_compile);
        compile_success = compiler.compile(result_out, errors);
      }
    }

    // Form the web.xml file,

    jsp_web_xml.append(jsp_web_mappings);
    jsp_web_xml.append("</web-app>\n");

    // Was the compile successful?
    if (compile_success) {

      // Update the set of all touched files,
      all_touched_files.addAll(compiler.getAllTouchedFiles());

      // Write out the mwp_jsp_web.xml file,
      String jsp_web_fname = project_path + target_build_path + "WEB-INF/mwp_jsp_web.xml";
      FileInfo finfo = repository.getFileInfo(jsp_web_fname);
      if (finfo == null) {
        repository.createFile(jsp_web_fname, "application/xml",
                              System.currentTimeMillis());
        finfo = repository.getFileInfo(jsp_web_fname);
      }
      all_touched_files.add(jsp_web_fname);
      OutputStream out =
              DataFileUtils.asSimpleDifferenceOutputStream(finfo.getDataFile());
      try {
        // Write out as UTF-8 encoding,
        OutputStreamWriter wout = new OutputStreamWriter(out, "UTF-8");
        wout.append(jsp_web_xml);
        wout.flush();
        wout.close();
      }
      catch (IOException e) {
        result_out.printException(e);
        return false;
      }

      // Recursive method that deletes any files not touched by the build
      // process,
      if (!same_src_and_target) {
        deleteUntouchedFiles(repository,
                          project_path + target_build_path, all_touched_files);
      }

      return true;
    }

    return false;

  }

  private static final String[] JAVA_EXT = new String[] { ".java" };
  private static final String[] JSP_EXT = new String[] { ".jsp", ".jspx" };
  private static final String[] JAR_EXT = new String[] { ".jar" };


  // -----

  /**
   * The context that fetches the compiled resources.
   */
  private static class CompilerServletContext extends JspCServletContext {

    // The backed repository,
    private final FileRepository repository;

    public CompilerServletContext(FileRepository repository,
                                  PrintWriter log_writer, URL base_url) {
      super(log_writer, base_url);
      
      this.repository = repository;
    }

    @Override
    public String getRealPath(String path) {
      return null;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
      return super.getResource(path);
    }

    @Override
    public InputStream getResourceAsStream(String path) {
      // PENDING: Could we make this more efficient by returning a stream to
      //   the DataFile itself?
      return super.getResourceAsStream(path);
    }

    @Override
    public Set<String> getResourcePaths(String path) {
      String dir = HttpUtils.decodeURLFileName(myResourceBaseURL);

      // Make sure the input 'path' is a directory spec,
      if (!path.endsWith("/")) {
        path = path + "/";
      }

      // Strip the starting '/' if there is one
      String norm_path = path;
      if (norm_path.startsWith("/")) {
        norm_path = norm_path.substring(1);
      }
      // Qualify,
      final String qual_path = new FileName(dir + norm_path).getPathFile();

      HashSet<String> resources = new HashSet();

      // Query the repository for the file/directory lists,
      List<FileInfo> file_list = repository.getFileList(qual_path);
      if (file_list != null) {
        for (FileInfo fi : file_list) {
          resources.add(path + fi.getItemName());
        }
      }
      List<FileInfo> dir_list = repository.getSubDirectoryList(qual_path);
      if (dir_list != null) {
        for (FileInfo fi : dir_list) {
          resources.add(path + fi.getItemName());
        }
      }

      return resources;

    }

  }

//  /**
//   * Contains various state for the jsp compiler.
//   */
//  public final static class JSPState implements Options {
//
//    /**
//     * The JSP class ID for Internet Exploder.
//     */
//    public static final String DEFAULT_IE_CLASS_ID =
//            "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";
//
//    /**
//     * Cache for the TLD locations
//     */
//    protected TldLocationsCache tldLocationsCache = null;
//
//    protected JspCServletContext context;
//    protected JspRuntimeContext rctxt;
//    protected JspConfig jspConfig;
//    protected TagPluginManager tagPluginManager;
//
//    protected String compilerTargetVM = "1.6";
//    protected String compilerSourceVM = "1.6";
//
//    protected String javaEncoding = "UTF-8";
//
//    protected boolean smapSuppressed = true;
//    protected boolean smapDumped = false;
//    protected boolean caching = true;
//    protected Map cache = new HashMap();
//    protected String ieClassId = DEFAULT_IE_CLASS_ID;
//    protected boolean trimSpaces = false;
//    protected boolean genStringAsCharArray = false;
//    protected boolean xpoweredBy;
//    protected boolean mappedFile = false;
//    protected boolean poolingEnabled = true;
//    protected String classPath = null;
//    protected String compiler = null;
//    protected boolean classDebugInfo = true;
//    protected boolean errorOnUseBeanInvalidClassAttribute = true;
//
//    protected File scratchDir = null;
//
//
//    /**
//     * Create an EmbeddedServletOptions object using data available from
//     * ServletConfig and ServletContext. 
//     */
//    public JSPState(ServletConfig config, ServletContext context) {
//
//      tldLocationsCache = TldLocationsCache.getInstance(context);
//      rctxt = new JspRuntimeContext(context, this);
//      jspConfig = new JspConfig(context);
//      tagPluginManager = new TagPluginManager(context);
//
//    }
//
//
//    static JSPState createJSPState(FileRepository repository,
//                                   String repository_id, String docbase_path,
//                                   StyledPrintWriter log_out) {
//      
//      try {
//
//        // The URL to the document base,
//        URL docbase_uri =
//                new URL("mwpfs", null, -1, "/" + repository_id + docbase_path);
//
//        // Wrap a print writer,
//        PrintWriter pw = new PrintWriter(StyledPrintUtil.wrapWriter(log_out));
//        ServletContext context = new JspCServletContext(pw, docbase_uri);
//        return new JSPState(null, context);
//
//      }
//      catch (MalformedURLException me) {
//        throw new RuntimeException(me);
//      }
//
//    }
//
//
////    void init(FileRepository repository,
////              String repository_id, String docbase_path,
////              StyledPrintWriter log_out) {
////      try {
////
////        // The URL to the document base,
////        URL docbase_uri =
////                new URL("mwpfs", null, -1, "/" + repository_id + docbase_path);
////
////        // Wrap a print writer,
////        PrintWriter pw = new PrintWriter(StyledPrintUtil.wrapWriter(log_out));
////        context = new JspCServletContext(pw, docbase_uri);
////
////      }
////      catch (MalformedURLException me) {
////        throw new RuntimeException(me);
////      }
////
////      tldLocationsCache = TldLocationsCache.getInstance(context);
////      rctxt = new JspRuntimeContext(context, this);
////      jspConfig = new JspConfig(context);
////      tagPluginManager = new TagPluginManager(context);
////
////    }
//
//    // ----- Implemented from Options -----
//
//    @Override
//    public boolean genStringAsCharArray() {
//      return genStringAsCharArray;
//    }
//
//    @Override
//    public Map getCache() {
//      return cache;
//    }
//
//    @Override
//    public int getCheckInterval() {
//      return 0;
//    }
//
//    @Override
//    public boolean getClassDebugInfo() {
//      return classDebugInfo;
//    }
//
//    @Override
//    public String getClassPath() {
//      if(classPath != null) return classPath;
//      // Get the Java class path,
//      String cp = AccessController.doPrivileged(new PrivilegedAction<String>() {
//        @Override
//        public String run() {
//          return System.getProperty("java.class.path");
//        }
//      });
//      
//      System.out.println("$$$ ClassPath = " + cp);
//      return cp;
//    }
//
//    @Override
//    public String getCompiler() {
//      return compiler;
//    }
//
//    @Override
//    public String getCompilerClassName() {
//      return null;
//    }
//
//    @Override
//    public String getCompilerSourceVM() {
//      return compilerSourceVM;
//    }
//
//    @Override
//    public String getCompilerTargetVM() {
//      return compilerTargetVM;
//    }
//
//    @Override
//    public boolean getDevelopment() {
//      return false;
//    }
//
//    @Override
//    public boolean getDisplaySourceFragment() {
//      return true;
//    }
//
//    @Override
//    public boolean getErrorOnUseBeanInvalidClassAttribute() {
//      return errorOnUseBeanInvalidClassAttribute;
//    }
//
//    @Override
//    public boolean getFork() {
//      return false;
//    }
//
//    @Override
//    public String getIeClassId() {
//      return ieClassId;
//    }
//
//    @Override
//    public String getJavaEncoding() {
//      return javaEncoding;
//    }
//
//    @Override
//    public JspConfig getJspConfig() {
//      return jspConfig;
//    }
//
//    @Override
//    public boolean getKeepGenerated() {
//      return true;
//    }
//
//    @Override
//    public boolean getMappedFile() {
//      return mappedFile;
//    }
//
//    @Override
//    public int getModificationTestInterval() {
//      return 0;
//    }
//
//    @Override
//    public boolean getRecompileOnFail() {
//      return false;
//    }
//
//    @Override
//    public File getScratchDir() {
//      return scratchDir;
//    }
//
////    @Override
////    public boolean getSendErrorToClient() {
////      return true;
////    }
//
//    @Override
//    public TagPluginManager getTagPluginManager() {
//      return tagPluginManager;
//    }
//
//    @Override
//    public TldLocationsCache getTldLocationsCache() {
//      return tldLocationsCache;
//    }
//
//    @Override
//    public boolean getTrimSpaces() {
//      return trimSpaces;
//    }
//
//    @Override
//    public boolean isCaching() {
//      return caching;
//    }
//
//    @Override
//    public boolean isPoolingEnabled() {
//      return poolingEnabled;
//    }
//
//    @Override
//    public boolean isSmapDumped() {
//      return smapDumped;
//    }
//
//    @Override
//    public boolean isSmapSuppressed() {
//      return smapSuppressed;
//    }
//
//    @Override
//    public boolean isXpoweredBy() {
//      return xpoweredBy;
//    }
//
//    @Override
//    public int getMaxLoadedJsps() {
//      return -1;
//    }
//
//    @Override
//    public int getJspIdleTimeout() {
//      return -1;
//    }
//
//  }

  /**
   * Custom modified JspCompilationContext.
   */
  private static class MWPJspCompilationContext extends JspCompilationContext {

    /**
     * The backed repository.
     */
    private final ClassLoader app_class_loader;

    private ProjectJSPCompiler t_jsp_compiler;


    public MWPJspCompilationContext(ClassLoader app_class_loader,
                                    String tagfile,
                                    TagInfo tagInfo,
                                    Options options,
                                    ServletContext context,
                                    JspServletWrapper jsw,
                                    JspRuntimeContext rctxt,
                                    JarResource tagJarResource) {
      super(tagfile, tagInfo, options, context, jsw, rctxt, tagJarResource);
      this.app_class_loader = app_class_loader;
    }

    public MWPJspCompilationContext(ClassLoader app_class_loader,
                                    String jspUri,
                                    Options options,
                                    ServletContext context,
                                    JspServletWrapper jsw,
                                    JspRuntimeContext rctxt) {
      super(jspUri, options, context, jsw, rctxt);
      this.app_class_loader = app_class_loader;
    }

    private void setJspCompiler(ProjectJSPCompiler jsp_compiler) {
      this.t_jsp_compiler = jsp_compiler;
    }

    /**
     * Sets the output directory.
     */
    public void setOutputDir(String output_dir) {
      this.outputDir = output_dir;
    }

    @Override
    public void checkOutputDir() {
      // Not necessary with our implementation,
    }

    @Override
    public ClassLoader getClassLoader() {
      // This must return a class loader that can access the compiled
      // directory,
      return app_class_loader;
    }

    @Override
    public org.apache.jasper.compiler.Compiler createCompiler() {
      return t_jsp_compiler;
    }

    @Override
    public org.apache.jasper.compiler.Compiler getCompiler() {
      return t_jsp_compiler;
    }

    
    

//    @Override
//    public String getOutputDir() {
//      return super.getOutputDir();
//    }

//    @Override
//    public URL getResource(String res) throws MalformedURLException {
//      System.out.println("URL: getResource(String: " + res + ")");
//      URL url = super.getResource(res);
//      System.out.println("-> URL: " + url);
//      return url;
//    }
//
//    @Override
//    public InputStream getResourceAsStream(String res) {
//      System.out.println("InputStream: getResourceAsStream(String: " + res + ")");
//      InputStream is = super.getResourceAsStream(res);
//      System.out.println("-> InputStream: " + is);
//      return is;
//    }



  }






  /**
   * The JSP compiler translates a .jsp page into a .java file.
   */
  private static class ProjectJSPCompiler
                                  extends org.apache.jasper.compiler.Compiler {

    /**
     * The list of files this compiler touches.
     */
    private HashSet<String> touched_files = new HashSet();

    /**
     * The FileRepository.
     */
    private FileRepository file_repository;

    ProjectJSPCompiler(FileRepository repository) {
      this.file_repository = repository;
    }

    /**
     * Returns the set of touched files.
     */
    Set<String> getTouchedFiles() {
      return touched_files;
    }

    /**
     * Returns an output stream to write the content of a servlet file.
     */
    @Override
    public OutputStream getServletFileOutputStream(String javaFileName)
                                                 throws FileNotFoundException {

      touched_files.add(javaFileName);

      FileInfo file_info = file_repository.getFileInfo(javaFileName);
      if (file_info == null) {
        file_repository.createFile(javaFileName,
                                   "text/x-java", System.currentTimeMillis());
        file_info = file_repository.getFileInfo(javaFileName);
      }
      else {
        file_repository.touchFile(javaFileName, System.currentTimeMillis());
      }

      return DataFileUtils.asSimpleDifferenceOutputStream(
                                                     file_info.getDataFile());
    }

    /**
     * This is not supported in this implementation (the compilation of the
     * .java file is a different process.
     */
    @Override
    protected void generateClass(String[] smap)
                     throws FileNotFoundException, JasperException, Exception {
      throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void removeGeneratedFiles() {
      // We overwrite these because they use java.io.File to delete from the
      // local filesystem.
      throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void removeGeneratedClassFiles() {
      // We overwrite these because they use java.io.File to delete from the
      // local filesystem.
      throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public boolean isOutDated() {
      return false;
    }

    @Override
    public boolean isOutDated(boolean checkClass) {
      return false;
    }

  }

  /**
   * A class loader that looks a lot like the class loader used to execute the
   * application being compiled, except it has access to the files currently
   * being compiled.
   */
  private static class TemporaryAppClassLoader extends URLClassLoader {

    private final String repository_id;
    private final FileRepository repository;

    TemporaryAppClassLoader(ClassLoader parent,
                            String repository_id, FileRepository repository) {
      super(new URL[0], parent);
      this.repository_id = repository_id;
      this.repository = repository;
    }

    private void addClassPathItem(String item) {
      try {
        URL uri = new URL("mwpfs", null, -1, "/" + repository_id + item);
        // Add the URL,
        addURL(uri);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Adds a directory to the class path.
     */
    private void addClassPathDir(String dir) {
      addClassPathItem(dir);
    }

    /**
     * Adds all the .jar files in the given directory to the class path.
     */
    private void addClassPathLibs(String dir) {
      // Get all the files in the subdirectory,
      List<FileInfo> file_list = repository.getFileList(dir);
      if (file_list != null) {
        for (FileInfo finfo : file_list) {
          if (finfo.isFile()) {
            String name = finfo.getItemName();
            if (name.endsWith(".jar") || name.endsWith(".zip")) {
              addClassPathItem(finfo.getAbsoluteName());
            }
          }
        }
      }
    }

  }

}
