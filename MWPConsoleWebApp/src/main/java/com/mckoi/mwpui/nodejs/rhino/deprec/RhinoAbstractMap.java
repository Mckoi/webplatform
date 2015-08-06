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

import java.util.HashSet;
import java.util.Set;
import org.mozilla.javascript.*;

/**
 * An object that can be exported as a JavaScript object in Rhino, that
 * implements a simple property map.
 * <pre>
 *   RhinoAbstractMap map = new MapImpl().init(scope);
 * </pre>
 * Implementations require 'getKey' and 'hasKey' to be implemented at minimum.
 * 'putKeyValue' to update the map.
 * <p>
 * When 'map['k1']' operations are performed, this object will check first
 * with the NativeObject implementation before setting or getting the value.
 *
 * @author Tobias Downer
 */
public abstract class RhinoAbstractMap extends NativeObject {

  /**
   * Call to initialize this object within the given scope.
   * 
   * @param scope
   * @return 
   */
  public RhinoAbstractMap init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(this, scope, TopLevel.Builtins.Object);
    return this;
  }

  // ----- To override -----
  
  protected abstract Object getKey(String id);
  
  protected abstract boolean hasKey(String id);

  protected void putKeyValue(String id, Object val) {
    throw immutableFail();
  }

  protected void deleteKey(String id) {
    throw immutableFail();
  }
  
  /**
   * Returns the set of keys, or throws 'keyAccessFail' if this operation is
   * not supported.
   * @return 
   */
  protected Set<String> keysArray() {
    throw keyAccessFail();
  }

  /**
   * Fail if the map is immutable.
   * @return 
   */
  protected EvaluatorException immutableFail() {
    return Context.reportRuntimeError("Object is immutable");
  }
  
  /**
   * Fail if key set not available.
   * @return 
   */
  protected EvaluatorException keyAccessFail() {
    return Context.reportRuntimeError("Keys not available");
  }

  @Override
  public Object get(String id, Scriptable start) {
    Object ob = super.get(id, start);
    if (ob != null && !UniqueTag.NOT_FOUND.equals(ob)) {
      return ob;
    }
    if (!hasKey(id)) {
      return UniqueTag.NOT_FOUND;
    }
    else {
      return getKey(id);
    }
  }

  @Override
  public boolean has(String id, Scriptable start) {
    if (super.has(id, start)) {
      return true;
    }
    return hasKey(id);
  }

  @Override
  public void put(String name, Scriptable start, Object value) {
    putKeyValue(name, value);
  }

  @Override
  public void put(int index, Scriptable start, Object value) {
    immutableFail();
  }

  @Override
  public void delete(String name) {
    deleteKey(name);
  }

  @Override
  public void delete(int index) {
    immutableFail();
  }

  @Override
  public Object[] getIds() {
    Set<String> gen_keys = keysArray();
    Object[] parent_keys = super.getIds();

    if (gen_keys.isEmpty()) {
      return parent_keys;
    }
    if (parent_keys == null || parent_keys.length == 0) {
      return gen_keys.toArray();
    }

    // Merge into a final set,
    Set<Object> out_set = new HashSet();
    for (String v : gen_keys) {
      out_set.add(v);
    }
    for (Object o : parent_keys) {
      out_set.add(o);
    }

    return out_set.toArray();
  }

}
