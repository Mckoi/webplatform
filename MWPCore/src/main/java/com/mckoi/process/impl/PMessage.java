/**
 * com.mckoi.process.impl.PMessage  Mar 23, 2012
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

package com.mckoi.process.impl;

import com.mckoi.process.ByteArrayProcessMessage;
import com.mckoi.process.ProcessFunctionError;
import com.mckoi.process.ProcessId;
import com.mckoi.process.ProcessMessage;
import com.mckoi.util.ByteArrayUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * A raw inter-process message. The message has a 20 byte header followed
 * by a user defined message (usually a set of primitive objects representing
 * an arguments list).
 *
 * @author Tobias Downer
 */

public final class PMessage {

  /**
   * The message content.
   */
  private final byte[] buf;
  private int pos = 0;

  PMessage next;

  /**
   * Constructor.
   */
  PMessage(byte[] buf) {
    this.buf = buf;
  }

  /**
   * Writes the given ByteBuffer to the message's buffer at the current
   * position.
   */
  void write(ByteBuffer bb, int p, int len) {

    // Set the position,
    int rpos = bb.position();
    bb.position(p);
    // Write to the message buffer,
    bb.get(buf, pos, len);
    // Reset position
    bb.position(rpos);

    pos += len;
    if (pos > buf.length) {
      throw new RuntimeException("Write past end of message buffer");
    }
  }

  /**
   * The remaining data needed to fill this message.
   */
  int remainingToWrite() {
    return buf.length - pos;
  }

  /**
   * Returns a ByteBuffer that contains this message in its entirety.
   */
  ByteBuffer asByteBuffer() {
    return ByteBuffer.wrap(buf);
  }

  /**
   * The size of this message in bytes.
   */
  int sizeInBytes() {
    return buf.length;
  }

  /**
   * Returns this message as a com.mckoi.process.ProcessMessage implementation
   * from the given offset in this message.
   */
  ProcessMessage asProcessMessage(int offset) {
    return new ByteArrayProcessMessage(buf, offset, buf.length - offset);
  }

  /**
   * Decodes the message as an arguments list offset from the given position
   * in the message buffer.
   */
  Object[] asArgsList(int offset) {
    return ByteArrayProcessMessage.decodeArgsList(buf, offset);
  }

  /**
   * Encodes the given arg list as the body of this message.
   */
  static PMessage encodeArgsList(byte[] header, Object[] args) {
    byte[] b = ByteArrayProcessMessage.encodeArgsList(header, args);
    return new PMessage(b);
  }

  /**
   * Returns true if the process id and call id of this message matches the
   * given.
   */
  boolean matches(ProcessId pid, int call_id) {
    ByteBuffer bb = asByteBuffer();
    int msg_call_id = bb.getInt(16);

//    System.out.println(Long.toHexString(call_id));
//    System.out.println(Long.toHexString(msg_call_id));

    if (call_id != msg_call_id) {
      return false;
    }
//    System.out.println("CALL ID MATCH");
    byte pid_pval = pid.getPathValue();
    long pid_high = pid.getHighLong();
    long pid_low = pid.getLowLong();
    
    byte bb_pval = bb.get(8);
    long bb_high = bb.getLong(0) & 0x000FFFFFFFFFFFFFFL;
    long bb_low = bb.getLong(8) & 0x000FFFFFFFFFFFFFFL;

//    System.out.println(Long.toHexString(bb_high));
//    System.out.println(Long.toHexString(pid_high));

    // Must all match,
    return (pid_high == bb_high && pid_low == bb_low && pid_pval == bb_pval);
  }

  /**
   * Returns the command code of this message.
   */
  byte getCommandCode() {
    return buf[0];
  }

  /**
   * Returns the call id of this message.
   */
  int getCallId() {
    return ByteArrayUtil.getInt(buf, 16);
  }

  /**
   * Returns the sequence value if this is a broadcast message.
   */
  long getSequenceValue() {
    return ByteArrayUtil.getLong(buf, 20);
  }

  /**
   * Returns true if the given reply is a success message.
   */
  boolean isSuccessMessage() {
    ByteBuffer bb = asByteBuffer();
    // Success messages have the characters 'S' and ';' at 20 and 21
    byte c1 = bb.get(20);
    byte c2 = bb.get(21);
    if (c1 == 'S' && c2 == ';') {
      return true;
    }
    return false;
//    System.out.println(reply.getDebugString());
  }

  /**
   * Returns true if the given reply is a failure message.
   */
  boolean isFailMessage() {
    ByteBuffer bb = asByteBuffer();
    // Fail messages have the characters 'F' at 20
    byte c1 = bb.get(20);
    if (c1 == 'F') {
      return true;
    }
    return false;
  }

  /**
   * Returns an error from the given failure type.
   */
  ProcessFunctionError messageFailError() {
    ByteBuffer bb = asByteBuffer();
    byte fail_type = bb.get(21);
    // Failure is a remote exception,
    if (fail_type == 'e') {
      try {
        ObjectInputStream oi = new ObjectInputStream(
                new ByteArrayInputStream(buf, 22, buf.length - 22));

        // Deserialize the information for the error,
        String error_type = oi.readUTF();
        String eclass_name = (String) oi.readObject();
        String e_inmsg = (String) oi.readObject();
        String emsg = eclass_name + ((e_inmsg == null) ? "" : ": " + e_inmsg);
        
        StackTraceElement[] est = (StackTraceElement[]) oi.readObject();
        ProcessFunctionError err = new ProcessFunctionError(error_type, emsg);
        err.setStackTrace(est);
        return err;
      }
      catch (ClassNotFoundException e) {
        throw new PRuntimeException(e);
      }
      catch (IOException e) {
        throw new PRuntimeException(e);
      }
    }
    else if (fail_type == '\'') {
      String fail_message = getRemainingAsString(22);
      return new ProcessFunctionError("ERROR", fail_message);
    }
    // Unknown exception type,
    else {
      throw new PRuntimeException("Unknown fail type: " + fail_type);
    }
  }

  static ProcessId createProcessIdFromBuffer(ByteBuffer bb) {
    byte bb_pval = bb.get(8);
    long bb_high = bb.getLong(0) & 0x000FFFFFFFFFFFFFFL;
    long bb_low = bb.getLong(8) & 0x000FFFFFFFFFFFFFFL;

    return new ProcessId(bb_pval, bb_high, bb_low);
  }

  /**
   * Returns the process id of this message.
   */
  ProcessId getProcessId() {
    return createProcessIdFromBuffer(asByteBuffer());
  }

  /**
   * Returns the part of the message between 'pos' and the end as a string
   * (encoded as UTF-8).
   */
  String getRemainingAsString(int pos) {
    try {
      return new String(buf, pos, buf.length - pos, "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      throw new PRuntimeException(ex);
    }
  }

  /**
   * Returns a string for debugging purposes.
   */
  String getDebugString() {
    StringBuilder b = new StringBuilder();
    b.append("Size: ");
    b.append(buf.length);
    b.append("\n");

    b.append("[ ");
    for (byte bv : buf) {
      String v = Integer.toHexString(((int) bv) & 0x0FF);
      if (v.length() == 1) {
        b.append('0');
      }
      b.append(v);
      b.append(" ");
    }
    b.append("]\n");
    
    b.append("[ ");
    for (byte bv : buf) {
      if (bv >= 33 && bv < 127) {
        b.append((char) bv);
      }
      else {
        b.append('.');
      }
    }
    b.append(" ]\n");
    
    return b.toString();
  }

}
