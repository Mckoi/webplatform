/**
 * com.mckoi.process.impl.ServersQueryFunctions  Nov 29, 2012
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

import com.mckoi.process.ByteArrayProcessMessage;
import com.mckoi.process.ProcessId;
import com.mckoi.process.ProcessMessage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Performs the servers query commands.
 *
 * @author Tobias Downer
 */

class ServersQueryFunctions {

  /**
   * A default kill signal message.
   */
  public static final ProcessMessage KILL_SIGNAL_MESSAGE;
  static {
    KILL_SIGNAL_MESSAGE = ByteArrayProcessMessage.encodeArgs("kill");
  }




  static boolean processMatches(ProcessInstanceImpl instance,
          String query_account, String query_application,
          String query_process_name) throws SuspendedProcessException {

    AccountApplication account_app = instance.getAccountApplication();
    String process_name = instance.getProcessName();

    if (query_account == null ||
        query_account.equals(account_app.getAccountName())) {

      if (query_application == null ||
          query_application.equals(account_app.getApplicationName())) {

        if (query_process_name == null ||
            process_name.startsWith(query_process_name)) {

          return true;

        }

      }

    }

    return false;

  }
  
  
  



  static String serverProcessSummary(
                                  ProcessServerService service, Object[] args) {

    // Get the process set,
    ProcessSet process_set = service.getProcessSet();

    // The user name,
    String query_account = (String) args[2];
    String query_webapp = (String) args[3];
    String query_process_name = (String) args[4];
    
    Iterator<ProcessId> all_managed = process_set.getAllManaged();

    Map<String, Object[]> process_summary = new HashMap();

    while (all_managed.hasNext()) {
      // For each instance,
      ProcessId process_id = all_managed.next();
      if (process_id != null) {
        ProcessInstanceImpl instance = process_set.getInstance(process_id);
        if (instance != null) {

          try {
            AccountApplication account_app = instance.getAccountApplication();
            String process_name = instance.getProcessName();
            boolean is_terminated = instance.isTerminated();
            boolean is_disposable = instance.isDisposable();

            // If not terminated and not disposable, then it's active,
            // If not terminated and is disposable, then it's inactive,
            // Otherwise it's terminated,
            
            int status;
            if (is_terminated) {
              status = 2;
            }
            else if (is_disposable) {
              status = 1;
            }
            else {
              status = 0;
            }

            if (processMatches(instance,
                          query_account, query_webapp, query_process_name)) {
              // A concatenation of the acount name, application name
              // and process name.
              String app_process_name =
                      account_app.getAccountName() + ":" +
                      account_app.getApplicationName() + ":" +
                      process_name;

              // If there's a '.state' key in the state map,
              String dot_script = instance.getStateMap().get(".script");
              if (dot_script != null && dot_script.length() > 0) {
                // Append it to 'app_process_name'
                app_process_name = app_process_name + "$" + dot_script;
              }

              // Put this in the summary,
              Object[] summary = process_summary.get(app_process_name);
              if (summary == null) {
                summary = new Object[] { 0, 0, 0 };
                process_summary.put(app_process_name, summary);
              }
              // Increment the count,
              summary[status] = ((Integer) summary[status]) + 1;
            }
          }
          catch (SuspendedProcessException e) {
            // If it's a suspended process then we don't count it,
          }
          finally {
            instance.preventRemoveUnlock();
          }
        }
      }
    }

    // Format the process summary into a return message,
    StringBuilder json_out = new StringBuilder();
    json_out.append("{");
    boolean first = true;
    // Note that this will truncate the returned map if the size of the
    // message will exceed 40000 characters.
    for (String key_str : process_summary.keySet()) {
      if (!first) {
        json_out.append(",");
      }
      Object[] val = process_summary.get(key_str);
      JSONArray json_arr = new JSONArray(Arrays.asList(val));
      String json_ob_str =
              JSONObject.quote(key_str) + ":" +
              json_arr.toString();
      if (json_out.length() + json_ob_str.length() > 40000) {
        // We have to end it here, the object is too large!
        break;
      }
      json_out.append(json_ob_str);
      first = false;
    }
    json_out.append("}");

    // Encode the output,
    return json_out.toString();

  }

