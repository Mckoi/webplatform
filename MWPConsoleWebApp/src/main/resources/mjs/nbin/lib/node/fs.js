// Copyright Joyent, Inc. and other Node contributors.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.

// Maintainers, keep in mind that octal literals are not allowed
// in strict mode. Use the decimal value and add a comment with
// the octal value. Example:
//
//   var mode = 438; /* mode=0666 */

var util = require('util');
var pathModule = require('path');

var mfs_binding = process.binding('mfilesystems');

var constants = process.binding('constants');
var Buffer = require('buffer').Buffer;
var Stream = require('stream').Stream;
var EventEmitter = require('events').EventEmitter;
var FSReqWrap = mfs_binding.FSReqWrap;

var Readable = Stream.Readable;
var Writable = Stream.Writable;

var kMinPoolSpace = 128;
var kMaxLength = require('smalloc').kMaxLength;

var O_APPEND = constants.O_APPEND || 0;
var O_CREAT = constants.O_CREAT || 0;
var O_EXCL = constants.O_EXCL || 0;
var O_RDONLY = constants.O_RDONLY || 0;
var O_RDWR = constants.O_RDWR || 0;
var O_SYNC = constants.O_SYNC || 0;
var O_TRUNC = constants.O_TRUNC || 0;
var O_WRONLY = constants.O_WRONLY || 0;

var isWindows = process.platform === 'win32';

var DEBUG = process.env.NODE_DEBUG && /fs/.test(process.env.NODE_DEBUG);
var errnoException = util._errnoException;


function rethrow() {
  // Only enable in debug mode. A backtrace uses ~1000 bytes of heap space and
  // is fairly slow to generate.
  if (DEBUG) {
    var backtrace = new Error;
    return function(err) {
      if (err) {
        backtrace.stack = err.name + ': ' + err.message +
                          backtrace.stack.substr(backtrace.name.length);
        err = backtrace;
        throw err;
      }
    };
  }

  return function(err) {
    if (err) {
      throw err;  // Forgot a callback but don't know where? Use NODE_DEBUG=fs
    }
  };
}

function maybeCallback(cb) {
  return util.isFunction(cb) ? cb : rethrow();
}

// Ensure that callbacks run in the global context. Only use this function
// for callbacks that are passed to the binding layer, callbacks that are
// invoked from JS already run in the proper scope.
function makeCallback(cb) {
  if (!util.isFunction(cb)) {
    return rethrow();
  }

  return function() {
    return cb.apply(null, arguments);
  };
}

function assertEncoding(encoding) {
  if (encoding && !Buffer.isEncoding(encoding)) {
    throw new Error('Unknown encoding: ' + encoding);
  }
}

function nullCheck(path, callback) {
//  if (('' + path).indexOf('\u0000') !== -1) {
//    var er = new Error('Path must be a string without null bytes.');
//    if (!callback)
//      throw er;
//    process.nextTick(function() {
//      callback(er);
//    });
//    return false;
//  }
  return true;
}

// The file system object we prototype with the functions.
var mfs = function(binding) {
  this._binding = binding;
};

// The 'repository' function creates a new file system object for the given
// named repository.
function repository(repository_id) {
  return new mfs(mfs_binding.open(repository_id));
}
exports.repository = repository;

// Static method to set the stats properties on a Stats object.
var Stats = function(
    dev,
    mode,
    nlink,
    uid,
    gid,
    rdev,
    blksize,
    ino,
    size,
    blocks,
    atim_msec,
    mtim_msec,
    ctim_msec,
    birthtim_msec) {
  this.dev = dev;
  this.mode = mode;
  this.nlink = nlink;
  this.uid = uid;
  this.gid = gid;
  this.rdev = rdev;
  this.blksize = blksize;
  this.ino = ino;
  this.size = size;
  this.blocks = blocks;
  this.atime = new Date(atim_msec);
  this.mtime = new Date(mtim_msec);
  this.ctime = new Date(ctim_msec);
  this.birthtime = new Date(birthtim_msec);
};
mfs.prototype.Stats = Stats;

// Create a C++ binding to the function which creates a Stats object.
mfs_binding.FSInitialize(Stats);

Stats.prototype._checkModeProperty = function(property) {
  return ((this.mode & constants.S_IFMT) === property);
};

Stats.prototype.isDirectory = function() {
  return this._checkModeProperty(constants.S_IFDIR);
};

Stats.prototype.isFile = function() {
  return this._checkModeProperty(constants.S_IFREG);
};

Stats.prototype.isBlockDevice = function() {
  return this._checkModeProperty(constants.S_IFBLK);
};

Stats.prototype.isCharacterDevice = function() {
  return this._checkModeProperty(constants.S_IFCHR);
};

Stats.prototype.isSymbolicLink = function() {
  return this._checkModeProperty(constants.S_IFLNK);
};

Stats.prototype.isFIFO = function() {
  return this._checkModeProperty(constants.S_IFIFO);
};

Stats.prototype.isSocket = function() {
  return this._checkModeProperty(constants.S_IFSOCK);
};

// don't allow fs.mode to accidentally be overwritten.
['F_OK', 'R_OK', 'W_OK', 'X_OK'].forEach(function(key) {
  Object.defineProperty(mfs.prototype, key, {
    enumerable: true, value: constants[key] || 0, writable: false
  });
});


mfs.prototype.access = function(path, mode, callback) {
  if (!nullCheck(path, callback))
    return;

  if (typeof mode === 'function') {
    callback = mode;
    mode = mfs.prototype.F_OK;
  } else if (typeof callback !== 'function') {
    throw new TypeError('callback must be a function');
  }

  mode = mode | 0;
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.access(pathModule._makeLong(path), mode, req);
};

