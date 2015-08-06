/**
 * com.mckoi.mwpcore.MckoiDDBAppPolicy  May 31, 2010
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

import com.mckoi.webplatform.util.HttpUtils;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.security.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

/**
 * 
 *
 * @author Tobias Downer
 */

final class MckoiDDBAppPolicy extends Policy {

  /**
   * The list of code source types to permission collection name.
   */
  private final ArrayList<CodeSourceMap> code_source_list;
  
  /**
   * The map from code source type to permission collection.
   */
  private final HashMap<String, PermissionCollection> permissions_map;

  private boolean policy_loaded = false;

  private static MckoiDDBAppPolicy current_instance;


  /**
   * Constructor.
   */
  MckoiDDBAppPolicy() {
    this.code_source_list = new ArrayList();
    this.permissions_map = new HashMap();
  }


  void setThisAsCurrentMckoiPolicy() {
    if (current_instance == null) {
      current_instance = this;
    }
  }

  static MckoiDDBAppPolicy getCurrentMckoiPolicy() {
    return current_instance;
  }

  /**
   * Returns the user level permissions.
   */
  PermissionCollection userLevelPermissions() {
    PermissionCollection c = permissions_map.get("USER");
    if (c != null) {
      return c;
    }
    throw new RuntimeException("USER permissions not defined in policy");
  }

  /**
   * Returns the system level permissions.
   */
  private PermissionCollection systemLevelPermissions() {
    PermissionCollection c = permissions_map.get("SYSTEM");
    if (c != null) {
      return c;
    }
    throw new RuntimeException("SYSTEM permissions not defined in policy");
  }



  /**
   * Load the policy from the file at the given URL. In addition, sets some
   * required system permissions such as granting read permission to system
   * paths.
   */
  void load(URL policy_file_url,
            String local_base_directory,
            String local_install_directory) throws IOException {
    if (policy_loaded) {
      throw new RuntimeException("Policy already loaded");
    }
    policy_loaded = true;

    URLConnection c = policy_file_url.openConnection();
    c.connect();
    InputStream instream = c.getInputStream();
    Reader reader = new InputStreamReader(instream, "UTF-8");

    // Interpret the policy file,
    PolicyInterpreter i = new PolicyInterpreter(reader,
                                local_base_directory, local_install_directory);
    i.parse();

  }



  /**
   * Handles special case inherits when parsing a GRANT. For example, the
   * following will inherit file read permissions for the java extentions
   * directory;
   * <pre>
   *   grant "DEF_JAVA_RESOURCE_PRIVS" {
   *     permission java.io.FilePermission "${java.home}/-", "read";
   *     permission java.io.FilePermission "${jdk.home}/lib/tools.jar", "read";
   *
   *    // This inherits java extension file permissions read from the
   *    // 'java.ext.dir' system property.
   *    inherit %java_ext_file_permissions;
   *  }
   * </pre>
   * 
   * @param special_code
   * @param permissions
   * @return 
   */
  private boolean addSpecialCasePermissions(
                                String special_code, Permissions permissions) {

    if (special_code.equals("%java_ext_file_permissions")) {

      // We grant read permission to paths in the java.ext.dirs system property.
      //
      // NOTE: There's a bug in Java 7 and 8 where 'System.loadLibrary' uses a
      //   URL encoded string to check if ext libraries exist. This causes a
      //   security exception if 'java.ext.dirs' contains files with characters
      //   that are encoded with % escape sequences (eg. whitespace is encoded
      //   as %20 so this is the literal path checked).
      //
      //   We work around this by granting read permission to the URL encoded
      //   representation.

      String ext_dirs = System.getProperty("java.ext.dirs");
      if (ext_dirs != null) {

        StringTokenizer tok = new StringTokenizer(ext_dirs, File.pathSeparator);
        while (tok.hasMoreTokens()) {
          String dir = tok.nextToken();
          if (!dir.endsWith(File.separator)) {
            dir = dir + File.separator;
          }
          Permission p = new FilePermission(dir + "-", "read");
          if (!permissions.implies(p)) {
//            System.out.println("ADDED SYSTEM PERMISSION: " + p);
            permissions.add(p);
          }
          // The permission to grant to the URL encoded version,
          try {
            String bug_workaround_file = new File(dir).toURI().toURL().getPath();
            File dir_file = new File(bug_workaround_file);
            bug_workaround_file = dir_file.getAbsolutePath();
            if (!bug_workaround_file.endsWith(File.separator)) {
              bug_workaround_file += File.separator;
            }
            p = new FilePermission(bug_workaround_file + "-", "read");
            if (!permissions.implies(p)) {
//              System.out.println("ADDED SYSTEM PERMISSION: " + p);
              permissions.add(p);
            }
          }
          catch (MalformedURLException ex) {
            // Ignore,
          }
        }
      }
      return true;
    }
    else {
      return false;
    }

  }



