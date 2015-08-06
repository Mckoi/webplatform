/**
 * com.mckoi.process.ServersQuery  Nov 28, 2012
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

package com.mckoi.process;

/**
 * A query that is to be executed on one or more process servers, to discover
 * information about the current state of the servers (such as the processes
 * currently being run on them).
 *
 * @author Tobias Downer
 */

public class ServersQuery {

  /**
   * The arguments.
   */
  private final Object[] args;
  

  private ServersQuery(Object[] args) {
    this.args = args;
  }

  /**
   * Return the query arguments for this query.
   */
  public Object[] getQueryArgs() {
    return args;
  }

  /**
   * Returns true if this is a query that can be resolved locally
   */
  public boolean isLocal() {
    String cmd = (String) args[0];
    return (cmd.equals("all_process_srvs"));
  }

  // -----

  /**
   * A servers query that finds all information regarding the current
   * processes being run on all the process servers, and for each process
   * return a summary of the process. The summary includes the total number of
   * active and terminated instances of the process type. Only returns
   * information regarding the processes that the account is permitted to
   * query.
   * <p>
   * The result is a JSON string which contains a map of 'machine name' to
   * 'processes object'. The 'processes object' is a map of 'process name' to
   * 'process details array'. The 'process details array' contains 3 integers
   * representing the active, inactive and terminated count of processes of
   * the given type.
   * <p>
   * The arguments will filter the result by account name, web application name
   * and process name. If any argument is null, all accessible values will be
   * returned for that type.
   */
  public static ServersQuery processSummary(
              String account_name, String web_app_name, String process_name) {

    return new ServersQuery(new Object[]
                      { "ps", account_name, web_app_name, process_name });

  }

  /**
   * Returns a JSON object where each key represents a process server machine
   * currently available on the network.
   */
  public static ServersQuery allProcessMachineNames() {

    return new ServersQuery(new Object[]
              { "all_process_srvs" });

  }

  /**
   * A query that finds all process id's that match the given criteria, and
   * that are accessible to the account that invokes the query. This will
   * find the individual process ids and any associated detail information
   * about the process (such as whether the process is active, and analytics
   * information).
   * <p>
   * The result is a JSON string which contains a map of 'machine name' to
   * 'processes object'. The 'processes object' is a map of 'process id string'
   * to 'process details array'. The 'process details array' contains values
   * associated with the current process status.
   * <p>
   * The arguments will filter the result by account name, web application name
   * and process name. If any argument is null, all accessible values will be
   * returned for that type.
   */
  public static ServersQuery allProcessIdsOf(
              String account_name, String web_app_name, String process_name) {

    return new ServersQuery(new Object[]
              { "all_ids", account_name, web_app_name, process_name });

  }

  /**
   * Forcibly closes a specific process id by calling the 'close' method in
   * the process instance. This does not guarantee that a process will be
   * 'killed' because a process could be stuck in an infinite loop and not
   * checking for kill signals.
   * <p>
   * The result is a JSON string which contains a map of 'machine name' to
   * 'machine result'. The 'machine result' is a singleton '{'result':'done'}'.
   */
  public static ServersQuery closeProcessId(String process_id_str) {

    return new ServersQuery(new Object[]
              { "close_pid", process_id_str });

  }

  public static ServersQuery closeProcessId(ProcessId process_id) {

    return closeProcessId(process_id.getStringValue());

  }

  /**
   * A query that contacts all process servers for processes that match the
   * given criteria, and if the process is older than the given timestamp then
   * the 'close' method is called on the process instance. This does not
   * guarantee that a process will be 'killed' because a process could be stuck
   * in an infinite loop and not checking for kill signals.
   * <p>
   * The result is a JSON string which contains a map of 'machine name' to
   * 'machine result'. The 'machine result' is a singleton '{'result':'done'}'.
   */
  public static ServersQuery closeProcessesQuery(
              String account_name, String web_app_name, String process_name,
              String timestamp_long, boolean hard_kill, boolean count_only) {

    return new ServersQuery(new Object[]
              { "close_older_than", account_name, web_app_name, process_name,
                timestamp_long,
                hard_kill ? "hard" : "",
                count_only ? "count" : ""
              });

  }

}
