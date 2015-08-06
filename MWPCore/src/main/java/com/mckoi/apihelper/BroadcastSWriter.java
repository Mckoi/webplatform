/**
 * com.mckoi.apihelper.BroadcastSWriter  Nov 23, 2012
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

package com.mckoi.apihelper;

import com.mckoi.process.ByteArrayProcessMessage;
import com.mckoi.process.ProcessInstance;
import com.mckoi.process.ProcessMessage;
import com.mckoi.util.IOWrapStyledPrintWriter;
import com.mckoi.util.StyledPrintWriter;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * A StyledPrintWriter that flushes control messages to a broadcast channel.
 * The control messages are encoded in a format that is easy for JavaScript
 * to process.
 *
 * @author Tobias Downer
 */

public class BroadcastSWriter implements StyledPrintWriter {

  /**
   * The broadcaster object.
   */
  private final Broadcaster broadcaster;

  /**
   * The packet builder.
   */
  protected final PacketBuilder packet_buffer;

  /**
   * Constructs a writer that flushes formatted messages to the given
   * Broadcaster. The 'broadcast_code' indicates the string to prepend to all
   * broadcast packets.
   */
  public BroadcastSWriter(Broadcaster broadcaster, String broadcast_code) {
    this.broadcaster = broadcaster;
    // The broadcast code we use for this frame,
    this.packet_buffer = new PacketBuilder(broadcast_code, 256);
  }

  /**
   * Creates a broadcast writer that broadcasts the styled messages to the
   * given ProcessInstance on the given channel.
   */
  public BroadcastSWriter(
            ProcessInstance instance, int channel_num, String broadcast_code) {
    this(new PIBroadcaster(instance, channel_num), broadcast_code);
  }

  /**
   * Checks the given string contains no invalid characters (contains no '|'
   * characters). If invalid, this method throws an exception.
   */
  static void checkValid(CharSequence str) {
    int sz = str.length();
    for (int i = 0; i < sz; ++i) {
      char ch = str.charAt(i);
      if (ch == '|') {
        throw new RuntimeException("Invalid string (contains '|')");
      }
      // If the character is a high or low surrogate then generate an error
      // since it appears most JavaScript implementations have trouble handling
      // surrogate pairs and could cause encoding/decoding errors.
      else if (Character.isHighSurrogate(ch) || Character.isLowSurrogate(ch)) {
        throw new RuntimeException("Invalid string (contains surrogate pair)");
      }
    }
  }

  /**
   * Flushes the current buffer to the broadcast channel.
   */
  @Override
  public void flush() {
    if (packet_buffer.getSize() > 0) {
      broadcaster.broadcastMessage(packet_buffer.getProcessMessage());
      packet_buffer.clear();
    }
  }

  /**
   * Sends the packet if enough data has been put into the packet that it should
   * be flushed to the client.
   */
  public void flushIfFull(int size_estimate) {
    if (packet_buffer.getSize() + size_estimate > 40000) {
      flush();
    }
  }
  

  /**
   * Prints text with a specific code with a style to the broadcast channel.
   */
  public void printStyle(char packet_code, CharSequence text, CharSequence style) {
    if (text == null) throw new NullPointerException();

    CharSequence style_str = (style == null) ? "" : style;

    flushIfFull(1 + style_str.length() + 1 + text.length());

    packet_buffer.appendCode(packet_code);
    packet_buffer.appendNoneDelim(style_str);
    packet_buffer.appendDelimiter();
    packet_buffer.appendMessage(text);

  }

  /**
   * Prints text with a style and ends the line. Broadcasts to the broadcast
   * channel.
   */
  @Override
  public void println(Object text, String style) {
    if (text == null) {
      println("null", style);
    }
    else if (text instanceof CharSequence) {
      printStyle('!', (CharSequence) text, style);
    }
    else if (text instanceof InnerHTML) {
      printStyle('H', ((InnerHTML) text).getHtml(), style);
    }
    else {
      println(text.toString(), style);
    }
  }

  @Override
  public void println(Object text) {
    println(text, null);
  }

  /**
   * Prints text with a style. Broadcasts to the broadcast channel.
   */
  @Override
  public void print(Object text, String style) {
    if (text == null) {
      print("null", style);
    }
    else if (text instanceof CharSequence) {
      printStyle('+', (CharSequence) text, style);
    }
    else if (text instanceof InnerHTML) {
      printStyle('h', ((InnerHTML) text).getHtml(), style);
    }
    else {
      print(text.toString(), style);
    }
  }
  @Override
  public void print(Object text) {
    print(text, null);
  }

