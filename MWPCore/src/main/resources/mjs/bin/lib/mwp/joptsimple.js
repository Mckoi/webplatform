
/**
 * A Javascript wrapper for joptsimple
 * <p>
 * An example of using this API;
 * 
 *   var CL = require('mwp/joptsimple');
 * 
 *   var parser = new CL.OptionParser();
 *   parser.acceptsAll( ['help', '?' ],
 *                     'Show this help');
 *   parser.acceptsAll( [ 'o', 'order' ],
 *                     'Order specification' )
 *         .withRequiredArg().describedAs('alpha or date');
 *
 *   // Parse the command line,
 *   var options;
 *   try {
 *     options = parser.parse( vars.cline );
 *   }
 *   catch (e) {
 *     out.println(e, "error");
 *     parser.printHelpOn(out);
 *     return;
 *   }
 *
 *   // If has the 'help' option,
 *   if (options.has('help')) {
 *     parser.printHelpOn(out);
 *     return;
 *   }
 *
 *   out.println(options.has('order'), "debug");
 *   out.println(options.valueOf('order'), "debug");
 * 
 */

(function() {
  
  // Java utility function,
  var TextUtils = Packages.com.mckoi.apihelper.TextUtils;
  var ArrayList = Packages.java.util.ArrayList;
  var JavaOptionParser = Packages.com.mckoi.lib.joptsimple.OptionParser;
  var JOptSimpleUtils = Packages.com.mckoi.apihelper.JOptSimpleUtils;



  var isDefined = function(v) {
    return (typeof v !== 'undefined');
  }
  
  var isString = function(v) {
    return (typeof v === 'string');
  }

  /**
   * Converts a JavaScript array to a Java Collection of strings.
   */
  var stringArrayToCollection = function(arr) {
    var java_list = new ArrayList();
    for (var i = 0, sz = arr.length; i < sz; ++i) {
      java_list.add(String(arr[i]));
    }
    return java_list;
  }

  /**
   * Returns a JavaScript array given a Java collection.
   */
  var collectionToArray = function(collection, CFun) {
    var i = collection.iterator();
    var out = [];
    if (isDefined(CFun)) {
      while (i.hasNext()) {
        out.push(CFun(i.next()));
      }
    }
    else {
      while (i.hasNext()) {
        out.push(i.next());
      }
    }
    return out;
  }

  /**
   * CFun that converts values in an array to JavaScript strings.
   */
  var js_cfun = function(v) {
    if (v !== null) {
      if (typeof v === 'object') {
        var clazz = v.getClass();
        if (clazz === 'java.lang.String') {
          return String(v);
        }
      }
    }
    return v;
  }

  /**
   * Wrapper for joptsimple OptionSpec.
   */
  var JSimpleOptionSpec = function(_java) {

    this.javaObject = _java;

    this.isForHelp = function() {
      return _java.isForHelp();
    }

    this.options = function() {
      return collectionToArray(_java.options(), js_cfun);
    }

    this.value = function(option_set) {
      return js_cfun(_java.value(option_set.javaObject));
    }

    this.values = function(option_set) {
      return collectionToArray(_java.values(option_set.javaObject), js_cfun);
    }

  }

  /**
   * Wrapper for joptsimple OptionSet.
   */
  var JSimpleOptionSet = function(_java) {

    this.javaObject = _java;

    this.has = function(option) {
      if (isString(option)) return _java.has(option);
      return _java.has(option.javaObject);
    }

    this.hasArgument = function(option) {
      if (isString(option)) return _java.hasArgument(option);
      return _java.hasArgument(option.javaObject);
    }

    this.hasOptions = function() {
      return _java.hasOptions();
    }

    this.nonOptionArguments = function() {
      return collectionToArray(_java.nonOptionArguments(),
                function(v) { return String(v); });
    }

    this.specs = function() {
      return collectionToArray(_java.specs(),
                function(v) { return new JSimpleOptionSpec(v); });
    }

    this.valueOf = function(option) {
      if (isString(option))
        return js_cfun(_java.valueOf(option));
      return js_cfun(_java.valueOf(option.javaObject));
    }

    this.valuesOf = function(option) {
      if (isString(option))
        return collectionToArray(_java.valuesOf(option), js_cfun);
      return collectionToArray(_java.valuesOf(option.javaObject), js_cfun);
    }

  }

  /**
   * Wrapper for joptsimple OperationParser.
   */
  var JSimpleOptionParser = function(option_spec) {

    // The Java object,
    var _java;
    if (isDefined(option_spec)) {
      _java = new JavaOptionParser(option_spec);
    }
    else {
      _java = new JavaOptionParser();
    }

    this.javaObject = _java;

    this.accepts = function(option, description) {
      if (isDefined(description)) {
        return _java.accepts(option, description);
      }
      else {
        return _java.accepts(option);
      }
    }

    this.acceptsAll = function(option_arr, description) {
      if (isDefined(description)) {
        return _java.acceptsAll(stringArrayToCollection(option_arr), description);
      }
      else {
        return _java.acceptsAll(stringArrayToCollection(option_arr));
      }
    }

    this.parse = function(args) {
      if (typeof args === 'string') {
        args = TextUtils.splitCommandLineAndUnquote(args);
      }
      try {
        return new JSimpleOptionSet(_java.parse(args));
        return _java.parse(args);
      }
      catch (e) {
        // Wrap the exception,
        if (isDefined(e.javaException)) {
          throw e.javaException.message;
        }
        throw e;
      }
    }

    this.printHelpOn = function(out) {
      if (isDefined(out.javaObject)) {
        out = out.javaObject;
      }
      JOptSimpleUtils.printHelpOn(_java, out);
    }

  }

  //
  // Return the wrapped operation parser.
  //
  exports.OptionParser = JSimpleOptionParser;
 
})();
