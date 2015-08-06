
//
// Kill all command,
// 
// Kills all processes that match the given search criteria.
//

(function() {

  var CL = require('mwp/joptsimple');
  var Process = require('mwp/process');


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
    parser.acceptsAll( ['d'],
                      'Issue the kill operations as opposed to just explaining the command' );
    parser.acceptsAll( ['t', 'olderthan'],
                      'Only kill processes that are older than the given time (eg. "4 hours ago")' )
          .withRequiredArg().describedAs('time');

    parser.acceptsAll( ['9'],
                      'Forces a call to the \'close\' method of the process');

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
    var older_than = options.valueOf('t');
    var hard_kill = options.has('9');
    var explain_only = !options.has('d');

    var older_than_filter = null;

    // Perform the query,

    // The 'on_reply' closure,
    var on_reply_fun = function(result_msg) {

      var args = Process.decodeProcessMessage(result_msg);
      // Get and parse the JSON string,
      var json = args[0];

//      out.println("'" + json + "'", "debug");

      var total_count = 0;

      var machine_msgs = JSON.parse(json);
      for (var machine in machine_msgs) {
        var stat_ob = machine_msgs[machine];
        if (stat_ob !== 'unavailable') {
          total_count += stat_ob.count;
        }
      }

      // Explain how many processes are effected
      if (explain_only) {
        out.print("This 'killall' will stop ");
        out.print(String(total_count), "info");
        out.println(" processes (use -d to kill these processes)");
      }

      out.flush();

      return STOP;

    };

    // Invoke the servers query,
    var query = Process.ServersQuery.closeProcessesQuery(
			account_filter, app_filter, process_filter, older_than_filter, hard_kill, explain_only);
    Process.invokeServersQuery(query, on_reply_fun);

    return WAIT;

  }

})();
