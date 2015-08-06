
var stdout = {
  write: function(text) {
    Packages.java.lang.System.out.print(text);
  }
};

var stderr = {
  write: function(text) {
    Packages.java.lang.System.err.print(text);
  }
};




exports.stdout = stdout;
exports.stderr = stderr;

