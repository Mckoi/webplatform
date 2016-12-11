/**
 * com.mckoi.mwpcore.ProcessThread  Mar 19, 2012
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

package com.mckoi.appcore;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A thread that handles a process.
 *
 * @author Tobias Downer
 */

public class ProcessThread extends Thread {

  /**
   * The appcore log.
   */
  private static final Logger LOG = AppServiceNode.APPCORE_LOG;

  /**
   * The process.
   */
  private final Process process;

  private BufferedWriter process_out;
  private BufferedReader process_in;

  private final List<Reply> reply_log;
  private int command_id = 0;

  private final Object finish_lock = new Object();
  private boolean is_finished = false;

  /**
   * Constructor.
   */
  public ProcessThread(Process process) throws IOException {
    this.process = process;

    process_in = new BufferedReader(
            new InputStreamReader(process.getInputStream(), "UTF-8"));
    process_out = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), "UTF-8"));

    reply_log = new ArrayList(4);
  }

  /**
   * Kill the process.
   */
  public void kill() {
    process.destroy();
  }

  /**
   * Sends a command to the process and waits for a reply.
   */
  public String send(String cmd) throws IOException {
    synchronized (reply_log) {
      int used_command_id = sendWithoutReply(cmd);
      // Wait for a reply,
      long timeout_start = System.currentTimeMillis();
      while ((System.currentTimeMillis() - timeout_start) < 15000) {
        try {
          reply_log.wait(3000);
        }
        catch (InterruptedException e) { }
        int sz = reply_log.size();
        for (int i = 0; i < sz; ++i) {
          Reply r = reply_log.get(i);
          if (r.command_id == used_command_id) {
            String reply = r.message;
            reply_log.remove(i);
            return reply;
          }
        }
      }
    }
    return "TIMEOUT";
  }

  /**
   * Send a command without caring if we get a reply from the server or not.
   */
  public int sendWithoutReply(String cmd) throws IOException {
    synchronized (reply_log) {
      int used_command_id = command_id;
      ++command_id;
      // Unique id for this command,
      process_out.append(Integer.toString(used_command_id));
      process_out.newLine();
      // Flush the command,
      process_out.append(cmd);
      process_out.newLine();
      process_out.flush();
      return used_command_id;
    }
  }

  /**
   * Returns true if this process thread is finished, false otherwise.
   */
  public boolean isFinished() {
    synchronized (finish_lock) {
      return is_finished;
    }
  }

  /**
   * Blocks until this thread is finished.
   */
  public void waitUntilFinished() throws InterruptedException {
    synchronized (finish_lock) {
      while (!is_finished) {
        finish_lock.wait();
      }
    }
  }

  @Override
  public void run() {

    LOG.info("ProcessThread started");
    try {
      while (true) {
        // Wait for messages from the process,
        String command = process_in.readLine();
        if (command == null) {
          // End reached,
          return;
        }
        // Echo command,
        if (command.startsWith("#")) {
          System.out.println(command.substring(1));
        }
        else if (command.startsWith(">")) {
          String reply_message = process_in.readLine();
          int reply_command_id = Integer.parseInt(command.substring(1));
          synchronized (reply_log) {
            reply_log.add(new Reply(reply_command_id, reply_message));
            // Don't let the reply log get too large,
            if (reply_log.size() > 512) {
              // Remove the first,
              reply_log.remove(0);
            }
            reply_log.notifyAll();
          }
        }
        else {
          LOG.log(Level.SEVERE, "Unknown command: {0}", command);
        }
      }
    }
    catch (IOException e) {
      LOG.log(Level.SEVERE, "ProcessThread IOException", e);
    }
    finally {
      LOG.info("ProcessThread ended");
      // Change the is_finished flag,
      synchronized (finish_lock) {
        is_finished = true;
        finish_lock.notifyAll();
      }
    }

  }

  private static class Reply {
    int command_id;
    String message;

    private Reply(int command_id, String message) {
      this.command_id = command_id;
      this.message = message;
    }
  }

}
