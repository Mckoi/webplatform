/**
 * com.mckoi.mwpcore.MWPCoreProcess  Mar 18, 2012
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

package com.mckoi.mwpcore;

import com.mckoi.network.MckoiDDBClient;
import com.mckoi.network.MckoiDDBClientUtils;
import com.mckoi.network.NetworkConfigResource;
import com.mckoi.process.impl.ProcessClientService;
import com.mckoi.process.impl.ProcessServerService;
import com.mckoi.webplatform.buildtools.SystemBuildStatics;
import com.mckoi.webplatform.impl.DataFileJarFile;
import com.mckoi.webplatform.impl.WebServiceNode;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.Policy;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * The core process invocation point for the HTTP, HTTPS servers and the
 * process server.
 *
 * @author Tobias Downer
 */

public class MWPCoreProcess extends AbstractCoreProcess {

  /**
   * The web service object.
   */
  private final WebServiceNode web_service_node;

  /**
   * The process service object.
   */
  private final ProcessServerService process_service_node;

  /**
   * Constructor.
   */
  private MWPCoreProcess() {
    super();

    web_service_node = new WebServiceNode();
    process_service_node = new ProcessServerService();

  }

  /**
   * Initializes the security manager.
   */
  private static void initSecurity(
          String app_home_path, String install_home_path,
          URL security_policy_url,
          ClassNameValidator allowed_sys_classes) throws IOException {

    // Build the list of trusted class loaders
    MckoiDDBAppSecurityManager.makeTrustedClassLoaders(
                                        MWPCoreProcess.class.getClassLoader());

    // Normalize the home path
    String normalized_app_path = new File(app_home_path).getCanonicalPath();
    String normalized_install_path =
                              new File(install_home_path).getCanonicalPath();

    // If the security policy is not disabled,
    if (security_policy_url != null) {
      // Set the security manager,
      MckoiDDBAppPolicy policy = new MckoiDDBAppPolicy();
      policy.load(security_policy_url,
                  normalized_app_path, normalized_install_path);
      policy.setThisAsCurrentMckoiPolicy();

      Policy.setPolicy(policy);
      MckoiDDBAppSecurityManager security_manager =
                            new MckoiDDBAppSecurityManager(allowed_sys_classes);
      System.setSecurityManager(security_manager);
    }

//    // Set up the Rhino security controller,
//    SecurityController.initGlobal(new MWPRhinoSecurityController());

    // Now this JVM is running under our custom security manager.

  }  

