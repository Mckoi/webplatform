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
import com.mckoi.data.DataFileUtils;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileUtilities;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tobias Downer
 */
public class NodeNativeMfs {

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

//  private GJSObject general_stats_ob;
  private final NodeMckoiInternal internal;

  public NodeNativeMfs(NodeMckoiInternal nm_internal) {
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
            this, "mfs", "FSInitialize", ob);
    
    GJSRuntime.setWrappedFunction(
            this, "mfs", "close", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "open", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "read", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "readdir", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "stat", ob);
    
    GJSRuntime.setWrappedFunction(
            this, "mfs", "rename", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "ftruncate", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "rmdir", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "mkdir", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "writeBuffer", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "writeString", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "utimes", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "futimes", ob);

    GJSRuntime.setWrappedFunction(
            this, "mfs", "fshift", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "fsetSize", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfs", "fcopy", ob);

  }

  /**
   * Casts the argument to a FileRepository object.
   * 
   * @param arg
   * @return 
   */
  private static FileRepository toFileRepository(Object arg) {
    return NodeNativeMFileSystems.toFileRepository(arg);
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
   * If 'fname' exists then determines if 'fname' can resolve to a directory
   * or a file. For example, if '/hoop/test1/' directory exists then
   * a file name of '/hoop/test1' would return as '/hoop/test1/'. If the
   * directory doesn't exist then '/hoop/test1' is returned, or null if
   * 'null_if_not_exists' is true.
   * 
   * @param fs
   * @param fname the file name. Assumes
   *    fname.getRepositoryId() == fs.getRepositoryId()
   * @return 
   */
  private static FileName resolveFileDirectoryAmbiguity(
                                          FileRepository fs, FileName fname) {

    if (!fname.isDirectory()) {
      // Does the directory version exist?
      FileName dir_fname = fname.asDirectory();
      if (fs.getFileInfo(dir_fname.getPathFile()) != null) {
        // It exists as a directory so return it,
        return dir_fname;
      }
    }

    // Otherwise, return it as originally specced,
    return fname;

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
  
  
  
  /**
   * Returns true if the given access of the file is permitted.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object access(Object thiz, Object... args) {

    FileRepository fs = toFileRepository(args[0]);
    String path = args[1].toString();
    int mode = GJSRuntime.toInt32(args[2]);
    boolean is_deferred = hasReqArg(args, 3);

    Boolean result;

    try {

      // Fast fail; can't write on read-only file system,
      if (fs == null && (mode & W_OK) != 0) {
        result = Boolean.FALSE;
      }
      else {
        GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
        FileName p = fileToAbsolute(pstate, path, false);
        // If read-only file system then create repository here,
        if (fs == null) {
          PlatformContext pctx = PlatformContextFactory.getPlatformContext();
          fs = pctx.getFileRepositoryFor(p);
        }
        // Repository not available or path is outside scope of current fs
        if (fs == null || !p.getRepositoryId().equals(fs.getRepositoryId())) {
          result = Boolean.FALSE;
        }
        else {
          String fname = p.getPathFile();
          FileInfo finfo = fs.getFileInfo(fname);
          // File not found or the object is not a file,
          if (finfo == null || !finfo.isFile()) {
            result = Boolean.FALSE;
          }
          else {
            // File is accessible,
            result = Boolean.TRUE;
          }
        }
      }

      if (is_deferred) {
        return postToImmediateQueue("access", thiz, args[3], null, result);
      }
      return result;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("access", thiz, args[3], ex);
      }
      throw ex;
    }

  }

  /**
   * Posix-like stat command.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object stat(Object thiz, Object... args) {

    FileRepository fs = toFileRepository(args[0]);
    Object arg = args[1];
    boolean is_deferred = hasReqArg(args, 2);

    FileInfo finfo;

    long file_size;

    try {
    
      // If it's a number then it's a FileDescriptor,
      if (arg instanceof Number) {
        FileDescriptor fd = internal.getFDMap().getFD(GJSRuntime.toInt32(arg));
        finfo = fd.getFileInfo();
        file_size = fd.getDataFile().size();
      }
      else {
        GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();

        FileName p = fileToAbsolute(pstate, arg.toString(), false);
        if (fs == null) {
          PlatformContext pctx = PlatformContextFactory.getPlatformContext();
          fs = pctx.getFileRepositoryFor(p);
        }
        if (fs == null) {
          // Repository not available,
          if (is_deferred) {
            postToImmediateQueue("stat", thiz, args[2], null, Boolean.FALSE);
          }
          return Boolean.FALSE;
        }
        if (!p.getRepositoryId().equals(fs.getRepositoryId())) {
          throw new GJavaScriptException(
                      "Repository of file does not match transaction: " +
                      p.getRepositoryId());
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
            if (is_deferred) {
              postToImmediateQueue("stat", thiz, args[2], null, Boolean.FALSE);
            }
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
      if (is_deferred) {
        return postToImmediateQueue("stat", thiz, args[2], null, stat_result);
      }
      return stat_result;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("stat", thiz, args[2], ex);
      }
      throw ex;
    }

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

    FileRepository fs = toFileRepository(args[0]);
    String path = args[1].toString();
    int flags = GJSRuntime.toInt32(args[2]);
    Object mode = args[3];
    boolean is_deferred = hasReqArg(args, 4);

    int fd_number;

    try {

      boolean is_creating = (flags & O_CREAT) != 0;
      boolean is_truncating = (flags & O_TRUNC) != 0;
      boolean is_appending = (flags & O_APPEND) != 0;
      boolean is_newfileonly = (flags & O_EXCL) != 0;
      boolean is_write = (flags & O_WRONLY) != 0 || (flags & O_RDWR) != 0;

      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();

      FileName fname = fileToAbsolute(pstate, path, false);
      if (fs == null) {
        PlatformContext pctx = PlatformContextFactory.getPlatformContext();
        fs = pctx.getFileRepositoryFor(fname);
        if (fs == null) {
          throw new GJavaScriptException("File not found: " + fname.toString());
        }
        // Don't allow writing when fs is null (the read-only file system),
        if (is_write || is_creating || is_truncating || is_appending || is_newfileonly) {
          throw new GJavaScriptException("This file system is read-only");
        }
      }

      if (!fname.getRepositoryId().equals(fs.getRepositoryId())) {
        throw new GJavaScriptException(
                    "Repository of file does not match transaction: " +
                    fname.getRepositoryId());
      }

      String fname_str = fname.getPathFile();
      FileInfo finfo = fs.getFileInfo(fname_str);
      FileDescriptor fd;
      if (is_write) {
        if (finfo == null) {
          // If the file isn't found and we are writing then create it now,
          if (is_creating) {
            fs.createFile(fname_str, FileUtilities.findMimeType(fname_str),
                          System.currentTimeMillis());
            finfo = fs.getFileInfo(fname_str);
            if (finfo == null) {
              // Would this ever happen?
              throw new GJavaScriptException(
                                  "Unable to create file: " + fname.toString());
            }
          }
          else {
            throw new GJavaScriptException("File not found: " + fname.toString());
          }
          fd = new FileDescriptor(fs, finfo, is_write);
        }
        else {
          // If it already exists,
          // Check it's a file,
          if (!finfo.isFile()) {
            throw new GJavaScriptException("Not a file: " + fname.toString());
          }

          if (is_newfileonly) {
            throw new GJavaScriptException("Already exists: " + fname.toString());
          }
          // We are either truncating or appending,
          fd = new FileDescriptor(fs, finfo, is_write);
          if (is_truncating) {
            DataFile dfile = fd.getDataFile();
            dfile.position(0);
            dfile.setSize(0);
          }
          else if (is_appending) {
            DataFile dfile = fd.getDataFile();
            dfile.position(dfile.size());
          }
        }
      }
      else {
        fd = new FileDescriptor(fs, finfo, is_write);
      }

      // Assign it a file descriptor number by adding to the map,
      fd_number = internal.getFDMap().mapFD(fd);

      if (is_deferred) {
        return postToImmediateQueue("open", thiz, args[4], null, fd_number);
      }
      // Return the descriptor,
      return fd_number;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("open", thiz, args[4], ex);
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
//    FileRepository fs = toFileRepository(args[0]);
    // Remove from the map,
    int fd_number = GJSRuntime.toInt32(args[1]);
    internal.getFDMap().removeFD(fd_number);

    if (hasReqArg(args, 2)) {
      return postToImmediateQueue("close", thiz, args[2], (Object) null);
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

//    FileRepository fs = toFileRepository(args[0]);
    // Look up the file descriptor in the map,
    FileDescriptor fd = internal.getFDMap().getFD(GJSRuntime.toInt32(args[1]));
    GJSObject buffer = (GJSObject) args[2];
    int offset = GJSRuntime.toInt32(args[3]);
    int length = GJSRuntime.toInt32(args[4]);
    long position = -1;
    if (!GJSStatics.UNDEFINED.equals(args[5])) {
      position = GJSRuntime.toInt64(args[5]);
    }

    ByteBuffer byte_buf = GJSRuntime.system().getExternalArrayDataOf(buffer);
    DataFile dfile = fd.getDataFile();
    if (position >= 0) {
      dfile.position(position);
    }

    // Clip the read against the end of the file,
    int to_read = (int) Math.min(dfile.size() - dfile.position(), length);

    dfile.get(byte_buf.array(), offset, to_read);

    if (hasReqArg(args, 6)) {
      return postToImmediateQueue("read", thiz, args[6], null, to_read);
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

    FileRepository fs = toFileRepository(args[0]);
    String path = args[1].toString();
    boolean is_deferred = hasReqArg(args, 2);

    try {
      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      FileName fname = fileToAbsolute(pstate, path, true);
      if (fs == null) {
        PlatformContext pctx = PlatformContextFactory.getPlatformContext();
        fs = pctx.getFileRepositoryFor(fname);
        if (fs == null) {
          throw new GJavaScriptException("Path not found: " + fname.toString());
        }
      }

      if (!fname.getRepositoryId().equals(fs.getRepositoryId())) {
        throw new GJavaScriptException(
                    "Repository of file does not match transaction: " +
                    fname.getRepositoryId());
      }
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
        return postToImmediateQueue("readdir", thiz, args[2], null, dir_list_arr);
      }
      return dir_list_arr;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("readdir", thiz, args[2], ex);
      }
      throw ex;
    }

  }

  
  
  // -----
  
  
  private static final Map<String, String> node_2_java_char_encoding = new HashMap<>();
  static {
    node_2_java_char_encoding.put("utf8", "UTF-8");
    node_2_java_char_encoding.put("utf-8", "UTF-8");
    node_2_java_char_encoding.put("ascii", "US-ASCII");
    node_2_java_char_encoding.put("ucs2", "UTF-16LE");
    node_2_java_char_encoding.put("ucs-2", "UTF-16LE");
    node_2_java_char_encoding.put("utf16le", "UTF-16LE");
    node_2_java_char_encoding.put("utf-16le", "UTF-16LE");
  }
  
  
  /**
   * 
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object rename(Object thiz, Object... args) {
    
    FileRepository fs = toFileRepository(args[0]);
    String filen = args[1].toString();
    String newfilen = args[2].toString();
    boolean is_deferred = hasReqArg(args, 3);
    
    try {
      if (fs == null) {
        throw new GJavaScriptException("File system is read only");
      }
      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      FileName src_fname = fileToAbsolute(pstate, filen, false);
      FileName dest_fname = fileToAbsolute(pstate, newfilen, false);
      // Resolve file/directory ambiguity
      src_fname = resolveFileDirectoryAmbiguity(fs, src_fname);

      String repo_id = fs.getRepositoryId();
      // Can't rename a file across a repository,
      if (!src_fname.getRepositoryId().equals(repo_id) ||
          !dest_fname.getRepositoryId().equals(repo_id)) {
        throw new GJavaScriptException(
                  "Rename source and destination not in the same repository");
      }

      // Fail if source doesn't exist,
      String src_pathfile = src_fname.getPathFile();
      FileInfo src_finfo = fs.getFileInfo(src_pathfile);
      if (src_finfo == null) {
        throw new GJavaScriptException("Not found: " + src_fname);
      }
      
      // Renaming to a different name,
      if (!src_fname.equals(dest_fname)) {
        // If source is a directory then make sure destination will be,
        if (src_finfo.isDirectory()) {
          dest_fname = dest_fname.asDirectory();
        }
        // Fail if destination already exists,
        String dest_pathfile = dest_fname.getPathFile();
        FileInfo dest_finfo = fs.getFileInfo(dest_pathfile);
        if (dest_finfo != null) {
          throw new GJavaScriptException("Already exists: " + dest_fname);
        }

        // If renaming a file,
        if (src_finfo.isFile()) {
          copyFile(fs, src_finfo, dest_pathfile);
        }
        // If renaming a directory
        else if (src_finfo.isDirectory()) {
          // Make the destination directory,
          fs.makeDirectory(dest_pathfile);
          // Make a list of files being copied,
          List<FileInfo> src_files = recurseCatalog(fs, src_pathfile);
          // Copy files individually,
          int root_delim = src_pathfile.length();
          List<FileInfo> dirs_to_remove = new ArrayList<>();
          // Copy files,
          for (FileInfo sf : src_files) {
            String sfan = sf.getAbsoluteName();
            String df = dest_pathfile + sfan.substring(root_delim);
            if (sf.isFile()) {
              copyFile(fs, sf, df);
            }
            else if (sf.isDirectory()) {
              dirs_to_remove.add(sf);
              fs.makeDirectory(df);
            }
          }
          // Remove empty directories,
          for (FileInfo d : dirs_to_remove) {
            fs.removeDirectory(d.getAbsoluteName());
          }
          fs.removeDirectory(src_finfo.getAbsoluteName());
        }
        else {
          throw new IllegalStateException("Unknown FileInfo type");
        }
      }

      // Return undefined as per the node documentation,
      if (is_deferred) {
        return postToImmediateQueue("rename", thiz, args[3], (Object) null);
      }
      return GJSStatics.UNDEFINED;

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("rename", thiz, args[3], ex);
      }
      throw ex;
    }

  }

  

  /**
   * Writes string content to a file descriptor.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object writeString(Object thiz, Object... args) {
    
    boolean is_deferred = (args[5] instanceof GJSObject);
    
    try {
      FileDescriptor fd = internal.getFDMap().getFD(GJSRuntime.toInt32(args[1]));
      String data = args[2].toString();
      long position = -1;
      if (args[3] instanceof Number) {
        position = GJSRuntime.toInt64(args[3]);
      }
      String encoding = args[4].toString();

      if (!fd.canWrite()) {
        throw new GJavaScriptException("Access error");
      }
      else {

        DataFile dfile = fd.getDataFile();
        if (position >= 0) {
          dfile.position(position);
        }

        long written = 0;

        try {
          String charset = node_2_java_char_encoding.get(encoding);
          if (charset == null) {
            throw new GJavaScriptException("Unknown encoding: " + encoding);
          }

          // Record the start and end position so we know the number of bytes
          // encoded.
          long position_start = dfile.position();
          OutputStream outs = DataFileUtils.asOutputStream(dfile);
          try (OutputStreamWriter w = new OutputStreamWriter(outs, charset)) {
            w.append(data);
          }
          written = dfile.position() - position_start;
          
          // Touch the file,
          if (written > 0) {
            FileInfo finfo = fd.getFileInfo();
            finfo.setLastModified(System.currentTimeMillis());
          }
          
        }
        catch (IOException ex) {
          throw new GJavaScriptException(ex);
        }

        if (is_deferred) {
          return postToImmediateQueue("writeString", thiz, args[5], null, written);
        }
        return written;

      }

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("writeString", thiz, args[5], ex);
      }
      throw ex;
    }
    
    
  }
  
  /**
   * Writes buffer content to a file descriptor.
   * 
   * @param thiz
   * @param args
   * @return 
   */
  public Object writeBuffer(Object thiz, Object... args) {
    
    boolean is_deferred = hasReqArg(args, 6);

    try {
//      FileRepository fs = toFileRepository(args[0]);
      FileDescriptor fd = internal.getFDMap().getFD(GJSRuntime.toInt32(args[1]));
      GJSObject buffer = (GJSObject) args[2];
      int offset = GJSRuntime.toInt32(args[3]);
      int length = GJSRuntime.toInt32(args[4]);
      long position = -1;
      if (!GJSStatics.UNDEFINED.equals(args[5])) {
        position = GJSRuntime.toInt64(args[5]);
      }

      if (!fd.canWrite()) {
        throw new GJavaScriptException("Access error");
      }
      else {

        ByteBuffer byte_buf = GJSRuntime.system().getExternalArrayDataOf(buffer);
        DataFile dfile = fd.getDataFile();
        if (position >= 0) {
          dfile.position(position);
        }

        dfile.put(byte_buf.array(), offset, length);

        // Touch the file,
        if (length > 0) {
          FileInfo finfo = fd.getFileInfo();
          finfo.setLastModified(System.currentTimeMillis());
        }

        if (is_deferred) {
          return postToImmediateQueue("writeBuffer", thiz, args[6], null, length);
        }
        return length;

      }

    }
    catch (GJavaScriptException ex) {
      if (is_deferred) {
        return postToImmediateQueue("writeBuffer", thiz, args[6], ex);
      }
      throw ex;
    }

  }

  private static void copyFile(FileRepository fs,
                               FileInfo src_finfo, String dest_pathfile) {
      // Create a new destination file,
      fs.createFile(dest_pathfile, src_finfo.getMimeType(),
                    src_finfo.getLastModified());
      DataFile dest_dfile = fs.getDataFile(dest_pathfile);
      DataFile src_dfile = src_finfo.getDataFile();
      src_dfile.position(0);
      dest_dfile.position(0);
      // Copy all data,
      dest_dfile.copyFrom(src_dfile, src_dfile.size());
      // Delete the original file,
      fs.deleteFile(src_finfo.getAbsoluteName());
  }

  private List<FileInfo> recurseCatalog(FileRepository fs, String directory) {
    List<FileInfo> out = new ArrayList<>(128);
    recurseCatalog(out, fs, directory);
    return out;
  }

  private List<FileInfo> recurseCatalog(
                  List<FileInfo> out, FileRepository fs, String directory) {
    List<FileInfo> dir_list = fs.getDirectoryFileInfoList(directory);
    for (FileInfo finfo : dir_list) {
      if (finfo.isDirectory()) {
        recurseCatalog(out, fs, finfo.getAbsoluteName());
      }
      out.add(finfo);
    }
    return out;
  }

}
