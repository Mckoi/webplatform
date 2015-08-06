/**
 * com.mckoi.process.impl.ByteArrayProcessMessage  Mar 30, 2012
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

package com.mckoi.process;

import java.io.*;

/**
 * An implementation of ProcessMessage using a materialized Java byte[] array.
 * This class provides a simple mechanism for serializing and deserializing
 * basic Java primitives in a ProcessMessage.
 *
 * @author Tobias Downer
 */

public final class ByteArrayProcessMessage implements ProcessMessage {

  private final byte[] buf;
  private final int offset;
  private final int length;

  /**
   * Constructor.
   */
  public ByteArrayProcessMessage(byte[] buf, int offset, int length) {
    this.buf = buf;
    this.offset = offset;
    this.length = length;
  }

  /**
   * Constructor.
   */
  public ByteArrayProcessMessage(byte[] buf) {
    this(buf, 0, buf.length);
  }

  private static Object decodeArg(DataInputStream din) throws IOException {
    byte type = din.readByte(); // The type,
    if (type == 0) {
      return null;
    }
    else if (type == 1) {
      return din.readInt();
    }
    else if (type == 2) {
      return din.readLong();
    }
    else if (type == 3) {
      return din.readUTF();
    }
    else {
      throw new RuntimeException("Unknown type: " + type);
    }
  }

  /**
   * Decodes the message as an arguments list offset from the given position
   * in the message buffer.
   */
  public static Object[] decodeArgsList(byte[] buf, int offset) {
    try {
      ByteArrayInputStream bin =
                   new ByteArrayInputStream(buf, offset, buf.length - offset);
      DataInputStream din = new DataInputStream(bin);
      int arg_list_size = ((int) din.readShort()) & 0x0FFFF;
      Object[] args = new Object[arg_list_size];
      for (int i = 0; i < arg_list_size; ++i) {
        args[i] = decodeArg(din);
      }
      return args;
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
  }

  /**
   * Decodes the arguments in a ProcessMessage using the 'decodeArgsList'
   * method.
   */
  public static Object[] decodeArgsList(ProcessMessage message, int offset) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream(message.size());
    try {
      message.writeTo(bout);
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
    return decodeArgsList(bout.toByteArray(), offset);
  }

  /**
   * Decodes the arguments in a ProcessMessage using the 'decodeArgsList'
   * method. This is the same as 'decodeArgsList(message, 0)'.
   */
  public static Object[] decodeArgsList(ProcessMessage message) {
    return decodeArgsList(message, 0);
  }

  /**
   * Decodes the message as an arguments list offset from the given position
   * in the message buffer.
   */
  public static String[] decodeStringArgsList(byte[] buf, int offset) {
    try {
      ByteArrayInputStream bin =
                   new ByteArrayInputStream(buf, offset, buf.length - offset);
      DataInputStream din = new DataInputStream(bin);
      int arg_list_size = ((int) din.readShort()) & 0x0FFFF;
      String[] args = new String[arg_list_size];
      for (int i = 0; i < arg_list_size; ++i) {
        args[i] = (String) decodeArg(din);
      }
      return args;
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
  }

  /**
   * Decodes the arguments in a ProcessMessage using the
   * 'decodeStringArgsList' method.
   */
  public static String[] decodeStringArgsList(
                                        ProcessMessage message, int offset) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream(message.size());
    try {
      message.writeTo(bout);
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
    return decodeStringArgsList(bout.toByteArray(), offset);
  }

  public static String[] decodeStringArgsList(ProcessMessage message) {
    return decodeStringArgsList(message, 0);
  }

  /**
   * Encodes the given arg list into a byte buffer message.
   */
  public static byte[] encodeArgsList(byte[] header, Object[] args) {
    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream(512);
      DataOutputStream dout = new DataOutputStream(bout);
      // Write the header,
      if (header != null) {
        dout.write(header);
      }
      // The number of args,
      dout.writeShort((short) args.length);
      for (Object arg : args) {
        if (arg == null) {
          dout.writeByte(0);
        }
        else if (arg instanceof Integer) {
          dout.writeByte(1);
          dout.writeInt((Integer) arg);
        }
        else if (arg instanceof Long) {
          dout.writeByte(2);
          dout.writeLong((Long) arg);
        }
        else if (arg instanceof String) {
          dout.writeByte(3);
          dout.writeUTF((String) arg);
        }
        else {
          throw new RuntimeException(
                  "Unknown object class: " + arg.getClass() + " '" + arg.toString() + "'");
        }
      }
      dout.flush();
      // Return it as a PMessage,
      byte[] b = bout.toByteArray();
      return b;
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a ByteArrayProcessMessage object that has the given arguments encoded
   * into it.
   */
  public static ByteArrayProcessMessage encodeArgs(Object... args) {
    byte[] b = encodeArgsList(null, args);
    return new ByteArrayProcessMessage(b);
  }

//  /**
//   * Decodes the arguments encoded in the message byte buffer.
//   */
//  public Object[] decodeArgs() {
//    return decodeArgsList(buf, 0);
//  }

  // ----- Implemented from ProcessMessage -----

  @Override
  public int size() {
    return length;
  }

  @Override
  public InputStream getMessageStream() {
    return new ByteArrayInputStream(buf, offset, length);
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    out.write(buf, offset, length);
  }

  // ----- Static -----
  
  /**
   * Returns an empty (null) ProcessMessage.
   */
  public static ProcessMessage emptyMessage() {
    return EMPTY_MESSAGE;
  }

  /**
   * Same as 'emptyMessage()'.
   */
  public static ProcessMessage nullMessage() {
    return emptyMessage();
  }

  /**
   * An empty input stream.
   */
  private final static InputStream EMPTY_INPUT_STREAM =
                                         new ByteArrayInputStream(new byte[0]);

  /**
   * An implementation of ProcessMessage that contains nothing.
   */
  private final static ProcessMessage EMPTY_MESSAGE = new ProcessMessage() {
    @Override
    public int size() {
      return 0;
    }
    @Override
    public InputStream getMessageStream() {
      return EMPTY_INPUT_STREAM;
    }
    @Override
    public void writeTo(OutputStream out) throws IOException {
      // Nothing to write,
    }
  };
  
}
