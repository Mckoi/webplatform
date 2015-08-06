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
 * A runtime exception that will get turned into a JavaScript exception.
 *
 * @author Tobias Downer
 */
public class GJavaScriptException extends RuntimeException {

  private Object internal_exception = null;

  public GJavaScriptException() {
  }

  public GJavaScriptException(String message) {
    super(message);
  }

  public GJavaScriptException(String message, Throwable cause) {
    super(message, cause);
  }

  public GJavaScriptException(Throwable cause) {
    super(cause);
  }

  
  /**
   * Internal representation of this exception, or null if there's not internal
   * representation. Use 'GJSSystem.getAlternativeStackTraceStyle' to format
   * the error in a different format.
   * 
   * @return 
   */
  public Object internalGetException() {
    return internal_exception;
  }

  /**
   * Sets the internal representation of this exception.
   * 
   * @param ex 
   */
  public void internalSetException(Object ex) {
    this.internal_exception = ex;
  }

}
