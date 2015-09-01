/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mckoi.mwpui.servlets;

import com.mckoi.appcore.UserApplicationsSchema;
import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.mwpui.apihelper.MJSONWriter;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.util.DirectorySynchronizer;
import com.mckoi.odb.util.DirectorySynchronizer.MckoiRepository;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.odb.util.SynchronizerFile;
import com.mckoi.webplatform.BuildSystem;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.LogSystem;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.UserManager;
import com.mckoi.webplatform.util.LogUtils;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.*;

/**
 * General system support servlet.
 *
 * @author Tobias Downer
 */
public class SystemSupportServlet extends HttpServlet {

  /**
   * The async attribute key.
   */
  private static String CONSUME_STATUS_KEY =
              "com.mckoi.mwpui.servlets.SystemSupportServlet.consume_status";

  /**
   * Key value that indicates the auth wait period has ended.
   */
  private static String CONSUME_STATUS_FAILWAITEND = "waitend";

  /**
   * The JSON writer.
   * 
   * @param response
   * @return
   * @throws IOException 
   */
  private static MJSONWriter getJSONWriter(HttpServletResponse response)
                                                          throws IOException {
    ServletOutputStream sout = response.getOutputStream();
    return new MJSONWriter(new BufferedWriter(
                      new OutputStreamWriter(sout, "UTF-8")));
  }

  /**
   * Handles asynchronous dispatch.
   * 
   * @param request
   * @param response
   * @throws IOException
   * @throws JSONException 
   */
  private void handleAsyncDispatch(PlatformContext ctx,
          HttpServletRequest request, HttpServletResponse response)
                                          throws IOException, JSONException {

    Object consume_status_key = request.getAttribute(CONSUME_STATUS_KEY);
    // Handle the delayed failure,
    if (consume_status_key.equals(CONSUME_STATUS_FAILWAITEND)) {
      response.setContentType("text/plain;charset=UTF-8");
      MJSONWriter writer = getJSONWriter(response);
      writer.object();
      writer.key("auth").value("FAILED");
      writer.endObject();

      writer.flush();
      writer.close();
    }

  }

  /**
   * Uses servlet 3.0 async feature to wait ~ a second before returning
   * a failure message. The idea behind this is that it makes brute force
   * user/password attempts a little more time consuming and prevents
   * timing attacks.
   * 
   * @param request 
   */
  private void dispatchDelayedAuthFailure(HttpServletRequest request) {
    AsyncContext async_context = request.startAsync();
    async_context.addListener(new AsyncListener() {
      @Override
      public void onComplete(AsyncEvent event) throws IOException {
      }
      @Override
      public void onTimeout(AsyncEvent event) throws IOException {
        // Dispatch,
        ServletRequest request = event.getSuppliedRequest();
        request.setAttribute(
                    CONSUME_STATUS_KEY, CONSUME_STATUS_FAILWAITEND);
        event.getAsyncContext().dispatch();
      }
      @Override
      public void onError(AsyncEvent event) throws IOException {
        onTimeout(event);
      }
      @Override
      public void onStartAsync(AsyncEvent event) throws IOException {
      }
    });
    // Set timeout to a random time (between 200 and 2200 ms)
    async_context.setTimeout(200 + (int) (Math.random() * 2000d));
  }

  /**
   * Recursively fetch files and read and output their timestamp.
   * 
   * @param writer
   * @param fs
   * @param path 
   */
  private void recurseFetchTreeTimestamps(MJSONWriter writer,
                  final String base_path, FileRepository fs, String path)
                                                        throws JSONException {

    List<FileInfo> dir_list = fs.getDirectoryFileInfoList(path);

    if (dir_list != null) {
      for (FileInfo finfo : dir_list) {
        if (finfo.isDirectory()) {
          recurseFetchTreeTimestamps(writer, base_path, fs, finfo.getAbsoluteName());
        }
        // Must be a file,
        else if (finfo.isFile()) {
          // The file timestamp,
          long ts = finfo.getLastModified();
          String flat_path =
                        finfo.getAbsoluteName().substring(base_path.length());
          writer.key(flat_path).value(Long.toString(ts));
        }
        else {
          throw new RuntimeException("FileInfo isn't File or Directory");
        }
      }
    }

  }

