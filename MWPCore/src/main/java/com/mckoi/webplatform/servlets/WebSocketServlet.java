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

package com.mckoi.webplatform.servlets;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * A Servlet that upgrades its connection end point to a WebSocket connection
 * allowing for bidirectional communication.
 *
 * @author Tobias Downer
 */

public abstract class WebSocketServlet extends HttpServlet
                                        implements WebSocketFactory.Acceptor {

  private final Logger LOG = Log.getLogger(getClass());

  private WebSocketFactory _webSocketFactory;

  /* ------------------------------------------------------------ */
  /**
   * @throws javax.servlet.ServletException
   * @see javax.servlet.GenericServlet#init()
   */
  @Override
  public void init() throws ServletException {

    try {

      String bs = getInitParameter("bufferSize");
      _webSocketFactory = new WebSocketFactory(
                              this, bs == null ? 8192 : Integer.parseInt(bs));
      _webSocketFactory.start();

      String max = getInitParameter("maxIdleTime");
      if (max != null)
          _webSocketFactory.setMaxIdleTime(Integer.parseInt(max));

      max = getInitParameter("maxTextMessageSize");
      if (max != null)
          _webSocketFactory.setMaxTextMessageSize(Integer.parseInt(max));

      max = getInitParameter("maxBinaryMessageSize");
      if (max != null)
          _webSocketFactory.setMaxBinaryMessageSize(Integer.parseInt(max));

      String min = getInitParameter("minVersion");
      if (min != null)
          _webSocketFactory.setMinVersion(Integer.parseInt(min));

    }
    catch (ServletException x) {
      throw x;
    }
    catch (Exception x) {
      throw new ServletException(x);
    }

  }

  /* ------------------------------------------------------------ */
  /**
   * @param request
   * @param response
   * @throws javax.servlet.ServletException
   * @throws java.io.IOException
   * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
   */
  @Override
  protected void service(
          HttpServletRequest request, HttpServletResponse response)
                                        throws ServletException, IOException {

    if (_webSocketFactory.acceptWebSocket(request, response) ||
        response.isCommitted())
      return;

    super.service(request, response);
  }

  /* ------------------------------------------------------------ */
  @Override
  public boolean checkOrigin(HttpServletRequest request, String origin) {
    return true;
  }

  /* ------------------------------------------------------------ */
  @Override
  public void destroy() {
    try {
      _webSocketFactory.stop();
    }
    catch (Exception x) {
      LOG.ignore(x);
    }
  }
  
}
