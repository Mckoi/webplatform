/**
 * com.mckoi.webplatform.impl.AppServiceNode  Mar 8, 2012
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

package com.mckoi.appcore;

import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.network.*;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBSession;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.odb.util.DirectorySynchronizer;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.odb.util.FileSystemImpl;
import com.mckoi.util.IOWrapStyledPrintWriter;
import com.mckoi.util.StyledPrintWriter;
import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;

/**
 * This is the invocation point of the Mckoi Platform service node. This
 * requires a simple setup script that identifies the public and private IP
 * address of this node, the location of external logging directories, and
 * any MckoiDDB related information.
 * <p>
 * This application periodically polls the MckoiDDB database for configuration
 * changes. The configuration includes what services to run (eg. public HTTP
 * service?), process server, etc.
 * <p>
 * Note that this object does not use much of the local JVM resources. When
 * an application instance is created it creates a new JVM process.
 *
 * @author Tobias Downer
 */

public final class AppServiceNode {

  /**
   * The appcore log.
   */
  public static final Logger APPCORE_LOG =
                                     Logger.getLogger("com.mckoi.appcore.Log");

  // The Jetty/Java web and process services
  private static final String JETTY_JAVA_SERVICE_CLASS = "com.mckoi.mwpcore.MWPCoreMain";

  // For the node.js JavaScript web and process services,
  private static final String NODEJS_SERVICE_CLASS = "com.mckoi.nodecore.MWPServiceNode";

  private static final Map<String, String> ROLE_TO_SERVICE_MAP = new HashMap<>();
  static {
    ROLE_TO_SERVICE_MAP.put("http", JETTY_JAVA_SERVICE_CLASS);
    ROLE_TO_SERVICE_MAP.put("https", JETTY_JAVA_SERVICE_CLASS);
    ROLE_TO_SERVICE_MAP.put("process", JETTY_JAVA_SERVICE_CLASS);
    ROLE_TO_SERVICE_MAP.put("nodejs_http", NODEJS_SERVICE_CLASS);
    ROLE_TO_SERVICE_MAP.put("nodejs_https", NODEJS_SERVICE_CLASS);
    ROLE_TO_SERVICE_MAP.put("nodejs_process", NODEJS_SERVICE_CLASS);
  }

  /**
   * The Mckoi client.
   */
  private MckoiDDBClient client;

  /**
   * The LOCK object we synchronize over.
   */
  private final Object LOCK = new Object();
  private boolean node_stopped = false;

  /**
   * The network config.
   */
  private NetworkConfigResource net_config;

  private File java_home;
  private File installs_path;
  private File temporary_path;
  private File log_path;
  private int logfile_limit;
  private int logfile_count;
  private InetAddress private_ip_inet;
  private InetAddress public_ip_inet;
  private String canonical_private_ip;
  private String canonical_public_ip;
  private String net_interface;
  private int admin_port;
  private File client_conf_file;
  private URL network_conf_url;
  private String java_args;

  private String loaded_install_name = null;
  private String loaded_install_version = null;

  private Map<String, ProcessThread> service_lookup = new HashMap<>();

  private List<String> cur_role_list;

  /**
   * Constructor.
   */
  public AppServiceNode(MckoiDDBClient client,
                        NetworkConfigResource net_config) {

    this.client = client;
    this.net_config = net_config;

  }

  public void setJavaHome(File java_home) {
    this.java_home = java_home;
  }

  public void setInstallsPath(File installs_path) {
    this.installs_path = installs_path;
  }

  public void setTemporaryPath(File temporary_path) {
    this.temporary_path = temporary_path;
  }

  public void setLogPath(File log_path) {
    this.log_path = log_path;
  }

  public void setLogFileLimit(int limit) {
    this.logfile_limit = limit;
  }

  public void setLogFileCount(int count) {
    this.logfile_count = count;
  }

  public void setPrivateIp(InetAddress private_ip_inet) {
    this.private_ip_inet = private_ip_inet;
  }

  public void setPublicIp(InetAddress public_ip_inet) {
    this.public_ip_inet = public_ip_inet;
  }

