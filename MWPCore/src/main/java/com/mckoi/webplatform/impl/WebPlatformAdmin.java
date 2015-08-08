/**
 * com.mckoi.webplatform.WebPlatformAdmin  May 14, 2010
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

import com.mckoi.appcore.AccountLoggerSchema;
import com.mckoi.appcore.AppCoreAdmin;
import com.mckoi.appcore.SystemStatics;
import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.data.PropertySet;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.network.*;
import com.mckoi.odb.*;
import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONStringer;

/**
 * This class performs various administration functions on a platform
 * installation, including initialization, account management, etc.
 *
 * @author Tobias Downer
 * @deprecated use MWPMain.AppCoreAdmin instead
 */

public class WebPlatformAdmin {

  private static final SecureRandom RND = SecureRandomUtil.RND;

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  /**
   * The network connection.
   */
  private final MckoiDDBClient client;
  
  /**
   * The network resources config object.
   */
  private final NetworkConfigResource network_resource;

  /**
   * Constructor.
   */
  WebPlatformAdmin(DBSessionCache sessions_cache,
                   MckoiDDBClient client,
                   NetworkConfigResource network_resource) {
    this.sessions_cache = sessions_cache;
    this.client = client;
    this.network_resource = network_resource;
  }

  /**
   * Given a list of strings, parses to a list of ServiceAddress.
   */
  static ServiceAddress[] parseMachinesList(String[] machines)
                                                          throws IOException {
    int sz = machines.length;
    ServiceAddress[] list = new ServiceAddress[sz];
    for (int i = 0; i < sz; ++i) {
      list[i] = ServiceAddress.parseString(machines[i]);
    }
    return list;
  }

  /**
   * Returns true if all the machines are in the network.
   */
  static boolean allMachinesInNetwork(
                           NetworkProfile network, ServiceAddress[] machines) {
    for (ServiceAddress machine : machines) {
      if (!network.isMachineInNetwork(machine)) {
        return false;
      }
    }
    return true;
  }



  /**
   * Add a path profile to the system instance.
   */
  static void addPathProfile(ODBTransaction t,
                      String path_name, String consensus_fun,
                      ServiceAddress root_leader,
                      ServiceAddress[] root_servers) {

    // Make a Path instance,,
    ODBObject path = createPathInstance(t, path_name,
                                     consensus_fun, root_leader, root_servers);

    // Get the paths index and add it to the list,
    ODBObject paths_index = t.getNamedItem("paths");
    ODBList paths_list = paths_index.getList("pathIdx");
    paths_list.add(path);

  }

  /**
   * @deprecated
   */
  static void addMachineProfile(PropertySet machines_pset,
                 String machineID, String privateIP, ArrayList<String> roles) {
    throw new RuntimeException("DEPRECATED");
  }



  static ODBObject createPathInstance(
          ODBTransaction t,
          String path_name, String consensus_fun,
          ServiceAddress root_leader,
          ServiceAddress[] root_servers) {

    String json_root_list;
    try {
      JSONStringer j = new JSONStringer();
      j.array();
      // Add the root leader address.
      j.value(root_leader.formatString());
      // Add the roles to the JSON array,
      for (ServiceAddress server : root_servers) {
        j.value(server.formatString());
      }
      j.endArray();
      json_root_list = j.toString();
    }
    catch (JSONException e) {
      throw new RuntimeException(e);
    }

    // Make a Path instance,,
    ODBObject path =
          t.constructObject(t.findClass("Path"),
                            path_name, consensus_fun, json_root_list);

    // Return the path object,
    return path;

  }



  static ODBObject createPathInstance(
                ODBTransaction t, PathInfo path_info) {

    String consensus_fun = path_info.getConsensusFunction();
    ServiceAddress root_leader = path_info.getRootLeader();
    ServiceAddress[] root_servers = path_info.getRootServers();

    return createPathInstance(t, path_info.getPathName(),
                              consensus_fun, root_leader, root_servers);

  }



  static ODBObject createMachineInstance(
                ODBTransaction t,
                String machineID, String privateIP, ArrayList<String> roles) {

    String json_role_list;
    try {
      JSONStringer j = new JSONStringer();
      j.array();
//      // Add the private ip address.
//      j.value(privateIP);
      // Add the roles to the JSON array,
      for (String role : roles) {
        j.value(role);
      }
      j.endArray();
      json_role_list = j.toString();
    }
    catch (JSONException e) {
      throw new RuntimeException(e);
    }

    // Make a Machine instance and add to the list,
    ODBObject machine =
            t.constructObject(t.findClass("Machine"),
                              machineID, privateIP, json_role_list);

    // Return the machine object,
    return machine;
  }

