/**
 * com.mckoi.webplatform.util.Security  Apr 17, 2011
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

package com.mckoi.webplatform.util;

/**
 * Various security related utility methods for the Mckoi Web Platform.
 *
 * @author Tobias Downer
 */

public class Security {

  /**
   * A secure string equality test for where optionally the last 18 characters
   * are tested using a constant time equality test to prevent timing
   * attacks.
   * <p>
   * If 'full' is true the full string is tested using a constant time
   * comparison.
   */
  public static boolean secureEquals(String s1, String s2, boolean full) {
    int sz = s1.length();
    if (sz == s2.length()) {
      final int test_sz = (full ? 0 : sz - 18);
      int i = 0;
      // The prefix does not need to use a constant time test because it
      // is a time component that could be inferred anyway.
      for (; i < test_sz; ++i) {
        if (s1.charAt(i) != s2.charAt(i)) {
          return false;
        }
      }
      // The rest of the characters (the random component of the cookie id)
      // are tested using a constant time comparison algorithm.
      char result = 0;
      for (; i < sz; ++i) {
        result |= (s1.charAt(i) ^ s2.charAt(i));
      }
      // If result is 0 then comparison is equal
      return result == 0;
    }
    return false;
  }

}
