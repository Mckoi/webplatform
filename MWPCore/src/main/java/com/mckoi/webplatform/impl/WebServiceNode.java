/**
 * com.mckoi.webplatform.impl.WebServiceNode  May 11, 2010
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
import com.mckoi.mwpcore.GeneralLogFormatter;
import com.mckoi.mwpcore.MWPClassLoaderSet;
import com.mckoi.mwpcore.SSLExtraConfig;
import com.mckoi.process.impl.ProcessClientService;
import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.JavaUtilLog;

/**
 * The application invocation point for a web service node on the network.
 * This starts the web server on port 80 ready to start processing
 * web requests.
 *
 * @author Tobias Downer
 */

public class WebServiceNode {

  private JettyWebService http_service;

  private String http_port;
  private String http_address;
  private String https_port;
  private String https_address;
  private String key_store_file;
  private String key_store_pwd;
  private String key_store_manager_pwd;

  private boolean init_complete = false;

  private File local_temp_folder;

  // Set via pre-security init,
  private DBSessionCache sessions_cache;
  private ProcessClientService process_client_service;

  private Logger JETTY_LOG;

  /**
   * The Jetty logs that we don't log the warnings of.
   */
  private static final Set<String> ignoreWarningsLogs;
  static {
    // A set of class names of Jetty logs that we ignore the warnings from,
    Set<String> ignore_warnings = new HashSet<>();
//    ignore_warnings.add("org.eclipse.jetty.http.HttpGenerator");
    ignoreWarningsLogs = Collections.unmodifiableSet(ignore_warnings);
  }


  /**
   * Constructor.
   */
  public WebServiceNode() {
  }


  /**
   * Initialization test.
   */
  private void initCheck() {
    if (!init_complete) {
      throw new RuntimeException("Not initialized");
    }
  }

  /**
   * JavaUtilLog with a custom 'newLogger' method.
   */
  private static class MckoiJavaUtilLog extends JavaUtilLog {
    @Override
    public org.eclipse.jetty.util.log.Logger newLogger(String name) {
      return newJettyLogger(name);
    }
  }

  /**
   * Creates a new Jetty logger. In our case, we filter it through to a Java
   * logger.
   */
  private static org.eclipse.jetty.util.log.Logger newJettyLogger(String name) {
    MckoiJavaUtilLog log = new MckoiJavaUtilLog();
    if (ignoreWarningsLogs.contains(name)) {
      return new JettyIgnoreWarningsLogger(log);
    }
    return log;
  }

