/**
 * com.mckoi.appcore.NodeInstallTool  Aug 16, 2012
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

import com.mckoi.network.*;
import com.mckoi.util.IOWrapStyledPrintWriter;
import com.mckoi.util.StyledPrintWriter;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tool for making and configuring an installation of the Mckoi Web Platform
 * on a Unix or Windows platform. This is an interactive tool that asks the
 * user various questions about the system, and then creates a directory
 * together with relevant configuration files. After installation, instructions
 * are given for running the service.
 * <p>
 * This tool supports different installation modes. For example, it can be
 * used to set up a test installation with a single node on localhost.
 * <p>
 * This tool requires that a 'support' directory exists that contains files
 * that support the installation. The support directory must contain the
 * 'MWPMain' and 'MckoiDDB' .jar files.
 * <p>
 * The question tree looks something like the following;
 * 1. Do you wish to quickly create a test installation on localhost?
 *    No, go to 3.
 * 2. Is there a MckoiDDB instance already running on localhost?
 *    Yes, ask for details and proceed.
 *    No, Set up a test installation on localhost.
 * 3. To proceed, there must be an existing installation of MckoiDDB.
 *    Ask for the details to connect to the network (manager password, manager
 *    server(s), etc).
 * 4. Ask for;
 *      a) The public and private ip of this machine (checked).
 *      b) The unique name of the machine that can resolve through DNS.
 *      c) The machines we wish to be web servers, process servers, etc.
 *      d) Accounts we wish to setup.
 *
 * @author Tobias Downer
 */

public class NodeInstallTool {

  private Console console;
  private static final String newline = System.getProperty("line.separator");
  private static final String fmt = "%1$s";
  private static final String fmt_nl = "%1$s " + newline;

  private static final InetAddress EXACT_LOOPBACK;
  static {
    InetAddress addr = null;
    try {
      addr = InetAddress.getByName("127.0.0.1");
    }
    catch (UnknownHostException ex) {
      ex.printStackTrace(System.err);
    }
    EXACT_LOOPBACK = addr;
  }

  /**
   * The directory where the support files are located.
   */
  private File support_path = new File("support");

  // Verified support .jar files,
  private File verified_mwp_jar;
  private File verified_ddb_jar;
  private File verified_json_jar;

  // Verified support console webapp
  private File verified_console_webapp_war;

  // Support 'install_dev' directory,
  private File verified_install_dev_path;
  private File verified_security_path;
  private File verified_templates_path;

  private File found_jdk_directory;
  private String found_nodejs_executable;
  private String found_nodejs_version;

  // True if making a windows installation,
  private boolean is_win = false;
  // An example path
  private String ex_path = "/mckoiddb/";
  // An example JDK path
  private String ex_jdk_path = "/usr/lib/jvm/java-1.6.0-openjdk";
  // An example nodejs executable
  private String ex_nodejs_executable = "/usr/bin/node";
  // The operating system text (either 'Unix' or 'Windows')
  private String os_text;
  

  // Standard print and println reports.
  public void p(String msg) {
    console.format(fmt, msg);
  }
  public void pln(String msg) {
    console.format(fmt_nl, msg);
  }
  public void pln() {
    console.format(newline);
  }

  public StyledPrintWriter getPWriter() {
    return new IOWrapStyledPrintWriter(console.writer());
  }

  // Standard function call reports,
  public void fpln(String msg) {
    console.format(fmt_nl, msg);
  }
  public void fpln() {
    console.format(newline);
  }
  
  // Standard error reports,
  public void epln(String msg) {
    console.format(fmt_nl, msg);
  }
  public void epln() {
    console.format(newline);
  }

  /**
   * Makes a random hash code string of the given length.
   * 
   * @param len the length of the hash code string to generate, in characters.
   * @return 
   */
  public static String makeRandomHashCode(int len) {
    // Make a random hash code using SecureRandom,
    SecureRandom sr = new SecureRandom();
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < len; ++i) {
      int c = sr.nextInt(26);
      char ch = (char) ((int)'a' + c);
      if (sr.nextBoolean()) {
        ch = Character.toUpperCase(ch);
      }
      b.append(ch);
    }

