/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mckoi.mwpui.nodejs;

import com.mckoi.data.DataFile;
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import java.nio.ByteBuffer;
import java.util.List;

/**
 *
 * @author Tobias Downer
 */
public class NodeNativeFs {

  private static final int O_RDONLY = 0;
  private static final int O_WRONLY = 1;
  private static final int O_RDWR = 2;
  private static final int S_IFMT = 61440;
  private static final int S_IFREG = 32768;
  private static final int S_IFDIR = 16384;
  private static final int S_IFCHR = 8192;
  private static final int S_IFLNK = 40960;
  private static final int O_CREAT = 256;
  private static final int O_EXCL = 1024;
  private static final int O_TRUNC = 512;
  private static final int O_APPEND = 8;
  private static final int F_OK = 0;
  private static final int R_OK = 4;
  private static final int W_OK = 2;
  private static final int X_OK = 1;

  private final NodeMckoiInternal internal;

//  /**
//   * Maps file descriptors to a numerical value representing it.
//   */
//  private final Map<Integer, FileDescriptor> file_descriptor_map = new HashMap();
//  private int current_fd_number = 128;

  public NodeNativeFs(NodeMckoiInternal nm_internal) {
    this.internal = nm_internal;
  }

  /**
   * Sets up all the file system functions on the given object.
   * 
   * @param ob 
   * @throws java.lang.NoSuchMethodException 
   */
  public void setupFSFunctionsOn(GJSObject ob) throws NoSuchMethodException {
    GJSRuntime.setWrappedFunction(
            this, "fs", "FSInitialize", ob);
    GJSRuntime.setWrappedFunction(
            this, "fs", "stat", ob);
    GJSRuntime.setWrappedFunction(
            this, "fs", "open", ob);
    GJSRuntime.setWrappedFunction(
            this, "fs", "close", ob);
    GJSRuntime.setWrappedFunction(
            this, "fs", "read", ob);
    GJSRuntime.setWrappedFunction(
            this, "fs", "readdir", ob);
  }

  /**
   * Returns true when there's a callback argument present.
   * 
   * @param args
   * @param index
   * @return 
   */
  private static boolean hasReqArg(Object[] args, int index) {
    return index < args.length && !GJSStatics.UNDEFINED.equals(args[index]);
  }

  /**
   * Posts a deferred function to be immediately called when the process is
   * not currently executing javascript. The 'complete_args' array is the
   * arguments to give the 'oncomplete' function.
   * 
   * @param req_arg
   * @param complete_args
   */
  private static Object postToImmediateQueue(String description,
                          Object thisObj, Object req_arg,
                          Object... complete_args) {

    GJSObject req = (GJSObject) req_arg;
    GJSObject oncomplete = (GJSObject) req.getMember("oncomplete");

    GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
    pstate.postToIOQueue(thisObj, oncomplete, complete_args);

    return GJSStatics.UNDEFINED;

  }

  /**
   * Called by nodejs to pass information here about how to format a stats
   * object.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object FSInitialize(Object thiz, Object... args) {
    GJSObject stats_ob = (GJSObject) args[0];
    internal.setFsStatsObject(stats_ob);
    return Boolean.TRUE;
  }

//  /**
//   * Can be constructed to create new objects of this type.
//   */
//  public static class FSReqWrapClass extends GJSAbstractFunction {
//
//    public FSReqWrapClass(String function_name) {
//      super(function_name);
//    }
//
//    @Override
//    public Object call(Object thiz, Object... args) {
//      // This is the constructor call,
//      return GJSStatics.UNDEFINED;
//    }
//
//  }

  private static FileName fileToAbsolute(GJSProcessSharedState pstate,
                                        String location, boolean directory) {
    FileName fname;
    if (directory) {
      fname = new FileName(location).asDirectory();
    }
    else {
      fname = new FileName(location);
    }
    if (fname.isRelative()) {
      FileName pwd = new FileName(pstate.getEnv().get("pwd")).asDirectory();
      fname = pwd.resolve(fname);
    }
    return fname;
  }

