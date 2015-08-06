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

import org.mozilla.javascript.*;

/**
 * A simple callable function implementation.
 *
 * @author Tobias Downer
 */
public class RhinoFunction extends BaseFunction {
  
  private String function_name = "";
  
  public RhinoFunction init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(
                                    this, scope, TopLevel.Builtins.Function);
    return this;
  }

  @Override
  public String getFunctionName() {
    return function_name;
  }

  public void setFunctionName(String name) {
    function_name = name;
  }

  

}
