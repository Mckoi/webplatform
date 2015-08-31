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

import com.mckoi.mwpui.FileUpload;
import com.mckoi.mwpui.ZipDownload;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import static com.mckoi.process.AsyncServletProcessUtil.*;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Tobias Downer
 */
public class GeneralCommandServlet extends HttpServlet {

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

    String e_param = request.getParameter("e");

    // Get the proces id,
    String process_id_str = request.getHeader("MckoiProcessId");
    if (process_id_str == null) {
      process_id_str = request.getParameter("pid");
    }

    if (process_id_str == null || e_param == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    // Is this a dispatch?
    Object consume_status_key = request.getAttribute(CONSUME_STATUS_KEY);

    // Make a process client,
    PlatformContext ctx = PlatformContextFactory.getPlatformContext();

    ProcessInputMessage result;
    ProcessResult result_ob;

    // The initial continuation call,
    if (consume_status_key == null) {

      AppServiceProcessClient process_client = ctx.getAppServiceProcessClient();
      ProcessId process_id = ProcessId.fromString(process_id_str);

      String ip_addr = request.getRemoteAddr();

      ProcessMessage msg_to_send;

      // The download utility,
      if (e_param.equals("dl")) {
        // The location string,
        String loc = request.getParameter("loc");
        // Ask the process to qualify the location and check validity,
        msg_to_send = ByteArrayProcessMessage.encodeArgs(
                                                     "dl", loc, null, ip_addr);
      }
      else if (e_param.equals("ul")) {
        // The location string,
        String loc = request.getParameter("loc");
        // Ask the process to qualify the location and check validity,
        msg_to_send = ByteArrayProcessMessage.encodeArgs(
                                                     "ul", loc, null, ip_addr);
      }
      else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                           "'e' parameter is invalid.");
        return;
      }

      result_ob = process_client.invokeFunction(process_id, msg_to_send, true);
      request.setAttribute(getClass().getName(), result_ob);

      result =
          result_ob.getResult(AsyncServletProcessUtil.createNotifier(request));
      if (result == null) {
        // Set timeout to 30 seconds,
        request.getAsyncContext().setTimeout(30 * 1000);
        return;
      }

    }
    // Not initial,
    else if (consume_status_key.equals(CONSUME_STATUS_AVAILABLE)) {
      result_ob = (ProcessResult) request.getAttribute(getClass().getName());
      result = result_ob.getResult();
    }
    // This would be a timeout or io error,
    else {
      String fail_msg = "Timed out waiting for response";
      if (consume_status_key.equals(CONSUME_STATUS_IOERROR) ) {
        fail_msg = "IO Error while waiting for response";
      }
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, fail_msg);
      return;
    }

    if (result.getType() == ProcessInputMessage.Type.RETURN_EXCEPTION) {
      throw new ServletException(result.getError());
    }

    // The result,
    Object[] args =
                ByteArrayProcessMessage.decodeArgsList(result.getMessage(), 0);
    if (args[0].equals("OK")) {
      
      // For download,
      if (e_param.equals("dl")) {
        String fs_name = (String) args[1];
        String qual_loc = (String) args[2];
        FileName normal_fn = new FileName(qual_loc);
        ZipDownload.process(request, response, ctx, normal_fn);
      }
      // For upload,
      else if (e_param.equals("ul")) {
        String fs_name = (String) args[1];
        String qual_loc = (String) args[2];
        FileName normal_fn = new FileName(qual_loc);
        // Response,
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();
        FileUpload.processUpload(request, ctx, normal_fn, out);
        out.close();
      }

    }
    // Otherwise failure,
    else {
      response.sendError(
              HttpServletResponse.SC_BAD_REQUEST, "Process rejected request");
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
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {

    // GET not allowed to prevent cross site exploits
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
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
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
    return "General commands to support Mckoi Web Platform";
  }

}