  public void setNetInterface(String net_interface_name) {
    this.net_interface = net_interface_name;
  }

  public void setAdminPort(int admin_port) {
    this.admin_port = admin_port;
  }

  public void setMckoiClient(File client_conf_file) {
    this.client_conf_file = client_conf_file;
  }

  public void setMckoiNetwork(URL network_conf_url) {
    this.network_conf_url = network_conf_url;
  }

  private void setJavaArgs(String java_args) {
    this.java_args = java_args;
  }

  /**
   * Returns the first line of the file in the file system as a string.
   */
  static String getFileAsString(FileSystem file_sys, String file_name)
                                                           throws IOException {
    DataFile file_content = file_sys.getDataFile(file_name);
    if (file_content == null) {
      return null;
    }
    BufferedReader r = new BufferedReader(new InputStreamReader(
                          DataFileUtils.asInputStream(file_content), "UTF-8"));
    return r.readLine();
  }

  /**
   * Writes the given string to the file in the file system. If the file
   * exists, the data in the file is removed before writing the update.
   */
  public static void putStringAsFile(String str,
                              FileSystemImpl file_sys, String file_name)
                                                           throws IOException {
    FileInfo finfo = file_sys.getFileInfo(file_name);
    if (finfo == null) {
      file_sys.createFile(file_name, "text/plain", System.currentTimeMillis());
      finfo = file_sys.getFileInfo(file_name);
    }
    else {
      finfo.setLastModified(System.currentTimeMillis());
    }
    OutputStreamWriter w = new OutputStreamWriter(
                   DataFileUtils.asOutputStream(finfo.getDataFile()), "UTF-8");
    w.append(str);
    w.flush();
  }

  /**
   * Generates an exception if directory value is invalid (is '.' or '..' or
   * contains '/' or '\' characters.
   */
  static void checkValidLocalFileString(String dir_name) {
    if (dir_name.endsWith("/")) {
      dir_name = dir_name.substring(0, dir_name.length() - 1);
    }
    if (dir_name.startsWith(".") ||
        dir_name.contains("/") || dir_name.contains("\\")) {
      throw new RuntimeException("Directory name contains invalid characters.");
    }
  }

  private static boolean listsSame(List<String> list1,
                                   List<String> list2) {

    // Handle nulls,
    if (list1 == null) {
      return (list2 == null);
    }
    return list2 != null && list1.equals(list2);

  }

  /**
   * Fetch the deploy string from the database.
   */
  private String fetchDeployString(FileSystem file_sys) throws IOException {
    // If there's a 'curinstall' file in the machine's directory, use that as
    // the version to deploy on this server,
    final String machine_curinstall =
                           "/servers/" + canonical_private_ip + "/curinstall";
    String current_version = getFileAsString(file_sys, machine_curinstall);
    // If it's null, try the global one,
    if (current_version == null) {
      // Read the global current version,
      final String global_curinstall = "/curinstall";
      current_version = getFileAsString(file_sys, global_curinstall);
      if (current_version == null) {
        APPCORE_LOG.severe(
             "/curinstall doesn't exist in the system platform file system.");
        return null;
      }
    }
    current_version = current_version.trim();
    return current_version;
  }

