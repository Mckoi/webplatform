/**
 * com.mckoi.appcore.ServerRolesSchema  Mar 16, 2012
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

import com.mckoi.odb.*;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileSystemImpl;
import com.mckoi.util.ByteArrayUtil;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Utility classes for managing the server roles in the system platform
 * schema.
 *
 * @author Tobias Downer
 */

public class ServerRolesSchema {

  /**
   * Creates the schema for server roles objects.
   */
  public static void defineSchema(ODBTransaction t)
                                              throws ClassValidationException {

    ODBClassCreator class_creator = t.getClassCreator();

    // The root object,
    class_creator.createClass("SR.ServerIndex")
       .defineString("version", true)
       .defineList("nameIdx", "SR.Server",
                   ODBOrderSpecification.lexicographic("name"), false)
       .defineList("publicipIdx", "SR.Server",
                   ODBOrderSpecification.lexicographic("publicip"), false)
       .defineList("privateipIdx", "SR.Server",
                   ODBOrderSpecification.lexicographic("privateip"), false)
       .defineString("value", true)
    ;

    // The Server object,
    class_creator.createClass("SR.Server")
       .defineString("name", false)
       .defineString("publicip", false)
       .defineString("privateip", false)
       .defineString("value", true)
    ;

    // The RolesIndex object
    class_creator.createClass("SR.RoleIndex")
       .defineList("serverIdx", "SR.Role",
                   ODBOrderSpecification.lexicographic("server"), true)
       .defineList("roleserverIdx", "SR.Role",
                   ODBOrderSpecification.lexicographic("roleserver"), false)
    ;

    // The Roles object,
    class_creator.createClass("SR.Role")
       .defineString("server", false)
       .defineString("roleserver", false)
    ;

    // Validate and complete the class assignments,
    class_creator.validateAndComplete();

  }

  /**
   * Initialize the server roles system.
   */
  public static void setup(ODBTransaction t) {

    {
      // Create the server root,
      ODBClass servers_class = t.findClass("SR.ServerIndex");

      // (version, nameIdx, publicipIdx, privateipIdx)
      ODBObject servers_ob = t.constructObject(servers_class,
              "1.0", null, null, null, "");

      // Create the named item,
      t.addNamedItem("servers", servers_ob);
    }

    {
      // Create the roles root,
      ODBClass roles_class = t.findClass("SR.RoleIndex");

      // (roleIdx)
      ODBObject roles_ob = t.constructObject(roles_class, null, null);

      // Create the named item,
      t.addNamedItem("roles", roles_ob);
    }

  }

//  /**
//   * Given a roles file, returns a list of roles specified in the file.
//   */
//  public static List<String> getRolesFromFile(DataFile roles_data)
//                                                           throws IOException {
//    ArrayList<String> roles_list = new ArrayList();
//    BufferedReader r = new BufferedReader( new InputStreamReader(
//                            DataFileUtils.asInputStream(roles_data), "UTF-8"));
//    while (true) {
//      String role_line = r.readLine();
//      if (role_line == null) {
//        break;
//      }
//      roles_list.add(role_line);
//    }
//    return roles_list;
//  }
//
//  /**
//   * Writes all the roles in the list to the given roles file.
//   */
//  public static void putRolesInFile(
//             List<String> roles_list, DataFile roles_data) throws IOException {
//
//    Writer w = new OutputStreamWriter(
//                            DataFileUtils.asOutputStream(roles_data), "UTF-8");
//    for (String role : roles_list) {
//      w.append(role);
//      w.append('\n');
//    }
//    w.flush();
//
//  }

