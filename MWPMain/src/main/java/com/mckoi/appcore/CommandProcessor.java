/**
 * com.mckoi.appcore.CommandProcessor  Sep 27, 2012
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

import com.mckoi.util.StyledPrintWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * An object that processes Mckoi Web Platform commands and dispatches the
 * commands to an AppCoreAdmin instance as appropriate.
 *
 * @author Tobias Downer
 */

public class CommandProcessor {

  /**
   * The AppCoreAdmin object.
   */
  private final AppCoreAdmin app_core_admin;

  private final Map<String, AppCoreCommand> command_map;
  private final List<AppCoreCommand> command_order;


  /**
   * Constructor.
   */
  public CommandProcessor(AppCoreAdmin app_core_admin) {
    this.app_core_admin = app_core_admin;
    this.command_map = new HashMap();
    this.command_order = new ArrayList();

    initCommandMap();

  }

  /**
   * Initializes the command map/documentation.
   */
  private void initCommandMap() {

    // Add the commands to the map,

    command_map.put("help", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          printHelp(out, args);
        }
      });

    command_map.put("initialize system", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.initializeSystemPlatform(out, (PathLocation) args[0]);
        }
      });

    command_map.put("initialize process", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.initializeProcesses(out, (PathLocation) args[0]);
        }
      });
    
    command_map.put("update network", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.updateNetwork(out);
        }
      });

    command_map.put("create install", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.createInstall(out, (String) args[0]);
        }
      });

    command_map.put("upload install", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.uploadInstall(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("deploy install", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.deployInstall(out, (String) args[0]);
        }
      });

    command_map.put("register server", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.registerServer(out, (String) args[0], (String) args[1], (String) args[2]);
        }
      });

    command_map.put("deregister server", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.deregisterServer(out, (String) args[0]);
        }
      });

    command_map.put("start role", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.startRole(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("stop role", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.stopRole(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("start ddb role", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.startDDBRole(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("stop ddb role", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.stopDDBRole(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("create odb resource", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.addODBResource(out, (String) args[0], (PathLocation) args[1]);
        }
      });

    command_map.put("add account user", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.addAccountUser(out, (String) args[0], (String) args[1], (String) args[2]);
        }
      });

    command_map.put("remove account user", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.removeAccountUser(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("set account user password",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.setAccountUserPassword(out, (String) args[0], (String) args[1], (String) args[2]);
        }
      });

    command_map.put("invalidate account user sessions",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.cleanAllAccountUserSessions(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("create account", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.createAccount(out, (String) args[0]);
        }
      });

    command_map.put("deactivate account",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.deactiveAccount(out, (String) args[0]);
        }
      });

    command_map.put("add account app", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.addAccountApp(out, (String) args[0], (String) args[1],
                            (String) args[2], (String) args[3],
                            (String) args[4]);
        }
      });

    command_map.put("remove account app", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.removeAccountApp(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("clean account apps", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.cleanAccountApps(out, (String) args[0]);
        }
      });

    command_map.put("upload account app", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.uploadAccountApp(out, (String) args[0],
                                (String) args[1], (String) args[2]);
        }
      });

    command_map.put("refresh all account apps",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.refreshAllAccountApps(out, (String) args[0]);
        }
      });

    command_map.put("refresh account app",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.refreshAccountApp(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("copy local",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          String local_dir = (String) args[0];
          String account = (String) args[1];
          String account_dir = (String) args[2];
          app_core_admin.copyLocalTo(out, local_dir, account, account_dir);
        }
      });

    command_map.put("copy account dir",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          String account = (String) args[0];
          String account_dir = (String) args[1];
          String local_dir = (String) args[2];
          app_core_admin.copyAccountDirTo(out, account, account_dir, local_dir);
        }
      });

    command_map.put("unzip local",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          String local_zip = (String) args[0];
          String account = (String) args[1];
          String account_dir = (String) args[2];
          app_core_admin.unzipLocalTo(out, local_zip, account, account_dir);
        }
      });

    command_map.put("connect vhost", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.connectVHost(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("disconnect vhost", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.disconnectVHost(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("connect resource", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.connectResource(out, (String) args[0], (String) args[1]);
        };
      });

    command_map.put("disconnect resource", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.disconnectResource(out, (String) args[0], (String) args[1]);
        }
      });

    command_map.put("grant superuser", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.grantSuperUser(out, (String) args[0]);
        }
      });

    command_map.put("revoke superuser", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.revokeSuperUser(out, (String) args[0]);
        }
      });

    command_map.put("add ddb path", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.addDDBPath(out,
                   (String) args[0], (String) args[1], (PathLocation) args[2]);
        }
      });

    command_map.put("check network", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.checkNetwork(out);
        }
      });

    command_map.put("fix block availability", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.fixBlockAvailability(out);
        }
      });

    command_map.put("rollback path",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.rollbackPath(out,
                  (String) args[0], (String) args[1]);
        }
      });



    command_map.put("show vhosts", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showVHosts(out);
        }
      });

    command_map.put("show account apps", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showAccountApps(out, (String) args[0]);
        }
      });

    command_map.put("show accounts", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showAccounts(out);
        }
      });

    command_map.put("show account", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showAccount(out, (String) args[0]);
        }
      });

    command_map.put("show installs", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showInstalls(out);
        }
      });

    command_map.put("show servers", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          String sortby = "name";
          if (args.length > 0) {
            sortby = (String) args[0];
          }
          app_core_admin.showServers(out, sortby);
        }
      });

    command_map.put("show network", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showDDBNetwork(out);
        }
      });

    command_map.put("show status",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showStatus(out);
        }
      });

    command_map.put("show free", 
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showFree(out);
        }
      });

    command_map.put("show paths",
      new AppCoreCommand() {
        @Override
        public void execute(StyledPrintWriter out, Object[] args) throws Exception {
          app_core_admin.showPaths(out);
        }
      });


    // Now parse the commands.txt file and complete the data
    // This has to be a privileged action unfortunately,
    InputStream commands_txt =
           (InputStream) AccessController.doPrivileged(new PrivilegedAction() {
      @Override
      public Object run() {
        return AppCoreAdmin.class.getResourceAsStream(
                                        "/com/mckoi/appcore/doc/commands.txt");
      }
    });

    try {
      BufferedReader br =
              new BufferedReader(new InputStreamReader(commands_txt, "UTF-8"));
      
      AppCoreCommand current_cmd = null;
      while (true) {
        String line = br.readLine();
        if (line == null) {
          break;
        }
        if (line.startsWith("+")) {
          current_cmd = command_map.get(line.substring(1));
          if (current_cmd == null) {
            throw new RuntimeException("Unknown command in commands.txt: " + line.substring(1));
          }
          command_order.add(current_cmd);
        }
        else if (line.startsWith("!")) {
          current_cmd.setCommandPattern(line.substring(1));
        }
        else if (line.startsWith("#")) {
          // Ignore comment line
        }
        else {
          current_cmd.addLineToHelp(line);
        }
      }

      commands_txt.close();
    }
    catch (IOException e) {
      throw new RuntimeException("IO Error", e);
    }

    // Parse the patterns,
    for (AppCoreCommand cmd : command_order) {
      cmd.parsePattern();
    }

  }

  /**
   * Tries to match the command with the database of commands. If a command
   * matches it returns the match. Commands that are near matches are added
   * to the 'near_matches' list.
   */
  private AppCoreCommand tryMatch(
                   List<String> in_args,
                   List<AppCoreCommand> near_matches,
                   boolean fuzzy_near_matches) {

    // For each command,
    for (AppCoreCommand cmd : command_order) {

      List<String> parse = cmd.getParseObject();

      // Compare it to the source,
      boolean matched = true;
      int i = 0;
      while (true) {
        if (i >= parse.size() || i >= in_args.size()) {
          // Match condition,
          if (i == parse.size() && i == in_args.size()) {
            matched = true;
            break;
          }
          if (i < parse.size()) {
            // These are ok
            if (parse.get(i).charAt(0) == ')') {
              matched = true;
              break;
            }
            if (parse.get(i).charAt(0) == '}') {
              matched = true;
              break;
            }
          }
          matched = false;
          break;
        }
        String parsed_arg = parse.get(i);
        char pcode = parsed_arg.charAt(0);
        if (pcode == ']') {
          // Match anything,
        }
        else if (pcode == ')') {
          // Match anything,
        }
        else if (pcode == '}') {
          // Match anything,
          matched = true;
          break;
        }
        else if (pcode == '@') {
          // Match exact string,
          String str_to_match = parsed_arg.substring(1);
          if (!in_args.get(i).equalsIgnoreCase(str_to_match)) {
            matched = false;
            break;
          }
        }
        else {
          throw new RuntimeException("Unknown pcode: " + pcode);
        }
        ++i;
      }

      // If match,
      if (matched) {
        return cmd;
      }
      else {
        // If one or words matched then add to the near match list,
        if (fuzzy_near_matches) {
          if (i > 0) {
            near_matches.add(cmd);
          }
        }
        else {
          if (i == in_args.size()) {
            near_matches.add(cmd);
          }
        }
      }

    }

    return null;

  }


  /**
   * Prints out the help for the administration tool.
   */
  public void printHelp(StyledPrintWriter out, Object[] args) throws IOException {

    out.println("MWP Administration (GPL)", StyledPrintWriter.INFO);
    out.println("See LICENSE.txt for license details.");
    out.println();
    
    if (args.length == 0) {

      out.println("Summary of all commands;");
      for (AppCoreCommand cmd : command_order) {
        out.println(" " + cmd.getCommandPattern(), StyledPrintWriter.INFO);
      }
      out.println();
      out.println("For specific help on a command use;");
      out.println(" mwp help [command name]", StyledPrintWriter.INFO);

    }
    else {

      List<String> in_args = new ArrayList(args.length);
//      StringBuilder b = new StringBuilder();
//      boolean first = true;
      for (Object arg : args) {
//        if (first) {
//          first = false;
//        }
//        else {
//          b.append(" ");
//        }
//        b.append(arg.toString());
        in_args.add(arg.toString());
      }
//      String command_str = b.toString();
//      out.println("Help for command: " + command_str);
//      out.println();

      // Try matching,
      List<AppCoreCommand> near_matches = new ArrayList();
      AppCoreCommand cmd = tryMatch(in_args, near_matches, false);

      // Use the first near match if an exact command not found,
      if (cmd == null && near_matches.size() == 1) {
        cmd = near_matches.get(0);
      }

      if (cmd == null) {
        out.println("Command not found.", StyledPrintWriter.ERROR);
        if (!near_matches.isEmpty()) {
          out.println();
          out.println(" Did you mean?");
          for (AppCoreCommand c : near_matches) {
            out.println("  " + c.getCommandPattern(), StyledPrintWriter.INFO);
          }
        }
        else {
          out.println();
          out.println("Use 'mwp help' for a list of all commands.",
                      StyledPrintWriter.INFO);
        }
      }
      else {
        // Print the command description,
        out.println(cmd.getCommandPattern(), StyledPrintWriter.INFO);
        out.println();
        String help_description = cmd.getCommandHelp();
        if (help_description != null) {
          out.println(help_description);
        }
      }

      

    }

  }




  /**
   * An administration command that can be executed.
   */
  public static abstract class AppCoreCommand {

    private String cmd_pattern;
    private String cmd_help;
    private List<String> cmd_parse = new ArrayList();

    AppCoreCommand() {
    }

    /**
     * A human and machine understandable description (prototype) of the
     * command.
     */
    public String getCommandPattern() {
      return cmd_pattern;
    }

    public String getCommandHelp() {
      return cmd_help;
    }

    private void setCommandPattern(String pattern) {
      cmd_pattern = pattern;
    }

    private void addLineToHelp(String line) {
      if (line == null) {
        return;
      }
      if (cmd_help == null) {
        cmd_help = line;
      }
      else {
        cmd_help = cmd_help + "\n" + line;
      }
    }

    private List<String> getParseObject() {
      return cmd_parse;
    }

    /**
     * Parse the pattern.
     */
    private void parsePattern() {
      // Parse the description
      String command_pattern = getCommandPattern();
      List<String> parse = new ArrayList();
      while (command_pattern.length() > 0) {
        if (command_pattern.startsWith("[")) {
          // This is an argument,
          int end = command_pattern.indexOf("]");
          parse.add("]" + command_pattern.substring(1, end));
          command_pattern = command_pattern.substring(end + 1).trim();
        }
        else if (command_pattern.startsWith("([[")) {
          int end = command_pattern.indexOf("]])");
          parse.add("}" + command_pattern.substring(3, end));
          command_pattern = command_pattern.substring(end + 3).trim();
        }
        else if (command_pattern.startsWith("([")) {
          int end = command_pattern.indexOf("])");
          parse.add(")" + command_pattern.substring(2, end));
          command_pattern = command_pattern.substring(end + 2).trim();
        }
        else {
          int end = command_pattern.indexOf(" ");
          if (end == -1) {
            end = command_pattern.length();
          }
          parse.add("@" + command_pattern.substring(0, end));
          command_pattern = command_pattern.substring(end).trim();
        }
      }
      // Set the parse list,
      cmd_parse = Collections.unmodifiableList(parse);
    }

    public abstract void execute(StyledPrintWriter out, Object[] commands)
                                                              throws Exception;


  }
  
  /**
   * Turns a string of the given type into the object specified.
   */
  private Object toACType(String str, String type)
                                     throws InvalidTypeException, IOException {

    // Root location type,
    if (type.equals("ddb root server(s)")) {
      try {
        return AppCoreAdmin.parsePathLocation(str);
      }
      catch (AppCoreAdmin.InvalidPathLocationException e) {
        throw new InvalidTypeException(e);
      }
    }
    // Otherwise just return it as a string,
    else {
      return str;
    }

  }

  /**
   * Processes a single command and any display output is sent to the given
   * StyledPrintWriter. Returns true if the command was known and successfully
   * processed. Returns false if the command is not known or formatted
   * incorrectly. Throws an exception if the command could not process because
   * of an error.
   * <p>
   * The 'in_args' list is the split command argument. For example, the
   * command;
   * <code>
   *   upload install "normal" "/mckoiwp/install_dev/"
   * </code>
   * would be represented by the list;
   * <code>
   *   { "upload", "install", "normal", "/mckoiwp/install_dev/" }
   * </code>
   */
  public boolean processCommand(StyledPrintWriter out, List<String> in_args)
                                                             throws Exception {

    // Show help if no command given,
    if (in_args.isEmpty()) {
      printHelp(out, new Object[0]);
      return true;
    }

    // Try and match the command,
    List<AppCoreCommand> near_match = new ArrayList();
    AppCoreCommand matched_command = tryMatch(in_args, near_match, true);

    // Did we find a matched command?
    if (matched_command != null) {

      List<Object> args = new ArrayList();
      List<String> parse = matched_command.getParseObject();

      try {
        // Yes, ok, parse the arguments,
        for (int i = 0; i < parse.size(); ++i) {
          String pstr = parse.get(i);
          if (pstr.startsWith("]")) {
            args.add(toACType(in_args.get(i), pstr.substring(1)));
          }
          else if (pstr.startsWith(")")) {
            if (i < in_args.size()) {
              args.add(toACType(in_args.get(i), pstr.substring(1)));
            }
          }
          else if (pstr.startsWith("}")) {
            for (int n = i; n < in_args.size(); ++n) {
              args.add(in_args.get(n));
            }
            break;
          }
        }

        // Turn the args list into an array and execute the command,
        Object[] args_arr = args.toArray();
        matched_command.execute(out, args_arr);

      }
      catch (InvalidTypeException e) {
        out.print("Error in command: ", StyledPrintWriter.ERROR);
        out.println(matched_command.getCommandPattern(),
                    StyledPrintWriter.ERROR);
        out.println("Unable to parse arguments in command because of the following error:",
                    StyledPrintWriter.ERROR);
        out.println(e.getMessage(),
                    StyledPrintWriter.ERROR);
      }

      return true;
    }

    // If no exact match then help the user out with some suggestions,
    else {
      out.println("Command not found.");
      if (!near_match.isEmpty()) {
        out.println();
        out.println(" Did you mean?");
        for (AppCoreCommand cmd : near_match) {
          out.print("  ");
          out.println(cmd.getCommandPattern());
        }
      }
      out.println();
    
      return false;
    }

  }
  
  /**
   * An exception when unable to parse a string into the requested type.
   */
  private static class InvalidTypeException extends RuntimeException {
    public InvalidTypeException() {
    }
    public InvalidTypeException(Throwable cause) {
      super(cause);
    }
    public InvalidTypeException(String message, Throwable cause) {
      super(message, cause);
    }
    public InvalidTypeException(String message) {
      super(message);
    }
  }

}
