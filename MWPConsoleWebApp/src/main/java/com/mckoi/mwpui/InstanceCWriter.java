/**
 * com.mckoi.mwpui.InstanceCWriter  May 2, 2012
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

package com.mckoi.mwpui;

import com.mckoi.apihelper.BroadcastSWriter;
import com.mckoi.mwpui.apihelper.TextUtils;
import com.mckoi.process.ProcessInstance;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * Handles terminal commands in an instance.
 *
 * @author Tobias Downer
 */

public class InstanceCWriter extends BroadcastSWriter implements CommandWriter {

  /**
   * The instance.
   */
  private final ProcessInstance instance;
  
  /**
   * Constructor.
   */
  public InstanceCWriter(String frame, ProcessInstance instance) {
    super(instance, 0, "F" + frame);
    this.instance = instance;
  }

  /**
   * Creates a new frame and returns an InstanceCWriter for writing to it.
   */
  InstanceCWriter createNewFrame(
                           String panel, String new_frame_name, String icon) {
    
    flushIfFull(1 + panel.length() + 1 + new_frame_name.length() + 1 + 
                icon.length() + 1);

    packet_buffer.appendCode('#');
    packet_buffer.appendNoneDelim(panel);
    packet_buffer.appendDelimiter();
    packet_buffer.appendNoneDelim(new_frame_name);
    packet_buffer.appendDelimiter();
    packet_buffer.appendNoneDelim(icon);
    packet_buffer.appendDelimiter();
    
    // Create a new frame instance,
    return new InstanceCWriter(new_frame_name, instance);

  }

  /**
   * Sets the current prompt string.
   */
  public void setPrompt(CharSequence prompt_string) {
    
    flushIfFull(1 + prompt_string.length() + 4);

    packet_buffer.appendCode('>');
    packet_buffer.appendMessage(prompt_string);
    
  }

  /**
   * Appends a single message with a code and sub_code character.
   * 
   * @param code
   * @param sub_code
   * @param message 
   */
  void appendExtendedLine(char code, char sub_code, String message) {
    flushIfFull(message.length() + 6 + 2);
    packet_buffer.appendCode(code);
    packet_buffer.appendCode(sub_code);
    packet_buffer.appendMessage(message);
  }

  /**
   * Splits the given message into lines and outputs each line using the
   * 'appendExtendedLine' method. This ensures we don't completely fill up the
   * packet buffer with one particularly large message.
   * 
   * @param code
   * @param sub_code
   * @param message 
   */
  void appendExtendedLines(char code, char sub_code, String message) {
    try (BufferedReader reader = new BufferedReader(new StringReader(message))) {
      String line = reader.readLine();
      while (line != null) {
        appendExtendedLine(code, sub_code, line);
        line = reader.readLine();
      }
    }
    catch (IOException ex) {
      // This should be impossible,
      throw new RuntimeException(ex);
    }
  }

  String errorToString(Object string_or_throwable) {
    if (string_or_throwable == null) {
      return null;
    }
    if (string_or_throwable instanceof CharSequence) {
      return string_or_throwable.toString();
    }
    else {
      Throwable e = (Throwable) string_or_throwable;
      try (StringWriter sout = new StringWriter()) {
        e.printStackTrace(new PrintWriter(sout));
        return sout.toString();
      }
      catch (IOException ex) {
        // This should be impossible,
        throw new RuntimeException(ex);
      }
    }
  }

  @Override
  public void printExtendedError(
              String message_line, Object title_block, Object extended_block) {

    String title_err = errorToString(title_block);
    String extended_err = errorToString(extended_block);

    if (message_line == null) {
      if (title_block == null) {
        if (extended_block == null) {
          return;
        }
        else {
          int delim = extended_err.indexOf('\n');
          if (delim == -1) {
            delim = extended_err.length();
          }
          message_line = extended_err.substring(0, delim);
          extended_err = delim < extended_err.length() ?
                          extended_err.substring(delim + 1) : "";
        }
      }
      else {
        message_line = "";
      }
    }

    appendExtendedLine('E', 's', message_line);
    if (title_block != null) {
      appendExtendedLines('E', 't', title_err);
    }
    if (extended_block != null) {
      appendExtendedLines('E', 'e', extended_err);
    }
    appendExtendedLine('E', 'f', "");

  }