  /**
   * Returns a ProcessThread for the given role, or null if the role
   * isn't recognised.
   */
  private ProcessThread getProcessThreadFor(String service_role)
                                                          throws IOException {

    // Is the service process already running?
    String service_classname = ROLE_TO_SERVICE_MAP.get(service_role);
    // If role not found,
    if (service_classname == null) {
      return null;
    }
    ProcessThread service_process = service_lookup.get(service_classname);
    if (service_process != null) {
      return service_process;
    }

    File java_cmd = new File(java_home, "bin");
    java_cmd = new File(java_cmd, "java");

    File java_tools = new File(java_home, "lib");
    java_tools = new File(java_tools, "tools.jar");

    File this_installs_path = new File(installs_path, loaded_install_name);
    this_installs_path = new File(this_installs_path, loaded_install_version);

    File lib_dir = new File(this_installs_path, "lib");
    File base_lib_dir = new File(lib_dir, "base");

    // We use /lib/base as the classpath when invoking the java VM,
    StringBuilder class_path = new StringBuilder();
    class_path.append(base_lib_dir.getCanonicalPath());
    class_path.append(File.separator);
    class_path.append("*");
    class_path.append(File.pathSeparator);
    class_path.append(java_tools.getCanonicalPath());
    class_path.append(File.pathSeparator);

    // Encode the public and private ip addresses,

    List<String> args = new ArrayList<>();

    // The java process command,
    args.add(java_cmd.toString());

    // Add any extra arguments,
    if (!java_args.equals("")) {
      args.add(java_args);
    }

    // The remainnig Java commands
    args.add("-cp");
    args.add(class_path.toString());
    args.add("com.mckoi.mwpcore.MWPCoreMain");

    System.out.println("Command: " + args);

    ProcessBuilder pb = new ProcessBuilder(args.toArray(new String[args.size()]));

    // Define environment vars,
    Map<String, String> env = pb.environment();
    env.put("mwp.config.client", client_conf_file.getCanonicalPath());
    env.put("mwp.config.network", network_conf_url.toString());
    env.put("mwp.config.javahome", java_home.getCanonicalPath());
    env.put("mwp.config.install", this_installs_path.getCanonicalPath());
    env.put("mwp.io.log", log_path.getCanonicalPath());
    env.put("mwp.io.temp", temporary_path.getCanonicalPath());
    env.put("mwp.io.privateip", canonical_private_ip);
    env.put("mwp.io.publicip", canonical_public_ip);
    if (net_interface != null) {
      env.put("mwp.io.netinterface", net_interface);
    }

    // Set the working directory
    pb.directory(this_installs_path);

    // Start the java process in a new thread,
    pb.redirectErrorStream(true);

    service_process = new ProcessThread(pb.start());
    service_process.start();

    // Put it in the lookup map,
    service_lookup.put(service_classname, service_process);

    // Return the process object,
    return service_process;

  }

  /**
   * Routes a role command to the process handling the given role type.
   */
  private String routeToCoreFunction(String role, String cmd) throws IOException {
    // Get the process thread. Note that this will create and start a process is
    // there's not one currently operating for the given role type.
    ProcessThread service_process = getProcessThreadFor(role);
    if (service_process == null) {
      String err_msg = MessageFormat.format(
              "Unable to execute command ''{1}'' for role ''{0}'' because the role is unrecognized",
              role, cmd);
      APPCORE_LOG.log(Level.SEVERE, err_msg);
      System.err.println(err_msg);
      return null;
    }
    else {
      return service_process.send(cmd + " " + role);
    }
  }

  /**
   * Shuts down all the service processes.
   */
  private void shutdownAllCoreFunctions() throws IOException {
    Collection<ProcessThread> values = service_lookup.values();
    for (ProcessThread process_service : values) {
      process_service.send("shutdown");
    }
    service_lookup.clear();
  }

