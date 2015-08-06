/**
 * com.mckoi.webplatform.SessionAuthenticator  Apr 17, 2011
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

package com.mckoi.webplatform;

import java.util.List;

/**
 * A session authenticator is used as part of an authentication system to
 * identify users by the cookie they provide. It provides the following network
 * wide services; 1) Generates and remembers a unique value for the application
 * to provide as a cookie for the session, 2) Given a cookie, returns the user
 * previously authenticated on the cookie, 3) Cleans the database of any
 * cookies assigned the user for when a logout happens.
 *
 * @author Tobias Downer
 */

public interface SessionAuthenticator {

  /**
   * Adds a cookie entry in the sessions database for the given authenticated
   * user, and returns the created cookie id.
   */
  String createCookieForAuthenticatedUser(String username);

  /**
   * Given a cookie identifier, returns the name of the authenticated user, or
   * null if there is no user for this cookie.
   */
  CookieInfo getAuthenticatedUserForCookie(String cookie_id);

  /**
   * Deletes the cookie_id entry stored in the sessions table.
   */
  void deleteCookie(String cookie_id);

  /**
   * Deletes all the cookies in the database for the given user.
   */
  void deleteAllAuthenticatedUser(String user_name);

  /**
   * Returns the list of all cookies for the given user, or an
   * empty list if none found. 'limit' is the maximum number of cookies that
   * may be returned.
   */
  List<String> getCookiesForUser(String user_name, int limit);

}
