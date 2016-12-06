
var mopt = require('mopt');
var textutils = require('textutils');

(function() {


  function doList(o, spec) {
    console.log(spec);
  }

  function main() {
    var opt = new mopt()
        .optSwitch( ['a', 'all'],   "Show all objects (including hidden)", false )
        .optSwitch( ['l', 'list'],  "Detailed file list", true )
        .optSwitch( ['d', 'dirs'],  "Show only directories", false )
        .optSwitch( ['f', 'files'], "Show only files", false )
        .optSwitch( ['s', 'short'], "Display only file/directory names", false )
        .optSwitch( ['m', 'mime'],  "Show file mime type", false )
        .optArg(    ['o', 'order'], "Sort order. Either 'alpha' or 'date'", 'alpha' )
        .optSwitch( ['h', '?', 'help'],  "Show help", false )
    ;

    var o = opt.parse();
  //  console.dir(o.argp);
  //  console.dir(o.descrip);

    var r = o.argp;
    // Print help,
    if (r.h) {
      console.log(textutils.stylizeWithColor('  nls <options> <files or dirs or wildcard>', 'green'));
      console.log();
      opt.printHelp();
      return;
    }

    // The target array,
    var target = o.argp._;
    var tlen = target.length;
    if (tlen === 0) {
      doList(o, "*");
    }
    else {
      // For each target,
      for (var i = 0; i < tlen; ++i) {
        var t = target[i];
        doList(o, t);
      }
    }
  }

  main();

})();
