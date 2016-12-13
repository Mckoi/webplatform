/*
 * Mckoi Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2016  Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mckoi.appcore;

import com.mckoi.network.MckoiDDBClient;
import com.mckoi.network.MckoiDDBClientUtils;
import com.mckoi.network.NetworkConfigResource;

import java.io.*;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.*;

/**
 * An abstract core process.
 *
 * @author Tobias Downer
 */

public abstract class AbstractCoreProcess {

  /**
   * The mwpcore log.
   */
  protected final Logger MWPCORE_LOG;

  /**
   * The process output stream (communicates with the parent process).
   */
  private final OutputStream out_stream;

  /**
   * The process input stream (receives commands from the parent process).
   */
  private final InputStream in_stream;

  private final Object finish_lock = new Object();
  private boolean is_finished = false;

  /**
   * Various configuration properties.
   */
  private File client_conf;
  private URL network_conf_url;
  private File java_home;
  private String nodejs_executable;
  private File install_path;
  private File log_path;
  private int logfile_limit;
  private int logfile_count;
  private File temp_path;
  private String private_ip;
  private String public_ip;
  private String net_interface;
  

  /**
   * Constructor.
   */
  protected AbstractCoreProcess() {
    out_stream = System.out;
    in_stream = System.in;

    PrintStream console_out = new PrintStream(new SystemOutRewrite());

    // Input is impossible with these process types,
    System.setIn(new ByteArrayInputStream(new byte[0]));
    System.setOut(console_out);
    System.setErr(console_out);
    // Set unix style line separator for JVM,
    System.setProperty("line.separator", "\n");

    // Create the logger here
    MWPCORE_LOG = Logger.getLogger("com.mckoi.appcore.Log");
  }

  public File getClientConf() {
    return client_conf;
  }

  public File getInstallPath() {
    return install_path;
  }

  public File getJavaHome() {
    return java_home;
  }

  public String getNodeJsExecutable() {
    return nodejs_executable;
  }

  public File getLogPath() {
    return log_path;
  }

  public int getLogFileLimit() {
    return logfile_limit;
  }

  public int getLogFileCount() {
    return logfile_count;
  }

  public URL getNetworkConf() {
    return network_conf_url;
  }

  public String getPrivateIp() {
    return private_ip;
  }

  public String getPublicIp() {
    return public_ip;
  }

  public String getNetInterface() {
    return net_interface;
  }

  public File getTempPath() {
    return temp_path;
  }
  
