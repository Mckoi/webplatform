/**
 * com.mckoi.mwpbase.DBBrowserServlet  Mar 4, 2012
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2010  Diehl and Associates, Inc.
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

import com.mckoi.gui.ODBHTMLFormatter;
import com.mckoi.mwpui.HTMLWriter;
import com.mckoi.odb.ODBClass;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.process.*;
import com.mckoi.webplatform.DDBResourceAccess;
import com.mckoi.webplatform.MckoiDDBPath;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * An administration function that allows the content of a database to be
 * browsed through a web interface.
 * 
 * @author Tobias Downer
 */
public class DBBrowserServlet extends HttpServlet {

  
  /**
   * Outputs a summery of an ODBTransaction together (the classes and
   * named items).
   */
  public static String pathListSummary(DDBResourceAccess mckoi_access,
                                      String link_prepend) throws IOException {

    StringWriter str_out = new StringWriter();
    PrintWriter out = new PrintWriter(str_out);

    // Path name
    out.println("<h2>Path List</h2>");

    Set<MckoiDDBPath> all_paths = mckoi_access.getAllPaths();
    for (MckoiDDBPath path : all_paths) {
      String path_name = path.getPathName();
      String consensus_fun = path.getConsensusFunction();

      if (consensus_fun.equals("com.mckoi.odb.ObjectDatabase")) {
        out.print("ObjectDatabase ");
        out.print("<a href='");
        out.print(link_prepend);
        out.print(HTMLWriter.toHTMLEntity(path_name) + "/");
        out.print("'>");
        out.print(HTMLWriter.toHTMLEntity(path_name));
        out.print("</a>");
      }
      else {
        out.print(HTMLWriter.toHTMLEntity(path_name));
      }

      out.print("<br/>");
    }

    out.flush();
    return str_out.toString();

  }

  /**
   * Outputs a summery of an ODBTransaction together (the classes and
   * named items).
   */
  public static String ODBPathSummary(ODBTransaction t, String path_name,
                                      String link_prepend) throws IOException {

    List<String> class_names = t.getClassNamesList();
    List<String> named_items = t.getNamedItemsList();

    StringWriter str_out = new StringWriter();
    PrintWriter out = new PrintWriter(str_out);

    // Path name
    out.print("<h2>Path: ");
    out.print(HTMLWriter.toHTMLEntity(path_name));
    out.println("</h2>");

    // Output classes,
    out.println("<h2>Classes</h2>");
    out.println("<ul>");
    for (String class_name : class_names) {

      ODBClass class_ob = t.findClass(class_name);
      String class_str = class_ob.getInstanceName();
      class_str = class_str.replace("#", "%23");

      out.print("<li><a href=\"");
      out.print(link_prepend);
      out.print("class:");

      out.print(class_str);

      out.print("\">");
      out.print(HTMLWriter.toHTMLEntity(class_name));
      out.print("</a></li>");

    }
    out.println("</ul>");

    // Output named items,
    out.println("<h2>Named Items</h2>");
    out.println("<ul>");
    for (String named_item : named_items) {

      ODBObject item_ob = t.getNamedItem(named_item);
      ODBClass class_ob = item_ob.getODBClass();

      String class_str = class_ob.getInstanceName();
      class_str = class_str.replace("#", "%23");

      String item_str = item_ob.getReference().toString();

      out.print("<li>");

      out.print("[");
      out.print("<a href=\"");
      out.print(link_prepend);
      out.print("class:");

      out.print(class_str);

      out.print("\">");
      out.print(HTMLWriter.toHTMLEntity(class_ob.getName()));
      out.print("</a>");
      out.print("] ");

      out.print("<a href=\"");
      out.print(link_prepend);
      out.print("instance:");
      out.print(class_str);
      out.print(":");
      out.print(item_str);

      out.print("\">");
      out.print(HTMLWriter.toHTMLEntity(named_item));
      out.print("</a>");
      out.print("</li>");

    }
    out.println("</ul>");

    out.flush();
    return str_out.toString();

  }

  
  /**
   * The login dialog.
   */
  private String doLoginDialog() {

    StringWriter str_out = new StringWriter();
    PrintWriter out = new PrintWriter(str_out);

    out.println("<h2>Login</h2>");
    out.println("<form method='post'>");
    out.println(
          "<input type='text' name='uname' size='20' /> <b>Username</b><br/>");
    out.println(
          "<input type='password' name='pass' size='20' /> <b>Password</b><br/>");
    out.println("<input type='submit' name='btn' value='Login' /><br/>");
    out.println("</form>");

    out.flush();
    return str_out.toString();

  }

