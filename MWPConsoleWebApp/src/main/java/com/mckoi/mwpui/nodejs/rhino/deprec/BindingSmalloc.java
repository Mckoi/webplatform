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

import com.mckoi.mwpui.nodejs.rhino.SmallocArrayData;
import org.mozilla.javascript.*;

/**
 * 
 *
 * @author Tobias Downer
 */
public class BindingSmalloc extends NativeObject {

  @Override
  public String getClassName() {
    return "buffer";
  }

  public BindingSmalloc init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Object);
    int ro_attr = PERMANENT | READONLY;

    // Define the function invoke callback function,
    defineProperty("alloc",
                     new AllocFun().init(this), ro_attr);
    defineProperty("truncate",
                     new TruncateFun().init(this), ro_attr);
    defineProperty("sliceOnto",
                     new SliceOntoFun().init(this), ro_attr);
    
    // The maximum buffer size allowed,
    defineProperty("kMaxLength", Context.javaToJS(256 * 1024, scope), ro_attr);

    return this;
  }

  // -----

  public static class AllocFun extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("alloc");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {
      
      ScriptableObject source = (ScriptableObject) args[0];
      int alloc_size = ScriptRuntime.toInt32(args[1]);
      
      source.setExternalArrayData(new SmallocArrayData(alloc_size));
      
      return source;
      
    }

  }

  public static class TruncateFun extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("truncate");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      ScriptableObject source = (ScriptableObject) args[0];
      int length = ScriptRuntime.toInt32(args[1]);

      SmallocArrayData array_data =
                              (SmallocArrayData) source.getExternalArrayData();
      
//      System.out.println("truncating, data of size " + array_data.getArrayLength() + " to " + length);
      
      array_data.truncate(length);
      return length;
      
//      throw Context.throwAsScriptRuntimeEx(
//                            new RuntimeException("truncate not implemented"));

    }

  }

  public static class SliceOntoFun extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("sliceOnto");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {

      ScriptableObject source = (ScriptableObject) args[0];
      ScriptableObject dest = (ScriptableObject) args[1];
      int start = ScriptRuntime.toInt32(args[2]);
      int end = ScriptRuntime.toInt32(args[3]);
      
      SmallocArrayData sourceArrayData =
                            (SmallocArrayData) source.getExternalArrayData();
      SmallocArrayData destArrayData =
                            (SmallocArrayData) dest.getExternalArrayData();

      int len = end - start;
      
      if (destArrayData != null) {
        throw Context.reportRuntimeError("Expecting destination to not have a buffer");
      }

      destArrayData = new SmallocArrayData(len);
      dest.setExternalArrayData(destArrayData);
      
      byte[] src_bytes = sourceArrayData.getByteArray();
      byte[] dest_bytes = destArrayData.getByteArray();

      System.arraycopy(src_bytes, start, dest_bytes, 0, len);

      return source;
    }

  }

}