/**
 * com.mckoi.webplatform.impl.TomcatWebService  Apr 18, 2011
 *
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

package com.mckoi.webplatform.impl;

import java.io.IOException;
import java.util.Timer;
import javax.servlet.ServletException;
//import org.apache.catalina.Context;
//import org.apache.catalina.LifecycleException;
//import org.apache.catalina.connector.Connector;
//import org.apache.catalina.connector.Request;
//import org.apache.catalina.connector.Response;
//import org.apache.catalina.core.StandardContext;
//import org.apache.catalina.core.StandardEngine;
//import org.apache.catalina.core.StandardHost;
//import org.apache.catalina.core.StandardServer;
//import org.apache.catalina.core.StandardService;
//import org.apache.catalina.startup.Tomcat.FixContextListener;

/**
 * 
 *
 * @author Tobias Downer
 */

class TomcatWebService {

  /**
   * The system timer.
   */
  private Timer system_timer;

  /**
   * Constructor.
   */
  TomcatWebService(Timer system_timer) {
    this.system_timer = system_timer;
  }

//  void start() throws IOException, ServletException,
//                      LifecycleException, InterruptedException  {
//
//    // The Tomcat service object,
//    StandardServer server = new StandardServer();
//
//    // Tomcat service,
//    StandardService service = new StandardService();
//    service.setName("Tomcat");
//    server.addService( service );
//
//    // Tomcat engine,
//    StandardEngine engine = new StandardEngine() {
//      @Override
//      public void invoke(Request request, Response response) throws IOException, ServletException {
//        System.out.println("1INVOKE: " + request);
//        super.invoke(request, response);
//      }
//    };
//    engine.setName( "Tomcat" );
//    engine.setDefaultHost("appadmin.localhost");
//    engine.setBaseDir("tomcattest\\"); // The tomcat installation directory
//
//    service.setContainer(engine);
//
//    // Tomcat host (not sure I understand this yet),
//    StandardHost host = new StandardHost() {
//      @Override
//      public void invoke(Request request, Response response) throws IOException, ServletException {
//        System.out.println("2INVOKE: " + request);
//        super.invoke(request, response);
//      }
//    };
//    host.setName("appadmin.localhost");
//
//    engine.addChild( host );
//
//
//    Context ctx = new StandardContext();
//    ctx.setPath( "/" );
//    ctx.setDocBase("test/");
//    ctx.addLifecycleListener(new FixContextListener());
//
//    host.addChild(ctx);
//
//
//
//
//
//    // Create a connector,
//    Connector connector = new Connector("HTTP/1.1");
//    // connector = new Connector("org.apache.coyote.http11.Http11Protocol");
//    connector.setPort(80);
//    service.addConnector( connector );
//
//    // Start the server,
//    server.start();
//    // Wait until it terminates,
//    server.await();
//
//
//
//  }

}