  /**
   * Populate the system tables with the network schema.
   */
  private void populateSystemTables(PrintStream out, NetworkProfile network)
                                   throws IOException, NetworkAdminException {

    // The system page session,
    ODBSession session = new ODBSession(client, SystemStatics.SYSTEM_PATH);

    // The list of all paths,
    String[] path_list = network.getAllPathNames();
    // The list of all machine nodes,
    MachineProfile[] machine_node_list = network.getAllMachineProfiles();

    ODBTransaction t = session.createTransaction();

    // 'MachineIndex'
    ODBObject machines_index = t.getNamedItem("machines");
    ODBList ip_list = machines_index.getList("ipaddressIdx");
    ODBList id_list = machines_index.getList("idIdx");
    ODBList managers_list = machines_index.getList("managers");
    ODBList roots_list = machines_index.getList("roots");
    ODBList blocks_list = machines_index.getList("blocks");

    // 'PathIndex'
    ODBObject paths_index = t.getNamedItem("paths");
    ODBList paths_list = paths_index.getList("pathIdx");

    try {
      // Copy the machine nodes schema info,
      int num = 1;
      for (MachineProfile m : machine_node_list) {
        ServiceAddress service_addr = m.getServiceAddress();
        String privateIP = service_addr.formatString();

        ArrayList<String> role_list = new ArrayList<>();
        if (m.isManager()) {
          role_list.add("manager");
        }
        if (m.isRoot()) {
          role_list.add("root");
        }
        if (m.isBlock()) {
          role_list.add("block");
        }

        // Make an anonymous random name for this introduced machine,
        String uid = Integer.toHexString(RND.nextInt());
        int dif = 8 - uid.length();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < dif; ++i) {
          buf.append('0');
        }
        buf.append(uid);
        String machineID = "suid-" + buf.toString();

        // If the instance is already defined in the list then delete it,
        if (ip_list.contains(privateIP)) {
          ODBObject existing_instance = ip_list.getObject(privateIP);
          String id_val = existing_instance.getString("id");

          // Copy the existing machine id
          machineID = id_val;

          // Remove from the index
          id_list.remove(id_val);
          ip_list.remove(existing_instance.getString("ipaddress"));
          managers_list.remove(id_val);
          roots_list.remove(id_val);
          blocks_list.remove(id_val);
        }

        // Create the machine instance,
        ODBObject machine_instance =
                       createMachineInstance(t, machineID, privateIP, role_list);

        // Add to the index,
        ip_list.add(machine_instance);
        id_list.add(machine_instance);

        // If manager, root or block role, then add the instance to the
        // appropriate list.
        if (m.isManager()) {
          managers_list.add(machine_instance);
        }
        if (m.isRoot()) {
          roots_list.add(machine_instance);
        }
        if (m.isBlock()) {
          blocks_list.add(machine_instance);
        }

        ++num;
      }

      // Copy the path schema info,
      for (String path_name : path_list) {
        PathInfo path_info = network.getPathInfoForPath(path_name);

        // Remove any entries with the same name if they are present,
        paths_list.remove(path_info.getPathName());

        ODBObject path_instance = createPathInstance(t, path_info);
        paths_list.add(path_instance);
      }

    }
    catch (ConstraintViolationException e) {

      // This shouldn't happen because we check for entries before adding
      // them.
      throw new RuntimeException(e);

    }

