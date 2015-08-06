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

package com.mckoi.mwpui.nodejs;

import com.mckoi.appcore.utils.ABase64;
import com.mckoi.mwpui.apihelper.TextUtils;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 *
 * @author Tobias Downer
 */
public class NodeNativeBuffer {

  public void setWrappedFunction(String name, GJSObject dest)
                                              throws NoSuchMethodException {
    GJSRuntime.setWrappedFunction(this, name, dest);
  }
  
  public Object setupBufferJS(Object thiz, Object... args) {

    GJSObject buffer_ob = (GJSObject) args[0];
    GJSObject internal_ob = (GJSObject) args[1];

    // --- Public functions added to 'buffer'

//    System.out.println("setupBufferJS");
//    System.out.println("this = " + hashCode());
//    System.out.println("native = " + internalGetNative().hashCode());
//    new Error().printStackTrace(System.err);

    GJSObject buf_prototype = buffer_ob.getPrototype();

    try {
      setWrappedFunction("hexSlice", buf_prototype);
      setWrappedFunction("utf8Slice", buf_prototype);
      setWrappedFunction("asciiSlice", buf_prototype);
      setWrappedFunction("binarySlice", buf_prototype);
      setWrappedFunction("base64Slice", buf_prototype);
      setWrappedFunction("ucs2Slice", buf_prototype);

      setWrappedFunction("hexWrite", buf_prototype);
      setWrappedFunction("utf8Write", buf_prototype);
      setWrappedFunction("asciiWrite", buf_prototype);
      setWrappedFunction("binaryWrite", buf_prototype);
      setWrappedFunction("base64Write", buf_prototype);
      setWrappedFunction("ucs2Write", buf_prototype);

      // --- Public functions added to 'internal'

      // (buffer, buffer)
      // Compares buffers, returns true if the same
      setWrappedFunction("compare", internal_ob);
      // (str, enc)
      // Calculates the byte length of the given string (eg. byte length of
      //   utf8 encoding)
      setWrappedFunction("byteLength", internal_ob);
      // (buffer, val, start, end)
      // Fills the buffer with the given value between start and end.
      setWrappedFunction("fill", internal_ob);
      // (buffer, off)
      // float encoding/decoding methods,
      setWrappedFunction("readFloatLE", internal_ob);
      setWrappedFunction("readFloatBE", internal_ob);
      setWrappedFunction("readDoubleLE", internal_ob);
      setWrappedFunction("readDoubleBE", internal_ob);
      setWrappedFunction("writeFloatLE", internal_ob);
      setWrappedFunction("writeFloatBE", internal_ob);
      setWrappedFunction("writeDoubleLE", internal_ob);
      setWrappedFunction("writeDoubleBE", internal_ob);

      return GJSStatics.UNDEFINED;

    }
    catch (NoSuchMethodException ex) {
      throw new GJavaScriptException(ex);
    }

  }

  public Object hexSlice(Object thiz, Object... args) {
    int start = GJSRuntime.toInt32(args[0]);
    int end = GJSRuntime.toInt32(args[1]);
    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bb.array();

    // Encode has hex (lower case)
    int len = (end - start);
    StringBuilder b = new StringBuilder(len * 2);
    for (int i = start; i < end; ++i) {
      b.append(Integer.toHexString(((int) byte_arr[i]) & 0x0FF));
    }
    return b.toString();
  }
  
  public Object utf8Slice(Object thiz, Object... args) {
    int start = GJSRuntime.toInt32(args[0]);
    int end = GJSRuntime.toInt32(args[1]);
    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bb.array();

    try {
      return new String(byte_arr, start, end - start, "UTF-8");
    }
    catch (UnsupportedEncodingException ex) {
      // Shouldn't ever happen,
      throw new RuntimeException(ex);
    }
  }

  public Object asciiSlice(Object thiz, Object... args) {
    int start = GJSRuntime.toInt32(args[0]);
    int end = GJSRuntime.toInt32(args[1]);
    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bb.array();

    try {
      return new String(byte_arr, start, end - start, "US-ASCII");
    }
    catch (UnsupportedEncodingException ex) {
      // Shouldn't ever happen,
      throw new RuntimeException(ex);
    }
  }

  public Object binarySlice(Object thiz, Object... args) {
    // Not supported - is being deprecated in node anyway,
    throw new UnsupportedOperationException();
  }

  public Object base64Slice(Object thiz, Object... args) {
    int start = GJSRuntime.toInt32(args[0]);
    int end = GJSRuntime.toInt32(args[1]);
    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bb.array();

    // Encode to string,
    return ABase64.encodeToString(
                          byte_arr, start, end - start, ABase64.NO_WRAP);
  }

  public Object ucs2Slice(Object thiz, Object... args) {
    int start = GJSRuntime.toInt32(args[0]);
    int end = GJSRuntime.toInt32(args[1]);
    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bb.array();

    try {
      return new String(byte_arr, start, end - start, "UTF-16LE");
    }
    catch (UnsupportedEncodingException ex) {
      // Shouldn't ever happen,
      throw new RuntimeException(ex);
    }
  }