    return b.toString();
  }
  
  
  /**
   * Searches the file array for a file that matches the given regular
   * expression.
   */
  private File findFile(File[] files, String reg_search) {
    Pattern p = Pattern.compile(reg_search);
    for (File f : files) {
      if (p.matcher(f.getName()).matches()) {
        return f;
      }
    }
    return null;
  }
  
  /**
   * Checks the support files exist.
   */
  private void checkSupportFiles() {
    
    String[] SUPPORT_EXPLAIN = new String[] {
      "This tool requires that the 'support/' directory contains the",
      "MckoiDDB, MWPMain and JSON .jar files. The distribution should",
      "have contained these files. Please check you are running this",
      "tool from the correct location." };
    
    if (!support_path.exists() ||
        !support_path.isDirectory()) {
      throw new NIException("The 'support/' directory does not exist.",
                            SUPPORT_EXPLAIN);
    }

    File[] files = support_path.listFiles();
    
    File mwp_jar = findFile(files, "MWPMain.*\\.jar");
    File ddb_jar = findFile(files, "MckoiDDB.*\\.jar");
    File json_jar = findFile(files, "json.*\\.jar");

    if (mwp_jar == null) {
      throw new NIException(
                 "Unable to find the MWPMain .jar file.", SUPPORT_EXPLAIN);
    }
    if (ddb_jar == null) {
      throw new NIException(
                 "Unable to find the MckoiDDB .jar file.", SUPPORT_EXPLAIN);
    }
    if (json_jar == null) {
      throw new NIException(
                 "Unable to find the JSON .jar file.", SUPPORT_EXPLAIN);
    }

    File console_webapp_path = new File(support_path, "console_webapp");
    File console_war = findFile(
                         console_webapp_path.listFiles(), "MWPUIApp.*\\.war");

    verified_mwp_jar = mwp_jar;
    verified_ddb_jar = ddb_jar;
    verified_json_jar = json_jar;

    verified_console_webapp_war = console_war;

    // The install_dev_path if it exists,
    verified_install_dev_path = new File(support_path, "install_dev");

    // The security path if it exists,
    verified_security_path = new File(support_path, "security");

    // The templates path if it exists,
    verified_templates_path = new File(support_path, "templates");

    fpln("Ok, found the support files.");
  }

  /**
   * Determines the JDK path, or sets the jdk path to 'null' if the location
   * is unknown.
   */
  private void determineJDKPath() {
    String sys_java_home = System.getProperty("java.home");
    File jh_file = new File(sys_java_home);
    if (isJDKDirectory(jh_file)) {
      found_jdk_directory = jh_file;
      return;
    }
    File jh_parent = jh_file.getParentFile();
    if (isJDKDirectory(jh_parent)) {
      found_jdk_directory = jh_parent;
      return;
    }

    // Otherwise look for 'jre' and substitute for 'jdk'
    String dir_name = jh_file.getName();
    String[] parts = dir_name.split("jre");
    if (parts.length == 2) {
      String alt_test_name = parts[0] + "jdk" + parts[1];
      File jh_jdk_name = new File(jh_parent, alt_test_name);
      if (isJDKDirectory(jh_jdk_name)) {
        found_jdk_directory = jh_jdk_name;
        return;
      }
    }

    // JDK directory not found :(

  }

  /**
   * Generalized OS command execution.
   */
  private List<String> executeCommand(String[] args) throws IOException {
    List<String> arglist = Arrays.asList(args);

    ProcessBuilder pb = new ProcessBuilder(arglist);
//    Map<String, String> env = pb.environment();
    Process p;

    p = pb.start();
    BufferedReader ins_reader = new BufferedReader(
            new InputStreamReader(p.getInputStream(), "UTF-8"));

    List<String> stdout_lines = new ArrayList<>();

    while (true) {
      String line = ins_reader.readLine();
      if (line == null) {
        break;
      }
      stdout_lines.add(line);
    }

    if (p.exitValue() != 0) {
      throw new IOException("Process terminated with exit code " + p.exitValue());
    }

    return stdout_lines;

  }

  /**
   * Determines the nodejs executable location, or sets the location to 'null'
   * if it's unknown.
   */
  private void determineNodeJSExecutableLocation() {

    // We test node is running by simply starting a 'node' process and see the
    // result. If it works then the node command becomes the executable.

    try {
      List<String> lines = executeCommand(new String[]{"node", "-v"});
      found_nodejs_executable = "node";
      found_nodejs_version = lines.get(0);
    }
    catch (IOException ex) {
      // Ignore this,
    }

    // Otherwise the user must specify the location of the node executable on
    // this machine.

  }

  /**
   * Parses a single machine address.
   */
  private ServiceAddress parseMachineAddress(String machine)
                                                          throws IOException {
    return ServiceAddress.parseString(machine);
  }

  /**
   * Ask a question with a yes/no/quit response. Quit will generate an
   * exception.
   */
  private boolean askYNQ(String question) {
    
    int count = 10;
    while (count > 0) {

      console.format(fmt, question);
      console.flush();
      String response = console.readLine();

      if (response != null && response.length() > 0) {
      
        response = response.toLowerCase(Locale.ENGLISH);

        // Yes
        if (response.startsWith("y")) {
          return true;
        }
        // No
        else if (response.startsWith("n")) {
          return false;
        }
        // Quit
        else if (response.startsWith("q")) {
          throw new QuitException();
        }

      }

      --count;

    }
    
    throw new NIException("Too many unknown responses given.");
    
  }

  /**
   * Ask for a string response, and validates the answer.
   */
  private String askString(String question, AnswerValidator validator) {

    int count = 10;
    while (count > 0) {

      console.format(fmt, question);
      console.flush();
      String response = console.readLine();

      // Is the response valid?
      if (validator == null || validator.isValidAnswer(response)) {
        return response;
      }

      --count;
    }

    throw new NIException("Too many invalid responses given.");

  }

  /**
   * Ask for a silent (password) string response, and validates the answer.
   */
  private String askPasswordString(String question, AnswerValidator validator) {

    int count = 10;
    while (count > 0) {

      console.format(fmt, question);
      console.flush();

      char[] resp_chars = console.readPassword();
      String response = String.valueOf(resp_chars);

      // Is the response valid?
      if (validator == null || validator.isValidAnswer(response)) {
        return response;
      }

      --count;
    }

    throw new NIException("Too many invalid responses given.");

  }

  /**
   * Ask a question with several options being the answer (the first string
   * details the available options).
   */
  public String askOpt(String options, String question) {
    
    int count = 10;
    while (count > 0) {

      console.format(fmt, question);
      console.flush();
      String response = console.readLine();

      if (response != null && response.length() > 0) {
      
        response = response.toLowerCase(Locale.ENGLISH);

        for (int i = 0; i < options.length(); ++i) {
          String opt_str = Character.toString(options.charAt(i));
          if (response.startsWith(opt_str)) {
            return opt_str;
          }
        }

        console.format(fmt_nl, "Invalid answer.");
        console.flush();

      }

      --count;
    }

    throw new NIException("Too many invalid responses given.");
    
  }

  /**
   * Returns true if the given path is a JDK directory (guesses by looking for
   * lib/tools.jar in the destination path).
   */
  private static boolean isJDKDirectory(File f) {
    // Check lib/tools.jar exists in this directory,
    File lib = new File(f, "lib");
    File toolsjar = new File(lib, "tools.jar");
    return (toolsjar.exists() && toolsjar.isFile());
  }

  /**
   * Returns true if the given file location is the nodejs executable.
   */
  private static boolean isNodeJsExecutableLocation(File f) {
    if (f.exists() && f.canExecute()) {
      String file_name = f.getName();
      return (file_name.equals("node") || file_name.equals("nodejs") ||
              file_name.equals("node.exe") || file_name.equals("nodejs.exe"));
    }
    return false;
  }

  /**
   * Create the given directory (throw exception if fail).
   */
  private void createDirectory(File install_path) throws IOException {
    String[] DIRECTORY_EXPLAIN = new String[] {
      "Failed to create the following directory:",
      "  " + install_path.getCanonicalPath() };
    boolean made_install_path = install_path.mkdirs();
    if (!made_install_path) {
      throw new NIException("Failed to create directory", DIRECTORY_EXPLAIN);
    }
  }

  /**
   * Copies the given file from the source to the destination (throw exception
   * if fail).
   */
  private void copyFile(File source, File destination) throws IOException {

    if (destination.exists()) {
      throw new NIException("Can't copy - destination file already exists: " +
                            destination.getCanonicalFile());
    }

    FileInputStream fin = new FileInputStream(source);
    FileOutputStream fout = new FileOutputStream(destination);

    byte[] buf = new byte[4096];
    while (true) {
      int read_count = fin.read(buf, 0, buf.length);
      // End reached,
      if (read_count == -1) {
        break;
      }
      fout.write(buf, 0, read_count);
    }

    // Flush and close the file,
    fout.flush();
    fout.close();

  }

  /**
   * Copies the given path from the source to the destination (throw exception
   * if fail).
   */
  private void copyPath(File source, File destination) throws IOException {

    if (destination.exists() && destination.isFile()) {
      throw new NIException("Can't copy - destination is a file: " +
                            destination.getCanonicalFile());
    }

    // Make sure the destination directory exists,
    destination.mkdirs();

    // For all files,
    File[] files = source.listFiles();
    for (File f : files) {

      // If it's a file, copy it,
      if (f.isFile()) {
        String file_name = f.getName();
        copyFile(f, new File(destination, file_name));
      }
      // If it's a directory, recurse,
      else if (f.isDirectory()) {
        String dir_name = f.getName();
        copyPath(f, new File(destination, dir_name));
      }

    }

  }

  private File installFile(HashMap properties, InputStream template_input_stream,
                           File path, String destination_name,
                           boolean escape_dash, boolean error_on_invalid_property)
                                                            throws IOException {

    Pattern var_pattern = Pattern.compile("\\$\\{([^\\}]+)\\}");

    String newline = System.getProperty("line.separator");

    InputStreamReader r = new InputStreamReader(template_input_stream, "UTF-8");

    BufferedReader br = new BufferedReader(r);
    StringBuilder b = new StringBuilder();
    while (true) {
      String line = br.readLine();

      if (line == null) {
        break;
      }

      StringBuilder out_line = new StringBuilder(line.length());

      while (true) {
        Matcher matcher = var_pattern.matcher(line);
        if (matcher.find()) {
          String var_name = matcher.group(1);

          String prop_value = (String) properties.get(var_name);
          if (prop_value == null) {
            if (error_on_invalid_property) {
              throw new NIException("Unknown property value: " + var_name);
            }

            int start = matcher.start();
            int end = matcher.end();

            // Make the substitution,
            out_line.append(line.substring(0, start));
            out_line.append("${");
            out_line.append(var_name);
            out_line.append("}");

            line = line.substring(end);

          }
          else {
            if (escape_dash) {
              // Replace "\" with "\\"
              prop_value = prop_value.replace("\\", "\\\\");
            }

            int start = matcher.start();
            int end = matcher.end();

            // Make the substitution,
            out_line.append(line.substring(0, start));
            out_line.append(prop_value);

            line = line.substring(end);

//            String cline = line.substring(0, start) + prop_value +
//                           line.substring(end);
//            line = cline;
          }

        }
        else {
          break;
        }
      }

      b.append(out_line);
      b.append(line);
      b.append(newline);
    }

    // Write the file,
    File towrite = new File(path, destination_name);
    FileOutputStream fout = new FileOutputStream(towrite);
    OutputStreamWriter w = new OutputStreamWriter(fout, "UTF-8");
    w.append(b);
    w.flush();
    w.close();

    return towrite;

  }


  private File installFile(HashMap properties, String qualif_resource_name,
          File path, String destination_name,
          boolean escape_dash, boolean error_on_invalid_property)
                                                           throws IOException {

    // Install from a resource template,
    InputStream ins =
            NodeInstallTool.class.getResourceAsStream(qualif_resource_name);

    return installFile(properties, ins, path, destination_name, escape_dash, error_on_invalid_property);

  }
  
  /**
   * Install the template resource file into the given path in the local
   * file system.
   */
  private void installResourceTemplate(
          HashMap properties, String template_resource_name,
          File path, String destination_name) throws IOException {

    String qual_name = "/com/mckoi/appcore/conf/" + template_resource_name;
    installFile(properties, qual_name, path, destination_name,
               true, true);

  }

  /**
   * Install the template resource file into the given path in the local
   * file system.
   */
  private void installFileTemplate(
          HashMap properties, File template_file,
          File path, String destination_name) throws IOException {

    installFile(properties, new FileInputStream(template_file), path, destination_name,
               true, true);

  }

  /**
   * Installs a shell script.
   */
  private void installShellScript(HashMap properties,
             String os, String source_file, File dest_path, String dest_file)
                                                           throws IOException {

    String qual_name = "/com/mckoi/appcore/sh/" + os + "/" + source_file;
    File script_file =
              installFile(properties, qual_name, dest_path, dest_file, false, false);
    // Make the script executable,
    script_file.setExecutable(true, false);

  }

  /**
   * Asks for the operating system. Side-effect is we set 'os_text', 'is_win',
   * 'ex_path' and 'ex_jdk_path'. Returns either 'u' or 'w'
   */
  private String askOperatingSystem() {
    is_win = false;
    ex_path = "/mckoiddb/";
    ex_jdk_path = "/usr/lib/jvm/java-1.6.0-openjdk";
    os_text = "Unix";
    ex_nodejs_executable = "/usr/bin/node";

    String os = askOpt("uw",
           "Are you installing on (U)nix or (W)indows? (Default = 'U'): ");

    if (os.equals("u")) {
      pln("Selected: Unix");
    }
    else if (os.equals("w")) {
      pln("Selected: Windows");
      is_win = true;
      ex_path = "C:\\mckoiddb\\";
      os_text = "Windows";
      ex_jdk_path = "C:\\Program Files\\Java\\jdk1.6.0_34\\";
      ex_nodejs_executable = "C:\\Program Files\\nodejs\\node.exe";
    }

    return os;

  }

  /**
   * Asks for the JDK path.
   */
  private String askJDKPath() throws IOException {

    String java_home_path = "";

    boolean ask_for_jdk_dir = true;
    if (found_jdk_directory != null) {
      pln("I found a JDK path. Is it correct?");
      boolean use_found_jdk = askYNQ(
              "Is the JDK path '" + found_jdk_directory.getCanonicalPath() +
                      "' correct? (Y/N/Q): ");
      if (use_found_jdk) {
        ask_for_jdk_dir = false;
      }
      else {
        java_home_path = found_jdk_directory.getCanonicalPath();
      }
    }
    if (ask_for_jdk_dir) {
      pln();
      pln("Please enter the path of the JDK installation on this machine.");
      pln("For example; " + ex_jdk_path);
      String java_home_answer = askString(
            "JDK path: ", jdk_path_valid_check);
      java_home_path = new File(java_home_answer).getCanonicalPath();
    }

    return java_home_path;

  }

  /**
   * Asks for the Node.js executable.
   */
  private String askNodeJSExecutable() throws IOException {

    String nodejs_ex_location = found_nodejs_executable;

    boolean ask_for_nodejs_dir = true;
    if (found_nodejs_executable != null) {
      pln("I found a version of Nodejs on this machine (version '" +
              found_nodejs_version + "').");
      boolean use_found_nodejs = askYNQ(
              "Should I use this version? (Y/N/Q): ");
      if (use_found_nodejs) {
        ask_for_nodejs_dir = false;
      }
    }
    if (ask_for_nodejs_dir) {
      pln();
      pln("Please enter the location of the nodejs executable on this machine.");
      pln("For example; " + ex_nodejs_executable);
      nodejs_ex_location = askString(
              "'nodejs' executable location: ", nodejs_executable_valid_check);
    }

    return nodejs_ex_location;

  }


  /**
   * Asks for the installation path.
   */
  private String askInstallPath() throws IOException {

    pln("The Mckoi software must be installed to a directory on this");
    pln("machine. For example; " + ex_path);
    return askString(
           "Enter installation path: ", path_valid_check);

  }

  /**
   * Asks for the default port for the HTTP service.
   * @return
   * @throws IOException
   */
  private String askHttpDefaultPort() throws IOException {

    String default_port = askString(
              "HTTP Port (Default 80): ", http_port_check);

    default_port = default_port.trim();
    if (default_port.equals("")) {
      return "80";
    }
    else {
      return default_port;
    }

  }

  /**
   * Validates the given IP address or host name is an interface of the local
   * machine.
   */
  private InterfaceInetAddress validateIPAddress(String address_string,
                                                 boolean do_reverse_lookup) {
    try {
      InetAddress inet_addr = InetAddress.getByName(address_string);

      String public_host_name = inet_addr.getCanonicalHostName();
      String public_ip = inet_addr.getHostAddress();

      boolean no_reverse_lookup = public_host_name.equals(public_ip);

      if (no_reverse_lookup) {
        public_host_name = address_string.trim();
      }
      String possible_vhost_name = public_host_name;

      // Is this a local network interface?
      NetworkInterface network_interface =
                              NetworkInterface.getByInetAddress(inet_addr);

      if (network_interface == null) {
        pln();
        pln("There does not appear to be a network interface on this machine");
        pln("with the IP address: " + public_ip);
        boolean continue_anyway =
                askYNQ("Continue installation with this IP? (Y/N/Q): ");
        if (continue_anyway) {
          return new InterfaceInetAddress(inet_addr, possible_vhost_name);
        }
      }
      else {
        if (do_reverse_lookup && no_reverse_lookup) {
          pln();
          pln("This server does not appear to have a host name that resolves");
          pln("against DNS (eg. reverse lookup).");
          boolean continue_anyway =
                  askYNQ("Continue installation? (Y/N/Q): ");
          if (continue_anyway) {
            return new InterfaceInetAddress(inet_addr, possible_vhost_name);
          }
        }
        else {
          return new InterfaceInetAddress(inet_addr, possible_vhost_name);
        }
      }

    }
    catch (SocketException ex) {
      throw new NIException("Socket Exception.", ex);
    }
    catch (UnknownHostException ex) {
      // Should not be possible because we checked already,
      throw new NIException("Unknown host exception.", ex);
    }

    return null;

  }

  /**
   * Validates that the address is not a loop back address.
   * 
   * @param addr
   * @return 
   */
  private InterfaceInetAddress validateNotLoopback(InterfaceInetAddress addr) {
    if (addr != null &&
        addr.inet_address.equals(EXACT_LOOPBACK)) {
      pln("Lookback address is not allowed.");
      return null;
    }
    return addr;
  }

  /**
   * Validates that the address is not an IPv6 address with a scope id.
   * 
   * @param addr
   * @return 
   */
  private InterfaceInetAddress validateNotScoped(InterfaceInetAddress addr) {
    if (addr != null) {
      InetAddress iaddr = addr.inet_address;
      if (iaddr instanceof Inet6Address) {
        Inet6Address v6ip = (Inet6Address) iaddr;
        int scope_id = v6ip.getScopeId();
        if (scope_id != 0) {
          pln("IPv6 address with a scope id is not allowed.");
          pln("Remove the scope id (the '%[interface]' part at the end) and");
          pln("everything should work fine.");
          pln();
          pln("NOTE: We can't accept an IPv6 address with a scope id because" +
              "  this address is shared between the other nodes, and another" +
              "  node may use a different scope id to access the network.");
          return null;
        }
      }
    }
    return addr;
  }

  /**
   * Validates that the address is not a link local address.
   * 
   * @param addr
   * @return 
   */
  private InterfaceInetAddress validateNotLinkLocal(InterfaceInetAddress addr) {
    if (addr != null) {
      InetAddress iaddr = addr.inet_address;
      if (iaddr.isLinkLocalAddress()) {
        pln("This IP address is a link local IP, which isn't a public ");
        pln("facing address.");
        return null;
      }
    }
    return addr;
  }
  
  /**
   * Returns a canonical location for the given file/url string.
   */
  private String getCanonicalLocation(String file_or_url) throws IOException {
    // First check if it's a URL,
    try {
      URL url = new URL(file_or_url);
      // Try and open it,
      URLConnection url_connection = url.openConnection();
      url_connection.connect();
      InputStream ins = url_connection.getInputStream();
      ins.close();
      return file_or_url;
    }
    catch (MalformedURLException e) {
      // Try it as a file,
      File file = new File(file_or_url);
      if (file.exists() && file.isFile()) {
        return file.getCanonicalPath();
      }
      throw new IOException("Not file or URL");
    }
  }
  
  /**
   * Validated the network.conf file given.
   */
  private boolean validateNetworkConf(String networkconf_location,
                                      String priv_ip, String ddb_port) {

    try {
      InputStream ins = null;

      // First check if it's a URL,
      try {
        URL url = new URL(networkconf_location);
        // Try and open it,
        URLConnection url_connection = url.openConnection();
        url_connection.connect();
        ins = url_connection.getInputStream();
      }
      catch (MalformedURLException e) {
        // Try it as a file,
        File file = new File(networkconf_location);
        if (file.exists() && file.isFile()) {
          try {
            ins = new FileInputStream(file);
          }
          catch (FileNotFoundException e2) {
            throw new RuntimeException(e2);
          }
        }
        else {
          throw new RuntimeException("Network config file not found");
        }
      }

      // Load the properties from the location,
      Properties p = new Properties();
      p.load(ins);
      ins.close();

      // Check the properties,
      String connect_whitelist = p.getProperty("connect_whitelist");
      String network_nodelist = p.getProperty("network_nodelist");

      String ddb_addr = priv_ip + ":" + (ddb_port.trim());

      String[] connect_whitelist_arr = connect_whitelist.split(",");
      String[] network_nodelist_arr = network_nodelist.split(",");

      // The connect addresses,
      boolean connect_ok = false;
      boolean node_addr_ok = false;
      for (String connect_addr : connect_whitelist_arr) {
        if (connect_addr.trim().equals(priv_ip)) {
          connect_ok = true;
        }
      }
      // The ddb node addresses,
      for (String ddb_node_addr : network_nodelist_arr) {
        if (ddb_node_addr.trim().equals(ddb_addr)) {
          node_addr_ok = true;
        }
      }

      // If the white lists are set up correctly,
      return connect_ok && node_addr_ok;

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }
  

  /**
   * Performs a stand-alone installation. This performs an installation of
   * MckoiDDB and the Mckoi Web Platform in a default configuration that
   * operates on a single server.
   */
  public void standAloneInstall() throws IOException, NetworkAdminException {

    pln();
    pln("Mckoi Web Platform Stand-Alone installation");
    pln(" -----------------------------------------");


    // Questions;

    //  + Ask about the environment (Windows or Unix),
    //  + Ask for location of installation directory,

    //  + Ask for a MckoiDDB network passkey (default = "random string")
    //  + Ask for the port of the MckoiDDB node on localhost (default = "8030")
    //  + Ask for the local machine name (default = "localhost")

    // Go to account management, etc.

    pln();
    String os = askOperatingSystem();

    pln(" ---");
    pln();
    String java_home_path = askJDKPath();

    pln(" ---");
    pln();
    String nodejs_executable = askNodeJSExecutable();

    pln(" ---");
    pln();
    String dir = askInstallPath();

    boolean is_public;
    String public_ip;
    String public_host_name;
    String vhost_name;

    while (true) {
    
      pln(" ---");
      pln();
      pln("Would you like the Web Platform to be a public service available");
      pln("to clients on the local network or the Internet? If you answer");
      pln("'yes', this machine must have an IP address for a network");
      pln("interface you wish to make the service public on (you will be");
      pln("asked for the IP address after).");
      pln("Answering 'no' will bind all services to the localhost, so the");
      pln("web platform will only be available on the local machine.");
      pln("Choosing 'no' will not require an IP address.");
      pln();
      is_public =
          askYNQ("Make the web platform public (requires network IP)? (Y/N/Q): ");

      public_ip = null;
      public_host_name = null;

      if (is_public) {

        pln(" ---");
        pln("Enter the host name (resolved against DNS) or the IP address of");
        pln("the network interface the Web Platform will be available.");
        pln();
        String entered_host_name =
                        askString("Host name or IP Address: ", ip_valid_check);

        InterfaceInetAddress ip_addr =
                                    validateIPAddress(entered_host_name, true);
        ip_addr = validateNotScoped(ip_addr);
        ip_addr = validateNotLinkLocal(ip_addr);

        // Loop if it didn't validate,
        if (ip_addr == null) {
          continue;
        }
        
        public_ip = ip_addr.inet_address.getHostAddress();
        public_host_name = ip_addr.host_name;
        vhost_name = public_host_name;

        pln();
        pln("Host Name: " + public_host_name);
        pln("IP Address: " + public_ip);

        break;

//        try {
//          InetAddress inet_addr = InetAddress.getByName(entered_host_name);
//
//          public_host_name = inet_addr.getCanonicalHostName();
//          public_ip = inet_addr.getHostAddress();
//
//          boolean no_reverse_lookup = public_host_name.equals(public_ip);
//
//          if (no_reverse_lookup) {
//            public_host_name = entered_host_name.trim();
//          }
//          vhost_name = public_host_name;
//
//
//          // Is this a local network interface?
//          NetworkInterface network_interface =
//                                  NetworkInterface.getByInetAddress(inet_addr);
//
//          if (network_interface == null) {
//            pln();
//            pln("There does not appear to be a network interface on this machine");
//            pln("with this IP address.");
//            boolean continue_anyway =
//                    askYNQ("Continue installation with this IP? (Y/N/Q): ");
//            if (continue_anyway) {
//              break;
//            }
//          }
//          else {
//            if (no_reverse_lookup) {
//              pln();
//              pln("This server does not appear to have a host name that resolves");
//              pln("against DNS (eg. reverse lookup).");
//              boolean continue_anyway =
//                      askYNQ("Continue installation? (Y/N/Q): ");
//              if (continue_anyway) {
//                break;
//              }
//            }
//            else {
//              break;
//            }
//          }
//
//        }
//        catch (SocketException ex) {
//          throw new NIException("Socket Exception.", ex);
//        }
//        catch (UnknownHostException ex) {
//          // Should not be possible because we checked already,
//          throw new NIException("Unknown host exception.", ex);
//        }

      }
      else {

        public_ip = "127.0.0.1";
        public_host_name = "localhost";
        vhost_name = public_host_name;
        break;

      }

    }

    pln();
    pln(" ---");
    pln("On which TCP port would you like the Mckoi Web Platform HTTP");
    pln("service to be available.");
    pln();
    String http_port = askHttpDefaultPort();

    pln();
    pln(" ---");
    pln("An admin account will be created inside the Mckoi Web Platform for");
    pln("administration functions. You will need to provide a username and");
    pln("password for this account.");
    pln();
    String admin_username = askString("Web Platform Admin Username: ",
                                      username_valid_check);
    String admin_password;
    while (true) {
      admin_password = askPasswordString("Admin Password: ",
                                        password_valid_check);
      String admin_password2 = askPasswordString("Confirm Password: ",
                                        password_valid_check);
      if (admin_password.equals(admin_password2)) {
        break;
      }
      pln("Confirmation password was not correct.");
      pln("Please enter password again.");
    }

    pln();
    pln("Installing files now..");
    pln();

    // PENDING: Wait on confirmation by the user.

    // Make the installation path,
    File install_path = new File(dir);
    File ddb_base_path = new File(install_path, "base");
    File ddb_log_path = new File(install_path, "log");

    File lib_path = new File(install_path, "lib");
    File tmp_path = new File(install_path, "temp");

    // Make the install_dev path
    File install_dev_path = new File(install_path, "install_dev");
    File install_dev_cert_path = new File(install_dev_path, "cert");
    File install_dev_conf_path = new File(install_dev_path, "conf");
//    File install_dev_lib_path = new File(install_dev_path, "lib");

    File bin_path = new File(install_path, "bin");
    
    // Create the directories (throws an exception if failure),
    createDirectory(install_path);
    createDirectory(ddb_base_path);
    createDirectory(ddb_log_path);

    createDirectory(lib_path);
    createDirectory(install_dev_path);
    createDirectory(install_dev_cert_path);
//    createDirectory(install_dev_conf_path);
//    createDirectory(install_dev_lib_path);
    createDirectory(bin_path);
    createDirectory(tmp_path);

    // Copy support files into the lib directory
    copyFile(verified_mwp_jar,
             new File(lib_path, verified_mwp_jar.getName()));
    copyFile(verified_ddb_jar,
             new File(lib_path, verified_ddb_jar.getName()));
    copyFile(verified_json_jar,
             new File(lib_path, verified_json_jar.getName()));

    File net_conf_canon = new File(install_path, "network.conf");
    File netconf_info_file = new File(install_path, "netconf_info");

    // Set up a properties object,
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("os_text", os_text);
    properties.put("install_path", dir);
    if (public_ip != null) {
      properties.put("public_ip", public_ip);
    }
    else {
      properties.put("public_ip", "127.0.0.1");
    }

    int pw_rnd = (int) (Math.random() * 999999.0);
    String network_password = "sa" + pw_rnd;

    String single_ddb_node_str = "127.0.0.1:8030";
    String private_ip = "127.0.0.1";

    // Discover the name of the network interface for '127.0.0.1',
    InetAddress ip_addr = InetAddress.getByName(private_ip);
    NetworkInterface net_if = NetworkInterface.getByInetAddress(ip_addr);
    if (net_if == null) {
      pln("Oops, I wasn't able to discover the network interface for; " + private_ip);
      pln();
      return;
    }
    String net_interface = net_if.getName();

    // Scope the ddb address to the interface if it's a link local IPv6 address.
    // NOTE: This is not really necessary if the private_ip is '127.0.0.1'
    String scoped_private_ip = private_ip;
    if (ip_addr instanceof Inet6Address) {
      Inet6Address ipv6_addr = (Inet6Address) ip_addr;
      if (ipv6_addr.isLinkLocalAddress()) {
        ip_addr = Inet6Address.getByAddress(null, ip_addr.getAddress(), net_if);
        scoped_private_ip = ip_addr.getHostAddress();
      }
    }

    properties.put("java_home", java_home_path);

    properties.put("network_password", network_password);
    properties.put("net_interface", net_interface);
    properties.put("this_ddb_address", scoped_private_ip);
    properties.put("this_ddb_port", "8030");
    properties.put("private_ip", private_ip);
    properties.put("manager_addr", single_ddb_node_str);
    properties.put("stand_alone_node", single_ddb_node_str);
    properties.put("network_conf_location", net_conf_canon.getCanonicalPath());
    properties.put("netconf_info_file", netconf_info_file.getCanonicalPath());
    properties.put("ddb_connect_whitelist", "127.0.0.1");
    properties.put("ddb_network_nodelist", "127.0.0.1:8030");
    properties.put("ddb_node_directory", ddb_base_path.getCanonicalPath());
    properties.put("log_directory", ddb_log_path.getCanonicalPath());
    properties.put("lib_directory", lib_path.getCanonicalPath());
    properties.put("root_directory", install_path.getCanonicalPath());

    properties.put("nodejs_executable", nodejs_executable);
    properties.put("nodejs_extra_args", "");

    // Set the http_port_line of the app_service_properties,
    if (http_port.trim().equals("80")) {
      properties.put("http_port_line", "#http_port = 80");
      properties.put("https_port_line", "#https_port = 443");
    }
    else {
      properties.put("http_port_line", "http_port = " + http_port.trim());
      properties.put("https_port_line", "#https_port = 443");
    }

    // Property for the mwp_main.conf file,
    if (is_win) {
      // If it's windows, we don't set the extra argument,
      properties.put("extra_mwp_main_arg01", "");
    }
    else {
      // If it's Unix, we set the extra arg property to ensure SecureRandom
      // uses the none-blocking seed generator.
      properties.put("extra_mwp_main_arg01",
                     "java_args=-Djava.security.egd=file:/dev/./urandom");
    }

    if (verified_install_dev_path.exists()) {

      // Install some configuration files with these properties,

      installResourceTemplate(new HashMap(), "README",
                          install_path, "README");
      installResourceTemplate(new HashMap(), "LICENSE_GPL3",
                          install_path, "LICENSE_GPL3");

      installResourceTemplate(properties, "client_conf.template",
                          install_path, "client.conf");
      pln("Wrote: client.conf");
      installResourceTemplate(properties, "network_conf.template",
                          install_path, "network.conf");
      pln("Wrote: network.conf");
      installResourceTemplate(properties, "node_info.template",
                          install_path, "node_info");
      pln("Wrote: node_info");
      installResourceTemplate(properties, "netconf_info.template",
                          install_path, "netconf_info");
      pln("Wrote: netconf_info");
      installResourceTemplate(properties, "node_conf.template",
                          install_path, "node.conf");
      pln("Wrote: node.conf");
      installResourceTemplate(properties, "mwp_main.template",
                          install_path, "mwp_main.conf");
      pln("Wrote: mwp_main.conf");

      // Copy the install_dev directory from the support directory,
      copyPath(verified_install_dev_path, install_dev_path);

      // Write the App Server configuration file into the install_dev,
      installFileTemplate(properties, new File(verified_templates_path, "app_service_properties.template"),
              install_dev_conf_path, "app_service.properties");
      installFileTemplate(properties, new File(verified_templates_path, "nodejs_app_service_properties.template"),
              install_dev_conf_path, "nodejs_app_service.properties");

      File lib_dest = new File(install_dev_path, "lib");
      lib_dest = new File(lib_dest, "base");

      // Copy the support library files to the lib directory,
      copyFile(verified_mwp_jar,
                 new File(lib_dest, verified_mwp_jar.getName()));
      copyFile(verified_ddb_jar,
                 new File(lib_dest, verified_ddb_jar.getName()));
      copyFile(verified_json_jar,
                 new File(lib_dest, verified_json_jar.getName()));

      // Copy the correct security policy into the install_dev directory,
      // If it's a window installation,
      File security_dest = new File(install_dev_path, "conf");
      String policy_to_copy_file_name;
      if (is_win) {
        policy_to_copy_file_name = "oraclejdk_mswindows_security.policy";
      }
      // Unix,
      else {
        policy_to_copy_file_name = "openjdk_linux_security.policy";
      }
      pln("Using security policy: " + policy_to_copy_file_name);
      copyFile(
             new File(verified_security_path, policy_to_copy_file_name),
             new File(security_dest, "security.policy"));

      pln("Finished building 'install_dev' path.");
    }
    else {
      pln("Copy failed: 'install_dev' path not found.");
      throw new QuitException();
    }

    // Create platform-specific scripts in the /bin directory

    String[] src_files;
    String[] dst_files;
    String resource_loc;

    if (is_win) {
      // Windows scripts,
      resource_loc = "windows";
      src_files = new String[] {
        "viewer.bat", "run_services.bat", "mwp.bat",
        "ddb_console.bat"
      };
      dst_files = new String[] {
        "viewer.bat", "run_services.bat", "mwp.bat",
        "ddb_console.bat"
      };
    }
    else {
      // Unix scripts,
      resource_loc = "unix";
      src_files = new String[] {
        "viewer.sh", "run_services.sh", "mwp.sh",
        "ddb_console.sh"
      };
      dst_files = new String[] {
        "viewer", "run_services", "mwp",
        "ddb_console"
      };
    }

    // Install the scripts,
    {
      int sz = src_files.length;
      for (int i = 0; i < sz; ++i) {
        installShellScript(properties, resource_loc, src_files[i],
                           bin_path, dst_files[i]);
        pln("Installed: " + dst_files[i]);
      }
    }

    // Start up the MckoiDDB node, start the manager/root and block servers
    // on the node.

    pln("Starting the Mckoi DDB machine node.");

    NetworkInterface network_if = null;
    if (net_interface != null) {
      network_if = NetworkInterface.getByName(net_interface);
    }
    DDBNode node = new DDBNode(install_path, network_password, network_if);
    node.start();

    // Wait for the node to start up, etc
    try {
      Thread.sleep(2500);
    }
    catch (InterruptedException ex) {
      throw new NIException("Interrupted Exception.");
    }

    // If the thread finished,
    if (node.isFinished()) {
      // Probably there was an error starting up the MckoiDDB node,
      throw new NIException("The MckoiDDB node stopped unexpectedly.",
                            DDB_STOP_EXPLAIN);
    }

    // Wait until the node is started,
    node.waitUntilStarted();

    pln("Setting up the machine node.");

    // The network profile object,

    NetworkProfile ddb_network = node.getNetworkProfile();
    ServiceAddress single_service_addr =
                                     parseMachineAddress(single_ddb_node_str);

    // Start the roles,

    ddb_network.startManager(single_service_addr);
    ddb_network.registerManager(single_service_addr);
    pln("Registered Manager Service Role.");
    ddb_network.startRoot(single_service_addr);
    ddb_network.registerRoot(single_service_addr);
    pln("Registered Root Service Role.");
    ddb_network.startBlock(single_service_addr);
    ddb_network.registerBlock(single_service_addr);
    pln("Registered Block Service Role.");

    // Now set up the Mckoi Web Platform Environment,

    // Sleep for some time to allow for init,
    try {
      Thread.sleep(2500);
    }
    catch (InterruptedException ex) {
      throw new NIException("Interrupted Exception.");
    }

    // The manager servers setup,
    ServiceAddress[] manager_servers = new ServiceAddress[] {
      single_service_addr
    };

    // ----- MWP Installation -----
    
    // This intializes the system platform and process path, registers the
    // server and creates a normal install for the service.
    // Also creates an admin user account.

    // Standard output,
    StyledPrintWriter std_out = getPWriter();

    // The MckoiDDBClient and AppCoreAdmin objects,
    MckoiDDBClient ddb_client = MckoiDDBClientUtils.connectTCP(
                                   manager_servers, network_password, net_if);
    // Get the network profile and set the network conf for it,
    NetworkProfile network_profile = ddb_client.getNetworkProfile(null);
    network_profile.setNetworkConfiguration(node.getNetConfigResource());
    AppCoreAdmin app_core = new AppCoreAdmin(ddb_client, network_profile);

    // The path location of the root server,
    PathLocation sys_path = new PathLocation(single_service_addr,
                                new ServiceAddress[] { single_service_addr } );

    // Intialize the system platform,
    app_core.initializeSystemPlatform(std_out, sys_path);
    // Initialize the process service,
    app_core.initializeProcesses(std_out, sys_path);

    // Make sure the system tables are populated,
    app_core.populateSystemTables(std_out, ddb_network);

    // Register the server,
    app_core.registerServer(std_out,
                            public_host_name,
                            public_ip,
                            private_ip);

    // --- Create the install ---

    // Create the 'normal' web/process install,
    app_core.createInstall(std_out, "normal");
    // Upload from the install_dev/ path,
    app_core.uploadInstall(std_out, "normal",
                           install_dev_path.getCanonicalPath());
    // Deploy this install,
    app_core.deployInstall(std_out, "normal");
    

    // --- Admin account ---

    // Create the file system resource for the admin user,
    app_core.addODBResource(std_out, "ufsadmin", sys_path);
    // Add the 'admin' account and user
    app_core.createAccount(std_out, "admin");
    app_core.addAccountUser(std_out, "admin", admin_username, admin_password);
    // Grant super user status to the admin account,
    app_core.grantSuperUser(std_out, "admin");

    // Connect the vhost to the account,
    app_core.connectVHost(std_out, vhost_name + ":http", "admin");
    app_core.connectVHost(std_out, vhost_name + ":https", "admin");
    // ISSUE: Should we make the public_ip a vhost address?
    app_core.connectVHost(std_out, public_ip + ":http", "admin");
    app_core.connectVHost(std_out, public_ip + ":https", "admin");

    // --- Service management ---
    
    // Register the http and process service roles on this server,
    app_core.startRole(std_out, "http", public_ip);
    pln("HTTP role registered to machine.");
    app_core.startRole(std_out, "process", public_ip);
    pln("PROCESS role registered to machine.");

    // --- Install console application to the admin account ---

    // ISSUE: Should we make the public_ip a vhost address?
    String vhost_list = vhost_name + ":*," + public_ip + ":*";
    app_core.addAccountApp(std_out, "admin", "console",
                           vhost_list, "console", "/apps/console/");
    
    if (verified_console_webapp_war != null) {
      String war_ob_str = verified_console_webapp_war.getCanonicalPath();
      app_core.uploadAccountApp(std_out, "admin", "console", war_ob_str);
    }
    else {
      pln("Administration console war was not uploaded because");
      pln("it was not found in the support directory.");
    }

    pln();
    pln("Stopping the MckoiDDB machine node...");

    node.close();
    node.waitUntilFinished();
    pln("MckoiDDB Stopped.");

    String http_port_string = "";
    if (!http_port.trim().equals("80")) {
      http_port_string = ":" + http_port.trim();
    }

    pln();
    pln();
    pln("-- Installation completed. --");

    pln();
    // Summarize the options,

    pln();
    pln("Summary of Installation");
    pln("-----------------------");
    pln();
    pln("Type:              Stand-alone installation");
    pln("OS:                " + os_text);
    pln("Install Directory: " + dir);
    pln("Is Public Server:  " + (is_public ? "Yes" : "No"));
    if (is_public) {
      pln("Interface IP:      " + public_ip);
    }
    pln("HTTP Service Port: " + http_port);
    pln("Admin username:    " + admin_username);

    pln();
    pln();
    pln("To start the MckoiDDB and Mckoi Web Platform 'daemon' on this");
    pln("machine, use the following command:");
    pln();
    pln("  bin/run_services");
    pln();
    pln("When the service has started the Mckoi Web Platform console is");
    pln("accessed by going to one of the following URLs;");
    pln();
    pln("  http://" + vhost_name + http_port_string + "/console/");
    pln("  http://" + public_ip + http_port_string + "/console/");
    pln();


  }

  /**
   * Performs a deploy installation.
   */
  public void deployInstall() throws IOException, NetworkAdminException {

    pln();
    pln("Mckoi Web Platform Node installation");
    pln(" ----------------------------------");

    pln();
    pln("Is there an existing MckoiDDB network operating on the network?");
    pln("An operating MckoiDDB network is a series of servers that are");
    pln("running the MckoiDDB machine node application, and has manager,");
    pln("root and block service roles allocated.");
    pln();
    pln("If you are currently setting up servers and do not currently");
    pln("have available an operating MckoiDDB network then you should");
    pln("answer 'no' here. Otherwise answer 'yes' and I will add this");
    pln("server into your existing infrastructure later in the");
    pln("installation process.");
    pln();

    boolean existing_network =
              askYNQ("Is an operating MckoiDDB network available? (Y/N/Q): ");

    pln(" ---");
    pln();
    String os = askOperatingSystem();

    pln(" ---");
    pln();
    String java_home_path = askJDKPath();

    pln(" ---");
    pln();
    String nodejs_executable = askNodeJSExecutable();

    pln(" ---");
    pln();
    final String dir = askInstallPath();
    File install_path = new File(dir);

    pln(" ---");
    pln();
    pln("Host IP address information");
    pln(" -------------------------");
    pln();
    pln("It is recommended that all servers that host Mckoi Web Platform");
    pln("nodes have two network interfaces with a private and public IP");
    pln("address. The private network interface IP should give access to");
    pln("all other nodes in the private Mckoi network (eg. the local");
    pln("network). The public network interface IP should give access to");
    pln("the network where the public facing services (the web");
    pln("application server) provided by the Mckoi Web Platform are to");
    pln("be made available. For example, the public IP may be an IP that");
    pln("is addressable over the Internet.");
    pln();
    pln("If the server has only one IP address then this address should");
    pln("be given as both the public and private IP.");
    pln();

    String scoped_private_ip = null;

    InterfaceInetAddress private_ip_addr = null;
    InterfaceInetAddress public_ip_addr = null;

    while (true) {

      while (private_ip_addr == null) {
        String private_ip_addr_str =
                          askString("PRIVATE IP Address: ", ip_valid_check);

        // Validate the IP addresses,
        private_ip_addr = validateIPAddress(private_ip_addr_str, false);

        private_ip_addr = validateNotLoopback(private_ip_addr);

        // If it's a private IPv6 link-local address then it must be scoped,

        if (private_ip_addr != null) {

          scoped_private_ip = private_ip_addr.inet_address.getHostAddress();

          InetAddress ip_addr = private_ip_addr.inet_address;
          if (ip_addr instanceof Inet6Address && ip_addr.isLinkLocalAddress()) {
            Inet6Address ipv6_addr = (Inet6Address) ip_addr;
            NetworkInterface scoped_if = ipv6_addr.getScopedInterface();
            if (scoped_if == null) {
              pln("This IP address is an IPv6 link-local address without a scope id.");
              pln("Please provide a scope id with the address so it can be resolved");
              pln("unambiguously.");
              pln();
              private_ip_addr = null;
            }
            else {
              // Address without a scope,
              InetAddress noscope_ip_addr =
                            InetAddress.getByAddress(ipv6_addr.getAddress());
              String public_host_name = noscope_ip_addr.getCanonicalHostName();
              // The private ip address with no scope,
              private_ip_addr =
                    new InterfaceInetAddress(noscope_ip_addr, public_host_name);
            }
          }
        }

      }

      while (public_ip_addr == null) {
        String public_ip_addr_str =
                          askString("PUBLIC IP Address: ", ip_valid_check);

        // Validate the IP addresses,
        public_ip_addr = validateIPAddress(public_ip_addr_str, false);

        public_ip_addr = validateNotLoopback(public_ip_addr);
        public_ip_addr = validateNotScoped(public_ip_addr);
        public_ip_addr = validateNotLinkLocal(public_ip_addr);

      }

      // Check if the ip addresses are the same,
      if (private_ip_addr.inet_address.equals(public_ip_addr.inet_address)) {
        pln();
        pln("The private and public ip addresses given are the same. This");
        pln("type of setup will work fine, however it is better to have a");
        pln("separate private and public network.");
        pln();
        boolean continue_anyway = askYNQ("Continue installation? (Y/N/Q): ");
        if (continue_anyway) {
          break;
        }
      }
      else {
        break;
      }
    }

    // Network Interface resolution,
    
    pln();
    pln("I need a name for a network interface to use for making");
    pln("connections to other nodes in the network. This is necessary");
    pln("to support link local IPv6 networks.");
    pln();
    pln("The network interface name I need is the one that's used");
    pln("to communicate with the Mckoi DDB network.");
    pln();
    pln("Guessing the interface to use (this guess is almost always");
    pln("correct).");

    // Good guess on network interface,
    InetAddress scoped_private_ip_addr =
                    InetAddress.getByName(scoped_private_ip);
    NetworkInterface guess_if =
                    NetworkInterface.getByInetAddress(scoped_private_ip_addr);

    if (guess_if == null) {
      pln("Oops, something bad happened!");
      pln();
      pln("I wasn't able to find a network interface for " + scoped_private_ip_addr);
      pln();
      return;
    }

    String if_to_use = guess_if.getName();
    boolean got_an_if = askYNQ(
              "Is '" + if_to_use + "' the correct network interface? (Y/N/Q)");

    while (!got_an_if) {
      pln();
      pln("The list of possible network interfaces on this machine are as");
      pln("follows:");
      Enumeration<NetworkInterface> local_ifs =
                                      NetworkInterface.getNetworkInterfaces();
      List<NetworkInterface> if_list = new ArrayList<>();
      int index = 1;
      while (local_ifs.hasMoreElements()) {
        NetworkInterface net_if = local_ifs.nextElement();
        if_list.add(net_if);
        Enumeration<InetAddress> inet_addresses = net_if.getInetAddresses();
        if (inet_addresses.hasMoreElements()) {
          pln("  " + net_if.getName());
        }
        ++index;
      }

      pln();
      while (!got_an_if) {
        String if_name = askString("Enter the name of the interface to use: ", null);
        // Check the name is valid,
        for (NetworkInterface nif : if_list) {
          if (nif.getName().equals(if_name)) {
            got_an_if = true;
            if_to_use = if_name;
            break;
          }
        }
        if (!got_an_if) {
          pln("Sorry, that's not a valid interface name.");
        }
      }

    }

    String net_interface = if_to_use;

    pln(" ---");
    pln();

    String networkconf_location = null;
    File netconf_info_file = new File(install_path, "netconf_info");

    pln("MckoiDDB settings");
    pln(" ---------------");
    pln();
//    pln("I need to know some information about your MckoiDDB setup (if");
//    pln("you have one).");
//    pln();
//    pln("Please choose one of the following options;");
//    pln();
//    pln(" 1. I want to build a new MckoiDDB installation on my");
//    pln("    network.");
//    pln();
//    pln(" 2. I have an existing MckoiDDB installation I want to connect");
//    pln("    to or expand.");
//    pln();
//    
//    String ddb_option1 = askOpt("12q", "Enter Option (1,2,Q): ");
//    if (ddb_option1.equals("q")) {
//      throw new QuitException();
//    }
//    
//    boolean new_install = ddb_option1.equals("1");
//
//    pln();

//    pln(" ---");
//    pln();

    pln("Please tell me the TCP port to install MckoiDDB on for this");
    pln("server. (eg. '13030')");

    String ddb_port =
                    askString("DDB Machine Node port: ", tcp_port_valid_check);

    pln(" ---");
    pln();
    
    String network_password;

    if (!existing_network) {

      pln("This is a new installation so we need to make a network");
      pln("password (also called a hash code) for it.");
      pln();
      pln("The network password, or hash code, is used by DDB whenever a");
      pln("connection is made between nodes. It confirms that the nodes");
      pln("are in agreement on the DDB network being talked to. It is");
      pln("NOT a security feature. All separate Mckoi DDB networks should");
      pln("have a unique code.");
      pln();
      pln("Either I can create a unique code for you, or you can enter");
      pln("one yourself.");
      pln();
      boolean random_network_password =
              askYNQ("Would you like me to create a random hash? (Y/N/Q): ");
      if (random_network_password) {
        network_password = "gen0" + makeRandomHashCode(10);
        pln("Ok, you network password is: " + network_password);
      }
      else {
        pln("Please enter a MckoiDDB network password for this new");
        pln("installation.");
        network_password = askString("Network Password: ", password_valid_check);
      }

    }
    else {
      pln("Please enter the MckoiDDB network password for the MckoiDDB");
      pln("installation.");
      pln();
      pln("You can find the network password in the node.conf file.");
      pln();
      network_password = askString("Network Password: ", password_valid_check);
    }

    pln(" ---");
    pln();

    boolean ask_netconf_loc = true;
    if (!existing_network) {
      pln("Would you like me to make a default 'network.conf' file for");
      pln("this new installation?");
      pln();
      pln("Note that if you create a new 'network.conf' file you will");
      pln("need to copy the file to a location (shared file system or");
      pln("a location accessible via a URL) that all other MckoiDDB");
      pln("nodes in the network will be able to access.");
      pln();
      pln("If you do not create a 'network.conf' file, you will be");
      pln("asked for the location of an existing 'network.conf'.");
      pln();

      ask_netconf_loc = !askYNQ("Create a 'network.conf' file? (Y/N/Q): ");
      pln();
    }

    if (ask_netconf_loc) {
      // Ask for the location of the network.conf file
      pln("Please tell me the location of your MckoiDDB 'network.conf'");
      pln("file. This file lists all the hosts in your MckoiDDB");
      pln("network and the client hosts that are permitted to connect.");
      pln("The location given here may be a URL or a path in the file");
      pln("system.");
      pln("(eg. 'http://myinternalserver.com/network.conf',");
      pln("'/shareddata/mckoi/network.conf')");
      pln();

      while (true) {
        networkconf_location =
              askString("'network.conf' location: ", networkconf_valid_check);
        String priv_ip = private_ip_addr.inet_address.getHostAddress();
        boolean networkconf_good = validateNetworkConf(networkconf_location,
                priv_ip, ddb_port);
        if (!networkconf_good) {
          pln("The given 'network.conf' does not have this server");
          pln("white-listed. You must edit this file and ensure the IP");
          pln("address (" + priv_ip + ") is included in the");
          pln("'connect_whitelist' property. You must also ensure that");
          pln("'" + priv_ip + ":" + ddb_port + "' is included in the");
          pln("'network_nodelist' property list.");
          pln();
          pln("Please add this information to the 'network.conf' file and");
          pln("try again.");
          pln();
        }
        else {
          // Canonical location,
          networkconf_location = getCanonicalLocation(networkconf_location);
          break;
        }
      }

      pln();
    }

    pln(" ---");
    pln();

    String manager_servers_value;

    boolean mwp_installation_found = false;

    NetworkInterface net_if = NetworkInterface.getByName(if_to_use);
    TCPConnectorValues connector_values =
                            new TCPConnectorValues(network_password, net_if);
    
    // Try and connect to the network and obtain information about it (such as
    // the location of the manager servers, etc).
    if (existing_network) {

      pln("Connecting to the existing MckoiDDB network...");
      pln();

      // Create a NetworkProfile object,
      // 'network.conf' file,
      NetworkConfigResource network_conf_resource =
                             NetworkConfigResource.parse(networkconf_location);

      NetworkProfile network_profile =
                                   NetworkProfile.tcpConnect(connector_values);
      network_profile.setNetworkConfiguration(network_conf_resource);

      // Refresh and get the list of all manager servers,
      pln("Discovering the manager servers.");
      network_profile.refresh();
      MachineProfile[] manager_servers = network_profile.getManagerServers();

      // Turn into a ServiceAddress array and format it as a string
      ServiceAddress[] arr = new ServiceAddress[manager_servers.length];
      for (int i = 0; i < manager_servers.length; ++i) {
        arr[i] = manager_servers[i].getServiceAddress();
      }
      manager_servers_value = ServiceAddress.formatServiceAddresses(arr);

      // Report
      pln("Manager: " + manager_servers_value);

      // Look for an existing installation of the Mckoi Web Platform,
      PathInfo sysplatform_pathinfo =
                             network_profile.getPathInfoForPath("sysplatform");
      
      if (sysplatform_pathinfo == null) {
        pln("Mckoi Web Platform is NOT installed.");
        mwp_installation_found = false;
      }
      else {
        pln("Mckoi Web Platform installation was found.");
        mwp_installation_found = true;
      }

    }
    
    else {
      
      while (true) {
        pln("You are installing the software without an operating MckoiDDB");
        pln("network yet. To complete the installation I will need to know");
        pln("the location of the manager server(s) on the network once it");
        pln("is operating. This value will be written to the 'client.conf'");
        pln("property file (in the 'manager_address' property).");
        pln();
        pln("An example 'manager_address' value looks like the following;");
        pln("  192.168.100.1:13030 , 192.168.100.2:13030");
        pln();
        pln("If you do not enter a value here, the installation will still");
        pln("complete however you will need to manually edit the");
        pln("'client.conf' file and set the appropriate manager_address");
        pln("property value once the MckoiDDB network is operating.");
        pln();

        manager_servers_value =
              askString("'manager_address' value: ", service_addrs_valid_check);

        manager_servers_value = manager_servers_value.trim();

        if (manager_servers_value.equals("")) {
          pln("You are choosing not to enter the 'manager_address' property");
          pln("at this time. This will require you to manually edit the");
          pln("'client.conf' file and set the value once you have an");
          pln("operating MckoiDDB network.");
          pln();
          boolean continue_install =
                    askYNQ("Continue with 'manager_address' not set? (Y/N/Q): ");
          pln();
          if (!continue_install) {
            continue;
          }
        }

        break;

      } // while (true)

    }

    pln(" ---");
    pln();
    pln("I now have enough information to install the Mckoi software on");
    pln("this server.");
    pln();
    if (!existing_network) {
      // No existing operating MckoiDDB network,
      pln("Once you have finished installing the software on all the");
      pln("servers in your network, you will need to use the");
      pln("'bin/ddb_console' command to assign manager, root and block");
      pln("server roles to servers in your network infrastructure.");
      pln();
      pln("After you have assigned the server roles, you must then");
      pln("initialize the Mckoi Web Platform by running this tool again");
      pln("and selecting option 3 from the first menu.");
      pln();
    }
    else if (!mwp_installation_found) {
      // Existing network but the Mckoi Web Platform is not installed,
      pln("Please note that once you have installed the software on the");
      pln("servers in your network you will need to initialize the Mckoi");
      pln("Web Platform by running this tool again and selecting option");
      pln("3 from the first menu.");
      pln();
    }
    
    boolean confirm_install = askYNQ("Confirm installation? (Y/N/Q): ");
    
    if (!confirm_install) {
      pln();
      pln("-- Installation Aborted --");
      return;
    }
    
    // Now go ahead and install the software,
    
    // Summarize the options,

    String private_ip = private_ip_addr.inet_address.getHostAddress();
    String public_ip = public_ip_addr.inet_address.getHostAddress();
    
    if (!ask_netconf_loc) {
      File install_dev_path = new File(install_path, "network.conf");
      networkconf_location = install_dev_path.getCanonicalPath();
    }

    pln();
    pln("Summary of Installation");
    pln("-----------------------");
    pln();
    pln("Type:                 Node Installation");
    pln("OS:                   " + os_text);
    pln("Install Directory:    " + dir);
    pln("Private Interface IP: " + private_ip);
    pln("Public Interface IP:  " + public_ip);
    pln("'network.conf':       " + networkconf_location);

    pln();

    // Make the installation path,
    File ddb_base_path = new File(install_path, "base");
    File ddb_log_path = new File(install_path, "log");

    File lib_path = new File(install_path, "lib");
    File tmp_path = new File(install_path, "temp");

//    // Make the install_dev path
//    File install_dev_path = new File(install_path, "install_dev");
//    File install_dev_cert_path = new File(install_dev_path, "cert");

    File bin_path = new File(install_path, "bin");
    
    // Create the directories (throws an exception if failure),
    createDirectory(install_path);
    createDirectory(ddb_base_path);
    createDirectory(ddb_log_path);

    createDirectory(lib_path);
//    createDirectory(install_dev_path);
//    createDirectory(install_dev_cert_path);
    createDirectory(bin_path);
    createDirectory(tmp_path);

    // Copy support files into the lib directory
    copyFile(verified_mwp_jar,
             new File(lib_path, verified_mwp_jar.getName()));
    copyFile(verified_ddb_jar,
             new File(lib_path, verified_ddb_jar.getName()));
    copyFile(verified_json_jar,
             new File(lib_path, verified_json_jar.getName()));

    // Set up a properties object,
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("os_text", os_text);
    properties.put("install_path", dir);
    properties.put("public_ip", public_ip);

    String single_ddb_node_str = private_ip + ":" + ddb_port;

    properties.put("java_home", java_home_path);

    properties.put("network_password", network_password);
    properties.put("net_interface", net_interface);
    properties.put("this_ddb_address", scoped_private_ip);
    properties.put("this_ddb_port", ddb_port);
    properties.put("private_ip", private_ip);
    properties.put("manager_addr", manager_servers_value);
    properties.put("stand_alone_node", single_ddb_node_str);
    properties.put("network_conf_location", networkconf_location);
    properties.put("netconf_info_file", netconf_info_file.getCanonicalPath());
    properties.put("ddb_connect_whitelist", "127.0.0.1," + private_ip);
    properties.put("ddb_network_nodelist", single_ddb_node_str);
    properties.put("ddb_node_directory", ddb_base_path.getCanonicalPath());
    properties.put("log_directory", ddb_log_path.getCanonicalPath());
    properties.put("lib_directory", lib_path.getCanonicalPath());
    properties.put("root_directory", install_path.getCanonicalPath());

    properties.put("nodejs_executable", nodejs_executable);
    properties.put("nodejs_extra_args", "");

    // Property for the mwp_main.conf file,
    if (is_win) {
      // If it's windows, we don't set the extra argument,
      properties.put("extra_mwp_main_arg01", "");
    }
    else {
      // If it's Unix, we set the extra arg property to ensure SecureRandom
      // uses the none-blocking seed generator.
      properties.put("extra_mwp_main_arg01",
                     "java_args=-Djava.security.egd=file:/dev/./urandom");
    }

    // Install some configuration files with these properties,

    installResourceTemplate(new HashMap(), "README",
                        install_path, "README");
    installResourceTemplate(new HashMap(), "LICENSE_GPL3",
                        install_path, "LICENSE_GPL3");

    installResourceTemplate(properties, "client_conf.template",
                        install_path, "client.conf");
    pln("Wrote: client.conf");
    if (!ask_netconf_loc) {
      installResourceTemplate(properties, "network_conf.template",
                          install_path, "network.conf");
    }
    pln("Wrote: network.conf");
    installResourceTemplate(properties, "node_info.template",
                        install_path, "node_info");
    pln("Wrote: node_info");
    installResourceTemplate(properties, "netconf_info.template",
                        install_path, "netconf_info");
    pln("Wrote: netconf_info");
    installResourceTemplate(properties, "node_conf.template",
                        install_path, "node.conf");
    pln("Wrote: node.conf");
    installResourceTemplate(properties, "mwp_main.template",
                        install_path, "mwp_main.conf");
    pln("Wrote: mwp_main.conf");

//    boolean install_dev_ready;
//
//    if (verified_install_dev_path.exists()) {
//      // Copy the install_dev directory from the support directory,
//      copyPath(verified_install_dev_path, install_dev_path);
//      File lib_dest = new File(install_dev_path, "lib");
//
//      // Copy the support library files to the lib directory,
//      copyFile(verified_mwp_jar,
//                 new File(lib_dest, verified_mwp_jar.getName()));
//      copyFile(verified_ddb_jar,
//                 new File(lib_dest, verified_ddb_jar.getName()));
//      copyFile(verified_json_jar,
//                 new File(lib_dest, verified_json_jar.getName()));
//
//      // Copy the correct security policy into the install_dev directory,
//      // If it's a window installation,
//      File security_dest = new File(install_dev_path, "conf");
//      String policy_to_copy_file_name;
//      if (is_win) {
//        policy_to_copy_file_name = "oraclejdk6_mswindows_security.policy";
//      }
//      // Unix,
//      else {
//        policy_to_copy_file_name = "openjdk6_linux_security.policy";
//      }
//      pln("Using security policy: " + policy_to_copy_file_name);
//      copyFile(
//             new File(verified_security_path, policy_to_copy_file_name),
//             new File(security_dest, "security.policy"));
//
//      pln("Finished building 'install_dev' path.");
//      install_dev_ready = true;
//    }
//    else {
//      pln("Copy failed: 'install_dev' path not found.");
//      install_dev_ready = false;
//    }

    // Create platform-specific scripts in the /bin directory

    String[] src_files;
    String[] dst_files;
    String resource_loc;

    if (is_win) {
      // Windows scripts,
      resource_loc = "windows";
      src_files = new String[] {
        "viewer.bat", "run_services.bat", "mwp.bat",
        "ddb_console.bat", "mwp_register.bat"
      };
      dst_files = new String[] {
        "viewer.bat", "run_services.bat", "mwp.bat",
        "ddb_console.bat", "mwp_register.bat"
      };
    }
    else {
      // Unix scripts,
      resource_loc = "unix";
      src_files = new String[] {
        "viewer.sh", "run_services.sh", "mwp.sh",
        "ddb_console.sh", "mwp_register.sh"
      };
      dst_files = new String[] {
        "viewer", "run_services", "mwp",
        "ddb_console", "mwp_register"
      };
    }

    // Install the scripts,
    {
      int sz = src_files.length;
      for (int i = 0; i < sz; ++i) {
        installShellScript(properties, resource_loc, src_files[i],
                           bin_path, dst_files[i]);
        pln("Installed: " + dst_files[i]);
      }
    }

    pln();
    pln("-- Installation completed. --");

    pln();
    pln("You can now start the 'daemon' services on this server by");
    pln("running the 'bin/run_services' script.");
    pln();
    pln("After the MckoiDDB node service is started, you may assign");
    pln("a role to this server (eg. assign it as a 'block' service)");
    pln("by using the 'bin/ddb_console' script. To proceed with the");
    pln("installation of the Mckoi Web Platform you must have an");
    pln("operating MckoiDDB network that includes manager, root");
    pln("and block roles assigned appropriately over the machines");
    pln("in the network. Please consult the MckoiDDB documentation");
    pln("for details on how to proceed with this.");
    pln();

    if (!existing_network) {
      pln("Once the MckoiDDB network is operating you may initialize");
      pln("the Mckoi Web Platform on the network by running the");
      pln("install tool again and selecting '3' from the initial");
      pln("options menu.");
      pln();
    }
    else if (!mwp_installation_found) {
      pln("Remember that you will need to install the Mckoi Web");
      pln("Platform on the MckoiDDB network. To do this, run this");
      pln("tool again and select '3' from the initial menu.");
      pln();
    }
    else {
      pln("You will need to register this server in the Mckoi Web");
      pln("Platform database. To do this, use the following command;");
      pln();
      pln("  bin/mwp_register");
      pln();
      pln("Once the server is registered, if you want to make this");
      pln("server an application server you will need to start the");
      pln("'http' role using the 'bin/mwp' script. For example, the");
      pln("commands to start the http, https and process roles on");
      pln("this server are;");
      pln();
      pln("  bin/mwp start role http on " + public_ip);
      pln("  bin/mwp start role https on " + public_ip);
      pln("  bin/mwp start role process on " + public_ip);
      pln();
    }

  }

  /**
   * Mckoi Web Platform initialization.
   */
  private void initializeInstall() throws IOException, NetworkAdminException {

    pln();
    pln("Mckoi Web Platform initialization");
    pln(" -------------------------------");

    pln();
    String os = askOperatingSystem();

    pln();
    pln("Please enter the location of the directory where the Mckoi");
    pln("software has been installed on this machine. If the Mckoi");
    pln("software has not been installed, then provide a path I can use");
    pln("to store some temporary files.");
    pln("For example; " + ex_path);
    String dir = askString("Installation path: ", mwp_path_valid_check);

    // The install_dev_path;
    File install_dev_path = new File(dir, "install_dev");
    File install_dev_conf_path = new File(install_dev_path, "conf");

    pln();
    pln("You are about to initialize a Mckoi Web Platform installation");
    pln("on an operating MckoiDDB network. This is a one time operation");
    pln("that is necessary before the Mckoi Web Platform can be used.");
    pln();
    pln("This operation will create the database structures necessary to");
    pln("support the Mckoi Web Platform, and create an administration");
    pln("account to use the web console user interface.");
    pln();
    pln("To complete this operation, you will need an operating MckoiDDB");
    pln("with at least one root server.");
    pln();

    pln("First I need the location of the 'network.conf' file that");
    pln("describes the MckoiDDB network topology.");
    pln();

    String networkconf_location =
              askString("Location of 'network.conf' (can be URL or file): ",
                        networkconf_valid_check);

    // Parse the network configuration string,
    NetworkConfigResource net_config_resource =
                             NetworkConfigResource.parse(networkconf_location);
    
    pln();
    pln("I need the location of the 'client.conf' file that tells me");
    pln("the location of the manager server(s) and the network password.");
    pln();
    String clientconf_location =
              askString("Location of 'client.conf': ",
                        clientconf_valid_check);

    // Load the properties,
    File clientconf_file = new File(clientconf_location);
    if (clientconf_file.isDirectory()) {
      clientconf_file = new File(clientconf_file, "client.conf");
    }
    Properties p = new Properties();
    FileReader cc_reader = new FileReader(clientconf_file);
    p.load(cc_reader);
    cc_reader.close();

    String network_password = p.getProperty("network_password");
    String manager_servers_value = p.getProperty("manager_address");
    String net_if_value = p.getProperty("net_interface");

    if (network_password == null) {
      epln();
      epln("Sorry but the 'client.conf' does not contain a");
      epln("'network password' property.");
      throw new QuitException();
    }
    if (manager_servers_value == null ||
        manager_servers_value.trim().length() == 0) {
      epln();
      epln("Sorry but the 'client.conf' does not contain a");
      epln("'manager_address' property.");
      throw new QuitException();
    }

    NetworkInterface net_if = null;
    if (net_if_value != null) {
      net_if = NetworkInterface.getByName(net_if_value);
    }

    manager_servers_value = manager_servers_value.trim();
    
    ServiceAddress[] manager_servers =
                   ServiceAddress.parseServiceAddresses(manager_servers_value);

    pln("Attempting to connect to the MckoiDDB network...");
    
    // Create a NetworkProfile object,
    // 'network.conf' file,
    NetworkConfigResource network_conf_resource =
                            NetworkConfigResource.parse(networkconf_location);

    TCPConnectorValues connector_values =
                            new TCPConnectorValues(network_password, net_if);
    
    NetworkProfile ddb_network = NetworkProfile.tcpConnect(connector_values);
    ddb_network.setNetworkConfiguration(network_conf_resource);

    ddb_network.refresh();

    MachineProfile[] manager_servers_mparr = ddb_network.getManagerServers();
    MachineProfile[] root_servers_mparr = ddb_network.getRootServers();
    MachineProfile[] block_servers_mparr = ddb_network.getBlockServers();
    
    String srv_type = null;
    if (manager_servers_mparr.length == 0) {
      srv_type = "manager";
    }
    else if (root_servers_mparr.length == 0) {
      srv_type = "root";
    }
    else if (block_servers_mparr.length == 0) {
      srv_type = "block";
    }

    if (srv_type != null) {
      epln();
      epln("I can't continue with this installation because there must be");
      epln("at least 1 " + srv_type + " server available on the MckoiDDB network.");
      epln("Please consult the documentation on assigning manager, root");
      epln("and block service roles in a MckoiDDB network.");
      throw new QuitException();
    }
    
    // Look for an existing installation of the Mckoi Web Platform,
    PathInfo sysplatform_pathinfo =
                            ddb_network.getPathInfoForPath("sysplatform");

    // Error if found,
    if (sysplatform_pathinfo != null) {
      epln();
      epln("I can't continue with this installation because a Mckoi Web");
      epln("Platform installation was already found in the database.");
      throw new QuitException();
    }

    pln("Connect successful.");
    pln();
    pln(" ---");
    pln("Which root server(s) do you want me to install the Mckoi Web");
    pln("Platform on. There are currently " + root_servers_mparr.length + " root servers available");
    pln("listed below;");
    for (MachineProfile m : root_servers_mparr) {
      pln("  " + m.getServiceAddress().formatString());
    }
    pln();
    pln("The root server(s) to use must be listed as a comma delimited");
    pln("list. Some examples follow;");
    pln("  192.168.1.100:13030");
    pln("  192.168.1.103:13030, 192.168.1.104:13030");
    pln();

    ServiceAddress[] sys_plat_root_servers;
root_srv_check:
    while (true) {
      String sys_root_srvs_value =
              askString("Root server(s) for Web Platform: ",
                        service_addrs_valid_check);

      ddb_network.refresh();
      MachineProfile[] root_servers_check_arr = ddb_network.getRootServers();

      sys_root_srvs_value = sys_root_srvs_value.trim();
    
      // Parse it into an array of ServiceAddress objects,
      sys_plat_root_servers =
                     ServiceAddress.parseServiceAddresses(sys_root_srvs_value);

      // Check the root servers are in the root servers list,
      for (ServiceAddress saddr : sys_plat_root_servers) {
        boolean its_good = false;
        for (MachineProfile m : root_servers_check_arr) {
          if (saddr.equals(m.getServiceAddress())) {
            its_good = true;
          }
        }
        if (!its_good) {
          pln("The root server '" + saddr + "' is not a root");
          pln("server in the MckoiDDB network.");
          continue root_srv_check;
        }
      }
      
      break;
    }

    pln();
    pln(" ---");
    pln("On which TCP port would you like the Mckoi Web Platform HTTP");
    pln("service to be available.");
    String http_port = askHttpDefaultPort();

    pln();
    pln(" ---");
    pln("An admin account will be created inside the Mckoi Web Platform for");
    pln("administration functions. You will need to provide a username and");
    pln("password for this account.");
    pln();
    String admin_username = askString("Web Platform Admin Username: ",
                                      username_valid_check);
    String admin_password;
    while (true) {
      admin_password = askPasswordString("Admin Password: ",
                                        password_valid_check);
      String admin_password2 = askPasswordString("Confirm Password: ",
                                        password_valid_check);
      if (admin_password.equals(admin_password2)) {
        break;
      }
      pln("Confirmation password was not correct.");
      pln("Please enter password again.");
    }

    pln();
    pln(" ---");
    pln("I need a comma delimited list of virtual host names that will");
    pln("resolve (via domain name resolution) to one of more of the");
    pln("servers in your Mckoi Web Platform infrastructure, and that");
    pln("you wish to use to access the administration console. The vhost");
    pln("names may also be IP addresses.");
    pln();
    pln("Please ensure any host names given here resolve to your Mckoi");
    pln("servers via DNS.");
    pln();
    pln("An example; 'admin.mydomain.com, 192.168.100.1'");
    pln("With the above example vhost list, after installation the");
    pln("administration console will be accessible at the following");
    pln("URLs (assuming the domain names/IP resolve to your servers);");
    pln("  http://admin.mydomain.com/console/");
    pln("  http://192.168.100.1/console/");
    pln();

    String vhost_list = askString(
                               "Virtual host names for admin application: ",
                               vhost_list_valid_check);

    // Turn the vhost list into a list collection,
    StyledPrintWriter temp_spw = new IOWrapStyledPrintWriter(new StringWriter());
    Set<String> vhosts_set = AppCoreAdmin.parseVHostList(temp_spw, vhost_list);

//    vhosts_list = new ArrayList();
//    String[] vhost_arr = vhost_list.split(",");
//    for (String vhost : vhost_arr) {
//      vhost = vhost.trim();
//      if (vhost.length() > 0) {
//        vhosts_list.add(vhost.trim());
//      }
//    }

    pln();
    pln(" ---");
    pln("I now have enough information to go ahead with the");
    pln("initialization / installation of the Mckoi Web Platform.");
    pln();

    boolean confirm_install = askYNQ("Confirm installation? (Y/N/Q): ");

    if (!confirm_install) {
      pln();
      pln("-- Installation Aborted --");
      return;
    }

    // Create the install dev path for installing the console,
    if (!install_dev_path.mkdirs()) {
      epln("Error, unable to mkdir: " + install_dev_path.getCanonicalPath());
      throw new QuitException();
    }

    if (verified_install_dev_path.exists()) {
      // Copy the install_dev directory from the support directory,
      copyPath(verified_install_dev_path, install_dev_path);

      // Set the template properties,
      HashMap<String, Object> properties = new HashMap<>();

      if (http_port.trim().equals("80")) {
        properties.put("http_port_line", "#http_port = 80");
        properties.put("https_port_line", "#https_port = 443");
      }
      else {
        properties.put("http_port_line", "http_port = " + http_port.trim());
        properties.put("https_port_line", "#https_port = 443");
      }

      // Write the App Server configuration file into the install_dev,
      installFileTemplate(properties, new File(verified_templates_path, "app_service_properties.template"),
              install_dev_conf_path, "app_service.properties");
      installFileTemplate(properties, new File(verified_templates_path, "nodejs_app_service_properties.template"),
              install_dev_conf_path, "nodejs_app_service.properties");

      File lib_dest = new File(install_dev_path, "lib");
      lib_dest = new File(lib_dest, "base");

      // Copy the support library files to the lib directory,
      copyFile(verified_mwp_jar,
                 new File(lib_dest, verified_mwp_jar.getName()));
      copyFile(verified_ddb_jar,
                 new File(lib_dest, verified_ddb_jar.getName()));
      copyFile(verified_json_jar,
                 new File(lib_dest, verified_json_jar.getName()));

      // Copy the correct security policy into the install_dev directory,
      // If it's a window installation,
      File security_dest = new File(install_dev_path, "conf");
      String policy_to_copy_file_name;
      if (is_win) {
        policy_to_copy_file_name = "oraclejdk_mswindows_security.policy";
      }
      // Unix,
      else {
        policy_to_copy_file_name = "openjdk_linux_security.policy";
      }
      pln("Using security policy: " + policy_to_copy_file_name);
      copyFile(
             new File(verified_security_path, policy_to_copy_file_name),
             new File(security_dest, "security.policy"));

      pln("Finished building 'install_dev' path.");
    }
    else {
      pln("Copy failed: 'install_dev' path not found.");
      throw new QuitException();
    }

    // ----- MWP Installation -----
    
    // This initializes the system platform and process path, registers the
    // server and creates a normal install for the service.
    // Also creates an admin user account.

    // Standard output,
    StyledPrintWriter std_out = getPWriter();

    // The MckoiDDBClient and AppCoreAdmin objects,
    MckoiDDBClient ddb_client = MckoiDDBClientUtils.connectTCP(
                                   manager_servers, network_password, net_if);
    // Get the network profile and set the network conf for it,
    NetworkProfile network_profile = ddb_client.getNetworkProfile(null);
    network_profile.setNetworkConfiguration(net_config_resource);

    // Create the admin object,
    AppCoreAdmin app_core = new AppCoreAdmin(ddb_client, network_profile);

    // The path location of the root server,
    PathLocation sys_path = new PathLocation(
                              sys_plat_root_servers[0], sys_plat_root_servers);

    // Initialize the system platform,
    app_core.initializeSystemPlatform(std_out, sys_path);
    // Initialize the process service,
    app_core.initializeProcesses(std_out, sys_path);

    // Make sure the system tables are populated,
    app_core.populateSystemTables(std_out, ddb_network);

//    // Register the server,
//    app_core.registerServer(std_out,
//                            public_host_name,
//                            public_ip,
//                            private_ip);

    // --- Create the install ---

    // Create the 'normal' web/process install,
    app_core.createInstall(std_out, "normal");
    // Upload from the install_dev/ path,
    app_core.uploadInstall(std_out, "normal",
                           install_dev_path.getCanonicalPath());
    // Deploy this install,
    app_core.deployInstall(std_out, "normal");
    

    // --- Admin account ---

    // Create the file system resource for the admin user,
    app_core.addODBResource(std_out, "ufsadmin", sys_path);
    // Add the 'admin' account and user
    app_core.createAccount(std_out, "admin");
    app_core.addAccountUser(std_out, "admin", admin_username, admin_password);
    // Grant super user status to the admin account,
    app_core.grantSuperUser(std_out, "admin");

    // Connect the vhosts to the account and make a vhost string for the
    // application account,
    StringBuilder console_app_vhosts = new StringBuilder();
    for (String vhost_name : vhosts_set) {
      if (console_app_vhosts.length() != 0) {
        console_app_vhosts.append(",");
      }
      console_app_vhosts.append(vhost_name);
      // Connect the vhost to the account,
      app_core.connectVHost(std_out, vhost_name, "admin");
      pln("Connected vhost name " + vhost_name + " to admin account.");
    }

    // --- Install console application to the admin account ---

    app_core.addAccountApp(std_out, "admin", "console",
                           console_app_vhosts.toString(),
                           "console", "/apps/console/");
    pln("Added console application to admin account.");

    // Upload the console web application war,
    if (verified_console_webapp_war != null) {
      String war_ob_str = verified_console_webapp_war.getCanonicalPath();
      app_core.uploadAccountApp(std_out, "admin", "console", war_ob_str);
    }
    else {
      pln("Administration console war was not uploaded because");
      pln("it was not found in the support directory.");
    }

    // After completion, remind the user that they will need to start the
    // http/https/process role for this server using the bin/mwp script.

    pln();
    pln("-- Installation completed. --");

    pln();
    pln("The Mckoi Web Platform has successfully been initialized on");
    pln("the MckoiDDB network. Before you can access the administration");
    pln("console you must ensure that the 'http' and 'process' roles are");
    pln("operating on the servers in your network. To do this, you need");
    pln("to run the following script to register the server and roles");
    pln("for each application server in your network;");
    pln("  bin/mwp_register");
    pln();
    pln("Once the server is registered you will be able to access the");
    pln("administration console by going to one of the following URLs;");

    String http_port_string = "";
    if (!http_port.trim().equals("80")) {
      http_port_string = ":" + http_port.trim();
    }

    for (String vhost_name : vhosts_set) {
      String domain = null;
      if (vhost_name.endsWith(":http")) {
        domain = vhost_name.substring(0, vhost_name.length() - 5);
      }
      else if (vhost_name.endsWith(":https")) {
        domain = vhost_name.substring(0, vhost_name.length() - 6);
      }
      if (domain != null) {
        pln("  http://" + domain + http_port_string + "/console/");
      }
    }
    pln();
    pln("If you are unable to connect then make sure the Mckoi Web");
    pln("Platform and MckoiDDB services are running on all the servers");
    pln("in your network. The services can be started by running the");
    pln("'bin/run_services' script.");
    pln();



  }

  
  
  /**
   * The invocation start point.
   */
  public void startInstall(String[] args) {
    
    try {

      console = System.console();

      // Oops, no console
      if (console == null) {
        throw new RuntimeException(
                           "There doesn't appear to be a console available!");
      }

      // Make sure unimportant log messages don't end up going to the
      // terminal,
      Logger logger = Logger.getLogger("com.mckoi.network.Log");
      logger.setLevel(Level.SEVERE);


      pln("Welcome to the Mckoi Web Platform Node Installation Tool.");
      pln();
      pln("This is an interactive console application that installs the");
      pln("Mckoi Web Platform on the local machine. It can be used to");
      pln("create a stand-alone installation as well as build a Web");
      pln("Platform node to be part of a cluster of servers.");
      pln();
      fpln("First I need to check I can find the support files.");
      checkSupportFiles();

      fpln("Looking for JDK directory.");
      determineJDKPath();
      fpln("Looking for NodeJS executable location.");
      determineNodeJSExecutableLocation();
      
      pln();
      pln("=====");
      pln();

      // Initial menu,

      pln("The following initial options are available;");
      pln();
      pln(" 1. Build a stand-alone installation of the Mckoi Web Platform.");
      pln();
      pln("    Once a stand-alone installation is complete, the Mckoi Web");
      pln("    Platform services will be available on this machine only.");
      pln("    This simple installation is recommended if you are testing");
      pln("    or learning about the software.");
      pln();
      pln(" 2. Deploy the Mckoi software on this server to be run as a");
      pln("    node in a cluster of servers.");
      pln();
      pln("    This option installs software on this server that, once ");
      pln("    operating, will make this server be part of a MckoiDDB");
      pln("    and Mckoi Web Platform network of servers.");
      pln();
      pln(" 3. Initialize the Mckoi Web Platform on an operating MckoiDDB");
      pln("    network.");
      pln();
      pln("    This option installs and configures the Mckoi Web Platform");
      pln("    on a MckoiDDB network. This operation only needs to be");
      pln("    performed once per MckoiDDB network.");
      pln();

//      // Question 1,
//      pln("Do you wish to make a stand-alone installation of the Mckoi Web");
//      pln("Platform?");
//      pln();
//      pln("This option is for a single machine installation that is very");
//      pln("simple to set up and configure. Once completed, the Mckoi");
//      pln("services (web and process services) will be operating on a");
//      pln("stand-alone machine.");
//      pln("This is the recommended option if you are testing or learning");
//      pln("about the software.");
//      pln();
//      pln("Answering 'No' here will take you to the server deployment");
//      pln("installation process.");
//      pln();

      String option = askOpt("123q", "Enter Option (1,2,3,Q): ");

      if (option.equals("q")) {
        throw new QuitException();
      }

      // If we are doing a stand-alone install,
      if (option.equals("1")) {
        standAloneInstall();
      }
      // If deploy install
      else if (option.equals("2")) {
        deployInstall();
      }
      // If MWP initialize,
      else if (option.equals("3")) {
        initializeInstall();
      }

    }
    catch (QuitException e) {
      epln();
      epln("/!\\ Installation aborted.");
      epln();
    }
    catch (Exception e) {
      epln();
      epln("/!\\ There was an error and I had to abort the installation.");
      epln();
      String err_msg = e.getMessage();
      if (err_msg == null) {
        err_msg = e.getClass().getName();
      }
      epln("Error: " + err_msg);
      if (e instanceof NIException) {
        NIException niexception = (NIException) e;
        String[] exp = niexception.getExplain();
        if (exp != null) {
          for (String exp_line : exp) {
            epln(exp_line);
          }
        }
      }
      else {
        e.printStackTrace(System.err);
      }
    }

  }
  
  // ---- Answer validators ----
  
  /**
   * Checks to determine if the given path answer is either an existing
   * mckoi software installation point or an empty directory.
   */
  private AnswerValidator mwp_path_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      File f = new File(answer);
      File parent_f = f.getParentFile();

      if (parent_f == null) {
        epln("The given path is invalid.");
        return false;
      }

      if (f.exists()) {
        File install_dev = new File(f, "install_dev");
        if (install_dev.exists()) {
          epln("The given path already has an 'install_dev' sub-directory.");
          return false;
        }
      }

      return true;
    }
  };
  
  /**
   * Checks to determine if the given path answer is valid (can be made into
   * a directory on the local machine).
   */
  private AnswerValidator path_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      File f = new File(answer);
      File parent_f = f.getParentFile();

      if (parent_f == null) {
        epln("The given path is invalid.");
        return false;
      }

      if (f.exists()) {
        epln("Path already exists.");
        return false;
      }

      return true;
    }
  };

  private AnswerValidator http_port_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      try {
        // If nothing entered then valid default of port '80',
        if (answer.trim().equals("")) {
          return true;
        }
        // Try and parse the port into a number between 1 and 65535
        int port_num = Integer.parseInt(answer);
        if (port_num > 0 && port_num < 65536) {
          return true;
        }
      }
      catch (NumberFormatException e) {
        // Invalid,
      }
      return false;
    }
  };

  private AnswerValidator jdk_path_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      File f = new File(answer);

      if (!f.exists() || !f.isDirectory()) {
        epln("Given path does not exist.");
        return false;
      }
      
      if (!isJDKDirectory(f)) {
        epln("Unable to find lib/tools.jar at this location.");
        return false;
      }
      
      return true;
    }

  };

  private AnswerValidator nodejs_executable_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      File f = new File(answer);

      if (!f.exists() || !f.isDirectory()) {
        epln("The executable does not exist.");
        return false;
      }

      if (!isNodeJsExecutableLocation(f)) {
        epln("Unable to find node executable at this location.");
        return false;
      }

      return true;
    }
  };


  private AnswerValidator tcp_port_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      try {
        int port_val = Integer.parseInt(answer);
        if (port_val >= 1024 && port_val < 65536) {
          return true;
        }
        epln("TCP Port number is out of range.");
        return false;
      }
      catch (NumberFormatException e) {
        epln("Invalid number.");
        return false;
      }
    }
  };

  private AnswerValidator networkconf_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      // First check if it's a URL,
      try {
        URL url = new URL(answer);
        // Try and open it,
        URLConnection url_connection = url.openConnection();
        url_connection.connect();
        InputStreamReader reader =
               new InputStreamReader(url_connection.getInputStream(), "UTF-8");
        reader.read(new char[10], 0, 10);
        reader.close();
        // Looks good,
        return true;
      }
      catch (MalformedURLException e) {
        // Try it as a file,
        File file = new File(answer);
        if (file.exists() && file.isFile()) {
          return true;
        }
        epln("Not a URL or file.");
      }
      catch (IOException e) {
        epln("Unable to read URL.");
      }
      return false;
    }
  };

  /**
   * Checks to determine if the given IP address or host string is valid,
   */
  private AnswerValidator ip_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      try {
        InetAddress inet_addr = InetAddress.getByName(answer);
        return true;
      }
      catch (UnknownHostException ex) {
        epln("Unknown host name.");
        return false;
      }
    }
  };
  
  private AnswerValidator username_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      int sz = answer.length();
      if (sz < 3) {
        epln("Name must be at least 3 characters.");
        return false;
      }
      if (sz > 90) {
        epln("Name may not be larger than 90 characters.");
        return false;
      }
      for (int i = 0; i < sz; ++i) {
        char ch = answer.charAt(i);
        if (!Character.isLetterOrDigit(ch)) {
          epln("Invalid character.");
          return false;
        }
      }

      return true;
    }
  };
  
  private AnswerValidator password_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      int sz = answer.length();
      if (sz < 3) {
        epln("Name must be at least 3 characters.");
        return false;
      }
      if (sz > 90) {
        epln("Name may not be larger than 90 characters.");
        return false;
      }
      for (int i = 0; i < sz; ++i) {
        char ch = answer.charAt(i);
        if (!Character.isLetterOrDigit(ch)) {
          epln("Invalid character.");
          return false;
        }
      }

      return true;
    }
  };

  private AnswerValidator service_addrs_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      if (answer.trim().equals("")) {
        return true;
      }
      try {
        ServiceAddress.parseServiceAddresses(answer);
        return true;
      }
      catch (IOException e) {
        epln("Invalid server list string.");
        return false;
      }
      catch (NumberFormatException e) {
        epln("Invalid server list string.");
        return false;
      }
    }
  };

  /**
   * Checks for a valid client.conf file location.
   */
  private AnswerValidator clientconf_valid_check = new AnswerValidator() {
    @Override
    public boolean isValidAnswer(String answer) {
      File f = new File(answer);
      if (!f.exists()) {
        epln("File was not found.");
        return false;
      }
      if (f.isDirectory()) {
        File cc = new File(f, "client.conf");
        if (!cc.exists() || !cc.isFile()) {
          epln("'client.conf' not found at the given location.");
          return false;
        }
      }
      return true;
    }
  };

  /**
   * Checks a host name list is valid.
   */
  private AnswerValidator vhost_list_valid_check = new AnswerValidator() {

    @Override
    public boolean isValidAnswer(String answer) {
      StringWriter str_writer = new StringWriter();
      StyledPrintWriter spw = new IOWrapStyledPrintWriter(str_writer);

      // Parse the vhosts list,
      Set<String> vhost_set = AppCoreAdmin.parseVHostList(spw, answer);
      if (vhost_set == null) {
        epln("Invalid vhosts list.");
        return false;
      }

      if (vhost_set.isEmpty()) {
        epln("No vhosts in list.");
        return false;
      }

      // Check the domain contains only valid characters,
      for (String v : vhost_set) {
        String norm_v;
        if (v.endsWith(":http")) {
          norm_v = v.substring(0, v.length() - 5);
        }
        else if (v.endsWith(":https")) {
          norm_v = v.substring(0, v.length() - 6);
        }
        else {
          epln("Host name must end with ':http' or ':https' : " + v);
          return false;
        }
        for (int i = 0; i < norm_v.length(); ++i) {
          char ch = norm_v.charAt(i);
          if (!Character.isLetterOrDigit(ch) && ch != '.' && ch != '-') {
            epln("Host name contains invalid characters: " + norm_v);
            return false;
          }
        }
      }

      return true;

    }

  };


  public static void main(String[] args) {
    
    NodeInstallTool tool = new NodeInstallTool();
    tool.startInstall(args);
    
  }



  public interface AnswerValidator {
    boolean isValidAnswer(String answer);
  }

  public static class NIException extends RuntimeException {
    private final String[] explain;
    public NIException(String str) {
      super(str);
      this.explain = null;
    }
    public NIException(String str, String[] explain) {
      super(str);
      this.explain = explain;
    }
    public NIException(String str, Throwable e) {
      super(str, e);
      this.explain = null;
    }
    public String[] getExplain() {
      return explain;
    }
  }

  public static class QuitException extends RuntimeException {
    public QuitException() {
      super();
    }
  }

  private static final String[] DDB_STOP_EXPLAIN = new String[] {
      "The tool tried to start a MckoiDDB machine node on this machine",
      "but was unable to. The reason for the failure may be displayed",
      "above or in the log." };


  private class InterfaceInetAddress {

    InetAddress inet_address;
    String host_name;

    private InterfaceInetAddress(InetAddress inet_addr,
                                 String host_name) {
      this.inet_address = inet_addr;
      this.host_name = host_name;
    }

  }
  
  
  /**
   * A thread for a MckoiDDB node.
   */
  private class DDBNode extends Thread {

    private final File install_dir;
    private final String network_password;
    private final NetworkInterface network_if;
    private final Object finish_lock = new Object();
    private boolean finished;
    private NetworkConfigResource net_config_resource;

    // The instance,
    private TCPInstanceAdminServer inst;

    DDBNode(File install_dir, String network_password, NetworkInterface network_if) {
      this.finished = false;
      this.install_dir = install_dir;
      this.network_password = network_password;
      this.network_if = network_if;
    }

    public NetworkConfigResource getNetConfigResource() {
      return net_config_resource;
    }

    public void run() {

      try {
        // 'network.conf' file,
        String net_conf_str =
                      new File(install_dir, "network.conf").getCanonicalPath();

        // Parse the network configuration string,
        net_config_resource = NetworkConfigResource.parse(net_conf_str);

        // The host and port,
        Properties node_addr_prop = new Properties();
        FileReader node_addr_reader =
                         new FileReader(new File(install_dir, "node_info"));
        node_addr_prop.load(node_addr_reader);
        node_addr_reader.close();

        // Get the node configuration file,
        Properties node_config_resource = new Properties();
        FileInputStream node_conf_reader =
                       new FileInputStream(new File(install_dir, "node.conf"));
        node_config_resource.load(new BufferedInputStream(node_conf_reader));
        node_conf_reader.close();

        // Disable logging to the terminal,
        node_config_resource.setProperty("log_use_parent_handlers", "no");

        String host_str = node_addr_prop.getProperty("mckoiddbhost");
        String port_str = node_addr_prop.getProperty("mckoiddbport");

        InetAddress host = InetAddress.getByName(host_str);
        int port = Integer.parseInt(port_str);

        inst = new TCPInstanceAdminServer(net_config_resource,
                                          host, port, node_config_resource);
        inst.run();

        synchronized (finish_lock) {
          finished = true;
          finish_lock.notifyAll();
        }

      }
      catch (IOException e) {
        epln("IOException starting MckoiDDB server.");
        e.printStackTrace(System.err);
      }

    }

    void waitUntilStarted() {
      inst.waitUntilStarted();
    }

    NetworkProfile getNetworkProfile() throws IOException {
      TCPConnectorValues connector_values =
                        new TCPConnectorValues(network_password, network_if);
      NetworkProfile network_profile =
                                  NetworkProfile.tcpConnect(connector_values);
      network_profile.setNetworkConfiguration(net_config_resource);
      return network_profile;
    }

    /**
     * Stops the DDB node.
     */
    public void close() {
      inst.close();
    }

    public boolean isFinished() {
      return finished;
    }

    public void waitUntilFinished() {
      synchronized (finish_lock) {
        while (!finished) {
          try {
            finish_lock.wait(1000);
          }
          catch (InterruptedException ex) {
            throw new RuntimeException(ex);
          }
        }
      }
    }

  }
  
  
}
