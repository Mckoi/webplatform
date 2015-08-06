/**
 * com.mckoi.webplatform.SystemStatics  May 11, 2010
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

package com.mckoi.appcore;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Static system resources.
 *
 * @author Tobias Downer
 */

public class SystemStatics {

  /**
   * The name of the system path that is used to reference the vhost and
   * account information.
   */
  public static final String SYSTEM_PATH = "sysplatform";

//  /**
//   * The name of the system path for user authentication and global session
//   * management.
//   */
//  public static final String AUTHENTICATION_PATH = "sysauthentication";

  /**
   * The name of the path where log information is kept.
   */
  public static final String ANALYTICS_PATH = "sysanalytics";

//  /**
//   * The version properties file that contains very infrequently changing
//   * properties about the system.
//   */
//  public static final String VERSION_FILE = "Version.properties";

//  /**
//   * The name of the accounts table in the sysplatform path (two columns;
//   * 'acount', 'info').
//   */
//  public static final String ACCOUNT_TABLE = "Account";
//
//  /**
//   * The account resource table.
//   */
//  public static final String ACCOUNT_RESOURCES_TABLE = "AccountResource";

//  /**
//   * The name of the servers table, which stores details about all the servers
//   * in the network.
//   */
//  public static final String SERVER_TABLE = "Server";

//  /**
//   * The name of the vhost table in the sysplatform path (two columns;
//   * 'account', 'path_info').
//   */
//  public static final String VHOST_TABLE = "VHost";
//
//  /**
//   * The name of the web server to accounts lookup table.
//   */
//  public static final String WSERVER2ACCOUNT_TABLE = "WServAccountMap";
//
//  /**
//   * The paths property set stores the set of all paths and their address and
//   * sequence number on the network.
//   */
//  public static final String PATHS_FILE = "SPaths.properties";
//
//  /**
//   * The machines property set stores the address of all the machines on the
//   * network.
//   */
//  public static final String MACHINES_FILE = "SMachines.properties";

//  /**
//   * The roles table stores the set of all roles of all machines on the
//   * network.
//   */
//  public static final String ROLES_TABLE = "SRoles";

//  /**
//   * The table that stores all the log buckets.
//   */
//  public static final String LOGBUCKETS_TABLE = "LogBuckets";
//
//  /**
//   * The table that holds periodically updating statistical history on all
//   * the account's pageviews.
//   */
//  public static final String STATHISTORY_TABLE = "StatHistory";
//
//  /**
//   * The table that holds periodically updating information about the load
//   * of various services.
//   */
//  public static final String SERVICELOAD_TABLE = "ServiceLoad";




  /**
   * Returns a string that is the hash of the given password string.
   */
  public static String toPasswordHash(String password) throws IOException {
    // Get SHA digest,
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    // Turn the password string into a byte array (UTF-16)
    byte[] password_arr = password.getBytes("UTF-16");
    // Update the digest,
    digest.update(password_arr, 0, password_arr.length);
    // Turn it into a hash,
    byte[] hash = digest.digest();
    // Turn the hash into a string
    StringBuilder b = new StringBuilder();
    for (byte h : hash) {
      int v = ((int) h) & 0x0FF;
      String hex = Integer.toString(v, 16);
      if (hex.length() == 1) {
        b.append('0');
      }
      b.append(hex);
    }

    // Return the hash string,
    return b.toString();
  }


  // ----- Base 64 encoding -----
  
