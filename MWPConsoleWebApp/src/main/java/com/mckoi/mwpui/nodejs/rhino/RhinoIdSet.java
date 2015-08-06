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

import java.util.AbstractSet;
import java.util.Iterator;

/**
 *
 * @author Tobias Downer
 */
public class RhinoIdSet extends AbstractSet<String> {

  private final Object[] arr;

  public RhinoIdSet(Object[] arr) {
    this.arr = arr;
  }

  @Override
  public Iterator<String> iterator() {
    return new Iterator<String>() {

      int index = 0;
      
      @Override
      public boolean hasNext() {
        return index < arr.length;
      }
      @Override
      public String next() {
        Object v = arr[index];
        ++index;
        return v == null ? null : v.toString();
      }
      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
      
    };
  }

  @Override
  public int size() {
    return arr.length;
  }
  
}
