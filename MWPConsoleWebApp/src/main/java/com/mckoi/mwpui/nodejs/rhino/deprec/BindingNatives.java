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

import com.mckoi.mwpui.NodeJSWrapSCommand;
import com.mckoi.mwpui.apihelper.StreamUtils;
import java.io.IOException;
import java.io.InputStream;
import org.mozilla.javascript.*;

import static com.mckoi.mwpui.NodeJSWrapSCommand.NODE_SHARED_PROCESS_STATE;

/**
 * An implementation of the node.js process.bindings['natives'] object.
 *
 * @author Tobias Downer
 */
public class BindingNatives extends RhinoAbstractMap {
  
  private boolean config_deleted = false;

  private boolean isConfigAndAvailable(String id) {
    return !config_deleted && id.equals("config");
  }

  @Override
  public String getClassName() {
    return "natives";
  }

  @Override
  protected Object getKey(String id) {
    return UniqueTag.NOT_FOUND;
  }

  @Override
  protected boolean hasKey(String id) {
    return false;
  }

  @Override
  protected void deleteKey(String id) {
    if (isConfigAndAvailable(id)) {
      config_deleted = true;
    }
    else {
      super.deleteKey(id);
    }
  }

  @Override
  public Object get(String id, Scriptable start) {
    Object ob = super.get(id, start);
    if (UniqueTag.NOT_FOUND.equals(ob)) {
      // Lookup the module_id,
      String module_id = id;

      // The following module names aren't allowed because they clash with
      // how Rhino accesses certain internal properties.
      // I'm not sure why nodejs decided to use a property lookup mechanism
      // to resolve module names. What's wrong with a getter function?
      if (allowedModuleId(module_id)) {
        if (isConfigAndAvailable(id)) {
          try {
            InputStream ins = NodeJSWrapSCommand.class.getResourceAsStream(
                                                        "nodeconfig.gypi");
            return StreamUtils.stringValueOfUTF8Stream(ins);
          }
          catch (IOException ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
          }
        }

        JSProcessSharedState pstate =
                (JSProcessSharedState) Context.getCurrentContext().getThreadLocal(
                                                    NODE_SHARED_PROCESS_STATE);
        NodeSourceLoader node_source_loader = pstate.getNodeSourceLoader();
        if (node_source_loader.isModuleNative(module_id)) {
          // Fetches the source code,
          try {
            String result = node_source_loader.getModuleSource(module_id);
            if (result != null) {
              return result;
            }
          }
          catch (IOException ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
          }
        }
      }

    }
    return UniqueTag.NOT_FOUND;
  }

  @Override
  public boolean has(String id, Scriptable start) {
    if (!super.has(id, start)) {
      // Lookup the module_id,
      String module_id = id;
      if (allowedModuleId(module_id)) {
        // Special case for config string,
        if (isConfigAndAvailable(id)) {
          return true;
        }
        JSProcessSharedState pstate =
                (JSProcessSharedState) Context.getCurrentContext().getThreadLocal(
                                                    NODE_SHARED_PROCESS_STATE);
        NodeSourceLoader node_source_loader = pstate.getNodeSourceLoader();
        return node_source_loader.isModuleNative(module_id);
      }
      else {
        return false;
      }
    }
    return true;
  }

  /**
   * Module id names that aren't permitted.
   * 
   * @param module_id
   * @return 
   */
  public static boolean allowedModuleId(String module_id) {
    return !module_id.startsWith("__") &&
           !module_id.equals("toString") &&
           !module_id.equals("valueOf") &&
           !module_id.equals("hasOwnProperty");
  }

}
