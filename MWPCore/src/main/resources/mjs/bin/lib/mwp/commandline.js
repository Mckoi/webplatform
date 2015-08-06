//
// A command line processing object
//

(function() {

  // Java utility function,
  var TextUtils = Packages.com.mckoi.apihelper.TextUtils;

  function ICOB(values_spec, spec_arg) {

    if (!spec_arg) spec_arg = [];

    // The program name,
    var prg = '';


    function printSpaces(out, sz) {
      out.print(require('./textformat').pad(sz));
    }

    // True if str1 starts with str2
    function strStartsWith(str1, str2) {
      return (str1.slice(0, str2.length) == str2);
    }



    function processSwitch(result_ob, option, item_args) {
      var longsw = option[0];
      var shortsw = option[1];
      var code = option[2];
      var ret_value, process_rest;
      if (code.slice(0, 1) == ':') {
        // Value switch,
        var value_expr = code.slice(1);
        // PENDING, validate arguments?

        // Value switches,
        if (item_args.slice(0, 1) == ':') {
          ret_value = item_args.slice(1);
        }
        else {
          ret_value = item_args;
        }
        process_rest = true;
      }
      else {
        // Boolean switches,
        ret_value = true;
        process_rest = false;
      }

      if (longsw != '') {
        if (result_ob.options[longsw]) throw 'option already set: --' + longsw;
        result_ob.options[longsw] = ret_value;
      }
      if (shortsw != '') {
        if (result_ob.options[shortsw]) throw 'option already set: -' + shortsw;
        result_ob.options[shortsw] = ret_value;
      }

      return process_rest;

    }

    //
    // Tries to match the expression. If it fails, throws an
    // exception. If it succeeds, returns an object the
    // describes the options selected.
    //
    this.match = function(expression) {

      // Split the expression into parts,
      var args = TextUtils.splitCommandLine(expression);
      prg = exports.unescapeQuotes(args[0]);

      var results_ob = {};
      results_ob.options = {};
      results_ob.values = [];
      results_ob.getCommandName = function() {
        return prg;
      }

      for (var i = 1, sz = args.length; i < sz; ++i) {
        // NOTE: This ensures 'item' is cast as a JavaScript string
        var item = '' + exports.unescapeQuotes(args[i]);

        var matched_switch = false;
        var item_switch, option, n;

        if (item.slice(0, 2) == '--') {
          item_switch = item.slice(2);
          // Process the switch,
          for (n = 0; n < spec_arg.length; ++n) {
            option = spec_arg[n];
            var longswitch_str = option[0];

            var switch_code;
            var delim = item_switch.indexOf(':');
            if (delim == -1) switch_code = item_switch;
            else switch_code = item_switch.substring(0, delim);

            if (longswitch_str != '' &&
                strStartsWith(longswitch_str, switch_code)) {
              if (matched_switch == true) {
                throw 'ambiguous switch: ' + item_switch;
              }
              processSwitch(results_ob, option,
                            item_switch.slice(switch_code.length));
              matched_switch = true;
            }
          }

          if (!matched_switch) {
            throw 'no match for switch: --' + item_switch;
          }

        }
        else if (item.slice(0, 1) == '-') {
          item_switch = '';
          // Split the characters up,
          for (var ci = 1; ci < item.length; ++ci) {
            item_switch = item.substring(ci);
            matched_switch = false;
            // Process the switch,
            for (n = 0; n < spec_arg.length; ++n) {
              option = spec_arg[n];
              var shortswitch_str = option[1];
              // item_switch contains this option,
              if ( shortswitch_str != '' &&
                  item_switch.charAt(0) == shortswitch_str ) {
                if (matched_switch) {
                  throw 'ambiguous switch: ' + item_switch;
                }
                // Found the option,
                var consumed_rest =
                    processSwitch(results_ob, option, item_switch.substring(1));
                if (consumed_rest) {
                  ci = item.length;
                }
                matched_switch = true;
              }
            }
            if (!matched_switch) {
              throw 'no match for switch: -' + item_switch;
            }
          }

        }
        // Not a switch so push this as a value,
        else {
          results_ob.values.push(args[i]);
        }

      }

      return results_ob;

    };

    //
    // Returns the name of the program (must be called after
    // 'match')
    //
    this.getCommandName = function() {
      return prg;
    }

    //
    // Prints documentation on the specification.
    //
    this.printHelp = function(out) {
      var i = 0, sz = spec_arg.length;

      var help_line = '  ';
      help_line += (prg + ' ');
      if (sz > 1) help_line += '<options> ';
      if (values_spec) help_line += values_spec;

      out.println(help_line, 'info');
      out.println();

      var line_st_arr = [];
      var line_st, option;
      while (i < sz) {
        option = spec_arg[i];
        var longswitch_str = option[0];
        var shortswitch_str = option[1];
        var code = option[2];

        line_st = '';
        if (longswitch_str != '') {
          line_st += ' --' + longswitch_str;
        }
        else {
          line_st += '  ';
        }
        if (shortswitch_str != '') {
          line_st += ' -' + shortswitch_str;
        }

        if (code.slice(0, 1) == ':') line_st += code;

        line_st_arr.push(line_st);
        ++i;
      }

      i = 0;
      while (i < sz) {
        option = spec_arg[i];
        line_st = line_st_arr[i];
        var doc = option[3];

        out.print(line_st);
        var margin_s = 30 - line_st.length;
        if (margin_s < 1) margin_s = 1;
        var lines_arr = TextUtils.splitIntoLines(doc, 55, 0);
        for (var line in lines_arr) {
          printSpaces(out, margin_s);
          out.println(lines_arr[line], 'info');
          margin_s = 30;
        }

        ++i;
      }

    };

  }

  //
  // Unescapes a quoted string.
  //
  exports.unescapeQuotes = function(arg) {
    if (arg.slice(0, 1) == '"') arg = arg.substring(1);
    if (arg.slice(-1) == '"') arg = arg.slice(0, -1);
    return arg;
  }

  //
  // Parses a command line specification.
  //
  exports.parse = function(values_spec, cl_spec_arg) {
    return new ICOB(values_spec, cl_spec_arg);
  }

})();
