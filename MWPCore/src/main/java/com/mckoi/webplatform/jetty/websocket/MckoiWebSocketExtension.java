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

import org.eclipse.jetty.websocket.api.extensions.ExtensionFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 *
 * @author Tobias Downer
 */

public class MckoiWebSocketExtension {

  public static void enableDraftCompressionExtensions(
                                            WebSocketServletFactory factory) {

    // NOTE: The extensions below are experimental as of Jetty 9.2.13. Because
    //   the compression spec is still under draft Jetty has disabled them by
    //   default. We enable them because, for us, WebSockets is a rather
    //   experimental feature anyway and we can always fall back to long-poll
    //   AJAX if WebSockets do not work.
    //
    //   At some point, presumably these extensions will be enabled by default
    //   when the spec is stable.
    ExtensionFactory extension_factory = factory.getExtensionFactory();
    extension_factory.register("deflate-frame",
          org.eclipse.jetty.websocket.common.extensions.compress.DeflateFrameExtension.class);
    extension_factory.register("permessage-deflate",
          org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension.class);
    extension_factory.register("x-webkit-deflate-frame",
          org.eclipse.jetty.websocket.common.extensions.compress.XWebkitDeflateFrameExtension.class);

  }

}
