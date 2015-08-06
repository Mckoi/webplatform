
// Support for standard streams (stdout, stderr, etc) through the Mckoi Web
// Platform.

var stream = require('stream');
var util = require('util');

// The MWP stream support,

var mwpconsole = process.binding('mwpconsole');

var setWriteFunction = function(stream, type) {
  
  function chunkAsString(chunk) {
    if (util.isBuffer(chunk)) {
      return chunk.toString(stream.defaultEncoding);
    }
    else {
      return chunk.toString();
    }
  };
  
  stream._writev = function(chunks, done) {
    // Make a big string from all the chunks,
    var towrite = '';
    chunks.forEach(function(chunk_ob) {
      towrite += chunkAsString(chunk_ob.chunk);
    });
//    Packages.java.lang.System.out.println("WRITING: " + util.inspect(towrite));
    mwpconsole.write(type, towrite);
    done();
  };
  
  stream._write = function(chunk, encoding, done) {
    var towrite = chunkAsString(chunk);
//    Packages.java.lang.System.out.println("WRITING: " + util.inspect(towrite));
    mwpconsole.write(type, towrite);
    done();
  };
};

var streamopts = { decodeStrings:false, objectMode:false };
var mwpout = new stream.Writable(streamopts);
var mwperr = new stream.Writable(streamopts);
setWriteFunction(mwpout, 'out');
setWriteFunction(mwperr, 'err');






exports.stdout = mwpout;
exports.stderr = mwperr;

