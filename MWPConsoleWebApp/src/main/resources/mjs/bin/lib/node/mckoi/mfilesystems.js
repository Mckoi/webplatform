
'use strict';

var util = require('util');
var mfs_c = require('mckoi/mfs').mfs_c;

var fs_binding = process.binding('fs');
var binding = process.binding('mfilesystems');
var FSReqWrap = fs_binding.FSReqWrap;

var mfilesystems = exports;

function makeCallback(cb) {
  if (!util.isFunction(cb)) {
    return rethrow();
  }

  return function() {
    return cb.apply(null, arguments);
  };
}


mfilesystems.open = function(repository_id, callback) {
  var req = new FSReqWrap();
  req.oncomplete = makeCallback(callback);
  binding.open(repository_id, req);
};

mfilesystems.openSync = function(repository_id) {
  var mfs_binding = binding.open(repository_id);
  return new mfs_c(mfs_binding);
};




