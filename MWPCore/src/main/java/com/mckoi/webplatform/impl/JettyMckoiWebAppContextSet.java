/**
 * com.mckoi.webplatform.impl.JettyMckoiWebAppContextSet  May 16, 2010
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

package com.mckoi.webplatform.impl;

import com.mckoi.appcore.UserApplicationsSchema;
import com.mckoi.data.DataFile;
import com.mckoi.data.PropertySet;
import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPClassLoaderSet;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * 
 *
 * @author Tobias Downer
 */

class JettyMckoiWebAppContextSet extends AbstractHandler { //extends HandlerList {

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  /**
   * The account name for this context.
   */
  private final String account_name;

  /**
   * The system timer.
   */
  private final Timer system_timer;

  /**
   * The local temporary folder.
   */
  private final File local_temp_folder;

  /**
   * The generally allowed system classes.
   */
  private final ClassNameValidator general_allowed_sys_classes;

  /**
   * The MWP class loaders.
   */
  private final MWPClassLoaderSet classloaders;  

  /**
   * The log system that's shared between all contexts.
   */
  private final LoggerService account_logger;

  /**
   * The set of JettyMckoiWebAppContext objects together with their vhost
   * and context information for fast lookup.
   */
  private final ArrayList<WALookup> lookups;

  /**
   * The queue of WALookup contexts that have gone out of scope and must be
   * stopped.
   */
  private final ArrayList<WALookup> stop_queue;

  /**
   * The time to clear the stop queue.
   */
  private long min_stop_queue_clear_ts = 0;

  /**
   * The file timestamp of the /system/webapps.properties file that was last
   * loaded.
   */
  private volatile long last_webapps_timestamp = 0;

  /**
   * The lock object.
   */
  private final Object lock = new Object();

  /**
   * The time that the next check happens.
   */
  private long next_check_ts;

  /**
   * Constructor.
   */
  JettyMckoiWebAppContextSet(DBSessionCache sessions_cache,
                             String account_name, Timer system_timer,
                             File local_temp_folder,
                             ClassNameValidator general_allowed_sys_classes,
                             MWPClassLoaderSet classloaders,
                             LoggerService account_logger) {

    this.sessions_cache = sessions_cache;
    this.account_name = account_name;
    this.system_timer = system_timer;
    this.account_logger = account_logger;
    this.lookups = new ArrayList<>();
    this.stop_queue = new ArrayList<>();

    this.local_temp_folder = local_temp_folder;
    this.general_allowed_sys_classes = general_allowed_sys_classes;
    this.classloaders = classloaders;
  }

  /**
   * Returns the LoggerService for this context set.
   */
  LoggerService getLogSystem() {
    return account_logger;
  }

