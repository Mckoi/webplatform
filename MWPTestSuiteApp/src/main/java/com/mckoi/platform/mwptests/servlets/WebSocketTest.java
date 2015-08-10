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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

/**
 * Tests Jetty WebSocket servlet.
 *
 * @author Tobias Downer
 */
public class WebSocketTest extends WebSocketServlet {

  @Override
  public WebSocket doWebSocketConnect(
                              HttpServletRequest request, String protocol) {
    System.out.println("connect: " + protocol);
    return new WSEvents();
  }
  
  private class WSEvents implements WebSocket.OnTextMessage {

    private Connection connection;
    
    @Override
    public void onOpen(Connection connection) {
      this.connection = connection;
      System.out.println("onOpen " + connection);
    }

    @Override
    public void onClose(int closeCode, String message) {
      this.connection = null;
      System.out.println("onClose " + closeCode + " " + message);
    }

    @Override
    public void onMessage(String data) {
      System.out.println("onMessage " + data);
      if (data.equals("INIT")) {
        try {
          byte[] buf = new byte[100];
          connection.sendMessage("INIT CONFIRMED!");
          connection.sendMessage(buf, 0, buf.length);
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    
  }
  
}
