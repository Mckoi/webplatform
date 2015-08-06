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

package com.mckoi.mwpui.nodejs.rhino.deprec;

import com.mckoi.appcore.utils.ABase64;
import com.mckoi.mwpui.apihelper.TextUtils;
import com.mckoi.mwpui.nodejs.rhino.SmallocArrayData;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import org.mozilla.javascript.*;

/**
 *
 * @author Tobias Downer
 */
public class BindingBuffer extends NativeObject {

  @Override
  public String getClassName() {
    return "buffer";
  }

  public BindingBuffer init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Object);
    int ro_attr = PERMANENT | READONLY;

    // Define the function invoke callback function,
    defineProperty("setupBufferJS",
                   new NodeSetupBufferJS().init(scope), ro_attr);

    return this;
  }

  // -----

  public static class NodeSetupBufferJS extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("setupBufferJS");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      ScriptableObject buffer_ob = (ScriptableObject) args[0];
      ScriptableObject internal_ob = (ScriptableObject) args[1];

      int attrs = DONTENUM | READONLY;

      // --- Public functions added to 'buffer'
      
      
      ScriptableObject buf_prototype =
                      (ScriptableObject) buffer_ob.get("prototype", buffer_ob);

      buf_prototype.defineProperty("hexSlice", new FhexSlice().init(scope), attrs);
      buf_prototype.defineProperty("utf8Slice", new Futf8Slice().init(scope), attrs);
      buf_prototype.defineProperty("asciiSlice", new FasciiSlice().init(scope), attrs);
      buf_prototype.defineProperty("binarySlice", new FbinarySlice().init(scope), attrs);
      buf_prototype.defineProperty("base64Slice", new Fbase64Slice().init(scope), attrs);
      buf_prototype.defineProperty("ucs2Slice", new Fucs2Slice().init(scope), attrs);

      buf_prototype.defineProperty("hexWrite", new FhexWrite().init(scope), attrs);
      buf_prototype.defineProperty("utf8Write", new Futf8Write().init(scope), attrs);
      buf_prototype.defineProperty("asciiWrite", new FasciiWrite().init(scope), attrs);
      buf_prototype.defineProperty("binaryWrite", new FbinaryWrite().init(scope), attrs);
      buf_prototype.defineProperty("base64Write", new Fbase64Write().init(scope), attrs);
      buf_prototype.defineProperty("ucs2Write", new Fucs2Write().init(scope), attrs);

      // --- Public functions added to 'internal'

      // (buffer, buffer)
      // Compares buffers, returns true if the same
      internal_ob.defineProperty("compare", new Icompare().init(scope), attrs);
      // (str, enc)
      // Calculates the byte length of the given string (eg. byte length of
      //   utf8 encoding)
      internal_ob.defineProperty("byteLength", new IbyteLength().init(scope), attrs);
      // (buffer, val, start, end)
      // Fills the buffer with the given value between start and end.
      internal_ob.defineProperty("fill", new Ifill().init(scope), attrs);
      // (buffer, off)
      // float encoding/decoding methods,
      internal_ob.defineProperty("readFloatLE", new IreadFloatLE().init(scope), attrs);
      internal_ob.defineProperty("readFloatBE", new IreadFloatBE().init(scope), attrs);
      internal_ob.defineProperty("readDoubleLE", new IreadDoubleLE().init(scope), attrs);
      internal_ob.defineProperty("readDoubleBE", new IreadDoubleBE().init(scope), attrs);
      internal_ob.defineProperty("writeFloatLE", new IwriteFloatLE().init(scope), attrs);
      internal_ob.defineProperty("writeFloatBE", new IwriteFloatBE().init(scope), attrs);
      internal_ob.defineProperty("writeDoubleLE", new IwriteDoubleLE().init(scope), attrs);
      internal_ob.defineProperty("writeDoubleBE", new IwriteDoubleBE().init(scope), attrs);
      
      
      return Undefined.instance;
    }

  }

  // -----
  // BUFFER FUNCTIONS
  // -----

  // *** STRING DECODING ***

  public static class FhexSlice extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("hexSlice");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      int start = ScriptRuntime.toInt32(args[0]);
      int end = ScriptRuntime.toInt32(args[1]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      // Encode has hex (lower case)
      int len = (end - start);
      StringBuilder b = new StringBuilder(len * 2);
      for (int i = start; i < end; ++i) {
        b.append(Integer.toHexString(((int) byte_arr[i]) & 0x0FF));
      }
      return b.toString();

    }

  }
  
  public static class Futf8Slice extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("utf8Slice");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      int start = ScriptRuntime.toInt32(args[0]);
      int end = ScriptRuntime.toInt32(args[1]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      try {
        return new String(byte_arr, start, end - start, "UTF-8");
      }
      catch (UnsupportedEncodingException ex) {
        // Shouldn't ever happen,
        throw new RuntimeException(ex);
      }

    }

  }
  
  public static class FasciiSlice extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("asciiSlice");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      int start = ScriptRuntime.toInt32(args[0]);
      int end = ScriptRuntime.toInt32(args[1]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      try {
        return new String(byte_arr, start, end - start, "US-ASCII");
      }
      catch (UnsupportedEncodingException ex) {
        // Shouldn't ever happen,
        throw new RuntimeException(ex);
      }

    }

  }
  
  public static class FbinarySlice extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("binarySlice");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // Not supported - is being deprecated in node anyway,
      throw new UnsupportedOperationException();

    }

  }
  
  public static class Fbase64Slice extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("base64Slice");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      int start = ScriptRuntime.toInt32(args[0]);
      int end = ScriptRuntime.toInt32(args[1]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      // Encode to string,
      return ABase64.encodeToString(
                            byte_arr, start, end - start, ABase64.NO_WRAP);

    }

  }
  
  public static class Fucs2Slice extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("ucs2Slice");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      int start = ScriptRuntime.toInt32(args[0]);
      int end = ScriptRuntime.toInt32(args[1]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      try {
        return new String(byte_arr, start, end - start, "UTF-16LE");
      }
      catch (UnsupportedEncodingException ex) {
        // Shouldn't ever happen,
        throw new RuntimeException(ex);
      }

    }

  }

  // *** STRING ENCODING ***

  public static class FhexWrite extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("hexWrite");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String str = args[0].toString();
      int offset = ScriptRuntime.toInt32(args[1]);
      int length = ScriptRuntime.toInt32(args[2]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

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

  }
  
  public static class Futf8Write extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("utf8Write");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String str = args[0].toString();
      int offset = ScriptRuntime.toInt32(args[1]);
      int length = ScriptRuntime.toInt32(args[2]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      // PENDING: Attach encoder to the context thread?
      CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
      ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
      encoder.encode(CharBuffer.wrap(str), bb, true);

      return length - bb.remaining();

    }

  }
  
  public static class FasciiWrite extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("asciiWrite");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String str = args[0].toString();
      int offset = ScriptRuntime.toInt32(args[1]);
      int length = ScriptRuntime.toInt32(args[2]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      // PENDING: Attach encoder to the context thread?
      CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
      ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
      encoder.encode(CharBuffer.wrap(str), bb, true);

      return length - bb.remaining();

    }

  }
  
  public static class FbinaryWrite extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("binaryWrite");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // Not supported - is being deprecated in node anyway,
      throw new UnsupportedOperationException();

    }

  }
  
  public static class Fbase64Write extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("base64Write");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String str = args[0].toString();
      int offset = ScriptRuntime.toInt32(args[1]);
      int length = ScriptRuntime.toInt32(args[2]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

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

  }
  
  public static class Fucs2Write extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("ucs2Write");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      String str = args[0].toString();
      int offset = ScriptRuntime.toInt32(args[1]);
      int length = ScriptRuntime.toInt32(args[2]);
      SmallocArrayData data = (SmallocArrayData) ((ScriptableObject) thisObj).getExternalArrayData();
      byte[] byte_arr = data.getByteArray();

      // PENDING: Attach encoder to the context thread?
      CharsetEncoder encoder = Charset.forName("UTF-16LE").newEncoder();
      ByteBuffer bb = ByteBuffer.wrap(byte_arr, offset, length);
      encoder.encode(CharBuffer.wrap(str), bb, true);

      return length - bb.remaining();

    }

  }

  // -----
  // INTERNAL FUNCTIONS
  // -----
  
  public static class Icompare extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("compare");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      ScriptableObject v1 = (ScriptableObject) args[0];
      ScriptableObject v2 = (ScriptableObject) args[1];
      return v1.getExternalArrayData().equals(v2.getExternalArrayData());
      
    }

  }

  public static class IbyteLength extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("byteLength");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

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

  }

  public static class Ifill extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("fill");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      ScriptableObject buf = (ScriptableObject) args[0];
      Object val = args[1];
      int start = ScriptRuntime.toInt32(args[2]);
      int end = ScriptRuntime.toInt32(args[3]);

      SmallocArrayData data = (SmallocArrayData) buf.getExternalArrayData();
      data.fill(val, start, end);

      return true;

    }

  }

  public static class IreadFloatLE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("readFloatLE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IreadFloatBE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("readFloatBE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IreadDoubleLE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("readDoubleLE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IreadDoubleBE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("readDoubleBE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IwriteFloatLE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("writeFloatLE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IwriteFloatBE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("writeFloatBE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IwriteDoubleLE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("writeDoubleLE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }

  public static class IwriteDoubleBE extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("writeDoubleBE");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      // TODO
      throw new UnsupportedOperationException();
    }

  }




}