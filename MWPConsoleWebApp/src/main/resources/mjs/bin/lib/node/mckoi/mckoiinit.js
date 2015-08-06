
// Sets up Node's native process hooks,

'use strict';


(function(process, $i) {

  var DEBUG_LOG = true;

  var global = this;

//  for (var key in $i) {
//    print(key + " = " + $i[key]);
//  }

  // Make a log for mckoiinit only. If there's any errors we can look at this.
  if (DEBUG_LOG) {
    process._syslog = '';
    Object.defineProperty(process, "_syslog", {
            configurable: true, enumerable: false, writable: true });
  }
  function logInfo(text) {
    if (DEBUG_LOG) {
      process._syslog += text + "\n";
    }
  };

  // The Timer object 

  var Timer = (function() {
    function Timer() { };
    function now() {
      return $i.timer_now();
    }
    function start(msec, v2) {
      return $i.timer_start(this, msec, v2);
    }
    function close() {
      return $i.timer_close(this);
    }
    function ref() {
      this._ref = "REF";
    }
    function unref() {
      this._ref = "UNREF";
    }
    Timer.kOnTimeout = 0;
    Timer.now = now;
    Timer.prototype.start = start;
    Timer.prototype.close = close;
    Timer.prototype.ref = ref;
    Timer.prototype.unref = unref;
    return Timer;
  })();

  /**
   * Constants as returned by binding('constants')
   */
  var nativeConstants = {
    SIGHUP: 1,
    SIGINT: 2,
    SIGILL: 4,
    SIGABRT: 22,
    SIGFPE: 8,
    SIGKILL: 9,
    SIGSEGV: 11,
    SIGTERM: 15,
    SIGBREAK: 21,
    SIGWINCH: 28,

    O_RDONLY: 0,
    O_WRONLY: 1,
    O_RDWR: 2,
    S_IFMT: 61440,
    S_IFREG: 32768,
    S_IFDIR: 16384,
    S_IFCHR: 8192,
    S_IFLNK: 40960,
    O_CREAT: 256,
    O_EXCL: 1024,
    O_TRUNC: 512,
    O_APPEND: 8,
    F_OK: 0,
    R_OK: 4,
    W_OK: 2,
    X_OK: 1
  };

  // process.binding('fs').FSReqWrap
  function FSReqWrap() {
  }

  // We create static object instances for each binding API with a
  // self-invoking anon function.

  // --- contextify ---
  
  function ContextifyScript(code, options) {
    if (!(this instanceof ContextifyScript)) {
      throw new Error("Expecting construction");
    }
    this.runInThisContext = function() {
      return $i.contextify_runInThisContext(code, options);
    };
  };
  var contextify = (function() {
    var contextify = {};
    contextify.ContextifyScript = ContextifyScript;
    return contextify;
  })();

  // --- buffer ---

  var buffer = (function() {
    function setupBufferJS(buffer_ob, internal_ob) {
      return $i.buffer_setupBufferJS(buffer_ob, internal_ob);
    }
    return {
      setupBufferJS: setupBufferJS
    };
  })();

  // --- smalloc ---

  var smalloc = (function() {
    function alloc(source, alloc_size) {
      return $i.smalloc_alloc(source, alloc_size);
    }
    function truncate(source, length) {
      return $i.smalloc_truncate(source, length);
    }
    function sliceOnto(source, dest, start, end) {
      return $i.smalloc_sliceOnto(source, dest, start, end);
    }
    return {
      kMaxLength: (1024 * 1024),
      alloc: alloc,
      truncate: truncate,
      sliceOnto: sliceOnto
    };
  })();

  // --- mwpconsole ---

  var mwpconsole = (function() {
    function write(dest, content) {
      return $i.mwpconsole_write(dest, content);
    }
    return {
      write: write
    };
  })();

//  // --- fs ---
//
//  var fs = (function() {
//    function FSInitialize(stats_class) {
//      return $i.fs_FSInitialize(stats_class);
//    }
//    function stat(v, req) {
//      return $i.fs_stat(v, req);
//    }
//    function fstat(v, req) {
//      return $i.fs_stat(v, req);
//    }
//    function open(path, flags, mode, req) {
//      return $i.fs_open(path, flags, mode, req);
//    }
//    function close(fd, req) {
//      return $i.fs_close(fd, req);
//    }
//    function read(fd, buffer, offset, length, position, req) {
//      return $i.fs_read(fd, buffer, offset, length, position, req);
//    }
//    function readdir(path, req) {
//      return $i.fs_readdir(path, req);
//    }
//    // Marker for read_only file system,
//    var read_only_fs = {};
//    read_only_fs._mckoiRofs = 1;
//    return {
//      _fs: read_only_fs,
//      FSReqWrap: FSReqWrap,
//      FSInitialize: FSInitialize,
//      stat: stat,
//      fstat: fstat,
//      open: open,
//      close: close,
//      read: read,
//      readdir: readdir
//    };
//  })();

  // --- mfs ---

  var mfs_class = (function() {
    // Prototype functions,

    function access(path, mode, req) {
      return $i.mfs_access(this._fs, path, mode, req);
    }
    function close(fd, req) {
      return $i.mfs_close(this._fs, fd, req);
    }
    function open(path, flags, mode, req) {
      return $i.mfs_open(this._fs, path, flags, mode, req);
    }
    function read(fd, buffer, offset, length, position, req) {
      return $i.mfs_read(this._fs, fd, buffer, offset, length, position, req);
    }
    function readdir(path, req) {
      return $i.mfs_readdir(this._fs, path, req);
    }
    function stat(v, req) {
      return $i.mfs_stat(this._fs, v, req);
    }
    function fstat(v, req) {
      return $i.mfs_stat(this._fs, v, req);
    }

    function rename(filen, newfilen, req) {
      return $i.mfs_rename(this._fs, filen, newfilen, req);
    }
    function ftruncate(fd, len, req) {
      return $i.mfs_ftruncate(this._fs, fd, len, req);
    }
    function rmdir(path, req) {
      return $i.mfs_rmdir(this._fs, path, req);
    }
    function mkdir(path, mode, req) {
      return $i.mfs_mkdir(this._fs, path, mode, req);
    }
    function writeBuffer(fd, buffer, offset, length, position, req) {
      return $i.mfs_writeBuffer(this._fs, fd, buffer, offset, length, position, req);
    }
    function writeString(fd, data, position, encoding, req) {
      return $i.mfs_writeString(this._fs, fd, data, position, encoding, req);
    }
    function utimes(path, atime, mtime, req) {
      return $i.mfs_utimes(this._fs, path, atime, mtime, req);
    }
    function futimes(fd, atime, mtime, req) {
      return $i.mfs_futimes(this._fs, fd, atime, mtime, req);
    }

    function fshift(fd, position, offset) {
      return $i.mfs_fshift(this._fs, fd, position, offset);
    }
    function fsetSize(fd, size) {
      return $i.mfs_fsetSize(this._fs, fd, size);
    }
    function fcopy(fd_src, pos_src, fd_dest, pos_dest, len) {
      return $i.mfs_fcopy(this._fs, fd_src, pos_src, fd_dest, pos_dest, len);
    }

    function endTransaction(req) {
      return $i.mfilesystems_close(this._fs, req);
    }
    function commitTransaction(req) {
      return $i.mfilesystems_commit(this._fs, req);
    }

    // Constructor,
    function MFS(fs) {
      if (!(this instanceof MFS)) throw new Error("Construction error");
      this._fs = fs;
    }
    var $p = MFS.prototype;
    $p.access = access;
    $p.close = close;
    $p.open = open;
    $p.read = read;
    $p.readdir = readdir;
    $p.stat = stat;
    $p.fstat = fstat;
    $p.rename = rename;
    $p.ftruncate = ftruncate;
    $p.rmdir = rmdir;
    $p.mkdir = mkdir;
    $p.writeBuffer = writeBuffer;
    $p.writeString = writeString;
    $p.utimes = utimes;
    $p.futimes = futimes;
    $p.fshift = fshift;
    $p.fsetSize = fsetSize;
    $p.fcopy = fcopy;
    $p.endTransaction = endTransaction;
    $p.commitTransaction = commitTransaction;
    return MFS;

  })();

  // --- mfilesystems ---

  var mfilesystems = (function() {

    function FSInitialize(stats_class) {
      return $i.mfs_FSInitialize(stats_class);
    }

    function globalReadOnly() {
      var ro = {};
      ro._mckoiRoFs = 1;
      return new mfs_class(ro);
    }

    // Being transaction on file system repository id,
    function open(rep_id, req) {
      // The transaction,
      if (req) {
        var wrapped_req = {};
        wrapped_req.oncomplete = function(err, repo_fs) {
          if (err) req.oncomplete(err);
          else {
            req.oncomplete(err, new mfs_class(repo_fs));
          }
        };
        $i.mfilesystems_open(rep_id, wrapped_req);
      }
      else {
        return new mfs_class($i.mfilesystems_open(rep_id));
      }
    }
    // End transaction,
    function close(mfsc, req) {
      return mfsc.endTransaction(req);
    }
    // Commit transaction,
    function commit(mfsc, req) {
      return mfsc.commitTransaction(req);
    }

    return {
      globalReadOnly : globalReadOnly,
      FSReqWrap : FSReqWrap,
      FSInitialize : FSInitialize,
      open: open,
      close: close,
      commit: commit
    };

  })();

  // --- timer_wrap ---

  var timer_wrap = (function() {
    return {
      Timer: Timer
    };
  })();



  /**
   * 
   * @param {String} arg
   * @returns {} native function result.
   */
  function binding(arg) {

    // --- contextify ---

    if (arg === 'contextify') {
      return contextify;
    }

    else if (arg === 'natives') {
      return $i.natives_object;
    }

    else if (arg === 'buffer') {
      return buffer;
    }

    else if (arg === 'smalloc') {
      return smalloc;
    }

    else if (arg === 'constants') {
      return nativeConstants;
    }

    else if (arg === 'mwpconsole') {
      return mwpconsole;
    }

//    else if (arg === 'fs') {
//      return fs;
//    }

    else if (arg === 'mfilesystems') {
      return mfilesystems;
    }

    else if (arg === 'timer_wrap') {
      return timer_wrap;
    }

    throw new Error('Unknown native binding: ' + arg);
  };

  function runMicrotasks() {
    return $i.process_runMicrotasks();
  }
  function _setupNextTick(tick_info, _tickCallback, _runMicrotasks) {
    tick_info['0'] = 0;
    tick_info['1'] = 0;
    _runMicrotasks.runMicrotasks = runMicrotasks;
    $i.process_exposeTickCallback(_tickCallback);
  }

  function cwd() {
    return $i.process_cwd();
  }

//  // We shouldn't show the function content when inspected.
//  // NOTE: This is easily reversed by calling 'delete class.toString;'
//
//  var NATIVETOSTRING = 'function() { [native] }';
//  var native_functions = [
//        nativeBinding, nativeSetupNextTick, nativeCWD, nativeRunMicrotasks,
//        ContextifyScript
//      ];
//  native_functions.forEach(function(f) {
//    f.toString = NATIVETOSTRING;
//  });

  // Check if Array.splice behaves like V8,
  if (JSON.stringify([1, 2, 3, 4].splice(2)) !== JSON.stringify([3, 4])) {
    logInfo("Array.prototype.splice single argument function changed");
    // Need to redefine splice,
    var splice_fun = Array.prototype.splice;
    Array.prototype.splice = function() {
      if (arguments.length === 1) {
        return splice_fun.call(this, arguments[0], this.length);
      }
      return splice_fun.apply(this, arguments);
    };
  }



  // Define the mozilla Object.prototype functions if they are missing,
  // (They are missing in Nashorn by default)
  if (Object.prototype.__defineGetter__ === undefined) {

    logInfo("Added [__defineGetter__, __defineSetter__, __lookupGetter__, __lookupSetter__]");

    Object.defineProperty(Object.prototype, "__defineGetter__", {
      configurable: true, enumerable: false, writable: true,
      value: function(name, func) {
        Object.defineProperty(this, name, {
              configurable: true, enumerable: true, get: func });
      }
    });

    Object.defineProperty(Object.prototype, "__defineSetter__", {
      configurable: true, enumerable: false, writable: true,
      value: function(name, func) {
        Object.defineProperty(this, name, {
              configurable: true, enumerable: true, set: func });
      }
    });

    Object.defineProperty(Object.prototype, "__lookupGetter__", {
      configurable: true, enumerable: false, writable: true,
      value: function(name) {
        var obj = this;
        while (obj) {
          var desc = Object.getOwnPropertyDescriptor(obj, name);
          if (desc) return desc.get;
          obj = Object.getPrototypeOf(obj);
        }
        return undefined;
      }
    });

    Object.defineProperty(Object.prototype, "__lookupSetter__", {
      configurable: true, enumerable: false, writable: true,
      value: function(name) {
        var obj = this;
        while (obj) {
          var desc = Object.getOwnPropertyDescriptor(obj, name);
          if (desc) return desc.set;
          obj = Object.getPrototypeOf(obj);
        }
        return undefined;
      }
    });

  }

  // Some of Nashorn's globals we need to remove,
  var remove_list = [];
  var globals_to_remove = [
    "print", "quit", "exit", "load", "loadWithNewGlobal"
  ];
  globals_to_remove.forEach(function (name) {
    if (typeof global[name] !== 'undefined') {
      try {
        delete global[name];
        if (DEBUG_LOG) {
          remove_list.push(name);
        }
      }
      catch (e) {
        logInfo("Exception when trying to delete " + name + ": " + e);
      }
    }
  });
  if (DEBUG_LOG) {
    var remove_list_str =
              remove_list.length === 0 ? '[None]' : remove_list.join(', ');
    logInfo("Properties removed from global: " + remove_list_str);
  }

  // The process variables,

  var NODE_VERSION = 'v0.12.7';

  process.title = '';
  process.version = NODE_VERSION;
  process.moduleLoadList = [];
  process.versions = {
    node: NODE_VERSION
  };
  process.platform = 'mwp';
  $i.process_setupProcessJS(process);
  
  process.binding = binding;
  process._setupNextTick = _setupNextTick;
  process.cwd = cwd;

});
