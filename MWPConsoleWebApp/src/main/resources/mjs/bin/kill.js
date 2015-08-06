
//
// Kill Process
// 
// Kills a single process identified directly via a process identifier
// string.
//

(function() {

  var CL = require('mwp/joptsimple');
  var Process = require('mwp/process');

  //
  // Hard kill (calls 'closeProcessId' servers query)
  //
  function doHardKill(out, process_id) {

    // The 'on_reply' closure,
    var on_reply_fun = function(result_msg) {

      var args = Process.decodeProcessMessage(result_msg);
      // Get and parse the JSON string,
      var json = args[0];

      out.print("Hard close on: ");
      out.println(process_id, "info");
//      out.println("'" + json + "'", "debug");
      out.flush();

      return STOP;

    };
    
    // Invoke the 'closeProcessId' servers query,
    var query = Process.ServersQuery.closeProcessId(process_id);
    Process.invokeServersQuery(query, on_reply_fun);

    return WAIT;

  }

  //
  // Soft kill (sends a 'kill' signal to the process id)
  //
  function doSoftKill(out, process_id) {

    // Send a kill signal to the process,
    Process.sendSignal(process_id, ['kill']);

    out.print("Kill signal sent: ");
    out.println(process_id, "info");
    out.flush();

    return STOP;

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

    var process_id = non_args[1];

    // If the process id is not defined then report an error,
    if (typeof process_id === 'undefined') {
      out.println(non_args[0] + ": no process id given", "error");
      return STOP;
    }
    
    var hard_kill = options.has('9');
    
    if (hard_kill) {
      return doHardKill(out, process_id);
    }
    // Otherwise it's a soft kill (send a .kill signal to the process),
    else {
      return doSoftKill(out, process_id);
    }

    // Wait until response,
    return WAIT;

  }

})();
