/**
 * com.mckoi.webplatform.UserProfile  May 29, 2010
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

/**
 * An interface that is used to query user information.
 *
 * @author Tobias Downer
 */

public interface UserProfile {

  /**
   * Returns the name of the user. This is a string that uniquely identifies
   * this user. Note that if a user is authenticated from a remote source,
   * then this string will be encoded with the external authentication method.
   */
  public String getName();

  /**
   * The email address for this profile, or null if no email set.
   */
  public String getEmail();

  /**
   * Returns the authentication type used for authenticating this profile.
   * 'local' if the local system can authenticate the user.
   */
  public String getAuthType();

  /**
   * Returns a user defined property value (used to store information in the
   * profile such as the full name).
   */
  public String getProperty(String key);

  /**
   * Returns true if the given password can be used to authenticate this user.
   * This only works when 'getAuthType' returns 'local'. Otherwise the user
   * must authenticate from a remote source.
   */
  public boolean matchesPassword(String password);

}
