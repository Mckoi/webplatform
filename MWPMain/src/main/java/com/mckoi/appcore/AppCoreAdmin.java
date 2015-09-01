/**
 * com.mckoi.appcore.AppCoreAdmin  Mar 9, 2012
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

import com.mckoi.appcore.UserApplicationsSchema.ApplicationsDocument;
import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.network.*;
import com.mckoi.odb.*;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.odb.util.*;
import com.mckoi.util.CommandLine;
import com.mckoi.util.IOWrapStyledPrintWriter;
import com.mckoi.util.StyledPrintWriter;
import java.io.*;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.*;
import org.w3c.dom.Node;

/**
 * A simple command line interface for initializing and managing the data
 * structures for the Mckoi application core.
 *
 * @author Tobias Downer
 */

public class AppCoreAdmin {

  private final static Random RND;
  private final MckoiDDBAccess client;
  private final NetworkAccess network_access;
  private final HashMap<String, ODBSession> cached_sessions;

  static {
    try {
      RND = SecureRandom.getInstance("SHA1PRNG");
    }
    catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException("SHA1PRNG unavailable", ex);
    }
  }

  /**
   * Constructor.
   */
  public AppCoreAdmin(MckoiDDBAccess client,
                      NetworkAccess network_access) {
    this.client = client;
    this.network_access = network_access;
    this.cached_sessions = new HashMap();
    
  }

  /**
   * Returns the NetworkAccess interface for accessing the MckoiDDB network.
   */
  protected NetworkAccess getNetworkProfile() throws IOException {
    return network_access;
//    // Initialize the network profile,
//    NetworkProfile network = client.getNetworkProfile("");
//    network.setNetworkConfiguration(getNetworkConfig());
//    network.refresh();
//    return network;
  }

  /**
   * Gets an ODBSession for the given path. Caches sessions for efficiency.
   */
  private ODBSession getSession(String path_name) {
    synchronized (cached_sessions) {
      ODBSession session = cached_sessions.get(path_name);
      if (session == null) {
        session = new ODBSession(client, path_name);
        cached_sessions.put(path_name, session);
      }
      return session;
    }
  }

  /**
   * Converts a numerical string to a string padded with zeroes so that it is
   * at least the given size.
   */
  private final static String ZERO_PAD = "00000000000000000000000000000000";

  private static String zeroPad(String v, int desired_size) {
    int value_sz = v.length();
    int pad_count = desired_size - value_sz;
    if (pad_count <= 0) {
      return v;
    }
    StringBuilder b = new StringBuilder();
    b.append(ZERO_PAD.substring(0, pad_count));
    b.append(v);
    return b.toString();
  }

  /**
   * Converts an int to a string padded with zeroes so that it is at least the
   * given size.
   */
  private static String zeroPad(int value, int desired_size) {
    String v = Integer.toString(value);
    return zeroPad(v, desired_size);
  }

  /**
   * Turns a String->String Map into a JSON string of an object with the same
   * associations as the map.
   */
  private static String mapAsJSONString(Map<String, String> properties) {
    StringWriter sout = new StringWriter();
    JSONWriter jout = new JSONWriter(sout);
    try {
      jout.object();
      for (String key : properties.keySet()) {
        jout.key(key);
        jout.value(properties.get(key));
      }
      jout.endObject();
    }
    catch (JSONException e) {
      throw new RuntimeException(e);
    }
    return sout.toString();
  }

  private static String accountToPathName(String account_name) {
    return "ufs" + account_name;
  }

  /**
   * Returns a FileSystemImpl of the given account's file system.
   * 
   * @param account_name
   * @return 
   */
  private FileSystemImpl getAccountFileSystem(String account_name) {
    // Create a transaction for the user's main path,
    ODBSession session = getSession(accountToPathName(account_name));
    ODBTransaction t = session.createTransaction();
    // Return the file system,
    return new FileSystemImpl(t, "accountfs");
  }

  /**
   * Returns true if all the machines are in the network.
   */
  private boolean allMachinesInNetwork(
                           NetworkAccess network, ServiceAddress[] machines) {
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
  private static void addPathProfile(ODBTransaction t,
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



  private static ODBObject createPathInstance(
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



  private static ODBObject createPathInstance(
                ODBTransaction t, PathInfo path_info) {

    String consensus_fun = path_info.getConsensusFunction();
    ServiceAddress root_leader = path_info.getRootLeader();
    ServiceAddress[] root_servers = path_info.getRootServers();

    return createPathInstance(t, path_info.getPathName(),
                              consensus_fun, root_leader, root_servers);

  }



  private static ODBObject createMachineInstance(
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
   * Checks the vhost string is valid. Returns true if valid, false otherwise.
   * A valid vhost string is a qualified domain name followed by a protocol
   * description. For example; 'www.example.com:http',
   * 'secure.example.com:https', 'example.com:http'.
   */
  public static boolean isValidVHost(String vhost) {
    int delim = vhost.lastIndexOf(":");
    if (delim >= 1) {
      String domain = vhost.substring(0, delim);
      String protocol = vhost.substring(delim + 1);
      if ( protocol.equals("http") ||
           protocol.equals("https") ||
           protocol.equals("*") ) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Creates a user profile on the given transaction resource. This will
   * generate an exception if a profile already exists.
   */
  public static void createUserProfile(ODBTransaction t,
                                String username, String email,
                                Map<String, String> properties) {

//    // Make a shared secret identifier for this account,
//    long account_id = Math.abs(RND.nextLong());
//    properties.put("actid", zeroPad(Long.toString(account_id, 32), 14));

    // Turn the properties map into a JSON string,
    String jinfo = mapAsJSONString(properties);

    // Add the user profile to the account,
    // The user class,
    ODBClass user_class = t.findClass("A.User");

    // The list of users
    ODBObject users = t.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");
    ODBList emails_idx = users.getList("emailIdx");

    // Check the name not already in the users index
    if (users_idx.contains(username)) {
      throw new RuntimeException("Profile already exists");
    }

    // (user, email, info)
    ODBObject user = t.constructObject(user_class,
                                       username, email, jinfo);

    // Add to the indexes,
    users_idx.add(user);
    emails_idx.add(user);

  }

  /**
   * Deletes a user profile on the given transaction resource. Returns true if
   * the user exists and was deleted, false otherwise.
   */
  public static boolean deleteUserProfile(ODBTransaction t,
                                          String username) {

    // The list of users
    ODBObject users = t.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");
    ODBList emails_idx = users.getList("emailIdx");

    ODBObject user_ob = users_idx.getObject(username);

    // Try and delete,
    if (user_ob == null) {
      return false;
    }

    // Remove from the email index,
    users_idx.remove(username);
    emails_idx.remove(user_ob.getReference());

    return true;
  }

  /**
   * Updates a user profile on the given transaction resource. Any properties
   * not in the map will NOT be updated.
   */
  public static boolean updateUserProfile(ODBTransaction t,
                           String username, Map<String, String> properties) {

    // Update the user profile in the account,

    // The list of users
    ODBObject users = t.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");

    // Fetch the user,
    ODBObject user_ob = users_idx.getObject(username);

    // If the user doesn't exist,
    if (user_ob == null) {
      throw new RuntimeException("Profile does not exist");
    }

    // Update the JSON properties string,
    try {
      String json_info_str = user_ob.getString("info");
      JSONObject json_info = new JSONObject(json_info_str);
      for (String key : properties.keySet()) {
        json_info.put(key, properties.get(key));
      }
      // Set the new JSON string,
      user_ob.setString("info", json_info.toString());
    }
    catch (JSONException e) {
      throw new RuntimeException(e);
    }

    // Done,
    return true;

  }

  /**
   * Adds a user account to a user resource path. The given transaction must
   * be to the account's main resource path.
   */
  public static void addUserToAccount(ODBTransaction t,
                                      String username, String email,
                                      String password, String priv)
                                                           throws IOException {

    // The password hash,
    String password_hash = SystemStatics.toPasswordHash(password);

    // Create the map for account info,
    HashMap<String, String> account_info = new HashMap();
    account_info.put("auth", "local");
    account_info.put("pash", password_hash);
    account_info.put("priv", priv);

    // Defer to the session authenticator account create method,
    createUserProfile(t, username, email, account_info);

  }

  /**
   * Removes a user from the account. Returns true if the user exists and was
   * deleted, false otherwise.
   */
  public static boolean removeUserFromAccount(ODBTransaction t,
                                          String username) throws IOException {

    return deleteUserProfile(t, username);

  }

  /**
   * Sets the password of the given user in the account represented by the
   * given ODBTransaction object (the account's main resource path).
   */
  public static boolean setUserPasswordInAccount(ODBTransaction t,
                         String username, String password) throws IOException {

    // The new password hash,
    String password_hash = SystemStatics.toPasswordHash(password);

    // Create the map for account info,
    HashMap<String, String> account_info = new HashMap();
    account_info.put("pash", password_hash);

    // Defer to the session authenticator account update method,
    return updateUserProfile(t, username, account_info);

  }

  /**
   * Cleans all the session state related to the given user in the given
   * account.
   */
  public static boolean cleanUserSessionInAccount(ODBTransaction t,
                                                  String username) {

    boolean removed = false;

    ODBObject sessions = t.getNamedItem("sessions");
    ODBList user_idx = sessions.getList("userIdx");
    ODBList domains_idx = sessions.getList("domaincookieIdx");

    // Get all the ODBObjects related to this user,
    ODBList tail = user_idx.tail(username);

    for (ODBObject user : tail) {
      if (user.getString("user").equals(username)) {
        // Remove from the domain index,
        removed |= domains_idx.remove(user.getString("domaincookie"));
      }
      else {
        break;
      }
    }
    // Remove from the user index,
    removed |= user_idx.removeAll(username);

    // Done,
    return removed;

  }

  
  /**
   * Parses a vhost expression and adds the elements to the list. Returns true
   * on a parse exception.
   */
  private static boolean parseVHost(
                   StyledPrintWriter out, String expr, Set<String> vhost_set) {

    boolean valid = !expr.contains("\\") && !expr.contains("/");

    // Ignore empty vhost expressions,
    if (expr.equals("")) {
    }
    // '*' = 'http' and 'https' allocation,
    else if (valid && expr.endsWith(":*")) {
      String host = expr.substring(0, expr.length() - 2);
      vhost_set.add(host + ":http");
      vhost_set.add(host + ":https");
    }
    // Standard http/https association,
    else if (valid && (expr.endsWith(":http") || expr.endsWith(":https"))) {
      vhost_set.add(expr);
    }
    else if (valid && !expr.contains(":")) {
      vhost_set.add(expr + ":http");
      vhost_set.add(expr + ":https");
    }
    // Format error,
    else {
      out.println("VHost expression is not valid: " + expr, "error");
      out.println("Below are some example valid vhost expressions;");
      out.println("  www.myhost.org:*", "info");
      out.println("  myhost.org:http", "info");
      out.println("  myhost.org:*", "info");
      out.println("  secure.myhost.org:https, secure2.myhost.org:https", "info");
      return true;
    }

    return false;

  }

  /**
   * Parses a vhost expression and returns a list of vhost strings representing
   * an expansion of the vhost definitions.
   */
  public static Set<String> parseVHostList(
                                    StyledPrintWriter out, String vhost_expr) {

    Set<String> vhost_set = new HashSet();
    while (true) {
      int delim = vhost_expr.indexOf(',');
      // End found,
      if (delim == -1) {
        vhost_expr = vhost_expr.trim();
        boolean error = parseVHost(out, vhost_expr, vhost_set);
        if (error) return null;
        break;
      }
      else {
        String expr = vhost_expr.substring(0, delim).trim();
        vhost_expr = vhost_expr.substring(delim + 1);
        boolean error = parseVHost(out, expr, vhost_set);
        if (error) return null;
      }
    }

    // Return the list,
    return vhost_set;

  }
  
  /**
   * Populate the system tables with the network schema.
   */
  public void populateSystemTables(StyledPrintWriter out, NetworkAccess network)
                                   throws IOException, NetworkAdminException {

    // The system page session,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

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

        ArrayList<String> role_list = new ArrayList();
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
      out.printException(e);
    }

  }

  /**
   * Returns true if the role is a valid DDB role.
   * @param role
   * @return 
   */
  private boolean validDDBRole(String role) {
    return role.equals("block") || role.equals("manager") ||
           role.equals("root");
  }

  /**
   * Updates the machine roles of the given machine address in the network.
   * Machine roles must be either 'manager', 'root', or 'block'
   */
  public void updateMachineRoles(StyledPrintWriter out, NetworkAccess network,
          ServiceAddress machine_addr, List<String> roles_string)
                                   throws IOException, NetworkAdminException {

    for (String role : roles_string) {
      if (!validDDBRole(role)) {
        throw new RuntimeException("Invalid Machine Role");
      }
    }

    // The system page session,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);
    ODBTransaction t = session.createTransaction();

    ODBObject machines_ob = t.getNamedItem("machines");
    // Get the indices,
    ODBList id_idx = machines_ob.getList("idIdx");
    ODBList ipaddress_idx = machines_ob.getList("ipaddressIdx");
    ODBList managers = machines_ob.getList("managers");
    ODBList roots = machines_ob.getList("roots");
    ODBList blocks = machines_ob.getList("blocks");

    ODBObject machine = ipaddress_idx.getObject(machine_addr.formatString());
    // If the ip isn't found, rebuild completely,
    if (machine == null) {
      populateSystemTables(out, network);
    }
    // Otherwise update the roles field,
    else {
      ODBObject new_machine = t.constructObject(t.findClass("Machine"),
              machine.getString("id"),
              machine.getString("ipaddress"),
              new JSONArray(roles_string).toString());
      Reference mref = machine.getReference();

      id_idx.remove(mref);
      ipaddress_idx.remove(mref);
      managers.remove(mref);
      roots.remove(mref);
      blocks.remove(mref);

      id_idx.add(new_machine);
      ipaddress_idx.add(new_machine);
      if (roles_string.contains("manager")) {
        managers.add(new_machine);
      }
      if (roles_string.contains("root")) {
        roots.add(new_machine);
      }
      if (roles_string.contains("block")) {
        blocks.add(new_machine);
      }

      // Commit,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }

    }
  }

  /**
   * Initializes the system platform at the given path location.
   */
  public void initializeSystemPlatform(StyledPrintWriter out,
                                       PathLocation system_path_location)
                                    throws IOException, NetworkAdminException {

    out.println("Initialize system platform on " + system_path_location);

    // Get the network profile,
    NetworkAccess network = getNetworkProfile();

    // Check the path doesn't already exist,
    if (network.getPathInfoForPath(SystemStatics.SYSTEM_PATH) != null) {
      out.println("WARNING: System path (" +
                           SystemStatics.SYSTEM_PATH + ") already exists");
    }
    else {

      if (system_path_location == null) {
        out.println("ERROR: No path location given. Can't create system path");
        return;
      }

      // Create the path if we can,
      ServiceAddress system_path_addr = system_path_location.getRootLeader();
      ServiceAddress[] system_server_cluster =
                                       system_path_location.getRootServers();

      // Check the path information given,
      if (!network.isMachineInNetwork(system_path_addr) ||
          !allMachinesInNetwork(network, system_server_cluster)) {
        out.println("FAILED: Machine node not found in network");
        return;
      }
      MachineProfile sp_machine = network.getMachineProfile(system_path_addr);
      if (!sp_machine.isRoot()) {
        out.println("FAILED: System path machine node is not a root server");
        return;
      }

      // Create the system path,
      network.addPathToNetwork(
                SystemStatics.SYSTEM_PATH, "com.mckoi.odb.ObjectDatabase",
                system_path_addr, system_server_cluster);

    }

    // Done with the network profile side of things,

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    boolean commit_needed = false;

    ODBObject version_ob = t.getNamedItem("version");
    if (version_ob == null) {

      {
        // Define the system classes,
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
          out.printException(e);
          return;
        }
      }

      // Populate,
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

      commit_needed = true;

      out.println("Created System Account, VHost, Machine Node/Path Schema");
    }

    // Get the filesystem object if it exists,
    ODBObject filesys_ob = t.getNamedItem("fs");
    if (filesys_ob == null) {
      // file system doesn't exist so create it,
      try {
        FileSystemImpl.defineSchema(t);
      }
      catch (ClassValidationException e) {
        out.println("FAILED: Class Validation Error");
        out.printException(e);
        return;
      }

      // Set up the file system
      FileSystemImpl file_sys = new FileSystemImpl(t, "fs");
      file_sys.setup("System File System",
                     "The file system for system information.");

      commit_needed = true;

      out.println("Created System File System");
    }

    // Get the servers object if it exists,
    ODBObject servers_ob = t.getNamedItem("servers");
    if (servers_ob == null) {
      // Define and setup
      try {
        ServerRolesSchema.defineSchema(t);
        ServerRolesSchema.setup(t);
        commit_needed = true;
      }
      catch (ClassValidationException e) {
        out.println("FAILED: Class Validation Error");
        out.printException(e);
        return;
      }
    }


    // Commit the changes if needed,
    if (commit_needed) {
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
        return;
      }
      // Populate the system tables with information from the network,
      populateSystemTables(out, network);
    }

  }

  /**
   * Initializes a single process path (sysprocess00) with the given path info.
   */
  public void initializeProcesses(StyledPrintWriter out, PathLocation path_location)
                                    throws IOException, NetworkAdminException {

    final String process_path_name = "sysprocess00";

    // Get the network profile,
    NetworkAccess network = getNetworkProfile();

    if (network.getPathInfoForPath(process_path_name) != null) {

      out.println("WARNING: Process path (" +
                                    process_path_name + ") already exists");

    }
    else {

      if (path_location == null) {
        out.println("ERROR: No path location given. Can't create process path");
        return;
      }

      ServiceAddress process_path_addr = path_location.getRootLeader();
      ServiceAddress[] process_path_cluster = path_location.getRootServers();

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

      network.addPathToNetwork(
              process_path_name, "com.mckoi.odb.ObjectDatabase",
              process_path_addr, process_path_cluster);

    }

    // Done with the network profile side of things,

    boolean commit_needed = false;

    // Create a session for the process path instance,
    ODBSession session = getSession(process_path_name);

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
        out.printException(e);
        return;
      }

      commit_needed = true;
    }

    // Set the root item,
    ODBObject root_ob = t.getNamedItem("root");
    if (root_ob == null) {
      root_ob = t.constructObject(
                   t.findClass("Root"), "Sys Platform 1.0", null, null, null);
      t.addNamedItem("root", root_ob);

      commit_needed = true;
    }

    if (commit_needed) {
      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }

      // Populate the system tables with information from the network,
      populateSystemTables(out, network);

    }

  }

  /**
   * Populates the system tables with information about the network. This
   * should be called if the network topology is changed outside of the
   * functions defined by MWPCore.
   */
  public void updateNetwork(StyledPrintWriter out)
                                    throws IOException, NetworkAdminException {
    // Get the network profile,
    NetworkAccess network = getNetworkProfile();
    // Populate the system tables with information from the network,
    populateSystemTables(out, network);
  }

  /**
   * Creates a new install.
   */
  public void createInstall(StyledPrintWriter out, String install_name) {
    // Make sure the install name doesn't contain any invalid characters,
    if (install_name.contains("/") || install_name.contains("\\")) {
      throw new RuntimeException("install_name contains invalid characters");
    }

    // Create a session for the system path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();
    
    // The file system,
    FileSystem file_sys = new FileSystemImpl(t, "fs");

    // Does the install already exist?
    String install_loc = "/installs/" + install_name + "/";
    FileInfo install_dir = file_sys.getFileInfo(install_loc);
    if (install_dir == null) {
      // Make the directory
      file_sys.makeDirectory(install_loc);
      // Commit
      try {
        file_sys.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAIL: Commit failed");
      }
    }
    else {
      out.println("Install already exists.");
    }

  }

  /**
   * Uploads a new install version with the given name. The install must
   * exist before this is called. The 'local_directory' is the location
   * in the local file system of the directory to recursively copy into
   * the install.
   */
  public void uploadInstall(StyledPrintWriter out,
                            String install_name, String local_directory)
                                                           throws IOException {
    // Make sure the install name doesn't contain any invalid characters,
    if (install_name.contains("/") || install_name.contains("\\")) {
      throw new RuntimeException("install_name contains invalid characters");
    }

    File local_dir = new File(local_directory);
    if (!local_dir.exists()) {
      out.println("FAIL: Local directory doesn't exist.");
      return;
    }
    if (!local_dir.isDirectory()) {
      out.println("FAIL: Must provide a local directory to install from.");
      return;
    }

    // Create a session for the system path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();
    
    // The file system,
    FileSystem file_sys = new FileSystemImpl(t, "fs");

    // Does the install already exist?
    String install_loc = "/installs/" + install_name + "/";
    FileInfo install_dir = file_sys.getFileInfo(install_loc);
    if (install_dir != null && install_dir.isDirectory()) {
      // Get the versions,
      List<FileInfo> versions = file_sys.getSubDirectoryList(install_loc);

      String old_install_dir = null;
      int version_num = 1;
      // If it's empty the version number starts at 1
      if (versions != null && !versions.isEmpty()) {
        FileInfo last_version = versions.get(versions.size() - 1);
        String version_dir_name = last_version.getItemName();
        if (version_dir_name.endsWith("/")) {
          version_dir_name =
                  version_dir_name.substring(0, version_dir_name.length() - 1);
        }
        String ver_val = version_dir_name.substring(1);
        // The previous installation directory,
        old_install_dir = last_version.getAbsoluteName();
        // Next version,
        version_num = Integer.parseInt(ver_val) + 1;
      }

      // Make a new version directory,
      String new_install_dir = install_loc + "v" + zeroPad(version_num, 5) + "/";
      file_sys.makeDirectory(new_install_dir);

      out.println("Install directory: " + new_install_dir);

      // If there's an old install dir
      if (old_install_dir != null) {
        // Recursively copy all the files from the old installation directory,
        DirectorySynchronizer s =
              DirectorySynchronizer.getMckoiToMckoiSynchronizer(
                   null, file_sys, old_install_dir, file_sys, new_install_dir);
        s.synchronize();
      }

      // Ok, now we need to determine how the local directory is different
      // from the current one. We only upload the files that changed.

      DirectorySynchronizer s =
              DirectorySynchronizer.getJavaToMckoiSynchronizer(
                          new DirectorySynchronizer.StyledDSFeedback(out),
                          local_dir, file_sys, new_install_dir);
      long update_count = s.synchronize();

      out.println("Update count " + update_count);

      if (update_count > 0) {
        // Commit
        try {
          file_sys.commit();
        }
        catch (CommitFaultException e) {
          out.println("FAIL: Commit failed");
          out.printException(e);
        }
      }
      else {
        out.println("No changes so did not make a new install version.");
      }

    }
    else {
      out.println("FAIL: Install doesn't exist (use 'create install [name]' to make one)");
    }

  }

  /**
   * Adds an object database path resource to the network. The resource is
   * created at the path location given.
   */
  public void addODBResource(StyledPrintWriter out,
                             String path_name, PathLocation location)
                                    throws IOException, NetworkAdminException {

    // Get the network profile,
    NetworkAccess network = getNetworkProfile();

    ServiceAddress path_addr = location.getRootLeader();
    ServiceAddress[] path_cluster = location.getRootServers();

    if (!network.isMachineInNetwork(path_addr) ||
        !allMachinesInNetwork(network, path_cluster)) {
      out.println("FAILED: Machine node not found in network");
      return;
    }
    MachineProfile machine = network.getMachineProfile(path_addr);
    if (!machine.isRoot()) {
      out.println("FAILED: Given path location is not a root server");
      return;
    }

    // Check the path doesn't already exist,
    if (network.getPathInfoForPath(path_name) != null) {
      out.println("FAILED: Resource path (" + path_name + ") already exists");
      return;
    }

    // Create the resource,
    network.addPathToNetwork(
           path_name, "com.mckoi.odb.ObjectDatabase", path_addr, path_cluster);

    // Add this path to the system platform database,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);
    ODBTransaction t = session.createTransaction();
    // Update the system platform database,
    addPathProfile(t, path_name,
                      "com.mckoi.odb.ObjectDatabase", path_addr, path_cluster);

    // Commit the new path addition,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAIL: Commit failed.");
      out.printException(e);
    }

  }

  /**
   * Creates a new account with a file system over the given
   * account_path_name.
   */
  public void createAccount(StyledPrintWriter out, String account_name)
                                                           throws IOException {

    // There account names are not allowed because they would produce
    // commands that are ambiguous (eg. 'show account apps')
    String lc_appname = account_name.toLowerCase(Locale.ENGLISH);
    if (lc_appname.equals("apps") || lc_appname.equals("app") ||
        lc_appname.equals("users") || lc_appname.equals("user")) {
      out.println("Invalid account name");
      return;
    }

    int sz = account_name.length();
    for (int i = 0; i < sz; ++i) {
      char ch = account_name.charAt(i);
      if (!Character.isLetterOrDigit(ch)) {
        out.println("Account name contains invalid characters");
        return;
      }
    }

    final String account_path_name = accountToPathName(account_name);

    // Check the filesystem path is an empty resource,
    // Create a session for the user's filesystem repository path,
    ODBSession filesys_session = getSession(account_path_name);

    // Is the account path already defined?
    {
      ODBTransaction t = filesys_session.createTransaction();
      if (t.getNamedItem("users") != null) {
        out.println("FAIL: User resource is already defined.");
        return;
      }
    }

    {
      // Create a session for the 'sysplatform' path instance,
      ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

      // Open a transaction
      ODBTransaction t = session.createTransaction();

      // Make an account object,
      ODBClass account_class = t.findClass("Account");
      // (account, vhost_list, resource_list, server_list, value*)
      ODBObject account = t.constructObject(account_class,
                              account_name, null, null, null, "");

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
              account_path_name,
              "$AccountFileRepository", "com.mckoi.odb.ObjectDatabase",
              account);

      // Add to the account resource index,
      ODBObject accountres_index = t.getNamedItem("accountresources");
      ODBList accountres_idx = accountres_index.getList("pathIdx");
      accountres_idx.add(account_resource);

      // Add the account resource to the account resource_list
      account_resource_list.add(account_resource);

      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
        return;
      }
    }

    // Define the schema,
    try {

      // Create a session for the user's filesystem repository path,
      ODBSession session = getSession(account_path_name);
      // Open a transaction
      ODBTransaction t = session.createTransaction();

      // Define the file system on the account path,
      FileSystemImpl.defineSchema(t);
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
      FileSystemImpl filesys = new FileSystemImpl(t, "accountfs");
      filesys.setup(account_path_name, "Account File System");

      // Make a system directory in the file system,
      filesys.makeDirectory("/config/mwp/");
      filesys.makeDirectory("/system/");
      // Add a plain text file with version details and with the account
      // name.
      filesys.createFile("/system/account.txt", "text/plain",
                          System.currentTimeMillis());
      DataFile dfile = filesys.getDataFile("/system/account.txt");
      PrintWriter pout = new PrintWriter(
              new OutputStreamWriter(DataFileUtils.asOutputStream(dfile)));
      pout.print("Mckoi Web Platform File System version 1.0\n");
      pout.print("Created: " + new Date().toString() + "\n");
      pout.print("Account: " + account_name + "\n");
      pout.print("Path: " + account_path_name + "\n");
      pout.flush();

      // Default Account Locale information,
      filesys.createFile("/config/mwp/locale", "text/plain",
                          System.currentTimeMillis());
      dfile = filesys.getDataFile("/config/mwp/locale");
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
      out.printException(e);
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Deactivates an account preventing the account from expressing itself.
   * This causes the application server to no longer show pages for this
   * account and processes belonging to this account will be terminated and new
   * processes for this account will not be able to be started.
   */
  public void deactiveAccount(StyledPrintWriter out, String string) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /**
   * Adds a new user to the account. This is for the purpose of the
   * administration functions on the account.
   */
  public void addAccountUser(StyledPrintWriter out,
                String account_name, String username, String password)
                                                           throws IOException {

    final String account_path_name = accountToPathName(account_name);

    // Create a transaction for the user's main path,
    ODBSession session = getSession(account_path_name);
    ODBTransaction t = session.createTransaction();

    // Add the user record to this account,
    addUserToAccount(t, username, "", password, "");

    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Removes a user from the account.
   */
  public void removeAccountUser(StyledPrintWriter out,
                     String account_name, String username) throws IOException {

    final String account_path_name = accountToPathName(account_name);

    // Create a transaction for the user's main path,
    ODBSession session = getSession(account_path_name);
    ODBTransaction t = session.createTransaction();

    // Remove the user record from this account,
    boolean changed = removeUserFromAccount(t, username);
    
    if (changed) {
      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Sets the password for the given user in the given account.
   */
  public void setAccountUserPassword(StyledPrintWriter out,
                  String account_name, String user_name, String new_password)
                                                           throws IOException {

    final String account_path_name = accountToPathName(account_name);

    // Create a transaction for the user's main path,
    ODBSession session = getSession(account_path_name);
    ODBTransaction t = session.createTransaction();

    // Remove the user record from this account,
    boolean changed = setUserPasswordInAccount(t, user_name, new_password);

    // Clean user session,
    changed |= cleanUserSessionInAccount(t, user_name);

    if (changed) {
      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Cleans any session cookies related to the given user in the given account.
   */
  public void cleanAllAccountUserSessions(StyledPrintWriter out,
                                       String account_name, String user_name) {

    final String account_path_name = accountToPathName(account_name);

    // Create a transaction for the user's main path,
    ODBSession session = getSession(account_path_name);
    ODBTransaction t = session.createTransaction();

    // Clean user session,
    boolean changed = cleanUserSessionInAccount(t, user_name);

    if (changed) {
      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Connects a resource to an account.
   */
  public void connectResource(StyledPrintWriter out,
                              String path_name, String account_name) {

    // Create a transaction for the user's main path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);
    ODBTransaction t = session.createTransaction();

    if (path_name.startsWith("sys")) {
      out.println("Not allowed to connect a system resource to an account");
      return;
    }

    // Query the accounts graph for the account,
    ODBObject accounts = t.getNamedItem("accounts");
    ODBList accounts_list = accounts.getList("accountIdx");
    ODBObject account_ob = accounts_list.getObject(account_name);
    if (account_ob == null) {
      out.println("Account not found");
      return;
    }

    // Query the paths graph for the path name,
    ODBObject paths_index = t.getNamedItem("paths");
    ODBList paths_list = paths_index.getList("pathIdx");
    ODBObject path_ob = paths_list.getObject(path_name);
    if (path_ob == null) {
      out.println("Path not found");
      return;
    }

    // The path type,
    String path_consensus = path_ob.getString("type");

    ODBObject accountresources_ob = t.getNamedItem("accountresources");
    ODBList pathidx_list = accountresources_ob.getList("pathIdx");
    if (pathidx_list.get(path_name) != null) {
      out.println("Path already associated to an account");
    }

    // Create an account resource object for the account,
    ODBClass account_resource_class = t.findClass("AccountResource");
    // (path, resource_name, type, account)
    ODBObject account_resource = t.constructObject(account_resource_class, 
            path_name, "Resource", path_consensus, account_ob);

    // Add the resource to the accounts list,
    ODBList account_resource_list = account_ob.getList("resource_list");
    account_resource_list.add(account_resource);

    // Add to the general resource list,
    pathidx_list.add(account_resource);

    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }
  
  /**
   * Disconnects a resource to an account.
   */
  public void disconnectResource(StyledPrintWriter out,
                                 String path_name, String account_name) {

    // Create a transaction for the user's main path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);
    ODBTransaction t = session.createTransaction();

    // Query the accounts graph for the account,
    ODBObject accounts = t.getNamedItem("accounts");
    ODBList accounts_list = accounts.getList("accountIdx");
    ODBObject account_ob = accounts_list.getObject(account_name);
    if (account_ob == null) {
      out.println("Account not found");
      return;
    }

    // Query the paths graph for the path name,
    ODBObject paths_index = t.getNamedItem("paths");
    ODBList paths_list = paths_index.getList("pathIdx");
    ODBObject path_ob = paths_list.getObject(path_name);
    if (path_ob == null) {
      out.println("Path not found");
      return;
    }

    // The 'resource_list' from the account object,
    ODBList account_resource_list = account_ob.getList("resource_list");
    // The global account resources list,
    ODBObject accountresources_ob = t.getNamedItem("accountresources");
    ODBList pathidx_list = accountresources_ob.getList("pathIdx");

    ODBList sublist = pathidx_list.tail(path_name);
    List<ODBObject> consider_res = new ArrayList();
    for (ODBObject resource : sublist) {
      if (resource.getString("path").equals(path_name)) {
        consider_res.add(resource);
      }
      else {
        break;
      }
    }

    Reference account_ob_reference = account_ob.getReference();
    boolean change_made = false;

    // For each considered resource,
    for (ODBObject res : consider_res) {
      // res is class 'AccountResource'
      ODBObject account_consider = res.getObject("account");
      Reference account_consider_reference = account_consider.getReference();
      // If the considered account is the same as the account ob,
      if (account_consider_reference.equals(account_ob_reference)) {
        // Remove from the global list,
        pathidx_list.remove(res.getReference());
        // Remove from the account's resource_list
        account_resource_list.remove(res.getReference());
        change_made = true;
      }
    }

    // Commit the changes,
    if (change_made) {
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Connects a VHost definition (either a single vhost or a list) to an
   * account that manages it. There may only be one domain->account association
   * per domain.
   * <p>
   * <pre>
   * Example vhost_expr; 
   *   mckoi.com:http
   *   www.mckoi.com:*
   *   z10.mckoi.com:*, z11.mckoi.com:*, z12.mckoi.com:*
   * </pre>
   */
  public void connectVHost(StyledPrintWriter out,
                   String vhost_expr, String account_name) throws IOException {

    // Parse the vhost list,
    Set<String> vhost_set = parseVHostList(out, vhost_expr);

    if (vhost_set == null) {
      return;
    }

    // Create a transaction for the user's main path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);
    ODBTransaction t = session.createTransaction();

    // Query the accounts graph for the account,
    ODBObject accounts = t.getNamedItem("accounts");
    ODBList accounts_list = accounts.getList("accountIdx");
    ODBObject account_ob = accounts_list.getObject(account_name);
    if (account_ob == null) {
      out.println("Account not found");
      return;
    }

    ODBObject vhosts_ob = t.getNamedItem("vhosts");
    ODBList vhosts_idx = vhosts_ob.getList("domainIdx");

    // List of vhosts on the account,
    ODBList account_vhosts_list = account_ob.getList("vhost_list");

    int update_count = 0;
    for (String vhost : vhost_set) {
      // Check if domain name already associated with an account,
      if (vhosts_idx.contains(vhost)) {
        out.println("VHost already associated: " + vhost);
      }
      else {

        // Create the VHost object
        ODBClass vhost_class = t.findClass("VHost");
        ODBObject new_vhost = t.constructObject(vhost_class, vhost, account_ob);

        // Add the association to the main index,
        vhosts_idx.add(new_vhost);
        // Add the association to the account object,
        account_vhosts_list.add(new_vhost);
        ++update_count;

      }
    }

    // Commit,
    if (update_count > 0) {
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Disconnect a VHost association with an account that currently manages it.
   */
  public void disconnectVHost(StyledPrintWriter out,
                   String vhost_expr, String account_name) throws IOException {

    // Parse the vhost list,
    Set<String> vhost_set = parseVHostList(out, vhost_expr);

    if (vhost_set == null) {
      return;
    }

    // Create a transaction for the user's main path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);
    ODBTransaction t = session.createTransaction();

    // Query the accounts graph for the account,
    ODBObject accounts = t.getNamedItem("accounts");
    ODBList accounts_list = accounts.getList("accountIdx");
    ODBObject account_ob = accounts_list.getObject(account_name);
    if (account_ob == null) {
      out.println("Account not found");
      return;
    }

    ODBObject vhosts_ob = t.getNamedItem("vhosts");
    ODBList vhosts_idx = vhosts_ob.getList("domainIdx");

    ODBList account_vhost_list = account_ob.getList("vhost_list");

    boolean change_made = false;

    // Check if domain name already associated with an account,
    for (String vhost : vhost_set) {
      ODBObject vhost_ob = vhosts_idx.getObject(vhost);
      if (vhost_ob != null) {
        // Remove from the account object
        change_made |= account_vhost_list.remove(vhost_ob.getReference());
        // Remove from the master vhosts index,
        change_made |= vhosts_idx.remove(vhost_ob.getReference());
      }
      else {
        out.println("VHost association not found: " + vhost);
      }
    }

    // Commit the changes,
    if (change_made) {
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Turns the given account into a super user, which gives it access to the
   * system paths.
   */
  public void grantSuperUser(StyledPrintWriter out, String account_name) {

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The account index,
    ODBObject accounts_named = t.getNamedItem("accounts");
    // The sorted list of accounts,
    ODBList accounts_idx = accounts_named.getList("accountIdx");

    // Get the account,
    ODBObject account_ob = accounts_idx.getObject(account_name);
    if (account_ob == null) {
      out.println("Account not found");
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
        out.printException(e);
      }

    }
    catch (JSONException e) {
      out.println("FAILED: JSON Exception");
      out.printException(e);
    }

  }

  /**
   * Revokes super user privs on the given account.
   */
  public void revokeSuperUser(StyledPrintWriter out, String account_name) {
    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The account index,
    ODBObject accounts_named = t.getNamedItem("accounts");
    // The sorted list of accounts,
    ODBList accounts_idx = accounts_named.getList("accountIdx");

    // Get the account,
    ODBObject account_ob = accounts_idx.getObject(account_name);
    if (account_ob == null) {
      out.println("Account not found");
      return;
    }

    try {

      JSONArray rw_json_ob = new JSONArray();

      JSONStringer json = new JSONStringer();
      json.object();
      json.key("readwrite_odb_paths");
      json.value(rw_json_ob);
      json.key("globalpriv");
      json.value("normal");
      json.endObject();

      account_ob.setString("value", json.toString());

      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }

    }
    catch (JSONException e) {
      out.println("FAILED: JSON Exception");
      out.printException(e);
    }
  }

  /**
   * Adds an application to the given account. If the application name already
   * exists then it is not changed and an error is reported. Otherwise an
   * entry is added to the '/config/mwp/webapps.xml' file with the given details.
   */
  public void addAccountApp(StyledPrintWriter out, String account_name,
          String app_name, String vhost_list, String context_path,
          String build_dir) throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // Read the existing applications doc,
    ApplicationsDocument applications_doc =
                    UserApplicationsSchema.readApplicationsDocument(file_sys);

    // All the applications
    List<Node> apps = applications_doc.getAllApplications();
    for (Node app : apps) {
      if (applications_doc.getAppName(app).equals(app_name)) {
        out.println("Application already exists");
        return;
      }
    }

    // Create the list of vhosts,
//    List<String> vhosts = parseVHostsList(vhost_list);
    Set<String> vhosts = parseVHostList(out, vhost_list);

    FileName build_dir_fname = new FileName(account_name, build_dir);

    // Create the new application,
    applications_doc.addDefaultApplication(
            app_name, "", context_path, null, build_dir_fname.toString(),
            vhosts, "full");

    // Write the applications doc out to the account file system,
    UserApplicationsSchema.writeApplicationsDocument(file_sys, applications_doc);

    // Build the webapps into a binary,
    UserApplicationsSchema.buildSystemWebApps(file_sys, out);

    // Commit the changes,
    try {
      file_sys.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Removes the application with the given name from the account. This will
   * simply alter the /config/mwp/webapps.xml file and remove the app.
   */
  public void removeAccountApp(StyledPrintWriter out,
                     String account_name, String app_name) throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // Read the existing applications doc,
    ApplicationsDocument applications_doc =
                    UserApplicationsSchema.readApplicationsDocument(file_sys);

    // All the applications
    List<Node> apps = applications_doc.getAllApplications();
    Node found_app_node = null;
    for (Node app : apps) {
      if (applications_doc.getAppName(app).equals(app_name)) {
        found_app_node = app;
      }
    }

    if (found_app_node == null) {
      out.println("Application not found");
      return;
    }

    // Remove the application node,
    found_app_node.getParentNode().removeChild(found_app_node);

    // Write the applications doc out to the account file system,
    UserApplicationsSchema.writeApplicationsDocument(file_sys, applications_doc);

    // Commit the changes,
    try {
      file_sys.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Deletes the /config/mwp/webapps.xml file and resets all the web apps on
   * the account.
   */
  public void cleanAccountApps(StyledPrintWriter out, String account_name) {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    file_sys.deleteFile(UserApplicationsSchema.WEBAPPS_CONF);
    file_sys.deleteFile(UserApplicationsSchema.WEBAPPS_CONF2);

    // Commit the changes,
    try {
      file_sys.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Displays a summary of all the applications on the given account.
   */
  public void showAccountApps(StyledPrintWriter out, String account_name) {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // Read the existing applications doc,
    ApplicationsDocument applications_doc =
                    UserApplicationsSchema.readApplicationsDocument(file_sys);

    // All the applications
    List<Node> apps = applications_doc.getAllApplications();
    for (Node app : apps) {
      String app_name = applications_doc.getAppName(app);
      String app_description = applications_doc.getAppDescription(app);
      out.println(app_name);
      if (app_description != null && app_description.trim().length() > 0) {
        out.println("  " + app_description);
      }
      List<Node> app_locations = applications_doc.getAppLocations(app);
      for (Node location: app_locations) {
        String context_path =
                applications_doc.getAttribute(location, "path");
        String repository =
                applications_doc.getAttribute(location, "repository");
        String fs_path =
                applications_doc.getAttribute(location, "repository_path");
        
        String rep_fname;
        // LEGACY,
        if (repository != null) {
          rep_fname = "/" + repository + fs_path;
        }
        else {
          rep_fname = fs_path;
        }
        
        out.println("  Location");
        out.println("    context_path =    " + context_path);
        out.println("    repository_path = " + rep_fname);
        List<String> vhosts = applications_doc.getLocationVHosts(location);
        List<String> rights = applications_doc.getLocationRights(location);
        out.println("    vhosts =          " + vhosts);
        out.println("    rights =          " + rights);
      }
      out.println();
    }

  }

  /**
   * Deploys the given installation name.
   */
  public void deployInstall(StyledPrintWriter out, String install_name)
                                                          throws IOException {

    // Make sure the install name doesn't contain any invalid characters,
    if (install_name.contains("/") || install_name.contains("\\")) {
      throw new RuntimeException("install_name contains invalid characters");
    }

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The system file system,
    FileSystemImpl file_sys = new FileSystemImpl(t, "fs");

    // The sub-directories of the installs directory represent all the
    // available versions for the install.
    String install_name_dir = "/installs/" + install_name + "/";
    FileInfo install = file_sys.getFileInfo(install_name_dir);
    if (install == null) {
      out.println("Install not found");
      return;
    }

    DateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    // Get the last version of this install
    List<FileInfo> versions =
                  file_sys.getSubDirectoryList(install.getAbsoluteName());
    int versions_sz = versions.size();
    long latest_timestamp = -1;
    String latest_version_str = null;
    if (versions_sz > 0) {
      FileInfo latest_version = versions.get(versions_sz - 1);
      latest_timestamp = latest_version.getLastModified();
      latest_version_str = latest_version.getItemName();
    }
    out.println(install.getItemName());
    if (latest_timestamp < 0) {
      out.println("No versions available");
      return;
    }

    out.println("Latest version: " +
                sdf.format(new Date(latest_timestamp)) +
                " (" + latest_version_str + ")");

    String use_dir = install_name_dir + latest_version_str;

    // Update the deploy file
    String current_install_txt =
                     AppServiceNode.getFileAsString(file_sys, "/curinstall");

    if (current_install_txt != null) {
      if (current_install_txt.equals(use_dir)) {
        out.println("Latest version already deployed");
        return;
      }
    }

    // Write the curinstall file,
    AppServiceNode.putStringAsFile(use_dir, file_sys, "/curinstall");

    // Commit,
    try {
      file_sys.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Uploads a webapp binary to the application's build location. The local
   * object may either be a path or a .war file.
   */
  public void uploadAccountApp(StyledPrintWriter out,
                String account_name, String app_name, String local_ob) 
                                                           throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // Get the map of locations for the application,
    Map<String, FileName> app_locations =
              UserApplicationsSchema.getUserAppLocationMap(file_sys, app_name);
    if (app_locations == null) {
      out.println("Application not found", "error");
      return;
    }
    FileName main_loc = app_locations.get("main");
    if (main_loc == null) {
      out.println("Application does not have a 'main' location type", "error");
      return;
    }
    // The main location to upload the web application,
    FileName rep_fname = main_loc;

    // Upload here,
    File local_ob_file = new File(local_ob);
    if (!local_ob_file.exists()) {
      out.println("Local .jar/.war or path not found in local file system", "error");
      return;
    }

    long update_count;
    FileSystem repos_file_sys;

    String found_repository = rep_fname.getRepositoryId();
    String found_repository_path = rep_fname.getPathFile();

    // The repository to upload to,
    if (found_repository.equals("$user") ||
        found_repository.equals(account_name)) {
      repos_file_sys = file_sys;
    }
    else {
      out.println("Repository other than '$user' is not supported", "error");
      return;
    }

    if (local_ob_file.isDirectory()) {
      // Synchronize directory,
      out.println("Uploading directory to " + rep_fname, "info");

      // Sync,
      DirectorySynchronizer s =
              DirectorySynchronizer.getJavaToMckoiSynchronizer(
                   new DirectorySynchronizer.StyledDSFeedback(out),
                   local_ob_file, repos_file_sys, found_repository_path);
      update_count = s.synchronize();

    }
    else {
      // Assume it's a .war or .jar file and uncompress it,
      out.println("Uploading archive to " + rep_fname, "info");

      // Sync,
      DirectorySynchronizer s =
              DirectorySynchronizer.getZipToMckoiSynchronizer(
                   new DirectorySynchronizer.StyledDSFeedback(out),
                   local_ob_file, repos_file_sys, found_repository_path);
      s.addPathToSkip("/META-INF/");
      update_count = s.synchronize();

    }

    out.println("Updating '/bin/', '/nbin/' directory from application " +
                rep_fname, "info");
    // Are there any 'mjs' scripts in this distribution to copy into the /bin
    // directory?

    // This is a little bit of magic. Basically any application will be able
    // to install '.js' scripts into the /bin directory, possibly overwriting
    // core commands.

    {
      String mjs_script_path = found_repository_path + "WEB-INF/classes/mjs/bin/";
      FileInfo script_dir_fi = repos_file_sys.getFileInfo(mjs_script_path);
      if (script_dir_fi != null && script_dir_fi.isDirectory()) {
        // Yes, so copy the files to the /bin directory,
        DirectorySynchronizer s =
             DirectorySynchronizer.getMckoiToMckoiSynchronizer(
                     new DirectorySynchronizer.StyledDSFeedback(out),
                     repos_file_sys, mjs_script_path, repos_file_sys, "/bin/");
        s.setDeleteFilesFlag(false);
        update_count += s.synchronize();
      }
    }
    // Node bin directory
    {
      String mjs_script_path = found_repository_path + "WEB-INF/classes/mjs/nbin/";
      FileInfo script_dir_fi = repos_file_sys.getFileInfo(mjs_script_path);
      if (script_dir_fi != null && script_dir_fi.isDirectory()) {
        // Yes, so copy the files to the /bin directory,
        DirectorySynchronizer s =
             DirectorySynchronizer.getMckoiToMckoiSynchronizer(
                     new DirectorySynchronizer.StyledDSFeedback(out),
                     repos_file_sys, mjs_script_path, repos_file_sys, "/nbin/");
        s.setDeleteFilesFlag(false);
        update_count += s.synchronize();
      }
    }

    // If there's something to update,
    if (update_count > 0) {
      // Commit the changes,
      try {
        file_sys.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault", "error");
        out.printException(e);
      }
    }

  }

  /**
   * Refreshes all the account applications for the given account name. This
   * will refresh the ClassLoader instances for both the web server and the
   * process manager causing a reload of the application.
   */
  public void refreshAllAccountApps(StyledPrintWriter out,
                                      String account_name) throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // Build the webapps system file,
    UserApplicationsSchema.buildSystemWebApps(file_sys, out);

    // Commit the changes,
    try {
      file_sys.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Refreshes a single application for the given account name. This will
   * refresh the ClassLoader instances for both the web server and the process
   * manager causing a reload of the application.
   */
  public void refreshAccountApp(StyledPrintWriter out,
             String account_name, String application_name) throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // Work out the project path,
    Map<String, FileName> user_app_map =
                  UserApplicationsSchema.getUserAppLocationMap(
                                                  file_sys, application_name);
    if (user_app_map == null) {
      out.print("FAILED: Application lookup failed: ");
      out.println(application_name);
      return;
    }
    FileName project_path = user_app_map.get("main");
    if (project_path == null) {
      out.print("FAILED: Unable to find 'main' location for application: ");
      out.println(application_name);
      return;
    }

    // Build the webapps system file,
    UserApplicationsSchema.reloadSystemWebAppsAt(
                                      file_sys, project_path.toString(), out);

    // Commit the changes,
    try {
      file_sys.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Copy files from the local file system to the account's directory.
   */
  public void copyLocalTo(StyledPrintWriter out,
                   String local_dir, String account_name, String account_dir)
                                                           throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    FileInfo account_fi = file_sys.getFileInfo(account_dir);
    if (account_fi != null && account_fi.isFile()) {
      out.println("Account directory specified is an existing file");
      return;
    }

    // Upload here,
    File local_ob_file = new File(local_dir);
    if (!local_ob_file.exists()) {
      out.println("Local directory or file was not found");
      return;
    }

    long update_count;
    FileSystem repos_file_sys = file_sys;

    if (local_ob_file.isDirectory()) {
      // Synchronize directory,
      out.println("Uploading directory to " +
                  account_dir + " @" + account_name);

      // Sync,
      DirectorySynchronizer s =
              DirectorySynchronizer.getJavaToMckoiSynchronizer(
                   new DirectorySynchronizer.StyledDSFeedback(out),
                   local_ob_file, repos_file_sys, account_dir);
      s.setDeleteFilesFlag(false);
      update_count = s.synchronize();

    }
    else {
      // A single file,
      out.println("Uploading file to " +
                  account_dir + " @" + account_name);

      // Sync,
      DirectorySynchronizer s =
              DirectorySynchronizer.getJavaToMckoiSynchronizer(
                   new DirectorySynchronizer.StyledDSFeedback(out),
                   local_ob_file, repos_file_sys, account_dir);
      s.setDeleteFilesFlag(false);
      update_count = s.synchronize();
      
    }

    // If there's something to update,
    if (update_count > 0) {
      // Commit the changes,
      try {
        file_sys.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Copies files from the given account directory to the local file system.
   */
  public void copyAccountDirTo(StyledPrintWriter out,
                 String account_name, String account_dir, String local_dir)
                                                           throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    // The local path,
    File local_ob_file = new File(local_dir);
    if (local_ob_file.exists() && local_ob_file.isFile()) {
      out.println("Local directory specified is an existing file");
      return;
    }

    // Check the account directory exists,
    FileInfo account_fi = file_sys.getFileInfo(account_dir);
    if (account_fi == null) {
      out.println("Account directory or file was not found");
      return;
    }

    long update_count;
    FileSystem repos_file_sys = file_sys;

    if (account_fi.isDirectory()) {
      // Synchronize directory,
      out.println("Downloading directory to " + local_ob_file);

      // Sync,
      DirectorySynchronizer s =
              DirectorySynchronizer.getMckoiToJavaSynchronizer(
                   new DirectorySynchronizer.StyledDSFeedback(out),
                   repos_file_sys, account_dir, local_ob_file);
      s.setDeleteFilesFlag(false);
      update_count = s.synchronize();

    }
    else {
      // A single file,
      out.println("Downloading file to " + local_ob_file);

      // Sync,
      DirectorySynchronizer s =
              DirectorySynchronizer.getMckoiToJavaSynchronizer(
                   new DirectorySynchronizer.StyledDSFeedback(out),
                   repos_file_sys, account_dir, local_ob_file);
      s.setDeleteFilesFlag(false);
      update_count = s.synchronize();

    }

    // If there's something to update,
    if (update_count > 0) {
      // Commit the changes,
      try {
        file_sys.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Copy files from the local zip archive to the given account's directory.
   */
  public void unzipLocalTo(StyledPrintWriter out,
                   String local_zip, String account_name, String account_dir)
                                                           throws IOException {

    // Get the account file system,
    FileSystemImpl file_sys = getAccountFileSystem(account_name);

    FileInfo account_fi = file_sys.getFileInfo(account_dir);
    if (account_fi != null && account_fi.isFile()) {
      out.println("Account directory specified is an existing file");
      return;
    }

    // Upload here,
    File local_zip_ob = new File(local_zip);
    if (!local_zip_ob.exists()) {
      out.println("Local zip archive was not found");
      return;
    }

    long update_count;
    FileSystem repos_file_sys = file_sys;

    // Synchronize directory,
    out.println("Upzipping archive to " +
                account_dir + " @" + account_name);

    // Sync,
    DirectorySynchronizer s =
            DirectorySynchronizer.getZipToMckoiSynchronizer(
                  new DirectorySynchronizer.StyledDSFeedback(out),
                  local_zip_ob, repos_file_sys, account_dir);
    s.setDeleteFilesFlag(false);
    update_count = s.synchronize();

    // If there's something to update,
    if (update_count > 0) {
      // Commit the changes,
      try {
        file_sys.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Registers the given server on the network. Once registered, the machine
   * may be used as a web or process server.
   */
  public void registerServer(StyledPrintWriter out,
                     String server_name, String public_ip, String private_ip)
                                                           throws IOException {

    // Resolve the ip addresses of the server to public/private addresses.
    String normalized_public_ip =
                        ServerRolesSchema.canonicalIPString(public_ip);
    String normalized_private_ip =
                        ServerRolesSchema.canonicalIPString(private_ip);

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // Query the machine location,
    ODBObject q1server_ob = ServerRolesSchema.lookupServer(t, server_name);
    ODBObject q2server_ob = ServerRolesSchema.lookupServer(t, public_ip);
    ODBObject q3server_ob = ServerRolesSchema.lookupServer(t, private_ip);
    if (q1server_ob != null || q2server_ob != null || q3server_ob != null) {
      out.println("Server already registered");
      return;
    }

    // Construct the server object
    ODBClass server_class = t.findClass("SR.Server");
    // (name, publicip, privateip, value)
    ODBObject server_ob = t.constructObject(server_class,
               server_name, normalized_public_ip, normalized_private_ip, "");

    ODBObject servers = t.getNamedItem("servers");
    ODBList server_name_idx = servers.getList("nameIdx");
    ODBList public_ip_idx = servers.getList("publicipIdx");
    ODBList private_ip_idx = servers.getList("privateipIdx");

    // Add the server to the indexes,
    server_name_idx.add(server_ob);
    public_ip_idx.add(server_ob);
    private_ip_idx.add(server_ob);

    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * De-registers the given server on the network.
   */
  public void deregisterServer(StyledPrintWriter out, String machine_location)
                                                           throws IOException {

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // Query the machine location,
    ODBObject server_ob = ServerRolesSchema.lookupServer(t, machine_location);
    if (server_ob == null) {
      out.println("Server not registered");
      return;
    }

    // The server information,
    ODBObject servers = t.getNamedItem("servers");
    ODBList server_name_idx = servers.getList("nameIdx");
    ODBList public_ip_idx = servers.getList("publicipIdx");
    ODBList private_ip_idx = servers.getList("privateipIdx");

    // Remove the server from the indexes,
    boolean r1 = server_name_idx.remove(server_ob.getString("name"));
    boolean r2 = public_ip_idx.remove(server_ob.getString("publicip"));
    boolean r3 = private_ip_idx.remove(server_ob.getString("privateip"));

    // Oops,
    if (!r1 || !r2 || !r3) {
      out.println("Index problem; unable to remove server from index");
      return;
    }

    // Commit the changes,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      out.println("FAILED: Commit fault");
      out.printException(e);
    }

  }

  /**
   * Instructs the given machine to start running the given command.
   */
  public void startRole(StyledPrintWriter out,
                     String role, String machine_location) throws IOException {

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // Query the machine location,
    ODBObject server_ob = ServerRolesSchema.lookupServer(t, machine_location);
    if (server_ob == null) {
      out.println("Server not registered");
      return;
    }

    boolean changed = ServerRolesSchema.addServerRoles(t,
                                  server_ob, Collections.singletonList(role));

    if (changed) {
      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Instructs the given machine to stop running the given command.
   */
  public void stopRole(StyledPrintWriter out,
                     String role, String machine_location) throws IOException {

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // Query the machine location,
    ODBObject server_ob = ServerRolesSchema.lookupServer(t, machine_location);
    if (server_ob == null) {
      out.println("Server not registered");
      return;
    }

    boolean changed = ServerRolesSchema.removeServerRoles(t,
                                  server_ob, Collections.singletonList(role));

    if (changed) {
      // Commit the changes,
      try {
        t.commit();
      }
      catch (CommitFaultException e) {
        out.println("FAILED: Commit fault");
        out.printException(e);
      }
    }

  }

  /**
   * Starts a role in the MckoiDDB network, where the role is either manager,
   * root or block.
   */
  public void startDDBRole(StyledPrintWriter out, String role, String machine_node)
                     throws IOException, NetworkAdminException, JSONException {

    // Get the MckoiDDB NetworkProfile,
    NetworkAccess network = getNetworkProfile();

    // Parse the machine node,
    ServiceAddress machine_addr = ServiceAddress.parseString(machine_node);

    // Make sure the server is in the network
    MachineProfile p = network.getMachineProfile(machine_addr);
    if (p == null) {
      out.println("Machine was not found in the MckoiDDB network.");
      return;
    }

    // Here we have some rules,
    // 1. There must be a manager server assigned before block and roots can be
    //    assigned.

    MachineProfile[] current_managers = network.getManagerServers();
    if (!role.equals("manager")) {
      if (current_managers.length == 0) {
        out.println("Can not assign block or root role when no manager is available on the");
        out.println("  network.");
        return;
      }
    }

    // Is the role valid?
    if (!validDDBRole(role)) {
      out.println("Unknown role: " + role);
      return;
    }

    // Check if the machine is already performing the role,
    List<String> roles_string = new ArrayList(3);
    boolean already_doing_role = false;
    if (p.isBlock()) {
      roles_string.add("block");
      already_doing_role |= role.equals("block");
    }
    if (p.isManager()) {
      roles_string.add("manager");
      already_doing_role |= role.equals("manager");
    }
    if (p.isRoot()) {
      roles_string.add("root");
      already_doing_role |= role.equals("root");
    }
    if (already_doing_role) {
      out.println("The machine node is already assigned to the " + role + " role.");
      return;
    }

    // Perform the assignment,
    if (role.equals("block")) {
      network.startBlock(machine_addr);
      network.registerBlock(machine_addr);
    }
    else if (role.equals("manager")) {
      network.startManager(machine_addr);
      network.registerManager(machine_addr);
    }
    else if (role.equals("root")) {
      network.startRoot(machine_addr);
      network.registerRoot(machine_addr);
    }

    // Add the role,
    roles_string.add(role);

    // Update the Mckoi Web Platform database,
    updateMachineRoles(out, network, machine_addr, roles_string);

  }

  /**
   * Stops a role in the MckoiDDB network.
   */
  public void stopDDBRole(StyledPrintWriter out, String role, String machine_node)
                                    throws IOException, NetworkAdminException {
    
    // Get the MckoiDDB NetworkProfile,
    NetworkAccess network = getNetworkProfile();

    // Parse the machine node,
    ServiceAddress machine_addr = ServiceAddress.parseString(machine_node);

    MachineProfile p = network.getMachineProfile(machine_addr);
    if (p == null) {
      out.println("Machine was not found in the network schema.");
      return;
    }

    // Here we have some rules,
    // 1. The manager can not be relieved until all block and root servers have
    //    been.

    MachineProfile[] current_managers = network.getManagerServers();
    MachineProfile[] current_roots = network.getRootServers();
    MachineProfile[] current_blocks = network.getBlockServers();
    if (role.equals("manager") && current_managers.length == 1) {
      if (current_roots.length > 0 ||
          current_blocks.length > 0) {
        out.println("Can not relieve manager role when there are existing block and root");
        out.println("  assignments.");
        return;
      }
    }

    // Is the role valid?
    if (!validDDBRole(role)) {
      out.println("Unknown role: " + role);
      return;
    }

    // Check that the machine is performing the role,
    List<String> roles_string = new ArrayList(3);
    boolean already_doing_role = false;
    if (p.isBlock()) {
      roles_string.add("block");
      already_doing_role |= role.equals("block");
    }
    if (p.isManager()) {
      roles_string.add("manager");
      already_doing_role |= role.equals("manager");
    }
    if (p.isRoot()) {
      roles_string.add("root");
      already_doing_role |= role.equals("root");
    }

    if (!already_doing_role) {
      out.println("The machine is not assigned to the " + role + " role.");
      return;
    }

    // Perform the assignment,
    if (role.equals("block")) {
      network.deregisterBlock(machine_addr);
      network.stopBlock(machine_addr);
    }
    else if (role.equals("manager")) {
      network.deregisterManager(machine_addr);
      network.stopManager(machine_addr);
    }
    else if (role.equals("root")) {
      network.deregisterRoot(machine_addr);
      network.stopRoot(machine_addr);
    }

    // Remove the role,
    roles_string.remove(role);

    // Update the Mckoi Web Platform database,
    updateMachineRoles(out, network, machine_addr, roles_string);

  }

  /**
   * Adds a DDB path to the MckoiDDB network.
   */
  public void addDDBPath(StyledPrintWriter out, String consensus,
                  String path_name, PathLocation path_location)
                                    throws IOException, NetworkAdminException {

    NetworkAccess network = getNetworkProfile();

    ServiceAddress[] addresses = path_location.getRootServers();

    for (int i = 0; i < addresses.length; ++i) {
      MachineProfile p = network.getMachineProfile(addresses[i]);
      if (p == null) {
        out.println("Machine was not found in the network schema.");
        out.println("Node: " + addresses[i].displayString());
        return;
      }
      if(!p.isRoot()) {
        out.println("Machine is not a root.");
        out.println("Node: " + addresses[i].displayString());
        return;
      }
    }

    PathInfo path_info = network.getPathInfoForPath(path_name);
    if (path_info != null) {
      out.println("A path with the same name already exists.");
      return;
    }

    out.println("Adding path " + consensus + " " + path_name + ".");
    out.print("Path Info ");
    out.println("Leader: " + path_location.getRootLeader().displayString());
    out.println(" Replicas:");
    for (int i = 0; i < addresses.length; ++i) {
      out.print("  ");
      out.println(addresses[i].displayString());
    }
    out.println();

    // Add the path,
    network.addPathToNetwork(path_name, consensus, addresses[0], addresses);

  }

  /**
   * Scans the MckoiDDB network for any errors.
   */
  public void checkNetwork(StyledPrintWriter out)
                                   throws IOException, NetworkAdminException {
    NetworkAccess network = getNetworkProfile();
    AdminInterpreter.checkBlockStatus(out, network);
  }

  /**
   * Fixes any block availability issues in the MckoiDDB network.
   */
  public void fixBlockAvailability(StyledPrintWriter out)
                                   throws IOException, NetworkAdminException {
    NetworkAccess network = getNetworkProfile();
    AdminInterpreter.fixBlockAvailability(out, network);
  }

  /**
   * Rolls back a path be the given time frame or time stamp. time_string can
   * be either a frame of time (eg. '2 days') or a specific date/time string.
   */
  public void rollbackPath(StyledPrintWriter out,
                           String path_name, String time_string) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /**
   * Shows information about the network.
   */
  public void showDDBNetwork(StyledPrintWriter out) {
    
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /**
   * Shows the status of the network according to the manager server.
   */
  public void showStatus(StyledPrintWriter out)
                                   throws IOException, NetworkAdminException {
    NetworkAccess network = getNetworkProfile();
    AdminInterpreter.showStatus(out, network);
  }

  /**
   * Shows information about the resources available on all the servers in
   * the network.
   */
  public void showFree(StyledPrintWriter out)
                                   throws IOException, NetworkAdminException {
    NetworkAccess network = getNetworkProfile();
    AdminInterpreter.showFree(out, network);
  }

  /**
   * Shows information about the paths in the network.
   */
  public void showPaths(StyledPrintWriter out)
                                   throws IOException, NetworkAdminException {
    // NOTE: should we read this information out of the MWP database?
    NetworkAccess network = getNetworkProfile();
    AdminInterpreter.showPaths(out, network);
  }




  /**
   * A runtime exception for an invalid path location.
   */
  public static class InvalidPathLocationException extends RuntimeException {
    public InvalidPathLocationException(Throwable cause) {
      super(cause);
    }
    public InvalidPathLocationException(String message, Throwable cause) {
      super(message, cause);
    }
    public InvalidPathLocationException(String message) {
      super(message);
    }
    public InvalidPathLocationException() {
    }
  }


  // ----- Display -----

  /**
   * Parses the JSON account value.
   */
  public JSONObject parseAccountValue(String acc_value) {
    if (acc_value != null && acc_value.length() > 0) {
      try {
        JSONTokener json_tok = new JSONTokener(acc_value);
        JSONObject val_ob = (JSONObject) json_tok.nextValue();
        return val_ob;
      }
      catch (JSONException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  /**
   * Shows a summary of all the accounts.
   */
  public void showAccounts(StyledPrintWriter out) {
    // Create a session for the system path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The accounts object,
    ODBObject accounts_ob = t.getNamedItem("accounts");
    ODBList all_accounts = accounts_ob.getList("accountIdx");
    for (ODBObject account : all_accounts) {
      String acc_value = account.getString("value");
      String account_name = account.getString("account");
      JSONObject val_ob = parseAccountValue(acc_value);
      String global_priv = "normal";
      if (val_ob != null) {
        global_priv = val_ob.optString("globalpriv");
      }
      out.print(account_name);
      if (global_priv.equals("superuser")) {
        out.println(" *SUPERUSER*");
      }
      else {
        out.println();
      }
    }
  }

  /**
   * Shows an account summary.
   */
  public void showAccount(StyledPrintWriter out, String account_name) {
    // Create a session for the system path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The accounts object,
    ODBObject accounts_ob = t.getNamedItem("accounts");
    ODBList all_accounts = accounts_ob.getList("accountIdx");
    ODBObject account = all_accounts.getObject(account_name);
    if (account == null) {
      out.println("Account not found.");
    }
    else {
      String acc_value = account.getString("value");
      ODBList vhost_list = account.getList("vhost_list");
      ODBList resources = account.getList("resource_list");

      boolean superuser = false;
      
      JSONObject val_ob = parseAccountValue(acc_value);
      if (val_ob != null) {
        String global_priv = val_ob.optString("globalpriv");
        if (global_priv.equals("superuser")) {
          superuser = true;
        }
      }

      out.println("Summary of " + account_name);
      if (superuser) {
        out.println("  * SUPERUSER *");
      }

      out.println("VHosts:");
      for (ODBObject vhost : vhost_list) {
        String domain = vhost.getString("domain");
        out.println("  " + domain);
      }
      out.println("Resources:");
      for (ODBObject resource : resources) {
        String res_path = resource.getString("path");
        String res_name = resource.getString("resource_name");
        String res_type = resource.getString("type");
        out.println("  '" + res_name + "' " + res_path + "(" + res_type + ")");
      }
    }
  }
  
  /**
   * Shows all the vhosts and the accounts they are associated to.
   */
  public void showVHosts(StyledPrintWriter out) {
    // Create a session for the system path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();
    
    ODBObject vhosts_index = t.getNamedItem("vhosts");
    ODBList domains = vhosts_index.getList("domainIdx");
    
    for (ODBObject domain : domains) {
      String domain_name = domain.getString("domain");
      ODBObject account = domain.getObject("account");
      String account_name = account.getString("account");
      
      out.println(domain_name + " -> " + account_name);
    }
    
  }

  /**
   * Displays a summary of the current installs, and their version.
   */
  public void showInstalls(StyledPrintWriter out) throws IOException {
    // Create a session for the system path,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    // The file system,
    FileSystem file_sys = new FileSystemImpl(t, "fs");

    // The sub-directories of the installs directory represent all the
    // available installs.
    List<FileInfo> installs = file_sys.getSubDirectoryList("/installs/");
    if (installs == null) {
      // No installs directory,
    }
    else {
      DateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      for (FileInfo install : installs) {
        // Get the last version of this install
        List<FileInfo> versions =
                       file_sys.getSubDirectoryList(install.getAbsoluteName());
        int versions_sz = versions.size();
        long latest_timestamp = -1;
        String latest_version_str = null;
        if (versions_sz > 0) {
          FileInfo latest_version = versions.get(versions_sz - 1);
          latest_timestamp = latest_version.getLastModified();
          latest_version_str = latest_version.getItemName();
        }
        out.println(install.getItemName());
        if (latest_timestamp < 0) {
          out.println("  No versions available.");
        }
        else {
          out.println("  Latest version: " +
                      sdf.format(new Date(latest_timestamp)) +
                      " (" + latest_version_str + ")");
        }
      }
    }

    String current_deploy = "None";
    String current_install_txt =
                       AppServiceNode.getFileAsString(file_sys, "/curinstall");

    if (current_install_txt != null) {
      current_deploy = current_install_txt;
    }

    out.println("Current Deploy: " + current_deploy);

  }
  
  /**
   * Displays a summary of all the servers registered in the system.
   */
  public void showServers(StyledPrintWriter out, String sort_by) throws IOException {
    
    String sort_index = "nameIdx";
    if (sort_by != null) {
      if (sort_by.equals("publicip")) {
        sort_index = "publicipIdx";
      }
      else if (sort_by.equals("privateip")) {
        sort_index = "privateipIdx";
      }
    }

    // Create a session for the 'sysplatform' path instance,
    ODBSession session = getSession(SystemStatics.SYSTEM_PATH);

    // Open a transaction
    ODBTransaction t = session.createTransaction();

    ODBObject servers = t.getNamedItem("servers");
    ODBList server_list = servers.getList(sort_index);
    for (ODBObject server_ob : server_list) {
      String private_ip_str =
              ServerRolesSchema.formatIpAddr(server_ob.getString("privateip"));
      String public_ip_str =
              ServerRolesSchema.formatIpAddr(server_ob.getString("publicip"));
      
      out.println(server_ob.getString("name"));
      out.println("  Public:  " + public_ip_str);
      out.println("  Private: " + private_ip_str);
    }

  }
  
  


  public static String argsToString(List<String> args, int pos) {
    StringBuilder b = new StringBuilder();
    int sz = args.size();
    for (int i = pos; i < sz - 1; ++i) {
      b.append(args.get(i));
      b.append(" ");
    }
    b.append(args.get(sz - 1));
    return b.toString();
  }
  
  /**
   * Parses a path location. The expected format is;
   *   address:port (address:port, address:port, ...)
   */
  public static PathLocation parsePathLocation(String path_string)
                             throws IOException, InvalidPathLocationException {
    
    // Path location with just 1 root server,
    Pattern single_root_server = Pattern.compile(
          "[,\\s]*([^\\s,]+:[\\d]+)");
    Pattern multi_root_servers = Pattern.compile(
          "[,\\s]*([^\\s,]+:[\\d]+)\\s+\\[(([,\\s]*[^\\s,]+:[\\d]+)+\\s*)\\]");

    String root_leader = null;
    List<String> root_servers = new ArrayList();

    {
      Matcher single_m = single_root_server.matcher(path_string.trim());
      
      if (single_m.matches()) {
        root_leader = single_m.group(1);
        root_servers.add(single_m.group(1));
      }
      
    }

    {
      Matcher multi_m = multi_root_servers.matcher(path_string.trim());
      if (multi_m.matches()) {

        root_leader = multi_m.group(1);
        // The servers list,
        String servers_list = multi_m.group(2);

        servers_list = servers_list.trim();
        Matcher single_m = single_root_server.matcher(servers_list);
        while (single_m.lookingAt()) {

          root_servers.add(single_m.group(1));
          
          // Move to next,
          servers_list = servers_list.substring(single_m.end()).trim();
          single_m = single_root_server.matcher(servers_list);
        }
        
        if (servers_list.length() > 0) {
          throw new InvalidPathLocationException(
                                             "Root servers list parse error");
        }

      }
    }

    // Parse the details,
    if (root_leader != null) {
      ServiceAddress rleader_addr = ServiceAddress.parseString(root_leader);
      ServiceAddress[] root_servers_arr =
                                       new ServiceAddress[root_servers.size()];

      int i = 0;
      for (String server : root_servers) {
        root_servers_arr[i] = ServiceAddress.parseString(server);
        ++i;
      }

      // Check the root leader is in the root servers list,
      boolean root_server_present = false;
      for (ServiceAddress addr : root_servers_arr) {
        if (rleader_addr.equals(addr)) {
          root_server_present = true;
        }
      }

      if (!root_server_present) {
        throw new InvalidPathLocationException(
                   "Root leader (" + root_leader + ") not found in root server list");
      }

      // Check there's no duplicates,
      Set<ServiceAddress> bset = new HashSet();
      for (ServiceAddress addr : root_servers_arr) {
        if (!bset.contains(addr)) {
          bset.add(addr);
        }
        else {
          throw new InvalidPathLocationException(
                  "Duplicate root server (" + addr + ") found in server list");
        }
      }

      return new PathLocation(rleader_addr, root_servers_arr);

    }
    else {
      throw new InvalidPathLocationException("Path location parse error");
    }

  }

//  /**
//   * Parses a list of vhost definitions (eg. "www.example.com:http,
//   * example.com:https", etc).
//   */
//  public static List<String> parseVHostsList(String vhosts) {
//
//    List<String> vhosts_out = new ArrayList();
//
//    // A vhost definition
//    Pattern single_vhost = Pattern.compile(
//          "[,\\s]*([^\\s,]+:(http|https|\\*))");
//
//    vhosts = vhosts.trim();
//    Matcher single_m = single_vhost.matcher(vhosts);
//    while (single_m.lookingAt()) {
//
//      String vhost = single_m.group(1);
//      // Handle :* to include both http and https,
//      if (vhost.endsWith(":*")) {
//        int delim = vhost.lastIndexOf(":");
//        vhosts_out.add(vhost.substring(0, delim) + ":http");
//        vhosts_out.add(vhost.substring(0, delim) + ":https");
//      }
//      else {
//        vhosts_out.add(vhost);
//      }
//
//      // Move to next,
//      vhosts = vhosts.substring(single_m.end()).trim();
//      single_m = single_vhost.matcher(vhosts);
//    }
//
//    if (vhosts.length() > 0) {
//      throw new RuntimeException("VHosts list parse error");
//    }
//
//    return vhosts_out;
//  }

//  /**
//   * Returns true if the command matches.
//   */
//  public static boolean commandEquals(List<String> command, String str) {
//    String[] in_cmds = str.split(" ");
//    List<String> in_cmds_list = new ArrayList();
//    for (String in_cmd : in_cmds) {
//      if (!in_cmd.equals("")) {
//        in_cmds_list.add(in_cmd);
//      }
//    }
//    if (command.size() >= in_cmds_list.size()) {
//      int sz = in_cmds_list.size();
//      for (int i = 0; i < sz; ++i) {
//        // If it's not a match,
//        if (!command.get(i).equals(in_cmds_list.get(i))) {
//          return false;
//        }
//      }
//      return true;
//    }
//    return false;
//  }

  /**
   * Command line invocation point.
   */
  public static void main(String[] args) {

    // Set the default newline string,
    System.setProperty("line.separator", "\n");

    // Only display severe error messages,
    Logger logger = Logger.getLogger("com.mckoi.network.Log");
    logger.setLevel(Level.SEVERE);

    StyledPrintWriter out = new IOWrapStyledPrintWriter(System.out);

    try {
      CommandLine command_line = new CommandLine(args);
      boolean failed = false;

      File home_path = new File(".").getCanonicalFile();

      String network_conf_value;
      String client_conf_arg;

      try {
        String netconf_info_val = command_line.switchArgument("-netconfinfo");
        String network_conf_val = command_line.switchArgument("-netconfig");

        // If 'netconfinfo' switch is found then we fetch the
        // 'netconf_location' property from the file and use that as the
        // network.conf location.
        if (netconf_info_val != null) {
          // Read the 'netconf_location' property from the netconfinfo file,
          Properties nci = new Properties();
          FileReader r = new FileReader(new File(netconf_info_val));
          nci.load(r);
          network_conf_value = nci.getProperty("netconf_location");
          r.close();
        }
        else if (network_conf_val != null) {
          network_conf_value = network_conf_val;
        }
        else {
          // Both null, so default,
          network_conf_value = "network.conf";
        }

        client_conf_arg =
                    command_line.switchArgument("-client", "client.conf");
      }
      catch (Throwable e) {
        System.out.println("Error parsing arguments.");
        return;
      }

      // Parse args,
      File client_conf_file = AppServiceNode.toFile(home_path, client_conf_arg);

      URL network_conf_url =
                       AppServiceNode.parseToURL(home_path, network_conf_value);

      // Make the MckoiDDBClient,
      MckoiDDBClient client = MckoiDDBClientUtils.connectTCP(client_conf_file);
      // The network resource,
      NetworkConfigResource network_resource =
                          NetworkConfigResource.getNetConfig(network_conf_url);
      // Get the network profile and set the network conf for it,
      NetworkProfile network_profile = client.getNetworkProfile(null);
      network_profile.setNetworkConfiguration(network_resource);

      // Extract the commands from the command line,
      List<String> commands = new ArrayList();
      boolean skip_next = false;
      for (String arg : args) {
        if (!skip_next) {
          if (arg.startsWith("-")) {
            if (arg.equals("-netconfig") ||
                arg.equals("-netconfinfo") ||
                arg.equals("-client")) {
              skip_next = true;
            }
          }
          else {
            commands.add(arg);
          }
        }
        else {
          skip_next = false;
        }
      }

      // Create the AppCoreAdmin object,
      AppCoreAdmin app_core_admin = new AppCoreAdmin(client, network_profile);

      // Create a CommandProcessor
      CommandProcessor command_processor = new CommandProcessor(app_core_admin);

      // Process a command,
      command_processor.processCommand(out, commands);

    }
    catch (Exception e) {
      // Report exception,
      e.printStackTrace(System.err);
    }
    finally {
      out.flush();
    }

  }

}