mfs.prototype.accessSync = function(path, mode) {
  nullCheck(path);

  if (mode === undefined)
    mode = mfs.prototype.F_OK;
  else
    mode = mode | 0;

  this._binding.access(pathModule._makeLong(path), mode);
};

mfs.prototype.exists = function(path, callback) {
  if (!nullCheck(path, cb)) return;
  var req = new FSReqWrap();
  req.oncomplete = cb;
  this._binding.stat(pathModule._makeLong(path), req);
  function cb(err, stats) {
    if (callback) callback(err ? false : true);
  }
};

mfs.prototype.existsSync = function(path) {
  try {
    nullCheck(path);
    this._binding.stat(pathModule._makeLong(path));
    return true;
  } catch (e) {
    return false;
  }
};

mfs.prototype.readFile = function(path, options, callback_) {
  var callback = maybeCallback(arguments[arguments.length - 1]);

  if (util.isFunction(options) || !options) {
    options = { encoding: null, flag: 'r' };
  } else if (util.isString(options)) {
    options = { encoding: options, flag: 'r' };
  } else if (!util.isObject(options)) {
    throw new TypeError('Bad arguments');
  }

  var encoding = options.encoding;
  assertEncoding(encoding);

  // first, stat the file, so we know the size.
  var size;
  var buffer; // single buffer with file data
  var buffers; // list for when size is unknown
  var pos = 0;
  var fd;

  var flag = options.flag || 'r';
  this.open(path, flag, 438 /*=0666*/, function(er, fd_) {
    if (er) return callback(er);
    fd = fd_;

    this.fstat(fd, function(er, st) {
      if (er) {
        return this.close(fd, function() {
          callback(er);
        });
      }

      size = st.size;
      if (size === 0) {
        // the kernel lies about many files.
        // Go ahead and try to read some bytes.
        buffers = [];
        return read();
      }

      if (size > kMaxLength) {
        var err = new RangeError('File size is greater than possible Buffer: ' +
            '0x3FFFFFFF bytes');
        return this.close(fd, function() {
          callback(err);
        });
      }
      buffer = new Buffer(size);
      read();
    });
  });

  function read() {
    if (size === 0) {
      buffer = new Buffer(8192);
      this.read(fd, buffer, 0, 8192, -1, afterRead);
    } else {
      this.read(fd, buffer, pos, size - pos, -1, afterRead);
    }
  }

  function afterRead(er, bytesRead) {
    if (er) {
      return this.close(fd, function(er2) {
        return callback(er);
      });
    }

    if (bytesRead === 0) {
      return close();
    }

    pos += bytesRead;
    if (size !== 0) {
      if (pos === size) close();
      else read();
    } else {
      // unknown size, just read until we don't get bytes.
      buffers.push(buffer.slice(0, bytesRead));
      read();
    }
  }

  function close() {
    this.close(fd, function(er) {
      if (size === 0) {
        // collected the data into the buffers list.
        buffer = Buffer.concat(buffers, pos);
      } else if (pos < size) {
        buffer = buffer.slice(0, pos);
      }

      if (encoding) buffer = buffer.toString(encoding);
      return callback(er, buffer);
    });
  }
};

mfs.prototype.readFileSync = function(path, options) {
  if (!options) {
    options = { encoding: null, flag: 'r' };
  } else if (util.isString(options)) {
    options = { encoding: options, flag: 'r' };
  } else if (!util.isObject(options)) {
    throw new TypeError('Bad arguments');
  }

  var encoding = options.encoding;
  assertEncoding(encoding);

  var flag = options.flag || 'r';
  var fd = this.openSync(path, flag, 438 /*=0666*/);

  var size;
  var threw = true;
  try {
    size = this.fstatSync(fd).size;
    threw = false;
  } finally {
    if (threw) this.closeSync(fd);
  }

  var pos = 0;
  var buffer; // single buffer with file data
  var buffers; // list for when size is unknown

  if (size === 0) {
    buffers = [];
  } else {
    var threw = true;
    try {
      buffer = new Buffer(size);
      threw = false;
    } finally {
      if (threw) this.closeSync(fd);
    }
  }

  var done = false;
  while (!done) {
    var threw = true;
    try {
      if (size !== 0) {
        var bytesRead = this.readSync(fd, buffer, pos, size - pos);
      } else {
        // the kernel lies about many files.
        // Go ahead and try to read some bytes.
        buffer = new Buffer(8192);
        var bytesRead = this.readSync(fd, buffer, 0, 8192);
        if (bytesRead) {
          buffers.push(buffer.slice(0, bytesRead));
        }
      }
      threw = false;
    } finally {
      if (threw) this.closeSync(fd);
    }

    pos += bytesRead;
    done = (bytesRead === 0) || (size !== 0 && pos >= size);
  }

  this.closeSync(fd);

  if (size === 0) {
    // data was collected into the buffers list.
    buffer = Buffer.concat(buffers, pos);
  } else if (pos < size) {
    buffer = buffer.slice(0, pos);
  }

  if (encoding) buffer = buffer.toString(encoding);
  return buffer;
};


// Used by binding.open and friends
function stringToFlags(flag) {
  // Only mess with strings
  if (!util.isString(flag)) {
    return flag;
  }

  switch (flag) {
    case 'r' : return O_RDONLY;
    case 'rs' : // fall through
    case 'sr' : return O_RDONLY | O_SYNC;
    case 'r+' : return O_RDWR;
    case 'rs+' : // fall through
    case 'sr+' : return O_RDWR | O_SYNC;

    case 'w' : return O_TRUNC | O_CREAT | O_WRONLY;
    case 'wx' : // fall through
    case 'xw' : return O_TRUNC | O_CREAT | O_WRONLY | O_EXCL;

    case 'w+' : return O_TRUNC | O_CREAT | O_RDWR;
    case 'wx+': // fall through
    case 'xw+': return O_TRUNC | O_CREAT | O_RDWR | O_EXCL;

    case 'a' : return O_APPEND | O_CREAT | O_WRONLY;
    case 'ax' : // fall through
    case 'xa' : return O_APPEND | O_CREAT | O_WRONLY | O_EXCL;

    case 'a+' : return O_APPEND | O_CREAT | O_RDWR;
    case 'ax+': // fall through
    case 'xa+': return O_APPEND | O_CREAT | O_RDWR | O_EXCL;
  }

  throw new Error('Unknown file open flag: ' + flag);
}

