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

import com.mckoi.mwpui.NodeJSWrapSCommand;
import com.mckoi.mwpui.apihelper.StreamUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * Accesses native library source files.
 *
 * @author Tobias Downer
 */
public class NodeNatives extends GJSAbstractObject {

  private boolean config_deleted = false;

  public NodeNatives() {
  }

  private boolean isConfigAndAvailable(String id) {
    return !config_deleted && id.equals("config");
  }

  @Override
  public String getClassName() {
    return "natives";
  }

  @Override
  public Set<String> keySet() {
    return Collections.EMPTY_SET;
  }

  @Override
  public void setMember(String name, Object value) {
    // Ignore,
  }

  @Override
  public Object getMember(String name) {
    // Lookup the module_id,
    String module_id = name;

    // The following module names aren't allowed because they clash with
    // how Rhino accesses certain internal properties.
    // I'm not sure why nodejs decided to use a property lookup mechanism
    // to resolve module names. What's wrong with a getter function?
    if (allowedModuleId(module_id)) {
      if (isConfigAndAvailable(module_id)) {
        try {
          InputStream ins = NodeJSWrapSCommand.class.getResourceAsStream(
                                                      "nodeconfig.gypi");
          return StreamUtils.stringValueOfUTF8Stream(ins);
        }
        catch (IOException ex) {
          throw new GJavaScriptException(ex);
        }
      }

      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      GJSNodeSourceLoader node_source_loader = pstate.getNodeSourceLoader();
      if (node_source_loader.isModuleNative(module_id)) {
        // Fetches the source code,
        try {
          String result = node_source_loader.getModuleSource(module_id);
          if (result != null) {
            return result;
          }
        }
        catch (IOException ex) {
          throw new GJavaScriptException(ex);
        }
      }
    }

    return GJSStatics.UNDEFINED;

  }

  @Override
  public boolean hasMember(String name) {
    // Lookup the module_id,
    String module_id = name;
    if (allowedModuleId(module_id)) {
      // Special case for config string,
      if (isConfigAndAvailable(module_id)) {
        return true;
      }
      GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
      GJSNodeSourceLoader node_source_loader = pstate.getNodeSourceLoader();
      return node_source_loader.isModuleNative(module_id);
    }
    else {
      return false;
    }
  }

  @Override
  public void removeMember(String name) {
    String module_id = name;
    if (isConfigAndAvailable(module_id)) {
      config_deleted = true;
    }
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
