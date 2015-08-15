
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
    var ws1_url = ws_url + "/WSock1";
    var ws2_url = ws_url + "/WSock2";

    // First test sequence,

    var first_test_sequence = function() {

      ui.info("Web Socket 1 URL: " + ws1_url);

      var count_order = 0;
      var counter_failed = false;
      var expecting_close = false;

      var connection = new WebSocket(ws1_url);
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

          connection.send('PLATCTX FSQUERY');
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
          else if (msg.startsWith('FSQUERY: ')) {
            ui.pass("Platform Context File System Query");
            ui.info(msg);

            // Close
            expecting_close = true;
            connection.close(1000, "OK");
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

      var connection = new WebSocket(ws1_url);
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
        third_test_sequence();

      };
      connection.onmessage = function(e) {
        var msg = e.data;
      };

    };

    // Third test sequence,

    var third_test_sequence = function() {

      oncomplete();

//      ui.info("Web Socket 2 URL: " + ws2_url);
//
//      var count_order = 0;
//      var counter_failed = false;
//      var expecting_close = false;
//
//      var connection = new WebSocket(ws2_url);
//      connection.binaryType = 'arraybuffer';
//
//      connection.onopen = function() {
//        ui.pass("WebSocket(3) Open");
//        connection.send('Hello');
//      };
//      connection.onclose = function(e) {
//        if (expecting_close) {
//          ui.pass("WebSocket(3) closed");
//        }
//        else {
//          ui.fail("Unexpected close");
//        }
//        var close_code = e.code;
//        var close_reason = e.reason;
//        var close_was_clean = e.wasClean;
//        console.log("Connection closed: %s '%s' %s", close_code, close_reason, close_was_clean);
//
//        // Complete the test,
//        oncomplete();
//
//      };
//      connection.onmessage = function(e) {
//        var msg = e.data;
//        if (msg === 'Server replied: Hello') {
//          ui.pass("WebSocket(3) Echo received");
//        }
//        else {
//          ui.pass("Invalid echo string from server");
//        }
//        connection.close(1000, "OK");
//        expecting_close = true;
//      };

    };


    // Start the test sequence,
    first_test_sequence();

  });

})();