  /**
   * Fetches the latest install from the system platform path.
   */
  private boolean loadInstallFromSystem() throws IOException {

    ODBSession session = new ODBSession(client, "sysplatform");
    ODBTransaction t = session.createTransaction();

    // Get the file system,
    FileSystemImpl file_sys = new FileSystemImpl(t, "fs");

    // Fetch the current install for this server
    String current_version = fetchDeployString(file_sys);

    // If no deploy file was found,
    if (current_version == null) {
      return false;
    }

    // The version part
    int vers_delim = current_version.lastIndexOf("/", current_version.length() - 2);
    int insname_delim = current_version.lastIndexOf("/", vers_delim - 1);
    
    String install_name =
            current_version.substring(insname_delim + 1, vers_delim);
    String version_name =
            current_version.substring(vers_delim + 1, current_version.length() - 1);

    // Check the directory string
    checkValidLocalFileString(install_name);
    checkValidLocalFileString(version_name);

    // Is the version different from the current installs?
    File local_install_path = new File(installs_path, install_name);
    local_install_path = new File(local_install_path, version_name);
    
    // If it doesn't exist, create the install directory,
    boolean needs_update = local_install_path.mkdirs();

    if (needs_update) {

      // Install path
      String db_install_path = current_version;
      // The installs directory,
      FileInfo install_path = file_sys.getFileInfo(db_install_path);
      // Mirror the directory to the local file system,
      if (!install_path.isDirectory()) {
        APPCORE_LOG.severe("Expecting install directory in database.");
        return false;
      }

      StyledPrintWriter pout = new IOWrapStyledPrintWriter(System.out);

      DirectorySynchronizer s =
            DirectorySynchronizer.getMckoiToJavaSynchronizer(
                    new DirectorySynchronizer.StyledDSFeedback(pout),
                    file_sys, db_install_path, local_install_path);
      long update_count = s.synchronize();

      pout.flush();

      APPCORE_LOG.log(Level.INFO, "Success: New install downloaded: {0}",
                                  local_install_path.getCanonicalPath());

    }

    // Load the process list for this server,
    ODBObject roles_ob = t.getNamedItem("roles");
    ODBList servers = roles_ob.getList("serverIdx");
    servers = servers.tail(canonical_private_ip);
    List<String> role_list = new ArrayList<>();
    for (ODBObject server : servers) {
      if (!server.getString("server").equals(canonical_private_ip)) {
        break;
      }
      // Fetch the role
      String role = server.getString("roleserver");
      role = role.substring(0, role.indexOf("."));
      role_list.add(role);
    }

    // Sort the role list,
    Collections.sort(role_list);
    // And set it as current,
    cur_role_list = Collections.unmodifiableList(role_list);

    // If the deployed version is different,
    if ( loaded_install_name == null || loaded_install_version == null ||
          ( !loaded_install_name.equals(install_name) ||
            !loaded_install_version.equals(version_name) ) ) {
      // Update and return true,
      loaded_install_name = install_name;
      loaded_install_version = version_name;
      return true;
    }
    else {
      return false;
    }

  }

