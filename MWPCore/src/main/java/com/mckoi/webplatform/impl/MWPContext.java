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

import javax.servlet.ServletRequest;

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
  private final PlatformContextBuilder context_builder;
  
  /**
   * The web app name.
   */
  private final String web_app_name;

  /**
   * Constructor.
   * 
   * @param context_builder
   * @param web_app_name 
   */
  MWPContext(PlatformContextBuilder context_builder, String web_app_name) {
    this.context_builder = context_builder;
    this.web_app_name = web_app_name;
  }

  public PlatformContextBuilder getContextBuilder() {
    return context_builder;
  }

  public String getWebAppName() {
    return web_app_name;
  }

  public void enterWebContext(Object grant_object, ServletRequest request) {
    context_builder.enterWebContext(grant_object, request);
    PlatformContextImpl.setWebApplicationName(web_app_name);
  }

  public void exitWebContext(Object grant_object) {
    context_builder.exitWebContext(grant_object);
  }
  
}
