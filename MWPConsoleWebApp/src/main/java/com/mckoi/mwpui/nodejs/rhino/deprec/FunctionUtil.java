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
 *
 * @author Tobias Downer
 */
public class FunctionUtil {

  /**
   * A function that returns a value.
   */
  public static class ObjReturnFunction extends RhinoFunction {

    private final Object to_return;

    public ObjReturnFunction(Object to_return) {
      this.to_return = to_return;
    }

    @Override
    public ObjReturnFunction init(Scriptable scope) {
      super.init(scope);
      setFunctionName("");
      return this;
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {
      return to_return;
    }

  }

}
