/**
 * com.mckoi.process.impl.ProcessServerService  Mar 21, 2012
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

package com.mckoi.process.impl;

import com.mckoi.appcore.ServerRolesSchema;
import com.mckoi.appcore.SystemStatics;
import com.mckoi.appcore.UserApplicationsSchema;
import com.mckoi.data.PropertySet;
import com.mckoi.mwpcore.*;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystemImpl;
import com.mckoi.process.ProcessId;
import com.mckoi.process.ProcessInputMessage;
import com.mckoi.process.ProcessMessage;
import com.mckoi.util.ByteArrayUtil;
import com.mckoi.webplatform.LogPageEvent;
import com.mckoi.webplatform.impl.LoggerService;
import com.mckoi.webplatform.util.LogUtils;
import java.io.*;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The service run on a machine for the Mckoi process.
 *
 * @author Tobias Downer
 */

public class ProcessServerService implements PEnvironment {

  /**
   * The process log.
   */
  public static final Logger PROCESS_LOG =
                                     Logger.getLogger("com.mckoi.process.Log");

  /**
   * The default process TCP port.
   */
  public static final int DEFAULT_PROCESS_TCP_PORT = 8322;

  /**
   * How long before a process is considered stale.
   */
  public static final int STALE_PROCESS_TIMEOUT = 5 * 60 * 1000;
//  // DEBUG VALUE
//  public static final int STALE_PROCESS_TIMEOUT = 9 * 1000;

  /**
   * Frequency between process set maintenance (flushes/suspends).
   */
  public static final int MAINT_FREQUENCY_TIME = (int) (1.5 * 60 * 1000);
//  // DEBUG VALUE
//  public static final int MAINT_FREQUENCY_TIME = 4 * 1000;

  /**
   * The timeout since an instance was lasted interacted with that will signal
   * the instance be removed from the process set. This must be a value much
   * larger than 'MAINT_FREQUENCT_TIME' and 'STALE_PROCESS_TIMEOUT'.
   */
  public static final int DISPOSABLE_PROCESS_TIMEOUT = 12 * 60 * 1000;
//  // DEBUG VALUE
//  public static final int DISPOSABLE_PROCESS_TIMEOUT = 18 * 1000;

  /**
   * The sessions cache object.
   */
  private DBSessionCache sessions_cache;

  /**
   * The ProcessClientService of this process service. (This object manages
   * how we talk to other processes on the network).
   */
  private ProcessClientService process_client_service;

  /**
   * True when initialization complete.
   */
  private boolean init_complete = false;

  /**
   * The process server bind address.
   */
  private InetAddress process_bind_address;

  /**
   * The port value.
   */
  private int process_port;

  /**
   * The currently active processes in this process service.
   */
  private final ProcessSet process_set;

  /**
   * A log of all modifications made to process paths.
   */
  private final List<String> process_modify_log;

  /**
   * A thread that listens for incoming connections to the service.
   */
  private NIOServerThread server_thread;

  /**
   * The process timer.
   */
  private Timer process_timer;

  /**
   * The callback scheduler timer.
   */
  private Timer callback_scheduler;

  /**
   * A thread used to dispatch 'function' calls to process operations.
   */
  private FunctionDispatcherThread function_dispatcher;

  /**
   * The process thread pool.
   */
  private ExecutorService thread_pool;

  /**
   * Allowed system classes.
   */
  private ClassNameValidator allowed_sys_classes;

  /**
   * The Mckoi Web Platform class loaders.
   */
  private MWPClassLoaderSet classloaders;

  /**
   * The set of allowed ip addresses that may connect to the process server.
   * This database is periodically loaded from the system database.
   */
  private final Object ALLOWED_IP_DB_LOCK = new Object();
  private List<String> allowed_ip_list;

  /**
   * Maps account name to AccountInfo object, that contains static data
   * associated to an account (such as the logger).
   */
  private final Object ACCOUNT_INFO_LOCK = new Object();
  private final Map<String, AccountInfo> account_info_cache;

  /**
   * Last time the DB was loaded.
   */
  private volatile long last_load_db_time;

  /**
   * The map of application class loaders in this service.
   */
  private final
    Map<AccountApplication, ProcessMckoiAppClassLoader> app_classloaders_cache;

  /**
   * A map used for broadcast event dispatching.
   */
  private final Map<NIOConnection, MessageBroadcastContainer>
                                        broadcast_connect_map = new HashMap();

  /**
   * Secure random number generator.
   */
  static final SecureRandom SECURE_RNG;

