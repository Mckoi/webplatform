//
// The Mckoi Web Platform JavaScript API for starting new processes and
// invoking functions on processes.
//

(function() {

  var JSWrapBase =
        Packages.com.mckoi.webplatform.rhino.JSWrapBase;
//  var JSWrapProcessOperation =
//        Packages.com.mckoi.webplatform.rhino.JSWrapProcessOperation;
  var PlatformContextFactory =
        Packages.com.mckoi.webplatform.PlatformContextFactory;
//  var ByteArrayProcessMessage =
//        Packages.com.mckoi.process.ByteArrayProcessMessage;
  var ProcessId =
        Packages.com.mckoi.process.ProcessId;
  var ProcessChannel =
        Packages.com.mckoi.process.ProcessChannel;
  var BroadcastSWriter =
        Packages.com.mckoi.apihelper.BroadcastSWriter;

  //
  // Returns true if the given obj is a JavaScript or Java string.
  //
  function isString(obj) {
    var obj_typeof = typeof obj;
    return (obj_typeof === 'string' ||
            (obj_typeof === 'object' && obj.getClass() === 'java.lang.String'));
  }

  //
  // If the process id is a string, converts it into a ProcessId object,
  //
  function castToProcessId(process_id) {
    // If 'process_id' is a string then convert it into an appropriate object,
    if (isString(process_id)) {
      process_id = ProcessId.fromString(process_id);
    }
    return process_id;
  }

  //
  // Closes the current process.
  //
  exports.close = function() {

    _get_processinstance().close();

  }

  //
  // Broadcasts a message on the given channel from the current process.
  //
  exports.broadcastMessage = function(channel_num, process_message) {

    _get_processinstance().broadcastMessage(channel_num, process_message);

  }

  //
  // Returns a StyledPrintWriter object that will encode styled print output on
  // a broadcast channel of the current process. If no channel number is given
  // then defaults to channel 0.
  //
  exports.getOut = function(channel_num) {
    if (typeof channel_num === 'undefined') {
      channel_num = 0;
    }
    return new BroadcastSWriter(_get_processinstance(), channel_num, 'b0');
  }

  //
  // Returns the process id of the current process.
  //
  exports.getProcessId = function() {

    return _get_processinstance().getId();

  }

  //
  // Given a process id string, returns a ProcessId object.
  //
  exports.createProcessId = function(process_id_str) {

    return ProcessId.fromString(process_id_str);

  }

  //
  // Returns the StateMap of the current process.
  //
  exports.getStateMap = function() {

    return _get_processinstance().getStateMap();

  }

  //
  // Sends a reply to the given process input message. This will only succeed if
  // the given input message is type 'INVOKE_FUNCTION'. The
  // 'process_input_message' is the function invoke message being replied to.
  //
  exports.sendReply = function(process_input_message, reply_process_message) {

    _get_processinstance().sendReply(process_input_message, reply_process_message);

  }

  //
  // Creates a new process that wraps the given JavaScript file and returns the
  // ProcessId object that is used to reference the process. Immediately calls
  // the 'init' function on the newly created process.
  //
  exports.createJSProcess = function(java_script_file_name, web_app_name) {

    var ctx = PlatformContextFactory.getPlatformContext();

    // If 'web_app_name' is not defined then set it to null,
    if (typeof web_app_name === 'undefined') {
      web_app_name = null;
    }

    // Invoke the process,
    return JSWrapBase.createJavaScriptProcess(
                                      ctx, web_app_name, java_script_file_name);

  };

  //
  // Invoke the function on the given ProcessId. When a reply to the call is
  // received, the 'on_reply(process_message)' function is called.
  // 
  // Example;
  //   var on_reply_fun = function(msg) { ... }
  //   invokeFunction(
  //       process_id, Process.encodeProcessMessage('cmd'), on_reply_fun);
  //
  exports.invokeFunction = function(process_id, out_process_msg, on_reply_fun) {

    var ctx = PlatformContextFactory.getPlatformContext();

    // Cast the process id object as appropriate,
    process_id = castToProcessId(process_id);

    // Reply is expected if 'on_reply_fun' is defined
    var reply_expected = (typeof on_reply_fun === 'function');

    // Invoke the function,
    var call_id = JSWrapBase.invokeJavaScriptFunction(
                              ctx, process_id, out_process_msg, reply_expected);

    // Associate the call id,
    if (reply_expected) {
      // INSTANCE SCOPE CALL: Make the function callback association,
      _fun_callback(call_id, on_reply_fun);
    }

  }

  //
  // Sends a signal to the given ProcessId. Sending a signal to a process will
  // not cause an automatic reply message.
  //
  // Example;
  //   sendSignal(process_id, ['kill']);
  //
  exports.sendSignal = function(process_id, signal_string_arr) {

    var ctx = PlatformContextFactory.getPlatformContext();

    // Cast the process id object as appropriate,
    process_id = castToProcessId(process_id);

    JSWrapBase.sendJavaScriptSignal(ctx, process_id, signal_string_arr);

  }

  //
  // Invoke a query over all the process servers. When a reply to the call is
  // ready, the 'on_reply(process_message)' function is called.
  // 
  // Example;
  //   var on_reply_fun = function(msg) { ... }
  //   var servers_query =
  //         Process.ServersQuery.processSummary('admin', null, null);
  //   invokeServersQuery(servers_query, on_reply_fun);
  //
  exports.invokeServersQuery = function(servers_query, on_reply_fun) {

    var ctx = PlatformContextFactory.getPlatformContext();

    // Exception if no 'on_reply_fun'
    if (typeof on_reply_fun !== 'function') {
      throw 'Expecting on_reply_fun argument';
    }

    // Invoke the function,
    var call_id = JSWrapBase.invokeJavaScriptServersQuery(ctx, servers_query);

    // Associate the call id,
    // INSTANCE SCOPE CALL: Make the function callback association,
    _fun_callback(call_id, on_reply_fun);

  }

  //
  // Returns the ServersQuery object for forming server queries.
  //
  exports.ServersQuery = Packages.com.mckoi.process.ServersQuery;

  //
  // Schedules a callback on this process after 'time_ms' milliseconds has
  // passed.
  //
  exports.setTimeout = function(scheduled_fun, time_ms, callback_msg) {

    _timed_callback(time_ms, scheduled_fun, callback_msg);

  }

  //
  // Sets a listener to the given broadcast channel of the given process.
  // 
  // Example;
  //   var listener_fun = function(msg) { ... }
  //   setBroadcastListener(process_id, 0, listener_fun);
  //
  exports.setBroadcastListener = function(
                                  process_id, channel_num, listener_fun) {

    // Cast the process id object as appropriate,
    process_id = castToProcessId(process_id);

    var process_channel = new ProcessChannel(process_id, channel_num);

    // INSTANCE SCOPE CALL: Set broadcast channel listener,
    _broadcast_setlistener(process_channel, listener_fun);

  }

  //
  // Removes a listener from the given broadcast channel of the given process.
  // If listener_fun is null, then all listeners on the given channel are
  // removed. If the given listener_fun is not listening on the broadcast channel
  // then does nothing.
  //
  exports.removeBroadcastListener = function(
                                    process_id, channel_num, listener_fun) {

    // Cast the process id object as appropriate,
    process_id = castToProcessId(process_id);

    var process_channel = new ProcessChannel(process_id, channel_num);

    // INSTANCE SCOPE CALL: Remove broadcast channel listener,
    _broadcast_removelistener(process_channel, listener_fun);

  }

  //
  // Given a set of string arguments, returns a com.mckoi.process.ProcessMessage
  // that is the encoded form of the string values.
  //
  exports.encodeProcessMessage = function() {

    return JSWrapBase.encodeProcessMessage(arguments);

  }

  //
  // Decodes a ProcessMessage into an array of strings.
  //
  exports.decodeProcessMessage = function(process_message) {

    return JSWrapBase.decodeProcessMessage(process_message);

  }
  
})();
