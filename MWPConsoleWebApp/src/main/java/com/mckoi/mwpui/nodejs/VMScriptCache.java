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
import com.mckoi.mwpui.ServerCommandContext;
import com.mckoi.mwpui.apihelper.StreamUtils;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tobias Downer
 */
public class VMScriptCache {

  /**
   * The cache.
   */
  
  private static final Map<String, Cache> system_cache_map = new HashMap<>();
  private static final Object LOCK = new Object();

  
  private static class Cache extends HashMap<FileName, CachedScriptInfo> {
  };
  
  /**
   * Returns a Cache for the given system type.
   *
   * @param system_id
   * @return
   */
  private static Cache getCache(String system_id) {
    synchronized (LOCK) {
      Cache cache = system_cache_map.get(system_id);
      if (cache == null) {
        cache = new Cache();
        system_cache_map.put(system_id, cache);
      }
      return cache;
    }
  }

  /**
   * Given a module file name, returns the source code as a String.
   * 
   * @param module_fname
   * @return
   * @throws IOException 
   */
  public static String getSourceCode(FileName module_fname) throws IOException {

    GJSSystem system = GJSRuntime.system();

    GJSProcessSharedState pstate = system.getSharedState();
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
      Cache cache = getCache(system.getSystemId());
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
//      System.out.println("FETCHED: " + module_fname);
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

    GJSSystem system = GJSRuntime.system();

    // Check cache for fast track,
    synchronized (LOCK) {
      Cache cache = getCache(system.getSystemId());
      if (cache.containsKey(module_fname)) {
        return true;
      }
    }

    GJSProcessSharedState pstate = system.getSharedState();
    ServerCommandContext sctx = pstate.getServerCommandContext();
    FileSystem fs =
                sctx.getPlatformContext().getFileRepositoryFor(module_fname);

    return fs.getFileInfo(module_fname.getPathFile()) != null;
  }

  /**
   * Compiles the code into a Script to be executed. The 'module_fname' serves
   * as a unique identifier for caching purposes.
   * 
   * @param module_fname
   * @param code
   * @return 
   */
  public static Object compileModule(FileName module_fname, String code) {

    GJSSystem system = GJSRuntime.system();
    Cache cache;

    Object script = null;
    
    // Check cache for previously compiled copy,
    synchronized (LOCK) {
      cache = getCache(system.getSystemId());
      CachedScriptInfo script_info = cache.get(module_fname);
      if (script_info != null && script_info.compiled_script != null) {
        script = script_info.compiled_script;
      }
    }

//    System.out.println("COMPILED: " + module_fname);

    if (script == null) {
      // Compile the code and put it in the cache,
      script = system.compileSourceCode(code, module_fname.toString(), 1);
      synchronized (LOCK) {
        CachedScriptInfo script_info = cache.get(module_fname);
        if (script_info != null) {
          script_info.compiled_script = script;
        }
      }
    }

    return script;

  }

  /**
   * Stores information about the script.
   */
  private static class CachedScriptInfo {
    
    private String source_code;
    private Object compiled_script;
    private long timestamp;

  }

}
