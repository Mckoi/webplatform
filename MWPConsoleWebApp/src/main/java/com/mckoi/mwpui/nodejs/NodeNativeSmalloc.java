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

import java.nio.ByteBuffer;

/**
 *
 * @author Tobias Downer
 */
public class NodeNativeSmalloc {

  public static Object alloc(Object thiz, Object... args) {
    GJSObject source = (GJSObject) args[0];
    int alloc_size = GJSRuntime.toInt32(args[1]);
    GJSRuntime.system().allocateExternalArrayDataOf(source, alloc_size);
    return source;
  }

  public static Object truncate(Object thiz, Object... args) {
    GJSObject source = (GJSObject) args[0];
    int length = GJSRuntime.toInt32(args[1]);

    // Get the ByteBuffer and set the limit.
    ByteBuffer bb = GJSRuntime.system().getExternalArrayDataOf(source);
    int cur_limit = bb.limit();
    if (length < cur_limit && length >= 0) {
      bb.limit(length);
    }
    
    return length;
  }

  public static Object sliceOnto(Object thiz, Object... args) {
    GJSObject source = (GJSObject) args[0];
    GJSObject dest = (GJSObject) args[1];
    int start = GJSRuntime.toInt32(args[2]);
    int end = GJSRuntime.toInt32(args[3]);

    ByteBuffer sourceArrayData =
            GJSRuntime.system().getExternalArrayDataOf(source);
    ByteBuffer destArrayData =
            GJSRuntime.system().getExternalArrayDataOf(dest);

    int len = end - start;

    if (destArrayData != null) {
      throw new GJavaScriptException("Expecting destination to not have a buffer");
    }

    destArrayData =
                GJSRuntime.system().allocateExternalArrayDataOf(dest, len);

    byte[] src_bytes = sourceArrayData.array();
    byte[] dest_bytes = destArrayData.array();

    System.arraycopy(src_bytes, start, dest_bytes, 0, len);

    return source;
  }

}
