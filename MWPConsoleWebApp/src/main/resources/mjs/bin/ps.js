
//
// Process Summary
// 
// Queries the process servers for information about currently active
// processes and displays the information to the user.
//

(function() {

  var CL = require('mwp/joptsimple');
  var Process = require('mwp/process');
  var TextUtil = require('mwp/textformat');

  /**
   * An object that parses the process information format string.
   */
  function PrcInfo(encoded_str) {

    var d1 = encoded_str.indexOf(':');
    var d2 = encoded_str.indexOf(':', d1 + 1);
    this.account = encoded_str.substring(0, d1);
    this.web_app = encoded_str.substring(d1 + 1, d2);
    this.process_name = encoded_str.substring(d2 + 1);

  }

  /**
   * The process summary style that shows all unique processes from all the process
   * servers.
   */
  function doPSStyle(out, account_filter, app_filter, process_filter, verbose) {

    // Global 'ps' command,
    var on_reply_fun = function(result_msg) {

      if (verbose) {
        out.println("  ACT INACT  TERM ACCOUNT     APP : PROCESS NAME");
      }
      else {
        out.println("  ACT INACT  TERM PROCESS NAME");
      }

      var args = Process.decodeProcessMessage(result_msg);
      // Get and parse the JSON string,
      var json = args[0];
      var machine_msgs = JSON.parse(json);

      // Merge the results from all the servers,
      var prc_map = {};
      var process_name, details_ob;

      for (var machine in machine_msgs) {

        // For each process on the machine,
        var processes = machine_msgs[machine];
        if (processes !== 'unavailable') {
          for (process_name in processes) {

            // Put in our output map,
            details_ob = prc_map[process_name];
            if (typeof details_ob === 'undefined') {
              details_ob = { act:0, inact:0, term:0 };
              prc_map[process_name] = details_ob;
            }

            // Get the details array,
            var details_arr = processes[process_name];

            // Update the counts for this process,
            details_ob.act += details_arr[0];
            details_ob.inact += details_arr[1];
            details_ob.term += details_arr[2];

          }
        }
      }

      // The keys sorted alphabetically,
      var sorted_prc_set = Object.keys(prc_map).sort();
      for (var i = 0, sz = sorted_prc_set.length; i < sz; ++i) {
        process_name = sorted_prc_set[i];
        // Get the details object,
        details_ob = prc_map[process_name];
        // A string representing the number of instances,
        out.print(TextUtil.rightAlign(String(details_ob.act), 4) + '  ');
        out.print(TextUtil.rightAlign(String(details_ob.inact), 4) + '  ');
        out.print(TextUtil.rightAlign(String(details_ob.term), 4) + '  ');
        // The process name
        var info = new PrcInfo(process_name);
        if (verbose) {
          out.print(TextUtil.leftAlign(String(info.account), 11) + ' ');
          out.print(info.web_app, "info");
          out.print(" : ");
        }
        out.print(info.process_name, "info");
        out.println();
      }

      out.flush();

      return STOP;

    };

    // Invoke the 'process summary' servers query,
    var query = Process.ServersQuery.processSummary(
                                    account_filter, app_filter, process_filter);
    Process.invokeServersQuery(query, on_reply_fun);

    // And wait,
    return WAIT;

  }

  /**
   * The process summary style that shows all individual processes from all the
   * process servers.
   */
  function doPSAllStyle(out, account_filter, app_filter, process_filter, verbose) {

    // The function to execute on reply,
    var on_reply_fun = function(result_msg) {

      if (verbose) {
        out.println("     LAST ACCESS   CALLS   BL            CPU  MACHINE                   ID");
      }
      else {
        out.println("     LAST ACCESS   CALLS   BL            CPU  MACHINE                   ID");
      }

      var args = Process.decodeProcessMessage(result_msg);
      // Get and parse the JSON string,
      var json = args[0];
      var machine_msgs = JSON.parse(json);

      // Merge the results from all the servers,
      var prc_map = {};
      var process_id, details_ob;

      for (var machine in machine_msgs) {

        // For each process on the machine,
        var processes = machine_msgs[machine];
        if (processes !== 'unavailable') {
          for (process_id in processes) {
            // Put in our output map,
            details_ob = prc_map[process_id];
            if (typeof details_ob === 'undefined') {
              // Get the details array,
              var details_arr = processes[process_id];
              details_ob = {
                lastaccess:details_arr[0],
                callcount:details_arr[1],
                broadcastlisteners:details_arr[2],
                cpu:details_arr[3],
                location:machine
              };
              prc_map[process_id] = details_ob;
            }
          }
        }

      }

      // The keys sorted alphabetically,
      var sorted_prc_set = Object.keys(prc_map).sort();
      for (var i = 0, sz = sorted_prc_set.length; i < sz; ++i) {
        process_id = sorted_prc_set[i];
        // Get the details object,
        details_ob = prc_map[process_id];
        // Format timestamp,
        var access_tsstring = TextUtil.formatDateTimeString(details_ob.lastaccess);
        var cpu_string = TextUtil.formatTimeFrame(String(details_ob.cpu));
        // A string representing the number of instances,
        out.print(TextUtil.rightAlign(access_tsstring, 17) + ' ');
        out.print(TextUtil.rightAlign(String(details_ob.callcount), 6) + ' ');
        out.print(TextUtil.rightAlign(String(details_ob.broadcastlisteners), 4) + ' ');
        out.print(TextUtil.rightAlign(cpu_string, 15) + ' ');
        out.print(TextUtil.leftAlign(String(details_ob.location), 25) + ' ', "info");
        // The process id string,
        out.print(process_id, "info");
        out.println();
      }

      out.flush();

      return STOP;

    };

    // Invoke the 'allProcessIdsOf' servers query,
    var query = Process.ServersQuery.allProcessIdsOf(
                                    account_filter, app_filter, process_filter);
    Process.invokeServersQuery(query, on_reply_fun);

    // And wait,
    return WAIT;

  }

  //
  // The initialization function.
  //
  exports.init = function(ctx, vars) {
    return CONSOLE_MODE;
  }

  //
  // The command process function.
  //
  exports.process = function(ctx, vars, out) {

    var parser = new CL.OptionParser();
    parser.acceptsAll( ['help', '?'],
                      'Show this help');
    parser.acceptsAll( ['u', 'account'],
                      'Filter output by the account name' )
          .withRequiredArg().describedAs('account name');
    parser.acceptsAll( ['a', 'application'],
                      'Filter output by the application name' )
          .withRequiredArg().describedAs('app name');
    parser.acceptsAll( ['p', 'process'],
                      'Filter output by the process name' )
          .withRequiredArg().describedAs('process name');
    parser.acceptsAll( ['i', 'ids'],
                      'Show individual process ids' );
    parser.acceptsAll( ['v'],
                      'Verbose output (show account names)');

    // Parse the command line,
    var options;
    try {
      options = parser.parse( vars.cline );
    }
    catch (e) {
      out.println(e, "error");
      return STOP;
    }

    // If has the 'help' option,
    if (options.has('help')) {
      parser.printHelpOn(out);
      return STOP;
    }

    var non_args = options.nonOptionArguments();

    var account_filter = options.valueOf('account');
    var app_filter = options.valueOf('application');
    var process_filter = options.valueOf('process');
    var verbose = options.has('v');
    var ids_style = options.has('i');

    // Perform the function,
    if (!ids_style) {
      return doPSStyle(out, account_filter, app_filter, process_filter, verbose);
    }
    else {
      return doPSAllStyle(out, account_filter, app_filter, process_filter, verbose);
    }

  }

})();
