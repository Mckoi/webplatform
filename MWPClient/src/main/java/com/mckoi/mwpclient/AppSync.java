/*
 * Copyright 2015 Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mckoi.mwpclient;

import com.mckoi.lib.joptsimple.OptionException;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.lib.joptsimple.OptionSet;
import com.mckoi.mwpclient.http.HttpStatusCodeException;
import com.mckoi.mwpclient.http.OutputDelegateEntity;
import com.mckoi.mwpclient.http.SelfSignedCertificateException;
import com.mckoi.mwpclient.http.SelfSignedCertificateException.FailType;
import com.mckoi.mwpclient.http.WebClient;
import com.mckoi.odb.util.DirectorySynchronizer.JavaRepository;
import com.mckoi.odb.util.DirectorySynchronizer.ZipRepository;
import com.mckoi.odb.util.FileUtilities;
import com.mckoi.odb.util.SynchronizerFile;
import com.mckoi.odb.util.SynchronizerRepository;
import com.mckoi.util.IOWrapStyledPrintWriter;
import com.mckoi.util.StyledPrintWriter;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import java.security.GeneralSecurityException;
import org.apache.http.client.methods.*;

import org.json.*;

import static com.mckoi.util.StyledPrintWriter.ERROR;
import static com.mckoi.util.StyledPrintWriter.INFO;
import static java.util.Arrays.asList;

/**
 * Synchronizes the files in a .war file or a local folder with an Mckoi
 * Web Platform application folder. Connects to the server using HTTPS by
 * default.
 * <p>
 * The MWP account must be running the MWP Console UI application.
 * <p>
 * The program connects to the MWP Console App and, using hashes, determines
 * how the remote file system needs to be updated, added or removed.
 *
 * @author Tobias Downer
 */
public class AppSync {

  /**
   * The command line option parser.
   */
  private final OptionParser parser = makeOptionParser();

  /**
   * The URI for the remote 'SA' servlet.
   */
  private URI support_app;

  /**
   * The URI for sending binary data to the remote 'SA' servlet.
   */
  private URI support_bin_app;

  /**
   * The username and password used in the session.
   */
  private String user;
  private String password;

  /**
   * The WebClient.
   */
  private WebClient web_client;

  /**
   * Convenience that encodes the given arguments into a FORM POST to the
   * 'support_app' URI, executes it in the web client, and decodes a JSON
   * response.
   * 
   * @param saargs
   * @return
   * @throws IOException
   * @throws JSONException
   * @throws HttpStatusCodeException 
   */
  private JSONObject postToSA(Map<String, String> saargs)
                  throws IOException, JSONException, HttpStatusCodeException {
    return web_client.postWithJSONResult(
                        web_client.createHttpFormPost(support_app, saargs));
  }