  /**
   * Perform some customized initialization stuff.
   */
  @Override
  protected void init() {
    super.init();

    try {

//      // Set/update the URL protocols system property,
//      String protocols = System.getProperty("java.protocol.handler.pkgs");
//      if (protocols == null) {
//        System.setProperty("java.protocol.handler.pkgs",
//                                         "com.mckoi.webplatform.protocols");
//      }
//      else if (!protocols.contains("com.mckoi.webplatform.protocols")){
//        System.setProperty("java.protocol.handler.pkgs",
//                           protocols + "|" + "com.mckoi.webplatform.protocols");
//      }

      // Set up the URL stream handler factory for the 'mwpfs' URL protocol,
      URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
          if (protocol.equals("mwpfs")) {
            return new com.mckoi.webplatform.impl.MWPFSURLStreamHandler();
          }
          else if (protocol.equals("jar")) {
            return new com.mckoi.webplatform.impl.MWPFSURLJarStreamHandler();
          }
          return null;
        }
      });

      // Create the Java compiler.
      // We have to do this before we employ the security policy because the
      // ToolProvider insists on loading the compiler on its own ClassLoader.

      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

      // If we can't, then report the error and exit
      if (compiler == null) {
        System.out.println("FAILED: ");
        System.out.println("JavaCompiler not found. Are you using 1.6 JDK (not JRE)?");
        return;
      }

      // Set the compiler in the JDK6Compiler class,
      SystemBuildStatics.setJVMJavaCompiler(compiler);
      // Find all the symbols for the system class loader/properties,
      SystemBuildStatics.buildSystemCompilerSymbols();

      // The app service properties,
      // This contains the location of the security policy, the allowed
      // classes file, and SSL information from the installs path.
      Properties app_service_config = new Properties();
      app_service_config.load(
                new FileInputStream(new File("conf/app_service.properties")));



      // Create the web service node properties,
      Properties web_config = new Properties();
      SSLExtraConfig ssl_extras = new SSLExtraConfig();

      web_config.setProperty("log_dir", getLogPath().getCanonicalPath());
      web_config.setProperty("network_config", getNetworkConf().toString());
      web_config.setProperty("mckoi_client", getClientConf().toString());
      web_config.setProperty("temporary_dir", getTempPath().toString());

      // Copy the http and https port values
      String http_port_value = app_service_config.getProperty("http_port");
      String https_port_value = app_service_config.getProperty("https_port");

      String key_store_file_val =
                              app_service_config.getProperty("key_store_file");
      if (key_store_file_val != null) {
        web_config.setProperty("key_store_file", key_store_file_val);
        String key_store_pwd = app_service_config.getProperty("key_store_pwd");
        String key_store_manager_pwd =
                       app_service_config.getProperty("key_store_manager_pwd");

        // Assert,
        if (key_store_pwd == null || key_store_manager_pwd == null) {
          throw new RuntimeException(
              "'key_store_pwd' or 'key_store_manager_pwd' properties are not defined in the app_service.properties file.");
        }

        web_config.setProperty("key_store_pwd", key_store_pwd);
        web_config.setProperty("key_store_manager_pwd", key_store_manager_pwd);
        
        // Excluded cipher suites
        String excluded_ciphers =
                  app_service_config.getProperty("ssl_exclude_cipher_suites");
        if (excluded_ciphers != null && excluded_ciphers.length() > 0) {
          String[] ciphers = excluded_ciphers.split(",");
          for (int i = 0; i < ciphers.length; ++i) {
            ciphers[i] = ciphers[i].trim();
          }
          ssl_extras.setCipherSuitesToExclude(ciphers);
        }

        // SSL renegotiation allowed?
        String renegotiation =
                  app_service_config.getProperty("ssl_renegotiation_allowed");
        if (renegotiation != null) {
          renegotiation = renegotiation.toLowerCase();
          if (renegotiation.equals("false")) {
            ssl_extras.setRenegotiationAllowed(false);
          }
          else if (renegotiation.equals("true")) {
            ssl_extras.setRenegotiationAllowed(true);
          }
        }

        // Protocols to exclude,
        String excluded_protocols =
                  app_service_config.getProperty("ssl_exclude_protocols");
        if (excluded_protocols != null && excluded_protocols.length() > 0) {
          String[] protocols = excluded_protocols.split(",");
          for (int i = 0; i < protocols.length; ++i) {
            protocols[i] = protocols[i].trim();
          }
          ssl_extras.setExcludedProtocols(protocols);
        }

      }

      // HTTP and HTTPS are public interface facing,
      web_config.setProperty("http_address", getPublicIp());
      web_config.setProperty("https_address", getPublicIp());

      // HTTP and HTTPS port values (if defined),
      if (http_port_value != null) {
        web_config.setProperty("http_port", http_port_value);
      }
      if (https_port_value != null) {
        web_config.setProperty("https_port", https_port_value);
      }

      // Create the process service node properties,
      Properties process_config = new Properties();

      process_config.setProperty("log_dir", getLogPath().getCanonicalPath());
      // Process service is private interface facing,
      process_config.setProperty("process_address", getPrivateIp());
      // The net interface,
      if (getNetInterface() != null) {
        process_config.setProperty("net_interface", getNetInterface());
      }
      // The location of the 'client.conf' file.
      process_config.setProperty("mckoi_client", getClientConf().toString());



      // Install home path
      String install_home_path = new File(".").getCanonicalPath();
      String base_home_page = new File("../../../").getCanonicalPath();

      // Is security policy disabled?
      String security_disabled =
                app_service_config.getProperty("security_disabled", "false");
      boolean b_security_disabled = security_disabled.equals("true");

      URL security_policy_url = null;
      if (!b_security_disabled) {
        // Security policy file,
        String security_policy_location =
                             app_service_config.getProperty("security_policy");

        // Assert,
        if (security_policy_location == null) {
          throw new RuntimeException(
              "'security_policy' is not defined in the app_service.properties file.");
        }

        // Convert the security policy location to a URL
        try {
          security_policy_url = new URL(security_policy_location);
        }
        catch (MalformedURLException e) {
          // Make it into a file url,
          security_policy_url =
             new File(security_policy_location).getCanonicalFile().toURI().toURL();
        }
      }

      // Load the allowed system classes,
      String allowed_sys_classes_location =
                             app_service_config.getProperty("allowed_classes");

      // Assert,
      if (allowed_sys_classes_location == null) {
        throw new RuntimeException(
            "'allowed_sys_classes_location' is not defined in the app_service.properties file.");
      }

      // Convert the allowed classes location to a URL
      URL allowed_sys_classes_url;
      try {
        allowed_sys_classes_url = new URL(allowed_sys_classes_location);
      }
      catch (MalformedURLException e) {
        allowed_sys_classes_url =
             new File(allowed_sys_classes_location).getCanonicalFile().toURI().toURL();
      }

      AllowedSystemClasses allowed_sys_classes = new AllowedSystemClasses();
      allowed_sys_classes.loadFrom(allowed_sys_classes_url);

      // The shared thread pool specifications (note that applications such
      // as Jetty may manage their own separate thread pool). This thread pool
      // is used for Mckoi processes.

      String shared_threadpool_min =
            app_service_config.getProperty("shared_threadpool_min_threads", "4");
      String shared_threadpool_max =
            app_service_config.getProperty("shared_threadpool_max_threads", "256");
      String shared_threadpool_timeout_seconds =
            app_service_config.getProperty("shared_threadpool_timeout_seconds", "60");
      
      int shared_tp_min, shared_tp_max, shared_tp_timeout;
      try {
        shared_tp_min = Integer.parseInt(shared_threadpool_min.trim());
        shared_tp_max = Integer.parseInt(shared_threadpool_max.trim());
        shared_tp_timeout = Integer.parseInt(shared_threadpool_timeout_seconds.trim());
      }
      catch (NumberFormatException ex) {
        String err_msg = MessageFormat.format(
            "Either {0}, {1} or {2} (from ''shared_threadpool_min_threads'', " +
            "''shared_threadpool_max_threads'' or ''shared_threadpool_timeout_seconds'') " +
            "are not numbers",
                  new Object[] { shared_threadpool_min,
                                 shared_threadpool_max,
                                 shared_threadpool_timeout_seconds });
        throw new RuntimeException(err_msg);
      }

      if (shared_tp_min < 1) {
        throw new RuntimeException("'shared_threadpool_min_threads' < 1");
      }
      if (shared_tp_max < 4) {
        throw new RuntimeException("'shared_threadpool_max_threads' < 4");
      }
      if (shared_tp_min > shared_tp_max) {
        throw new RuntimeException(
          "'shared_threadpool_min_threads' > 'shared_threadpool_max_threads'");
      }
      if (shared_tp_timeout < 1) {
        throw new RuntimeException("'shared_threadpool_timeout_seconds' < 1");
      }

      ThreadPoolExecutor thread_pool_executor =
               new ThreadPoolExecutor(shared_tp_min, shared_tp_max,
                                      shared_tp_timeout, TimeUnit.SECONDS,
                                      new LinkedBlockingQueue<Runnable>());      

      // Create the user code class loader,
      
      // The user code class loader is a child of the system class loader,
      MWPSystemClassLoader sys_classloader =
              (MWPSystemClassLoader) getClass().getClassLoader();
      // Add the .jars in the lib/user/ directory,
      File lib_user_path = new File(new File(getInstallPath(), "lib"), "user");
      URL[] user_path_urls = MWPUserClassLoader.getJarsInPath(lib_user_path);

      // Set the empty zip file,
      File empty_zip = new File(lib_user_path, "empty.zip");
      if (!empty_zip.exists() || !empty_zip.isFile()) {
        throw new RuntimeException("Unable to find 'empty.zip' in lib/user");
      }
      DataFileJarFile.setEmptyZipLocation(empty_zip);

      MWPClassLoaderSet class_loader_set =
                      new MWPClassLoaderSet(sys_classloader, user_path_urls);

      // Client.conf properties,
      final String network_conf_location = getNetworkConf().toString();
      final String client_config_file = getClientConf().toString();

      // PENDING: Give option to load from URL?
      Properties client_conf_p = new Properties();
      client_conf_p.load(new BufferedInputStream(new FileInputStream(
                                             new File (client_config_file))));

      // Set default cache settings,
      String transaction_cache_size_str =
              client_conf_p.getProperty("transaction_cache_size", "14MB");
      client_conf_p.setProperty(
              "transaction_cache_size", transaction_cache_size_str);

      String global_cache_size_str =
              client_conf_p.getProperty("global_cache_size", "32MB");
      client_conf_p.setProperty(
              "global_cache_size", global_cache_size_str);

      // The name of the network interface to be used to talk with the DDB
      // network (for IPv6 link local)
      String net_interface_name = client_conf_p.getProperty("net_interface");
      NetworkInterface net_if = null;
      if (net_interface_name == null) {
        System.out.println(
            "WARNING: No 'net_interface' property found in client.conf.");
        System.out.println(
            "  This means you will not be able to connect to machines with IPv6");
        System.out.println(
            "  link-local addresses.");
        System.out.println(
            "  If you are using IPv6 you can fix this by adding a 'net_interface'");
        System.out.println(
            "  property to client.conf.");
        System.out.println(
            "  For example; 'net_interface=eth0'");
      }
      else {
        // Check the net interface binds to a NetworkInterface on this machine,
        net_if = NetworkInterface.getByName(net_interface_name);
        if (net_if == null) {
          String err_msg = MessageFormat.format(
              "The 'net_interface' property in client.conf does not match a " +
              "network interface on this machine. net_interface = '{0}'",
              net_interface_name);
          System.out.println("ERROR: " + err_msg);
          throw new RuntimeException(err_msg);
        }
      }

      // Report the configuration settings,
      System.out.println("Client Configuration");
      if (net_if != null) {
        System.out.println(
                "  network_interface = " + net_if);
      }
      System.out.println(
              "  transaction_cache_size = " + transaction_cache_size_str);
      System.out.println(
              "  global_cache_size = " + global_cache_size_str);
      System.out.println();

      System.out.println("Web Node Configuration");

      // Get the MckoiDDB client connection,
      final MckoiDDBClient client =
                                 MckoiDDBClientUtils.connectTCP(client_conf_p);

      // Load the network resource,
      NetworkConfigResource network_resource;

      String net_resource_str;
      try {
        URL nc_url = new URL(network_conf_location);
        network_resource = NetworkConfigResource.getNetConfig(nc_url);
        net_resource_str = nc_url.toString();
      }
      catch (MalformedURLException e) {
        // Try as a file,
        File net_conf_file = new File(network_conf_location);
        network_resource = NetworkConfigResource.getNetConfig(net_conf_file);
        net_resource_str = net_conf_file.getCanonicalPath();
      }
      System.out.println("  Network Resource = " + net_resource_str);

      // Create the shared thread pool,
      ExecutorService shared_thread_pool = thread_pool_executor;

      // Create the global sessions cache (1 second age),
      DBSessionCache sessions_cache =
                            new DBSessionCache(client, network_resource, 1000);
      // Create the process client service,
      ProcessClientService process_client_service = new ProcessClientService(
                          sessions_cache, shared_thread_pool, net_if);

      // Opens the client service,
      process_client_service.open();