  /**
   * Processes the HTTP request,
   */
  protected void processRequest(
              HttpServletRequest request, HttpServletResponse response,
              String request_type)
                                         throws ServletException, IOException {

    try {

      // Is this a dispatch?
      Object consume_status_key =
              request.getAttribute(AsyncServletProcessUtil.CONSUME_STATUS_KEY);

      // Make a process client,
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();

      ProcessInputMessage result;
      ProcessResult result_ob;

      // Is this the continuation init?
      if (consume_status_key == null) {

        Cookie pid_cookie = null;
        // The process id cookie,
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
          for (Cookie c : cookies) {
            String cname = c.getName();
            if (cname.equals("pid")) {
              pid_cookie = c;
            }
          }
        }

        if (pid_cookie == null) {
          // Give a friendlier error message here?
          response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                             "Session cookie not found");
          return;
        }

        // Get the cookie's value,
        String process_id_str = pid_cookie.getValue();

        AppServiceProcessClient process_client =
                                              ctx.getAppServiceProcessClient();
        ProcessId process_id = ProcessId.fromString(process_id_str);

        String ip_addr = request.getRemoteAddr();
        
        // Check if the process is valid,
        ProcessMessage msg_to_send = ByteArrayProcessMessage.encodeArgs(
                                                     "?", null, null, ip_addr);

        // Invoke the function on the process id,
        result_ob =
                 process_client.invokeFunction(process_id, msg_to_send, true);
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
      else {
        result_ob = (ProcessResult) request.getAttribute(getClass().getName());
        result = result_ob.getResult();
      }

      // This would be a timeout,
      if (result == null) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                           "Timed out waiting for response");
        return;
      }
      if (result.getType() == ProcessInputMessage.Type.RETURN_EXCEPTION) {
        throw new ServletException(result.getError());
      }

      // Ok, we now have our result from the process,
      Object[] args =
                ByteArrayProcessMessage.decodeArgsList(result.getMessage(), 0);
      String resp = (String) args[0];
      if (!resp.equals("OK")) {
        throw new RuntimeException("Invalid response from process: " + resp);
      }

      // The user of the process,
      String username = (String) args[1];

      // Ok, everything looks good to go,

      String location;
      String html_body;

      // Context info,
      String context_path = request.getContextPath();
      if (context_path == null) context_path = "";
      if (context_path.equals("/")) context_path = "";
      String servlet_path = request.getServletPath();
      String path_info = request.getPathInfo();
      if (path_info == null) path_info = "/";

      // Create the response,

      DDBResourceAccess mckoi_access = ctx.getDDBResourceAccess();

      // Parse the link,

      StringBuilder request_link = new StringBuilder();
      request_link.append(context_path);
      request_link.append(servlet_path);
      request_link.append(path_info);

      // Sub-part,
      int delim = path_info.lastIndexOf("/");
      String path_p = path_info.substring(0, delim);
      if (path_p.startsWith("/")) {
        path_p = path_p.substring(1);
      }

      // 'path_p' now contains the path we are looking at,

      // If no path_p,
      if (path_p.equals("")) {

        String link_prepend = context_path + servlet_path + "/";

        location = "Path List";
        html_body = pathListSummary(mckoi_access, link_prepend);

      }
      else {

        ODBTransaction t = mckoi_access.createODBTransaction(path_p);

        // Extract the location,
        delim = request_link.lastIndexOf("/");
        location = request_link.substring(delim + 1);

        String link_prepend = request_link.substring(0, delim + 1);

        // Get the 'pos' and 'size' vars if they exist,
        String pos = request.getParameter("pos");
        String size = request.getParameter("size");
        if (pos != null) {
          location = location + "?pos=" + pos + "&size=" + size;
        }

        if (location.equals("")) {

          location = "Path: " + path_p;
          html_body = ODBPathSummary(t, path_p, link_prepend);

        }
        else {

          // Get the formatter,
          ODBHTMLFormatter formatter = new ODBHTMLFormatter(t, link_prepend);

          // Format it,
          html_body = formatter.format(location);

        }

      }

      response.setContentType("text/html;charset=UTF-8");
      PrintWriter out = response.getWriter();

      try {

        out.println("<html>");
        out.println("<head>");
        out.println("<title>DBBrowser - " + HTMLWriter.toHTMLEntity(location) + "</title>");

        out.println("<style type=\"text/css\">");
        out.println(" table { border: 1px solid #000000; border-collapse: collapse; }");
        out.println(" td { border-left: 1px solid #000000; border-right: 1px solid #000000; padding: 0px 6px 0px 6px; }");
        out.println(" th { background: #000000; color: #FFFFFF; }");
        out.println(" table.oblist { font-family:monospace; }");
        out.println(" table.data { border: none; border-collapse: collapse; }");
        out.println(" table.data td { border-left: none; border-right: none; padding: 2px 12px 2px 12px; }");
        out.println("</style>");

        out.println("</head>");
        out.println("<body>");
        out.println(html_body);
        out.println("</body>");
        out.println("</html>");

      }
      finally {
        out.close();
      }

    }
    catch (Throwable e) {
      // Output the error message,
      
      response.setContentType("text/html;charset=UTF-8");
      PrintWriter out = response.getWriter();

      try {

        out.println("<html>");
        out.println("<head>");
        out.println("<title>DBBrowser - error when producing page.</title>");      
        out.println("</head>");
        out.println("<body>");
        out.println("<h1>" + HTMLWriter.toHTMLEntity(e.getMessage()) + "</h1>");
        out.print("<pre>");
        StringWriter str_out = new StringWriter();
        PrintWriter pout = new PrintWriter(str_out);
        e.printStackTrace(pout);
        pout.flush();
        out.print(HTMLWriter.toHTMLEntity(str_out.toString()));
        out.println("</pre>");
        out.println("</body>");
        out.println("</html>");

      }
      finally {
        out.close();
      }

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
    processRequest(request, response, "GET");
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
    processRequest(request, response, "POST");
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Database Browser";
  }

}