  /**
   * Reads the /system/webapps.properties file from the user's file repository
   * and initializes the web applications defined there in this context set.
   */
  private void initializeWebApps() {

    // Get the account file system,
    ODBTransaction fs_t =
                        sessions_cache.getODBTransaction("ufs" + account_name);
    FileRepositoryImpl account_filesystem =
                       new FileRepositoryImpl(account_name, fs_t, "accountfs");

    // webapps_file is at 'webapps.pset'
    // It's a binary file that can be easily queried
    FileInfo wa_finfo =
           UserApplicationsSchema.getWebAppsBinaryFileInfo(account_filesystem);

    if (wa_finfo != null) {

      // The timestamp of the web apps file,
      long webapps_timestamp = wa_finfo.getLastModified();

      // We only continue if the file timestamp is different to the one we
      // have on record,
      if (last_webapps_timestamp <= 0 ||
          webapps_timestamp != last_webapps_timestamp) {

        // Get the webapps file as a DataFile,
        DataFile webapps_file = wa_finfo.getDataFile();

        // The new lookups list,
        ArrayList<WALookup> new_lookups = new ArrayList<>();

        // The property set representing the web apps information,
        PropertySet pset = new PropertySet(webapps_file);

        // The set of all vhost keys,
        SortedSet<String> vhosts = pset.keySet().tailSet("vh.");

        // For each virtual host entry,
        for (String key : vhosts) {
          // Break the loop if we are no longer looking at virtual host keys,
          if (!key.startsWith("vh.")) {
            break;
          }
          // The encoded query arg, eg.
          //   www.mckoi.com:http:admin/
          String encoded_arg = key.substring(3);

          String domain;
          String protocol = null;
          String context_path = null;

          int ldelim = encoded_arg.lastIndexOf(":");
          if (ldelim > 0) {
            context_path = encoded_arg.substring(ldelim + 1);
            encoded_arg = encoded_arg.substring(0, ldelim);
          }
          ldelim = encoded_arg.lastIndexOf(":");
          if (ldelim > 0) {
            protocol = encoded_arg.substring(ldelim + 1);
            encoded_arg = encoded_arg.substring(0, ldelim);
          }
          domain = encoded_arg;

          if (domain != null && protocol != null && context_path != null) {
            // Create the web app to handle this,
            JettyMckoiWebAppContext context;

            // Make sure the context path starts and ends with "/"
            if (!context_path.startsWith("/")) {
              context_path = "/" + context_path;
            }
            if (!context_path.endsWith("/")) {
              context_path += "/";
            }

            // Get the application id for this domain location,
            String app_id = pset.getProperty(key);
            // Other keys,
            String name_key = "p." + app_id + ".name";
            String repository_key = "p." + app_id + ".repository";
            String repository_path_key = "p." + app_id + ".repository_path";

            String repository = pset.getProperty(repository_key);
            String repository_path = pset.getProperty(repository_path_key);

            FileName rep_fname;
            // Legacy,
            if (repository != null) {
              rep_fname = new FileName(repository, repository_path);
            }
            else {
              rep_fname = new FileName(repository_path);
            }

            // PENDING: Handle apps in different file repositories,
            // Location of the webapp,
            String webapp_path = rep_fname.getPathFile();

            String webapp_name = pset.getProperty(name_key);

            // PENDING: Contexts may permit varying levels of system class
            //   acceptance.
            ClassNameValidator allowed_system_classes =
                                                    general_allowed_sys_classes;

            // Create the web app context object,
            context = new JettyMckoiWebAppContext(
                          sessions_cache, account_name,
                          domain, protocol, context_path, webapp_path,
                          account_logger,
                          allowed_system_classes, classloaders,
                          webapp_name, local_temp_folder);

            // Create the lookup object,
            WALookup lookup =
                    new WALookup(app_id, webapp_name,
                                 domain, protocol, context_path, context);
            // Add it to the list,
            new_lookups.add(lookup);
          }
        }

        boolean pending_stops = false;

        // Update the lookups object,
        synchronized (lookups) {
          ArrayList<WALookup> final_lookups = new ArrayList<>();
          ArrayList<Integer> touched_indexes = new ArrayList<>();
          // For each new lookup,
          for (WALookup lookup : new_lookups) {
            // If the current lookups list already contains an entry with the
            // same id we use that
            int same_id = -1;
            int i = 0;
            for (WALookup searchl : lookups) {
              if (lookup.id.equals(searchl.id) &&
                  lookup.domain.equals(searchl.domain) &&
                  lookup.protocol.equals(searchl.protocol)) {
                same_id = i;
                break;
              }
              ++i;
            }
            // Not found, so add the new entry,
            if (same_id == -1) {
              // Complete the context,
              // Set the server,
              lookup.context.setServer(getServer());
              final_lookups.add(lookup);
            }
            // Found, so inherit the entry from the current lookups,
            else {
              final_lookups.add(lookups.get(same_id));
              touched_indexes.add(same_id);
            }
          }

          // Any lookups we didn't inherit we need to put into the stop queue,
          int sz = lookups.size();
          for (int i = 0; i < sz; ++i) {
            if (!touched_indexes.contains(i)) {
              stop_queue.add(lookups.get(i));
              pending_stops = true;
            }
          }

          // Clear the lookups list and set it to the final version,
          lookups.clear();
          lookups.addAll(final_lookups);

          if (pending_stops) {
            min_stop_queue_clear_ts = System.currentTimeMillis() + (60 * 1000);
          }
        }

        // Set the webapps timestamp,
        last_webapps_timestamp = webapps_timestamp;

        // If there are pending stops, schedule the stop process,
        if (pending_stops) {
          system_timer.schedule(new StopQueueTask(), (70 * 1000));
        }

      } // if webapps timestamp different

    } // if webapps properties file exists

  }