// exported but hidden, only used by test/simple/test-fs-open-flags.js
Object.defineProperty(exports, '_stringToFlags', {
  enumerable: false,
  value: stringToFlags
});


// Yes, the follow could be easily DRYed up but I provide the explicit
// list to make the arguments clear.

mfs.prototype.close = function(fd, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.close(fd, req);
};

mfs.prototype.closeSync = function(fd) {
  return this._binding.close(fd);
};

function modeNum(m, def) {
  if (util.isNumber(m))
    return m;
  if (util.isString(m))
    return parseInt(m, 8);
  if (def)
    return modeNum(def);
  return undefined;
}

mfs.prototype.open = function(path, flags, mode, callback) {
  callback = makeCallback(arguments[arguments.length - 1]);
  mode = modeNum(mode, 438 /*=0666*/);

  if (!nullCheck(path, callback)) return;

  var req = new FSReqWrap();
  req.oncomplete = callback;

  this._binding.open(pathModule._makeLong(path),
               stringToFlags(flags),
               mode,
               req);
};

mfs.prototype.openSync = function(path, flags, mode) {
  mode = modeNum(mode, 438 /*=0666*/);
  nullCheck(path);
  return this._binding.open(pathModule._makeLong(path), stringToFlags(flags), mode);
};

mfs.prototype.read = function(fd, buffer, offset, length, position, callback) {
  if (!util.isBuffer(buffer)) {
    // legacy string interface (fd, length, position, encoding, callback)
    var cb = arguments[4],
        encoding = arguments[3];

    assertEncoding(encoding);

    position = arguments[2];
    length = arguments[1];
    buffer = new Buffer(length);
    offset = 0;

    callback = function(err, bytesRead) {
      if (!cb) return;

      var str = (bytesRead > 0) ? buffer.toString(encoding, 0, bytesRead) : '';

      (cb)(err, str, bytesRead);
    };
  }

  function wrapper(err, bytesRead) {
    // Retain a reference to buffer so that it can't be GC'ed too soon.
    callback && callback(err, bytesRead || 0, buffer);
  }

  var req = new FSReqWrap();
  req.oncomplete = wrapper;

  this._binding.read(fd, buffer, offset, length, position, req);
};

mfs.prototype.readSync = function(fd, buffer, offset, length, position) {
  var legacy = false;
  if (!util.isBuffer(buffer)) {
    // legacy string interface (fd, length, position, encoding, callback)
    legacy = true;
    var encoding = arguments[3];

    assertEncoding(encoding);

    position = arguments[2];
    length = arguments[1];
    buffer = new Buffer(length);

    offset = 0;
  }

  var r = this._binding.read(fd, buffer, offset, length, position);
  if (!legacy) {
    return r;
  }

  var str = (r > 0) ? buffer.toString(encoding, 0, r) : '';
  return [str, r];
};

// usage:
//  fs.write(fd, buffer, offset, length[, position], callback);
// OR
//  fs.write(fd, string[, position[, encoding]], callback);
mfs.prototype.write = function(fd, buffer, offset, length, position, callback) {
  function strWrapper(err, written) {
    // Retain a reference to buffer so that it can't be GC'ed too soon.
    callback(err, written || 0, buffer);
  }

  function bufWrapper(err, written) {
    // retain reference to string in case it's external
    callback(err, written || 0, buffer);
  }

  if (util.isBuffer(buffer)) {
    // if no position is passed then assume null
    if (util.isFunction(position)) {
      callback = position;
      position = null;
    }
    callback = maybeCallback(callback);
    var req = new FSReqWrap();
    req.oncomplete = strWrapper;
    return this._binding.writeBuffer(fd, buffer, offset, length, position, req);
  }

  if (util.isString(buffer))
    buffer += '';
  if (!util.isFunction(position)) {
    if (util.isFunction(offset)) {
      position = offset;
      offset = null;
    } else {
      position = length;
    }
    length = 'utf8';
  }
  callback = maybeCallback(position);
  var req = new FSReqWrap();
  req.oncomplete = bufWrapper;
  return this._binding.writeString(fd, buffer, offset, length, req);
};

// usage:
//  fs.writeSync(fd, buffer, offset, length[, position]);
// OR
//  fs.writeSync(fd, string[, position[, encoding]]);
mfs.prototype.writeSync = function(fd, buffer, offset, length, position) {
  if (util.isBuffer(buffer)) {
    if (util.isUndefined(position))
      position = null;
    return this._binding.writeBuffer(fd, buffer, offset, length, position);
  }
  if (!util.isString(buffer))
    buffer += '';
  if (util.isUndefined(offset))
    offset = null;
  return this._binding.writeString(fd, buffer, offset, length, position);
};