  /**
   * Recursively visits the sub-directory tree from the path and adds the path
   * to the 'to_delete' list if the directory is empty and can be deleted.
   * Paths are added to the 'to_delete' list by deepest path first.
   * 
   * @param fs
   * @param path
   * @param to_delete populated with the list of paths to delete (depth first
   *    order)
   * @return 
   */
  private boolean recurseFindEmptyDirectories(
                      FileSystem fs, String path, List<String> to_delete) {
    List<FileInfo> dir_file_list = fs.getDirectoryFileInfoList(path);
    if (dir_file_list.isEmpty()) {
      // If there's nothing here then delete it,
      to_delete.add(path);
      return true;
    }
    else {
      // Look at the sub-directories.
      List<FileInfo> subdir_list = fs.getSubDirectoryList(path);
      // Keep a count of the sub directories that are marked for removal,
      int del_count = 0;
      for (FileInfo subdir : subdir_list) {
        boolean deleted = recurseFindEmptyDirectories(
                                      fs, subdir.getAbsoluteName(), to_delete);
        if (deleted) {
          ++del_count;
        }
      }
      // If all the sub directories were marked for removal,
      // And there's no files,
      if (del_count == subdir_list.size() && fs.getFileList(path).isEmpty()) {
        // Add this path to the delete list,
        to_delete.add(path);
        return true;
      }
      // Otherwise return false.
      return false;
    }
  }

  /**
   * Given a location string and a flag indicating if the location is a
   * contextual path ('location' is an app name otherwise), returns a JSON
   * object with all existing file names and file timestamps of the current
   * data in the file system paths relevant to updating the data.
   * <p>
   * The returns JSON object is "basepath" -> Map of relative path -> timestamp.
   * For example;
   * <code>
   *   "ts_data":
   *     { "/bin/",
   *         { "cd.js":TS, "cls.js":TS, "cp.js":TS, "download.js":TS, ... },
   *       "/apps/console/",
   *         { "WEB-INF/web.xml":TS, "index.html":TS, ... } }
   * </code>
   * 
   * @param ctx
   * @param writer
   * @param location
   * @param location_is_path 
   */
  private void fetchFileTimestamps(PlatformContext ctx, MJSONWriter writer,
              String location, boolean location_is_path) throws JSONException {

    // Trim the location string,
    location = location.trim();
    
    FileName path;

    // If the location is an application name,
    if (!location_is_path) {

      // Look for the application,
      FileSystem fs = ctx.getFileRepository();
      Map<String, FileName> user_app_map =
                  UserApplicationsSchema.getUserAppLocationMap(fs, location);
      // App not found
      if (user_app_map == null) {
        writer.key("error").value("UNKNOWN_APP " + location);
        return;
      }
      // The application main location,
      FileName main_location = user_app_map.get("main");
      if (main_location == null) {
        writer.key("error").value("WEBAPP_MAIN_LOCATION_MISSING " + location);
        return;
      }
      
      path = main_location;

    }
    else {

      String floc = location;
      // Make sure path is absolute,
      if (!floc.startsWith("/")) {
        floc = "/" + floc;
      }
      // And that it's a directory,
      if (!floc.endsWith("/")) {
        floc = floc + "/";
      }
      path = new FileName(floc);

    }

    FileRepository fs = ctx.getFileRepositoryFor(path);
    if (fs == null) {
      writer.key("error").value("REPOSITORY_NOT_AVAILABLE " + path.toString());
      return;
    }

    // Start the JSON 'ts_data' map item,
    writer.key("ts_data");
    writer.object();

    writer.key(path.toString());

    writer.object();
    String path_file = path.getPathFile();
    recurseFetchTreeTimestamps(writer, path_file, fs, path_file);
    writer.endObject();

    writer.endObject();
    
  }

