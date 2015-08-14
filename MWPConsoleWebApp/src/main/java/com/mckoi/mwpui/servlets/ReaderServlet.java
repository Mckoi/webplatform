/**
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

package com.mckoi.mwpui.servlets;

import com.mckoi.mwpui.ServletUtils;
import com.mckoi.process.*;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The main servlet that clients sit on using AJAX.
 * 
 * @author Tobias Downer
 */

//@WebServlet(name = "MainServlet", urlPatterns = {"/M"},
//            asyncSupported = true)
public class ReaderServlet extends HttpServlet {

  
  static void formatMessages(PrintWriter out, String session_state,
                             Collection<ProcessMessage> messages)
                                                          throws IOException {

    out.println("OK");
    out.println(session_state);

    // If there are messages, return them immediately,
    for (ProcessMessage m : messages) {
      BufferedReader r = new BufferedReader(
                        new InputStreamReader(m.getMessageStream(), "UTF-8"));
      StringBuilder b = new StringBuilder(m.size() + 2);
      while (true) {
        int ch = r.read();
        if (ch < 0) {
          break;
        }
        b.append((char) ch);
      }
      String msg_str = b.toString();
      int delim = msg_str.indexOf("|");
      if (delim == -1) {
        throw new RuntimeException("Message format invalid");
      }
      String message_type = msg_str.substring(0, delim);
      String message_body = msg_str.substring(delim + 1);

      out.print(message_type);
      out.print("|");
      out.print(Integer.toString(message_body.length()));
      out.print("|");
      out.print(message_body);
    }

  }

  /**
   * Processes requests for both HTTP
   * <code>GET</code> and
   * <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   * @throws com.mckoi.process.ProcessUnavailableException
   */
  protected void processRequest(
                  HttpServletRequest request, HttpServletResponse response)
                                      throws ServletException, IOException,
                                             ProcessUnavailableException {

    // Is this a dispatch?
    Object consume_status_key =
              request.getAttribute(AsyncServletProcessUtil.CONSUME_STATUS_KEY);
    Map<String, String> params;
    if (consume_status_key == null) {
      // No so decode the args,
      params = ServletUtils.decodeArguments(request.getInputStream());
      request.setAttribute(getClass().getName(), params);
    }
    else {
      params = (Map<String, String>) request.getAttribute(getClass().getName());
    }

    // Get the session state,
    final String session_state_str = params.get("ss");

    // Bad request if no session state,
    if (session_state_str == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    // Get the context,
    PlatformContext ctx = PlatformContextFactory.getPlatformContext();

    // Disable automatic logging from this point on,
    ctx.getLogControl().setAutomaticLogging(false);

    // The process client,
    AppServiceProcessClient pc = ctx.getAppServiceProcessClient();

    ChannelConsumer consumer = pc.getChannelConsumer(
                                   new ChannelSessionState(session_state_str));

    Collection<ProcessMessage> messages;

    if (consume_status_key == null) {

      // Consume from channel.
      // If nothing then return (the request in will redispatched when messages
      // are available).
      messages = consumer.consumeFromChannel(
                         100, AsyncServletProcessUtil.createNotifier(request));
      if (messages == null) {
        return;
      }

    }
    // Either a timeout or available status - consume anything available,
    else {
      messages = consumer.consumeFromChannel(100);
    }

    // New session state,
    ChannelSessionState new_state = consumer.getSessionState();

    response.setContentType("text/plain;charset=UTF-8");

    // Output the messages,
    try (PrintWriter out = response.getWriter()) {
      // Output the messages,
      formatMessages(out, new_state.toString(), messages);
      out.flush();
    }

  }

  /**
   * Handles the HTTP
   * <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(
                   HttpServletRequest request, HttpServletResponse response)
                                        throws ServletException, IOException {
    response.sendError(
                HttpServletResponse.SC_FORBIDDEN, "GET request is forbidden");

  }

  /**
   * Handles the HTTP
   * <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(
                   HttpServletRequest request, HttpServletResponse response)
                                        throws ServletException, IOException {

    try {
      processRequest(request, response);
    }
    catch (ProcessUnavailableException e) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                         "Process Server Unavailable");
    }

  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "The main servlet AJAX handler";
  }

}