  @Override
  public void cls() {
    sendControl('C');
  }

  @Override
  public Object createHtml(String html_text) {
    if (html_text == null) throw new NullPointerException();

    return new InnerHTML(html_text);
  }

  @Override
  public Object createApplicationLink(String label, String relative_url_query) {
    if (label == null) throw new NullPointerException();
    if (relative_url_query == null) throw new NullPointerException();

    StringBuilder b = new StringBuilder();
    b.append("<a class=\"terminal_app_ahref\" href=\"");
    b.append(relative_url_query);
    b.append("\" target=\"_blank\">");
    b.append(HTMLWriter.toHTMLEntity(label));
    b.append("</a>");
    return new InnerHTML(b);
  }

  @Override
  public Object createCallbackLink(String label, String callback_command) {
    if (label == null) throw new NullPointerException();

    StringBuilder b = new StringBuilder();
    b.append("<a class=\"terminal_ahref\" href=\"#");
    // Encode the callback command,
    if (callback_command != null) {
      b.append(HTMLWriter.URIEncodeComponent(callback_command));
    }

    b.append("\">");
    b.append(HTMLWriter.toHTMLEntity(label));
    b.append("</a>");
    return new InnerHTML(b);
  }

  @Override
  public Object createInputField(String var, int size, int limit) {
    if (var == null) throw new NullPointerException();

    // size maxlength value
    StringBuilder b = new StringBuilder();
    b.append("<input class=\"terminal_inputfield\" type=\"text\" name=\"");
    b.append(HTMLWriter.toHTMLEntity(var));
    b.append("\" size=\"");
    b.append(Integer.toString(size));
    b.append("\" maxlength=\"");
    b.append(Integer.toString(limit));
    b.append("\" />");
    return new InnerHTML(b);
  }

  @Override
  public Object createInputField(String var, int size) {
    if (var == null) throw new NullPointerException();

    // size maxlength value
    StringBuilder b = new StringBuilder();
    b.append("<input type=\"text\" name=\"");
    b.append(HTMLWriter.toHTMLEntity(var));
    b.append("\" size=\"");
    b.append(Integer.toString(size));
    b.append("\" />");
    return new InnerHTML(b);
  }

  @Override
  public Object createCallbackButton(String label, Map<String, String> params) {
    throw new UnsupportedOperationException("Not supported yet.");
  }


  @Override
  public void runScript(String javascript_file,
                        String invocation_function, String command_str) {

    if (command_str == null) command_str = "";

    flushIfFull(1 + javascript_file.length() + 1 +
                invocation_function.length() + 1 + command_str.length() + 4);

    packet_buffer.appendCode('s');
    packet_buffer.appendNoneDelim(javascript_file);
    packet_buffer.appendDelimiter();
    packet_buffer.appendNoneDelim(invocation_function);
    packet_buffer.appendDelimiter();
    packet_buffer.appendMessage(command_str);

  }

  @Override
  public void switchToFrame(String frame_value) {

    flushIfFull(1 + frame_value.length() + 1);

    packet_buffer.appendCode('f');
    packet_buffer.appendNoneDelim(frame_value);
    packet_buffer.appendDelimiter();

  }

  @Override
  public void sendGeneral(String message) {
    if (message == null) throw new NullPointerException();

    flushIfFull(1 + message.length() + 4);

    packet_buffer.appendCode('^');
    packet_buffer.appendMessage(message);

  }

  /**
   * Sends an interact-reply message back to the client.
   */
  public void sendInteractReply(String reply) {
    if (reply == null) throw new NullPointerException();

    flushIfFull(1 + reply.length() + 4);
    
    packet_buffer.appendCode('t');
    packet_buffer.appendMessage(reply);

  }

  /**
   * Writes a packet block to the packet buffer. It's assumed the given string
   * is a properly formatted packet block. Use with care - you can mess up the
   * packet stream if you use this incorrectly.
   */
  void writePacketBlock(String packet_block) {
    flushIfFull(packet_block.length());
    packet_buffer.appendPacketBlock(packet_block);
  }

}