  /**
   * Starts the service node, blocking forever.
   */
  public void start() {

    final Runnable stop_runnable = new Runnable() {
      @Override
      public void run() {
        stop();
      }
    };

    // Add a shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(stop_runnable));
    
    try {

      // The appcore logger,
      // Output the content of this log to 'appcore.log'
      {
        Logger log = APPCORE_LOG;
        // The debug output level,
        log.setLevel(Level.INFO);
        // Don't propagate log messages,
        log.setUseParentHandlers(false);

        // Output to the log file,
        String log_file_name = new File(log_path, "appcore.log").getCanonicalPath();
        FileHandler fhandler =
                new FileHandler(log_file_name, logfile_limit, logfile_count, true);
        fhandler.setFormatter(new AppCoreLogFormatter());
        log.addHandler(fhandler);
      }

      // Log,
      APPCORE_LOG.info("Starting AppServiceNode");

      // The unique id of this service node,
      canonical_private_ip =
              ServerRolesSchema.canonicalIPString(private_ip_inet);
      canonical_public_ip =
              ServerRolesSchema.canonicalIPString(public_ip_inet);

    }
    catch (IOException e) {
      System.err.println("Failed to initialize App Service");
      e.printStackTrace(System.err);
      return;
    }

    int log_fail_retry = 1;

    while (true) {

      try {

        int ms_to_wait = 30 * 1000;
        boolean fail_retry = false;
        Throwable fail_e = null;

        try {
          // Remember roles list before we load (could be null)
          List<String> old_roles_list = cur_role_list;

          // Loads any installs from the network,
          boolean loaded_new = loadInstallFromSystem();

          // Did we load a new install?
          if (loaded_new) {
            // Yes, so shutdown any existing processes,
            shutdownAllCoreFunctions();
            old_roles_list = null;
          }

          // If we loaded a new install, or the roles list changed, then change
          // the statue of the various applications in the child process,
          if (cur_role_list != null &&
                  (loaded_new || !listsSame(old_roles_list, cur_role_list))) {

            // The roles to stop,
            if (old_roles_list != null) {
              for (String role : old_roles_list) {
                if (!cur_role_list.contains(role)) {
                  routeToCoreFunction(role, "stop");
                }
              }
            }

            // The roles to start,
            for (String role : cur_role_list) {
              if (old_roles_list == null || !old_roles_list.contains(role)) {
                routeToCoreFunction(role, "start");
              }
            }

          }

          // Reset log_fail_retry if a loop iteration was successful.
          log_fail_retry = 1;

        }
        // On a ServiceNotConnectedException, we wait 10 sec and retry the
        // loop. Every 12 retries (after roughly 2 mins) the exception message
        // will be logged.
        catch (ServiceNotConnectedException e) {
          // Set wait time to 10 sec,
          ms_to_wait = (10 * 1000);
          fail_retry = true;
          fail_e = e;
        }
        // If the sys_platform path isn't available we fail/retry as well.
        catch (PathNotAvailableException e) {
          // Set wait time to 10 sec,
          ms_to_wait = (15 * 1000);
          fail_retry = true;
          fail_e = e;
        }
        
        if (fail_retry) {
          // This is so we don't spam the log with retry messages,
          --log_fail_retry;
          if (log_fail_retry == 0) {
            log_fail_retry = 12;
            
            APPCORE_LOG.log(Level.SEVERE,
              "Service not connected exception in AppService loop, retrying in 10 sec", fail_e);
          }
        }

        try {
          synchronized(LOCK) {
            LOCK.wait(ms_to_wait);
            // If the node is stopped, return
            if (node_stopped) {
              break;
            }
          }
        }
        catch (InterruptedException e) {
          // Shut down the node,
          new Thread(stop_runnable).start();
        }

      }
      catch (IOException e) {
        APPCORE_LOG.log(Level.SEVERE, "Error during service loop", e);
        e.printStackTrace(System.err);
        APPCORE_LOG.log(Level.SEVERE, "Loop is not terminating because of error.");
      }

    } // while (true)

  }

  /**
   * Stop the node.
   */
  public void stop() {
    synchronized (LOCK) {
      node_stopped = true;
    }
  }

  /**
   * Turns the string into a file reference.
   */
  static File toFile(File home_path, String location) {
    File f = new File(location);
    if (f.exists()) {
      return f;
    }
    else {
      return new File(home_path, location);
    }
  }

  /**
   * Try and parse the given file as a URL location.
   */
  static URL parseToURL(File home_path, String location)
                                                 throws MalformedURLException {
    try {
      URL url = new URL(location);
      return url;
    }
    catch (MalformedURLException e) {
      // Try as a file,
      File file = toFile(home_path, location);
      return file.toURI().toURL();
    }
  }

