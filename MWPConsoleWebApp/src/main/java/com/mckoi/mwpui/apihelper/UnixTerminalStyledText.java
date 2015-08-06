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

package com.mckoi.mwpui.apihelper;

/**
 * Describes a run of styled text.
 *
 * @author Tobias Downer
 */
public interface UnixTerminalStyledText {

  /**
   * @return true if the run terminates with a new line.
   */
  public boolean endsInNewline();

  /**
   * @return true if this run has text (false if this is a new line only).
   */
  public boolean hasText();

  /**
   * @return true if this run has a style, or false if it's the default style.
   */
  public boolean hasStyles();

  /**
   * @return the text content of the run.
   */
  public String getText();

  /**
   * @return comma delimited list of all styles to be applied to this run in
   *   stack order. (eg. 'uxred,bold' for Unix Red and Bold styles)
   */
  public String getStyles();

}