    // Commit,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      e.printStackTrace(out);
    }

  }

  /**
   * Initializes the platform.
   */
  public void initialize(PrintStream out,
                         String system_path_machine_node,
                         String[] system_machine_cluster,
                         String log_path_machine_node,
                         String[] log_machine_cluster)
                                   throws IOException, NetworkAdminException {

    // Create the path,
    NetworkProfile network = client.getNetworkProfile("");
    network.setNetworkConfiguration(network_resource);
    network.refresh();

    ServiceAddress system_path_addr =
                         ServiceAddress.parseString(system_path_machine_node);
    ServiceAddress log_path_addr =
                         ServiceAddress.parseString(log_path_machine_node);

    ServiceAddress[] system_server_cluster =
                                    parseMachinesList(system_machine_cluster);
    ServiceAddress[] log_server_cluster =
                                       parseMachinesList(log_machine_cluster);

    if (!network.isMachineInNetwork(system_path_addr) ||
        !network.isMachineInNetwork(log_path_addr) ||
        !allMachinesInNetwork(network, system_server_cluster) ||
        !allMachinesInNetwork(network, log_server_cluster)) {
      out.println("FAILED: Machine node not found in network");
      return;
    }
    MachineProfile sp_machine = network.getMachineProfile(system_path_addr);
    MachineProfile lp_machine = network.getMachineProfile(log_path_addr);
    if (!sp_machine.isRoot()) {
      out.println("FAILED: System path machine node is not a root server");
      return;
    }
    if (!lp_machine.isRoot()) {
      out.println("FAILED: Log path machine node is not a root server");
      return;
    }

    // Create the system and log paths,
    // Check the paths don't already exist,
    if (network.getPathInfoForPath(SystemStatics.SYSTEM_PATH) != null) {
      out.println("WARNING: System path (" +
                           SystemStatics.SYSTEM_PATH + ") already exists");
    }
    else {
      network.addPathToNetwork(
              SystemStatics.SYSTEM_PATH, "com.mckoi.odb.ObjectDatabase",
              system_path_addr, system_server_cluster);
    }
    if (network.getPathInfoForPath(SystemStatics.ANALYTICS_PATH) != null) {
      out.println("WARNING: Log path (" +
                           SystemStatics.ANALYTICS_PATH + ") already exists");
    }
    else {
      network.addPathToNetwork(
              SystemStatics.ANALYTICS_PATH, "com.mckoi.odb.ObjectDatabase",
              log_path_addr, log_server_cluster);
    }

    // Done with the network profile side of things,

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = new ODBSession(client, SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    ODBObject version_ob = t.getNamedItem("version");
    if (version_ob != null) {
      // The system class is initialized already,
      out.println("FAILED: Already initialized.");
      return;
    }

    // Define the system classes,
    {
      ODBClassCreator class_creator = t.getClassCreator();
      // The Version class,
      ODBClassDefinition version_class = class_creator.createClass("Version");
      version_class.defineString("value");

      // The VHost class
      ODBClassDefinition vhost_class = class_creator.createClass("VHost");
      vhost_class.defineString("domain");
      vhost_class.defineMember("account", "Account");

      // The Account class
      ODBClassDefinition account_class =
                                        class_creator.createClass("Account");
      account_class.defineString("account");
      account_class.defineList("vhost_list", "VHost", false);
      account_class.defineList("resource_list", "AccountResource", false);
      account_class.defineList("server_list", "AccountServer", false);
      account_class.defineString("value", true);

      // The Account resource class
      ODBClassDefinition account_resource_class =
                                class_creator.createClass("AccountResource");
      account_resource_class.defineString("path");
      account_resource_class.defineString("resource_name");
      account_resource_class.defineString("type");
      account_resource_class.defineMember("account", "Account");

      // The Account server class
      ODBClassDefinition account_server_class =
                                  class_creator.createClass("AccountServer");
      account_server_class.defineString("machine");
      account_server_class.defineMember("account", "Account");


      // The Machine class
      ODBClassDefinition machine_class =
                                        class_creator.createClass("Machine");
      machine_class.defineString("id");
      machine_class.defineString("ipaddress");
      machine_class.defineString("roles");

      // The Path class
      ODBClassDefinition path_class = class_creator.createClass("Path");
      path_class.defineString("path");
      path_class.defineString("type");
      path_class.defineString("location");





      // The Account index class
      ODBClassDefinition account_index_class =
                                   class_creator.createClass("AccountIndex");
      account_index_class.defineList("accountIdx", "Account",
                      ODBOrderSpecification.lexicographic("account"), false);

      // The VHost index class
      ODBClassDefinition vhost_index_class =
                                     class_creator.createClass("VHostIndex");
      vhost_index_class.defineList("domainIdx", "VHost",
                      ODBOrderSpecification.lexicographic("domain"), false);

      // The account resource index class
      ODBClassDefinition account_resource_index_class =
                           class_creator.createClass("AccountResourceIndex");
      account_resource_index_class.defineList("pathIdx", "AccountResource",
                      ODBOrderSpecification.lexicographic("path"), true);

      // The Machine index class,
      ODBClassDefinition machine_index_class =
                                   class_creator.createClass("MachineIndex");
      machine_index_class.defineList("idIdx", "Machine",
                      ODBOrderSpecification.lexicographic("id"), false);
      machine_index_class.defineList("ipaddressIdx", "Machine",
                      ODBOrderSpecification.lexicographic("ipaddress"), false);
      machine_index_class.defineList("managers", "Machine",
                      ODBOrderSpecification.lexicographic("id"), false);
      machine_index_class.defineList("roots", "Machine",
                      ODBOrderSpecification.lexicographic("id"), false);
      machine_index_class.defineList("blocks", "Machine",
                      ODBOrderSpecification.lexicographic("id"), false);

      // The Path index class,
      ODBClassDefinition path_index_class =
                                   class_creator.createClass("PathIndex");
      path_index_class.defineList("pathIdx", "Path",
                      ODBOrderSpecification.lexicographic("path"), false);


      // Validate and complete the class assignments,
      try {
        class_creator.validateAndComplete();
      }
      catch (ClassValidationException e) {
        out.println("FAILED: Class Validation Error");
        e.printStackTrace(out);
        return;
      }

    }

    {
      // Create a version object,
      ODBClass version_class = t.findClass("Version");
      version_ob = t.constructObject(version_class, "Mckoi Platform 1.0");
      // Make it a named object,
      t.addNamedItem("version", version_ob);

      // Add the object indexes,
      ODBObject account_idx = t.constructObject(
              t.findClass("AccountIndex"), new Object[] { null });
      ODBObject vhost_idx = t.constructObject(
              t.findClass("VHostIndex"), new Object[] { null });
      ODBObject account_resource_idx = t.constructObject(
              t.findClass("AccountResourceIndex"), new Object[] { null });
      ODBObject machine_idx = t.constructObject(
              t.findClass("MachineIndex"), new Object[] { null, null, null, null, null });
      ODBObject path_idx = t.constructObject(
              t.findClass("PathIndex"), new Object[] { null });

      t.addNamedItem("accounts", account_idx);
      t.addNamedItem("vhosts", vhost_idx);
      t.addNamedItem("accountresources", account_resource_idx);
      t.addNamedItem("machines", machine_idx);
      t.addNamedItem("paths", path_idx);

    }







    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      e.printStackTrace(out);
      return;
    }

    // Populate the system tables with information from the network,
    populateSystemTables(out, network);

    out.println("SUCCESS");
  }

  /**
   * Initializes a single process path (sysprocess00) with the given path info.
   * 
   * @param out
   * @param process_path_leader
   * @param process_path_servers
   * @throws java.io.IOException
   * @throws com.mckoi.network.NetworkAdminException
   */
  public void initProcess(PrintStream out,
                String process_path_leader, String[] process_path_servers)
                                    throws IOException, NetworkAdminException {

    final String process_path_name = "sysprocess00";

    // Create the path,
    NetworkProfile network = client.getNetworkProfile("");
    network.setNetworkConfiguration(network_resource);
    network.refresh();

    ServiceAddress process_path_addr =
                              ServiceAddress.parseString(process_path_leader);

    ServiceAddress[] process_path_cluster =
                              parseMachinesList(process_path_servers);

    if (!network.isMachineInNetwork(process_path_addr) ||
        !allMachinesInNetwork(network, process_path_cluster)) {
      out.println("FAILED: Machine node not found in network");
      return;
    }
    MachineProfile pp_machine = network.getMachineProfile(process_path_addr);
    if (!pp_machine.isRoot()) {
      out.println("FAILED: Process path machine node is not a root server");
      return;
    }

    // Check the paths don't already exist,
    if (network.getPathInfoForPath(process_path_name) != null) {
      out.println("WARNING: Process path (" +
                                    process_path_name + ") already exists");
    }
    else {
      network.addPathToNetwork(
              process_path_name, "com.mckoi.odb.ObjectDatabase",
              process_path_addr, process_path_cluster);
    }

    // Done with the network profile side of things,

    // Create a session for the process path instance,
    ODBSession session = new ODBSession(client, process_path_name);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // Define the system classes,
    if (t.findClass("Command") == null) {

      ODBClassCreator class_creator = t.getClassCreator();

      // The Command class,
      ODBClassDefinition command_class = class_creator.createClass("Command");
      command_class.defineString("instance");
      command_class.defineString("value", true);

      // The Process class,
      ODBClassDefinition process_class = class_creator.createClass("Process");
      process_class.defineString("id");
      process_class.defineString("machine", true);
      process_class.defineString("value", true);
      process_class.defineData("state");

      // The Root class,
      ODBClassDefinition root_class = class_creator.createClass("Root");
      root_class.defineString("version", true);
      root_class.defineList("all_processIdx", "Process",
                      ODBOrderSpecification.lexicographic("id"), false);
      root_class.defineList("available_processIdx", "Process",
                      ODBOrderSpecification.lexicographic("id"), false);
      root_class.defineList("commandIdx", "Command",
                      ODBOrderSpecification.lexicographic("instance"), false);

      

      // Validate and complete the class assignments,
      try {
        class_creator.validateAndComplete();
      }
      catch (ClassValidationException e) {
        out.println("FAILED: Class Validation Error");
        e.printStackTrace(out);
        return;
      }

    }

    // Set the root item,
    ODBObject root_ob = t.getNamedItem("root");
    if (root_ob == null) {
      root_ob = t.constructObject(
                   t.findClass("Root"), "Sys Platform 1.0", null, null, null);
      t.addNamedItem("root", root_ob);
    }

    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      e.printStackTrace(out);
    }

    // Populate the system tables with information from the network,
    populateSystemTables(out, network);

  }

  /**
   * Public method for populating the system tables with the current network
   * information.
   * 
   * @param out
   * @throws java.io.IOException
   * @throws com.mckoi.network.NetworkAdminException
   */
  public void populateSystemTables(PrintStream out)
                                    throws IOException, NetworkAdminException {
    // NetworkProfile info,
    NetworkProfile network = client.getNetworkProfile("");
    network.setNetworkConfiguration(network_resource);
    network.refresh();

    populateSystemTables(out, network);
  }
  

  /**
   * Adds a user account to a user resource path. The given transaction must
   * be to the account's main resource path.
   * 
   * @param t
   * @param username
   * @param email
   * @param password
   * @param priv
   */
  public static void addUserToAccount(ODBTransaction t,
                                      String username, String email,
                                      String password, String priv) {

    // Create the map for account info,
    Map<String, String> account_info = new HashMap<>();
    account_info.put("auth", "local");
    account_info.put("pass", password);
    account_info.put("priv", priv);

    // Defer to the session authenticator account create method,
    AppCoreAdmin.createUserProfile(t, username, email, account_info);

  }


  /**
   * Adds a new account to the platform.
   * 
   * @param out
   * @param userfs_path_root_leader
   * @param userfs_path_root_cluster
   * @param username
   * @param password
   * @throws java.io.IOException
   * @throws com.mckoi.network.NetworkAdminException
   */
  public void addAccount(PrintStream out,
                         String userfs_path_root_leader,
                         String[] userfs_path_root_cluster,
                         final String username, String password)
                                   throws IOException, NetworkAdminException {

    // Create an Object Database path for the account.

    NetworkProfile network = client.getNetworkProfile("");
    network.setNetworkConfiguration(network_resource);
    network.refresh();

    ServiceAddress userfs_path_addr =
                         ServiceAddress.parseString(userfs_path_root_leader);
    ServiceAddress[] userfs_path_cluster =
                                 parseMachinesList(userfs_path_root_cluster);

    if (!network.isMachineInNetwork(userfs_path_addr) ||
        !allMachinesInNetwork(network, userfs_path_cluster)) {
      out.println("FAILED: Machine node not found in network");
      return;
    }
    MachineProfile ufs_machine = network.getMachineProfile(userfs_path_addr);
    if (!ufs_machine.isRoot()) {
      out.println("FAILED: User file system machine node is not a root server");
      return;
    }

    final String user_path_name = "ufs" + username;

    // Check the path doesn't already exist,
    if (network.getPathInfoForPath(user_path_name) != null) {
      out.println("FAILED: User filesystem path (" + user_path_name + ") already exists");
      return;
    }

    // Done with the network profile side of things,




    // Set up the file system repository and initialize it

    if (true) {
      // Create a session for the 'sysplatform' path instance,
      ODBSession session = new ODBSession(client, SystemStatics.SYSTEM_PATH);

      // Open a transaction
      ODBTransaction t = session.createTransaction();

      // Make an account object,
      ODBClass account_class = t.findClass("Account");
      // (account, vhost_list, resource_list, server_list, value*)
      ODBObject account = t.constructObject(account_class,
              username, null, null, null, "");

      // The account resources list,
      ODBList account_resource_list = account.getList("resource_list");

      // Add to the account index,
      ODBObject account_index = t.getNamedItem("accounts");
      ODBList account_idx = account_index.getList("accountIdx");
      account_idx.add(account);

      // Make an account resource object,
      ODBClass account_resource_class = t.findClass("AccountResource");
      // (path, resource_name, type, account)
      ODBObject account_resource = t.constructObject(account_resource_class,
              user_path_name,
              "$AccountFileRepository", "com.mckoi.odb.ObjectDatabase",
              account);

      // Add to the account resource index,
      ODBObject accountres_index = t.getNamedItem("accountresources");
      ODBList accountres_idx = accountres_index.getList("pathIdx");
      accountres_idx.add(account_resource);

      // Add the account resource to the account resource_list
      account_resource_list.add(account_resource);

      // Update the system tables with the new path,
      addPathProfile(t,
                     user_path_name, "com.mckoi.odb.ObjectDatabase",
                     userfs_path_addr, userfs_path_cluster);

      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        e.printStackTrace(out);
        return;
      }

    }

    // Create the user filesystem paths,
    network.addPathToNetwork(
            user_path_name, "com.mckoi.odb.ObjectDatabase",
            userfs_path_addr, userfs_path_cluster);

    // Set up and initialize the file system on the created path,

    // Setup the ufs path,
    if (true) {
      // Define the schema,
      try {

        // Create a session for the analytics path instance,
        ODBSession session = new ODBSession(client, user_path_name);
        // Open a transaction
        ODBTransaction t = session.createTransaction();

        // Define the file system on the account path,
        FileRepositoryImpl.defineSchema(t);
        // Define the log system on the account path,
        AccountLoggerSchema.defineSchema(t);

        // Create some other classes,
        ODBClassCreator creator = t.getClassCreator();
        ODBClassDefinition user_def = creator.createClass("A.User");
        user_def.defineString("user")
                .defineString("email")
                .defineString("info", true);

        ODBClassDefinition user_index_def = creator.createClass("A.UserIndex");
        user_index_def.defineList("userIdx", "A.User",
                           ODBOrderSpecification.lexicographic("user"), false);
        user_index_def.defineList("emailIdx", "A.User",
                          ODBOrderSpecification.lexicographic("email"), true);

        ODBClassDefinition session_def = creator.createClass("A.Session");
        session_def.defineString("domaincookie")
                   .defineString("user")
                   .defineString("timestamp");

        ODBClassDefinition session_index_def =
                                         creator.createClass("A.SessionIndex");
        session_index_def.defineList("domaincookieIdx", "A.Session",
                   ODBOrderSpecification.lexicographic("domaincookie"), false);
        session_index_def.defineList("userIdx", "A.Session",
                           ODBOrderSpecification.lexicographic("user"), true);

        // Complete the class definitions,
        creator.validateAndComplete();

        // Create the index,
        ODBObject users_index = t.constructObject(
                                    t.findClass("A.UserIndex"), null, null);
        t.addNamedItem("users", users_index);
        ODBObject sessions_index = t.constructObject(
                                  t.findClass("A.SessionIndex"), null, null);
        t.addNamedItem("sessions", sessions_index);

        // Set up the log system
        AccountLoggerSchema.setup(t);

        // Set up the file system,
        FileRepositoryImpl filesys =
                            new FileRepositoryImpl(username, t, "accountfs");
        filesys.setup(user_path_name, "Account File System");

        // Add a super user record to this account,
        addUserToAccount(t, username, "", password, "SU");

        // Make a system directory in the file system,
        filesys.makeDirectory("/system/");
        filesys.makeDirectory("/log/");
        // Add a plain text file with version details and with the account
        // name.
        filesys.createFile("/system/account", "text/plain",
                           System.currentTimeMillis());
        DataFile dfile = filesys.getDataFile("/system/account");
        PrintWriter pout = new PrintWriter(
                new OutputStreamWriter(DataFileUtils.asOutputStream(dfile)));
        pout.print("Mckoi Web Platform File System version 1.0\n");
        pout.print("Created: " + new Date().toString() + "\n");
        pout.print("User: " + username);
        pout.flush();

        // Default Account Locale information,
        filesys.createFile("/system/locale", "text/plain",
                           System.currentTimeMillis());
        dfile = filesys.getDataFile("/system/locale");
        pout = new PrintWriter(
                new OutputStreamWriter(DataFileUtils.asOutputStream(dfile)));
        pout.print("enUS\n");
        pout.print("PST\n");
        pout.flush();

        // And commit,
        t.commit();

      }
      catch (ClassValidationException e) {
        out.println("FAILED: Class validation exception");
        e.printStackTrace(out);
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        e.printStackTrace(out);
      }
    }

    // Done,

  }

  /**
   * Makes the given account name a super user, allowing it read/write access
   * to the system platform.
   * 
   * @param out
   * @param account_name
   */
  public void makeSuperUser(PrintStream out, String account_name) {

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = new ODBSession(client, SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The account index,
    ODBObject accounts_named = t.getNamedItem("accounts");
    // The sorted list of accounts,
    ODBList accounts_idx = accounts_named.getList("accountIdx");

    // Get the account,
    ODBObject account_ob = accounts_idx.getObject(account_name);
    if (account_ob == null) {
      out.println("FAILED: Account " + account_name + " not found.");
      return;
    }

    try {

      JSONArray rw_json_ob = new JSONArray();
      rw_json_ob.put(SystemStatics.SYSTEM_PATH);
      rw_json_ob.put(SystemStatics.ANALYTICS_PATH);
      rw_json_ob.put("sysprocess00");
      rw_json_ob.put("sysprocess01");
      rw_json_ob.put("sysprocess02");
      rw_json_ob.put("sysprocess03");
      rw_json_ob.put("sysprocess04");
      rw_json_ob.put("sysprocess05");
      rw_json_ob.put("sysprocess06");
      rw_json_ob.put("sysprocess07");
      rw_json_ob.put("sysprocess08");
      rw_json_ob.put("sysprocess09");
      rw_json_ob.put("sysprocess0a");
      rw_json_ob.put("sysprocess0b");
      rw_json_ob.put("sysprocess0c");
      rw_json_ob.put("sysprocess0d");
      rw_json_ob.put("sysprocess0e");
      rw_json_ob.put("sysprocess0f");

      JSONStringer json = new JSONStringer();
      json.object();
      json.key("readwrite_odb_paths");
      json.value(rw_json_ob);
      json.key("globalpriv");
      json.value("superuser");
      json.endObject();

      account_ob.setString("value", json.toString());

      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        e.printStackTrace(out);
      }

    }
    catch (JSONException e) {
      out.println("FAILED: JSON Exception");
      e.printStackTrace(out);
    }
    

  }

  /**
   * Maps a vhost (eg. mckoi.com) to an account.
   * 
   * @param out
   * @param domain_name
   * @param account_name
   */
  public void addVHost(PrintStream out,
                       String domain_name, String account_name) {

    // Sanity checks on the input,
    if (domain_name.length() < 3) {
      out.println("FAILED: Domain name '" + domain_name + "' string too small.");
    }
    else if (domain_name.length() > 256) {
      out.println("FAILED: Domain name '" + domain_name + "' too large.");
    }

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = new ODBSession(client, SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The account index,
    ODBObject accounts_named = t.getNamedItem("accounts");
    // The sorted list of accounts,
    ODBList accounts_idx = accounts_named.getList("accountIdx");

    // Get the account,
    ODBObject account_ob = accounts_idx.getObject(account_name);
    if (account_ob == null) {
      out.println("FAILED: Account " + account_name + " not found.");
      return;
    }

    // The VHost index,
    ODBList domain_idx = t.getNamedItem("vhosts").getList("domainIdx");

    // Check the domain isn't already defined,
    if (domain_idx.contains(domain_name)) {
      out.println("FAILED: VHost " + domain_name + " already defined.");
      return;
    }

    // Create a VHost object for the account,
    ODBClass vhost_class = t.findClass("VHost");
    // (domain, account)
    ODBObject vhost_ob =
                     t.constructObject(vhost_class, domain_name, account_ob);

    // Update the vhost index,
    domain_idx.add(vhost_ob);
    // Update the account record,
    account_ob.getList("vhost_list").add(vhost_ob);

    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      e.printStackTrace(out);
    }

    // Done,

  }

}