  /**
   * Timed task that stops all the contexts that have gone out of scope and
   * no longer accessible.
   */
  private class StopQueueTask extends TimerTask {
    @Override
    public void run() {

      boolean is_empty;
      ArrayList<WALookup> to_stop = new ArrayList<>();

      synchronized (lookups) {
        if (min_stop_queue_clear_ts < System.currentTimeMillis()) {
          to_stop.addAll(stop_queue);
          stop_queue.clear();
        }
        is_empty = stop_queue.isEmpty();
      }

      // Stop all the contexts so they can be GC'd,
      for (WALookup lookup : to_stop) {
        try {
          if (lookup.context.isStarted() ||
              lookup.context.isStarting()) {
            lookup.context.stop();
          }
        }
        catch (Throwable e) {
          e.printStackTrace(System.err);
        }
      }

      // If the queue isn't empty, schedule a new clear,
      if (!is_empty) {
        system_timer.schedule(new StopQueueTask(), (60 * 1000));
      }
    }
  };

  /**
   * The start operation reads the web application set from the configuration
   * file stored in the account's file system.
   */
  @Override
  protected void doStart() throws Exception {

    // Set the minimum time that a re-initialization check is performed
    // (2 seconds from now).
    next_check_ts = System.currentTimeMillis() + (2 * 1000);

    // Initialize the various web application contexts,
    initializeWebApps();

    super.doStart();
  }

  @Override
  protected void doStop() throws Exception {
    super.doStop();

    // Force the logger to flush
    account_logger.forceFlush();

  }




  @Override
  public void handle(String target, Request jetty_request,
                     HttpServletRequest request, HttpServletResponse response)
                                         throws IOException, ServletException {

    long current_ts = System.currentTimeMillis();
    boolean do_reinit = false;

    synchronized (lock) {
      // If the current timestamp is greater than the next check timestamp,
      // we call the 'initializeWebApps' method.
      if (current_ts > next_check_ts) {
        // Set the next check to two seconds from now,
        next_check_ts = current_ts + (2 * 1000);
        do_reinit = true;
      }
    }

    // If we are to do a reinitialization,
    if (do_reinit) {
      initializeWebApps();
    }

    // eg. http or https,
    String rscheme = request.getScheme();
    // The request server name,
    String rhost = request.getServerName();

    JettyMckoiWebAppContext found_context = null;
    int found_path_str_length = 0;

//    System.out.println("  ()(START)()");

    // Query the lookups list and the first entry that matches the input
    // we break from the loop.
    synchronized (lookups) {
      // Iterate over all the locations defined on this account. If more than
      // one prefix matches, the prefix that is longest is picked. For example,
      // given one application that has a path prefix of '/'  and another that
      // has a path prefix of '/mail/', the '/' application will be picked for
      // any request other than '/mail/[something]'.
      for (WALookup lookup : lookups) {

        if (rhost.equals(lookup.domain)) {
          String lproto = lookup.protocol;
          // Match protocol,
          if (lproto.equals("*") || lproto.equals(rscheme)) {
            // Finally see if the target starts with the matched path,
            if (target.startsWith(lookup.path)) {
              int path_str_length = lookup.path.length();
              if (path_str_length > found_path_str_length) {
                found_context = lookup.context;
                found_path_str_length = path_str_length;
              }
            }
          }
        }
      }
    }

    if (found_context != null) {

      PlatformContextImpl.setUserClassLoader(
                             found_context.getUserClassLoader());
      PlatformContextImpl.setApplicationClassLoader(
                             found_context.getClassLoader());

      // Start the context if it's not started,
      if (!found_context.isStarted()) {
        try {
          found_context.start();
        }
        catch (Exception e) {
          e.printStackTrace(System.err);
          throw new RuntimeException(
                                  "Failed to start application context.", e);
        }
      }

      // Handle the request,
      found_context.handle(target, jetty_request, request, response);

    }

  }



  // ----------

  /**
   * Inner class representing a lookup of a vhost and context path to the
   * web app context that manages it.
   */
  private static class WALookup {

    private final String id;
    private final String name;
    private final String domain;
    private final String protocol;
    private final String path;
    private JettyMckoiWebAppContext context;

    public WALookup(String id, String name,
                    String domain, String protocol,
                    String path, JettyMckoiWebAppContext context) {
      this.id = id;
      this.name = name;
      this.domain = domain;
      this.protocol = protocol;
      this.path = path;
      this.context = context;
    }

  }

}
