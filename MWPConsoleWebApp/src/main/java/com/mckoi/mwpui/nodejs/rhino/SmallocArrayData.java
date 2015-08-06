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

package com.mckoi.mwpui.nodejs.rhino;

import java.nio.ByteBuffer;
import org.mozilla.javascript.ExternalArrayData;
import org.mozilla.javascript.ScriptRuntime;

/**
 * A Rhino external array data object wrapped over a ByteBuffer.
 *
 * @author Tobias Downer
 */
public class SmallocArrayData implements ExternalArrayData {
  
  private final ByteBuffer byte_buffer;

  public SmallocArrayData(int alloc_size) {
    this.byte_buffer = ByteBuffer.allocate(alloc_size);
  }

  public void truncate(int length) {
    if (length <= byte_buffer.limit() && length >= 0) {
      byte_buffer.limit(length);
    }
    else {
      throw new IndexOutOfBoundsException();
    }
  }

  @Override
  public Object getArrayElement(int index) {
    byte b = byte_buffer.get(index);
    return ((int) b) & 0x0FF;
  }

  @Override
  public void setArrayElement(int index, Object ob) {
    byte_buffer.put(index, (byte) ScriptRuntime.toInt32(ob));
  }

  @Override
  public int getArrayLength() {
    return byte_buffer.limit();
  }

  /**
   * Returns the RAW byte array of this object. Note that 'byte_array.length'
   * should not be used to determine the end of this array. Use 'getArrayLength'
   * instead to work out how much of this array can be read.
   * 
   * @return 
   */
  public byte[] getByteArray() {
    return byte_buffer.array();
  }

  /**
   * Returns the RAW byte data as a ByteBuffer with the limit set to the length
   * of the array. To truncate this array, simply change the limit of the
   * returned byte buffer.
   * 
   * @return 
   */
  public ByteBuffer getByteBuffer() {
    return byte_buffer;
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    final SmallocArrayData other = (SmallocArrayData) obj;
    byte_buffer.position(0);
    other.byte_buffer.position(0);
    return byte_buffer.equals(other.byte_buffer);
  }


  public void fill(Object val, int start, int end) {
    byte b = (byte) ScriptRuntime.toInt32(val);
    int cur_limit = byte_buffer.limit();
    byte_buffer.position(start);
    byte_buffer.limit(end);
    ByteBuffer bw = byte_buffer.slice();
    while (bw.hasRemaining()) {
      bw.put(b);
    }
    // Reset position and limit
    byte_buffer.position(0);
    byte_buffer.limit(cur_limit);
  }
  
}
