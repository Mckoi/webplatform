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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * The native timer_wrap binding.
 *
 * @author Tobias Downer
 */
public class NodeNativeTimerWrap {

  private static final RuntimeMXBean mxBean =
                                        ManagementFactory.getRuntimeMXBean();

  public static Object now(Object thiz, Object... args) {
    long vm_uptime = mxBean.getUptime();
    // Is this an optimization at all, I wonder?
    if (vm_uptime < Integer.MAX_VALUE) {
      return (int) vm_uptime;
    }
    return vm_uptime;
  }

  public static Object start(Object thiz, Object... args) {
    GJSObject timer_ob = (GJSObject) args[0];
    long msec = GJSRuntime.toInt64(args[1]);
    // What's this for? Seems to always be passed in as '0'
    long v2 = GJSRuntime.toInt64(args[2]);

    if (v2 != 0) {
      throw new GJavaScriptException("'repeat' argument not supported");
    }

    GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
    pstate.postTimedCallback(timer_ob, msec);

    return Boolean.TRUE;
  }

  public static Object close(Object thiz, Object... args) {
    // Should we do anything when a timer is closed? We don't have any
    // natively stored information per timer do we?
    GJSObject timer_ob = (GJSObject) args[0];
    return Boolean.TRUE;
  }

  public static Object ref(Object thiz, Object... args) {
    ((GJSObject) thiz).setMember("_ref", "REF");
    return Boolean.TRUE;
  }

  public static Object unref(Object thiz, Object... args) {
    ((GJSObject) thiz).setMember("_ref", "UNREF");
    return Boolean.TRUE;
  }

}