  /**
   * Returns a JSON string containing all the process ids (as string value)
   * that match the input criteria.
   */
  private static String serverAllProcessIds(
                                 ProcessServerService service, Object[] args) {

    // Get the process set,
    ProcessSet process_set = service.getProcessSet();

    // The user name,
    String query_account = (String) args[2];
    String query_webapp = (String) args[3];
    String query_process_name = (String) args[4];
    
    Iterator<ProcessId> all_managed = process_set.getAllManaged();

    // Format the process summary into a return message,
    StringBuilder json_out = new StringBuilder();
    json_out.append("{");
    boolean first = true;

    while (all_managed.hasNext()) {
      // For each instance,
      ProcessId process_id = all_managed.next();
      if (process_id != null) {
        ProcessInstanceImpl instance = process_set.getInstance(process_id);
        if (instance != null) {

          try {
//            AccountApplication account_app = instance.getAccountApplication();
//            String process_name = instance.getProcessName();

            // If this matches the instance,
            if (processMatches(instance,
                          query_account, query_webapp, query_process_name)) {

              if (!first) {
                json_out.append(",");
              }

              String key_str = instance.getId().getStringValue();

              // The number of broadcast listeners on this instance,
              long last_access_ts = instance.lastAccessTimestamp();
              int function_call_count = instance.functionCallCount();
              int broadcast_count = instance.getBroadcastListenersSize();
              long cpu_time_nanos = instance.functionCPUTimeNanos();

              Object[] details = new Object[] {
                Long.toString(last_access_ts),
                function_call_count,
                broadcast_count,
                Long.toString(cpu_time_nanos)
              };

              JSONArray json_arr = new JSONArray(Arrays.asList(details));
              String json_ob_str =
                      JSONObject.quote(key_str) + ":" +
                      json_arr.toString();

              if (json_out.length() + json_ob_str.length() > 40000) {
                // We have to end it here, the object is too large!
                break;
              }

              json_out.append(json_ob_str);
              first = false;

            }
          }
          catch (SuspendedProcessException e) {
            // If it's a suspended process then we don't count it,
          }
          finally {
            instance.preventRemoveUnlock();
          }
        }
      }
      
    }

    json_out.append("}");

    // Encode the output,
    return json_out.toString();

  }

  /**
   * Closes the given process id.
   */
  private static String serverCloseProcessId(
                                ProcessServerService service, Object[] args) {

    // Get the process set,
    ProcessSet process_set = service.getProcessSet();

    String process_id_str = (String) args[2];
    ProcessId process_id = ProcessId.fromString(process_id_str);

    ProcessInstanceImpl instance = process_set.getInstance(process_id);
    if (instance != null) {
      try {

        // Close the instance,
        instance.close();

        // Return 'done' result,
        return "{\"result\":\"done\"}";

      }
      finally {
        instance.preventRemoveUnlock();
      }
    }

    return "{\"result\":\"not found\"}";

  }

  /**
   * Closes all processes on this machine that are older than the given time
   * point.
   */
  private static String serverCloseProcessIdsOlderThan(
                                ProcessServerService service, Object[] args) {

    // Get the process set,
    ProcessSet process_set = service.getProcessSet();

    // The user name,
    String query_account = (String) args[2];
    String query_webapp = (String) args[3];
    String query_process_name = (String) args[4];

    long timestamp = Long.MAX_VALUE;
    if (args[5] != null) {
      timestamp = Long.parseLong((String) args[5]);
    }

    // Status flags,
    boolean hard_kill = args[6].equals("hard");
    boolean count_only = args[7].equals("count");

    // All managed processes,
    Iterator<ProcessId> all_managed = process_set.getAllManaged();

    int count = 0;

    // Format the process summary into a return message,
    while (all_managed.hasNext()) {
      // For each instance,
      ProcessId process_id = all_managed.next();
      if (process_id != null) {
        ProcessInstanceImpl instance = process_set.getInstance(process_id);
        if (instance != null) {

          try {
//            AccountApplication account_app = instance.getAccountApplication();
//            String process_name = instance.getProcessName();

            // If this matches the instance,
            if (processMatches(instance,
                          query_account, query_webapp, query_process_name)) {

              // The number of broadcast listeners on this instance,
              long last_access_ts = instance.lastAccessTimestamp();
              if (last_access_ts < timestamp) {
                if (!count_only) {
                  // If it's a hard kill,
                  if (hard_kill) {
                    instance.close();
                  }
                  // Otherwise just send a kill signal to the process,
                  else {
                    instance.putAnonymousSignalOnQueue(KILL_SIGNAL_MESSAGE);
                    service.notifyMessagesAvailable(process_id);
                  }
                }
                ++count;
              }

            }

          }
          catch (SuspendedProcessException e) {
            // If it's a suspended process then we don't count it,
          }
          finally {
            instance.preventRemoveUnlock();
          }
        }
      }
      
    }

    // Encode the output,
    String json_out = "{\"result\":\"done\",\"count\":" + count + "}";
    return json_out.toString();

  }

  static String executeServersQuery(
                                ProcessServerService service, Object[] args) {
    
    String cmd = (String) args[1];

    String result_str;
    
    // Process summary,
    // This command will provide a summary of all processes run by the
    // given user,
    if (cmd.equals("ps")) {
      result_str = serverProcessSummary(service, args);
    }

    else if (cmd.equals("all_ids")) {
      result_str = serverAllProcessIds(service, args);
    }

    else if (cmd.equals("close_pid")) {
      result_str = serverCloseProcessId(service, args);
    }

    else if (cmd.equals("close_older_than")) {
      result_str = serverCloseProcessIdsOlderThan(service, args);
    }

    // Unknown command on this server,
    else {
      result_str = "noop";
    }
    
    return result_str;
  }

}
