/**
 * com.mckoi.process.ProcessFunctionError  Dec 8, 2012
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2012  Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this program.  If not, see ( http://www.gnu.org/licenses/ ) or
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 *
 * Change Log:
 *
 *
 */

package com.mckoi.process;

/**
 * An object that describes an error.
 *
 * @author Tobias Downer
 */

public class ProcessFunctionError extends Throwable {

  /**
   * The error type.
   */
  private final String error_type;

  public ProcessFunctionError(String error_type, String msg, Throwable e) {
    super(msg, e);
    this.error_type = error_type;
  }

  public ProcessFunctionError(String error_type, String msg) {
    super(msg);
    this.error_type = error_type;
  }

  public ProcessFunctionError(String error_type, Throwable e) {
    super(e);
    this.error_type = error_type;
  }

  public String getErrorType() {
    return error_type;
  }

}
