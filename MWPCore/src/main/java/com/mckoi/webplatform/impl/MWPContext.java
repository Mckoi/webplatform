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

package com.mckoi.webplatform.impl;

import com.mckoi.mwpcore.ContextBuilder;

/**
 * An object that describes the MWP context of a servlet operation, including
 * the name of the web app and any other relevant information needed to
 * recreate the context state.
 *
 * @author Tobias Downer
 */

public final class MWPContext {

  /**
   * The ServletContext attribute key.
   */
  public final static String ATTRIBUTE_KEY =
                                    "com.mckoi.webplatform.impl.MWPContext";

  /**
   * The context builder.
   */
  private final ContextBuilder context_builder;
  
  /**
   * Constructor.
   * 
   * @param context_builder
   * @param web_app_name 
   */
  MWPContext(ContextBuilder context_builder) {
    this.context_builder = context_builder;
  }

  public ContextBuilder getContextBuilder(Object grant_object) {
    if (grant_object != PlatformContextBuilder.CONTEXT_GRANT) {
      throw new IllegalStateException("Invalid grant object");
    }
    return context_builder;
  }

}
