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

import com.mckoi.webplatform.impl.MWPContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.CloseInfo;
import org.eclipse.jetty.websocket.common.SessionListener;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.common.WebSocketSessionFactory;
import org.eclipse.jetty.websocket.common.events.EventDriver;
import org.eclipse.jetty.websocket.common.events.EventDriverFactory;
import org.eclipse.jetty.websocket.common.events.EventDriverImpl;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * This is the base WebSocketServerFactory used in the Mckoi Web Platform. The
 * main purpose is to wrap all Web Socket frame code paths that end up in
 * user-code to be within the same account's platform context.
 * <p>
 * NOTE: We intercept frame calls in a way that I consider quite hacky and it
 *   very well might break in future releases of Jetty. We wrap all standard
 *   web socket drivers with delegates that intercept code paths we know end
 *   up in user code.
 * <p>
 *   It would have been a lot nicer if Jetty allowed us to override web socket
 *   sessions directly through a method in server factory.
 *
 * @author Tobias Downer
 */

public final class MckoiWebSocketServerFactoryOrig extends WebSocketServerFactory {

  /**
   * Object needed to switch contexts in PlatformContextBuilder.
   */
  private static Object CONTEXT_GRANT = null;
  public static void givePlatformContextGrant(Object grant_object) {
    if (CONTEXT_GRANT == null) CONTEXT_GRANT = grant_object;
  }

  @Override
  public void init() throws Exception {
    super.init();
  }
  
  @Override
  public WebSocketServletFactory createFactory(WebSocketPolicy policy) {

    WebSocketServerFactory servlet_factory =
                        (WebSocketServerFactory) super.createFactory(policy);

    servlet_factory.addSessionFactory(
                                  new WrappedSessionFactory(servlet_factory));

    // Wrap all the driver implementations so that they go into user-code when
    // handling frames.
    EventDriverFactory driver_factory = servlet_factory.getEventDriverFactory();
    List<EventDriverImpl> implementations =
                                          driver_factory.getImplementations();
    List<EventDriverImpl> impl_copy = new ArrayList<>(implementations);
    driver_factory.clearImplementations();
    for (EventDriverImpl impl : impl_copy) {
      driver_factory.addImplementation(new WrappedDriverImp(impl));
    }

    return servlet_factory;
  }

  /**
   * This is a delegate for EventDriverImpl that wraps Jetty drivers.
   */
  private static class WrappedDriverImp implements EventDriverImpl {
    private final EventDriverImpl impl;

    public WrappedDriverImp(EventDriverImpl impl) {
      this.impl = impl;
    }

    @Override
    public EventDriver create(Object websocket, WebSocketPolicy policy)
                                                            throws Throwable {
      return new WrappedEventDriver(impl.create(websocket, policy));
    }

    @Override
    public String describeRule() {
      return impl.describeRule();
    }

    @Override
    public boolean supports(Object websocket) {
      return impl.supports(websocket);
    }
    
  }
  
  /**
   * Wrapped EventDriver that puts us into user code context when processing
   * events.
   */
  private static class WrappedEventDriver implements EventDriver {

    private final EventDriver backed;

    private WrappedEventDriver(EventDriver backed) {
      this.backed = backed;
    }

    @Override
    public WebSocketPolicy getPolicy() {
      return backed.getPolicy();
    }

    @Override
    public WebSocketSession getSession() {
      return backed.getSession();
    }

    @Override
    public void onBinaryFrame(ByteBuffer buffer, boolean fin) throws IOException {
      backed.onBinaryFrame(buffer, fin);
    }

    @Override
    public void onBinaryMessage(byte[] data) {
      backed.onBinaryMessage(data);
    }

    @Override
    public void onClose(CloseInfo close) {
      backed.onClose(close);
    }

    @Override
    public void onConnect() {
      backed.onConnect();
    }

    @Override
    public void onContinuationFrame(ByteBuffer buffer, boolean fin) throws IOException {
      backed.onContinuationFrame(buffer, fin);
    }

    @Override
    public void onError(Throwable t) {
      backed.onError(t);
    }

    @Override
    public void onFrame(Frame frame) {
      backed.onFrame(frame);
    }

    @Override
    public void onInputStream(InputStream stream) throws IOException {
      backed.onInputStream(stream);
    }

    @Override
    public void onPing(ByteBuffer buffer) {
      backed.onPing(buffer);
    }

    @Override
    public void onPong(ByteBuffer buffer) {
      backed.onPong(buffer);
    }

    @Override
    public void onReader(Reader reader) throws IOException {
      backed.onReader(reader);
    }

    @Override
    public void onTextFrame(ByteBuffer buffer, boolean fin) throws IOException {
      backed.onTextFrame(buffer, fin);
    }

    @Override
    public void onTextMessage(String message) {
      backed.onTextMessage(message);
    }

    @Override
    public void openSession(WebSocketSession session) {
      backed.openSession(session);
    }

    // Context wraps,

    @Override
    public void incomingError(Throwable t) {

      ServletUpgradeRequest req =
                    (ServletUpgradeRequest) getSession().getUpgradeRequest();
      HttpServletRequest http_srequest = req.getHttpServletRequest();
      ServletContext context = http_srequest.getServletContext();
      MWPContext mwp_context =
                  (MWPContext) context.getAttribute(MWPContext.ATTRIBUTE_KEY);

      // Enter the user-code context,
      mwp_context.enterWebContext(CONTEXT_GRANT, http_srequest);
      try {

        backed.incomingError(t);

      }
      finally {
        mwp_context.exitWebContext(CONTEXT_GRANT);
      }
    }

    @Override
    public void incomingFrame(Frame frame) {

      ServletUpgradeRequest req =
                    (ServletUpgradeRequest) getSession().getUpgradeRequest();
      HttpServletRequest http_srequest = req.getHttpServletRequest();
      ServletContext context = http_srequest.getServletContext();
      MWPContext mwp_context =
                  (MWPContext) context.getAttribute(MWPContext.ATTRIBUTE_KEY);

      // Enter the user-code context,
      mwp_context.enterWebContext(CONTEXT_GRANT, http_srequest);
      try {

        backed.incomingFrame(frame);

      }
      finally {
        mwp_context.exitWebContext(CONTEXT_GRANT);
      }
    }

  }

  private static class WrappedSessionFactory extends WebSocketSessionFactory {

    public WrappedSessionFactory(SessionListener... sessionListeners) {
      super(sessionListeners);
    }

    @Override
    public boolean supports(EventDriver websocket) {
      if (websocket instanceof WrappedEventDriver) {
        // Unwrap,
        WrappedEventDriver wrapped_driver = (WrappedEventDriver) websocket;
        return super.supports(wrapped_driver.backed);
      }
      return false;
    }

  }

}
