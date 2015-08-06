
// Initializes the 'global' object.

//testob = {};
//
//Packages.java.lang.System.out.println("Called 'initglobal.js'");
////Packages.java.lang.System.out.println("test1 = " + _fun_callback);
//Packages.java.lang.System.out.println("test1 = " +
//        Packages.com.mckoi.mwpui.apihelper.TextUtils.javaObjectDump(testob));
//Packages.java.lang.System.out.println("test2 = " +
//        Packages.com.mckoi.mwpui.apihelper.TextUtils.javaObjectDump(global));
//Packages.java.lang.System.out.println("p test1 = " + testob);
//Packages.java.lang.System.out.println("p test2 = " + global);

// NOTE: The order of these assignments is important. The 'console' module
//   is dependant most other global variables already having been assigned.

global.GLOBAL = global;
global.root = global;

// This process specific functions
global.process = require('./thisprocess');


//// Define the console
//global.console = require('console');

global.__defineGetter__('console', function() {
  return require('console');
});