//      // Set the sessions cache in the MWPFS URL handler,
//      MWPFSURLStreamHandler.setDBSessionCache(sessions_cache);

      // -- BEFORE SECURITY INITIALIZATION --
      
      web_service_node.preSecurityInit(
                       web_config, sessions_cache, process_client_service);
      process_service_node.preSecurityInit(
                       process_config, sessions_cache, process_client_service);

      // -- SECURITY INIT --
      
      // Setup the security manager,
      initSecurity(base_home_page,
                  install_home_path, security_policy_url, allowed_sys_classes);

      // -- POST SECURITY INIT --

      web_service_node.init(web_config, ssl_extras,
                            allowed_sys_classes, class_loader_set);
      process_service_node.init(
                      process_config, allowed_sys_classes,
                      class_loader_set, shared_thread_pool);

      // Start the web service node (with no connectors)
      web_service_node.start();
      // Start the process service node,
      process_service_node.start();

    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    
  }

  
  
  
  
  
  
  private void startHttp() {
    web_service_node.startHttp();
  }

  private void startHttps() {
    web_service_node.startHttps();
  }

  private void startProcess() {
    process_service_node.startProcess();
  }

  private void stopHttp() {
    web_service_node.stopHttp();
  }

  private void stopHttps() {
    web_service_node.stopHttps();
  }

  private void stopProcess() {
    process_service_node.stopProcess();
  }

  private void shutdown() {
    process_service_node.stop();
    web_service_node.stop();
    
    // Exit the system
    stop();
  }
  

  @Override
  protected String runCommand(String command) {
    if (command.equals("start http")) {
      startHttp();
    }
    else if (command.equals("start https")) {
      startHttps();
    }
    else if (command.equals("start process")) {
      startProcess();
    }
    else if (command.equals("stop http")) {
      stopHttp();
    }
    else if (command.equals("stop https")) {
      stopHttps();
    }
    else if (command.equals("stop process")) {
      stopProcess();
    }
    else if (command.equals("shutdown")) {
      shutdown();
    }

    else {
      return "FAIL: Unknown command: " + command;
    }

    return "OK";
  }



  /**
   * Process invocation point.
   */
  public static void main(String[] args) {
    MWPCoreProcess process = new MWPCoreProcess();
    // Init,
    process.init();
    process.start();
  }

}
