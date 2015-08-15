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

package com.mckoi.webplatform.impl;

import com.mckoi.appcore.SystemStatics;
import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPClassLoaderSet;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.process.impl.ProcessClientService;
import com.mckoi.webplatform.jetty.websocket.MckoiWebSocketServerFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import javax.servlet.ServletRequest;
import org.eclipse.jetty.server.Server;

/**
 * This is a static object per account per classloader that sets up a
 * platform context given a URI request.
 *
 * @author Tobias Downer
 */

public final class PlatformContextBuilder {

  /**
   * The static permission object needed by callees to create and exit contexts.
   */
  final static Object CONTEXT_GRANT = new Object();
  static {
    JettyMckoiRequestHandler.givePlatformContextGrant(CONTEXT_GRANT);
    MckoiWebSocketServerFactory.givePlatformContextGrant(CONTEXT_GRANT);
  }

  /**
   * The Server object.
   */
  private Server server = null;

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
  private final Map<String, JettyMckoiWebAppContextSet> webapp_map =
                                                              new HashMap<>();
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
   * 
   * @param sessions_cache
   * @param process_client_service
   * @param system_timer
   * @param general_allowed_sys_classes
   * @param classloaders
   * @param local_temp_folder 
   */
  PlatformContextBuilder(
                  DBSessionCache sessions_cache,
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

  void setServer(JettyMckoiServer server) {
    if (this.server == null) {
      this.server = server;
    }
  }
  
  DBSessionCache getSessionsCache() {
    return sessions_cache;
  }

  ProcessClientService getProcessClientService() {
    return process_client_service;
  }
  
  Timer getSystemTimer() {
    return system_timer;
  }
  
  ClassNameValidator getClassNameValidator() {
    return general_allowed_sys_classes;
  }
  
  MWPClassLoaderSet getClassLoaderSet() {
    return classloaders;
  }
  
  File getLocalTempDir() {
    return local_temp_folder;
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
   * 
   * @param protocol either 'http' or 'https'
   * @param server_address the vhost string.
   */
  private String getAccountForVHost(final String protocol,
                                    final String server_address) {

    // Assert,
    if (server_address.contains("/") || server_address.contains(":")) {
      throw new IllegalStateException();
    }

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
                                          this, account_name, account_logger);
        webapp_map.put(account_name, web_app);
        web_app.setServer(server);
      }
    }

    return web_app;
  }

  /**
   * Throws a SecurityException if the given grant object does not match the
   * internal CONTEXT_GRANT.
   * 
   * @param grant_object 
   */
  private void checkGrantObject(Object grant_object) {
    if (grant_object != CONTEXT_GRANT) {
      throw new SecurityException();
    }
  }

  /**
   * Enters the platform context for the given vhost server name.
   * 
   * @param grant_object
   * @param server_name
   * @param is_secure
   * @return 
   */
  public JettyMckoiWebAppContextSet enterWebContext(
                  Object grant_object, String server_name, boolean is_secure) {

    // Must provide the correct context grant to use this method,
    checkGrantObject(grant_object);

    // The protocol,
    String protocol;
    if (is_secure) {
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
      return null;
    }

    Thread.currentThread().setContextClassLoader(
                            JettyMckoiRequestHandler.class.getClassLoader());

    // Set the thread context,
    PlatformContextImpl.setCurrentThreadContextForAppService(
                                  this, account_name, server_name, protocol);

    // Create the web app context for this account,
    JettyMckoiWebAppContextSet context =
                                   getWebAppContextForAccount(account_name);

    // Set the platform context logging,
    PlatformContextImpl.setCurrentThreadLogger(context.getLogSystem());

    // Set the allowed system classes validator object,
    PlatformContextImpl.setAllowedSystemClasses(getClassNameValidator());

    return context;

  }

  /**
   * Enters the platform context for the given URI.
   * 
   * @param grant_object
   * @param request
   * @return 
   */
  public JettyMckoiWebAppContextSet enterWebContext(
                                Object grant_object, ServletRequest request) {

    return enterWebContext(
                  grant_object, request.getServerName(), request.isSecure());
    
  }
  
  /**
   * Exits the current web context. If 'enterWebContext' returned an object
   * then this must be called.
   * 
   * @param grant_object
   */
  public void exitWebContext(Object grant_object) {
    // Must provide the correct context grant to use this method,
    checkGrantObject(grant_object);
    // Make sure we remove the current thread context,
    PlatformContextImpl.removeCurrentThreadContext();
  }

}
