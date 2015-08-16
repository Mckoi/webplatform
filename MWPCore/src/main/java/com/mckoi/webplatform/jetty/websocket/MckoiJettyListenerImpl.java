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

package com.mckoi.webplatform.jetty.websocket;

import com.mckoi.webplatform.impl.LoggerService;
import com.mckoi.webplatform.impl.PlatformContextImpl;
import com.mckoi.webplatform.util.LogUtils;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.events.EventDriver;
import org.eclipse.jetty.websocket.common.events.JettyListenerImpl;

/**
 * Wraps the jetty web socket listener to forward uncaught exceptions to our
 * internal handler.
 *
 * @author Tobias Downer
 */

public class MckoiJettyListenerImpl extends JettyListenerImpl {

  @Override
  public EventDriver create(Object websocket, WebSocketPolicy policy) {
    WebSocketListener listener = (WebSocketListener) websocket;
    return super.create(new WrappedWebSocketListener(listener), policy);
  }

  private static class WrappedWebSocketListener implements WebSocketListener {
    private final WebSocketListener backed;

    public WrappedWebSocketListener(WebSocketListener backed) {
      this.backed = backed;
    }

    /**
     * Log the unhandled exception to 'webapp' log.
     * 
     * @param e 
     */
    private void logUnhandled(final Throwable e) {
      final String stack_trace = LogUtils.stringStackTrace(e);

      AccessController.doPrivileged(new PrivilegedAction<Object>() {
        @Override
        public Object run() {
          LoggerService logger_service =
                                  PlatformContextImpl.getCurrentThreadLogger();
          if (logger_service != null) {
            logger_service.secureLog(Level.SEVERE, "webapp",
                    "Web Socket failed with exception.\n{0}",
                    stack_trace);
          }

          System.err.println("-- Web Socket Exception --");
          System.err.println(stack_trace);

          return null;
        }
      });

    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
      try {
        backed.onWebSocketBinary(payload, offset, len);
      }
      catch (Throwable e) {
        logUnhandled(e);
        throw e;
      }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      try {
        backed.onWebSocketClose(statusCode, reason);
      }
      catch (Throwable e) {
        logUnhandled(e);
        throw e;
      }
    }

    @Override
    public void onWebSocketConnect(Session session) {
      try {
        backed.onWebSocketConnect(session);
      }
      catch (Throwable e) {
        logUnhandled(e);
        throw e;
      }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
      try {
        backed.onWebSocketError(cause);
      }
      catch (Throwable e) {
        logUnhandled(e);
        throw e;
      }
    }

    @Override
    public void onWebSocketText(String message) {
      try {
        backed.onWebSocketText(message);
      }
      catch (Throwable e) {
        logUnhandled(e);
        throw e;
      }
    }

  }

}
