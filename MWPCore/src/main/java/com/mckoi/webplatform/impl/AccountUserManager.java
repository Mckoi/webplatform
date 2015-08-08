/**
 * com.mckoi.webplatform.AccountUserManager  Jul 9, 2010
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

import com.mckoi.appcore.AppCoreAdmin;
import com.mckoi.appcore.SystemStatics;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.ODBClass;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.webplatform.MWPRuntimeException;
import com.mckoi.webplatform.UserManager;
import com.mckoi.webplatform.UserProfile;
import com.mckoi.webplatform.util.Security;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An object for managing the user information in an account.
 *
 * @author Tobias Downer
 */

final class AccountUserManager implements UserManager {

  /**
   * The account name.
   */
  private final String account_name;

  /**
   * The transaction object for communicating with the user's path.
   */
  private ODBTransaction transaction;

  /**
   * Constructor.
   */
  AccountUserManager(ODBTransaction transaction, String account_name) {
    this.account_name = account_name;
    this.transaction = transaction;
  }


  /**
   * Turns an ODBObject of type A.User into a UserProfile object.
   */
  private static UserProfile toUserProfile(ODBObject user_ob) {
    String user = user_ob.getString("user");
    String email = user_ob.getString("email");
    String info = user_ob.getString("info");
    return new SAUserProfile(user, email, info);
  }


  /**
   * Secure method (that inherits the trusted status of the class) for
   * fetching a UserProfile from a list of user profiles.
   */
  private UserProfile secureGetUserProfile(ODBList list, long i) {
    return toUserProfile(list.getObject(i));
  }

  // -----

  @Override
  public void createNewUser(String name, String email,
                            Map<String, String> properties) {
    AppCoreAdmin.createUserProfile(transaction, name, email, properties);
  }

  @Override
  public void deleteUser(String name) {
    AppCoreAdmin.deleteUserProfile(transaction, name);
  }

  @Override
  public UserProfile getUserProfile(String name) {
    // The list of users
    ODBObject users = transaction.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");
    ODBObject user_ob = users_idx.getObject(name);

    if (user_ob == null) {
      return null;
    }

    return toUserProfile(user_ob);
  }

  @Override
  public boolean canUserAuthenticate(String name, String password) {
    if (name == null || password == null) {
      return false;
    }
    UserProfile user = getUserProfile(name);
    if (user == null) {
      return false;
    }
    return user.matchesPassword(password);
  }

  @Override
  public List<UserProfile> getUserProfilesWithEmail(String email) {
    // The list of users
    ODBObject users = transaction.getNamedItem("users");

    // The emails indexes
    ODBList emails_idx = users.getList("emailIdx");
    long start = emails_idx.indexOf(email);
    if (start < 0) {
      return new ArrayList<>(0);
    }
    long end = emails_idx.lastIndexOf(email) + 1;

    return new AUMUserList(emails_idx, start, end);
  }

  @Override
  public List<UserProfile> getUsersList() {
    // The list of users
    ODBObject users = transaction.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");

    return new AUMUserList(users_idx, 0, users_idx.size());
  }

  @Override
  public void setUserProperty(String name, String key, String value) {
    // The list of users
    ODBObject users = transaction.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");
    ODBObject user_ob = users_idx.getObject(name);

    if (user_ob == null) {
      throw new MWPRuntimeException("Profile ''{0}'' does not exist", name);
    }

    // Update the JSON formatted properties string,
    String info = user_ob.getString("info");
    try {
      JSONObject properties = new JSONObject(info);
      properties.put(key, value);
      user_ob.setString("info", properties.toString());
    }
    catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updateUserEmail(String name, String email) {
    // The list of users
    ODBObject users = transaction.getNamedItem("users");

    // The users indexes
    ODBList users_idx = users.getList("userIdx");
    ODBList emails_idx = users.getList("emailIdx");
    ODBObject user_ob = users_idx.getObject(name);

    if (user_ob == null) {
      throw new MWPRuntimeException("Profile ''{0}'' does not exist", name);
    }

    // Remove the object from the indexes,
    users_idx.remove(name);
    emails_idx.remove(user_ob.getReference());

    // The user class,
    ODBClass user_class = transaction.findClass("A.User");

    // Create a new user profile object with the new email,
    // (user, email, info)
    ODBObject user = transaction.constructObject(user_class,
                                      name, email, user_ob.getString("info"));

    // Add to the indexes,
    users_idx.add(user);
    emails_idx.add(user);
  }

  @Override
  public void updateUserPassword(String name, String password) {
    // Update the pass property
    setUserProperty(name, "pass", password);
  }

  @Override
  public void commit() throws CommitFaultException {
    try {
      transaction.commit();
    }
    finally {
      // Make sure to invalidate,
      transaction = null;
    }
  }

  // ------


  private class AUMUserList extends AbstractList<UserProfile> {

    private final ODBList list;
    private final long start;
    private final long end;

    public AUMUserList(ODBList list, long start, long end) {
      this.list = list;
      this.start = start;
      this.end = end;
    }

    @Override
    public UserProfile get(int index) {
      if (index < 0 || index >= (end - start)) {
        throw new IndexOutOfBoundsException();
      }
      return secureGetUserProfile(list, start + index);
    }

    @Override
    public int size() {
      long size = end - start;
      if (size > Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
      return (int) size;
    }

  }



  private static class SAUserProfile implements UserProfile {

    private final String username;

    private final String email;

    private final JSONObject properties;


    private SAUserProfile(String user, String email, String info) {
      this.username = user;
      this.email = email;
      try {
        properties = new JSONObject(info);
      }
      catch (JSONException e) {
        throw new RuntimeException(e);
      }
    }

    private String insecureGetProperty(String key) {
      try {
        return properties.getString(key);
      }
      catch (JSONException e) {
        return null;
      }
    }

    @Override
    public String getAuthType() {
      return insecureGetProperty("auth");
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public String getName() {
      return username;
    }

    @Override
    public String getProperty(String key) {
      // Don't allow the 'pass' property to be fetched.
      if (key.equals("pass")) {
        return null;
      }
      else {
        return insecureGetProperty(key);
      }
    }

    @Override
    public boolean matchesPassword(String password) {
      if (getAuthType().equals("local")) {
        // First look for the 'pash' property and compare hashes,
        String pash = insecureGetProperty("pash");
        if (pash != null) {
          // Turn it into a hash,
          try {
            String password_hash = SystemStatics.toPasswordHash(password);
            return Security.secureEquals(pash, password_hash, true);
          }
          catch (IOException e) {
            return false;
          }
        }
        // If no pash then compare the password with the 'pass' property which
        // is a plain-text password.
        String pwd = insecureGetProperty("pass");
        if (pwd != null) {
          return Security.secureEquals(pwd, password, true);
        }
      }
      return false;
    }

  }

}