  /**
   * Pre security initialization.
   * 
   * @param web_config
   * @param sessions_cache
   * @param process_client_service
   */
  public void preSecurityInit(Properties web_config,
                              DBSessionCache sessions_cache,
                              ProcessClientService process_client_service) {

    if (web_config == null) throw new NullPointerException();
    if (sessions_cache == null) throw new NullPointerException();
    if (process_client_service == null) throw new NullPointerException();

    if (init_complete) {
      throw new RuntimeException("Initialized");
    }

    // The sessions cache,
    this.sessions_cache = sessions_cache;
    // Create the process client service,
    this.process_client_service = process_client_service;

    final String log_path = web_config.getProperty("log_dir", null);
    final String log_level = web_config.getProperty("log_level", "INFO");

    int logfile_limit = (1 * 1024 * 1024);
    String lf_limit = web_config.getProperty("logfile_limit");
    if (lf_limit != null) {
      logfile_limit = Integer.parseInt(lf_limit);
    }
    int logfile_count = 4;
    String lf_count = web_config.getProperty("logfile_count");
    if (lf_count != null) {
      logfile_count = Integer.parseInt(lf_count);
    }

    // -------------------------------------------
    // ---- Hacky SecurityManager workarounds ----
    // -------------------------------------------
    // These are calls that are necessary because they perform operations
    // within static methods that need privileges that are not allowed
    // by the security context they normally get initially used in.
    //
    // These calls are a little awkward to have to support. Perhaps it
    // could be generalized to a bunch of class and method calls so that
    // we can support these hacks in all environments.

    // 'javax.servlet.http.Cookie' checks the
    // "org.glassfish.web.rfc2109_cookie_names_enforced" system property in
    // a static method.
    Object c = new javax.servlet.http.Cookie("cookieinit", "testval");

    // The static methods in PlatformContextBuilder distribute an object that
    // permits certain classes to switch contexts.
    Class<PlatformContextBuilder> clazz = PlatformContextBuilder.class;
    
    // Jetty introduced a ShutdownMonitor object that has a way of reading
    // system properties that requires read, write access to all, which we'd
    // prefer not to have to allow.
    try {
      Object jetty_shutdownmonitor =
                    org.eclipse.jetty.server.ShutdownMonitor.getInstance();
    }
    catch (Throwable e) {
      // Ignore because of future updates,
      System.err.println("WARNING: Ignoring Exception during initialization;");
      e.printStackTrace(System.err);
    }


    try {

      // Set up the web node logging,

      // Use JavaUtilLog, but change 'getLogger' to return the same instance
      // so that log entries always end up going to the same handlers defined
      // below.
      org.eclipse.jetty.util.log.Logger logger = new MckoiJavaUtilLog();
      org.eclipse.jetty.util.log.Log.setLog(logger);
      org.eclipse.jetty.util.log.Log.initialized();

      // Get the Java logger,
      Logger log = Logger.getLogger(logger.getName());
      // The debug output level,
      log.setLevel(Level.parse(log_level.toUpperCase(Locale.ENGLISH)));
      // Don't propagate log messages,
      log.setUseParentHandlers(false);

      // Output to the log file,
      String log_file_name = new File(log_path, "jetty.log").getCanonicalPath();
      FileHandler fhandler =
            new FileHandler(log_file_name, logfile_limit, logfile_count, true);
      fhandler.setFormatter(new GeneralLogFormatter());
      log.addHandler(fhandler);

      // Log start message,
      log.log(Level.INFO, "Jetty Init ({0})",
              new File(".").getCanonicalPath());

      JETTY_LOG = log;

      System.out.println("  Jetty Log = " + log_file_name);
      System.out.println("  Log Level = " + log_level);

      // Set AWT headless mode,
      System.setProperty("java.awt.headless", "true");

      // The local temporary folder,
      String temporary_dir = web_config.getProperty("temporary_dir", "./temp/");
      local_temp_folder = new File(temporary_dir).getCanonicalFile();
      if (!local_temp_folder.isDirectory()) {
        throw new RuntimeException(
                        "Couldn't find temporary directory: " + temporary_dir);
      }

      System.out.println("  Temp Directory = " + local_temp_folder);

      // Set the Jetty version property,
      String property = System.getProperty("jetty.version");
      if (property == null) {
        System.setProperty("jetty.version", Server.getVersion());
      }

    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Initialization.
   * 
   * @param web_config
   * @param ssl_extras
   * @param allowed_sys_classes
   * @param classloaders
   */
  public void init(Properties web_config,
                   SSLExtraConfig ssl_extras,
                   ClassNameValidator allowed_sys_classes,
                   MWPClassLoaderSet classloaders) {

    if (web_config == null) throw new NullPointerException();
    if (allowed_sys_classes == null) throw new NullPointerException();

    if (init_complete) {
      throw new RuntimeException("Initialized");
    }

    try {

      // Fetch the properties from the properties file,
      http_port = web_config.getProperty("http_port", "80");
      http_address = web_config.getProperty("http_address", null);
      https_port = web_config.getProperty("https_port", "443");
      https_address = web_config.getProperty("https_address", null);

      key_store_file = web_config.getProperty("key_store_file", null);
      key_store_pwd = web_config.getProperty("key_store_pwd", null);
      key_store_manager_pwd =
              web_config.getProperty("key_store_manager_pwd", null);

      // Normalize the keystore file location,
      if (key_store_file != null) {
        key_store_file = new File(key_store_file).getCanonicalPath();
      }

      // The local JVM system timer thread,
      Timer system_timer = new Timer("Mckoi Web Platform System Timer");

      // The PlatformContextBuilder,
      PlatformContextBuilder context_builder = new PlatformContextBuilder(
              sessions_cache, process_client_service, system_timer,
              allowed_sys_classes, classloaders, local_temp_folder);

      // Start the Jetty web service,
      http_service = new JettyWebService(context_builder, ssl_extras);

      // Init successful
      init_complete = true;

    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Start the http service.
   */
  public void start() throws Exception {
    initCheck();
    http_service.start();
  }  
  
  /**
   * Stops the http service.
   */
  public void stop() {
    initCheck();
    http_service.stop();
  }

  /**
   * Starts accepting http connections.
   */
  public void startHttp() {
    initCheck();
    int http_port_i = Integer.parseInt(http_port);
    // This is done by adding the http connector,
    http_service.startHTTP(http_address, http_port_i);

    System.out.println(MessageFormat.format(
            "Jetty HTTP Started on ''{0}'' port {1}",
            http_address, http_port));

  }

  /**
   * Stops accepting http connections.
   */
  public void stopHttp() {
    initCheck();
    http_service.stopHTTP();

    System.out.println(MessageFormat.format(
            "Jetty HTTP Stopped on ''{0}'' port {1}",
            http_address, http_port));

  }

  /**
   * Starts accepting https connections.
   */
  public void startHttps() {
    initCheck();
    int https_port_i = Integer.parseInt(https_port);
    
    if (key_store_file == null || key_store_pwd == null ||
        key_store_manager_pwd == null) {
      JETTY_LOG.log(Level.SEVERE,
              "Unable to start HTTPS service because keystore information is not complete.");
      throw new RuntimeException(
              "Unable to start HTTPS service because keystore information is not complete.");
    }

    http_service.startHTTPS(https_address, https_port_i,
                            key_store_file, key_store_pwd,
                            key_store_manager_pwd);

    System.out.println(MessageFormat.format(
            "Jetty HTTPS Started on ''{0}'' port {1}",
            https_address, https_port));

  }
  
  /**
   * Stops accepting https connections.
   */
  public void stopHttps() {
    initCheck();
    http_service.stopHTTPS();

    System.out.println(MessageFormat.format(
            "Jetty HTTPS Stopped on ''{0}'' port {1}",
            https_address, https_port));

  }


  private static class JettyIgnoreWarningsLogger
                            extends org.eclipse.jetty.util.log.AbstractLogger {

    private final JavaUtilLog parent;

    public JettyIgnoreWarningsLogger(JavaUtilLog parent) {
      this.parent = parent;
    }

    @Override
    protected org.eclipse.jetty.util.log.Logger newLogger(String fullname) {
      return newJettyLogger(fullname);
    }

    @Override
    public String getName() {
      return parent.getName();
    }

    @Override
    public void warn(String msg, Object... args) {
      // Warn turns into 'debug' messages,
      parent.debug(msg, args);
    }

    @Override
    public void warn(Throwable thrown) {
      parent.debug(thrown);
    }

    @Override
    public void warn(String msg, Throwable thrown) {
      parent.debug(msg, thrown);
    }

    @Override
    public void info(String msg, Object... args) {
      parent.info(msg, args);
    }

    @Override
    public void info(Throwable thrown) {
      parent.info(thrown);
    }

    @Override
    public void info(String msg, Throwable thrown) {
      parent.info(msg, thrown);
    }

    @Override
    public boolean isDebugEnabled() {
      return parent.isDebugEnabled();
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
      parent.setDebugEnabled(enabled);
    }

    @Override
    public void debug(String msg, Object... args) {
      parent.debug(msg, args);
    }

    @Override
    public void debug(Throwable thrown) {
      parent.debug(thrown);
    }

    @Override
    public void debug(String msg, Throwable thrown) {
      parent.debug(msg, thrown);
    }

    @Override
    public void ignore(Throwable ignored) {
      parent.ignore(ignored);
    }

  }

}
