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
import com.mckoi.mwpui.SessionProcessOperation;
import com.mckoi.process.*;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import com.mckoi.webplatform.UserManager;
import com.mckoi.webplatform.UserProfile;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 
 * 
 * @author Tobias Downer
 */
public class AuthServlet extends HttpServlet {

  /**
   * Processes requests for both HTTP
   * <code>GET</code> and
   * <code>POST</code> methods.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  protected void processRequest(
              HttpServletRequest request, HttpServletResponse response)
                                        throws ServletException, IOException {

    response.setContentType("text/plain;charset=UTF-8");
    PrintWriter out = response.getWriter();

    try {

      // Decode parameters from client,
      Map<String, String> params =
                        ServletUtils.decodeArguments(request.getInputStream());
      
      // AJAX authentication procedure,
      String username = params.get("user");
      String password = params.get("pass");

      // Try and authenticate,
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      UserManager user_manager = ctx.getUserManager();
      UserProfile profile = user_manager.getUserProfile(username);

      if (profile != null && profile.matchesPassword(password)) {

        // Auth success!
        // After authentication, we create a process for handling this session,

        try {
          AppServiceProcessClient process_client =
                                              ctx.getAppServiceProcessClient();
          String this_webapp = ctx.getWebApplicationName();
          ProcessId process_id = process_client.createProcess(
                                this_webapp,
                                SessionProcessOperation.class.getName());

          String ip_addr = request.getRemoteAddr();

          // Initialization stack frame '0',
          ByteArrayProcessMessage msg = ByteArrayProcessMessage.encodeArgs(
                                               "init", username, "0", ip_addr);
          // Reply to this is not expected
          process_client.invokeFunction(process_id, msg, false);

          // Channel 0 consumer,
          ChannelConsumer c0_consumer = process_client.getChannelConsumer(
                                            new ProcessChannel(process_id, 0));

          // Set a 'pid' cookie for this session,
          String pid_string = process_id.getStringValue();

          // Context info,
          String context_path = request.getContextPath();
          if (context_path == null) context_path = "";
          if (context_path.equals("/")) context_path = "";

          // Set the process id cookie,
          Cookie pid_cookie = new Cookie("pid", pid_string);
          // Session expiration,
          pid_cookie.setMaxAge(-1);
          pid_cookie.setPath(context_path);
          response.addCookie(pid_cookie);

          // Send success message,
          out.println("OK");
          // The process id for the session,
          out.println(pid_string);
          // Channel 0 broadcast consumer state,
          out.println(c0_consumer.getSessionState());

        }
        catch (Throwable e) {
          out.println("FAIL:Exception");
          e.printStackTrace(out);
        }

      }
      else {
        out.println("FAIL:AUTH");
      }

    }
    finally {      
      out.close();
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
//    processRequest(request, response);

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

    processRequest(request, response);

  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Authentication Servlet";
  }

}
