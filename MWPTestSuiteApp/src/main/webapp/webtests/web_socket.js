
// A client-side test of WebSockets.


(function() {

  defineTest(function(ui, oncomplete) {

    var url = window.location.href;
    var file_part = url.substring(0, url.lastIndexOf("/"));
    var ws_url = "";
    // The web socket link is either encrypted or not depending on test page,
    if (file_part.startsWith("http://")) {
      ws_url = "ws://" + file_part.substring(7);
    }
    else if (file_part.startsWith("https://")) {
      ws_url = "wss://" + file_part.substring(8);
    }
    ws_url = ws_url + "/WSock";

    ui.info("Web Socket URL: " + ws_url);
    
    // First test sequence,
    
    var first_test_sequence = function() {
    
      var count_order = 0;
      var counter_failed = false;
      var expecting_close = false;

      var connection = new WebSocket(ws_url);
      connection.binaryType = 'arraybuffer';
      connection.onopen = function() {
        ui.pass("WebSocket(1) Open");
        connection.send('HANDSHAKE');
      };
      connection.onclose = function(e) {
        if (expecting_close) {
          ui.pass("WebSocket(1) client-side closed");
        }
        else {
          ui.fail("Unexpected close");
        }
        var close_code = e.code;
        var close_reason = e.reason;
        var close_was_clean = e.wasClean;
        console.log("Connection closed: %s '%s' %s", close_code, close_reason, close_was_clean);

        // Complete the test,
        second_test_sequence();

      };
      connection.onmessage = function(e) {
        var msg = e.data;

        if (msg instanceof ArrayBuffer) {
          var arr = new Int8Array(msg);
          ui.pass("Binary Object Received");
          // Close
          expecting_close = true;
          connection.close(1000, "OK");
        }
        else {
          if (msg === 'HANDSHAKE RET') {
            ui.pass("Handshake Received");
            connection.send('START COUNTER');
          }
          else if (msg.startsWith('count=')) {
            var counter_val = parseInt(msg.substring(6));
            if (count_order !== counter_val) {
              ui.fail("Counter order fail " + count_order + " !== " + counter_val);
              counter_failed = true;
            }
            ++count_order;
          }
          else if (msg === 'COUNT RET') {
            if (!counter_failed) {
              ui.pass("Count Complete");
            }
            else {
              ui.fail("Count Complete (but failed)");
            }
            connection.send('GET BINARY');
          }
          else {
            ui.fail("Unexpected server message: " + msg);
          }
        }
      };

    };

    // Second test sequence,

    var second_test_sequence = function() {
    
      var count_order = 0;
      var counter_failed = false;
      var expecting_close = false;

      var connection = new WebSocket(ws_url);
      connection.binaryType = 'arraybuffer';

      connection.onopen = function() {
        ui.pass("WebSocket(2) Open");
        expecting_close = true;
        connection.send('REMOTE CLOSE');
      };
      connection.onclose = function(e) {
        if (expecting_close) {
          ui.pass("WebSocket(2) Server-side closed");
        }
        else {
          ui.fail("Unexpected close");
        }
        var close_code = e.code;
        var close_reason = e.reason;
        var close_was_clean = e.wasClean;
        console.log("Connection closed: %s '%s' %s", close_code, close_reason, close_was_clean);

        // Complete the test,
        oncomplete();

      };
      connection.onmessage = function(e) {
        var msg = e.data;
      };

    };

    // Start the test sequence,
    first_test_sequence();

  });
  
})();