  static {
    try {
      SECURE_RNG = SecureRandom.getInstance("SHA1PRNG");
    }
    catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException("SHA1PRNG unavailable", ex);
    }
  }

  /**
   * Constructor.
   */
  public ProcessServerService() {
    this.allowed_ip_list = new ArrayList(0);
    this.process_set = new ProcessSet();
    this.process_modify_log = new ArrayList(64);
    this.app_classloaders_cache = new HashMap();
    this.account_info_cache = new HashMap();
  }

  /**
   * Returns the ProcessSet object.
   */
  ProcessSet getProcessSet() {
    return process_set;
  }

  /**
   * Returns the DBSessionCache object for this service.
   */
  DBSessionCache getDBSessionCache() {
    return sessions_cache;
  }

  /**
   * Returns the process client service for this service.
   */
  ProcessClientService getProcessClientService() {
    return process_client_service;
  }

  /**
   * The allowed system classes.
   */
  ClassNameValidator getAllowedSystemClasses() {
    return allowed_sys_classes;
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
   * Pre security initialization.
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

    // Set the sessions cache,
    this.sessions_cache = sessions_cache;
    // The process client service (how we talk to other processes),
    this.process_client_service = process_client_service;

    try {
      // Get the Java logger,
      Logger log = PROCESS_LOG;
      // The debug output level,
      log.setLevel(Level.parse(log_level));
      // Don't propogate log messages,
      log.setUseParentHandlers(false);

      // Output to the log file,
      String log_file_name = new File(log_path, "process.log").getCanonicalPath();
      FileHandler fhandler =
            new FileHandler(log_file_name, logfile_limit, logfile_count, true);
      fhandler.setFormatter(new GeneralLogFormatter());
      log.addHandler(fhandler);

      // Log start message,
      PROCESS_LOG.log(Level.INFO, "Process Init ({0})",
                      new File(".").getCanonicalPath());
      
      System.out.println("  Process Log = " + log_file_name);
      System.out.println("  Log Level = " + log_level);

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Post security init.
   */
  public void init(Properties web_config,
                   ClassNameValidator allowed_sys_classes,
                   MWPClassLoaderSet classloaders,
                   ExecutorService shared_thread_pool) {

    if (web_config == null) throw new NullPointerException();
    if (allowed_sys_classes == null) throw new NullPointerException();

    if (init_complete) {
      throw new RuntimeException("Initialized");
    }

    try {

      this.allowed_sys_classes = allowed_sys_classes;
      this.classloaders = classloaders;

      // Fetch the properties from the properties file,
      String process_port_str =
            web_config.getProperty("process_port",
                                   Integer.toString(DEFAULT_PROCESS_TCP_PORT));

      // The process address and port,
      String process_address = web_config.getProperty("process_address", null);
      // Get the net interface,
      String net_interface = web_config.getProperty("net_interface", null);
      process_port = Integer.parseInt(process_port_str);

      // The scope to bind the interface on,
      NetworkInterface to_scope_if = null;
      if (net_interface != null) {
        to_scope_if = NetworkInterface.getByName(net_interface);
        if (to_scope_if == null) {
          String err_msg = MessageFormat.format(
              "The network interface was not found: {0}", net_interface);
          PROCESS_LOG.log(Level.SEVERE, err_msg);
          throw new IOException(err_msg);
        }
      }
      
      // Convert the 'process_address' string into an IP address that we
      // know binds to an interface on this server.
      InetAddress ip_addr = InetAddress.getByName(process_address);

      // We have to handle IPv6 specially to support link local addresses.
      // Basically, if the address doesn't have a scope we assign it one. If
      // it does, we check it's the same as the one we want.
      if (ip_addr instanceof Inet6Address) {
        Inet6Address ipv6_addr = (Inet6Address) ip_addr;

        NetworkInterface current_scope_if = ipv6_addr.getScopedInterface();
        if (current_scope_if == null) {
          if (to_scope_if == null) {
            if (ipv6_addr.isLinkLocalAddress()) {
              // It's a link local address with no scope id, so generate an
              // error.
              String err_msg = MessageFormat.format(
                  "The process address is a link local IPv6 address without a scope id: {0}",
                  ip_addr);
              PROCESS_LOG.log(Level.SEVERE, err_msg);
              throw new IOException(err_msg);
            }
            else {
              // Good to go otherwise,
            }
          }
          else {
            ip_addr = Inet6Address.getByAddress(
                                  null, ipv6_addr.getAddress(), to_scope_if);
          }
        }
        // If current_scope_if != null &&
        else if (to_scope_if != null && !current_scope_if.equals(to_scope_if)) {
          // The process bind address scope doesn't match the desired scope,
          String err_msg = MessageFormat.format(
                  "The process address scope does not match the specified scope. '{0}' scope = {1}",
                  new Object[] { ipv6_addr, to_scope_if.getName() } );
          PROCESS_LOG.log(Level.SEVERE, err_msg);
          throw new IOException(err_msg);
        }
      }

      // Check the IP binds to a local interface,
      NetworkInterface net_if = NetworkInterface.getByInetAddress(ip_addr);
      if (net_if == null) {
        String err_msg = MessageFormat.format(
            "The process address does not bind to a local interface: {0}",
            ip_addr);
        PROCESS_LOG.log(Level.SEVERE, err_msg);
        throw new IOException(err_msg);
      }

      this.process_bind_address = ip_addr;

      // The thread pool (max of 256 concurrent threads),
      if (shared_thread_pool == null) {
        this.thread_pool = Executors.newFixedThreadPool(48);
      }
      else {
        this.thread_pool = shared_thread_pool;
      }

      // Init successful
      init_complete = true;

    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Start the process server.
   */
  public void start() {
    initCheck();

    // Load the system data,
    loadSystemData();
  }

  /**
   * Stops the process server.
   */
  public void stop() {
    initCheck();

    if (server_thread != null) {
      stopProcess();
    }

  }

  /**
   * Start the process server and start accepting new connections.
   */
  public void startProcess() {
    initCheck();

    if (server_thread != null) {
      throw new RuntimeException("Already started");
    }

    // The process timer thread,
    process_timer = new Timer("Mckoi Process Timer");
    callback_scheduler = new Timer("Mckoi Callback Scheduler");

    // Start the function dispatcher thread,
    function_dispatcher = new FunctionDispatcherThread();
    function_dispatcher.start();

    // Start the connection thread,
    server_thread = new NIOServerThread(process_bind_address, process_port, this);
    server_thread.start();

    // Initialize the log flushing mechanism to keep the sysprocess paths up
    // to date with the changes that have happened,
    process_timer.scheduleAtFixedRate(new ProcessMaintenanceTimerTask(),
                                      15 * 1000, MAINT_FREQUENCY_TIME);

  }

  /**
   * Stops the process server.
   */
  public void stopProcess() {
    initCheck();

    if (server_thread == null) {
      throw new RuntimeException("Already stopped");
    }

    // Finish the function dispatcher,
    function_dispatcher.finish();
    function_dispatcher = null;

    // Cancel the callback scheduler,
    callback_scheduler.cancel();
    callback_scheduler = null;

    // Cancel the process and callback timers
    process_timer.cancel();
    process_timer = null;

    // Stop the accept thread,
    server_thread.finish();
    server_thread = null;

  }

  // ----------
  
  /**
   * Logs an exception to the application account of the process instance
   * given. The entries are logged to the 'process' log in the account's
   * log system.
   */
  void logAccountException(ProcessInstanceImpl instance, Throwable ex) {

    boolean log_failed = false;

    // The first exception if this is a failed account log,
    Throwable first_ex = null;

    ProcessId process_id = null;
    String account_name = null;
    String app_name = null;
    String process_name = null;

    // Get the application account,
    try {

      AccountApplication account_application =
                                           instance.getAccountApplication();

      // NOTE: Order here is important
      process_id = instance.getId();
      account_name = account_application.getAccountName();
      app_name = account_application.getApplicationName();
      process_name = instance.getProcessName();

      // Get the account log,
      LoggerService account_log = fastGetLoggerServiceForAccount(account_name);

      String ex_stack_trace;
      if (ex instanceof ProcessUserCodeException) {
        ProcessUserCodeException user_code_ex = (ProcessUserCodeException) ex;
        ex_stack_trace = user_code_ex.getUserCodeStackTrace();
      }
      else {
        // Convert the exception to a stack trace string,
        ex_stack_trace = LogUtils.stringStackTrace(ex);
      }

      // Write the error to the log,
      LogPageEvent evt = account_log.secureLog(Level.SEVERE, "process",
              "Process failed with exception. App Name: {0} Process: {1} ( {2} )\n{3}",
              app_name, process_name,
              process_id.getStringValue(), ex_stack_trace);

      System.err.println("-- Process Exception --");
      System.err.println(evt.asString());

    }
    catch (SuspendedProcessException e) {
      log_failed = true;
    }
    catch (PException e) {
      first_ex = e;
      log_failed = true;
    }
    catch (Exception e) {
      first_ex = e;
      log_failed = true;
    }
    
    // If the log failed (for example, the network isn't available), we
    // instead put it in the user log file of the local server.

    if (log_failed) {
      // Write to the process log,
      if (first_ex != null) {
        // Reason why we couldn't log to the MWP log system,
        PROCESS_LOG.log(Level.SEVERE,
              "Exception when logging to the MWP log system.", first_ex);
      }
      StringBuilder b = new StringBuilder();
      b.append("Process failed with exception.");
      b.append(" Account: ");
      b.append(account_name);
      b.append(" App Name: ");
      b.append(app_name);
      b.append(" Process: ");
      b.append(process_name);
      b.append(" ( ");
      b.append(process_id != null ? process_id.getStringValue() : "[NULL ID]");
      b.append(" )");
      // Log the process description as best we can,
      PROCESS_LOG.log(Level.SEVERE, b.toString());
      // Log the exception,
      PROCESS_LOG.log(Level.SEVERE,
              "Exception that failed to log to the MWP log system.", ex);
      System.err.println("Process failed with exception: " + b.toString());
      ex.printStackTrace(System.err);
    }

  }

  // ----------

  /**
   * Loads the set of allowed IP addresses that may connect to the process
   * server from the system database.
   */
  private void loadSystemData() {

    last_load_db_time = System.currentTimeMillis();

    ODBTransaction t =
                  sessions_cache.getODBTransaction(SystemStatics.SYSTEM_PATH);
    ODBObject servers = t.getNamedItem("servers");
    // The sorted list of all private ip addresses
    ODBList private_ip_idx = servers.getList("privateipIdx");
    int list_sz = (int) private_ip_idx.size();
    List<String> allowedips = new ArrayList(list_sz);
    for (ODBObject private_ip : private_ip_idx) {
      String ip = private_ip.getString("privateip");
      allowedips.add(ip);
    }

    // Set the allowed ip list,
    synchronized (ALLOWED_IP_DB_LOCK) {
      allowed_ip_list = allowedips;
    }
  }

  /**
   * Returns true if some arbitrary time has passed since the last time
   * 'loadSystemData' was called.
   */
  private boolean notLoadedDBInAWhile() {
    long time_now = System.currentTimeMillis();
    // 60 seconds
    if (time_now - (60 * 1000) > last_load_db_time) {
      return true;
    }
    return false;
  }

  /**
   * Checks the allowed ip address set and returns true if the given ip
   * string is allowed to connect to a process server.
   */
  private boolean isAllowedIp(String ip_string) {
    synchronized (ALLOWED_IP_DB_LOCK) {
      // Binary search on the sorted list of ip addresses,
      return Collections.binarySearch(allowed_ip_list, ip_string) >= 0;
    }
  }

  private static void fillHeader(
          byte[] buf, ProcessId process_id, int call_id, byte control_code) {
    if (process_id != null) {
      ByteArrayUtil.setLong(process_id.getHighLong(), buf, 0);
    }
    buf[0] = control_code;
    if (process_id != null) {
      ByteArrayUtil.setLong(process_id.getLowLong(), buf, 8);
      buf[8] = process_id.getPathValue();
    }
    ByteArrayUtil.setInt(call_id, buf, 16);
  }

  /**
   * Returns a byte array containing the command process id and call id data
   * encoded for a message header.
   */
  static byte[] createHeader(ProcessId process_id, int call_id,
                             byte control_code) {
    byte[] buf = new byte[20];
    fillHeader(buf, process_id, call_id, control_code);
    return buf;
  }

  /**
   * Returns a byte array containing the command process id and call id data
   * encoded for a message header.
   */
  static byte[] createSuccessHeader(ProcessId process_id, int call_id,
                                    byte control_code) {
    byte[] buf = new byte[22];
    fillHeader(buf, process_id, call_id, control_code);
    buf[20] = 'S';
    buf[21] = ';';
    return buf;
  }

  /**
   * Returns a success PMessage on a call.
   */
  static PMessage successMessage(ProcessId process_id, int call_id) {
    return new PMessage(
        createSuccessHeader(process_id, call_id, CommConstants.CALL_REPLY_CC));
  }

  /**
   * Returns a failure PMessage object.
   */
  static PMessage failMessage(ProcessId process_id, int call_id, String msg) {

    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      bout.write(
               createHeader(process_id, call_id, CommConstants.CALL_REPLY_CC));
      Writer w = new OutputStreamWriter(bout, "UTF-8");
      w.write("F'" + msg);
      w.flush();
      return new PMessage(bout.toByteArray());
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }

  }

  /**
   * Returns a failure PMessage object against an exception. 'error_type' is
   * a code describing the type of error. For example 'TIMEOUT'.
   */
  static PMessage failMessage(ProcessId process_id, int call_id,
                              String error_type, Throwable throwable) {

    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      bout.write(
               createHeader(process_id, call_id, CommConstants.CALL_REPLY_CC));
      PrintWriter p = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));
      p.append("Fe");
      p.flush();
      // Serialize the exception and error type,
      ObjectOutputStream oo = new ObjectOutputStream(bout);
      oo.writeUTF(error_type);
      oo.writeObject(throwable.getClass().getName());
      oo.writeObject(throwable.getMessage());
      oo.writeObject(throwable.getStackTrace());
      oo.flush();
      
      return new PMessage(bout.toByteArray());
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }

  }

  /**
   * Adds an event to the process log.
   */
  private void addLogEvent(String log_str) {
    synchronized (process_modify_log) {
      process_modify_log.add(log_str);
    }
  }

  /**
   * Adds a log entry for a process id being used.
   */
  private void addLogProcessManaged(ProcessId process_id) {

    // Encode the log message as follows (25 chars);
    //   +00zzzzzzzzzzzxxxxxxxxxxx
    // where 00 = path
    //       z+ = process high
    //       x+ = process low

    // The path value,
    String path_str =
                Integer.toHexString(((int) process_id.getPathValue()) & 0x0FF);
    
    StringBuilder b = new StringBuilder();
    b.append("+");
    if (path_str.length() == 1) {
      b.append("0");
    }
    b.append(path_str);
    SystemStatics.encodeLongBase64(process_id.getHighLong(), b);
    SystemStatics.encodeLongBase64(process_id.getLowLong(), b);
    addLogEvent(b.toString());
  }

  /**
   * Creates the class loader for the given account/application. This looks up
   * the application in the WEBAPPS_BIN file in the account's file system and
   * takes the location of the WEB-INF directory from it. It then builds a
   * class loader with the referenced /classes/ and /lib/*.jar locations as
   * the class path.
   * <p>
   * 'current_class_loader' is used when we are testing to see if a new class
   * loaded might need to be reloaded because the webapp has been recompiled.
   * 'current_class_loader' can be null if there is no previous version or
   * a new class loader is desired. When 'current_class_loader' is used, if a
   * new version is not available then the given class loader is returned.
   */
  private ProcessMckoiAppClassLoader createClassLoaderForApplication(
                      final ProcessMckoiAppClassLoader current_class_loader,
                      final AccountApplication account_app) throws PException {

//    System.out.println("createClassLoaderFromApplication");
//    if (current_class_loader != null) {
//      System.out.println(" current_class_loader.getAppVersion()" +
//                         current_class_loader.getAppVersion());
//    }

    // Get the account name,
    String account_name = account_app.getAccountName();
    // The current version snapshot of the class loader's filesystem,
    DBPathSnapshot ver_snapshot =
                  sessions_cache.getLatestDBPathSnapshot("ufs" + account_name);

    // Get the app version,
    String current_app_version = null;
    if (current_class_loader != null) {
      // Update the db check flag,
      current_class_loader.updateDBCheck(ver_snapshot);
      current_app_version = current_class_loader.getAppVersion();
    }

    // Create a transaction for the user's file system,
    ODBTransaction t =
                     sessions_cache.createReadOnlyODBTransaction(ver_snapshot);
    FileSystemImpl fs = new FileSystemImpl(t, "accountfs");
    // The web apps binary,
    FileInfo webapps_bin_file =
                           UserApplicationsSchema.getWebAppsBinaryFileInfo(fs);
    if (webapps_bin_file == null) {
      // If the file doesn't exist,
      throw new PException("WEBAPPS_BIN file does not exist");
    }
    // Make it into a property set
    PropertySet pset = new PropertySet(webapps_bin_file.getDataFile());

    String application_name = account_app.getApplicationName();

    // The application name,
    String id_list = pset.getProperty("id." + application_name);
    // If it's not found,
    if (id_list == null) {
      throw new PException("Application not found: " + application_name);
    }
    // Turn it into a list,
    List<String> ids = UserApplicationsSchema.fromDelimString(id_list);

    String app_fs_name = null;
    FileSystemImpl app_fs = null;
    String app_webapps_dir = null;
    String app_version_id = null;

    for (String id : ids) {
      // Is this the main location of the application?
      String pid = "p." + id;
      String app_type =
              pset.getProperty(pid + ".type").toLowerCase(Locale.ENGLISH);
      if (app_type.equals("main")) {
        String repository_loc = pset.getProperty(pid + ".repository");
        String webapps_dir = pset.getProperty(pid + ".repository_path");

        FileName rep_fname;
        // LEGACY,
        if (repository_loc != null) {
          rep_fname = new FileName(repository_loc, webapps_dir);
        }
        else {
          rep_fname = new FileName(webapps_dir);
        }

        // PENDING: Handle webapp dir from variable repository,

        app_fs_name = account_name;
        app_fs = fs;
        app_webapps_dir = rep_fname.getPathFile();
        app_version_id = id;
        break;
      }
    }

    // Did we find a repository location for the project files?
    if (app_fs == null) {
      throw new PException("Could not find \"main\" type in WEBAPPS_BIN");
    }

//    System.out.println(" current_app_version = " + current_app_version);
//    System.out.println(" app_version_id = " + app_version_id);

    // If the app version is the same then we just return the current,
    if (current_app_version != null &&
        current_app_version.equals(app_version_id)) {
      return current_class_loader;
    }

    // PENDING: Priv check if we can load webapps from other repositories,
    //   (currently can't)

    // The lib directory (if it exists),
    List<FileInfo> file_list =
                          app_fs.getFileList(app_webapps_dir + "WEB-INF/lib/");
    List<String> classpath_libs = Collections.emptyList();
    if (file_list != null) {
      // Make a list of all .jar and .zip files from the lib directory,
      classpath_libs = new ArrayList(file_list.size());
      for (FileInfo file : file_list) {
        String abs_name = file.getAbsoluteName();
        if (abs_name.endsWith(".zip") || abs_name.endsWith(".jar")) {
          classpath_libs.add(abs_name);
        }
      }
    }

    try {

      // Create the user class loader,
      MWPUserClassLoader user_cl =
                 classloaders.createUserClassLoader(allowed_sys_classes, false);

      // Create the process class loader as a child of the user class loader,
      ProcessMckoiAppClassLoader cl = new ProcessMckoiAppClassLoader(
                user_cl, app_version_id, ver_snapshot, allowed_sys_classes);

      // Set up the classpath for the class loader. We add the
      // 'WEB-INF/classes/' and all the .jar files in the lib dir.
      // Add the 'classes' directory to the class path,
      cl.addClassPath(
               "mwpfs:/" + app_fs_name + app_webapps_dir + "WEB-INF/classes/");
      // Add the libs,
      for (String classpath_lib : classpath_libs) {
        cl.addClassPath("mwpfs:/" + app_fs_name + classpath_lib);
      }

      return cl;

    }
    catch (IOException e) {
      // Should never happen,
      throw new RuntimeException(e);
    }

  }

  /**
   * Returns a class loader for the given account/application. This is backed
   * by a cache so all requests for a class loader for the same account
   * application version will produce the same class loader object. Note that
   * if the application is recompiled and a new version of the app is
   * produced, this will return a new class loader object for the new version.
   */
  ProcessMckoiAppClassLoader getClassLoaderForApplication(
                      final AccountApplication account_app) throws PException {
    // Check the cache
    synchronized (app_classloaders_cache) {
      // Get the class loader if there's one already defined,
      ProcessMckoiAppClassLoader cl = app_classloaders_cache.get(account_app);
      // If not defined,
      if (cl == null) {
        cl = createClassLoaderForApplication(null, account_app);
        app_classloaders_cache.put(account_app, cl);
      }
      else {
        // If it is defined, is it time to refresh from the db?
        long last_db_check = cl.getLastDBCheck();
        long time_now = System.currentTimeMillis();
        if (time_now > last_db_check + (4 * 1000)) {
          // Refresh the class loader if necessary,
          cl = createClassLoaderForApplication(cl, account_app);
          app_classloaders_cache.put(account_app, cl);
        }
      }
      return cl;
    }
  }

  /**
   * Fetches the process instance. This will always return a none-null
   * ProcessInstanceImpl object. Note that the returned process instance may
   * not be materialized.
   */
  private ProcessInstanceImpl getProcessInstanceImpl(ProcessId process_id) {

    // Get the instance,
    ProcessInstanceImpl instance = process_set.getInstance(process_id);

    // If it's null,
    if (instance == null) {
      // We make a process instance that is resumable,
      // This process instance starts in a 'suspended' state.
      ProcessInstanceImpl spec_instance =
                 new ProcessInstanceImpl(ProcessServerService.this, process_id);
      instance = process_set.setManagedIfAbsent(process_id, spec_instance);
    }

    // Return the instance,
    return instance;

  }

  /**
   * Tries to resume the process. There should be a high confidence that this
   * will succeed.
   */
  private void tryResume(ProcessId process_id,
                         ProcessInstanceImpl instance)
                                  throws PException, ProcessUserCodeException {

    // Get the process path and id string,
    String process_path = process_id.getProcessPath();
    String process_id_string = process_id.getStringValue();

    // Fetch the process information,
    ODBTransaction t =
                sessions_cache.getODBTransaction(process_path);
    // Find the process id,
    ODBObject root_ob = t.getNamedItem("root");
    ODBList all_processIdx = root_ob.getList("all_processIdx");
    // Fetch the process object,
    ODBObject process_odb =
                    all_processIdx.getObject(process_id_string);
    // If it's not here,
    if (process_odb == null) {
      // Oops, means there's inconsistency with the database
      // and the in-memory structure,
      PROCESS_LOG.log(Level.SEVERE,
                "Process not found: {0}", process_id_string);
      throw new PException("Process not found");
    }

    // When this returns, the process should be resumed (even
    // if it returns false).
    instance.executeResume(process_odb);

  }

  /**
   * Executes the given query provided the process is not suspended. If it
   * is suspended, the process instance is resumed first before the query
   * is executed. If 'try_immediate' is true then the query is attempted
   * immediately on the callee's thread (if this fails because the process
   * is suspended a resume/retry is attempted on the thread pool). If 
   * 'try_immediate' is false then this returns immediately (with null) and
   * the query is performed on the thread pool.
   */
  private PMessage submitQuery(final NIOConnection connection,
                               final ProcessId process_id,
                               final int call_id,
                               boolean try_immediate,
                               final ProcessQuery query) {

    // If we should try the query immediately,
    if (try_immediate) {
      // Get the instance (always returns non-null object),
      ProcessInstanceImpl instance = getProcessInstanceImpl(process_id);
      try {

        Throwable account_th = null;

        // Try executing the query,
        try {

          // Execute the query on the instance and return the immediate
          // result,
          return query.execute(instance);

        }
        catch (SuspendedProcessException e) {
          // Ignore (we try the query again in the thread pool this time)
        }
        catch (ProcessUserCodeException e) {
          account_th = e;
        }
        catch (PException e) {
          account_th = e;
        }
        catch (IOException e) {
          account_th = e;
        }

        // Log throwable,
        if (account_th != null) {
          logAccountException(instance, account_th);
          if (!(account_th instanceof ProcessUserCodeException)) {
            // Send this as a fail message,
            return failMessage(process_id, call_id, "USER", account_th);
          }
          return null;
        }

      }
      finally {
        instance.preventRemoveUnlock();
      }
    }

    // Delegate the function,
    getThreadPool().submit(new Runnable() {

      /**
       * Thread pool invocation.
       */
      @Override
      public void run() {

        try {

          // Get the instance (always returns non-null object),
          ProcessInstanceImpl instance = getProcessInstanceImpl(process_id);

          // When we get here, 'instance' will be a unique object for the
          // instance that may need to be resumed,

          // Try/Finally block that ensures we remove the 'prevent remove'
          // lock.
          try {

            PMessage reply_msg = null;

            // The account exception thrown (if any)
            Throwable account_th = null;

            try {

              int resume_count = 0;
              while (true) {
                try {
                  // Try and execute the query on the instance,
                  // This may throw 'SuspendedProcessException' which we
                  // catch and try and resume the instance if we get this.
                  reply_msg = query.execute(instance);
                  break;
                }
                catch (SuspendedProcessException e) {
                  // Tried to resume too many times, so fail,
                  if (resume_count > 3) {
                    throw new PException("Process resume failed");
                  }
                  // If suspended, we try and resume,
                  tryResume(process_id, instance);
                  ++resume_count;
                }
              }

            }
            catch (ProcessUserCodeException e) {
              account_th = e;
            }
            catch (PException e) {
              account_th = e;
            }
            catch (IOException e) {
              account_th = e;
            }

            // Log the account throwable
            if (account_th != null) {
              // Send this to the account's log system,
              logAccountException(instance, account_th);
//                PROCESS_LOG.log(Level.SEVERE,
//                           "Error during instance query dispatch", account_th);
              if (!(account_th instanceof ProcessUserCodeException)) {
                // Send this as a fail message,
                reply_msg = failMessage(process_id, call_id, "USER", account_th);
              }
            }

            // If there is a reply then send it immediately,
            if (reply_msg != null) {

              // If replying to a connection,
              if (connection != null) {
                try {
//                  System.out.println("DBG: Replying to: " + call_id);
                  // Send the reply message,
                  connection.sendFirstMessage(reply_msg);
                  connection.flushSendMessages();
                }
                catch (IOException e) {
                  // On IOException, close the connection,
                  PROCESS_LOG.log(Level.SEVERE,
                    "Closed connection due to IOException in delegated task", e);
                  e.printStackTrace(System.err);
                  connection.close();
                }
              }

            }

          }
          finally {
            instance.preventRemoveUnlock();
          }

        }
        catch (Throwable e) {
          // Oops,
          PROCESS_LOG.log(Level.SEVERE,
                          "Error during instance query dispatch", e);
          e.printStackTrace(System.err);
        }

      }

    });

    return null;

  }

  /**
   * Notifies the function dispatcher that items are ready on the function
   * queue of the process instance.
   */
  void notifyMessagesAvailable(ProcessId process_id) {
    function_dispatcher.notifyMessagesAvailable(process_id);
  }

  // ----- Process functions -----

  /**
   * Process initialization - this fails if the process is already initialized
   * on this server, or the process id is not allocated to this server. If
   * the conditions are met to initialize the process on this server then
   * the process instance is flagged as operating.
   */
  private PMessage processProcessInit(final NIOConnection connection,
                      final ProcessId process_id,
                      final int call_id, PMessage msg) {

    // Extract the arguments from the msg,
    Object[] args = msg.asArgsList(20);

    // The account the process is running on,
    String account_name = (String) args[0];
    // The application name for this process,
    String app_name = (String) args[1];
    // The process name,
    String process_name = (String) args[2];

    // The account application,
    AccountApplication account_app =
                                new AccountApplication(account_name, app_name);

    // Create a ProcessInstanceImpl,
    ProcessInstanceImpl instance = new ProcessInstanceImpl(
              ProcessServerService.this, process_id, account_app, process_name);

    // Try and manage the process id given,
    boolean success = process_set.setManaged(process_id, instance);

    if (!success) {
      return failMessage(process_id, call_id, "process_id in use");
    }

    try {
      // NOTE: Should we do a validity check here to make sure this server can
      //  handle this process? We'd need to look up the id in the sysprocessxx
      //  path.
      //  If we can fully trust the clients then this check isn't necessary.
      //  To implement a validated check, we'd need to delegate the function to
      //  the worker pool or have some sort of offline process that periodically
      //  downloads from the database.

      // Log that this process is being managed
      addLogProcessManaged(process_id);
      return successMessage(process_id, call_id);

    }
    finally {
      instance.preventRemoveUnlock();
    }

  }

  /**
   * Performs a function call on the given process running on this server. If
   * the process instance is not flagged on this server then an immediate
   * fail message is generated. Otherwise, the message is put onto the
   * function queue of the instance and the function call delegation thread
   * will call the 'function' method of the instance if needed.
   */
  private PMessage processFunctionCall(final NIOConnection connection,
                      final ProcessId process_id,
                      final int call_id, final PMessage msg,
                      final boolean reply_expected) {

    // Turn the message into a function invoke queue item,
    ProcessMessage pmsg = msg.asProcessMessage(20);
    final FunctionQueueItem function_item =
        new FunctionQueueItem(call_id,
             ProcessInputMessage.Type.FUNCTION_INVOKE, pmsg,
             null, null, connection, reply_expected);

    // Submit the query to push it onto the function queue (this will
    // probably execute immediately).
    return submitQuery(connection, process_id, call_id, true,
                       new ProcessQuery() {
      @Override
      public PMessage execute(ProcessInstanceImpl process_instance)
                  throws IOException, PException,
                         SuspendedProcessException, ProcessUserCodeException {

        // Fail if the instance is terminated,
        if (process_instance.isTerminated()) {
          return failMessage(process_id, call_id,
                             "process_id is not managed here");
        }

        // Put this item on the function queue,
        process_instance.pushToFunctionQueue(function_item);

        // Notify that new messages are available to process on this instance,
        notifyMessagesAvailable(process_id);

        // Return null,
        return null;
      }
    });
    
  }

  /**
   * Puts a signal onto the signal function queue of the instance.
   */
  private PMessage processSignalCall(final NIOConnection connection,
                      final ProcessId process_id,
                      final int call_id, final PMessage msg) {

    // Turn the message into a function invoke queue item,
    final ProcessMessage pmsg = msg.asProcessMessage(20);
    final FunctionQueueItem function_item =
        new FunctionQueueItem(call_id,
             ProcessInputMessage.Type.SIGNAL_INVOKE, pmsg,
             null, null, connection, false);

    // Submit the query to push it onto the function queue (this will
    // probably execute immediately).
    return submitQuery(connection, process_id, call_id, true,
                       new ProcessQuery() {
      @Override
      public PMessage execute(ProcessInstanceImpl process_instance)
                  throws IOException, PException,
                         SuspendedProcessException, ProcessUserCodeException {

        // Fail if the instance is terminated,
        if (process_instance.isTerminated()) {
          return failMessage(process_id, call_id,
                             "process_id is not managed here");
        }

        // Put the signal on to the start of the function queue,
        process_instance.putSignalOnQueue(function_item);

        // Notify that new messages are available to process on this instance,
        notifyMessagesAvailable(process_id);

        // Return null,
        return null;
      }
    });
    
  }

  /**
   * A request to receive broadcast messages from the given process by the
   * client on the other end of this connection. This should reply with a
   * command code '20' with the format '[process_id] [channel]'
   * <p>
   * This is called when a client attempts to consume broadcast messages.
   * Broadcast requests may be refreshed periodically by a client that's
   * listening for messages. Typically this is called every 2 minutes by
   * clients interested in the channel.
   */
  private PMessage processBroadcastRequest(final NIOConnection connection,
                      final ProcessId process_id,
                      int call_id, final PMessage msg) {

    // Sumbits a query on the process to the thread pool,
    return submitQuery(connection, process_id, call_id, false,
                       new ProcessQuery() {
      @Override
      public PMessage execute(ProcessInstanceImpl instance)
                                 throws SuspendedProcessException, PException {

        // If the instance is terminated then we send back a code '21' which
        // clears any listeners on this process,
        if (instance.isTerminated()) {
          // This is the 'terminated' process notification,
          byte[] msg_buf =
              createHeader(process_id, 0, CommConstants.NOTIFY_TERMINATED_CC);
          return new PMessage(msg_buf);
        }

        // The incoming message is (message size = 32 bytes),
        // [process_id] [0] [channel] [min_sequence_val]

        // We make an association for this broadcast channel,
        ByteBuffer bb = msg.asByteBuffer();
        int channel_num = bb.getInt(20);
        long min_sequence_val = bb.getLong(24);

        // Make a broadcast request for the given connection on the process
        // instance,

        try {
          instance.addBroadcastRequest(
                                   channel_num, connection, min_sequence_val);
          if (PROCESS_LOG.isLoggable(Level.FINE)) {
            PROCESS_LOG.log(Level.FINE,
                            "Broadcast request on {0} ( seq: {1} )",
                            new Object[] { process_id.getStringValue(),
                                           min_sequence_val });
          }

        }
        catch (IOException e) {
          throw new PException(e);
        }

        // NOTE: reply with a command code '20' to acknowledge the broadcast
        //   request.
        //   The format of the reply is (message size = 20 bytes),
        //   [process_id] [channel]

        // The acknowledgement message,
        byte[] msg_buf = createHeader(
              process_id, channel_num, CommConstants.ACK_BROADCAST_REQUEST_CC);
        return new PMessage(msg_buf);

      }
    });

  }

  /**
   * A request to receive broadcast messages from the given process by the
   * client on the other end of this connection.
   */
  private PMessage processProcessQueryRequest(final NIOConnection connection,
                      final ProcessId process_id,
                      final int call_id, PMessage msg) {

    // Sumbits a query on the process to the thread pool,
    return submitQuery(connection, process_id, call_id, true,
                       new ProcessQuery() {
      @Override
      public PMessage execute(ProcessInstanceImpl instance)
                                 throws SuspendedProcessException, PException {

        // Fail if the instance is terminated,
        if (instance.isTerminated()) {
          return failMessage(process_id, call_id,
                             "process_id is not managed here");
        }

        // If we can extract the values here then we are good to go,
        String process_name = instance.getProcessName();
        AccountApplication account_app = instance.getAccountApplication();
        // Reply with the args,
        Object[] args = new Object[3];
        args[0] = account_app.getAccountName();
        args[1] = account_app.getApplicationName();
        args[2] = process_name;

        return PMessage.encodeArgsList(
            createSuccessHeader(process_id, call_id,
                                CommConstants.CALL_REPLY_CC), args);

      }
    });

  }

  /**
   * A request to receive some details about the process server, such as a
   * summary of active processes.
   */
  private PMessage processServerQueryRequest(final NIOConnection connection,
                      final int call_id, final PMessage msg) {

    // Dispatch it,
    getThreadPool().submit(new Runnable() {

      @Override
      public void run() {

        try {
          // The query arguments,
          Object[] args = msg.asArgsList(20);

          // The account name that invoked this query,
          String invoker_account_name = (String) args[0];

          // The reply,
          PMessage reply_msg;
          // The reply header,
          byte[] reply_header =
                createSuccessHeader(null, call_id, CommConstants.CALL_REPLY_CC);

          // Execute the servers query,
          String result_str = ServersQueryFunctions.executeServersQuery(
                                              ProcessServerService.this, args);

          reply_msg = PMessage.encodeArgsList(
                          reply_header, new Object[] { result_str });

          // If replying to a connection,
          if (connection != null) {
            try {
              // Send the reply message,
              connection.sendFirstMessage(reply_msg);
              connection.flushSendMessages();
            }
            catch (IOException e) {
              // On IOException, close the connection,
              PROCESS_LOG.log(Level.SEVERE,
                "Closed connection due to IOException in delegated task", e);
              e.printStackTrace(System.err);
              connection.close();
            }
          }

        }
        catch (Throwable e) {
          // Oops,
          // These errors aren't associated with an account, therefore we can't
          // log them to an account's log system.
          PROCESS_LOG.log(Level.SEVERE,
                          "Error during instance query dispatch", e);
          e.printStackTrace(System.err);
        }

      }

    });

    return null;

  }

  /**
   * Process all the messages on the given connection. Follows same thread
   * semantics as the 'handleMessages' method.
   */
  private void processMessages(NIOConnection connection,
                            Collection<PMessage> messages) throws IOException {

    PMessage head_msg = null;
    PMessage last_msg = null;

    for (PMessage msg : messages) {

      ByteBuffer bb = msg.asByteBuffer();

      // Extract the command_id, process_instance_id and call_id from the byte
      // array,
      byte[] cmd = new byte[20];
      bb.get(cmd, 0, 20);
      byte command_code = cmd[0];
      cmd[0] = 0;
      byte path_val = cmd[8];
      cmd[8] = 0;
      long process_id1_val = ByteArrayUtil.getLong(cmd, 0);
      long process_id2_val = ByteArrayUtil.getLong(cmd, 8);
      int call_id = ByteArrayUtil.getInt(cmd, 16);

      // Create the process instance id object,
      ProcessId command_process_id =
              new ProcessId(path_val, process_id1_val, process_id2_val);

      // Message is either a process init (1), function call (2), broadcast
      // request (3), process query (7).

//      System.out.println("DBG: Processing message: " + call_id);

      PMessage reply_msg;

      // function init,
      if (command_code == CommConstants.FUNCTION_INIT_CC) {
        reply_msg = processProcessInit(
                        connection, command_process_id, call_id, msg);
      }
      // function call (reply expected),
      else if (command_code == CommConstants.CALL_RET_EXPECTED_CC) {
        reply_msg = processFunctionCall(
                        connection, command_process_id, call_id, msg, true);
      }
      // function call (reply not expected),
      else if (command_code == CommConstants.CALL_RET_NOT_EXPECTED_CC) {
        reply_msg = processFunctionCall(
                        connection, command_process_id, call_id, msg, false);
      }
      // broadcast request,
      else if (command_code == CommConstants.BROADCAST_REQUEST_CC) {
        reply_msg = processBroadcastRequest(
                        connection, command_process_id, call_id, msg);
      }
      // process query,
      else if (command_code == CommConstants.PROCESS_QUERY) {
        reply_msg = processProcessQueryRequest(
                        connection, command_process_id, call_id, msg);
      }
      // server query,
      else if (command_code == CommConstants.SERVICE_QUERY) {
        reply_msg = processServerQueryRequest(
                        connection, call_id, msg);
      }
      // send signal,
      else if (command_code == CommConstants.SEND_SIGNAL) {
        reply_msg = processSignalCall(
                        connection, command_process_id, call_id, msg);
      }

      else {
        PROCESS_LOG.log(Level.SEVERE,
                        "Message has invalid command_id: {0}", command_code);
        reply_msg = null;
      }

      // If the command produced an immediate reply,
      if (reply_msg != null) {
        // Add it to the linked list,
        if (head_msg == null) {
          head_msg = reply_msg;
        }
        else {
          last_msg.next = reply_msg;
        }
        last_msg = reply_msg;
      }

    }

    // Send,
    connection.sendAllMessages(head_msg);

  }

