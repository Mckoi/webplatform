/**
 * com.mckoi.webplatform.impl.JettyWebService  Apr 17, 2011
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

import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPClassLoaderSet;
import com.mckoi.mwpcore.SSLExtraConfig;
import com.mckoi.process.impl.ProcessClientService;
import java.io.File;
import java.util.Timer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * The invocation point for starting a Jetty embedded application server
 * on this machine.
 *
 * @author Tobias Downer
 */

class JettyWebService {

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  /**
   * The ProcessClientService that supports communication with processes on
   * the network.
   */
  private final ProcessClientService process_client_service;

  /**
   * The system timer.
   */
  private final Timer system_timer;

  /**
   * A class name validator for generally allowed system classes.
   */
  private final ClassNameValidator general_allowed_sys_classes;

  /**
   * The MWP class loaders.
   */
  private final MWPClassLoaderSet classloaders;

  /**
   * Extra SSL configuration properties.
   */
  private final SSLExtraConfig ssl_extras;

  /**
   * The location of the local temporary folder.
   */
  private final File local_temp_folder;

  /**
   * The server.
   */
  private final JettyMckoiServer server;
  
  /**
   * The current HTTP connector.
   */
  private Connector current_http_connector = null;

  /**
   * The current HTTPS connector.
   */
  private Connector current_https_connector = null;

  /**
   * Constructor.
   */
  public JettyWebService(DBSessionCache sessions_cache,
                         ProcessClientService process_client_service,
                         Timer system_timer,
                         ClassNameValidator general_allowed_sys_classes,
                         MWPClassLoaderSet classloaders,
                         SSLExtraConfig ssl_extras,
                         File local_temp_folder) {

    this.sessions_cache = sessions_cache;
    this.process_client_service = process_client_service;
    this.system_timer = system_timer;
    this.general_allowed_sys_classes = general_allowed_sys_classes;
    this.classloaders = classloaders;
    this.ssl_extras = ssl_extras;
    this.local_temp_folder = local_temp_folder;

    // Create the Jetty context,
    this.server = new JettyMckoiServer();
    this.server.setGracefulShutdown(4500);
  }

  /**
   * Starts the Jetty web service. Returns immediately.
   */
  void start() throws Exception, InterruptedException {

    // Add the request handler,
    JettyMckoiRequestHandler handler = new JettyMckoiRequestHandler(
                 sessions_cache, process_client_service, system_timer,
                 general_allowed_sys_classes, classloaders,
                 local_temp_folder);

    server.setHandler(handler);

    // Start the server, etc.
    server.start();

  }

  /**
   * Stops the Jetty web service.
   */
  void stop() {
    try {
      if (current_http_connector != null) {
        current_http_connector.stop();
      }
    }
    catch (Exception e) { /* ignore */ }
    try {
      if (current_https_connector != null) {
        current_https_connector.stop();
      }
    }
    catch (Exception e) { /* ignore */ }
    try {
      server.stop();
    }
    catch (Exception e) {
      throw new RuntimeException("Error shutting down", e);
    }
  }

  /**
   * Starts the HTTP service on a running jetty web service.
   */
  void startHTTP(String http_address, int http_port_i) {
    if (current_http_connector == null) {
      
      Connector http_connector = new SelectChannelConnector();
      http_connector.setHost(http_address);
      http_connector.setPort(http_port_i);  // default: 80
      server.addConnector(http_connector);
      try {
        http_connector.start();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      current_http_connector = http_connector;

    }
    else {
      throw new RuntimeException("http service already started");
    }
  }

  /**
   * Starts the HTTPS service on a running jetty web service.
   */
  void startHTTPS(String https_address, int https_port_i,
                  String key_store_file, String key_store_pwd,
                  String key_store_manager_pwd) {

    if (current_https_connector == null) {

      // Configure the secure https connector,
      SslSelectChannelConnector ssl_connector = new SslSelectChannelConnector();
      ssl_connector.setHost(https_address);
      ssl_connector.setPort(https_port_i);  // default: 443
      SslContextFactory cf = ssl_connector.getSslContextFactory();
      cf.setKeyStorePath(key_store_file);
      cf.setKeyStorePassword(key_store_pwd);
      cf.setKeyManagerPassword(key_store_manager_pwd);
      // Cipher excludes,
      String[] cipher_patterns = ssl_extras.getCipherSuitesToExclude();
      if (cipher_patterns != null) {
        cf.addExcludeCipherSuites(cipher_patterns);
      }
      // Renegotiation allowed,
      Boolean renegotiation = ssl_extras.getRenegotiationAllowed();
      if (renegotiation != null) {
        cf.setAllowRenegotiate(renegotiation);
      }
      // Protocols to exclude,
      String[] protocols = ssl_extras.getExcludedProtocols();
      if (protocols != null) {
        cf.addExcludeProtocols(protocols);
      }

      server.addConnector(ssl_connector);
      try {
        ssl_connector.start();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      current_https_connector = ssl_connector;

    }
    else {
      throw new RuntimeException("https service already started");
    }
  }

  /**
   * Stops the HTTP service on a running Jetty web service.
   */
  void stopHTTP() {
    if (current_http_connector != null) {
      try {
        current_http_connector.stop();
      }
      catch (Exception e) {
        e.printStackTrace(System.err);
      }
      server.removeConnector(current_http_connector);
      current_http_connector = null;
    }
  }
  
  /**
   * Stops the HTTPS service on a running Jetty web service.
   */
  void stopHTTPS() {
    if (current_https_connector != null) {
      try {
        current_https_connector.stop();
      }
      catch (Exception e) {
        e.printStackTrace(System.err);
      }
      server.removeConnector(current_https_connector);
      current_https_connector = null;
    }
  }

}
