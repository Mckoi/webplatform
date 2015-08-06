/**
 * com.mckoi.mwpcore.AbstractCoreProcess  Mar 19, 2012
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

import com.mckoi.appcore.AppServiceNode;
import java.io.*;
import java.net.URL;
import java.util.Map;
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
    MWPCORE_LOG = Logger.getLogger("com.mckoi.mwpcore.Log");
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
  protected void init() {

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
        // Don't propogate log messages,
        log.setUseParentHandlers(false);

        // Output to the log file,
        String log_file_name =
                          new File(log_path, "mwpcore.log").getCanonicalPath();
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
        // Don't propogate log messages,
        log.setUseParentHandlers(false);

        // Output to the log file,
        String log_file_name =
                       new File(log_path, "ddbappcore.log").getCanonicalPath();
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
