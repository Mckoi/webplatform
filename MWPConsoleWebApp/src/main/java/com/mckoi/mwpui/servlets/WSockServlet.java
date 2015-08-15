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
package com.mckoi.mwpui.servlets;

import com.mckoi.process.AppServiceProcessClient;
import com.mckoi.process.ChannelConsumer;
import com.mckoi.process.ChannelSessionState;
import com.mckoi.process.ProcessMessage;
import com.mckoi.process.ProcessResultNotifier;
import com.mckoi.process.ProcessUnavailableException;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.jetty.websocket.MckoiWebSocketExtension;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Uses Jetty's WebSocket API to implement a bi-directional message
 * communication system between the web client and the server process managing
 * the user.
 *
 * @author Tobias Downer
 */
public class WSockServlet extends WebSocketServlet {

//  @Override
//  public WebSocket doWebSocketConnect(
//                              HttpServletRequest request, String protocol) {
//
//    PlatformContext ctx = PlatformContextFactory.getPlatformContext();
//    String ip_addr = request.getRemoteAddr();
//
////    // Make a context dispatcher for the current context,
////    ContextDispatcher context_dispatcher = ctx.createContextDispatcher();
//
//    return new WSEvents(ip_addr);
//  }

  @Override
  public void configure(WebSocketServletFactory factory) {

    // Enable compression extensions
    MckoiWebSocketExtension.enableDraftCompressionExtensions(factory);

    factory.register(WSEvents.class);
  }

  /**
   * The communication object.
   */
  public static class WSEvents extends WebSocketAdapter {

    private String current_session_state = null;

    private volatile ProcessResultNotifier.CleanupHandler cleanup_handler;
    

    private void dispatchMessages(
                  String session_state, Collection<ProcessMessage> messages)
                                                          throws IOException {
      StringWriter sout = new StringWriter();
      try (PrintWriter out = new PrintWriter(sout)) {
        out.append("<");
        ReaderServlet.formatMessages(out, session_state, messages);
        out.flush();
      }
      RemoteEndpoint remote = getRemote();
      if (remote != null) {
        remote.sendString(sout.toString());
      }
    }

    private void continueNotifierChain() {

      try {
        // Get the context,
        PlatformContext ctx = PlatformContextFactory.getPlatformContext();
        // The process client,
        AppServiceProcessClient pc = ctx.getAppServiceProcessClient();
        ChannelConsumer consumer = pc.getChannelConsumer(
                           new ChannelSessionState(current_session_state));
        // Consume all pending messages,
        while (true) {
          // The next notifier in the chain,
          ProcessResultNotifier notifier = new ProcessResultNotifier() {
            @Override
            public void init(CleanupHandler cleanup_handler) {
              WSEvents.this.cleanup_handler = cleanup_handler;
            }
            @Override
            public void notifyMessages() {
              continueNotifierChain();
            }
          };
          // Consume messages,
          Collection<ProcessMessage> messages =
                                consumer.consumeFromChannel(100, notifier);
          // Note, if messages == null then the notifier will be notified
          // when messages are available. Otherwise the notifier isn't
          // used.
          if (messages == null) {
            return;
          }
          // Update session state,
          String session_state = consumer.getSessionState().toString();
          current_session_state = session_state;
          // Dispatch messages,
          dispatchMessages(session_state, messages);
        }

      }
      catch (ProcessUnavailableException ex) {
        ex.printStackTrace(System.err);
        throw new RuntimeException(ex);
      }
      catch (IOException ex) {
        ex.printStackTrace(System.err);
        throw new RuntimeException(ex);
      }
      catch (Exception e) {
        e.printStackTrace(System.err);
        throw e;
      }

    }

    @Override
    public void onWebSocketConnect(Session sess) {
      super.onWebSocketConnect(sess);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      super.onWebSocketClose(statusCode, reason);
      if (cleanup_handler != null) {
        cleanup_handler.performCleanup();
      }
    }

    

    @Override
    public void onWebSocketText(String data) {

      // The message type character,
      final char type = data.charAt(0);
      if (type == '$') {
        
        // This is a handshake command that sets up the initial session state,
        current_session_state = data.substring(1);

        // Start the notifier chain,
        continueNotifierChain();

      }
      // Acknowledged json command,
      else {

        int ack_id;
        String json_str;
        // Decode the input
        if (type == '>') {
          ack_id = -1;
          json_str = data.substring(1);
        }
        // Unacknowleged json command,
        else if (type == '!') {
          // The ack_id,
          int delim = data.indexOf(' ');
          ack_id = Integer.parseInt(data.substring(1, delim));
          json_str = data.substring(delim + 1);
        }
        else {
          throw new RuntimeException("Decoding error");
        }

        // Decode the json,
        try {
          JSONObject params = new JSONObject(json_str);

          // Get the command and process id string
          final String process_id_str = params.optString("p", null);
          final String frame_str = params.optString("f", null);
          // Command to pass to process,
          final String command_str = params.optString("c", null);
          // Signal to pass to process,
          final String signal_str = params.optString("s", null);
          final String signal_feature_str = params.optString("sf", null);

          StringWriter sout = new StringWriter();
          try (PrintWriter pout = new PrintWriter(sout)) {

            CommandHandler cmd = new CommandHandler(
                    process_id_str, frame_str, command_str,
                    signal_str, signal_feature_str) {
              @Override
              PrintWriter getPrintWriter() throws IOException {
                return pout;
              }
            };
            // Check if the request is valid,
            if (!cmd.isValidRequest()) {
              throw new RuntimeException("Invalid request");
            }

            // Okay, run the command,
            String ip_addr = getSession().getRemoteAddress().getHostString();
            cmd.run(ip_addr);

          }

          if (ack_id >= 0) {
            // Send the reply message if it needs acknowlegment.
            String reply_message = sout.toString();
            getRemote().sendString(
                        "!" + Integer.toString(ack_id) + " " + reply_message);
          }

        }
        catch (JSONException ex) {
          ex.printStackTrace(System.err);
          throw new RuntimeException(ex);
        }
        catch (IOException ex) {
          ex.printStackTrace(System.err);
          throw new RuntimeException(ex);
        }

      }
      
    }

  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Mckoi Web Platform Web Socket";
  }

}