  /**
   * Reports an error generated from a remote method.
   * 
   * @param out
   * @param json_ob
   * @throws JSONException 
   */
  private boolean handleError(StyledPrintWriter out, JSONObject json_ob)
                                                        throws JSONException {
    // All replies must have an auth key,
    if (!json_ob.has("auth")) {
      out.print("Error: ", ERROR);
      out.println("The reply is formatted incorrectly (no 'auth' key)");
      return true;
    }
    
    if (!json_ob.getString("auth").equals("OK")) {
      out.print("Error: ", ERROR);
      out.println("Authorization failed.");
      return true;
    }

    if (json_ob.has("error")) {
      String error_type = json_ob.getString("error");
      String values = "";
      int cdelim = error_type.indexOf(" ");
      if (cdelim >= 0) {
        values = error_type.substring(cdelim + 1);
        error_type = error_type.substring(0, cdelim);
      }

      out.print("Error: ", ERROR);
      String err_str;
      if (error_type.equals("UNKNOWN_COMMAND")) {
        err_str = "(UNKNOWN_COMMAND) Tried to execute a command that the server doesn't know.";
      }
      else if (error_type.startsWith("UNKNOWN_APP")) {
        err_str = "(UNKNOWN APP) The application is not found: ";
      }
      else if (error_type.startsWith("REPOSITORY_NOT_AVAILABLE")) {
        err_str = "Repository not found: ";
      }
      else {
        // PENDING: convert 'error_type' to a property error message,
        err_str = "(" + error_type + ") ";
      }
      out.print(err_str, ERROR);
      if (values != null) {
        out.println(values, ERROR);
      }
      else {
        out.println();
      }
      
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Recursively fetches the timestamps of all the files in the given
   * repository and adds to the ts_local map.
   */
  private void recurseFetchLocalTimestamps(SynchronizerRepository r,
                                String path, Map<String, Long> ts_local) {
    List<String> sub_dirs = r.allSubDirectories(path);
    for (String dir : sub_dirs) {
      recurseFetchLocalTimestamps(r, path + dir, ts_local);
    }
    List<SynchronizerFile> file_set = r.allFiles(path);
    for (SynchronizerFile f : file_set) {
      ts_local.put(path.substring(1) + f.getName(), f.getTimestamp());
    }
  }
  
  /**
   * Calculates the difference between the local repository and the remote
   * repository as presented as a ts_data format returned from an SA query.
   * The returned list is a set of commands needed to be executed to
   * synchronize the files.
   * <p>
   * This may need to post additional queries to discover repository
   * differences.
   * 
   * @param source_repository
   * @param ts_data
   * @return 
   */
  private Map<String, EntryDifference> calculateDifferences(
              SynchronizerRepository source_repository,
              String remote_base_path, JSONObject ts_remote)
                  throws IOException, JSONException, HttpStatusCodeException {

    // Create a map of all local files with timestamps.

    Map<String, Long> ts_local = new LinkedHashMap();
    
    recurseFetchLocalTimestamps(source_repository, "/", ts_local);

    // 'ts_local' now summarizes the local file location and 'ts_remote'
    // summarizes the remote file location in roughly similar formats.

    Map<String, EntryDifference> differences = new LinkedHashMap();

    // We iterate through local and find all files that are added (not in
    // remote), removed (not in local), and updated (assume all files are
    // updated for now).

    List<String> removed = new ArrayList();
    List<String> updated = new ArrayList();

    // For all local files,
    for (String file : ts_local.keySet()) {
      if (ts_remote.has(file)) {
        // Is in remote,
        updated.add(file);
      }
      else {
        // Not in remote,
        // Do not include META-INF path,
        if (!file.startsWith("META-INF/")) {
          differences.put(file, ADD_ENTRY);
        }
      }
    }
    
    // For all remote files,
    Iterator<String> remote_files = ts_remote.keys();
    while (remote_files.hasNext()) {
      String file = remote_files.next();
      // If the file isn't in local then it must be removed,
      if (!ts_local.containsKey(file)) {
        removed.add(file);
      }
    }

    // Now we need to analyse the updated set. Our sync method requires
    // comparing sha hashes.

    Map<String, String> hash_lookup = new HashMap();
    JSONArray check_hash_list = new JSONArray();
    
    Iterator<String> hash_check_it = updated.iterator();
    while (hash_check_it.hasNext()) {
      // We split SHA checks into maximumal groups of file blocks,
      for (int i = 0; i < 64; ++i) {
        if (hash_check_it.hasNext()) {
          String file = hash_check_it.next();
          check_hash_list.put(file);
        }
      }

      // The server query,
      Map<String, String> saargs = new HashMap();
      saargs.put("cmd", "calc_file_hash");
      saargs.put("user", user);
      saargs.put("pass", password);
      saargs.put("base_path", remote_base_path);
      saargs.put("file_list", check_hash_list.toString());
      JSONObject calc_file_hash_reply = postToSA(saargs);

      // Oops, there was an error,
      if (calc_file_hash_reply.has("error")) {
        throw new IOException(calc_file_hash_reply.getString("error"));
      }

      // The hash code results from the server,
      JSONArray hash_data = calc_file_hash_reply.getJSONArray("hash_data");

      // Populate the 'hash_lookup' map,
      for (int i = 0; i < check_hash_list.length(); ++i) {
        String hash_file = check_hash_list.getString(i);
        String hash_result = hash_data.getString(i);
        hash_lookup.put(hash_file, hash_result);
      }

      // Reset the check array,
      check_hash_list = new JSONArray();
    }

    // We now scan through the local files again and check file hashes there.

    for (String file : updated) {
      int delim = file.lastIndexOf("/");
      String fpath = "/" + file.substring(0, delim + 1);
      String file_name = file.substring(delim + 1);
      SynchronizerFile file_ob =
                          source_repository.getFileObject(fpath, file_name);
      // The local file hash,
      String local_hash = WebClient.hexEncode(file_ob.getSHAHash());
      // Compare it to remote,
      String remote_hash_encoded = hash_lookup.get(file);
      // Decode the hash string,
      int rh_delim = remote_hash_encoded.indexOf(' ');
      String remote_hash = remote_hash_encoded.substring(0, rh_delim);
      long remote_size =
                Long.parseLong(remote_hash_encoded.substring(rh_delim + 1));
      
      boolean hash_difference = false;
      boolean ts_difference = false;
      
      // Compare hashes,
      if (!local_hash.equals(remote_hash)) {
        // Not the same, so add to the hash different list,
        hash_difference = true;
      }
      // Compare timestamps,
      long remote_ts = Long.parseLong(ts_remote.getString(file));
      long local_ts = file_ob.getTimestamp();
      
      if (local_ts < remote_ts - 500 || local_ts > remote_ts + 500) {
        ts_difference = true;
      }

      if (hash_difference || ts_difference) {
        EntryDifference dif = new EntryDifference(
            (hash_difference ? EntryDifference.HASH : 0) |
            (ts_difference ? EntryDifference.TIMESTAMP : 0)
        );
        differences.put(file, dif);
      }
      
    }

    // Add in the removed entries at the end,
    for (String file : removed) {
      differences.put(file, REMOVE_ENTRY);
    }

    return differences;

  }

  /**
   * Handles an --addcert command.
   * 
   * @param out
   * @param console
   * @param remote_uri
   * @throws IOException 
   */
  private void handleAddCert(StyledPrintWriter out, Console console,
                              URI remote_uri) throws IOException {

    out.print("Connecting to: ");
    out.println(remote_uri, INFO);
    out.println();
    
    try {
      Map<String, String> saargs = new HashMap();
      saargs.put("cmd", "vcheck");
      // This communication is for SSL check only,
      postToSA(saargs);
      
      // There was no SelfSignedCertificateException so just return
      out.print("Connection is valid. --addcert is not needed.", INFO);
      
    }
    catch (SelfSignedCertificateException ex) {

      FailType fail_type = ex.getFailType();
      boolean big_warning = false;
      if (fail_type.equals(FailType.CERT_UNKNOWN)) {
        big_warning = false;
        out.println("This will add the certificate to a local file in your home directory.");
        out.println("After the certificate is added, when you next use this tool it will permit");
        out.println("commands being executed on the host. You should confirm with the");
        out.println("administrator that the thumbprint below is correct.");
        out.println();
      }
      else if (fail_type.equals(FailType.CERT_CHANGED)) {
        out.println("*** THIS CERTIFICATE CHANGED FROM THE ONE PREVIOUSLY SAVED ***", ERROR);
        out.println();
        out.println("Before you confirm this self-signed certificate, you should check with the");
        out.println("server administrator that the certificate was changed and the thumbprint");
        out.println("is correct.");
        out.println();
        big_warning = true;
      }
      else {
        throw new RuntimeException("Unknown fail type");
      }
      out.print("  Host: ");
      out.println(ex.getHost(), INFO);
      out.print("  SHA1 Thumbprint: [ ");
      out.print(WebClient.createSHA1Thumbprint(ex.getCert()), INFO);
      out.println(" ]");
      out.println();
      if (big_warning) {
        out.println("If you are ABSOLUTELY certain this self-signed certificate is allowed,");
        out.print("Confirm (enter YES): ");
        out.flush();
      }
      else {
        out.print("Confirm this self-signed certificate is permitted (enter YES): ");
        out.flush();
      }

      String entered = console.readLine();
      if (entered.equals("YES")) {
        web_client.userPermitCertificate(ex.getHost(), ex.getCert());
        out.println();
        out.println("OK, this self-signed certificate is now permitted for the host.");
      }
      else {
        out.println();
        out.println("Certificate was NOT added because confirmation was not 'YES'");
      }

    }
    catch (JSONException | HttpStatusCodeException ex) {
      out.println("Error: Unexpected exception.", ERROR);
      out.printException(ex);
    }
  }

  /**
   * Main method call.
   * 
   * @param out
   * @param args 
   * @throws java.io.IOException 
   */
  public void main(final StyledPrintWriter out, final Console console,
                                          String[] args) throws IOException {

    try {
      OptionSet parse = parser.parse(args);

      // Print help if requested,
      if (parse.has("?")) {
        printHelp(out);
        return;
      }

      List<String> nonOptionArguments = parse.nonOptionArguments();
      String mwp_url = (String) parse.valueOf("mwp");

      this.user = (String) parse.valueOf("user");
      this.password = (String) parse.valueOf("password");

      if (mwp_url == null) {
        printHelp(out);
        out.println();
        out.println("Error: --mwp argument is missing.", ERROR);
        return;
      }
      
      if (!mwp_url.endsWith("/")) {
        mwp_url = mwp_url + "/";
      }
      
      // Create the WebClient
      this.web_client = new WebClient();
      web_client.init();

      // Validate remote URI,
      URI remote_uri;
      String scheme;
      try {
        remote_uri = new URI(mwp_url);
        
        scheme = remote_uri.getScheme();
        if (scheme == null ||
            !(scheme.equals("http") || scheme.equals("https"))) {
          out.println("Error: URI syntax error: " + mwp_url, ERROR);
          out.println("Must be https or http scheme.", ERROR);
          return;
        }
        
      }
      catch (URISyntaxException ex) {
        out.println("Error: URI syntax error: " + mwp_url, ERROR);
        out.print("(", ERROR);
        out.print(ex.getMessage(), ERROR);
        out.println(")", ERROR);
        return;
      }

      this.support_app = remote_uri.resolve("SA");
      this.support_bin_app = remote_uri.resolve("SA?bin");

      boolean add_cert_switch = parse.has("addcert");
      
      // If we are adding a cert, do so here,
      if (add_cert_switch) {
        if (!scheme.equals("https")) {
          out.println();
          out.println("Error: --addcert can only work on 'https' scheme.", ERROR);
          return;
        }

        handleAddCert(out, console, remote_uri);

        return;
      }

      if (user == null) {
        printHelp(out);
        out.println();
        out.println("Error: --user argument is missing.", ERROR);
        return;
      }
      
      if (password == null) {
        printHelp(out);
        out.println();
        out.println("Error: --password argument is missing.", ERROR);
        return;
      }
      
      // Parse out the non optional arguments,
      if (nonOptionArguments.size() != 2) {
        printHelp(out);
        out.println();
        out.println("Error: Need a local source directory/archive and destination app name.", ERROR);
        return;
      }

      boolean allow_insecure_connections = parse.has("INSECURE");
      boolean remote_is_dir = parse.has("d");
      
      // The local and remote locations.
      String local_src = nonOptionArguments.get(0).trim();
      String remote_dest = nonOptionArguments.get(1).trim();

      // Check the local file or directory exists. The local file must be
      // either a .zip, .jar or .war.

      File local_f = new File(local_src);

      final SynchronizerRepository source_repository;

      if (local_f.exists()) {
        if (local_f.isFile()) {
          // Ok, it's a file so lets assume it's an archive,
          ZipRepository zip_source_repository = new ZipRepository(local_f);
          zip_source_repository.init();
          source_repository = zip_source_repository;
        }
        else if (local_f.isDirectory()) {
          // It's a directory, so process the files here,
          source_repository = new JavaRepository(local_f);
        }
        else {
          // Shouldn't be possible,
          throw new IOException();
        }
      }
      else {
        out.print("Error: a local file or directory called '", ERROR);
        out.print(local_src, ERROR);
        out.println("' was not found.", ERROR);
        return;
      }

      // Connect to the remote address specified in the -mwp argument.

      out.print("Connecting to: ");
      out.println(remote_uri, INFO);
      out.println();

      try {
        // Is the required app running?

        // Issue a warning because the communication with the server isn't
        // secure.
        if (!scheme.equals("https")) {
          if (!allow_insecure_connections) {
            out.println("ERROR: Insecure '" + scheme + "' connection with server.");
            out.println("  Use --INSECURE option to override.", ERROR);
            return;
          }
          else {
            out.println("WARNING: Allowing insecure connection with server.");
            out.println("  Passwords are being sent to the server as plain text!");
            out.println();
          }
        }

        String sa_version = null;

        // Post a 'vcheck' command to the SA Servlet. This confirms the
        // app is running.
        {
          try {
            Map<String, String> saargs = new HashMap();
            saargs.put("cmd", "vcheck");
            JSONObject vcheck_reply = postToSA(saargs);
            // Note: Generates JSONException if no 'version' string,
            sa_version = vcheck_reply.getString("version");
          }
          catch (SelfSignedCertificateException ex) {
            out.print("Problem; ", ERROR);
            
            FailType ss_cert_fail = ex.getFailType();
            if (ss_cert_fail.equals(FailType.CERT_UNKNOWN)) {
              out.println(
                  "The server has a self-signed certificate that is not registered on this");
              out.println("  account.");
              out.println();
              out.print("SHA1 Thumbprint: [ ");
              out.print(WebClient.createSHA1Thumbprint(ex.getCert()), INFO);
              out.println(" ]");
            }
            else if (ss_cert_fail.equals(FailType.CERT_UNKNOWN)) {
              out.println("*** THE SERVER CERTIFICATE HAS CHANGED ***", ERROR);
              out.println("  The self-signed certificate that is registered is not the same as the certificate");
              out.println("  currently used on the server. This may indicate a man-in-the-middle attack");
              out.println("  is in progress. You must confirm with the administrator that the certificate");
              out.println("  was changed.");
              out.println();
              out.print("SHA1 Thumbprint: [ ");
              out.print(WebClient.createSHA1Thumbprint(ex.getCert()), INFO);
              out.println(" ]");
            }
            else {
              throw ex;
            }
            out.println();
            out.println("Use --addcert to register the certificate. Then retry.");
            return;
          }
        }

        out.print("Success; ");
        out.print(support_app, INFO);
        out.print(" version: ");
        out.println(sa_version, INFO);
 
        // Ok, the SA Servlet exists,

        boolean authorized = false;

        // Check user authentication,
        {
          Map<String, String> saargs = new HashMap();
          saargs.put("cmd", "validate");
          saargs.put("user", user);
          saargs.put("pass", password);
          JSONObject validate_reply = postToSA(saargs);
          authorized = validate_reply.getString("auth").equals("OK");
        }

        if (!authorized) {
          out.print("Failed; ");
          out.println("Sorry, but you are not authorized.", ERROR);
          return;
        }
        else {
          out.print("Success; ");
          out.println("User Authenticated", INFO);
        }

        // The timestamps
        JSONObject ts_data = null;

        // Request the timestamps for the remote directory or path,
        {
          Map<String, String> saargs = new HashMap();
          saargs.put("cmd", "fetch_file_timestamps");
          saargs.put("user", user);
          saargs.put("pass", password);
          saargs.put("location", remote_dest);
          saargs.put("location_is_path", Boolean.toString(remote_is_dir));
          JSONObject file_timestamps_reply = postToSA(saargs);
          
          if (handleError(out, file_timestamps_reply)) {
            return;
          }

          ts_data = file_timestamps_reply.getJSONObject("ts_data");

        }

        // Now we have a map of remote files, we compare the time stamps with
        // the local files.

        if (ts_data.length() != 1) {
          out.print("Failed; ");
          out.println("Expected only a single key in 'ts_data'", ERROR);
          return;
        }
        final String remote_path = (String) ts_data.keys().next();

        // Calculate a map of files to differences,
        final Map<String, EntryDifference> differences =
              calculateDifferences(source_repository,
                             remote_path, ts_data.getJSONObject(remote_path));

        HttpPost sa_request = new HttpPost(support_bin_app);
        // The POST input is a delegate that binary encodes the difference
        // data.
        sa_request.setEntity(new OutputDelegateEntity() {
          @Override
          public void writeTo(OutputStream outstream) throws IOException {
            BufferedOutputStream bout = new BufferedOutputStream(outstream);
            DataOutputStream dout = new DataOutputStream(bout);
            // Encode it,
            try {
              encodeDifferenceOutput(out, dout,
                                source_repository, remote_path, differences);
            }
            finally {
              dout.flush();
            }
          }

        });
        // Perform the HTTP Post and upload the file binary difference data
        JSONObject data_upload_reply =
                                  web_client.postWithJSONResult(sa_request);
//        JSONObject data_upload_reply = postToSA(sa_request);
        // If there was an error,
        if (handleError(out, data_upload_reply)) {
          return;
        }
        // Otherwise, the reply contains summary data which we'll print out.
        boolean changes_made =
                      data_upload_reply.getString("changes").equals("true");
        long write_count = data_upload_reply.getLong("write_count");
        long touch_count = data_upload_reply.getLong("touch_count");
        long delete_count = data_upload_reply.getLong("delete_count");
        long mjs_scripts_count = data_upload_reply.getLong("mjs_scripts_count");

        out.println();
        out.println("Sync Summary");
        
        if (write_count == 0 && touch_count == 0 &&
            delete_count == 0 && mjs_scripts_count == 0) {
          out.println("  No Updates", INFO);
        }
        else {
          if (write_count > 0) {
            out.print("  Written; ");
            out.println(Long.toString(write_count), INFO);
          }
          if (touch_count > 0) {
            out.print("  Touched; ");
            out.println(Long.toString(touch_count), INFO);
          }
          if (delete_count > 0) {
            out.print("  Deleted; ");
            out.println(Long.toString(delete_count), INFO);
          }
          if (mjs_scripts_count > 0) {
            out.print("  /bin/;   ");
            out.println(Long.toString(mjs_scripts_count), INFO);
          }
        }

        // Finally, if the app was updated we ask the server to refresh the
        // app.

        if (changes_made && !remote_is_dir) {
          out.println();
          out.print("Refreshing webapp at: ");
          out.println(remote_path, INFO);
          // We updated an app, so invoke a 'ogon refreshwebapps' type command.
          Map<String, String> saargs = new HashMap();
          saargs.put("cmd", "refresh_webapp");
          saargs.put("user", user);
          saargs.put("pass", password);
          saargs.put("app_path", remote_path);
          JSONObject refresh_webapp_reply = postToSA(saargs);
          if (handleError(out, refresh_webapp_reply)) {
            return;
          }
          out.println("Complete");
        }

      }
      catch (HttpStatusCodeException ex) {
        int status_code = ex.getStatusLine().getStatusCode();
        out.print("Error: the server returned status code '", ERROR);
        out.print(String.valueOf(status_code), ERROR);
        out.println("' indicating");
        out.println("  either the server is down or it's not running the MWP software.");
        out.print("  ", ERROR);
        out.println(ex.getStatusLine().getReasonPhrase());
      }
      catch (UnknownHostException ex) {
        out.print("Error: Failed to connect (Unknown host): ", ERROR);
        out.println(remote_uri, ERROR);
      }
      catch (ConnectException ex) {
        out.print("Error: Failed to connect: ", ERROR);
        out.println(remote_uri, ERROR);
      }
      catch (JSONException ex) {
        out.print("Error: Response format error: ", ERROR);
        out.println(ex.getMessage(), ERROR);
      }
      
    }
    catch (OptionException ex) {
      printHelp(out);
      out.println();
      out.print("Error: ", ERROR);
      out.println(ex.getMessage(), ERROR);
    }
    catch (GeneralSecurityException ex) {
      out.print("Error: ", ERROR);
      out.println(ex.getMessage(), ERROR);
      out.printException(ex);
    }

  }

  /**
   * Encodes into a binary format the changes necessary to synchronize the
   * local files with the remote system.
   * 
   * @param out
   * @param data_out
   * @param source_repository
   * @param remote_dest
   * @param differences 
   */
  private void encodeDifferenceOutput(
          StyledPrintWriter out,
          DataOutputStream data_out,
          SynchronizerRepository source_repository,
          String remote_dest, Map<String, EntryDifference> differences)
                                                          throws IOException {

    // NOTE: For the decoding of this, see com.mckoi.mwpui.SystemSupportServlet
    // ------------------------------------------------------------------------

    // Authenticate,
    data_out.writeUTF("EC");
    data_out.writeUTF(user);
    data_out.writeUTF(password);

    byte[] COPY_BUF = new byte[1024];

    // Write the command,
    data_out.writeUTF("APPSYNC");

    Set<Entry<String, EntryDifference>> entries = differences.entrySet();

    // The remote destination being sync'd with,
    data_out.writeUTF(remote_dest);

    // The total number of operations,
    data_out.writeInt(entries.size());

    // Ok, encode each operation,
    for (Entry<String, EntryDifference> entry : entries) {
      String file = entry.getKey();
      EntryDifference dif = entry.getValue();

      String cmd = null;
      if (dif.isHashChanged() || dif.isOnlyLocal()) {
        cmd = "UPLOAD";
      }
      else if (dif.isOnlyRemote()) {
        cmd = "DELETE";
      }
      else if (dif.isTimestampChanged()) {
        cmd = "TOUCH";
      }
      else {
        throw new IOException("EntryDifference is formatted incorrectly");
      }

      data_out.writeUTF(cmd);
      data_out.writeUTF(file);

      // If it's NOT a delete operation,
      if ( !cmd.equals("DELETE") ) {
        // It's either UPLOAD or TOUCH
        int delim = file.lastIndexOf("/");
        String fpath = "/" + file.substring(0, delim + 1);
        String file_name = file.substring(delim + 1);

        // Write the timestamp,
        SynchronizerFile file_obj =
                            source_repository.getFileObject(fpath, file_name);
        long timestamp = file_obj.getTimestamp();

        data_out.writeLong(timestamp);

        // If it's an UPLOAD command, send the file content,
        if (cmd.equals("UPLOAD")) {

          long file_size = file_obj.getSize();

          out.print("WRITE; ");
          out.print(file, INFO);
          out.print(" (");
          out.print(Long.toString(file_size));
          out.println(" bytes)");

          // The mime type of the file,
          String mime_type = FileUtilities.findMimeType(file_name);
          data_out.writeUTF(mime_type);
          
          data_out.writeLong(file_size);

          try (InputStream file_in = file_obj.getInputStream()) {
            while (true) {
              // Copy to output,
              int read = file_in.read(COPY_BUF, 0, COPY_BUF.length);
              if (read == -1) {
                break;
              }
              data_out.write(COPY_BUF, 0, read);
            }

          }

        }
        else {

          out.print("TOUCH; ");
          out.println(file, INFO);

        }

      }
      else {

        out.print("DELETE; ");
        out.println(file, INFO);

      }

    }

  }

  /**
   * Prints command syntax.
   * @param out
   */
  public void printHelp(StyledPrintWriter out) throws IOException {
    out.println("Syntax: AppSync <options> [local source path or .war] [destination]");
    out.println();
    out.println("[destination] is either an application name (eg. 'console') or if the -d switch", INFO);
    out.println("is used, a path to an existing directory in the remote file system.", INFO);
    out.println();

    Writer w = out.asWriter();
    parser.printHelpOn(w);
    w.flush();
  }

  /**
   * Creates the options to be parsed.
   * @return 
   */
  private static OptionParser makeOptionParser() {
    OptionParser parser = new OptionParser();
    parser.accepts("mwp", "MWP console URL (eg. 'https://myserver.com/console/'").
            withRequiredArg().ofType(String.class);
    parser.acceptsAll(asList("d"), "Destination is a remote directory");
    parser.acceptsAll(asList("user", "u"), "Login username").
            withRequiredArg().ofType(String.class);
    parser.acceptsAll(asList("password", "pw", "p"), "Login password").
            withRequiredArg().ofType(String.class);
    parser.acceptsAll(asList("help", "?"), "Help");
    parser.acceptsAll(asList("INSECURE"),
                      "Permit insecure HTTP connections (do not use this)");
    parser.acceptsAll(asList("addcert"), "Adds a HTTPS self-signed certificate to permitted list");

    return parser;
  }

  /**
   * Invocation point for console access.
   * 
   * @param args 
   */
  public static void main(String[] args) {
    try {
      IOWrapStyledPrintWriter pout = new IOWrapStyledPrintWriter(System.out);
      Console console = System.console();
      new AppSync().main(pout, console, args);
      pout.flush();
    }
    catch (IOException ex) {
      ex.printStackTrace(System.err);
    }
  }

  // -----

  /**
   * Describes the difference of a local file with a remote file.
   */
  private static class EntryDifference {

    public static int HASH        = 0x001;
    public static int TIMESTAMP   = 0x002;
    public static int ONLY_LOCAL  = 0x004;
    public static int ONLY_REMOTE = 0x008;

    private final int type;

    EntryDifference(int type) {
      this.type = type;
    }

    boolean isUpdate() {
      return (type & (HASH | TIMESTAMP)) != 0;
    }
    boolean isOnlyLocal() {
      return (type & ONLY_LOCAL) != 0;
    }
    boolean isOnlyRemote() {
      return (type & ONLY_REMOTE) != 0;
    }
    boolean isHashChanged() {
      return (type & HASH) != 0;
    }
    boolean isTimestampChanged() {
      return (type & TIMESTAMP) != 0;
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("[");
      if (isOnlyLocal()) {
        b.append("+");
      }
      if (isOnlyRemote()) {
        b.append("-");
      }
      if (isUpdate()) {
        b.append("U");
        if (isHashChanged()) {
          b.append("#");
        }
        if (isTimestampChanged()) {
          b.append("T");
        }
      }
      b.append("]");
      return b.toString();
    }
    
  }

  private static final EntryDifference ADD_ENTRY =
                              new EntryDifference(EntryDifference.ONLY_LOCAL);
  private static final EntryDifference REMOVE_ENTRY =
                              new EntryDifference(EntryDifference.ONLY_REMOTE);


}
