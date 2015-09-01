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

import com.mckoi.odb.util.*;
import java.io.IOException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;

/**
 * Resolves javascript source files given their module id.
 *
 * @author Tobias Downer
 */
public class NodeSourceLoader {

  /**
   * The path where node libraries are resolved against (eg. '/admin/nbin/lib/')
   */
  private final FileName library_path;

  /**
   * The base path of the executing scripts. Modules that start with './' are
   * resolved against this path.
   */
  private final FileName execution_path;

  /**
   * Constructs the loader.
   * 
   * @param library_path the path where library files are resolved against.
   * @param execution_path the path which relative module_ids are resolved
   *   against.
   */
  public NodeSourceLoader(FileName library_path, FileName execution_path) {
    this.library_path = library_path;
    this.execution_path = execution_path;
  }

  /**
   * Constructs the loader.
   * 
   * @param library_path the path where library files are resolved against.
   */
  public NodeSourceLoader(FileName library_path) {
    this.library_path = library_path;
    this.execution_path = library_path;
  }

  /**
   * Returns a String containing the source code of the given module_id. Note
   * that module ids that start with './' are resolved against the current
   * directory.
   * 
   * @param module_id
   * @return 
   * @throws java.io.IOException 
   */
  public String getModuleSource(String module_id) throws IOException {
    return VMScriptCache.getSourceCode(resolveFileName(module_id));
  }

  /**
   * Returns true if there exists a module with the given id that can be
   * loaded.
   * 
   * @param module_id
   * @return 
   */
  public boolean isModuleLoadable(String module_id) {
    return VMScriptCache.checkModuleExists(resolveFileName(module_id));
  }

  /**
   * Compiles the given 'code' and returns a Script that can execute the
   * code. The 'module_id' is used as a unique identifier that allows for
   * compiles to be cached.
   * 
   * @param cx
   * @param module_id
   * @param code
   * @return 
   */
  public Script compileModule(Context cx, String module_id, Object code) {
    return VMScriptCache.compileModule(
                            cx, resolveFileName(module_id), code.toString());
  }

  /**
   * Resolves the module_id into a FileName referencing the source code.
   * 
   * @param module_id
   * @return 
   */
  public FileName resolveFileName(String module_id) {
    String js_file = module_id + ".js";
    if (module_id.startsWith("./")) {
      return execution_path.resolve(new FileName(js_file)).normalize();
    }
    else {
      return library_path.resolve(new FileName(js_file)).normalize();
    }
  }

  /**
   * Returns true if the module is native (it's not a user space module). A
   * native library is located in the /nbin/lib/ directory of the file system.
   * 
   * @param module_id
   * @return 
   */
  public boolean isModuleNative(String module_id) {
    FileName module_fname = resolveFileName(module_id);
    // Get the path part of the fname,
    String path_file = module_fname.getPathFile();
    if (path_file.startsWith("/nbin/lib/")) {
      return VMScriptCache.checkModuleExists(module_fname);
    }
    return false;
  }


  

}
