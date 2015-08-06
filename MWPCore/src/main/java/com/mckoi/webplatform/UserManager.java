/**
 * com.mckoi.webplatform.UserManager  Feb 24, 2011
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

import com.mckoi.network.CommitFaultException;
import java.util.List;
import java.util.Map;

/**
 * An interface for managing the database of user profiles in an account. This
 * object follows standard transactional behaviour, requiring a 'commit' to
 * make the changes in the backed database.
 *
 * @author Tobias Downer
 */

public interface UserManager {

  /**
   * Returns a lexicographically sorted list of all user names currently in
   * the database. This list may be lazily created - meaning accessing the
   * list will load the data.
   * 
   * @return 
   */
  List<UserProfile> getUsersList();

  /**
   * Returns the UserProfile of the user with the given name. Returns null
   * if the user doesn't exist.
   * 
   * @param name
   * @return 
   */
  UserProfile getUserProfile(String name);

  /**
   * Returns true if a user with the given name exists, and the password
   * authenticates with the user, otherwise returns false. This is a
   * convenience for;
   * <code>
   *   UserProfile user = getUserProfile(name);
   *   boolean auth = (user != null && user.matchesPassword(password));
   * </code>
   * 
   * @param name
   * @param password
   * @return false if name or password are null, or if the name and password
   *   do not validate to a currently active user profile.
   */
  boolean canUserAuthenticate(String name, String password);

  /**
   * Returns the list of UserProfile's with the given email. Note that
   * user profiles can be stored with no email. Returns an empty list if
   * no users with the given email are found.
   * 
   * @param email
   * @return 
   */
  List<UserProfile> getUserProfilesWithEmail(String email);

  /**
   * Creates a new user if a user with the given name doesn't already exist.
   * If the user already exists an exception is generated.
   * 
   * @param name
   * @param email
   * @param properties
   */
  void createNewUser(String name, String email,
                     Map<String, String> properties);

  /**
   * Deletes a user. If the user doesn't exist an exception is generated.
   * 
   * @param name
   */
  void deleteUser(String name);

  /**
   * Updates the email address of a user with the given name. If the user
   * doesn't exist an exception is generated.
   * 
   * @param name
   * @param email
   */
  void updateUserEmail(String name, String email);

  /**
   * Updates the password of the user with the given name. If the user
   * doesn't exist an exception is generated.
   * 
   * @param name
   * @param password
   */
  void updateUserPassword(String name, String password);

  /**
   * Sets a property in the user account. If the user doesn't exist an
   * exception is generated.
   * 
   * @param name
   * @param key
   * @param value
   */
  void setUserProperty(String name, String key, String value);

  /**
   * Commits any changes made in this object to the user database.
   * 
   * @throws com.mckoi.network.CommitFaultException
   */
  void commit() throws CommitFaultException;

}