  /**
   * Initializes the variables. Must be called before 'start'.
   */
  protected void init(String base_log_file_name, String ddb_log_file_name) {

    try {
      // REALLY HACKY!
      // We remove the console handler from the root logger and replace it with
      // a fresh instantiation incase the root logger has an incorrect
      // System.err
      boolean console_handler_removed = false;
      Logger root_logger = Logger.getLogger("");
      Handler[] handlers = root_logger.getHandlers();
      for (Handler h : handlers) {
        if (h instanceof ConsoleHandler) {
          root_logger.removeHandler(h);
          console_handler_removed = true;
        }
      }
      if (console_handler_removed) {
        root_logger.addHandler(new ConsoleHandler());
      }

      // The environment variables passed from the parent,
      Map<String, String> env = System.getenv();

      // The log directory,
      client_conf = new File(env.get("mwp.config.client"));
      network_conf_url = new URL(env.get("mwp.config.network"));
      java_home = new File(env.get("mwp.config.javahome"));
      nodejs_executable = env.get("mwp.config.nodejsexecutable");
      install_path = new File(env.get("mwp.config.install"));
      log_path = new File(env.get("mwp.io.log"));
      temp_path = new File(env.get("mwp.io.temp"));
      private_ip = env.get("mwp.io.privateip");
      public_ip = env.get("mwp.io.publicip");
      net_interface = env.get("mwp.io.netinterface");

      logfile_limit = (1 * 1024 * 1024);
      String lf_limit = env.get("mwp.io.logfilelimit");
      if (lf_limit != null) {
        logfile_limit = Integer.parseInt(lf_limit);
      }
      logfile_count = 4;
      String lf_count = env.get("mwp.io.logfilecount");
      if (lf_count != null) {
        logfile_count = Integer.parseInt(lf_count);
      }

      // Get the Java logger,
      {
        Logger log = MWPCORE_LOG;
        // The debug output level,
        log.setLevel(Level.INFO);
        // Don't propagate log messages,
        log.setUseParentHandlers(false);

        // Output to the log file,
        String log_file_name =
                          new File(log_path, base_log_file_name).getCanonicalPath();
        FileHandler fhandler = new FileHandler(
                            log_file_name, logfile_limit, logfile_count, true);
        fhandler.setFormatter(new AppServiceNode.AppCoreLogFormatter());
        log.addHandler(fhandler);
      }

      // Set the DDB logger,
      {
        Logger log = Logger.getLogger("com.mckoi.network.Log");

        // The debug output level,
        log.setLevel(Level.INFO);
        // Don't propagate log messages,
        log.setUseParentHandlers(false);

        // Output to the log file,
        String log_file_name =
                       new File(log_path, ddb_log_file_name).getCanonicalPath();
        FileHandler fhandler = new FileHandler(
                            log_file_name, logfile_limit, logfile_count, true);
        fhandler.setFormatter(new SimpleFormatter());
        log.addHandler(fhandler);
        
      }

      // Log that the core process started,
      MWPCORE_LOG.info("Core process started");
      MWPCORE_LOG.log(Level.INFO, "Client: {0}", client_conf);
      MWPCORE_LOG.log(Level.INFO, "Network: {0}", network_conf_url.toString());
      MWPCORE_LOG.log(Level.INFO, "Java Home: {0}", java_home);
      MWPCORE_LOG.log(Level.INFO, "Node JS: {0}", nodejs_executable);
      MWPCORE_LOG.log(Level.INFO, "Log Path: {0}", log_path);
      MWPCORE_LOG.log(Level.INFO, "Temp Path: {0}", temp_path);
      MWPCORE_LOG.log(Level.INFO, "Private IP: {0}", private_ip);
      MWPCORE_LOG.log(Level.INFO, "Public IP: {0}", public_ip);

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Initializes connections to the MckoiDDB network. Returns a map containing
   * the Java objects generated.
   * <p>
   * "mckoiddb_client" => MckoiDDBClient
   *   The MckoiDDB connection
   * "network_config_resource" => NetworkConfigResource
   *   The Network configuration resource for administrative control over the
   *   network.
   * "network_interface" => NetworkInterface
   *   The Network Interface for communicating with the MckoiDDB network, in
   *   case of link-local IPv6.
   */
  protected Map<String, Object> initializeDDBConnections() throws IOException {

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
                "The ''net_interface'' property in client.conf does not match a " +
                        "network interface on this machine. net_interface = ''{0}''",
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

    // Returns the initialized MckoiDDB connections,
    Map<String, Object> ret_values = new HashMap<>();
    ret_values.put("mckoiddb_client", client);
    ret_values.put("network_config_resource", network_resource);
    ret_values.put("network_interface", net_if);
    return ret_values;

  }




  /**
   * Runs a command sent from the parent context.
   */
  protected abstract String runCommand(String command);

  /**
   * Runs the process.
   */
  protected void start() {

    synchronized (finish_lock) {
      if (is_finished) {
        return;
      }
    }

    try {

      // Wait for incoming commands from the parent process,
      BufferedReader r = new BufferedReader(
                                new InputStreamReader(in_stream, "UTF-8"));
      BufferedWriter w = new BufferedWriter(
                                new OutputStreamWriter(out_stream, "UTF-8"));

      while (true) {
        String command_id_str = r.readLine();
        String command_msg = r.readLine();
        if (command_id_str == null || command_msg == null) {
          MWPCORE_LOG.severe("Core process finished because input stream ended");
          return;
        }

//        int command_id = Integer.parseInt(command_id_str);
        
        // Do something with the command,
        MWPCORE_LOG.log(Level.INFO, "Command: {0}", command_msg);

        // Process the command,
        String command_response;
        try {
          command_response = runCommand(command_msg);
        }
        catch (RuntimeException e) {
          command_response =
                  "FAIL: Exception (" + e.getClass().getName() +
                  "): " + ((e.getMessage() != null) ? e.getMessage() : "");
          MWPCORE_LOG.log(Level.SEVERE, "Command Failed.", e);
        }

        // The reply,
        synchronized (out_stream) {
          w.append(">");
          w.append(command_id_str);
          w.newLine();
          w.append(command_response);
          w.newLine();
          w.flush();
        }

        synchronized (finish_lock) {
          if (is_finished) {
            return;
          }
        }

      }

    }
    catch (IOException e) {
      MWPCORE_LOG.log(Level.SEVERE, "IOException in Core process", e);
    }
    catch (Throwable e) {
      e.printStackTrace(System.out);
      MWPCORE_LOG.log(Level.SEVERE, "Throwable in Core process", e);
    }
    finally {
      // Log that the core process ended,
      MWPCORE_LOG.info("Core process ended");
    }
  }
  
  /**
   * Stops this process and exits the notifier thread after the next message
   * is sent.
   */
  protected void stop() {
    synchronized (finish_lock) {
      is_finished = true;
    }
  }

  /**
   * Rewrites System.out to our custom protocol.
   */
  private class SystemOutRewrite extends OutputStream {

    private final ByteArrayOutputStream bout;

    private SystemOutRewrite() {
      bout = new ByteArrayOutputStream(150);
      bout.write('#');
    }

    @Override
    public void write(int b) throws IOException {
      // Buffer until we write a '\n'
      if (b == '\n') {
        // Write out the line,
        bout.write(b);
        byte[] flush_buf = bout.toByteArray();
        synchronized (out_stream) {
          out_stream.write(flush_buf, 0, flush_buf.length);
        }
        bout.reset();
        bout.write('#');
      }
      else {
        bout.write(b);
      }
    }

    @Override
    public void flush() throws IOException {
      synchronized (out_stream) {
        out_stream.flush();
      }
    }

  }

}