  /**
   * Prints an empty line.
   */
  @Override
  public void println() {
    printStyle('!', "", null);
  }

  /**
   * Sends a control code to the client (to set the prompt mode, for example).
   */
  public void sendControl(char control_char) {
    flushIfFull(1 + 1);

    packet_buffer.appendCode('$');
    packet_buffer.appendCode(control_char);

  }

  private void addStackTraceCause(
                    StringBuilder b, Throwable e, StackTraceElement[] parent,
                    int line_track) {

    if (e == null) {
      return;
    }

    // Find common stack elements between this stack trace and the parent
    StackTraceElement[] trace = e.getStackTrace();
    int m = trace.length - 1;
    int n = parent.length - 1;
    while (m >= 0 && n >=0 && trace[m].equals(parent[n])) {
      --m;
      --n;
    }
    int common_count = trace.length - m - 1;

    b.append("Caused by: ").append(e.toString()).append("\n");
    ++line_track;
    for (int i = 0; i <= m; ++i) {
      b.append("    at ").append(trace[i].toString()).append("\n");
      ++line_track;
      if (line_track > 350) {
        b.append("  [ Stack trace truncated ]\n");
        break;
      }
    }
    if (common_count != 0) {
      b.append("    ... ").append(common_count).append(" more\n");
      ++line_track;
    }

    // Recurse
    addStackTraceCause(b, e.getCause(), trace, line_track);
  }

  @Override
  public void printException(Throwable e) {

    // The maximum number of stack elements to show,
    int MAX_STACK_ELEMENT_SHOW = 250;

    // Format the stack trace dump,
    StringBuilder b = new StringBuilder();
    
//    // Handle special case,
//    if (e instanceof RemoteException) {
//      RemoteException re = (RemoteException) e;
//      String remote_stack_trace = re.getRemoteStackTrace();
//      b.append(remote_stack_trace);
//      b.append("  Remote exception was caused by;\n");
//    }

    StackTraceElement[] stack_trace = e.getStackTrace();
    b.append(e.toString()).append("\n");
    int sz = Math.min(stack_trace.length, MAX_STACK_ELEMENT_SHOW);
    for (int i = 0; i < sz; ++i) {
      b.append("    at ").append(stack_trace[i].toString()).append("\n");
    }
    if (sz < stack_trace.length) {
      b.append("     ( not showing ")
              .append(stack_trace.length - MAX_STACK_ELEMENT_SHOW)
              .append(" entries )")
              .append("\n");
    }
    addStackTraceCause(b, e.getCause(), stack_trace, sz);

    // Send an exception code,
    flushIfFull(1 + b.length() + 4);
    packet_buffer.appendCode('e');
    packet_buffer.appendMessage(b);

  }

  @Override
  public Writer asWriter(final String style) {
    return new IOWrapStyledPrintWriter.ReclassWriter(this, style);
  }

  @Override
  public Writer asWriter() {
    return new IOWrapStyledPrintWriter.ReclassWriter(this);
  }

  /**
   * Sents an interact-reply message back to the client.
   */
  void sendInteractReply(String reply) {
    if (reply == null) throw new NullPointerException();

    flushIfFull(1 + reply.length() + 4);
    
    packet_buffer.appendCode('t');
    packet_buffer.appendMessage(reply);

  }

  /**
   * Used to construct packets to send back to the JavaScript client. Performs
   * various validation checks on the packet to ensure it can't be built in a
   * way that causes the packet stream to be invalidated.
   * <p>
   * This will always build a valid UTF-8 string. If character codes are given
   * that can not be encoded to UTF-8 then an exception is generated.
   */
  public static class PacketBuilder {

    private final String broadcast_code;
    private final ByteArrayOutputStream out;
    private final BufferedWriter writer;
    private int size = 0;
    
    PacketBuilder(String broadcast_code, int size_guess) {
      checkValid(broadcast_code);
      this.broadcast_code = broadcast_code;
      size_guess = Math.max(8, size_guess);
      try {
        out = new ByteArrayOutputStream(Math.min(size_guess, 65536));
        writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.append(broadcast_code);
        writer.append('|');
      }
      catch (IOException e) {
        // Every JVM should support UTF-8
        throw new RuntimeException(e);
      }
    }

