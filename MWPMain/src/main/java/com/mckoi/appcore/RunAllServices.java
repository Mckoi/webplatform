/**
 * com.mckoi.appcore.RunAllServices  Sep 16, 2012
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

import com.mckoi.network.NetworkConfigResource;
import com.mckoi.network.TCPInstanceAdminServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A Java application that starts both a MckoiDDB node and a Mckoi Web
 * Platform bootstrap node (com.mckoi.appcore.AppServiceNode) using the
 * configuration information found at the given path. The configuration files
 * specifically used are the 'mwp_main.conf', 'node.conf' and 'node_info'
 * files.
 *
 * @author Tobias Downer
 */

public class RunAllServices {

  /**
   * The 'main' method.
   */
  public static void main(String[] args) {

    System.out.println("RunAllServices - Starts all Mckoi 'daemon' services (Mckoi ");
    System.out.println("  Web Platform and MckoiDDB)");
    System.out.println();
    System.out.println("See README for license details.");
    System.out.println();

    // The base path location of the configuration files,
    if (args.length == 0) {
      System.out.println("Must provide installation path.");
    }

    try {
      File install_path = new File(args[0]);
      if (!install_path.exists() || !install_path.isDirectory()) {
        System.out.println("Invalid path.");
      }

      // The configuration files,
      File mwp_main_file = new File(install_path, "mwp_main.conf");
      File node_file = new File(install_path, "node.conf");
      File node_info_file = new File(install_path, "node_info");

      // Check these files exist,
      checkFileExists(mwp_main_file);
      checkFileExists(node_file);
      checkFileExists(node_info_file);

      // Load each of the files as java.util.Properties
      
      FileInputStream fin1 = new FileInputStream(mwp_main_file);
      FileInputStream fin2 = new FileInputStream(node_file);
      FileInputStream fin3 = new FileInputStream(node_info_file);
      final Properties mwp_main = new Properties();
      final Properties node = new Properties();
      final Properties node_info = new Properties();
      mwp_main.load(fin1);
      node.load(fin2);
      node_info.load(fin3);
      fin1.close();
      fin2.close();
      fin3.close();

      // Where's the network.conf file?
      String netconf_info_value = mwp_main.getProperty("mckoi_netconf_info");
      String network_conf_loc;
      if (netconf_info_value == null) {
        network_conf_loc =
                      mwp_main.getProperty("mckoi_network", "./network.conf");
      }
      else {
        // Load from the netconf_info file,
        File netconf_info_file = new File(netconf_info_value);
        FileInputStream fin4 = new FileInputStream(netconf_info_file);
        Properties netconf_info_prop = new Properties();
        netconf_info_prop.load(fin4);
        fin4.close();
        network_conf_loc = netconf_info_prop.getProperty("netconf_location");
      }

      // Fetch the host and the port,
      String ddb_host = node_info.getProperty("mckoiddbhost");
      String ddb_port = node_info.getProperty("mckoiddbport");
      
      if (ddb_host == null) {
        throw new RASException("No 'host' property found in 'node_info' file.");
      }
      if (ddb_port == null) {
        throw new RASException("No 'port' found in 'node_info' file.");
      }

      Logger log = Logger.getLogger("com.mckoi.network.Log");
      // Don't propogate log messages,
      log.setUseParentHandlers(false);

      // There's now enough information to start the services,

      // --- DDB service ---

      // Parse the network configuration string,
      NetworkConfigResource net_config_resource =
                                 NetworkConfigResource.parse(network_conf_loc);

      // The base path,
      InetAddress host;
      host = InetAddress.getByName(ddb_host);

      int port;
      try {
        port = Integer.parseInt(ddb_port);
      }
      catch (Throwable e) {
        throw new RASException("Unable to parse port argument: " + ddb_port);
      }

      ddb_host = ddb_host + " ";
      System.out.println("MckoiDDB service being started.");
      System.out.println("Machine Node, " + ddb_host +
                         "port: " + ddb_port);

      // Make the instance object and start it on its own thread,
      TCPInstanceAdminServer inst =
            new TCPInstanceAdminServer(net_config_resource,
                                       host, port, node);
      new Thread(inst).start();

      // Wait until the server is started,
      inst.waitUntilStarted();

      // Sleep for 5 seconds
      try {
        Thread.sleep(5000);
      }
      catch (InterruptedException e) { /* ignore */ }

      // --- MWP service ---

      System.out.println("Mckoi Web Platform service being started.");

      // Start the MWP bootstrap service on a new thread,

      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            AppServiceNode.serviceMain(mwp_main);
          }
          catch (IOException e) {
            System.out.println("MWP service stopped because of IOException.");
            e.printStackTrace(System.err);
          }
          catch (RuntimeException e) {
            System.out.println("MWP service stopped because of exception.");
            e.printStackTrace(System.err);
          }
        }
      }).start();

      // Done.

    }
    catch (IOException e) {
      System.out.println("Aborted because of IOException.");
      e.printStackTrace(System.err);
      System.exit(-1);
    }
    catch (RASException e) {
      System.out.println();
      System.out.println(e.getMessage());
      System.out.println("Aborted.");
      System.exit(-1);
    }

  }

  /**
   * Checks if the file exists and is a file, otherwise throws an
   * IOException.
   */
  private static void checkFileExists(File file) throws IOException {
    if (!file.exists() || !file.isFile()) {
      throw new RASException(
              "Required file is missing: " + file.getCanonicalPath());
    }
  }

  private static class RASException extends RuntimeException {
    public RASException(Throwable cause) {
      super(cause);
    }
    public RASException(String message, Throwable cause) {
      super(message, cause);
    }
    public RASException(String message) {
      super(message);
    }
    public RASException() {
    }
  }

}
