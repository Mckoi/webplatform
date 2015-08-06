/**
 * com.mckoi.webplatform.buildtools.BuildError  Apr 15, 2011
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

package com.mckoi.webplatform;

/**
 * Encapsulates the information in a build or compilation error.
 *
 * @author Tobias Downer
 */

public final class BuildError {

  /**
   * Build error types.
   */
  public static final String ERROR = "error";
  public static final String WARNING = "warning";
  public static final String INFO = "info";

  /**
   * The error string in human understandable form.
   */
  private String error_string;

  /**
   * The Throwable that generated this error, if there is one.
   */
  private Throwable error_th;

  /**
   * The error type (either 'error', 'warning', 'info').
   */
  private String error_type;

  /**
   * The name of the file that produced the error, or null if unknown.
   */
  private String file_name;

  /**
   * The line number the error occurred, or -1 if unknown.
   */
  private long line_number;

  /**
   * The column number the error occurred, or -1 if unknown.
   */
  private long column_number;

  /**
   * Constructors the error object.
   */
  public BuildError(String error_string, Throwable e, String error_type,
                    String file_name,
                    long line_number, long column_number) {
    this.error_string = error_string;
    this.error_th = e;
    this.error_type = error_type;
    this.file_name = file_name;
    this.line_number = line_number;
    this.column_number = column_number;
  }

  /**
   * The build error message string.
   */
  public String getErrorString() {
    return error_string;
  }

  /**
   * The error throwable if there is one, or null if the error wasn't generated
   * because of an exception.
   */
  public Throwable getErrorException() {
    return error_th;
  }

  /**
   * The type of the error ('error', 'warning', 'info').
   */
  public String getType() {
    return error_type;
  }

  /**
   * The name of the file where the error occurred.
   */
  public String getFile() {
    return file_name;
  }

  /**
   * The line of the error.
   */
  public long getLineNumber() {
    return line_number;
  }

  /**
   * The column of the error.
   */
  public long getColumnNumber() {
    return column_number;
  }

}
