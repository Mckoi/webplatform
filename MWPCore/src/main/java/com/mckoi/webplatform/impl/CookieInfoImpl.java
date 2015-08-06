/**
 * com.mckoi.webplatform.CookieInfoImpl  May 28, 2010
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

import com.mckoi.webplatform.CookieInfo;

/**
 * Contains the information associated with a cookie in the session
 * authenticator.
 *
 * @author Tobias Downer
 */

final class CookieInfoImpl implements CookieInfo {

  /**
   * The authenticated username assigned the cookie.
   */
  private final String username;

  /**
   * The timestamp when the cookie was created/renewed.
   */
  private final long timestamp;

  CookieInfoImpl(String username, long timestamp) {
    this.username = username;
    this.timestamp = timestamp;
  }

  /**
   * Returns the authenticated username.
   */
  public String getAuthenticatedUser() {
    return username;
  }

  /**
   * Returns the timestamp the cookie was created or renewed.
   */
  public long getTimestamp() {
    return timestamp;
  }

}