mfs.prototype.rename = function(oldPath, newPath, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(oldPath, callback)) return;
  if (!nullCheck(newPath, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.rename(pathModule._makeLong(oldPath),
                       pathModule._makeLong(newPath),
                       req);
};

mfs.prototype.renameSync = function(oldPath, newPath) {
  nullCheck(oldPath);
  nullCheck(newPath);
  return this._binding.rename(pathModule._makeLong(oldPath),
                              pathModule._makeLong(newPath));
};

mfs.prototype.truncate = function(path, len, callback) {
  if (util.isNumber(path)) {
    return this.ftruncate(path, len, callback);
  }
  if (util.isFunction(len)) {
    callback = len;
    len = 0;
  } else if (util.isUndefined(len)) {
    len = 0;
  }

  callback = maybeCallback(callback);
  this.open(path, 'r+', function(er, fd) {
    if (er) return callback(er);
    var req = new FSReqWrap();
    req.oncomplete = function ftruncateCb(er) {
      this.close(fd, function(er2) {
        callback(er || er2);
      });
    };
    this._binding.ftruncate(fd, len, req);
  });
};

mfs.prototype.truncateSync = function(path, len) {
  if (util.isNumber(path)) {
    // legacy
    return this.ftruncateSync(path, len);
  }
  if (util.isUndefined(len)) {
    len = 0;
  }
  // allow error to be thrown, but still close fd.
  var fd = this.openSync(path, 'r+');
  try {
    var ret = this.ftruncateSync(fd, len);
  } finally {
    this.closeSync(fd);
  }
  return ret;
};

mfs.prototype.ftruncate = function(fd, len, callback) {
  if (util.isFunction(len)) {
    callback = len;
    len = 0;
  } else if (util.isUndefined(len)) {
    len = 0;
  }
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.ftruncate(fd, len, req);
};

mfs.prototype.ftruncateSync = function(fd, len) {
  if (util.isUndefined(len)) {
    len = 0;
  }
  return this._binding.ftruncate(fd, len);
};

mfs.prototype.rmdir = function(path, callback) {
  callback = maybeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.rmdir(pathModule._makeLong(path), req);
};

mfs.prototype.rmdirSync = function(path) {
  nullCheck(path);
  return this._binding.rmdir(pathModule._makeLong(path));
};

mfs.prototype.fdatasync = function(fd, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.fdatasync(fd, req);
};

mfs.prototype.fdatasyncSync = function(fd) {
  return this._binding.fdatasync(fd);
};

mfs.prototype.fsync = function(fd, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.fsync(fd, req);
};

mfs.prototype.fsyncSync = function(fd) {
  return this._binding.fsync(fd);
};

mfs.prototype.mkdir = function(path, mode, callback) {
  if (util.isFunction(mode)) callback = mode;
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.mkdir(pathModule._makeLong(path),
                      modeNum(mode, 511 /*=0777*/),
                      req);
};

mfs.prototype.mkdirSync = function(path, mode) {
  nullCheck(path);
  return this._binding.mkdir(pathModule._makeLong(path),
                             modeNum(mode, 511 /*=0777*/));
};

mfs.prototype.readdir = function(path, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.readdir(pathModule._makeLong(path), req);
};

mfs.prototype.readdirSync = function(path) {
  nullCheck(path);
  return this._binding.readdir(pathModule._makeLong(path));
};

mfs.prototype.fstat = function(fd, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.fstat(fd, req);
};

mfs.prototype.lstat = function(path, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.lstat(pathModule._makeLong(path), req);
};

mfs.prototype.stat = function(path, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.stat(pathModule._makeLong(path), req);
};

mfs.prototype.fstatSync = function(fd) {
  return this._binding.fstat(fd);
};

mfs.prototype.lstatSync = function(path) {
  nullCheck(path);
  return this._binding.lstat(pathModule._makeLong(path));
};

mfs.prototype.statSync = function(path) {
  nullCheck(path);
  return this._binding.stat(pathModule._makeLong(path));
};

mfs.prototype.readlink = function(path, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.readlink(pathModule._makeLong(path), req);
};

mfs.prototype.readlinkSync = function(path) {
  nullCheck(path);
  return this._binding.readlink(pathModule._makeLong(path));
};

function preprocessSymlinkDestination(path, type) {
  if (!isWindows) {
    // No preprocessing is needed on Unix.
    return path;
  } else if (type === 'junction') {
    // Junctions paths need to be absolute and \\?\-prefixed.
    return pathModule._makeLong(path);
  } else {
    // Windows symlinks don't tolerate forward slashes.
    return ('' + path).replace(/\//g, '\\');
  }
}

mfs.prototype.symlink = function(destination, path, type_, callback) {
  var type = (util.isString(type_) ? type_ : null);
  var callback = makeCallback(arguments[arguments.length - 1]);

  if (!nullCheck(destination, callback)) return;
  if (!nullCheck(path, callback)) return;

  var req = new FSReqWrap();
  req.oncomplete = callback;

  this._binding.symlink(preprocessSymlinkDestination(destination, type),
                        pathModule._makeLong(path),
                        type,
                        req);
};

mfs.prototype.symlinkSync = function(destination, path, type) {
  type = (util.isString(type) ? type : null);

  nullCheck(destination);
  nullCheck(path);

  return this._binding.symlink(preprocessSymlinkDestination(destination, type),
                               pathModule._makeLong(path),
                               type);
};

mfs.prototype.link = function(srcpath, dstpath, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(srcpath, callback)) return;
  if (!nullCheck(dstpath, callback)) return;

  var req = new FSReqWrap();
  req.oncomplete = callback;

  this._binding.link(pathModule._makeLong(srcpath),
                     pathModule._makeLong(dstpath),
                     req);
};

mfs.prototype.linkSync = function(srcpath, dstpath) {
  nullCheck(srcpath);
  nullCheck(dstpath);
  return this._binding.link(pathModule._makeLong(srcpath),
                            pathModule._makeLong(dstpath));
};

mfs.prototype.unlink = function(path, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.unlink(pathModule._makeLong(path), req);
};

mfs.prototype.unlinkSync = function(path) {
  nullCheck(path);
  return this._binding.unlink(pathModule._makeLong(path));
};

mfs.prototype.fchmod = function(fd, mode, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.fchmod(fd, modeNum(mode), req);
};

mfs.prototype.fchmodSync = function(fd, mode) {
  return this._binding.fchmod(fd, modeNum(mode));
};

//if (constants.hasOwnProperty('O_SYMLINK')) {
//  fs.lchmod = function(path, mode, callback) {
//    callback = maybeCallback(callback);
//    fs.open(path, constants.O_WRONLY | constants.O_SYMLINK, function(err, fd) {
//      if (err) {
//        callback(err);
//        return;
//      }
//      // prefer to return the chmod error, if one occurs,
//      // but still try to close, and report closing errors if they occur.
//      fs.fchmod(fd, mode, function(err) {
//        fs.close(fd, function(err2) {
//          callback(err || err2);
//        });
//      });
//    });
//  };
//
//  fs.lchmodSync = function(path, mode) {
//    var fd = fs.openSync(path, constants.O_WRONLY | constants.O_SYMLINK);
//
//    // prefer to return the chmod error, if one occurs,
//    // but still try to close, and report closing errors if they occur.
//    var err, err2;
//    try {
//      var ret = fs.fchmodSync(fd, mode);
//    } catch (er) {
//      err = er;
//    }
//    try {
//      fs.closeSync(fd);
//    } catch (er) {
//      err2 = er;
//    }
//    if (err || err2) throw (err || err2);
//    return ret;
//  };
//}


mfs.prototype.chmod = function(path, mode, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.chmod(pathModule._makeLong(path),
                      modeNum(mode),
                      req);
};

mfs.prototype.chmodSync = function(path, mode) {
  nullCheck(path);
  return this._binding.chmod(pathModule._makeLong(path), modeNum(mode));
};

//if (constants.hasOwnProperty('O_SYMLINK')) {
//  fs.lchown = function(path, uid, gid, callback) {
//    callback = maybeCallback(callback);
//    fs.open(path, constants.O_WRONLY | constants.O_SYMLINK, function(err, fd) {
//      if (err) {
//        callback(err);
//        return;
//      }
//      fs.fchown(fd, uid, gid, callback);
//    });
//  };
//
//  fs.lchownSync = function(path, uid, gid) {
//    var fd = fs.openSync(path, constants.O_WRONLY | constants.O_SYMLINK);
//    return fs.fchownSync(fd, uid, gid);
//  };
//}

mfs.prototype.fchown = function(fd, uid, gid, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.fchown(fd, uid, gid, req);
};

mfs.prototype.fchownSync = function(fd, uid, gid) {
  return this._binding.fchown(fd, uid, gid);
};

mfs.prototype.chown = function(path, uid, gid, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.chown(pathModule._makeLong(path), uid, gid, req);
};

mfs.prototype.chownSync = function(path, uid, gid) {
  nullCheck(path);
  return this._binding.chown(pathModule._makeLong(path), uid, gid);
};

// converts Date or number to a fractional UNIX timestamp
function toUnixTimestamp(time) {
  if (util.isNumber(time)) {
    return time;
  }
  if (util.isDate(time)) {
    // convert to 123.456 UNIX timestamp
    return time.getTime() / 1000;
  }
  throw new Error('Cannot parse time: ' + time);
}

// exported for unit tests, not for public consumption
mfs.prototype._toUnixTimestamp = toUnixTimestamp;

mfs.prototype.utimes = function(path, atime, mtime, callback) {
  callback = makeCallback(callback);
  if (!nullCheck(path, callback)) return;
  var req = new FSReqWrap();
  req.oncomplete = callback;
  this._binding.utimes(pathModule._makeLong(path),
                       toUnixTimestamp(atime),
                       toUnixTimestamp(mtime),
                       req);
};

mfs.prototype.utimesSync = function(path, atime, mtime) {
  nullCheck(path);
  atime = toUnixTimestamp(atime);
  mtime = toUnixTimestamp(mtime);
  this._binding.utimes(pathModule._makeLong(path), atime, mtime);
};

mfs.prototype.futimes = function(fd, atime, mtime, callback) {
  atime = toUnixTimestamp(atime);
  mtime = toUnixTimestamp(mtime);
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  this._binding.futimes(fd, atime, mtime, req);
};

mfs.prototype.futimesSync = function(fd, atime, mtime) {
  atime = toUnixTimestamp(atime);
  mtime = toUnixTimestamp(mtime);
  this._binding.futimes(fd, atime, mtime);
};

function writeAll(fd, buffer, offset, length, position, callback) {
  callback = maybeCallback(arguments[arguments.length - 1]);

  // write(fd, buffer, offset, length, position, callback)
  this.write(fd, buffer, offset, length, position, function(writeErr, written) {
    if (writeErr) {
      this.close(fd, function() {
        if (callback) callback(writeErr);
      });
    } else {
      if (written === length) {
        this.close(fd, callback);
      } else {
        offset += written;
        length -= written;
        position += written;
        writeAll(fd, buffer, offset, length, position, callback);
      }
    }
  });
}

mfs.prototype.writeFile = function(path, data, options, callback) {
  var callback = maybeCallback(arguments[arguments.length - 1]);

  if (util.isFunction(options) || !options) {
    options = { encoding: 'utf8', mode: 438 /*=0666*/, flag: 'w' };
  } else if (util.isString(options)) {
    options = { encoding: options, mode: 438, flag: 'w' };
  } else if (!util.isObject(options)) {
    throw new TypeError('Bad arguments');
  }

  assertEncoding(options.encoding);

  var flag = options.flag || 'w';
  this.open(path, flag, options.mode, function(openErr, fd) {
    if (openErr) {
      if (callback) callback(openErr);
    } else {
      var buffer = util.isBuffer(data) ? data : new Buffer('' + data,
          options.encoding || 'utf8');
      var position = /a/.test(flag) ? null : 0;
      writeAll(fd, buffer, 0, buffer.length, position, callback);
    }
  });
};

mfs.prototype.writeFileSync = function(path, data, options) {
  if (!options) {
    options = { encoding: 'utf8', mode: 438 /*=0666*/, flag: 'w' };
  } else if (util.isString(options)) {
    options = { encoding: options, mode: 438, flag: 'w' };
  } else if (!util.isObject(options)) {
    throw new TypeError('Bad arguments');
  }

  assertEncoding(options.encoding);

  var flag = options.flag || 'w';
  var fd = this.openSync(path, flag, options.mode);
  if (!util.isBuffer(data)) {
    data = new Buffer('' + data, options.encoding || 'utf8');
  }
  var written = 0;
  var length = data.length;
  var position = /a/.test(flag) ? null : 0;
  try {
    while (written < length) {
      written += this.writeSync(fd, data, written, length - written, position);
      position += written;
    }
  } finally {
    this.closeSync(fd);
  }
};

mfs.prototype.appendFile = function(path, data, options, callback_) {
  var callback = maybeCallback(arguments[arguments.length - 1]);

  if (util.isFunction(options) || !options) {
    options = { encoding: 'utf8', mode: 438 /*=0666*/, flag: 'a' };
  } else if (util.isString(options)) {
    options = { encoding: options, mode: 438, flag: 'a' };
  } else if (!util.isObject(options)) {
    throw new TypeError('Bad arguments');
  }

  if (!options.flag)
    options = util._extend({ flag: 'a' }, options);
  this.writeFile(path, data, options, callback);
};

mfs.prototype.appendFileSync = function(path, data, options) {
  if (!options) {
    options = { encoding: 'utf8', mode: 438 /*=0666*/, flag: 'a' };
  } else if (util.isString(options)) {
    options = { encoding: options, mode: 438, flag: 'a' };
  } else if (!util.isObject(options)) {
    throw new TypeError('Bad arguments');
  }
  if (!options.flag)
    options = util._extend({ flag: 'a' }, options);

  this.writeFileSync(path, data, options);
};

function FSWatcher() {
  EventEmitter.call(this);

  var self = this;
  var FSEvent = process.binding('fs_event_wrap').FSEvent;
  this._handle = new FSEvent();
  this._handle.owner = this;

  this._handle.onchange = function(status, event, filename) {
    if (status < 0) {
      self._handle.close();
      self.emit('error', errnoException(status, 'watch'));
    } else {
      self.emit('change', event, filename);
    }
  };
}
util.inherits(FSWatcher, EventEmitter);

FSWatcher.prototype.start = function(filename, persistent, recursive) {
  nullCheck(filename);
  var err = this._handle.start(pathModule._makeLong(filename),
                               persistent,
                               recursive);
  if (err) {
    this._handle.close();
    throw errnoException(err, 'watch');
  }
};

FSWatcher.prototype.close = function() {
  this._handle.close();
};

mfs.prototype.watch = function(filename) {
  nullCheck(filename);
  var watcher;
  var options;
  var listener;

  if (util.isObject(arguments[1])) {
    options = arguments[1];
    listener = arguments[2];
  } else {
    options = {};
    listener = arguments[1];
  }

  if (util.isUndefined(options.persistent)) options.persistent = true;
  if (util.isUndefined(options.recursive)) options.recursive = false;

  watcher = new FSWatcher();
  watcher.start(filename, options.persistent, options.recursive);

  if (listener) {
    watcher.addListener('change', listener);
  }

  return watcher;
};


// Stat Change Watchers

function StatWatcher() {
  EventEmitter.call(this);

  var self = this;
  this._handle = new this._binding.StatWatcher();

  // uv_fs_poll is a little more powerful than ev_stat but we curb it for
  // the sake of backwards compatibility
  var oldStatus = -1;

  this._handle.onchange = function(current, previous, newStatus) {
    if (oldStatus === -1 &&
        newStatus === -1 &&
        current.nlink === previous.nlink) return;

    oldStatus = newStatus;
    self.emit('change', current, previous);
  };

  this._handle.onstop = function() {
    self.emit('stop');
  };
}
util.inherits(StatWatcher, EventEmitter);


StatWatcher.prototype.start = function(filename, persistent, interval) {
  nullCheck(filename);
  this._handle.start(pathModule._makeLong(filename), persistent, interval);
};


StatWatcher.prototype.stop = function() {
  this._handle.stop();
};


var statWatchers = {};
function inStatWatchers(filename) {
  return Object.prototype.hasOwnProperty.call(statWatchers, filename) &&
      statWatchers[filename];
}


mfs.prototype.watchFile = function(filename) {
  nullCheck(filename);
  filename = pathModule.resolve(filename);
  var stat;
  var listener;

  var options = {
    // Poll interval in milliseconds. 5007 is what libev used to use. It's
    // a little on the slow side but let's stick with it for now to keep
    // behavioral changes to a minimum.
    interval: 5007,
    persistent: true
  };

  if (util.isObject(arguments[1])) {
    options = util._extend(options, arguments[1]);
    listener = arguments[2];
  } else {
    listener = arguments[1];
  }

  if (!listener) {
    throw new Error('watchFile requires a listener function');
  }

  if (inStatWatchers(filename)) {
    stat = statWatchers[filename];
  } else {
    stat = statWatchers[filename] = new StatWatcher();
    stat.start(filename, options.persistent, options.interval);
  }
  stat.addListener('change', listener);
  return stat;
};

mfs.prototype.unwatchFile = function(filename, listener) {
  nullCheck(filename);
  filename = pathModule.resolve(filename);
  if (!inStatWatchers(filename)) return;

  var stat = statWatchers[filename];

  if (util.isFunction(listener)) {
    stat.removeListener('change', listener);
  } else {
    stat.removeAllListeners('change');
  }

  if (EventEmitter.listenerCount(stat, 'change') === 0) {
    stat.stop();
    statWatchers[filename] = undefined;
  }
};

// Regexp that finds the next partion of a (partial) path
// result is [base_with_slash, base], e.g. ['somedir/', 'somedir']
if (isWindows) {
  var nextPartRe = /(.*?)(?:[\/\\]+|$)/g;
} else {
  var nextPartRe = /(.*?)(?:[\/]+|$)/g;
}

// Regex to find the device root, including trailing slash. E.g. 'c:\\'.
if (isWindows) {
  var splitRootRe = /^(?:[a-zA-Z]:|[\\\/]{2}[^\\\/]+[\\\/][^\\\/]+)?[\\\/]*/;
} else {
  var splitRootRe = /^[\/]*/;
}

mfs.prototype.realpathSync = function realpathSync(p, cache) {
  // make p is absolute
  p = pathModule.resolve(p);
  return p;
};


mfs.prototype.realpath = function realpath(p, cache, cb) {
  if (!util.isFunction(cb)) {
    cb = maybeCallback(cache);
    cache = null;
  }

  var original = p;

  // make p is absolute
  p = pathModule.resolve(p);

  if (cache) cache[original] = p;
  cb(null, p);
};


mfs.prototype.endTransaction = function endTransaction() {
  this._binding.endTransaction();
};

mfs.prototype.commitTransaction = function commitTransaction() {
  this._binding.commitTransaction();
};


var pool;

function allocNewPool(poolSize) {
  pool = new Buffer(poolSize);
  pool.used = 0;
}



mfs.prototype.createReadStream = function(path, options) {
  var fs = this;
  return new ReadStream(path, options, fs);
};

util.inherits(ReadStream, Readable);
mfs.prototype.ReadStream = ReadStream;

function ReadStream(path, options, fs) {
  if (!(this instanceof ReadStream))
    return new ReadStream(path, options);

  this._fs = fs ? fs : exports._rofs;

  // a little bit bigger buffer and water marks by default
  options = util._extend({
    highWaterMark: 64 * 1024
  }, options || {});

  Readable.call(this, options);

  this.path = path;
  this.fd = options.hasOwnProperty('fd') ? options.fd : null;
  this.flags = options.hasOwnProperty('flags') ? options.flags : 'r';
  this.mode = options.hasOwnProperty('mode') ? options.mode : 438; /*=0666*/

  this.start = options.hasOwnProperty('start') ? options.start : undefined;
  this.end = options.hasOwnProperty('end') ? options.end : undefined;
  this.autoClose = options.hasOwnProperty('autoClose') ?
      options.autoClose : true;
  this.pos = undefined;

  if (!util.isUndefined(this.start)) {
    if (!util.isNumber(this.start)) {
      throw TypeError('start must be a Number');
    }
    if (util.isUndefined(this.end)) {
      this.end = Infinity;
    } else if (!util.isNumber(this.end)) {
      throw TypeError('end must be a Number');
    }

    if (this.start > this.end) {
      throw new Error('start must be <= end');
    }

    this.pos = this.start;
  }

  if (!util.isNumber(this.fd))
    this.open();

  this.on('end', function() {
    if (this.autoClose) {
      this.destroy();
    }
  });
}

mfs.prototype.FileReadStream = mfs.prototype.ReadStream; // support the legacy name

ReadStream.prototype.open = function() {
  var self = this;
  this._fs.open(this.path, this.flags, this.mode, function(er, fd) {
    if (er) {
      if (self.autoClose) {
        self.destroy();
      }
      self.emit('error', er);
      return;
    }

    self.fd = fd;
    self.emit('open', fd);
    // start the flow of data.
    self.read();
  });
};

ReadStream.prototype._read = function(n) {
  if (!util.isNumber(this.fd))
    return this.once('open', function() {
      this._read(n);
    });

  if (this.destroyed)
    return;

  if (!pool || pool.length - pool.used < kMinPoolSpace) {
    // discard the old pool.
    pool = null;
    allocNewPool(this._readableState.highWaterMark);
  }

  // Grab another reference to the pool in the case that while we're
  // in the thread pool another read() finishes up the pool, and
  // allocates a new one.
  var thisPool = pool;
  var toRead = Math.min(pool.length - pool.used, n);
  var start = pool.used;

  if (!util.isUndefined(this.pos))
    toRead = Math.min(this.end - this.pos + 1, toRead);

  // already read everything we were supposed to read!
  // treat as EOF.
  if (toRead <= 0)
    return this.push(null);

  // the actual read.
  var self = this;
  self._fs.read(this.fd, pool, pool.used, toRead, this.pos, onread);

  // move the pool positions, and internal position for reading.
  if (!util.isUndefined(this.pos))
    this.pos += toRead;
  pool.used += toRead;

  function onread(er, bytesRead) {
    if (er) {
      if (self.autoClose) {
        self.destroy();
      }
      self.emit('error', er);
    } else {
      var b = null;
      if (bytesRead > 0)
        b = thisPool.slice(start, start + bytesRead);

      self.push(b);
    }
  }
};


ReadStream.prototype.destroy = function() {
  if (this.destroyed)
    return;
  this.destroyed = true;

  if (util.isNumber(this.fd))
    this.close();
};


ReadStream.prototype.close = function(cb) {
  var self = this;
  if (cb)
    this.once('close', cb);
  if (this.closed || !util.isNumber(this.fd)) {
    if (!util.isNumber(this.fd)) {
      this.once('open', close);
      return;
    }
    return process.nextTick(this.emit.bind(this, 'close'));
  }
  this.closed = true;
  close();

  function close(fd) {
    self._fs.close(fd || self.fd, function(er) {
      if (er)
        self.emit('error', er);
      else
        self.emit('close');
    });
    self.fd = null;
  }
};




mfs.prototype.createWriteStream = function(path, options) {
  var fs = this;
  return new WriteStream(path, options, fs);
};

util.inherits(WriteStream, Writable);
mfs.prototype.WriteStream = WriteStream;
function WriteStream(path, options, fs) {
  if (!(this instanceof WriteStream))
    return new WriteStream(path, options);

  this._fs = fs ? fs : exports._rofs;

  options = options || {};

  Writable.call(this, options);

  this.path = path;
  this.fd = null;

  this.fd = options.hasOwnProperty('fd') ? options.fd : null;
  this.flags = options.hasOwnProperty('flags') ? options.flags : 'w';
  this.mode = options.hasOwnProperty('mode') ? options.mode : 438; /*=0666*/

  this.start = options.hasOwnProperty('start') ? options.start : undefined;
  this.pos = undefined;
  this.bytesWritten = 0;

  if (!util.isUndefined(this.start)) {
    if (!util.isNumber(this.start)) {
      throw TypeError('start must be a Number');
    }
    if (this.start < 0) {
      throw new Error('start must be >= zero');
    }

    this.pos = this.start;
  }

  if (!util.isNumber(this.fd))
    this.open();

  // dispose on finish.
  this.once('finish', this.close);
}

mfs.prototype.FileWriteStream = mfs.prototype.WriteStream; // support the legacy name


WriteStream.prototype.open = function() {
  this._fs.open(this.path, this.flags, this.mode, function(er, fd) {
    if (er) {
      this.destroy();
      this.emit('error', er);
      return;
    }

    this.fd = fd;
    this.emit('open', fd);
  }.bind(this));
};


WriteStream.prototype._write = function(data, encoding, cb) {
  if (!util.isBuffer(data))
    return this.emit('error', new Error('Invalid data'));

  if (!util.isNumber(this.fd))
    return this.once('open', function() {
      this._write(data, encoding, cb);
    });

  var self = this;
  self._fs.write(this.fd, data, 0, data.length, this.pos, function(er, bytes) {
    if (er) {
      self.destroy();
      return cb(er);
    }
    self.bytesWritten += bytes;
    cb();
  });

  if (!util.isUndefined(this.pos))
    this.pos += data.length;
};


WriteStream.prototype.destroy = ReadStream.prototype.destroy;
WriteStream.prototype.close = ReadStream.prototype.close;

// There is no shutdown() for files.
WriteStream.prototype.destroySoon = WriteStream.prototype.end;


// SyncWriteStream is internal. DO NOT USE.
// Temporary hack for process.stdout and process.stderr when piped to files.
function SyncWriteStream(fd, options, fs) {
  Stream.call(this);

  this._fs = fs ? fs : exports._rofs;

  options = options || {};

  this.fd = fd;
  this.writable = true;
  this.readable = false;
  this.autoClose = options.hasOwnProperty('autoClose') ?
      options.autoClose : true;
}

util.inherits(SyncWriteStream, Stream);


// Export
mfs.prototype.SyncWriteStream = SyncWriteStream;


SyncWriteStream.prototype.write = function(data, arg1, arg2) {
  var encoding, cb;

  // parse arguments
  if (arg1) {
    if (util.isString(arg1)) {
      encoding = arg1;
      cb = arg2;
    } else if (util.isFunction(arg1)) {
      cb = arg1;
    } else {
      throw new Error('bad arg');
    }
  }
  assertEncoding(encoding);

  // Change strings to buffers. SLOW
  if (util.isString(data)) {
    data = new Buffer(data, encoding);
  }

  this._fs.writeSync(this.fd, data, 0, data.length);

  if (cb) {
    process.nextTick(cb);
  }

  return true;
};


SyncWriteStream.prototype.end = function(data, arg1, arg2) {
  if (data) {
    this.write(data, arg1, arg2);
  }
  this.destroy();
};


SyncWriteStream.prototype.destroy = function() {
  if (this.autoClose)
    this._fs.closeSync(this.fd);
  this.fd = null;
  this.emit('close');
  return true;
};

SyncWriteStream.prototype.destroySoon = SyncWriteStream.prototype.destroy;



// The read-only file systems,
var rofs = new mfs(mfs_binding.globalReadOnly());
exports._rofs = rofs;

var funs_to_copy = [
  'access', 'accessSync', 'exists', 'existsSync', 'readFile', 'readFileSync',
  'close', 'closeSync', 'open', 'openSync', 'read', 'readSync',
  'write', 'writeSync', 'rename', 'renameSync', 'truncate', 'truncateSync',
  'ftuncate', 'ftruncateSync', 'rmdir', 'rmdirSync',
  'fdatasync', 'fdatasyncSync', 'fsync', 'fsyncSync', 'mkdir', 'mkdirSync',
  'readdir', 'readdirSync', 'fstat', 'lstat', 'stat', 'fstatSync',
  'lstatSync', 'statSync', 'readlink', 'readlinkSync', 'symlink', 'symlinkSync',
  'link', 'linkSync', 'unlink', 'unlinkSync', 'fchmod', 'fchmodSync',
  'chmod', 'chmodSync', 'fchown', 'fchownSync', 'chown', 'chownSync',
  '_toUnixTimestamp', 'utimes', 'utimesSync', 'futimes', 'futimesSync',
  'writeFile', 'writeFileSync', 'appendFile', 'appendFileSync',
  'watch', 'watchFile', 'unwatchFile', 'realpathSync', 'realpath',
  'endTransaction', 'commitTransaction',
  'createReadStream', 'createWriteStream'
  ];

var rofs_proto = Object.getPrototypeOf(rofs);
funs_to_copy.forEach(function (member) {
  var mfun = rofs[member];
  exports[member] = function() { return mfun.apply(rofs, arguments); };
});
exports.ReadStream = mfs.prototype.ReadStream;
exports.FileReadStream = mfs.prototype.FileReadStream;
exports.WriteStream = mfs.prototype.WriteStream;
exports.FileWriteStream = mfs.prototype.FileWriteStream;
exports.SyncWriteStream = mfs.prototype.SyncWriteStream;
