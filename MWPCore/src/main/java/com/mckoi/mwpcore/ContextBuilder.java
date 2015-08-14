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

package com.mckoi.mwpcore;

/**
 * A ContextBuilder allows for a context to be entered and exited. Used when
 * a thread calls into user-code and a thread context needs to be defined.
 *
 * @author Tobias Downer
 */

public interface ContextBuilder {

  /**
   * The ContextBuilder for no context.
   */
  public final static ContextBuilder NO_CONTEXT = new ContextBuilder() {
    @Override
    public void enterContext() {
    }
    @Override
    public void exitContext() {
    }
  };

  /**
   * Enters a context. Once this is called then 'exitContext' must also be
   * called when the user code has completed.
   */
  void enterContext();

  /**
   * Exits a context.
   */
  void exitContext();

}
