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
package com.mckoi.mwpui.nodejs.nashorn;

import jdk.nashorn.api.scripting.NashornException;

/**
 *
 * @author Tobias Downer
 */
public class WrappedNashornException extends NashornException {

  public WrappedNashornException(String msg, String fileName, int line, int column) {
    super(msg, fileName, line, column);
  }

  public WrappedNashornException(String msg, Throwable cause, String fileName, int line, int column) {
    super(msg, cause, fileName, line, column);
  }

  public WrappedNashornException(String msg, Throwable cause) {
    super(msg, cause);
  }
  
}
