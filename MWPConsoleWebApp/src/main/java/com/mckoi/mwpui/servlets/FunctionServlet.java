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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Tobias Downer
 */
public class FunctionServlet extends HttpServlet {

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
        final HttpServletRequest request, final HttpServletResponse response)
                                         throws ServletException, IOException {

    // Decode parameters from client,
    Map<String, String> params =
                        ServletUtils.decodeArguments(request.getInputStream());

    // Get the command and process id string
    final String process_id_str = params.get("p");
    final String frame_str = params.get("f");
    // Command to pass to process,
    final String command_str = params.get("c");
    // Signal to pass to process,
    final String signal_str = params.get("s");
    final String signal_feature_str = params.get("sf");

    CommandHandler cmd = new CommandHandler(
                                  process_id_str, frame_str, command_str,
                                  signal_str, signal_feature_str) {
      @Override
      PrintWriter getPrintWriter() throws IOException {
        response.setContentType("text/plain;charset=UTF-8");
        return response.getWriter();
      }
    };
    
    // Check if the request is valid,
    if (!cmd.isValidRequest()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    // Okay, run the command,
    cmd.run(request.getRemoteAddr());

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
    return "Process function handling.";
  }

}