//  /**
//   * Function callback task.
//   */
//  private class ProcessCallbackTask extends TimerTask {
//
//    private final ProcessId process_id;
//    private final ProcessMessage msg;
//
//    private ProcessCallbackTask(ProcessId process_id, ProcessMessage msg) {
//      this.process_id = process_id;
//      this.msg = msg;
//    }
//
//    @Override
//    public void run() {
//      submitQuery(null, process_id, -1, false, new ProcessQuery() {
//        @Override
//        public PMessage execute(ProcessInstanceImpl instance)
//                   throws IOException, PException, SuspendedProcessException,
//                          ProcessUserCodeException {
//
//          instance.executeUserFunction(-1, msg);
//          return null;
//
//        }
//      });
//    }
//
//  }

  /**
   * Schedule a callback function on the instance of the given process id.
   */
  void doScheduleCallback(TimerTask callback_task, long delay_ms) {

    callback_scheduler.schedule(callback_task, delay_ms);

  }

  // ----- Process message dispatching -----

  /**
   * The event that manages sending broadcast messages from the process to
   * a connection.
   */
  private class PushBroadcastRunnable implements Runnable {
    private final NIOConnection conn;
    private PushBroadcastRunnable(NIOConnection conn) {
      this.conn = conn;
    }
    
    @Override
    public void run() {

      boolean elegant_cleanup = false;

      try {

        int update_version = -1;

        // The map to process in the loop,
        Map<ProcessInstanceImpl, MessagePushValue> local_pmap = new HashMap();

        while (true) {

          // Fetch the 'MessagePushValue' from the map. If it hasn't changed
          // since we started then we remove from the map and return,
          synchronized (broadcast_connect_map) {
            MessageBroadcastContainer container =
                                               broadcast_connect_map.get(conn);
            int cur_version = container.getUpdateVersion();
            // If no changes then we know we are up to date,
            if (cur_version == update_version) {
              broadcast_connect_map.remove(conn);
              // Unlock all the instances,
              container.cleanup();
              elegant_cleanup = true;
              break;
            }
            update_version = cur_version;

            // Make a copy of everything we are interested in,
            Map<ProcessInstanceImpl, MessagePushValue> global_pmap =
                                                                container.map;
            for (ProcessInstanceImpl instance : global_pmap.keySet()) {
              MessagePushValue mpv = local_pmap.get(instance);
              if (mpv == null) {
                mpv = new MessagePushValue();
                mpv.sent_sequences = new HashSet();
                local_pmap.put(instance, mpv);
              }
              mpv.copyFrom(global_pmap.get(instance));
            }

          }

          // Push all messages pending across all the instances,
          for (ProcessInstanceImpl instance : local_pmap.keySet()) {
            MessagePushValue mpv = local_pmap.get(instance);
            List<PMessage> msgs = instance.allBroadcastMessagesBetween(
                         mpv.min_seq_val, mpv.max_seq_val, mpv.sent_sequences);

//            System.out.println("PUSHING (" + mpv.min_seq_val + " to " +
//                             mpv.max_seq_val + ") size = " + msgs.size() );

            for (PMessage msg : msgs) {
              // NOTE: can block
              conn.sendFirstMessage(msg);
            }
          }

        }

        // NOTE: can block
        conn.flushSendMessages();

      }
      catch (IOException e) {
        PROCESS_LOG.log(Level.SEVERE,
                        "Cancelled broadcast because of IOException", e);
        e.printStackTrace(System.err);
        // On IOException, we close the connection,
        conn.close();
      }
      finally {
        // Make sure the connection is removed from the broadcast map,
        // We have to ensure this is cleaned otherwise we'll never have
        // a broadcast dispatch again.
        if (!elegant_cleanup) {
          synchronized (broadcast_connect_map) {
            MessageBroadcastContainer container =
                                               broadcast_connect_map.get(conn);
            broadcast_connect_map.remove(conn);
            // Unlock the instance making it able to be removed,
            container.cleanup();
          }
        }
      }

    }
  }

  /**
   * Called when new messages are added to the broadcast queue. This will
   * dispatch an event for each client connection interested in the process
   * channel that pushes the message out to the listener.
   */
  void notifyNewBroadcastMessage(
                           ProcessInstanceImpl instance, long sequence_value) {

    // Get all the connections interested in broadcasts from the instance,
    List<NIOConnection> connections = instance.getBroadcastListeners();
    List<NIOConnection> to_remove = null;
    synchronized (broadcast_connect_map) {
      for (final NIOConnection conn : connections) {
        // If the connection is not valid then we remove it and don't attempt
        // to notify.
        if (!conn.isValid()) {
          if (to_remove == null) {
            to_remove = new ArrayList();
          }
          to_remove.add(conn);
        }
        else {
          MessageBroadcastContainer container =
                                               broadcast_connect_map.get(conn);
          if (container == null) {
            container = new MessageBroadcastContainer();
            // Put into the map,
            broadcast_connect_map.put(conn, container);
            // Dispatch to the thread pool,
            // This is a privileged action since we will commonly end up here
            // via user-code.
            AccessController.doPrivileged(new PrivilegedAction() {
              @Override
              public Object run() {
                thread_pool.submit(new PushBroadcastRunnable(conn));
                return null;
              }
            });
          }
          container.notifyNewBroadcastMessage(instance, sequence_value);
        }
      }
    }

    // If there are connections to remove from the instance because the
    // connection is invalid,
    if (to_remove != null) {
      for (NIOConnection conn : to_remove) {
        instance.removeNIOConnection(conn);
      }
    }

  }

  // ----- Implemented from PEnvironment -----

  /**
   * Called when a new connection is accepted. If the channel is accepted
   * (for example, comes from a valid IP address), returns true. Otherwise
   * returns false.
   */
  @Override
  public boolean channelConnectionValid(SocketChannel ch) {
    // The connection is from here..
    InetAddress remote_address = ch.socket().getInetAddress();
    // Convert it into an IP address string,
    String address_ip;
    try {
      address_ip = ServerRolesSchema.canonicalIPString(remote_address);
    }
    catch (IOException e) {
      return false;
    }
    // Check the address is in the allowed hosts list,
    boolean allowed_ip = isAllowedIp(address_ip);
    // If the allowed ip is false, we reload the system data if we haven't
    // refreshed the ip information in a while,
    if (allowed_ip == false && notLoadedDBInAWhile()) {
      loadSystemData();
      allowed_ip = isAllowedIp(address_ip);
    }
    return allowed_ip;
  }

  /**
   * Returns the thread pool for this service.
   * @return 
   */
  @Override
  public ExecutorService getThreadPool() {
    return thread_pool;
  }

  /**
   * Initializes the connection.
   */
  @Override
  public void initializeConnection(NIOConnection connection) {
    // Pick a value used for connection handshake that is not between 0 and 256
    long rand_val = 0;
    while (rand_val >= 0 && rand_val < 256) {
      rand_val = SECURE_RNG.nextLong();
    }
    // Send initial handshake communication to the client,
    byte[] init_msg = new byte[20];
    ByteArrayUtil.setLong(0,        init_msg, 0);
    ByteArrayUtil.setLong(rand_val, init_msg, 8);
    ByteArrayUtil.setInt(1,         init_msg, 16);
    // The control code ensures the initialization message gets put on the
    // correct queue,
    init_msg[0] = CommConstants.CALL_REPLY_CC;
    // The message,
    PMessage msg = new PMessage(init_msg);
    // Immediately send the initialize message to the connection,
    try {
      connection.setStateLong(rand_val);
      connection.sendFirstMessage(msg);
      connection.flushSendMessages();
    }
    catch (IOException e) {
      PROCESS_LOG.log(Level.SEVERE,
                      "Connection initialization failed due to exception", e);
      e.printStackTrace(System.err);
      // Close the connection on error,
      connection.close();
    }
  }

  /**
   * Notifies when a connection is closed.
   */
  @Override
  public void connectionClosed(NIOConnection connection) {
  }

  /**
   * Handles messages from a connection.
   */
  @Override
  public void handleMessages(final NIOConnection connection) {
    // Fetch the collection of all messages,
    Collection<PMessage> messages = connection.consumeAllFromQueue();

    // No message so return,
    if (messages.isEmpty()) {
      return;
    }

    try {
      // If the connection is in initial state,
      Long state_long = connection.getStateLong();
      if (state_long == null) {
        // There was a message before initialization, so close.
        connection.close();
      }

      else {
        // If 'state_long' isn't 0, then we need to do a handshake,
        if (state_long != 0) {
          // Confirm the handshake,
          // There should be at least 1 message. If there's more than 1, the
          // first is the handshake confirmation.
          Iterator<PMessage> iterator = messages.iterator();
          PMessage msg = iterator.next();
          ByteBuffer msg_bb = msg.asByteBuffer();
          // The version (should be 0),
          long in_ver = msg_bb.getLong(0) & 0x000FFFFFFFFFFFFFFL;
          // The handshake value,
          long in_val = msg_bb.getLong(8);
          // The call_id (should be 1)
          int call_id = msg_bb.getInt(16);
          // handshake values don't match so close,
          if (in_ver != 0 || call_id != 1 || in_val != state_long) {
            // Bad handshake, so close the connection immediately and return
            // from this consuming any pending messages.
            connection.close();
            return;
          }

          // Otherwise connection is validated!
          connection.setStateLong((long) 0);

          // Process the remaining messages,
          List<PMessage> remaining_messages = new ArrayList();
          while (iterator.hasNext()) {
            remaining_messages.add(iterator.next());
          }

          // Handshake was successful so set 'messages' to the remaining
          // messages,
          messages = remaining_messages;
        }

        // Process any remaining messages,
        if (!messages.isEmpty()) {
          // Process all the messages,
          processMessages(connection, messages);
        }

      }

    }
    catch (IOException e) {
      PROCESS_LOG.log(Level.SEVERE,
                      "IOException during message handling", e);
      e.printStackTrace(System.err);
      // Close the connection on error,
      connection.close();
    }
  }

  /**
   * Returns the LoggerService for the given account. This will validate the
   * account name and throw a PException if the account_name is invalid.
   */
  LoggerService getLoggerServiceForAccount(String account_name)
                                                            throws PException {

    // The system platform,
    ODBTransaction t =
                  sessions_cache.getODBTransaction(SystemStatics.SYSTEM_PATH);
    ODBObject accounts_root = t.getNamedItem("accounts");
    ODBList accounts = accounts_root.getList("accountIdx");
    ODBObject account_ob = accounts.getObject(account_name);
    if (account_ob == null) {
      throw new PException("Account not valid: " + account_name);
    }

    // Cache the logger service via the AccountInfo,
    synchronized (ACCOUNT_INFO_LOCK) {
      AccountInfo account_info = account_info_cache.get(account_name);
      if (account_info == null) {
        account_info = new AccountInfo();
        String log_path = "ufs" + account_name;
        account_info.logger_service =
                    new LoggerService(sessions_cache, log_path, process_timer);
        account_info_cache.put(account_name, account_info);
      }
      return account_info.logger_service;
    }
  }

  /**
   * Same as 'getLoggerServiceForAccount' only this method checks the
   * local cache first before going to the database to ask for the account
   * logger.
   */
  LoggerService fastGetLoggerServiceForAccount(String account_name)
                                                            throws PException {

    // Check the logger service cache via the AccountInfo,
    synchronized (ACCOUNT_INFO_LOCK) {
      AccountInfo account_info = account_info_cache.get(account_name);
      if (account_info != null) {
        return account_info.logger_service;
      }
    }

    // Delegate,
    return getLoggerServiceForAccount(account_name);

  }

  /**
   * Creates a string that provides a summary of all the process ids.
   */
  private static String createProcessIdSummary(
                                         Collection<ProcessId> process_list) {
    StringBuilder b = new StringBuilder();
    for (ProcessId process_id : process_list) {
      b.append(process_id.getStringValue()).append(", ");
    }
    return b.toString();
  }

  // ----------

  /**
   * AccountInfo object that contains static information about an account.
   */
  private static class AccountInfo {
    
    // The LoggerService
    private LoggerService logger_service;

  }

  /**
   * An ODBTransaction that has a self-reported flag on whether it has been
   * changed or not.
   */
  private static class ChangeReportedODBTransaction {
    private final ODBTransaction transaction;
    private boolean changed = false;
    private ChangeReportedODBTransaction(ODBTransaction odbt) {
      this.transaction = odbt;
    }
  }

  // ---- Scheduled flush events ----

  /**
   * Fetch the ODBTransaction for the process_id. If it's in the
   * 'process_paths_changed' map then retrieve it from there, otherwise look
   * it up in the sessions cache and put it into the map.
   */
  private ChangeReportedODBTransaction fetchTransaction(
                 Map<Byte, ChangeReportedODBTransaction> process_paths_changed,
                 ProcessId process_id) {

    Byte path_b = process_id.getPathValue();
    ChangeReportedODBTransaction t = process_paths_changed.get(path_b);
    // If not in the map, create it and put it in the map,
    if (t == null) {
      ODBTransaction odbt =
                 sessions_cache.getODBTransaction(process_id.getProcessPath());
      t = new ChangeReportedODBTransaction(odbt);
      process_paths_changed.put(path_b, t);
    }

    return t;
  }

  /**
   * Handles the process modification log.
   */
  private void flushProcessModifyLog(
               Map<Byte, ChangeReportedODBTransaction> process_paths_changed) {

    List<String> log_entries;
    synchronized (process_modify_log) {
      int sz = process_modify_log.size();
      // Nothing to process, so exit,
      if (sz == 0) {
        return;
      }
      log_entries = new ArrayList(sz);
      log_entries.addAll(process_modify_log);
      // Clear the log,
      process_modify_log.clear();
    }

    int count = 0;

    // For each entry,
    for (String entry : log_entries) {
      // The entry type,
      char c = entry.charAt(0);
      // Process id being managed event,
      if (c == '+') {
        // Decode the process id information,
        String process_id_string = entry.substring(1);
        ProcessId process_id = ProcessId.fromString(process_id_string);
        // Delete the record from the process path,
        ChangeReportedODBTransaction crt =
                           fetchTransaction(process_paths_changed, process_id);
        ODBTransaction t = crt.transaction;
        // Find the process id,
        ODBObject root_ob = t.getNamedItem("root");
        ODBList avail_processIdx = root_ob.getList("available_processIdx");
        boolean removed = avail_processIdx.remove(process_id_string);
        if (removed) {
          crt.changed = true;
          ++count; 
        }
        else {
          // If this happens, it means the client created some process ids
          // and allocated against one of them, but this view  (the servers)
          // of the database is not yet consistent with the clients view (we
          // don't see the process ids yet) because the cleanup happened too
          // recently after the client created the ids.
          // In this case, we put the id back onto the modify log so that
          // it is cleaned in the next cycle. We need to make sure these ids
          // are cleaned before the process instance is removed from memory.
          // PENDING: We might want to put these into a priority cleaner that
          // separately removes the keys.
          addLogProcessManaged(process_id);
          PROCESS_LOG.log(Level.FINE,
                  "Process {0} returned to log to be cleaned next cycle",
                  process_id_string);
        }
      }
      else {
        PROCESS_LOG.log(Level.SEVERE,
                        "Discarding log entry. Unknown type: {0}", c);
      }
    }

    if (count > 0) {
      PROCESS_LOG.log(Level.FINE,
                     "Removed {0} process_ids from the available set", count);
    }

  }

  /**
   * Flush any process instances whose state is pending to be flushed to
   * the database.
   */
  private void flushProcessInstances(
               Collection<ProcessId> terminated_processes,
               Collection<ProcessId> stale_processes,
               Collection<ProcessId> suspended_processes,
               Collection<ProcessId> outofdate_processes,
               Map<Byte, ChangeReportedODBTransaction> process_paths_changed) {

    // Iterate through each process in the process set and if it's flushable
    // then flush it. Otherwise, if it's terminated then add to the
    // terminated set.

    int flush_total = 0;
    int flush_count = 0;

//    StringBuilder flushed_ids_summary = new StringBuilder();

    Iterator<ProcessId> all_processes = process_set.getAllManaged();
    while (all_processes.hasNext()) {
      ProcessId process_id = all_processes.next();
      ProcessInstanceImpl instance = process_set.getInstance(process_id);
      instance.preventRemoveUnlock();

      // Clean any expired broadcast messages from the instance,
      int expired_count = instance.cleanExpiredBroadcastMessages();
      if (expired_count > 0) {
        PROCESS_LOG.log(Level.FINE,
                "Removed {0} expired broadcast messages from instance",
                new Object[] { expired_count });
      }
      // Clean any expired connections to the instance,
      int expired_conn_count = instance.cleanExpiredConnections();
      if (expired_conn_count > 0) {
        PROCESS_LOG.log(Level.FINE,
                "Removed {0} expired connections from instance",
                new Object[] { expired_conn_count });
      }

      // Is it terminated?
      if (instance.isTerminated()) {
        terminated_processes.add(process_id);
      }
      // Is it suspended?
      else if (instance.isSuspended()) {
        // This makes it eligible for GC
        suspended_processes.add(process_id);
      }
      // Otherwise (not terminated),
      // Is the instance currently flushable?
      else {
        if (instance.isCurrentlyFlushable()) {

          // Yes, so flush it!
          ChangeReportedODBTransaction crt =
                           fetchTransaction(process_paths_changed, process_id);
          ODBTransaction t = crt.transaction;
          // The record key,
          String process_id_string = process_id.getStringValue();
          // Find the process id,
          ODBObject root_ob = t.getNamedItem("root");
          ODBList avail_processIdx = root_ob.getList("all_processIdx");
          ODBObject process_ob = avail_processIdx.getObject(process_id_string);

          // If not found,
          if (process_ob == null) {
            // Oops, means there's inconsistency with the database and the
            // in-memory structure,
            String err_msg = MessageFormat.format(
                        "Flushed process not found: {0}", process_id_string);
            PROCESS_LOG.log(Level.SEVERE, err_msg);
            System.err.println(err_msg);
          }
          else {

            Throwable account_th = null;

            // Flush to the 'state' field,
            try {
//              flushed_ids_summary.append(process_id_string).append(", ");
              ++flush_total;
              boolean flushed = instance.executeFlush(process_ob);
              ++flush_count;
              // If flushed then report in the change reported ODB transaction,
              if (flushed) {
                crt.changed = true;
              }
            }
            catch (SuspendedProcessException e) {
              // Shouldn't be possible because we should have an exclusive lock
              // on the instance,
              account_th = e;
            }
            catch (ProcessUserCodeException e) {
              account_th = e;
            }
            catch (PException e) {
              account_th = e;
            }

            if (account_th != null) {
              // Send this to the account's log system,
              logAccountException(instance, account_th);
//              PROCESS_LOG.log(Level.SEVERE,
//                              "Flush failed for process {0} {1}",
//                              new Object[] { process_id_string,
//                                             instance.getPrintString() });
//              // Log the error,
//              PROCESS_LOG.log(Level.SEVERE,
//                              "Exception during process flush", account_th);
            }

          }
        }

        // If the process is stale then add to the stale_processes set,
        // We make sure to perform this test regardless of whether a flush
        // actually happened or not.
        if (instance.isCurrentlyStale()) {
          // Yes, so add to the stale set.
          // This will make this process eligible to be suspended,
          stale_processes.add(process_id);
        }
        // Else if the instance is out of date then we add to the outofdate
        // set,
        else if (instance.isOutOfDate()) {
          outofdate_processes.add(process_id);
        }
      }
    }

    if (flush_total > 0) {
      PROCESS_LOG.log(Level.FINE, "Flushed {0} processes (out of {1})",
                                  new Object[] { flush_count, flush_total });
//      PROCESS_LOG.log(Level.FINE, "Flushed: {0}", flushed_ids_summary);
    }

  }

  /**
   * Attempts to reload the processes in the given set (it is determined the
   * class loader of the process is out of date).
   */
  private void reloadOutOfDateInstances(Set<ProcessId> outofdate_processes,
               Map<Byte, ChangeReportedODBTransaction> process_paths_changed)
                                                            throws PException {

    int reload_count = 0;
    int reload_total = 0;

    // For each out of date process,
    for (ProcessId process_id : outofdate_processes) {

      // Fetch the process instance,
      ProcessInstanceImpl instance = process_set.getInstance(process_id);
      instance.preventRemoveUnlock();

      String process_id_string = process_id.getStringValue();
      // Get the transaction for the process
      ChangeReportedODBTransaction crt =
                          fetchTransaction(process_paths_changed, process_id);
      ODBTransaction t = crt.transaction;
      // Find the process id,
      ODBObject root_ob = t.getNamedItem("root");
      ODBList all_processIdx = root_ob.getList("all_processIdx");
      // Fetch the process object,
      ODBObject process_ob = all_processIdx.getObject(process_id_string);
      // If it's not here,
      if (process_ob == null) {
        // Oops, means there's inconsistency with the database and the
        // in-memory structure,
        String err_msg = MessageFormat.format(
                      "Out of date process not found: {0}", process_id_string);
        PROCESS_LOG.log(Level.SEVERE, err_msg);
        System.err.println(err_msg);
      }
      else {

        // Otherwise, try and reload the process.
        // This may not be possible for any number of reasons.

        boolean reload_happened = false;
        Throwable account_th = null;
        try {
          ++reload_total;
          boolean is_reloaded = instance.executeReload(process_ob);
          reload_happened = true;
          ++reload_count;
          // If it was suspended,
          if (is_reloaded) {
            crt.changed = true;
          }
        }
        catch (PException ex) {
          account_th = ex;
        }
        catch (ProcessUserCodeException ex) {
          account_th = ex;
        }
        finally {
          if (!reload_happened) {
            PROCESS_LOG.log(Level.FINE,
                            "No reload: {0}", process_id_string);
          }
        }
        
        if (account_th != null) {
          // If the reload generated an exception then log it to the account's
          // log system.
          logAccountException(instance, account_th);
//          PROCESS_LOG.log(Level.SEVERE, "Reload failed for process {0} {1}",
//                   new Object[]{process_id_string, instance.getPrintString()});
//          PROCESS_LOG.log(Level.SEVERE,
//                          "Exception during process reload", e);
        }

      }

    }

    if (reload_total > 0) {
      PROCESS_LOG.log(Level.FINE,
                      "Reload on {0} processes (out of {1})",
                      new Object[] { reload_count, reload_total });
    }

  }

  /**
   * Attempts to suspend processes that are determined to be stale (have not
   * been accessed recently).
   */
  private void suspendStaleInstances(Set<ProcessId> stale_processes,
                Map<Byte, ChangeReportedODBTransaction> process_paths_changed)
                                                            throws PException {

    int suspend_count = 0;
    int suspend_total = 0;

    // For each stale process,
    for (ProcessId process_id : stale_processes) {

      // Fetch the process instance,
      ProcessInstanceImpl instance = process_set.getInstance(process_id);
      instance.preventRemoveUnlock();

      String process_id_string = process_id.getStringValue();
      // Get the transaction for the process
      ChangeReportedODBTransaction crt =
                          fetchTransaction(process_paths_changed, process_id);
      ODBTransaction t = crt.transaction;
      // Find the process id,
      ODBObject root_ob = t.getNamedItem("root");
      ODBList all_processIdx = root_ob.getList("all_processIdx");
      // Fetch the process object,
      ODBObject process_ob = all_processIdx.getObject(process_id_string);
      // If it's not here,
      if (process_ob == null) {
        // Oops, means there's inconsistency with the database and the
        // in-memory structure,
        String err_msg = MessageFormat.format(
                            "Stale process not found: {0}", process_id_string);
        PROCESS_LOG.log(Level.SEVERE, err_msg);
        System.err.println(err_msg);
      }
      else {

        // Otherwise, try and suspend the process.
        // This will attempt to suspend the instance. This may not be possible
        // for a number of reasons;
        // 1) The process may not be suspendable.
        // 2) The process may have been stale but there was interaction
        //    recently that changed its 'staleness'
        // 3) An exclusive lock could not be obtained on the instance.

        Throwable account_th = null;
        boolean suspend_happened = false;
        try {
          ++suspend_total;
          boolean db_changed = instance.executeSuspend(process_ob);
          // Suspend success!
          suspend_happened = true;
          ++suspend_count;
          // If it was suspended,
          if (db_changed) {
            crt.changed = true;
          }
        }
        catch (SuspendedProcessException e) {
          // Shouldn't be possible for this to be thrown,
          account_th = e;
        }
        catch (ProcessUserCodeException e) {
          account_th = e;
        }
        catch (PException e) {
          account_th = e;
        }
        finally {
          if (!suspend_happened) {
            PROCESS_LOG.log(Level.FINE,
                            "No suspend: {0}", process_id_string);
          }
        }

        // Send this to the account's log system,
        if (account_th != null) {

          logAccountException(instance, account_th);

//          PROCESS_LOG.log(Level.SEVERE, "Suspend failed for process {0} {1}",
//                   new Object[]{process_id_string, instance.getPrintString()});
//          PROCESS_LOG.log(Level.SEVERE,
//                          "Exception during process suspend", account_th);

        }

      }

    }

    if (suspend_total > 0) {
      PROCESS_LOG.log(Level.FINE,
                      "Suspend on {0} processes (out of {1})",
                      new Object[] { suspend_count, suspend_total });
//      String stale_ids_summary = createProcessIdSummary(stale_processes);
//      PROCESS_LOG.log(Level.FINE, "Stales: {0}", stale_ids_summary);
    }

  }

  /**
   * Finds all terminated process instances in the ProcessSet and removes
   * them, allowing the GC to free up resources associated with it.
   */
  private void cleanProcessSet(
                   Collection<ProcessId> terminated_processes,
                   Collection<ProcessId> suspended_processes) {

    PROCESS_LOG.log(Level.FINE, "Clean START");

    // For each terminated process, remove from the managed set,
    if (!terminated_processes.isEmpty()) {
      int count = process_set.removeAllManaged(terminated_processes);
      PROCESS_LOG.log(Level.FINE,
                     "Removed {0} process_ids from the terminated set", count);
    }

    if (!suspended_processes.isEmpty()) {
      int count = process_set.removeAllStaleManaged(suspended_processes);
      int total = suspended_processes.size();
      PROCESS_LOG.log(Level.FINE,
                "Removed {0} process_ids (out of {1}) from the suspended set",
                new Object[] { count, total });
    }

//    if (!terminated_processes.isEmpty() || !suspended_processes.isEmpty()) {
//      String terminated_ids_summary =
//                                  createProcessIdSummary(terminated_processes);
//      String suspended_ids_summary =
//                                  createProcessIdSummary(suspended_processes);
//      PROCESS_LOG.log(Level.FINE,
//                      "Cleaned terminated: {0}", terminated_ids_summary);
//      PROCESS_LOG.log(Level.FINE,
//                      "Cleaned suspended: {0}", suspended_ids_summary);
//    }

  }

  /**
   * Notification that the given process instance is currently using a
   * class loader with application code that is out of date (a new version is
   * available). This should force all the instances that are out of date
   * to flush.
   * <p>
   * Note that this may be called a lot if there are many processes being
   * run and the application updates.
   */
  private final AtomicBoolean out_of_date_flush_active =
                                                    new AtomicBoolean(false);
  void notifyOutOfDateProcessInstances(ProcessInstanceImpl process_instance) {
    if (out_of_date_flush_active.compareAndSet(false, true)) {
      // Flush after 2 seconds,
      process_timer.schedule(new ProcessMaintenanceTimerTask(), 2000);
    }
  }

  /**
   * A thread that keeps track of when a process has queue items that must
   * be processed, and calls back on them.
   */
  private class FunctionDispatcherThread extends Thread {

    private final Object lock = new Object();
    private boolean finished = false;
    
    private Set<ProcessId> instance_events = new HashSet();
    

    public FunctionDispatcherThread() {
      super("Mckoi Function Dispatcher");
    }

    /**
     * Notify to this dispatcher thread that events are pending.
     */
    void notifyMessagesAvailable(ProcessId id) {
      synchronized (lock) {
        instance_events.add(id);
        lock.notifyAll();
      }
    }

    /**
     * Dispatch the function operation on the thread pool.
     */
    private void dispatchFunctionOperation(final ProcessId process_id) {
      
      submitQuery(null, process_id, -1, false, new ProcessQuery() {
        @Override
        public PMessage execute(ProcessInstanceImpl process_instance)
                   throws IOException, PException,
                          SuspendedProcessException, ProcessUserCodeException {

          // We assume user code ran (in the case of an exception being
          // thrown from user code).
          boolean user_code_ran = true;
          boolean is_suspended = false;
          
          try {
            user_code_ran = process_instance.callUserFunctionExecute();
          }
          catch (SuspendedProcessException e) {
            // It's suspended, so set the flag,
            is_suspended = true;
            throw e;
          }
          finally {
            // If user code ran and it's not suspended, then we do some
            // checking. Note that if it is suspended the 'submitQuery'
            // method will retry this operation once it has resumed.
            if (!is_suspended && user_code_ran) {
              // If we didn't consume any messages,
              int cur_count = process_instance.incNoConsumeCount();
              PROCESS_LOG.log(Level.FINE, "cur_count = {0}", cur_count);
              // We tried to dispatch on this process instance but it's not
              // consuming messages. This means we need to terminate the
              // instance.
              if (cur_count > 32) {
                // Close it,
                process_instance.close();
                // Log an exception,
                logAccountException(process_instance, new PRuntimeException(
                    "Terminated process because it's not consuming messages."));
              }

              // If the instance is closed then we don't put any messages on the
              // function queue,
              if (process_instance.isTerminated()) {
                process_instance.cleanFunctionQueue();
              }
              else {
                // If there's still items after user code was run then the
                // process is still marked as 'dirty',
                if (user_code_ran && !process_instance.isFunctionQueueEmpty()) {
                  notifyMessagesAvailable(process_id);
                }
              }
            }
          }

          return null;

        }
      });

    }

    @Override
    public void run() {
      int loop_count_without_sleep = 0;
      try {
        synchronized (lock) {
          while (!finished) {

            if (instance_events.isEmpty()) {
              loop_count_without_sleep = 0;
              lock.wait();
            }
            else {
              ++loop_count_without_sleep;
              if (loop_count_without_sleep > 16) {
                loop_count_without_sleep = 0;
                lock.wait(0);
              }
            }

            // Trigger events,
            for (ProcessId id : instance_events) {
              dispatchFunctionOperation(id);
            }

            instance_events.clear();

          }
        }
      }
      catch (InterruptedException e) {
        throw new RuntimeException("Interrupted", e);
      }
    }

    private void finish() {
      synchronized (lock) {
        finished = true;
        lock.notifyAll();
      }
    }

  }

  /**
   * A timer task the periodically flushes any pending state to the system
   * platform paths.
   */
  private void processMaintenanceAction() {
    try {

//      // Reports,
//      System.out.println("PROCESS FLUSH REPORT");
//      System.out.println("--------------------");
//      System.out.println(server_thread.report());

      PROCESS_LOG.fine("Maint START");

      // Map of process paths we changed.
      Map<Byte, ChangeReportedODBTransaction>
                                       process_paths_changed = new HashMap();

      // Flush the process modification log entries,
      flushProcessModifyLog(process_paths_changed);

      // Processes that have terminated,
      Set<ProcessId> terminated_processes = new HashSet(128);
      // Stale processes,
      Set<ProcessId> stale_processes = new HashSet(128);
      // Suspended processes,
      Set<ProcessId> suspended_processes = new HashSet(128);
      // Out of date processes,
      Set<ProcessId> outofdate_processes = new HashSet(128);

      // Flush process instances,
      flushProcessInstances(terminated_processes,
                            stale_processes,
                            suspended_processes,
                            outofdate_processes,
                            process_paths_changed);

      // Reload the out of date processes,
      if (!outofdate_processes.isEmpty()) {
        reloadOutOfDateInstances(outofdate_processes,
                                 process_paths_changed);
      }

      // If there are stale instances,
      if (!stale_processes.isEmpty()) {
        suspendStaleInstances(stale_processes,
                              process_paths_changed);
      }

      // Commit all the process paths we changed,
      Collection<ChangeReportedODBTransaction> crts =
                                              process_paths_changed.values();
      if (!crts.isEmpty()) {
        try {
          for (ChangeReportedODBTransaction crt : crts) {
            // Commit if the transaction was reported to have changed,
            if (crt.changed) {
              crt.transaction.commit();
            }
          }
        }
        catch (CommitFaultException e) {
          // This shouldn't be possible,
          throw new PRuntimeException(e);
        }
      }

      // Clean up the process set for terminated processes,
      cleanProcessSet(terminated_processes, suspended_processes);

      PROCESS_LOG.fine("Maint END");

    }
    catch (Exception e) {
      PROCESS_LOG.log(Level.SEVERE, "Exception during flush task", e);
      e.printStackTrace(System.err);
    }
    finally {
      // Make sure to reset the flush notification,
      out_of_date_flush_active.set(false);
    }
  }

  private class ProcessMaintenanceTimerTask extends TimerTask {
    @Override
    public void run() {
      processMaintenanceAction();
    }
  };

  // -----

  /**
   * Used for broadcast message dispatching.
   */
  private static class MessagePushValue {
    private long min_seq_val;
    private long max_seq_val;
    private Set<Long> sent_sequences;
    MessagePushValue(long sequence_value) {
      this.min_seq_val = sequence_value;
      this.max_seq_val = sequence_value;
    }
    MessagePushValue() {
      this.min_seq_val = Long.MAX_VALUE;
      this.max_seq_val = -1;
    }
    private void updateSequenceValue(long new_seq) {
      if (new_seq < min_seq_val) {
        min_seq_val = new_seq;
      }
      if (new_seq > max_seq_val) {
        max_seq_val = new_seq;
      }
    }
    private void copyFrom(MessagePushValue src) {
      if (src.min_seq_val < min_seq_val) {
        min_seq_val = src.min_seq_val;
      }
      if (src.max_seq_val > max_seq_val) {
        max_seq_val = src.max_seq_val;
      }
    }
  }

  /**
   * Container for message broadcasts from instances.
   */
  private static class MessageBroadcastContainer {
    private int update_version = 0;
    private final Map<ProcessInstanceImpl, MessagePushValue> map =
                                                                 new HashMap();
    private void notifyNewBroadcastMessage(
                           ProcessInstanceImpl instance, long sequence_value) {
      MessagePushValue mpv = map.get(instance);
      if (mpv == null) {
        mpv = new MessagePushValue(sequence_value);
        map.put(instance, mpv);
        instance.preventRemoveLock();
      }
      else {
        mpv.updateSequenceValue(sequence_value);
      }
      ++update_version;
    }
    private int getUpdateVersion() {
      return update_version;
    }

    private void cleanup() {
      for (ProcessInstanceImpl instance : map.keySet()) {
        instance.preventRemoveUnlock();
      }
    }
  }

  /**
   * A closure for a query on a process. This is used to perform an arbitrary
   * query on a process instance. The 'execute' method may choose to throw
   * SuspendedProcessException which causes the system resume the process
   * instance and then retry executing the process on the resumed instance.
   */
  private static interface ProcessQuery {
    PMessage execute(ProcessInstanceImpl process_instance)
                     throws IOException, PException, SuspendedProcessException,
                            ProcessUserCodeException;
  }

}
