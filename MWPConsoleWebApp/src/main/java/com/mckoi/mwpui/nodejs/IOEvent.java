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
 * An IOEvent that ready to be dispatched when the JavaScript context is not
 * currently executing anything.
 *
 * @author Tobias Downer
 */
public class IOEvent {

  private final Object thisObj;
  private final GJSObject oncomplete;
  private final Object[] complete_args;

  public IOEvent(Object thisObj, GJSObject oncomplete, Object[] complete_args) {
    this.thisObj = thisObj;
    this.oncomplete = oncomplete;
    this.complete_args = complete_args;
  }

  public Object getThisObject() {
    return thisObj;
  }

  public GJSObject getOnCompleteFunction() {
    return oncomplete;
  }
  
  public Object[] getOnCompleteArgs() {
    return complete_args;
  }

}
