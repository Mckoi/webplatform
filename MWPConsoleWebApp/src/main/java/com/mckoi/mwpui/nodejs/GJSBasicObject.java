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
 * A basic object which will get converted into a native JavaScript object
 * (eg. var basic_object = {} ) in the script runtime.
 *
 * @author Tobias Downer
 */
public final class GJSBasicObject extends GJSAbstractObject {

  private final String class_name;
  
  public GJSBasicObject() {
    this.class_name = null;
  }
  
  public GJSBasicObject(String class_name) {
    this.class_name = class_name;
  }

  @Override
  public String getClassName() {
    return class_name;
  }

}
