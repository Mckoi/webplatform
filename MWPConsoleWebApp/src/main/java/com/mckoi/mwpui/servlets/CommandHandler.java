/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.mckoi.mwpui.servlets;

import com.mckoi.process.AppServiceProcessClient;
import com.mckoi.process.ByteArrayProcessMessage;
import com.mckoi.process.ProcessId;
import com.mckoi.process.ProcessMessage;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author Tobias Downer
 */
abstract class CommandHandler {
  
  private final String process_id_str;
  private final String frame_str;
  private final String command_str;
  private final String signal_str;
  private final String signal_feature_str;

  CommandHandler(String process_id_str, String frame_str, String command_str, String signal_str, String signal_feature_str) {
    this.process_id_str = process_id_str;
    this.frame_str = frame_str;
    this.command_str = command_str;
    this.signal_str = signal_str;
    this.signal_feature_str = signal_feature_str;
  }

  boolean isValidRequest() {
    return process_id_str != null &&
            ( command_str != null ||
             ( signal_str != null && signal_feature_str != null ) );
  }

  abstract PrintWriter getPrintWriter() throws IOException;

  void run(String ip_addr) throws IOException {

    PlatformContext ctx = PlatformContextFactory.getPlatformContext();
    // Disable automatic logging,
    ctx.getLogControl().setAutomaticLogging(false);
    AppServiceProcessClient process_client = ctx.getAppServiceProcessClient();
    ProcessId process_id = ProcessId.fromString(process_id_str);

    // NOTE: We can reply here for immediate response to the client if needed
    //   for some commands/state.
    PrintWriter out = getPrintWriter();

    try {

      // Immediate push and ignore the result,
      // NOTE; This ignores failure conditions,

      // Send the command,
      if (command_str != null) {
        // Create the message,
        ProcessMessage msg = ByteArrayProcessMessage.encodeArgs("@",
                                      command_str, frame_str, ip_addr);
        // Invoke the function on the process,
        process_client.invokeFunction(process_id, msg, false);
      }
      // Send the signal,
      if (signal_str != null) {
        // If it's a kill signal,
        if (signal_feature_str.equals("kill")) {
          String[] signal = new String[]
                    { signal_feature_str, frame_str, ip_addr };
          process_client.sendSignal(process_id, signal);
        }
        // Otherwise, send as an interact message,
        else {
          // Create the message,
          ProcessMessage msg = ByteArrayProcessMessage.encodeArgs("#",
                                      signal_feature_str, frame_str, ip_addr);
          // Invoke the function on the process,
          process_client.invokeFunction(process_id, msg, false);
        }
      }
      
      // NOTE; we don't care about the result, we let it GC
      out.println("OK");

    }
    catch (Throwable e) {
      out.println("FAIL:Exception");
      e.printStackTrace(out);
    }

    out.flush();
    out.close();

  }
  
  
}
