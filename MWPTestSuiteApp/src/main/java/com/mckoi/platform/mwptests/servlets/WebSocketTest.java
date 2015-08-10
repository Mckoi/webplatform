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
    return new WSEvents();
  }
  
  private class WSEvents implements WebSocket.OnTextMessage {

    private Connection connection;
    
    @Override
    public void onOpen(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void onClose(int closeCode, String message) {
      this.connection = null;
    }

    @Override
    public void onMessage(String data) {
      try {
        if (data.equals("HANDSHAKE")) {
          connection.sendMessage("HANDSHAKE RET");
        }
        else if (data.equals("START COUNTER")) {
          for (int i = 0; i < 10; ++i) {
            connection.sendMessage("count=" + Integer.toString(i));
          }
          connection.sendMessage("COUNT RET");
        }
        else if (data.equals("GET BINARY")) {
          byte[] buf = new byte[100];
          connection.sendMessage(buf, 0, buf.length);
        }
        else if (data.equals("REMOTE CLOSE")) {
          connection.close();
        }
        
        
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
    
  }
  
}