  /**
   * Converts a hash byte array into a fixed sized string.
   * 
   * @param buf
   * @return 
   */
  public static String hashToString(byte[] buf) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < buf.length; ++i) {
      int n = ((int) buf[i]) & 0x0FF;
      String hex = Integer.toHexString(n);
      if (hex.length() == 1) {
        b.append('0');
      }
      b.append(hex);
    }
    return b.toString();
  }

  /**
   * Calculates the SHA hashes for the given file_set.
   * 
   * @param ctx
   * @param writer
   * @param base_path
   * @param flist 
   */
  private void fetchFileHashes(PlatformContext ctx, MJSONWriter writer,
                                String base_path, JSONArray file_set)
                                          throws IOException, JSONException {

    FileName base = new FileName(base_path);
    if (!base.isAbsolute() || !base.isDirectory()) {
      writer.key("error").value("BASE_PATH_NOT_DIRECTORY");
      return;
    }

    String base_repository_id = base.getRepositoryId();

    if (base_repository_id == null) {
      writer.key("error").value("BASE_PATH_MUST_SPEC_REPOSITORY");
      return;
    }

    FileSystem fs = ctx.getFileRepository(base_repository_id);
    // Use DirectorySynchronizer.MckoiRepository to discover the SHA hash
    // for the files.
    MckoiRepository repository = new MckoiRepository(fs, base.getPathFile());

    // This list will contain the hashes in the same order as the input file
    // set.
    List<String> file_hashes = new ArrayList();

    for (int i = 0; i < file_set.length(); ++i) {
      String filename = file_set.getString(i);

      String dir;
      String file;
      int delim = filename.lastIndexOf("/");
      if (delim == -1) {
        dir = "/";
        file = filename;
      }
      else {
        dir = "/" + filename.substring(0, delim + 1);
        file = filename.substring(delim + 1);
      }

      SynchronizerFile file_obj = repository.getFileObject(dir, file);
      if (!file_obj.exists()) {
        writer.key("error").value("FILE_NOT_FOUND " + filename);
        return;
      }
      long file_size = file_obj.getSize();
      byte[] hash = file_obj.getSHAHash();

      // Turn the hash into a string,
      String hash_string = hashToString(hash);
      file_hashes.add(hash_string + " " + Long.toString(file_size));

    }

    // Start the JSON 'hash_data' map item,
    writer.key("hash_data");

    // Write all the hash values into the array,
    writer.array();
    for (String hash : file_hashes) {
      writer.value(hash);
    }
    writer.endArray();

  }

  /**
   * Refreshes the web application context with the given name.
   * 
   * @param ctx
   * @param writer
   * @param app_path
   * @throws IOException
   * @throws JSONException 
   */
  private void refreshWebApp(PlatformContext ctx, MJSONWriter writer,
                             String app_path)
                                          throws IOException, JSONException {

    FileName fname = new FileName(app_path);
    String repository_id = fname.getRepositoryId();
    String app_fpath = fname.getPathFile();
    
    FileRepository fs = ctx.getFileRepository(repository_id);

    BuildSystem build_sys = ctx.getBuildSystem();
    boolean b = build_sys.reloadWebApplicationAt(fs, app_fpath, null);

    if (b) {
      try {
        fs.commit();
      }
      catch (CommitFaultException ex) {
        writer.key("error").key("COMMIT_FAULT");
      }
    }
    else {
      writer.key("error").key("APP_RELOAD_FAILED");
    }

  }

  /**
   * Handles a POST request with FORM style parameter encoding.
   * 
   * @param ctx
   * @param log
   * @param request
   * @param response
   * @throws IOException
   * @throws JSONException 
   */
  private void handleFormPost(PlatformContext ctx, LogSystem log,
                  HttpServletRequest request, HttpServletResponse response)
                                            throws IOException, JSONException {

    // The request command,
    String cmd = request.getParameter("cmd");

    if (cmd == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    // If it's a 'vcheck' command,
    if (cmd.equals("vcheck")) {
      response.setContentType("text/plain;charset=UTF-8");
      MJSONWriter writer = getJSONWriter(response);

      writer.object();
      writer.key("version").value("1.0");
      writer.endObject();

      writer.flush();
      writer.close();

    }
    // Other commands require validation,
    else {

      String user = request.getParameter("user");
      String pass = request.getParameter("pass");

      if (user == null || pass == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }

      boolean user_authenticated = false;

      // Can the user authenticate?
      UserManager user_manager = ctx.getUserManager();
      if (user_manager.canUserAuthenticate(user, pass)) {
        // YES!
        user_authenticated = true;
      }

      // ISSUE: Auth failure is delayed here, however this doesn't help if
      //   the client issues many synchronous calls. We perhaps need to
      //   cap password attempt throughput by IP address.

      if (!user_authenticated) {
        // Dispatch auth failure after a random delay,
        dispatchDelayedAuthFailure(request);
        return;
      }

      // Disable automatic logging from this point on,
      ctx.getLogControl().setAutomaticLogging(false);

      response.setContentType("text/plain;charset=UTF-8");
      MJSONWriter writer = getJSONWriter(response);
      writer.object();
      writer.key("auth").value("OK");

      // Validate the username/password is valid
      if (cmd.equals("validate")) {
        // Enable automatic logging for the validate command,
        ctx.getLogControl().setAutomaticLogging(true);
      }
      // Fetch current directory structure with timestamps,
      else if (cmd.equals("fetch_file_timestamps")) {
        String location = request.getParameter("location");
        boolean location_is_path = Boolean.parseBoolean(
                                  request.getParameter("location_is_path"));
        fetchFileTimestamps(ctx, writer, location, location_is_path);
      }
      // Fetch SHA hash for the group of input files,
      else if (cmd.equals("calc_file_hash")) {
        String base_path = request.getParameter("base_path");
        String file_list = request.getParameter("file_list");
        // Input list is a JSON array,
        JSONTokener t = new JSONTokener(file_list);
        JSONArray flist = new JSONArray(t);
        fetchFileHashes(ctx, writer, base_path, flist);
      }
      // Refresh web app,
      else if (cmd.equals("refresh_webapp")) {
        String app_path = request.getParameter("app_path");
        if (app_path == null) {
          writer.key("error").value("NO_APP_PATH");
        }
        else {
          refreshWebApp(ctx, writer, app_path);
        }
      }

      else {
        // User validated but command is unknown
        writer.key("error").value("UNKNOWN_COMMAND");
      }

      // Close out the JSON object,
      writer.endObject();
      writer.flush();
      writer.close();

    }

  }

  /**
   * Handles a POST request which is encoded in a Java binary format, for
   * handling the upload of binary data.
   * 
   * @param ctx
   * @param log
   * @param request
   * @param response
   * @throws IOException
   * @throws JSONException 
   */
  private void handleBinaryPost(PlatformContext ctx, LogSystem log,
                  HttpServletRequest request, HttpServletResponse response)
                                            throws IOException, JSONException {

    byte[] COPY_BUF = new byte[1024];

    // Just incase, we confirm the content type of the request body,
    String content_type = request.getContentType();
    if (!content_type.equals("application/octet-stream")) {
      response.sendError(
                  HttpServletResponse.SC_BAD_REQUEST, "Invalid content-type");
      return;
    }

    ServletInputStream sin = request.getInputStream();

    BufferedInputStream bin = new BufferedInputStream(sin);
    DataInputStream data_in = new DataInputStream(bin);

    // NOTE: For the encoding of this message, see com.mckoi.mwpclient.AppSync
    // -----------------------------------------------------------------------

    String code = data_in.readUTF();
    if (!code.equals("EC")) {
      response.sendError(
                  HttpServletResponse.SC_BAD_REQUEST, "Invalid content-type");
      return;
    }

    // Authenticate,
    String user = data_in.readUTF();
    String pass = data_in.readUTF();

    boolean user_authenticated = false;

    // Can the user authenticate?
    UserManager user_manager = ctx.getUserManager();
    if (user_manager.canUserAuthenticate(user, pass)) {
      // YES!
      user_authenticated = true;
    }

    // ISSUE: Auth failure is delayed here, however this doesn't help if
    //   the client issues many synchronous calls. We perhaps need to
    //   cap password attempt throughput by IP address.

    if (!user_authenticated) {
      // Dispatch auth failure after a random delay,
      dispatchDelayedAuthFailure(request);
      return;
    }

    response.setContentType("text/plain;charset=UTF-8");
    MJSONWriter writer = getJSONWriter(response);
    writer.object();
    writer.key("auth").value("OK");

    // What's the command?
    String cmd = data_in.readUTF();
    if (cmd.equals("APPSYNC")) {

      int delete_count = 0;
      int touch_count = 0;
      int write_count = 0;
      int mjs_scripts_count = 0;

      // The destination we are syncing with,
      final String location = data_in.readUTF();
      int difference_entries = data_in.readInt();

      // The file system,
      FileName location_fname = new FileName(location);
      FileSystem fs = ctx.getFileRepositoryFor(location_fname);
      String loc_path = location_fname.getPathFile();

      // For each difference,
      for (int i = 0; i < difference_entries; ++i) {

        String dif_command = data_in.readUTF();
        String file = data_in.readUTF();

        String full_repo_filename = loc_path + file;

        if (dif_command.equals("DELETE")) {

          fs.deleteFile(full_repo_filename);

          ++delete_count;

        }
        else {
          // Read the timestamp of the file being updated or added,
          long timestamp = data_in.readLong();

          if (dif_command.equals("UPLOAD")) {

            String file_mime = data_in.readUTF();
            long file_size = data_in.readLong();

            DataFile df = fs.getDataFile(full_repo_filename);
            if (df == null) {
              // Create the file,
              fs.createFile(full_repo_filename, file_mime, timestamp);
              df = fs.getDataFile(full_repo_filename);
            }
            else {
              fs.touchFile(full_repo_filename, timestamp);
            }
            OutputStream sdo =
                          DataFileUtils.asSimpleDifferenceOutputStream(df);

            long upload_remaining = file_size;

            while (upload_remaining > 0) {
              int to_read = (int) Math.min(
                                (long) COPY_BUF.length, upload_remaining);
              int act_read = data_in.read(COPY_BUF, 0, to_read);
              if (act_read == -1) {
                // Oops, stream closed before end?
                throw new IOException("Premature end of stream");
              }

              // Write from the copy buffer to the file.
              sdo.write(COPY_BUF, 0, act_read);

              upload_remaining -= act_read;
            }

            // Close and truncate file,
            sdo.close();

            ++write_count;

          }
          else if (dif_command.equals("TOUCH")) {

            // Touch the file,
            fs.touchFile(full_repo_filename, timestamp);

            ++touch_count;

          }
          else {
            throw new IOException("Unknown command: " + dif_command);
          }

        }

      }

      // If files were deleted, we check for any empty directories and
      // delete them,

      // ISSUE: We really should have the sync source tell us what the
      //   directory tree should be. This will remove directories that
      //   exist in the source, just have no files/subdirectories.

      List<String> paths_to_delete = new ArrayList(6);
      recurseFindEmptyDirectories(fs, loc_path, paths_to_delete);
      if (!paths_to_delete.isEmpty()) {
        for (String path : paths_to_delete) {
          fs.removeDirectory(path);
        }
      }

      // Are there any mjs scripts to copy into the /bin/ directory?
      {
        String mjs_script_path = loc_path + "WEB-INF/classes/mjs/bin/";
        FileInfo script_dir_fi = fs.getFileInfo(mjs_script_path);
        if (script_dir_fi != null && script_dir_fi.isDirectory()) {
          // Yes, so copy the files to the /bin directory,
          DirectorySynchronizer s =
               DirectorySynchronizer.getMckoiToMckoiSynchronizer(null,
                                              fs, mjs_script_path, fs, "/bin/");
          s.setDeleteFilesFlag(false);
          mjs_scripts_count += s.synchronize();
        }
      }
      // Are there any mjs scripts to copy into the /nbin/ directory?
      {
        String mjs_script_path = loc_path + "WEB-INF/classes/mjs/nbin/";
        FileInfo script_dir_fi = fs.getFileInfo(mjs_script_path);
        if (script_dir_fi != null && script_dir_fi.isDirectory()) {
          // Yes, so copy the files to the /bin directory,
          DirectorySynchronizer s =
               DirectorySynchronizer.getMckoiToMckoiSynchronizer(null,
                                              fs, mjs_script_path, fs, "/nbin/");
          s.setDeleteFilesFlag(false);
          mjs_scripts_count += s.synchronize();
        }
      }

      boolean changes_made;
      boolean operation_success;
      if (write_count > 0 || delete_count > 0 ||
          touch_count > 0 || mjs_scripts_count > 0) {
        try {
          fs.commit();
          operation_success = true;
          changes_made = true;
        }
        catch (CommitFaultException ex) {
          // Erk,
          operation_success = false;
          writer.key("error").value("COMMIT_FAULT");
          changes_made = false;
        }
      }
      else {
        changes_made = false;
        operation_success = true;
      }

      if (operation_success) {
        // Write summary message,
        writer.key("result").value("sync_complete");
        writer.key("changes").value(changes_made ? "true" : "false");
        writer.key("write_count").value((long) write_count);
        writer.key("touch_count").value((long) touch_count);
        writer.key("delete_count").value((long) delete_count);
        writer.key("mjs_scripts_count").value((long) mjs_scripts_count);

        // Put some messages on the log,
        log.log(Level.INFO, "app",
                "Application Synchronize Data Upload Complete.\n" +
                "  path: {0}\n" +
                "  write = {1}, touch = {2}, delete = {3}, mjs_scripts = {4}",
              location,
              Integer.toString(write_count),
              Integer.toString(touch_count),
              Integer.toString(delete_count),
              Integer.toString(mjs_scripts_count));
      }

    }
    else {
      // User validated but command is unknown
      writer.key("error").value("UNKNOWN_COMMAND");
    }

    // Close out the JSON object,
    writer.endObject();
    writer.flush();
    writer.close();

  }

  /**
   * Processes requests for both HTTP
   * <code>GET</code> and
   * <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(
                  HttpServletRequest request, HttpServletResponse response)
                                         throws ServletException, IOException {

    PlatformContext ctx = PlatformContextFactory.getPlatformContext();
    LogSystem log = ctx.getLogSystem();

    try {
      // Handle async messages,
      Object consume_status_key = request.getAttribute(CONSUME_STATUS_KEY);
      if (consume_status_key != null) {
        handleAsyncDispatch(ctx, request, response);
        return;
      }

      // Check the query string, if it is 'bin' then we are handling a binary
      // encoded command.
      String query_string = request.getQueryString();
      if (query_string != null && query_string.equals("bin")) {
        handleBinaryPost(ctx, log, request, response);
      }
      else {
        handleFormPost(ctx, log, request, response);
      }
      
    }
    catch (JSONException ex) {
      // Log the exception before we return,
      log.log(Level.SEVERE, "app", "JSON Exception: {0}\n{1}",
                            ex.getMessage(), LogUtils.stringStackTrace(ex));
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                          "JSON Exception");
    }
  }

  /**
   * Handles the HTTP
   * <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {

    // GET not allowed to prevent cross site exploits
    response.sendError(
                HttpServletResponse.SC_FORBIDDEN, "GET request is forbidden");

  }

  /**
   * Handles the HTTP
   * <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {

    processRequest(request, response);

  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "General services that support a Mckoi Web Platform installation";
  }

}