  public static String canonicalIPString(InetAddress addr) throws IOException {
    byte[] b = addr.getAddress();
    byte[] ipv6addr = new byte[16];
    // If the address is ipv4,
    if (b.length == 4) {
      // Format the network address as an 16 byte ipv6 on ipv4 network address.
      // This creates an 'IPv4-Compatible IPv6 address' as per the IPv6 spec.
      ipv6addr[10] = (byte) 0x0FF;
      ipv6addr[11] = (byte) 0x0FF;
      ipv6addr[12] = b[0];
      ipv6addr[13] = b[1];
      ipv6addr[14] = b[2];
      ipv6addr[15] = b[3];
    }
    // If the address is ipv6
    else if (b.length == 16) {
      for (int i = 0; i < 16; ++i) {
        ipv6addr[i] = b[i];
      }
    }
    // Turn it into a fully qualified ipv6 address
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 16; i += 2) {
      int v = ((int) ByteArrayUtil.getShort(ipv6addr, i)) & 0x0FFFF;
      String n = Integer.toHexString(v).toUpperCase(Locale.ENGLISH);
      sb.append(n);
      if (i < 14) {
        sb.append(':');
      }
    }
    return sb.toString();
  }
  
  /**
   * Given an host address string (either a domain lookup or IP address),
   * returns a globally unique ip address string (fully expanded ipv6 form).
   */
  public static String canonicalIPString(String host_name_or_ip)
                                                           throws IOException {
    // Map the address into an InetAddress,
    try {
      InetAddress inet_addr = InetAddress.getByName(host_name_or_ip);
      return canonicalIPString(inet_addr);
    }
    // On Unknown host exception,
    catch (UnknownHostException e) {
      return null;
    }
  }

  /**
   * Formats an ipv6 address into a human understandable form.
   */
  public static String formatIpAddr(String ip_addr_str) throws IOException {
    InetAddress inet = InetAddress.getByName(ip_addr_str);
    return inet.getHostAddress();
  }

  /**
   * Given either a server name, a public ip address or a private ip address,
   * returns the Server object of the machine that has been registered with
   * the database (via 'registerServer'). Returns null if the machine location
   * is not found.
   */
  public static ODBObject lookupServer(ODBTransaction t,
                                String machine_location) throws IOException {

    ODBObject servers = t.getNamedItem("servers");
    ODBObject server_ob;
    // First treat it as a regular server name and look up based on that,
    ODBList server_name_idx = servers.getList("nameIdx");
    server_ob = server_name_idx.getObject(machine_location);
    if (server_ob == null) {
      // Convert to an ip address,
      String normalized_ip = canonicalIPString(machine_location);
      if (normalized_ip != null) {
        // Look up in the public ip index,
        ODBList public_ip_idx = servers.getList("publicipIdx");
        server_ob = public_ip_idx.getObject(normalized_ip);
        if (server_ob == null) {
          // Finally private ip lookup,
          ODBList private_ip_idx = servers.getList("privateipIdx");
          server_ob = private_ip_idx.getObject(normalized_ip);
        }
      }
    }

    return server_ob;
  }

  /**
   * Given either a server name, a public ip address or a private ip address,
   * returns the unique canonical public ip address string of the machine that
   * has been registered with the database (via 'registerServer'). Returns null
   * if the machine location is not found.
   */
  public static String lookupPublicIpOf(ODBTransaction t,
                                 String machine_location) throws IOException {

    // Look up the server object,
    ODBObject server_ob = lookupServer(t, machine_location);

    // If not found,
    if (server_ob == null) {
      return null;
    }
    // Otherwise return the public ip address,
    return server_ob.getString("publicip");
  }

  /**
   * Adds a record to the system path that specifies the given server should
   * perform the given role. For example, role = http. Returns true if the
   * database changed (one or more roles are not being performed by the server).
   */
  public static boolean addServerRoles(ODBTransaction t,
                  ODBObject server_ob, List<String> roles) throws IOException {

    boolean changed = false;

    // The id to use to represent the server,
    String server_id = server_ob.getString("privateip");

    // Get the system platform file system,
    FileSystemImpl fs = new FileSystemImpl(t, "fs");

    // Make the server directory if it doesn't exist,
    String server_dir = "/servers/" + server_id + "/";
    // If it doesn't exist, create it,
    FileInfo finfo = fs.getFileInfo(server_dir);
    if (finfo == null) {
      fs.makeDirectory(server_dir);
      changed = true;
    }

    // Add to the roles db,
    ODBObject roles_ob = t.getNamedItem("roles");
    ODBList server_idx = roles_ob.getList("serverIdx");
    ODBList roleserver_idx = roles_ob.getList("roleserverIdx");
    // The role object,
    ODBClass role_class = t.findClass("SR.Role");
    for (String role : roles) {
      if (role.contains(".")) {
        throw new RuntimeException("role string contains invalid chars");
      }
      String roleserver_str = role + "." + server_id;
      // Do we not already contain this index?
      if (!roleserver_idx.contains(roleserver_str)) {
        // (server, roleserver)
        ODBObject role_ob =
                      t.constructObject(role_class, server_id, roleserver_str);
        // Add to the index,
        server_idx.add(role_ob);
        roleserver_idx.add(role_ob);
        changed = true;
      }
    }

    return changed;
  }

  /**
   * Removes the given roles from being performed by the server. If any of
   * the roles are not being performed by the server then returns false and
   * the database isn't changed.
   */
  public static boolean removeServerRoles(ODBTransaction t,
                  ODBObject server_ob, List<String> roles) throws IOException {

    boolean changed = false;

    // The id to use to represent the server,
    String server_id = server_ob.getString("privateip");

    // Remove from the roles db,
    ODBObject roles_ob = t.getNamedItem("roles");
    ODBList server_idx = roles_ob.getList("serverIdx");
    ODBList roleserver_idx = roles_ob.getList("roleserverIdx");

    // The role object,
    for (String role : roles) {
      if (role.contains(".")) {
        throw new RuntimeException("role string contains invalid chars");
      }
      String roleserver_str = role + "." + server_id;
      ODBObject role_ob = roleserver_idx.getObject(roleserver_str);
      // Do we contain this index?
      if (role_ob != null) {
        // Remove from the index,
        server_idx.remove(role_ob.getReference());
        roleserver_idx.remove(roleserver_str);
        changed = true;
      }
    }

    return changed;
  }

}