  @Override
  public Parameters getParameters() {
    return super.getParameters();
  }

  @Override
  public PermissionCollection getPermissions(CodeSource codesource) {
    // If null code source,
    if (codesource == null) {
      // This is from a bootstrap loader,
      return new WritablePermissionCollection(systemLevelPermissions());
    }

    URL url = codesource.getLocation();
    if (url == null) {
      // Odd, but this will happen when ClassLoader define class used with
      //   a null protect domain.
      return new WritablePermissionCollection(userLevelPermissions());
    }
    String protocol = url.getProtocol();
//    String path = url.getPath();
    String path;
    try {
      path = URLDecoder.decode(url.getPath(), "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }

    // Look for a match,
    String match_name = null;
    for (int i = 0; i < code_source_list.size() && match_name == null; ++i) {
      CodeSourceMap cs_item = code_source_list.get(i);
      if (cs_item.protocol.equals(protocol)) {
        String file_expr = cs_item.file_expression;

        if (file_expr.endsWith("-")) {
          String f_part = file_expr.substring(0, file_expr.length() - 1);
          if (path.startsWith(f_part)) {
            match_name = cs_item.name;
          }
        }
        else if (file_expr.endsWith("*")) {
          String f_part = file_expr.substring(0, file_expr.length() - 1);
          if (path.startsWith(f_part)) {
            if (path.substring(f_part.length()).indexOf("/") == -1) {
              match_name = cs_item.name;
            }
          }
        }
        else {
          if (path.equals(file_expr)) {
            match_name = cs_item.name;
          }
        }
      }
    }

    // Match found?
    if (match_name != null) {
//      System.err.println("Using " + match_name + " for " + path);
      PermissionCollection collection = permissions_map.get(match_name);
//      System.err.println(collection);
      return new WritablePermissionCollection(collection);
    }
    else {
      System.err.println("No CodeSource match for: " + protocol + ":" + path);
      // Match not found, so return a user level permissions object.
      return new WritablePermissionCollection(userLevelPermissions());
    }
  }

  @Override
  public PermissionCollection getPermissions(final ProtectionDomain domain) {
    CodeSource code_source = domain.getCodeSource();
    if (code_source == null) {
      // If null code_source, return a user level permissions object.
      return new WritablePermissionCollection(userLevelPermissions());
    }
    else {
      return getPermissions(code_source);
    }
  }

  @Override
  public Provider getProvider() {
    return super.getProvider();
  }

  @Override
  public String getType() {
    return super.getType();
  }

  @Override
  public boolean implies(ProtectionDomain domain, Permission permission) {
    PermissionCollection pc;
    if (domain == null) {
      pc = new Permissions();
    }
    else {
      pc = getPermissions(domain);
    }
    boolean b = pc.implies(permission);
//    if (b == false) {
//      System.out.println("Failed Permission: " + permission);
//    }
    return b;
  }

  @Override
  public void refresh() {
    super.refresh();
  }



  // ----- Inner classes ------

  private static class CodeSourceMap {

    private final String protocol;
    private final String file_expression;
    private final String name;

    private CodeSourceMap(String protocol, String file_expression,
                          String name) {
      this.protocol = protocol;
      this.file_expression = file_expression;
      this.name = name;
    }

    public String toString() {
      return name + " " + protocol + " : " + file_expression;
    }

  }

  private static class WritablePermissionCollection
                                                extends PermissionCollection {
    
    private final PermissionCollection backed;
    private PermissionCollection overwrite;
    
    private WritablePermissionCollection(PermissionCollection c) {
      this.backed = c;
      overwrite = null;
    }

    @Override
    public void add(Permission permission) {
      if (overwrite == null) {
        overwrite = new Permissions();
      }
      overwrite.add(permission);
    }

    @Override
    public boolean implies(Permission permission) {
      if (overwrite != null) {
        boolean test = overwrite.implies(permission);
        if (test) {
          return true;
        }
      }
      return backed.implies(permission);
    }

    @Override
    public Enumeration<Permission> elements() {
      if (overwrite == null) {
        return backed.elements();
      }
      ArrayList<Permission> arr = new ArrayList();
      Enumeration<Permission> e = backed.elements();
      while (e.hasMoreElements()) {
        arr.add(e.nextElement());
      }
      e = overwrite.elements();
      while (e.hasMoreElements()) {
        arr.add(e.nextElement());
      }
      return Collections.enumeration(arr);
    }

  }


  private class PolicyInterpreter {

    private final StreamTokenizer t;
    private final String JAVA_HOME;
    private final String JDK_HOME;
    private final String JAVA_TEMP;
    private final String LOCAL_BASE;
    private final String INSTALL_BASE;

    
    private String normalizeFile(String file) throws IOException {
      return HttpUtils.decodeURLFileName(
                          new File(file).getCanonicalFile().toURI().toURL());
    }
    
    private PolicyInterpreter(Reader reader,
                              String local_base_dir,
                              String local_install_dir) throws IOException {
      t = new StreamTokenizer(reader);
      t.slashSlashComments(true);
      t.slashStarComments(true);

//      String home_dir =
//              new File(local_base_dir).getCanonicalFile().toURI().getRawPath();
      String home_dir = normalizeFile(local_base_dir);
      if (home_dir.endsWith("/")) {
        home_dir = home_dir.substring(0, home_dir.length() - 1);
      }
      LOCAL_BASE = home_dir;

//      String install_dir =
//           new File(local_install_dir).getCanonicalFile().toURI().getRawPath();
      String install_dir = normalizeFile(local_install_dir);
      if (install_dir.endsWith("/")) {
        install_dir = install_dir.substring(0, install_dir.length() - 1);
      }
      INSTALL_BASE = install_dir;

//      String javahome_dir = new File(System.getProperty("java.home")).
//                                       getCanonicalFile().toURI().getRawPath();
      String javahome_dir = normalizeFile(System.getProperty("java.home"));

      if (javahome_dir.endsWith("/")) {
        javahome_dir = javahome_dir.substring(0, javahome_dir.length() - 1);
      }
      JAVA_HOME = javahome_dir;
      
      int delim = javahome_dir.lastIndexOf("/");
      if (delim >= 0) {
        JDK_HOME = javahome_dir.substring(0, delim);
      }
      else {
        JDK_HOME = "";
      }

//      String java_temp_dir = new File(System.getProperty("java.io.tmpdir")).
//                                       getCanonicalFile().toURI().getRawPath();
      String java_temp_dir = normalizeFile(System.getProperty("java.io.tmpdir"));
      if (java_temp_dir.endsWith("/")) {
        java_temp_dir = java_temp_dir.substring(0, java_temp_dir.length() - 1);
      }
      JAVA_TEMP = java_temp_dir;

      System.out.println("Security policy system properties:");
      System.out.println("  ${java.home} = " + JAVA_HOME);
      System.out.println("  ${jdk.home} = " + JDK_HOME);
      System.out.println("  ${java.io.tmpdir} = " + JAVA_TEMP);
      System.out.println("  ${mckoi.base} = " + LOCAL_BASE);
      System.out.println("  ${mckoi.install} = " + INSTALL_BASE);
      System.out.println();
    }

    private RuntimeException parseException(String msg) {
      return new RuntimeException("Line " + t.lineno() + ": " + msg);
    }

    private String propertySubstitute(String expr, String from, String to) {
      int p = expr.indexOf(from);
      if (p != -1) {
        String st = expr.substring(0, p);
        String end = expr.substring(p + from.length());
        expr = st + to + end;
      }
      return expr;
    }

    private String propertySubstitute(String expr) {
      expr = propertySubstitute(expr, "${mckoi.base}", LOCAL_BASE);
      expr = propertySubstitute(expr, "${mckoi.install}", INSTALL_BASE);
      expr = propertySubstitute(expr, "${java.io.tmpdir}", JAVA_TEMP);
      expr = propertySubstitute(expr, "${jdk.home}", JDK_HOME);
      expr = propertySubstitute(expr, "${java.home}", JAVA_HOME);
      return expr;
    }


    private String next() throws IOException {
      int ttype = t.nextToken();
      if (ttype == StreamTokenizer.TT_EOF) {
        throw parseException("Unexpected EOF");
      }
      else if (ttype == StreamTokenizer.TT_WORD) {
        return t.sval;
      }
      else if (ttype == '\"') {
        return t.sval;
      }
      else if (ttype == StreamTokenizer.TT_NUMBER) {
        return Double.toString(t.nval);
      }
      else if (ttype == StreamTokenizer.TT_EOL) {
        throw parseException("Unexpected EOL");
      }
      else {
        return Character.toString((char) t.ttype);
      }
    }

    private void consume(String tok) throws IOException {
      String val = next();
      if (val.equals(tok)) {
        return;
      }
      throw parseException("Expected: " + tok);
    }

    private void parseDefine() throws IOException {
      consume("codesource");
      String code_source_name = next();
      consume("{");
      while (true) {
        // We either consume a '}' indicating end of define, of code source
        // protocol.
        String c = next();
        if (c.equals("}")) {
          // Done.
          return;
        }

        // Protocol,
        String cs_protocol = c;
        // file expression,
        String cs_location_expr = next();
        // Ending token,
        consume(";");

        cs_location_expr = propertySubstitute(cs_location_expr);

        // Update the list,
        CodeSourceMap cs_map = new CodeSourceMap(
                            cs_protocol, cs_location_expr, code_source_name);
        code_source_list.add(cs_map);

      }
    }

    private void parseGrant() throws IOException {
      String code_source_name = next();
      Permissions permissions = new Permissions();

      consume("{");
      while (true) {
        String c = next();
        if (c.equals("}")) {
          // Done.
          break;
        }

        // If it's a 'permission'
        if (c.equals("permission")) {

          String permission_class = next();
          String permission_name = next();
          permission_name = propertySubstitute(permission_name);

          String permission_action = next();
          if (permission_action.equals(",")) {
            permission_action = next();
            consume(";");
          }
          else if (permission_action.equals(";")) {
            permission_action = null;
          }
          else {
            consume(";");
          }

          try {
            // Create the permission,
            Class permission_c = Class.forName(permission_class);
            Constructor[] constructors = permission_c.getConstructors();
            int cargs = permission_action == null ? 1 : 2;
            Constructor best_constructor = null;
            for (Constructor cl : constructors) {
              Class[] args = cl.getParameterTypes();
              if (args.length == cargs) {
                best_constructor = cl;
              }
            }

            if (best_constructor == null) {
              throw parseException(
                        "Constructor for " + permission_class + " not found.");
            }

            // -
            Permission permission;
            if (cargs == 1) {
              permission =
                      (Permission) best_constructor.newInstance(permission_name);
            }
            else if (cargs == 2) {
              permission = (Permission) best_constructor.newInstance(
                                            permission_name, permission_action);
            }
            else {
              // Shouldn't happen
              throw new RuntimeException();
            }

            // Add to the list of permissions,
            permissions.add(permission);

          }
          catch (InvocationTargetException e) {
            throw new RuntimeException(e);
          }
          catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
          catch (InstantiationException e) {
            throw new RuntimeException(e);
          }
          catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }

        }
        
        // If it's 'inherit'
        else if (c.equals("inherit")) {
          // Inherit the permissions previously defined,
          String inherit_code_source = next();
          consume(";");

          if (inherit_code_source == null) {
            throw parseException(
                    "Grant to be inherited can not be found: " +
                    inherit_code_source);
          }

          // Special system level permissions,
          if (inherit_code_source.startsWith("%")) {
            boolean added =
                    addSpecialCasePermissions(inherit_code_source, permissions);
            if (!added) {
              throw parseException(
                      "Don't know how to handle special permissions: " +
                      inherit_code_source);
            }
          }
          else {

            PermissionCollection inherit_permissions =
                                       permissions_map.get(inherit_code_source);

            if (inherit_permissions == null) {
              throw parseException(
                      "Grant to be inherited can not be found: " +
                      inherit_code_source);
            }

            // Add these permissions,
            Enumeration<Permission> e = inherit_permissions.elements();
            while (e.hasMoreElements()) {
              Permission rp = e.nextElement();
              // Add the permission if the permissions doesn't imply it,
              if (!permissions.implies(rp)) {
                permissions.add(rp);
              }
            }

          }

        }

        else {
          throw parseException("Expected: 'permission' or 'inherit'");
        }

        // Continue processing,
      }

      // Create the mapping,
      permissions.setReadOnly();
      permissions_map.put(code_source_name, permissions);

    }

    private void parse() throws IOException {
      while (true) {
        t.nextToken();
        if (t.ttype == StreamTokenizer.TT_EOF) {
          // Done.
          break;
        }

        // Either 'define' or 'grant'
        if (t.sval.equals("define")) {
          parseDefine();
        }
        else if (t.sval.equals("grant")) {
          parseGrant();
        }
        else {
          throw parseException("Unexpected: " + t.sval);
        }
      }
    }

  }

}
