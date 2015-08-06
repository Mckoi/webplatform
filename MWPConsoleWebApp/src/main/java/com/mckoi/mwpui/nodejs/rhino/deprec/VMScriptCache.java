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
import com.mckoi.data.DataFileUtils;
import com.mckoi.mwpui.NodeJSWrapSCommand;
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.mwpui.apihelper.StreamUtils;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;

/**
 * A thread-safe cache that separately maps a module file name to its source
 * code and compiled script formats.
 * <p>
 * Note that this object holds onto a lock for only very short intervals, which
 * means in some situations when there are a lot of threads compiling
 * or loading code, the same module may be compiled/loaded multiple times.
 *
 * @author Tobias Downer
 */
public class VMScriptCache {

  /**
   * The cache.
   */
  private static final Map<FileName, CachedScriptInfo> cache = new HashMap();
  private static final Object LOCK = new Object();

  /**
   * Given a module file name, returns the source code as a String.
   * 
   * @param module_fname
   * @return
   * @throws IOException 
   */
  public static String getSourceCode(FileName module_fname) throws IOException {

    Context ctx = Context.getCurrentContext();
    JSProcessSharedState pstate = (JSProcessSharedState) ctx.getThreadLocal(
                                NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE);
    ServerCommandContext sctx = pstate.getServerCommandContext();
    FileSystem fs =
                sctx.getPlatformContext().getFileRepositoryFor(module_fname);
    String fname = module_fname.getPathFile();
    FileInfo finfo = fs.getFileInfo(fname);
    if (finfo == null) {
      // File not found,
      throw new IOException("Not found: " + module_fname.toString());
    }

    long flast_modified = finfo.getLastModified();

    // If it's cached then return that,
    CachedScriptInfo script_info;
    String scode;

    synchronized (LOCK) {
      script_info = cache.get(module_fname);
      // Create a new CachedScriptInfo if the timestamp is different or
      // there's nothing in the cache.
      if (script_info == null || script_info.timestamp != flast_modified) {
        script_info = new CachedScriptInfo();
        cache.put(module_fname, script_info);
      }
      scode = script_info.source_code;
    }

    if (scode == null) {
      System.out.println("FETCHED: " + module_fname);
      DataFile dfile = fs.getDataFile(fname);
      scode = StreamUtils.stringValueOfUTF8Stream(
                                          DataFileUtils.asInputStream(dfile));
      synchronized (LOCK) {
        script_info.source_code = scode;
        script_info.timestamp = flast_modified;
      }
    }

    return scode;

  }

  /**
   * Returns true if a module file with the given name exists.
   * 
   * @param module_fname
   * @return 
   */
  public static boolean checkModuleExists(FileName module_fname) {

    // Check cache for fast track,
    synchronized (LOCK) {
      if (cache.containsKey(module_fname)) {
        return true;
      }
    }

    Context ctx = Context.getCurrentContext();
    JSProcessSharedState pstate = (JSProcessSharedState) ctx.getThreadLocal(
                                NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE);
    ServerCommandContext sctx = pstate.getServerCommandContext();
    FileSystem fs =
                sctx.getPlatformContext().getFileRepositoryFor(module_fname);

    return fs.getFileInfo(module_fname.getPathFile()) != null;
  }

  /**
   * Compiles the code into a Script to be executed. The 'module_fname' serves
   * as a unique identifier for caching purposes.
   * 
   * @param cx
   * @param module_fname
   * @param code
   * @return 
   */
  public static Script compileModule(
                              Context cx, FileName module_fname, String code) {

    // Check cache for previously compiled copy,
    synchronized (LOCK) {
      CachedScriptInfo script_info = cache.get(module_fname);
      if (script_info != null && script_info.compiled_script != null) {
        return script_info.compiled_script;
      }
    }

//    System.out.println("COMPILED: " + module_fname);

    Script script = cx.compileString(
                      code, module_fname.toString(), 1, VMScriptCache.class);
    
    synchronized (LOCK) {
      CachedScriptInfo script_info = cache.get(module_fname);
      if (script_info != null) {
        script_info.compiled_script = script;
      }
    }

    return script;
  }

  /**
   * Stores information about the script.
   */
  private static class CachedScriptInfo {
    
    private String source_code;
    private Script compiled_script;
    private long timestamp;

  }
  
}
