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

package com.mckoi.mwpui.nodejs.rhino.deprec;

import com.mckoi.data.DataFile;
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.mwpui.nodejs.rhino.SmallocArrayData;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.webplatform.PlatformContext;
import org.mozilla.javascript.*;

import static com.mckoi.mwpui.NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Native binding to the Mckoi web platform file system.
 *
 * @author Tobias Downer
 */
public class BindingFs extends NativeObject {

  /**
   * The prototype stats class.
   */
  private NativeFunction general_stats_ob;

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

  
  /**
   * Maps file descriptors to a numerical value representing it.
   */
  private final Map<Integer, FileDescriptor> file_descriptor_map = new HashMap();
  private int current_fd_number = 128;


  @Override
  public String getClassName() {
    return "fs";
  }

  public BindingFs init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Object);
    int ro_attr = PERMANENT | READONLY;

    // Define the function invoke callback function,

    defineProperty("FSInitialize", new FSInitializeFun().init(scope), ro_attr);
    defineProperty("FSReqWrap", new FSReqWrapClass().init(scope), ro_attr);

    defineProperty("stat", new StatFun().init(scope), ro_attr);
    defineProperty("fstat", new StatFun().init(scope), ro_attr);
    defineProperty("open", new OpenFun().init(scope), ro_attr);
    defineProperty("close", new CloseFun().init(scope), ro_attr);
    defineProperty("read", new ReadFun().init(scope), ro_attr);
    defineProperty("readdir", new ReadDirFun().init(scope), ro_attr);

    return this;
  }

  /**
   * Returns true when there's a callback argument present.
   * 
   * @param args
   * @param index
   * @return 
   */
  private static boolean hasReqArg(Object[] args, int index) {
    return index < args.length;
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
                          Context cx,
                          Scriptable scope, Scriptable thisObj,
                          Object req_arg, Object... complete_args) {

    Scriptable req = (Scriptable) req_arg;
    BaseFunction oncomplete = (BaseFunction) req.get("oncomplete", null);

    JSProcessSharedState pstate = (JSProcessSharedState)
                                  cx.getThreadLocal(NODE_SHARED_PROCESS_STATE);
    pstate.postToIOQueue(thisObj, oncomplete, complete_args);

    return Undefined.instance;

  }


  // -----

  /**
   * Initializes the general stats object.
   */
  public class FSInitializeFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      NativeFunction stats_ob = (NativeFunction) args[0];
      general_stats_ob = stats_ob;
      return Boolean.TRUE;

    }

  }

  /**
   * File system callback function.
   */
  public static class FSReqWrapClass extends NativeFunction {

    public FSReqWrapClass init(Scriptable scope) {
      ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Function);
      return this;
    }

    @Override
    public String getClassName() {
      return "FSReqWrap";
    }

    


    @Override
    protected int getLanguageVersion() {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

    @Override
    protected int getParamCount() {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

    @Override
    protected int getParamAndVarCount() {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

    @Override
    protected String getParamOrVarName(int index) {
      // What should we do with these? They seem to be used by codegen/optimizer,
      throw new UnsupportedOperationException();
    }

  }

  /**
   * Emulates the Unix statSync.
   */
  public class StatFun extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      Object arg = args[0];

      FileInfo finfo;

      long file_size;
      
      // If it's a number then it's a FileDescriptor,
      if (ScriptRuntime.typeof(arg).equals("number")) {
        FileDescriptor fd = file_descriptor_map.get(ScriptRuntime.toInt32(arg));
        finfo = fd.getFileInfo();
        file_size = fd.getDataFile().size();
      }
      else {
        JSProcessSharedState pstate =
                (JSProcessSharedState) cx.getThreadLocal(NODE_SHARED_PROCESS_STATE);
        ServerCommandContext ctx = pstate.getServerCommandContext();

        PlatformContext pctx = ctx.getPlatformContext();
        FileName p = new FileName(args[0].toString());
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

      Object ZERO = Context.javaToJS(new Integer(0), scope);
      Object ONE = Context.javaToJS(new Integer(1), scope);
      Object mod_ts_number = Context.javaToJS(finfo.getLastModified(), scope);

      Object[] stats_construct = new Object[] {
        ZERO,                           // dev
        Context.javaToJS(mode, scope),  // mode
        ONE,                            // nlink
        ZERO,                           // uid
        ZERO,                           // gid
        ZERO,                           // rdev
        Undefined.instance,             // blksize
        ZERO,                           // ino
        new Long(file_size),            // size
        Undefined.instance,             // blocks
        mod_ts_number,                  // atim_msec
        mod_ts_number,                  // mtim_msec
        mod_ts_number,                  // ctim_msec
        mod_ts_number,                  // birthtim_msec
      };

      Object stat_result = general_stats_ob.construct(cx, scope, stats_construct);
      
      // Is this a callback?
      if (hasReqArg(args, 1)) {
        return postToImmediateQueue("stat", cx, scope, thisObj, args[1], null, stat_result);
      }
      return stat_result;

    }

  }

  /**
   * Emulates a file open. Returns a file descriptor.
   */
  public class OpenFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("open");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String path = args[0].toString();
      int flags = ScriptRuntime.toInt32(args[1]);
      Object mode = args[2];
      boolean is_deferred = hasReqArg(args, 3);

      int fd_number;

      try {

        // Currently we can only read from files,
        if (flags != O_RDONLY) {
          throw Context.reportRuntimeError("Only 'r' flag is currently supported");
        }

        JSProcessSharedState pstate =
                  (JSProcessSharedState) cx.getThreadLocal(NODE_SHARED_PROCESS_STATE);
        ServerCommandContext ctx = pstate.getServerCommandContext();

        FileName fname = new FileName(path);
        FileSystem fs = ctx.getPlatformContext().getFileRepositoryFor(fname);
        if (fs == null) {
          throw Context.reportRuntimeError("File not found: " + fname.toString());
        }
        String fname_str = fname.getPathFile();
        FileInfo finfo = fs.getFileInfo(fname_str);
        if (finfo == null || finfo.isDirectory()) {
          throw Context.reportRuntimeError("File not found: " + fname.toString());
        }

        fd_number = current_fd_number;
        current_fd_number = current_fd_number + 1;

        // Assign it a file descriptor number by adding to the map,
        FileDescriptor fd = new FileDescriptor(finfo);
        file_descriptor_map.put(fd_number, fd);

        if (is_deferred) {
          return postToImmediateQueue("open", cx, scope, thisObj, args[3], null, fd_number);
        }
        // Return the descriptor,
        return fd_number;

      }
      catch (EvaluatorException ex) {
        if (is_deferred) {
          return postToImmediateQueue("open", cx, scope, thisObj, args[3], ex);
        }
        throw ex;
      }

    }

  }

  /**
   * Close file function.
   */
  public class CloseFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("close");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // Remove from the map,
      int fd_number = ScriptRuntime.toInt32(args[0]);
      file_descriptor_map.remove(fd_number);

      if (hasReqArg(args, 1)) {
        return postToImmediateQueue("close", cx, scope, thisObj, args[1], (Object) null);
      }
      return Boolean.TRUE;

    }

  }

  public class ReadFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("read");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // Look up the file descriptor in the map,
      FileDescriptor fd = file_descriptor_map.get(ScriptRuntime.toInt32(args[0]));
      ScriptableObject buffer = (ScriptableObject) args[1];
      int offset = ScriptRuntime.toInt32(args[2]);
      int length = ScriptRuntime.toInt32(args[3]);
      long position = -1;
      if (!Undefined.instance.equals(args[4])) {
        position = ((Number) Context.jsToJava(args[4], Number.class)).longValue();
      }

      SmallocArrayData byte_arr =
                              (SmallocArrayData) buffer.getExternalArrayData();
      DataFile dfile = fd.getDataFile();
      if (position >= 0) {
        dfile.position(position);
      }
      
      // Clip the read against the end of the file,
      int to_read = (int) Math.min(dfile.size() - dfile.position(), length);
      
      dfile.get(byte_arr.getByteArray(), offset, to_read);

      if (hasReqArg(args, 5)) {
        return postToImmediateQueue("read", cx, scope, thisObj, args[5], null, to_read, buffer);
      }
      return to_read;

    }

  }
  
  /**
   * Read directory function.
   */
  public class ReadDirFun extends RhinoFunction {

    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("readdir");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String path = args[0].toString();
      boolean is_deferred = hasReqArg(args, 1);

      try {
        JSProcessSharedState pstate =
                  (JSProcessSharedState) cx.getThreadLocal(NODE_SHARED_PROCESS_STATE);
        ServerCommandContext ctx = pstate.getServerCommandContext();

        FileName fname = new FileName(path).asDirectory();
        FileSystem fs = ctx.getPlatformContext().getFileRepositoryFor(fname);
        if (fs == null) {
          throw Context.reportRuntimeError("Path not found: " + fname.toString());
        }
        String fname_str = fname.getPathFile();
        List<FileInfo> native_dir = fs.getDirectoryFileInfoList(fname_str);
        if (native_dir == null) {
          throw Context.reportRuntimeError("Path not found: " + fname.toString());
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
          arr[i] = Context.javaToJS(mod_path, scope);
          ++i;
        }

        // Wrap the list in an array,
        ScriptableObject dir_list_arr =
                                    (ScriptableObject) cx.newArray(scope, arr);

        if (is_deferred) {
          return postToImmediateQueue("readdir", cx, scope, thisObj, args[1], null, dir_list_arr);
        }
        return dir_list_arr;

      }
      catch (EvaluatorException ex) {
        if (is_deferred) {
          return postToImmediateQueue("readdir", cx, scope, thisObj, args[1], ex);
        }
        throw ex;
      }

    }

  }
  
  
  public class FileDescriptor {
    private final FileInfo file_info;
    private DataFile data_file;
    public FileDescriptor(FileInfo file_info) {
      this.file_info = file_info;
    }
    public DataFile getDataFile() {
      if (data_file == null) {
        data_file = file_info.getDataFile();
        data_file.position(0);
      }
      return data_file;
    }
    public FileInfo getFileInfo() {
      return file_info;
    }
  }



}
