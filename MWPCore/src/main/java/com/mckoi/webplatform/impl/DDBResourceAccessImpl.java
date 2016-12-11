/**
 * com.mckoi.webplatform.impl.DDBResourceAccessImpl  Mar 5, 2012
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

import com.mckoi.mwpcore.MWPDBSessionCache;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.webplatform.DDBResourceAccess;
import com.mckoi.webplatform.MWPRuntimeException;
import com.mckoi.webplatform.MckoiDDBPath;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * A user facing interface for access to Mckoi database related API.
 *
 * @author Tobias Downer
 */

class DDBResourceAccessImpl implements DDBResourceAccess {

  /**
   * The session cache.
   */
  private final MWPDBSessionCache sessions_cache;

  /**
   * The account name.
   */
  private final String account_name;

  /**
   * Set to true when the account info has been loaded.
   */
  private boolean account_info_loaded = false;

  /**
   * The privs of this user.
   */
  private final Set<String> account_privs;

  /**
   * The set of paths that are accessible to this user.
   */
  private Set<MckoiDDBPath> accessible_paths = null;

  /**
   * Constructor.
   */
  DDBResourceAccessImpl(MWPDBSessionCache sessions_cache, String account_name) {
    this.sessions_cache = sessions_cache;
    this.account_name = account_name;

    this.account_privs = new HashSet<>();
  }

  /**
   * Loads user privs for this account from the database.
   */
  private void ensureUserPrivs() {
    if (!account_info_loaded) {
      account_info_loaded = true;

      // Session manager,
      MWPDBSessionCache session_manager = sessions_cache;
      // Access the system platform,
      ODBTransaction t = session_manager.getODBTransaction("sysplatform");

      ODBObject accounts = t.getNamedItem("accounts");
      ODBList accounts_idx = accounts.getList("accountIdx");
      ODBObject account_ob = accounts_idx.getObject(account_name);

      Set<MckoiDDBPath> inc_path_set = new TreeSet<>();

      // Value contains priv information for the user,
      String value = account_ob.getString("value");
      ODBList resource_list = account_ob.getList("resource_list");

      for (ODBObject resource : resource_list) {
        String consensus_fun = resource.getString("type");
        String path_name = resource.getString("path");
        MckoiDDBPath p = new MckoiDDBPath(path_name, consensus_fun);
        inc_path_set.add(p);
        // Set the privs,
        if (consensus_fun.equals("com.mckoi.odb.ObjectDatabase")) {
          account_privs.add("rw_odb." + path_name);
        }
      }

      // Parse the account access permissions from 'value'
      String json_val = value == null ? "" : value.trim();

      if (!json_val.equals("")) {
        try {
          JSONTokener json_tok = new JSONTokener(json_val);
          JSONObject priv_ob = (JSONObject) json_tok.nextValue();

          ODBObject paths = t.getNamedItem("paths");
          ODBList paths_idx = paths.getList("pathIdx");

          // If this is super user?
          String global_priv = priv_ob.optString("globalpriv", "");
          if (global_priv.equals("superuser")) {
            // YES, this is super user so add all the system paths,
            // Iterate over all system paths (paths beginning with 'sys')
            paths_idx = paths_idx.tail("sys");
            // Add to the path set list,
            for (ODBObject path : paths_idx) {
              String path_name = path.getString("path");
              if (!path_name.startsWith("sys")) {
                break;
              }
              // Add the system path,
              String consensus_fun = path.getString("type");
              MckoiDDBPath p = new MckoiDDBPath(path_name, consensus_fun);
              inc_path_set.add(p);
            }
          }

          {
            // Any extra ODB paths accessible to this account?
            JSONArray read_odb_paths = priv_ob.optJSONArray("read_odb_paths");
            if (read_odb_paths != null) {
              // Yes, so add the system paths to the access set,
              int len = read_odb_paths.length();
              for (int i = 0; i < len; ++i) {
                String path_name = read_odb_paths.getString(i);
                if (paths_idx.contains(path_name)) {
                  String consensus_fun = "com.mckoi.odb.ObjectDatabase";
                  MckoiDDBPath p = new MckoiDDBPath(path_name, consensus_fun);
                  inc_path_set.add(p);
                }
                // Add the priv,
                account_privs.add("r_odb." + path_name);
              }
            }
          }

          {
            JSONArray readwrite_odb_paths =
                                    priv_ob.optJSONArray("readwrite_odb_paths");
            if (readwrite_odb_paths != null) {
              // Yes, so add the system paths to the access set,
              int len = readwrite_odb_paths.length();
              for (int i = 0; i < len; ++i) {
                String path_name = readwrite_odb_paths.getString(i);
                if (paths_idx.contains(path_name)) {
                  String consensus_fun = "com.mckoi.odb.ObjectDatabase";
                  MckoiDDBPath p = new MckoiDDBPath(path_name, consensus_fun);
                  inc_path_set.add(p);
                }
                // Add the priv,
                account_privs.add("rw_odb." + path_name);
              }
            }
          }

        }
        catch (JSONException e) {
          // Unable to parse, default priv
          e.printStackTrace(System.err);
        }
      }

      // Make an immutable set,
      accessible_paths = Collections.unmodifiableSet(inc_path_set);

    }

  }
  

  // ----- Implemented -----

  @Override
  public Set<MckoiDDBPath> getAllPaths() {

    // Update the user privs,
    ensureUserPrivs();
    return accessible_paths;

  }

  @Override
  public ODBTransaction createODBTransaction(String path_name) {

    // Update the user privs,
    ensureUserPrivs();

    // Check we're allowed to access this,
    MckoiDDBPath checked_path =
                   new MckoiDDBPath(path_name, "com.mckoi.odb.ObjectDatabase");

    // Security check,
    if (accessible_paths.contains(checked_path)) {

      // Does the account have privs to read/write this path,
      if (account_privs.contains("rw_odb." + path_name)) {
        // Return the transaction,
        return sessions_cache.getODBTransaction(path_name);
      }
      // No, so write in an unmodifiable version,
      else {
        // Return a readonly odb transaction,
        return sessions_cache.getReadOnlyODBTransaction(path_name);
      }

    }
    else {
      throw new MWPRuntimeException("Path not found");
    }

  }

}
