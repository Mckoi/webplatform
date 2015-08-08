/**
 * com.mckoi.webplatform.SessionAuthenticatorImpl  May 28, 2010
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

import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.*;
import com.mckoi.webplatform.CookieInfo;
import com.mckoi.webplatform.MWPRuntimeException;
import com.mckoi.webplatform.SessionAuthenticator;
import com.mckoi.webplatform.util.Security;
import java.security.SecureRandom;
import java.util.ArrayList;
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

final class SessionAuthenticatorImpl implements SessionAuthenticator {

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  /**
   * Secure random number generator.
   */
  private final SecureRandom SECURE_RANDOM = SecureRandomUtil.RND;

  private final char[] char_code =
       { '0', '1', '2', '3', '4', '5',
         '6', '7', '8', '9', 'a', 'b',
         'c', 'd', 'e', 'f', 'g', 'h',
         'i', 'j', 'k', 'l', 'm', 'n',
         'o', 'p', 'q', 'r', 's', 't',
         'u', 'v', 'w', 'x', 'y', 'z'   };

  /**
   * The vhost for this session.
   */
  private final String vhost;

  /**
   * The account name of the current context.
   */
  private final String account_name;

  /**
   * Called when a cookie is generated. Used to make sure only one cookie can
   * even be generated per session.
   */
  private boolean cookie_generated = false;

  /**
   * Constructor.
   */
  SessionAuthenticatorImpl(DBSessionCache sessions_cache,
                           String vhost, String account_name) {
    this.sessions_cache = sessions_cache;
    this.vhost = vhost;
    this.account_name = account_name;
  }

  /**
   * Returns a transaction to the sessions table.
   */
  private ODBTransaction getTransaction() {
    // PENDING: We can shard out here if necessary,
    return sessions_cache.getODBTransaction("ufs" + account_name);
  }

  /**
   * Creates a unique id string for a cookie identifier. The id has a random
   * and time based aspect based on the system clock, and lexicographically
   * orders by time.
   * <p>
   * Note all ids generated are the same size.
   */
  private String generateUniqueCookieID() {
    // The current system time
    long time_val = System.currentTimeMillis();
    // The random element
    int[] ran_val = new int[3];
    ran_val[0] = SECURE_RANDOM.nextInt();
    ran_val[1] = SECURE_RANDOM.nextInt();
    ran_val[2] = SECURE_RANDOM.nextInt();

    // This unique id has 154 bits of data. The first 13 characters are an
    // encoding of the current time and the final encode a random id.

    // Turn the time into a base 32 string and pad to 13 chars
    String time_str_val = Long.toString(time_val, 32);
    int tlen_pad = 13 - time_str_val.length();

    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < tlen_pad; ++i) {
      buf.append('0');
    }
    buf.append(time_str_val);
    // Append 18 characters from the random element,
    for (int i = 0; i < ran_val.length; ++i) {
      int v = ran_val[i];
      for (int n = 0; n < 6; ++n) {
        buf.append(char_code[v & 0x01F]);
        v = v >> 5;
      }
    }

    assert(buf.length() == (12 + 18));

    return buf.toString();
  }

  /**
   * Adds a cookie entry in the sessions database for the given authenticated
   * user, and returns the created cookie id.
   */
  @Override
  public String createCookieForAuthenticatedUser(String username) {
    // Assert username size,
    if (username.length() > 256) {
      throw new MWPRuntimeException("Username exceeds maximum length");
    }

    // Assert that a cookie has not already been created. This prevents
    // using the authenticator to store information other than is intended.
    if (cookie_generated) {
      throw new MWPRuntimeException(
            "A Cookie was already generated, only one cookie can be generated per session.");
    }
    cookie_generated = true;

    String cookie_id = generateUniqueCookieID();

    // Current timestamp is encoded as a base32 string.
    long time_val = System.currentTimeMillis() / 1024;
    String timestamp_str = Long.toString(time_val, 32);

    String domaincookie_str = vhost + "/" + cookie_id;

    ODBTransaction t = getTransaction();
    // Create a A.Session object,
    ODBClass cookie_class = t.findClass("A.Session");

    // Construct a new cookie object,
    // (domaincookie, user, timestamp)
    ODBObject cookie_ob = t.constructObject(cookie_class,
                                   domaincookie_str, username, timestamp_str);

    // Get the session index object,
    ODBObject session_index = t.getNamedItem("sessions");

    // Get the indexes,
    ODBList domaincookie_idx = session_index.getList("domaincookieIdx");
    ODBList user_idx = session_index.getList("userIdx");

    // Add the cookie object to the indexes,
    domaincookie_idx.add(cookie_ob);
    user_idx.add(cookie_ob);

    // Commit it,
    try {
      t.commit();
    }
    catch (CommitFaultException e) {
      // Shouldn't be possible for this to happen,
      throw new MWPRuntimeException(e);
    }

    return cookie_id;
  }

  /**
   * Given a cookie identifier, returns the name of the authenticated user, or
   * null if there is no user for this cookie.
   */
  @Override
  public CookieInfo getAuthenticatedUserForCookie(String cookie_id) {

    // Create a transaction object,
    ODBTransaction t = getTransaction();
    // Qualify the cookie
    final String qualified_cookie = vhost + "/" + cookie_id;

    // Get the session index object,
    ODBObject session_index = t.getNamedItem("sessions");

    // Get the index of cookie ids,
    ODBList domaincookie_idx = session_index.getList("domaincookieIdx");

    // Query the index,
    // NOTE; This will perform an index lookup for the cookie and then fetch
    // the first entry of the list and use the 'secureEquals' method to
    // determine if the cookie id matches. The reason for this code is to
    // prevent a certain type of attack where the attacker uses the response
    // time of the algorithm to determine the characters of the cookie id.
    domaincookie_idx = domaincookie_idx.tail(qualified_cookie);
    if (domaincookie_idx.size() > 0) {
      ODBObject session_ob = domaincookie_idx.getObject(0);
      String domain_cookie = session_ob.getString("domaincookie");
      if (Security.secureEquals(domain_cookie, qualified_cookie, false)) {
        // Found a match, so return the user and timestamp,
        String username = session_ob.getString("user");
        String timestamp = session_ob.getString("timestamp");
        long timestamp_val = Long.parseLong(timestamp, 32) * 1024;
        CookieInfoImpl user_info = new CookieInfoImpl(username, timestamp_val);
        return user_info;
      }
    }
    // Not found,
    return null;

  }

  /**
   * Deletes the cookie_id entry stored in the sessions table.
   */
  @Override
  public void deleteCookie(String cookie_id) {

    // Create a transaction object,
    ODBTransaction t = getTransaction();
    // Qualify the cookie
    final String qualified_cookie = vhost + "/" + cookie_id;

    // Get the session index object,
    ODBObject session_index = t.getNamedItem("sessions");

    // Get the index of cookie ids,
    ODBList domaincookie_idx = session_index.getList("domaincookieIdx");
    ODBList user_idx = session_index.getList("userIdx");

    // Get the session object
    // (NOTE: This will be a time variant search).
    ODBObject cookie_ob = domaincookie_idx.getObject(qualified_cookie);

    // Remove the cookie object from the indexes,
    domaincookie_idx.removeAll(cookie_ob.getReference());
    user_idx.removeAll(cookie_ob.getReference());

  }

  /**
   * Deletes all the cookies in the database for the given user.
   */
  @Override
  public void deleteAllAuthenticatedUser(String user_name) {

    // Create a transaction object,
    ODBTransaction t = getTransaction();

    // Get the session index object,
    ODBObject session_index = t.getNamedItem("sessions");

    // The list of references to delete,
    ArrayList<Reference> references = new ArrayList<>(70);

    boolean end_reached = false;
    while (!end_reached) {
      // Clear the references list,
      references.clear();

      ODBList user_idx = session_index.getList("userIdx");
      ODBList cookie_idx = session_index.getList("domaincookieIdx");

      // Find all the entries that match the user,
      ODBList index = user_idx.tail(user_name);

      int count = 0;
      for (ODBObject ob : index) {
        if (ob.getString("user").equals(user_name)) {
          references.add(ob.getReference());
        }
        else {
          end_reached = true;
          break;
        }
        ++count;
        if (count == 64) {
          break;
        }
      }

      // Delete the references from the list,
      for (Reference ref : references) {
        user_idx.removeAll(ref);
        cookie_idx.removeAll(ref);
      }
    }

  }

  /**
   * Returns the list of all cookies for the given user, or an
   * empty list if none found. 'limit' is the maximum number of cookies that
   * may be returned.
   */
  @Override
  public List<String> getCookiesForUser(String user_name, int limit) {

    // Create a transaction object,
    ODBTransaction t = getTransaction();

    // Get the session index object,
    ODBObject session_index = t.getNamedItem("sessions");

    // The list of cookies,
    ArrayList<String> cookies = new ArrayList<>(70);

    ODBList user_idx = session_index.getList("userIdx");

    // Find all the entries that match the user,
    ODBList index = user_idx.tail(user_name);

    String host_pre = vhost + "/";

    for (ODBObject ob : index) {
      if (limit > 0 && ob.getString("user").equals(user_name)) {
        String dc = ob.getString("domaincookie");
        if (dc.startsWith(host_pre)) {
          cookies.add(dc.substring(host_pre.length()));
          --limit;
        }
      }
      else {
        break;
      }
    }

    return cookies;
  }

}
