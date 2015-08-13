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
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.websocket.Extension;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServletConnection;

/**
 *
 * @author Tobias Downer
 */

public class WebSocketFactory extends org.eclipse.jetty.websocket.WebSocketFactory {

  public WebSocketFactory(Acceptor acceptor) {
    super(acceptor);
  }

  public WebSocketFactory(Acceptor acceptor, int bufferSize) {
    super(acceptor, bufferSize);
  }

  public WebSocketFactory(Acceptor acceptor, int bufferSize, int minVersion) {
    super(acceptor, bufferSize, minVersion);
  }

  @Override
  public void upgrade(HttpServletRequest request, HttpServletResponse response,
                    WebSocket websocket, String protocol) throws IOException {

    // Upgrade the connection,
    super.upgrade(request, response, websocket, protocol);

    // This is a nasty hack. Before we return we take the
    // WebSocketServletConnection and wrap it. We use the wrapped connection
    // to put user-code into the MWP context.
    WebSocketServletConnection connection = (WebSocketServletConnection)
                      request.getAttribute("org.eclipse.jetty.io.Connection");
    connection = new WSSWrap(connection);
    request.setAttribute("org.eclipse.jetty.io.Connection", connection);

  }

  public static class WSSWrap implements WebSocketServletConnection {
    private final WebSocketServletConnection connection;

    private WSSWrap(WebSocketServletConnection connection) {
      this.connection = connection;
    }

    @Override
    public void handshake(HttpServletRequest request, HttpServletResponse response, String subprotocol) throws IOException {
      connection.handshake(request, response, subprotocol);
    }

    @Override
    public void fillBuffersFrom(Buffer buffer) {
      connection.fillBuffersFrom(buffer);
    }

    @Override
    public List<Extension> getExtensions() {
      return connection.getExtensions();
    }

    @Override
    public WebSocket.Connection getConnection() {
      return connection.getConnection();
    }

    @Override
    public void shutdown() {
      connection.shutdown();
    }

    @Override
    public void onInputShutdown() throws IOException {
      connection.onInputShutdown();
    }

    @Override
    public Connection handle() throws IOException {
      return connection.handle();
    }

    @Override
    public long getTimeStamp() {
      return connection.getTimeStamp();
    }

    @Override
    public boolean isIdle() {
      return connection.isIdle();
    }

    @Override
    public boolean isSuspended() {
      return connection.isSuspended();
    }

    @Override
    public void onClose() {
      connection.onClose();
    }

    @Override
    public void onIdleExpired(long idleForMs) {
      connection.onIdleExpired(idleForMs);
    }
    
  }
  
  
}