  public Object hexWrite(Object thiz, Object... args) {
    String str = args[0].toString();
    int offset = GJSRuntime.toInt32(args[1]);
    int length = GJSRuntime.toInt32(args[2]);
    ByteBuffer bbz = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bbz.array();

    ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
    // Encode the hex string,
    int len = str.length() & 0x07FFFFFFE;
    for (int i = 0; i < len && bb.hasRemaining(); i += 2) {
      char h1 = str.charAt(i);
      char h2 = str.charAt(i + 1);
      int highv = TextUtils.hexCharacterToValue(h1);
      int lowv = TextUtils.hexCharacterToValue(h2);
      bb.put((byte) ((highv * 16) + lowv));
    }

    return length - bb.remaining();
  }

  public Object utf8Write(Object thiz, Object... args) {
    String str = args[0].toString();
    int offset = GJSRuntime.toInt32(args[1]);
    int length = GJSRuntime.toInt32(args[2]);
    ByteBuffer bbz = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bbz.array();

    // PENDING: Attach encoder to the context thread?
    CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
    ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
    encoder.encode(CharBuffer.wrap(str), bb, true);

    return length - bb.remaining();
  }

  public Object asciiWrite(Object thiz, Object... args) {
    String str = args[0].toString();
    int offset = GJSRuntime.toInt32(args[1]);
    int length = GJSRuntime.toInt32(args[2]);
    ByteBuffer bbz = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bbz.array();

    // PENDING: Attach encoder to the context thread?
    CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
    encoder.encode(CharBuffer.wrap(str), bb, true);

    return length - bb.remaining();
  }
  
  public Object binaryWrite(Object thiz, Object... args) {
    // Not supported - is being deprecated in node anyway,
    throw new UnsupportedOperationException();
  }

  public Object base64Write(Object thiz, Object... args) {
    String str = args[0].toString();
    int offset = GJSRuntime.toInt32(args[1]);
    int length = GJSRuntime.toInt32(args[2]);
    ByteBuffer bbz = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bbz.array();

    byte[] result;
    try {
      // NOTE: There's lots of array copies here. We encode the string
      //   as US-ASCII byte[] array, then decode it into another byte array,
      //   then copy the result of that into the destination array.

      result = ABase64.decode(str.getBytes("US-ASCII"), ABase64.DEFAULT);

      int copy_len = Math.min(length, result.length);
      System.arraycopy(result, 0, byte_arr, offset, copy_len);
      return copy_len;

    }
    catch (UnsupportedEncodingException ex) {
      // Shouldn't happen
      throw new RuntimeException(ex);
    }
  }

  public Object ucs2Write(Object thiz, Object... args) {
    String str = args[0].toString();
    int offset = GJSRuntime.toInt32(args[1]);
    int length = GJSRuntime.toInt32(args[2]);
    ByteBuffer bbz = GJSRuntime.system().getExternalArrayDataOf((GJSObject) thiz);
    byte[] byte_arr = bbz.array();

    // PENDING: Attach encoder to the context thread?
    CharsetEncoder encoder = Charset.forName("UTF-16LE").newEncoder();
    ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
    encoder.encode(CharBuffer.wrap(str), bb, true);

    return length - bb.remaining();
  }

  public Object compare(Object thiz, Object... args) {
    GJSObject v1 = (GJSObject) args[0];
    GJSObject v2 = (GJSObject) args[1];

    ByteBuffer bb1 = GJSRuntime.system().getExternalArrayDataOf(v1);
    ByteBuffer bb2 = GJSRuntime.system().getExternalArrayDataOf(v2);
    bb1.position(0);
    bb2.position(0);
    return bb1.equals(bb2);
  }

  public Object byteLength(Object thiz, Object... args) {
    String str = args[0].toString();
    String enc = args[1].toString();

    if (enc.equals("utf8") || enc.equals("utf-8")) {
      return calcUtf8Length(str);
    }
    else if (enc.equals("base64")) {
      return str.length() * 3 / 4;
    }
    else {
      // TODO
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Calculate the byte length of string encoded as UTF8.
   * 
   * @param sequence
   * @return 
   */
  public static int calcUtf8Length(CharSequence sequence) {
    int count = 0;
    for (int i = 0, len = sequence.length(); i < len; i++) {
      char ch = sequence.charAt(i);
      if (ch <= 0x7F) {
        count++;
      } else if (ch <= 0x7FF) {
        count += 2;
      } else if (Character.isHighSurrogate(ch)) {
        count += 4;
        ++i;
      } else {
        count += 3;
      }
    }
    return count;
  }


  public Object fill(Object thiz, Object... args) {
    GJSObject buf = (GJSObject) args[0];
    Object val = args[1];
    int start = GJSRuntime.toInt32(args[2]);
    int end = GJSRuntime.toInt32(args[3]);

    byte v = (byte) GJSRuntime.toInt32(val);

    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf(buf);
    int rlimit = bb.limit();
    try {
      // Set position and limit to the area being filled,
      bb.position(start).limit(end);
      // Fill
      while (bb.hasRemaining()) {
        bb.put(v);
      }
    }
    finally {
      // Reset limit on bb
      bb.position(0).limit(rlimit);
    }

    return true;
  }

  public Object readFloatLE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object readFloatBE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object readDoubleLE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object readDoubleBE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object writeFloatLE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object writeFloatBE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object writeDoubleLE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

  public Object writeDoubleBE(Object thiz, Object... args) {
    // TODO
    throw new UnsupportedOperationException();
  }

}