    /**
     * Returns the broadcast code.
     */
    public String getBroadcastCode() {
      return broadcast_code;
    }

    /**
     * Turns the packet into a ProcessMessage.
     */
    public ProcessMessage getProcessMessage() {
      try {
        writer.flush();
        return new ByteArrayProcessMessage(out.toByteArray());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Clears the builder.
     */
    public void clear() {
      try {
        writer.flush();
        out.reset();
        writer.append(broadcast_code);
        writer.append('|');
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Returns the current size of the buffer.
     */
    public int getSize() {
      return size;
    }

    /**
     * Appends a control code.
     */
    public void appendCode(char code) {
      if (code == '|' ||
          Character.isHighSurrogate(code) || Character.isLowSurrogate(code)) {
        throw new RuntimeException("Character code is invalid");
      }
      try {
        writer.append(code);
        ++size;
      }
      catch (IOException e) {
        // Shouldn't happen,
        throw new RuntimeException(e);
      }
    }

    /**
     * Appends a delimiter to the packet. The delimiter is character '|'.
     */
    public void appendDelimiter() {
      try {
        writer.append('|');
        ++size;
      }
      catch (IOException e) {
        // Shouldn't happen,
        throw new RuntimeException(e);
      }
    }

    /**
     * Appends a none-delimiter string to the packet. 
     */
    public void appendNoneDelim(CharSequence str) {
      checkValid(str);
      try {
        writer.append(str);
        size += str.length();
      }
      catch (IOException e) {
        // Shouldn't happen,
        throw new RuntimeException(e);
      }
    }

    /**
     * Appends a size/message entry to the packet. The message can be any
     * valid string.
     */
    public void appendMessage(CharSequence msg) {
      // Check the message for any surrogate pair codes. We don't allow these
      // because many JavaScript implementations don't handle them.

      // NOTE: This destructive changes the string by replacing surrogate
      //   pairs with '?' characters.

      StringBuilder sb = null;
      int sz = msg.length();
      for (int i = 0; i < sz; ++i) {
        char ch = msg.charAt(i);
        if (Character.isHighSurrogate(ch) || Character.isLowSurrogate(ch)) {
          // If we have a high or low surrogate then we replace the character
          // with '?'.
          if (sb == null) {
            sb = new StringBuilder();
            sb.append(msg.subSequence(0, i));
          }
          sb.append('?');
        }
        else if (sb != null) {
          sb.append(ch);
        }
      }
      // Either 'sb' is null and we can use 'msg', or we use 'sb',
      CharSequence str_encod = (sb == null) ? msg : sb;

      // Append the message,
      try {
        String msg_size_str = Integer.toString(str_encod.length());
        writer.append(msg_size_str);
        writer.append("|");
        writer.append(str_encod);
        size += (msg_size_str.length() + 1 + str_encod.length());
      }
      catch (IOException e) {
        // Shouldn't happen,
        throw new RuntimeException(e);
      }

    }

    /**
     * Appends a string directly into the packet builder without checking it
     * for validity. It is assumed this will only ever be used to pipe one
     * packet stream into another.
     */
    public void appendPacketBlock(String packet_block) {
      try {
        writer.append(packet_block);
        size += packet_block.length();
      }
      catch (IOException e) {
        // Shouldn't happen,
        throw new RuntimeException(e);
      }
    }

  }

  /**
   * Used to represent inner HTML objects.
   */
  public static class InnerHTML {

    private CharSequence html;

    public InnerHTML(CharSequence html) {
      this.html = html;
    }

    public CharSequence getHtml() {
      return html;
    }

    @Override
    public String toString() {
      return html.toString();
    }

  }

  /**
   * Public interface for broadcasting a message.
   */
  public static interface Broadcaster {

    void broadcastMessage(ProcessMessage msg);

  }

  /**
   * A Broadcast implementation on a process instance.
   */
  public static class PIBroadcaster implements Broadcaster {

    private final ProcessInstance instance;
    private final int channel_num;

    public PIBroadcaster(ProcessInstance instance, int channel_num) {
      this.instance = instance;
      this.channel_num = channel_num;
    }

    @Override
    public void broadcastMessage(ProcessMessage msg) {
      instance.broadcastMessage(channel_num, msg);
    }

  }

}