  /**
   * Posix-like stat command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object stat(Object thiz, Object... args) {

    Object arg = args[0];

    FileInfo finfo;

    long file_size;

    // If it's a number then it's a FileDescriptor,
    if (arg instanceof Number) {
      FileDescriptor fd = internal.getFDMap().getFD(GJSRuntime.toInt32(arg));
      finfo = fd.getFileInfo();
      file_size = fd.getDataFile().size();
    }
    else {
      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      ServerCommandContext ctx = pstate.getServerCommandContext();

      PlatformContext pctx = ctx.getPlatformContext();
      FileName p = fileToAbsolute(pstate, args[0].toString(), false);
      FileSystem fs = pctx.getFileRepositoryFor(p);
      if (fs == null) {
        // Repository not available,
        return Boolean.FALSE;
      }
      String fname = p.getPathFile();
      finfo = fs.getFileInfo(fname);
      if (finfo == null) {
        // Check if it's a directory
        if (!fname.endsWith("/")) {
          finfo = fs.getFileInfo(fname + "/");
        }
        if (finfo == null) {
          // If it doesn't exist,
          return Boolean.FALSE;
        }
      }
      if (finfo.isFile()) {
        file_size = finfo.getDataFile().size();
      }
      else {
        file_size = 0;
      }
    }

    int mode = 0;
    if (finfo.isFile()) mode |= S_IFREG;
    if (finfo.isDirectory()) mode |= S_IFDIR;

    Object ZERO = new Integer(0);
    Object ONE = new Integer(1);
    Object mod_ts_number = finfo.getLastModified();

    Object[] stats_construct = new Object[] {
      ZERO,                           // dev
      new Integer(mode),              // mode
      ONE,                            // nlink
      ZERO,                           // uid
      ZERO,                           // gid
      ZERO,                           // rdev
      GJSStatics.UNDEFINED,           // blksize
      ZERO,                           // ino
      new Long(file_size),            // size
      GJSStatics.UNDEFINED,           // blocks
      mod_ts_number,                  // atim_msec
      mod_ts_number,                  // mtim_msec
      mod_ts_number,                  // ctim_msec
      mod_ts_number,                  // birthtim_msec
    };

    Object stat_result = internal.getFsStatsObject().newObject(stats_construct);

    // Is this a callback?
    if (hasReqArg(args, 1)) {
      return postToImmediateQueue("stat", thiz, args[1], null, stat_result);
    }
    return stat_result;

  }

  /**
   * Posix-like fstat command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object fstat(Object thiz, Object... args) {
    return stat(thiz, args);
  }

  /**
   * Posix-like open command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object open(Object thiz, Object... args) {

    String path = args[0].toString();
    int flags = GJSRuntime.toInt32(args[1]);
    Object mode = args[2];
    boolean is_deferred = hasReqArg(args, 3);

    int fd_number;

    try {

      // Currently we can only read from files,
      if (flags != O_RDONLY) {
        throw new GJavaScriptException("Only 'r' flag is currently supported");
      }

      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      ServerCommandContext ctx = pstate.getServerCommandContext();

      FileName fname = fileToAbsolute(pstate, path, false);
      FileRepository fs = ctx.getPlatformContext().getFileRepositoryFor(fname);
      if (fs == null) {
        throw new GJavaScriptException("File not found: " + fname.toString());
      }
      String fname_str = fname.getPathFile();
      FileInfo finfo = fs.getFileInfo(fname_str);
      if (finfo == null || finfo.isDirectory()) {
        throw new GJavaScriptException("File not found: " + fname.toString());
      }

      // Assign it a file descriptor number by adding to the map,
      fd_number = internal.getFDMap().mapFD(new FileDescriptor(fs, finfo, false));

      if (is_deferred) {
        return postToImmediateQueue("open", thiz, args[3], null, fd_number);
      }
      // Return the descriptor,
      return fd_number;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("open", thiz, args[3], ex);
      }
      throw ex;
    }

  }

  /**
   * Posix-like close command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object close(Object thiz, Object... args) {
    // Remove from the map,
    int fd_number = GJSRuntime.toInt32(args[0]);
    internal.getFDMap().removeFD(fd_number);

    if (hasReqArg(args, 1)) {
      return postToImmediateQueue("close", thiz, args[1], (Object) null);
    }
    return Boolean.TRUE;
  }

  /**
   * Posix-like read command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object read(Object thiz, Object... args) {

    // Look up the file descriptor in the map,
    FileDescriptor fd = internal.getFDMap().getFD(GJSRuntime.toInt32(args[0]));
    GJSObject buffer = (GJSObject) args[1];
    int offset = GJSRuntime.toInt32(args[2]);
    int length = GJSRuntime.toInt32(args[3]);
    long position = -1;
    if (!GJSStatics.UNDEFINED.equals(args[4])) {
      position = GJSRuntime.toInt64(args[4]);
    }

    ByteBuffer byte_buf = GJSRuntime.system().getExternalArrayDataOf(buffer);
    DataFile dfile = fd.getDataFile();
    if (position >= 0) {
      dfile.position(position);
    }

    // Clip the read against the end of the file,
    int to_read = (int) Math.min(dfile.size() - dfile.position(), length);

    dfile.get(byte_buf.array(), offset, to_read);

    if (hasReqArg(args, 5)) {
      return postToImmediateQueue("read", thiz, args[5], null, to_read, buffer);
    }
    return to_read;

  }

  /**
   * Posix-like readdir command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object readdir(Object thiz, Object... args) {

    String path = args[0].toString();
    boolean is_deferred = hasReqArg(args, 1);

    try {
      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      ServerCommandContext ctx = pstate.getServerCommandContext();

      FileName fname = fileToAbsolute(pstate, path, true);
      FileSystem fs = ctx.getPlatformContext().getFileRepositoryFor(fname);
      if (fs == null) {
        throw new GJavaScriptException("Path not found: " + fname.toString());
      }
      String fname_str = fname.getPathFile();
      List<FileInfo> native_dir = fs.getDirectoryFileInfoList(fname_str);
      if (native_dir == null) {
        throw new GJavaScriptException("Path not found: " + fname.toString());
      }

      // PENDING: Make this into a dynamic array class that materializes the
      //   information as the array is traversed?

      Object[] arr = new Object[native_dir.size()];
      int i = 0;
      for (FileInfo finfo : native_dir) {
        String mod_path = finfo.getItemName();
        if (mod_path.endsWith("/")) {
          mod_path = mod_path.substring(0, mod_path.length() - 1);
        }
        arr[i] = mod_path;
        ++i;
      }

      // Wrap the list in an array,
      GJSObject dir_list_arr = GJSRuntime.system().asJavaScriptArray(arr);

      if (is_deferred) {
        return postToImmediateQueue("readdir", thiz, args[1], null, dir_list_arr);
      }
      return dir_list_arr;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("readdir", thiz, args[1], ex);
      }
      throw ex;
    }

  }
  

  
  // -----

//  public static class FileDescriptor {
//    private final FileInfo file_info;
//    private DataFile data_file;
//    public FileDescriptor(FileInfo file_info) {
//      this.file_info = file_info;
//    }
//    public DataFile getDataFile() {
//      if (data_file == null) {
//        data_file = file_info.getDataFile();
//        data_file.position(0);
//      }
//      return data_file;
//    }
//    public FileInfo getFileInfo() {
//      return file_info;
//    }
//  }
  
}
