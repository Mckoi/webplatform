/**
 * com.mckoi.webplatform.impl.SecureRandomUtil  Mar 9, 2012
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

package com.mckoi.webplatform.impl;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * A centralized secure random object.
 *
 * @author Tobias Downer
 */

public class SecureRandomUtil {

  /**
   * A secure random number generator.
   */
  static final SecureRandom RND;

  static {
    try {
      RND = SecureRandom.getInstance("SHA1PRNG");
    }
    catch (NoSuchAlgorithmException ex) {
      throw new RuntimeException("SHA1PRNG unavailable", ex);
    }
  }

  /**
   * Pads the string 'v' to the given size.
   */
  static String pad(String v, int size) {
    StringBuilder b = new StringBuilder();
    int sz = size - v.length();
    for (int i = 0; i < sz; ++i) {
      b.append("0");
    }
    b.append(v);
    return b.toString();
  }

  static String createTimeOrderedUID() {
    long timestamp = Math.abs(System.currentTimeMillis());
    int rv = Math.abs(RND.nextInt());
    String v1 = Long.toString(timestamp, 32);
    String v2 = Integer.toString(rv, 32);

    return pad(v1, 14) + pad(v2, 7);
  }

}
