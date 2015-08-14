/**
 * com.mckoi.webplatform.impl.JettyMckoiRequestHandler  May 11, 2010
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

package com.mckoi.webplatform.impl;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * 
 *
 * @author Tobias Downer
 */

class JettyMckoiRequestHandler extends AbstractHandler {

  /**
   * Object needed to switch contexts in PlatformContextBuilder.
   */
  private static Object CONTEXT_GRANT = null;
  static void givePlatformContextGrant(Object grant_object) {
    if (CONTEXT_GRANT == null) CONTEXT_GRANT = grant_object;
  }

  private final PlatformContextBuilder context_builder;

  /**
   * Constructor.
   */
  JettyMckoiRequestHandler(PlatformContextBuilder context_builder) {
    this.context_builder = context_builder;
  }

  /**
   * Handle the page request.
   */
  @Override
  public void handle(String target, Request jetty_request,
                     HttpServletRequest request, HttpServletResponse response)
                                        throws IOException, ServletException {

    // Enter the context for this request,
    JettyMckoiWebAppContextSet context =
                      context_builder.enterWebContext(CONTEXT_GRANT, request);
    if (context == null) {
      return;
    }
    
    try {

      // If it hasn't been started,
      if (!context.isStarted()) {
        try {
          // Start it,
          context.start();
        }
        catch (Exception e) {
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                             e.getMessage());
          // PENDING: Put this error in a log,
          e.printStackTrace(System.err);
          return;
        }
      }

      // If the context is still starting, respond with a service unavailable
      if (context.isStarting()) {
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           "Service unavailable while context is starting");
        return;
      }

      // Context is started, so delegate the request to the web app,
      context.handle(target, jetty_request, request, response);

      // If the request isn't handled we put out a 404
      if (!jetty_request.isHandled() && !response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }

    }
    finally {
      // Make sure we remove the current thread context,
      context_builder.exitWebContext(CONTEXT_GRANT);
    }

  }

}
