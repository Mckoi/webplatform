/*
 * Copyright 2015 Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mckoi.platform.mwptests.servlets;

import com.mckoi.platform.mwptests.FormatHelper;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONStringer;

/**
 * Unit tests for Web application server functionality only, such as
 * WebSockets, JSP, etc.
 *
 * @author Tobias Downer
 */
@WebServlet(name = "MainServlet", urlPatterns = {"/Main"})
public class MainServlet extends HttpServlet {

  protected void processRequest(
                  HttpServletRequest request, HttpServletResponse response)
                                        throws ServletException, IOException {

    // Get the test parameter,
    String[] test_param = request.getParameterValues("case");
    if (test_param == null) {
      response.sendError(
          HttpServletResponse.SC_BAD_REQUEST, "No 'case' parameter");
      return;
    }
    // If multiple parameters,
    if (test_param.length > 1) {
      response.sendError(
          HttpServletResponse.SC_BAD_REQUEST, "Multiple 'case' parameters");
      return;
    }

    // The test case,
    String test_case = test_param[0];

    response.setContentType("text/plain;charset=UTF-8");

    try (PrintWriter out = response.getWriter()) {
      JSONStringer json_s = new JSONStringer();
      json_s.object();
      json_s.key("case").value(test_case);
      
      
      
      
      json_s.endObject();
      out.append(json_s.toString());
    }
    catch (JSONException ex) {
      // Format exception as return message,
      String json_err = FormatHelper.jsonErrorOutput(ex);
      response.sendError(
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR, json_err);
    }

  }



  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                                        throws ServletException, IOException {

    processRequest(req, resp);

  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                                        throws ServletException, IOException {

    processRequest(req, resp);

  }

}
