/**
 * com.mckoi.appcore.MWPServerRegister  Sep 14, 2012
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

import com.mckoi.network.MckoiDDBClient;
import com.mckoi.network.MckoiDDBClientUtils;
import com.mckoi.network.NetworkConfigResource;
import com.mckoi.network.NetworkProfile;
import com.mckoi.util.IOWrapStyledPrintWriter;
import com.mckoi.util.StyledPrintWriter;
import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.util.Properties;

/**
 * A tool that connects to the Mckoi Web Platform database and registers
 * the server details pointed to by the given properties file.
 *
 * @author Tobias Downer
 */

public class MWPServerRegister {



  /**
   * Application invocation.
   */
  public static void main(String[] args) {
    
    System.out.println("MWPServerRegister - Mckoi Server Registration");
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

      String home_dir = config_properties.getProperty("home_dir", "./");
      File home_path = new File(home_dir).getCanonicalFile();

      String client_conf =
              config_properties.getProperty("mckoi_client", "./client.conf");
      File client_conf_file = new File(home_path, client_conf);

      // Fetch the location of the 'network.conf' file, either by reading
      // it from the 'mckoi_network' property or dereferencing it from the
      // 'mckoi_netconf_info' location.
      String network_config_val =
                              config_properties.getProperty("mckoi_network");
      String netconf_info_val =
                         config_properties.getProperty("mckoi_netconf_info");
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
      URL network_conf_url =
                        AppServiceNode.parseToURL(home_path, network_conf);

      // The inet address of the private address of this node,
      String private_ip =
              config_properties.getProperty("private_ip", null);
      // The inet address of the public address of this node,
      String public_ip =
              config_properties.getProperty("public_ip", null);

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

      // Make sure the private and public ip addresses given are local
      // network interfaces.
      if (!AppServiceNode.isLocalAddress(private_ip_inet)) {
        System.out.println("ERROR: private_ip is not a local interface.");
        return;
      }
      if (!AppServiceNode.isLocalAddress(public_ip_inet)) {
        System.out.println("ERROR: public_ip is not a local interface.");
        return;
      }

      // Guess unique host name,
      System.out.println("Guessing the host name for this server...");

      // Qualify from the public then the private IP interface if the public
      // host name does not qualify,
      String public_host_name = discoverHostName(public_ip_inet);
      if (public_host_name == null || public_host_name.equals("")) {
        public_host_name = discoverHostName(private_ip_inet);
      }

      // If no host name discovered, we create a random unique host name
      if (public_host_name == null || public_host_name.equals("")) {
        System.out.println("No host name for this server was found.");
        System.out.println("Creating a random unique host name.");
        long sid = (long) (Math.random() * 100000000.0d);
        public_host_name = "host" + sid;
      }
      System.out.println();

      // Print the properties,
      System.out.println("Configuration:");
      System.out.println("  home_dir = " + home_path);
      System.out.println("  mckoi_client = " + client_conf_file);
      System.out.println("  mckoi_network = " + network_conf_url);
      System.out.println();
      System.out.println("  host_name = " + public_host_name);
      System.out.println("  private_ip = " + private_ip_inet);
      System.out.println("  public_ip = " + public_ip_inet);
      System.out.println();

      // Make the MckoiDDBClient,
      MckoiDDBClient client = MckoiDDBClientUtils.connectTCP(client_conf_file);
      // The network resource,
      NetworkConfigResource network_resource =
                          NetworkConfigResource.getNetConfig(network_conf_url);
      // Get the network profile and set the network conf for it,
      NetworkProfile network_profile = client.getNetworkProfile(null);
      network_profile.setNetworkConfiguration(network_resource);

      // Create the app core admin object,
      AppCoreAdmin app_core = new AppCoreAdmin(client, network_profile);

      StyledPrintWriter std_out = new IOWrapStyledPrintWriter(System.out);

      // Register the server,
      app_core.registerServer(std_out,
                              public_host_name,
                              public_ip,
                              private_ip);
      System.out.println("Registered host: " + public_host_name);

      // Start the roles,
      app_core.startRole(std_out, "http", public_ip);
      System.out.println("Registered HTTP service.");
      app_core.startRole(std_out, "https", public_ip);
      System.out.println("Registered HTTPS service.");
      app_core.startRole(std_out, "process", public_ip);
      System.out.println("Registered PROCESS service.");

      System.out.println();
      System.out.println("Server Registration Completed.");

    }
    catch (IOException e) {
      System.out.println("Sorry, an error occurred: " + e.getMessage());
      e.printStackTrace(System.err);
    }

  }


  /**
   * Best guess full qualified host name given an IP address.
   */
  private static String discoverHostName(InetAddress ip_inet)
                                                           throws IOException {

    String host_name = ip_inet.getCanonicalHostName();
    // Did we find a host name?
    if (host_name.equals(ip_inet.getHostAddress())) {
      return null;
    }
    return host_name;

  }

}
