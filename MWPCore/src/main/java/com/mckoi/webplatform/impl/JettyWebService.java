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

import com.mckoi.mwpcore.SSLExtraConfig;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * The invocation point for starting a Jetty embedded application server
 * on this machine.
 *
 * @author Tobias Downer
 */

class JettyWebService {

  /**
   * Extra SSL configuration properties.
   */
  private final SSLExtraConfig ssl_extras;

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
  private final PlatformContextBuilder context_builder;

  /**
   * Constructor.
   */
  public JettyWebService(PlatformContextBuilder context_builder,
                         SSLExtraConfig ssl_extras) {

    this.context_builder = context_builder;
    this.ssl_extras = ssl_extras;

    // Create the Jetty context,
    this.server = new JettyMckoiServer();
    
    this.context_builder.setServer(server);

  }

  /**
   * Starts the Jetty web service. Returns immediately.
   */
  void start() throws Exception, InterruptedException {

    // Add the request handler,
    JettyMckoiRequestHandler handler =
                                new JettyMckoiRequestHandler(context_builder);

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
      
      ServerConnector http_connector = new ServerConnector(server);
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

      SslContextFactory cf = new SslContextFactory();
      cf.setKeyStorePath(key_store_file);
      cf.setKeyStorePassword(key_store_pwd);
      cf.setKeyManagerPassword(key_store_manager_pwd);
      // Cipher excludes,
      String[] cipher_patterns = ssl_extras.getCipherSuitesToExclude();
      if (cipher_patterns != null) {
        cf.addExcludeCipherSuites(cipher_patterns);
      }
//      // Renegotiation allowed,
//      Boolean renegotiation = ssl_extras.getRenegotiationAllowed();
//      if (renegotiation != null) {
//        cf.setAllowRenegotiate(renegotiation);
//      }
      // Protocols to exclude,
      String[] protocols = ssl_extras.getExcludedProtocols();
      if (protocols != null) {
        cf.addExcludeProtocols(protocols);
      }

      HttpConfiguration https_config = new HttpConfiguration();
//      https_config.setSecurePort(https_port_i);  // default: 443
      https_config.addCustomizer(new SecureRequestCustomizer());

      ServerConnector https_connector =
          new ServerConnector(server,
              new SslConnectionFactory(cf, HttpVersion.HTTP_1_1.asString()),
              new HttpConnectionFactory(https_config));
      https_connector.setHost(https_address);
      https_connector.setPort(https_port_i);

      server.addConnector(https_connector);
      try {
        https_connector.start();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      current_https_connector = https_connector;

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
