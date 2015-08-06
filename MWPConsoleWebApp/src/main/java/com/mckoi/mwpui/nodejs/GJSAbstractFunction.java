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

/**
 *
 * @author Tobias Downer
 */
public abstract class GJSAbstractFunction extends GJSAbstractObject {

  private final String function_name;

  public GJSAbstractFunction(String function_name) {
    this.function_name = function_name;
  }

  public String getFunctionName() {
    return function_name;
  }

  @Override
  public boolean isFunction() {
    return true;
  }

  
  
  
}