  /**
   * Lexicographic alphabet for base 64 encoded data.
   * Attributed to Robert Harder from Base64 library (Public Domain)
   */
  private final static byte[] LEXI_BASE64_ALPHABET = {
    (byte)'-',
    (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4',
    (byte)'5', (byte)'6', (byte)'7', (byte)'8', (byte)'9',
    (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F', (byte)'G',
    (byte)'H', (byte)'I', (byte)'J', (byte)'K', (byte)'L', (byte)'M', (byte)'N',
    (byte)'O', (byte)'P', (byte)'Q', (byte)'R', (byte)'S', (byte)'T', (byte)'U',
    (byte)'V', (byte)'W', (byte)'X', (byte)'Y', (byte)'Z',
    (byte)'_',
    (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f', (byte)'g',
    (byte)'h', (byte)'i', (byte)'j', (byte)'k', (byte)'l', (byte)'m', (byte)'n',
    (byte)'o', (byte)'p', (byte)'q', (byte)'r', (byte)'s', (byte)'t', (byte)'u',
    (byte)'v', (byte)'w', (byte)'x', (byte)'y', (byte)'z'
  };

  /**
   * Reverse lookup for the base 64 encoded data.
   * Attributed to Robert Harder from Base64 library (Public Domain)
   */
  private final static byte[] LEXI_BASE64_DECODABET = {
    -9,-9,-9,-9,-9,-9,-9,-9,-9,                 // Decimal  0 -  8
    -5,-5,                                      // Whitespace: Tab and Linefeed
    -9,-9,                                      // Decimal 11 - 12
    -5,                                         // Whitespace: Carriage Return
    -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 14 - 26
    -9,-9,-9,-9,-9,                             // Decimal 27 - 31
    -5,                                         // Whitespace: Space
    -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,              // Decimal 33 - 42
    -9,                                         // Plus sign at decimal 43
    -9,                                         // Decimal 44
    0,                                          // Minus sign at decimal 45
    -9,                                         // Decimal 46
    -9,                                         // Slash at decimal 47
    1,2,3,4,5,6,7,8,9,10,                       // Numbers zero through nine
    -9,-9,-9,                                   // Decimal 58 - 60
    -1,                                         // Equals sign at decimal 61
    -9,-9,-9,                                   // Decimal 62 - 64
    11,12,13,14,15,16,17,18,19,20,21,22,23,     // Letters 'A' through 'M'
    24,25,26,27,28,29,30,31,32,33,34,35,36,     // Letters 'N' through 'Z'
    -9,-9,-9,-9,                                // Decimal 91 - 94
    37,                                         // Underscore at decimal 95
    -9,                                         // Decimal 96
    38,39,40,41,42,43,44,45,46,47,48,49,50,     // Letters 'a' through 'm'
    51,52,53,54,55,56,57,58,59,60,61,62,63,     // Letters 'n' through 'z'
    -9,-9,-9,-9,-9                                 // Decimal 123 - 127
      ,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 128 - 139
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 140 - 152
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 153 - 165
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 166 - 178
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 179 - 191
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 192 - 204
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 205 - 217
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 218 - 230
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,     // Decimal 231 - 243
      -9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9,-9         // Decimal 244 - 255 
  };


  /**
   * Appends an encoded form of the given long value to the StringBuilder.
   * The value is encoded with the LEXI_BASE64_ALPHABET. The string builder
   * will always expand by exactly 11 characters in size. Long = 64 bits.
   * 64 / 6 = 10 2/3.
   * <p>
   * The encoded string will be retain the same ordering as the original
   * long value in a lexicographical index.
   */
  public static StringBuilder encodeLongBase64(
                                           final long val, StringBuilder b) {
    int bit = -2;
    while (bit < 64) {
      long wv = val >> (58 - bit);
      wv = (wv & 63);
      b.append((char) LEXI_BASE64_ALPHABET[(int) wv]);
      bit += 6;
    }
    return b;
  }

  /**
   * Appends an encoded form of the given long value to the StringBuilder.
   * The value is encoded with the LEXI_BASE64_ALPHABET. The string builder
   * will always expand by exactly 11 characters in size. Long = 64 bits.
   * 64 / 6 = 10 2/3.
   * <p>
   * The encoded string will be retain the same ordering as the original
   * long value in a lexicographical index.
   */
  public static StringBuilder encodeLongBase64NoPad(
                                           final long val, StringBuilder b) {
    boolean start = true;
    int bit = -2;
    while (bit < 64) {
      long wv = val >> (58 - bit);
      wv = (wv & 63);
      boolean no_print = false;
      if (start) {
        if (wv == 0) {
          no_print = true;
        }
        else {
          start = false;
        }
      }
      if (!no_print) {
        b.append((char) LEXI_BASE64_ALPHABET[(int) wv]);
      }
      bit += 6;
    }
    if (start == true) {
      b.append((char) LEXI_BASE64_ALPHABET[0]);
    }
    return b;
  }

  /**
   * Returns a string of the given long value that is encoded with the
   * LEXI_BASE64_ALPHABET. The string will always be exactly 11 characters in
   * size. Long = 64 bits. 64 / 6 = 10 2/3.
   * <p>
   * The returned string will be retain the same ordering as the original
   * long value in a lexicographical index.
   */
  public static StringBuilder encodeLongBase64(final long val) {
    return encodeLongBase64(val, new StringBuilder());
  }

  /**
   * Returns a long after decoding a base64 string encoded with the
   * 'encodeLongBase64' method. The long will be decoded from the first 11
   * characters in the given string.
   */
  public static long decodeLongBase64(final String str) {
    if (str.length() < 11) {
      throw new IllegalArgumentException("string is less than 11 characters");
    }
    long v = 0;
    int bit = -2;
    for (int i = 0; i < 11; ++i) {
      // Turn it into a byte,
      byte b = (byte) str.charAt(i);
      long val = LEXI_BASE64_DECODABET[b];
      if (val < 0) {
        throw new IllegalArgumentException("Invalid character");
      }
      v |= val << (58 - bit);
      bit += 6;
    }
    return v;
  }

}
