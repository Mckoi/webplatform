/**
 * com.mckoi.webplatform.impl.JettyMckoiRequestHandler  May 11, 2010
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

import com.mckoi.appcore.SystemStatics;
import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPClassLoaderSet;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.process.impl.ProcessClientService;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
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

class JettyMckoiRequestHandler extends AbstractHandler {

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  /**
   * The ProcessClientService that supports messaging to process services on
   * the network.
   */
  private final ProcessClientService process_client_service;

  /**
   * The cache from account name to web app contexts.
   */
  private HashMap<String, JettyMckoiWebAppContextSet> webapp_map = new HashMap();
  private final Object webapp_map_lock = new Object();

  /**
   * The Timer used to schedule timed event (such as flushing logs).
   */
  private final Timer system_timer;

  /**
   * The generally allowed system classes.
   */
  private final ClassNameValidator general_allowed_sys_classes;

  /**
   * The MWP class loaders.
   */
  private final MWPClassLoaderSet classloaders;

  /**
   * The location of the local temporary folder.
   */
  private final File local_temp_folder;


  /**
   * Constructor.
   */
  JettyMckoiRequestHandler(DBSessionCache sessions_cache,
                           ProcessClientService process_client_service,
                           Timer system_timer,
                           ClassNameValidator general_allowed_sys_classes,
                           MWPClassLoaderSet classloaders,
                           File local_temp_folder) {
    this.sessions_cache = sessions_cache;
    this.process_client_service = process_client_service;
    this.system_timer = system_timer;
    this.general_allowed_sys_classes = general_allowed_sys_classes;
    this.classloaders = classloaders;
    this.local_temp_folder = local_temp_folder;
  }


  /**
   * Returns a thread-safe transaction object representing the sysplatform
   * path.
   */
  private ODBTransaction getSystemPathTransaction() {
    return sessions_cache.getODBTransaction(SystemStatics.SYSTEM_PATH);
  }

  /**
   * Looks up a vhost address and returns the account name for the host.
   * Returns null if the vhost wasn't found.
   */
  private String getAccountForVHost(final String protocol,
                                    final String server_address) {

    // Turn it into a qualified address
    // (eg. "http:mydomain.com")
    final String qual_address = server_address + ":" + protocol;

    // TODO: This query probably needs to get cached,

    // Create and query the transaction snapshot,
    ODBTransaction t = getSystemPathTransaction();

    // The vhosts index
    ODBObject vhost_index = t.getNamedItem("vhosts");
    // The index of domain objects,
    ODBList domain_idx = vhost_index.getList("domainIdx");
    // Query the index,
    ODBList domain_index = domain_idx.tail(qual_address);

    ODBObject account = null;

    // If the index returned something
    if (domain_index.size() > 0) {
      // Check if the first entry is the same as the domain name,
      ODBObject vhost_ob = domain_index.getObject(0);
      if (vhost_ob.getString("domain").equals(qual_address)) {
        // Yes, so set the account name,
        account = vhost_ob.getObject("account");
      }
    }

    // Return the account name (may return null),
    if (account == null) {
      return null;
    }
    else {
      return account.getString("account");
    }

  }

  /**
   * Returns the JettyMckoiWebAppContextSet for the given account name.
   */
  private JettyMckoiWebAppContextSet
                             getWebAppContextForAccount(String account_name) {

    JettyMckoiWebAppContextSet web_app;

    // Is it in the cache?
    synchronized (webapp_map_lock) {
      web_app = webapp_map.get(account_name);
      // Not in cache so create it.
      if (web_app == null) {

        // Create the account log system,
        LoggerService account_logger = new LoggerService(sessions_cache,
                                           "ufs" + account_name, system_timer);

        web_app = new JettyMckoiWebAppContextSet(
                                sessions_cache, account_name,
                                system_timer, local_temp_folder,
                                general_allowed_sys_classes, classloaders,
                                account_logger);
        webapp_map.put(account_name, web_app);
        web_app.setServer(getServer());
      }
    }

    return web_app;
  }





  /**
   * Handle the page request.
   */
  @Override
  public void handle(String target, Request jetty_request,
                     HttpServletRequest request, HttpServletResponse response)
                                        throws IOException, ServletException {

    // Get the server name from the HTTP request,
    String server_name = request.getServerName();

    // The protocol,
    String protocol;
    if (request.isSecure()) {
      protocol = "https";
    }
    else {
      protocol = "http";
    }

    // TODO: Handle case-sensitivity in a compatible way with DNS
    server_name = server_name.toLowerCase();

    // Translate the server name into an app domain and process accordingly,
    String account_name = getAccountForVHost(protocol, server_name);

    // Account name wasn't defined so report error,
    if (account_name == null) {
      return;
    }

    // PENDING: Check this server is authorized to serve up pages for this
    //  account.



    try {

      Thread.currentThread().setContextClassLoader(
                              JettyMckoiRequestHandler.class.getClassLoader());

      // Set the thread context,
      PlatformContextImpl.setCurrentThreadContext(
                          true,  // is_app_service_context
                          sessions_cache,
                          process_client_service, null,
                          account_name, server_name, protocol);

      // Create the web app context for this account,
      JettyMckoiWebAppContextSet context =
                                     getWebAppContextForAccount(account_name);

      // Set the platform context logging,
      PlatformContextImpl.setCurrentThreadLogger(context.getLogSystem());

      // If it hasn't been started,
      if (!context.isStarted()) {
        try {
          // Start it,
          context.start();
        }
        catch (Exception e) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                             e.getMessage());
          // PENDING: Put this error in a log,
          e.printStackTrace(System.err);
          return;
        }
      }

      // If the context is still starting, respond with a service unavailable
      if (context.isStarting()) {
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           "Service unavailable while context is starting");
        return;
      }

      // Context is started, so delegate the request to the web app,
      context.handle(target, jetty_request, request, response);

      // If the request isn't handled we put out a 404
      if (!jetty_request.isHandled() && !response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }

    }
    finally {
      // Make sure we remove the current thread context,
      PlatformContextImpl.removeCurrentThreadContext();
    }

  }

}
