/**
 * com.mckoi.process.impl.TestClientFixture  Mar 23, 2012
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

import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.network.MckoiDDBClient;
import com.mckoi.network.MckoiDDBClientUtils;
import com.mckoi.network.NetworkConfigResource;
import com.mckoi.webplatform.buildtools.SystemBuildStatics;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * A test client.
 *
 * @author Tobias Downer
 */

public class TestClientFixture {

  private Properties client_conf_p;
  private NetworkConfigResource network_resource;
  private ExecutorService shared_thread_pool;

  private NetworkInterface network_interface;


  /**
   * Initialize the JVM and object for this test fixture.
   */
  public void init() {

    final String NETWORK_CONF_LOC = "./network.conf";
    final String CLIENT_CONF_LOC = "./client.conf";


    try {

      // Set/update the URL protocols system property,
      String protocols = System.getProperty("java.protocol.handler.pkgs");
      if (protocols == null) {
        System.setProperty("java.protocol.handler.pkgs",
                                           "com.mckoi.webplatform.protocols");
      }
      else if (!protocols.contains("com.mckoi.webplatform.protocols")){
        System.setProperty("java.protocol.handler.pkgs",
                           protocols + "|" + "com.mckoi.webplatform.protocols");
      }

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

      // Client.conf properties,
      final String network_conf_location = NETWORK_CONF_LOC.toString();
      final String client_config_file = CLIENT_CONF_LOC.toString();

      // PENDING: Give option to load from URL?
      client_conf_p = new Properties();
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

      network_interface = net_if;

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

      // Load the network resource,

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
      shared_thread_pool = Executors.newFixedThreadPool(48);

      
    }
    catch (Throwable e) {
      e.printStackTrace(System.err);
      System.exit(-1);
    }

  }


  /**
   * Creates a ProcessClientService.
   */
  public ProcessClientService createProcessClientService() throws IOException{

    if (client_conf_p == null ||
        network_resource == null ||
        shared_thread_pool == null) {
      throw new NullPointerException();
    }

    // Get the MckoiDDB client connection,
    final MckoiDDBClient client =
                                 MckoiDDBClientUtils.connectTCP(client_conf_p);

    // Create the global sessions cache (1 second age),
    DBSessionCache sessions_cache =
                            new DBSessionCache(client, network_resource, 1000);
    // Create the process client service,
    ProcessClientService process_client_service = new ProcessClientService(
                          sessions_cache, shared_thread_pool, network_interface);
    
    // Opens the client service,
    process_client_service.open();

    return process_client_service;

  }




}
