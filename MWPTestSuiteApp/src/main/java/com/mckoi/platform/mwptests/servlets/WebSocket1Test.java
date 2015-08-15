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

import com.mckoi.odb.util.FileInfo;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * Tests Jetty WebSocket servlet.
 *
 * @author Tobias Downer
 */

@WebServlet(name = "WebSocket1Test", urlPatterns = {"/WSock1"})
public class WebSocket1Test extends WebSocketServlet {


  private static void handle(String data,
                                 RemoteEndpoint connection, Session session) {
    try {
      if (data.equals("HANDSHAKE")) {
        connection.sendString("HANDSHAKE RET");
      }
      else if (data.equals("START COUNTER")) {
        for (int i = 0; i < 10; ++i) {
          connection.sendString("count=" + Integer.toString(i));
        }
        connection.sendString("COUNT RET");
      }
      else if (data.equals("GET BINARY")) {
        byte[] buf = new byte[100];
        connection.sendBytes(ByteBuffer.wrap(buf, 0, buf.length));
      }
      else if (data.equals("PLATCTX FSQUERY")) {
        PlatformContext ctx = PlatformContextFactory.getPlatformContext();
        FileRepository file_repo = ctx.getFileRepository();
        List<FileInfo> file_list = file_repo.getFileList("/");
        connection.sendString("FSQUERY: File count of '/' = " + file_list.size());
      }
      else if (data.equals("REMOTE CLOSE")) {
        session.close();
      }


    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
  
  
  

  @Override
  public void configure(WebSocketServletFactory factory) {
//    try {
//      System.out.println("--ST-----");
//      Enumeration<URL> systemResources = ClassLoader.getSystemResources("META-INF/services/org.eclipse.jetty.websocket.servlet.WebSocketServletFactory");
//      while (systemResources.hasMoreElements()) {
//        System.out.println(systemResources.nextElement());
//      }
//      System.out.println("--EN---");
//    }
//    catch (IOException ex) {
//      ex.printStackTrace(System.err);
//    }
//    
//    ExtensionFactory extensions = factory.getExtensionFactory();
//    extensions.register("mwp", TestExtension.class);
    factory.register(WebSocket1TestAdapter.class);
  }

  public static class WebSocket1TestAdapter extends WebSocketAdapter {
    
    @Override
    public void onWebSocketText(String message) {
      handle(message, getRemote(), getSession());
    }
    
  }

}


//@WebServlet(name = "WebSocketTest", urlPatterns = {"/WSock"})
//public class WebSocketTest extends WebSocketServlet {
//
//  @Override
//  public WebSocket doWebSocketConnect(
//                              HttpServletRequest request, String protocol) {
//    return new WSEvents();
//  }
//  
//  private class WSEvents implements WebSocket.OnTextMessage {
//
//    private Connection connection;
//    
//    @Override
//    public void onOpen(Connection connection) {
//      this.connection = connection;
//    }
//
//    @Override
//    public void onClose(int closeCode, String message) {
//      this.connection = null;
//    }
//
//    @Override
//    public void onMessage(String data) {
//      try {
//        if (data.equals("HANDSHAKE")) {
//          connection.sendMessage("HANDSHAKE RET");
//        }
//        else if (data.equals("START COUNTER")) {
//          for (int i = 0; i < 10; ++i) {
//            connection.sendMessage("count=" + Integer.toString(i));
//          }
//          connection.sendMessage("COUNT RET");
//        }
//        else if (data.equals("GET BINARY")) {
//          byte[] buf = new byte[100];
//          connection.sendMessage(buf, 0, buf.length);
//        }
//        else if (data.equals("REMOTE CLOSE")) {
//          connection.close();
//        }
//        
//        
//      }
//      catch (IOException ex) {
//        throw new RuntimeException(ex);
//      }
//    }
//    
//  }
//  
//}