  /**
   * Returns true if the given address is an interface of the local JVM.
   */
  static boolean isLocalAddress(InetAddress addr) throws IOException {
    if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
      return true;
    }
    return NetworkInterface.getByInetAddress(addr) != null;
  }

  /**
   * The main method for starting the service node for the Mckoi Web
   * Platform. This will start the bootstrap service and blocks until some
   * sort of critical error happens.
   */
  public static void serviceMain(Properties mwp_main_conf_properties)
                                                          throws IOException {

    String home_dir = mwp_main_conf_properties.getProperty("home_dir", "./");
    File home_path = new File(home_dir).getCanonicalFile();

    String java_home_dir =
        mwp_main_conf_properties.getProperty("java_home", "./");
    File java_home_path = new File(java_home_dir).getCanonicalFile();

    String installs_dir =
        mwp_main_conf_properties.getProperty("installs_dir", "./installs/");
    File installs_path = new File(home_path, installs_dir);

    String client_conf =
        mwp_main_conf_properties.getProperty("mckoi_client", "./client.conf");
    File client_conf_file = new File(home_path, client_conf);

    // Fetch the location of the 'network.conf' file, either by reading
    // it from the 'mckoi_network' property or dereferencing it from the
    // 'mckoi_netconf_info' location.
    String network_config_val =
                   mwp_main_conf_properties.getProperty("mckoi_network");
    String netconf_info_val =
                   mwp_main_conf_properties.getProperty("mckoi_netconf_info");
    String network_conf;
    if (netconf_info_val != null) {
      Properties nci = new Properties();
      FileReader r = new FileReader(new File(netconf_info_val));
      nci.load(r);
      network_conf = nci.getProperty("netconf_location");
      r.close();
    }
    else if (network_config_val != null) {
      network_conf = network_config_val;
    }
    else {
      network_conf = "./network.conf";
    }

    // Parse it to a URL,
    URL network_conf_url = parseToURL(home_path, network_conf);

    String temporary_dir =
            mwp_main_conf_properties.getProperty("temporary_dir", "./temp/");
    File temporary_path = new File(home_path, temporary_dir);

    String log_dir =
            mwp_main_conf_properties.getProperty("log_dir", "./log/");
    File log_path = new File(home_path, log_dir);

    int logfile_limit = (1 * 1024 * 1024);
    String lf_limit = mwp_main_conf_properties.getProperty("logfile_limit");
    if (lf_limit != null) {
      logfile_limit = Integer.parseInt(lf_limit);
    }
    int logfile_count = 4;
    String lf_count = mwp_main_conf_properties.getProperty("logfile_count");
    if (lf_count != null) {
      logfile_count = Integer.parseInt(lf_count);
    }

    // The inet address of the private address of this node,
    String private_ip =
            mwp_main_conf_properties.getProperty("private_ip", null);
    // The inet address of the public address of this node,
    String public_ip =
            mwp_main_conf_properties.getProperty("public_ip", null);

    // The net interface,
    String net_interface =
            mwp_main_conf_properties.getProperty("net_interface", null);

    // The port of the admin server used to refresh the install,
    String admin_port =
            mwp_main_conf_properties.getProperty("admin_port", "9671");

    // Extra arguments to put on the 'java' command line,
    String java_args =
            mwp_main_conf_properties.getProperty("java_args", "");

    // java_home must be defined,
    if (java_home_dir.equals("./")) {
      System.out.println("ERROR: java_home is not set.");
      return;
    }

    // Network tests,
    if (private_ip == null) {
      System.out.println("ERROR: private_ip is not set.");
      return;
    }
    if (public_ip == null) {
      System.out.println("ERROR: public_ip is not set.");
      return;
    }

    // Resolve to Inet address,
    InetAddress private_ip_inet;
    InetAddress public_ip_inet;
    private_ip_inet = InetAddress.getByName(private_ip);
    public_ip_inet = InetAddress.getByName(public_ip);

    if (!isLocalAddress(private_ip_inet)) {
      System.out.println("ERROR: private_ip is not a local interface.");
      return;
    }
    if (!isLocalAddress(public_ip_inet)) {
      System.out.println("ERROR: public_ip is not a local interface.");
      return;
    }

    // Print the properties,
    System.out.println("Configuration:");
    System.out.println("  home_dir = " + home_path);
    System.out.println("  java_home = " + java_home_path);
    System.out.println("  installs_dir = " + installs_path);
    System.out.println("  mckoi_client = " + client_conf_file);
    System.out.println("  mckoi_network = " + network_conf_url);
    System.out.println("  temporary_dir = " + temporary_path);
    System.out.println("  log_dir = " + log_path);
    if (!java_args.equals("")) {
      System.out.println("  java_args = '" + java_args + "'");
    }
    System.out.println();
    System.out.println("  private_ip = " + private_ip_inet);
    System.out.println("  public_ip = " + public_ip_inet);
    if (net_interface != null) {
      System.out.println("  net_interface = " + net_interface);
    }
    System.out.println("  admin_port = " + admin_port);
    System.out.println();

    // Make the MckoiDDBClient,
    MckoiDDBClient client = MckoiDDBClientUtils.connectTCP(client_conf_file);
    // The network resource,
    NetworkConfigResource network_resource =
                        NetworkConfigResource.getNetConfig(network_conf_url);

    // The node configuration,
    AppServiceNode server = new AppServiceNode(client, network_resource);
    server.setInstallsPath(installs_path);
    server.setJavaHome(java_home_path);
    server.setTemporaryPath(temporary_path);
    server.setLogPath(log_path);
    server.setLogFileLimit(logfile_limit);
    server.setLogFileCount(logfile_count);
    server.setPrivateIp(private_ip_inet);
    server.setPublicIp(public_ip_inet);
    server.setNetInterface(net_interface);
    server.setAdminPort(Integer.parseInt(admin_port));
    server.setMckoiClient(client_conf_file);
    server.setMckoiNetwork(network_conf_url);
    server.setJavaArgs(java_args);

    // Start the server,
    server.start();

  }

  /**
   * Application invocation.
   */
  public static void main(String[] args) {
    
    System.out.println("AppServiceNode - Mckoi System Service Node");
    System.out.println();
    System.out.println("See README for license details.");
    System.out.println();

    // First argument is the location of the configuration file,
    if (args.length != 1) {
      System.out.println("Please provide configuration file.");
      return;
    }

    try {

      // Load configuration
      String config_file_name = args[0];
      File config_file = new File(config_file_name);

      Properties config_properties = new Properties();
      config_properties.load(new BufferedReader(new FileReader(config_file)));

      // Set up the DDB client logger because we know nothing else will have
      // changed the handlers list.
      // Output the content of this log to 'ddbappservice.log'
      {

        // Set the logger file for 'com.mckoi.network.Log' messages,
        String home_dir = config_properties.getProperty("home_dir", "./");
        File home_path = new File(home_dir).getCanonicalFile();
        String log_dir = config_properties.getProperty("log_dir", "./log/");
        File log_path = new File(home_path, log_dir);

        int logfile_limit = (1 * 1024 * 1024);
        String lf_limit = config_properties.getProperty("logfile_limit");
        if (lf_limit != null) {
          logfile_limit = Integer.parseInt(lf_limit);
        }
        int logfile_count = 4;
        String lf_count = config_properties.getProperty("logfile_count");
        if (lf_count != null) {
          logfile_count = Integer.parseInt(lf_count);
        }

        Logger log = Logger.getLogger("com.mckoi.network.Log");

        // The debug output level,
        log.setLevel(Level.INFO);
        // Don't propogate log messages,
        log.setUseParentHandlers(false);

        // Output to the log file,
        String log_file_name =
                    new File(log_path, "ddbappservice.log").getCanonicalPath();
        FileHandler fhandler =
            new FileHandler(log_file_name, logfile_limit, logfile_count, true);
        fhandler.setFormatter(new SimpleFormatter());
        log.addHandler(fhandler);
        
      }

      // Service main method,
      serviceMain(config_properties);

    }
    catch (IOException e) {
      System.out.println("Sorry, an error occurred: " + e.getMessage());
      e.printStackTrace(System.err);
    }

  }

  /**
   * Simple formatter for app core log entries.
   */
  public static class AppCoreLogFormatter extends Formatter {

    private final SimpleDateFormat formatter;
    private final Date date = new Date();
    private final String line_separator = System.getProperty("line.separator");

    public AppCoreLogFormatter() {
      formatter = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss.SSS");
    }

    @Override
    public String format(LogRecord record) {
      StringBuilder b = new StringBuilder();
      date.setTime(record.getMillis());
      b.append(formatter.format(date));
      b.append(" ");
      b.append(record.getLevel().getName());
      b.append(" ");
      b.append(formatMessage(record));
      b.append(line_separator);
      if (record.getThrown() != null) {
        b.append("#");
        try {
          StringWriter sw = new StringWriter();
	        PrintWriter pw = new PrintWriter(sw);
	        record.getThrown().printStackTrace(pw);
	        pw.close();
          b.append(sw.toString());
        }
        catch (Exception ex) {
          // Don't throw an exception here
        }
      }
      return b.toString();
    }
  }

  
}
