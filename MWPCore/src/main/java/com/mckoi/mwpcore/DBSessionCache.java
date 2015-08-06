/**
 * com.mckoi.mwpcore.DBSessionCache  Mar 21, 2012
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

package com.mckoi.mwpcore;

import com.mckoi.network.*;
import com.mckoi.odb.*;
import com.mckoi.webplatform.impl.SuperUserPlatformContextImpl;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.List;

/**
 * A cache for MckoiDDB session data. This is a cache that may be shared
 * between several services. It is a thread-safe way to create transactions
 * that helps minimize the load on the root servers by caching root data
 * states. The down side to using this cache is that data is worst-case, 4
 * seconds out of date.
 * <p>
 * SECURITY: Care must be taken to ensure this object does not end up in
 *   user-code. User access to this object will expose access to the MckoiDDB
 *   infrastructure API.
 *
 * @author Tobias Downer
 */

public final class DBSessionCache {

  /**
   * The client access to the MckoiDDB network.
   */
  private final MckoiDDBClient client;

  /**
   * The network config resource for admin operations.
   */
  private final NetworkConfigResource network_config_resource;

  /**
   * Map from path name to SessionStates.
   */
  private final HashMap<String, SessionState> session_map;

  /**
   * The maximum time (in milliseconds) of how out of date a transaction may
   * be. Defaults to 4 seconds.
   */
  private final int max_time_out_of_date;

  /**
   * Constructor.
   */
  public DBSessionCache(MckoiDDBClient client,
                        NetworkConfigResource network_config_resource,
                        int max_time_out_of_date) {

    if (client == null) throw new NullPointerException();
    if (network_config_resource == null) throw new NullPointerException();

    this.client = client;
    this.network_config_resource = network_config_resource;
    this.max_time_out_of_date = max_time_out_of_date;

    session_map = new HashMap();
  }

  /**
   * Returns a MckoiDDBAccess object that checks it is able to access its
   * function by calling 'PlatformContextImpl.checkSUAccess()'.
   */
  public MckoiDDBAccess getSecureDDBAccess() {
    // Secure implementation checks off against SuperUserPlatformContextImpl
    return new SecureMckoiDDBAccess(client) {
      @Override
      void checkSecurity() {
        SuperUserPlatformContextImpl.checkSUAccess();
      }
    };
  }

  /**
   * Returns a NetworkAccess object that checks it is able to access its
   * function by calling 'PlatformContextImpl.checkSUAccess()'
   */
  public NetworkAccess getSecureNetworkAccess() {

    // The NetworkProfile is created in a privileged call.
    NetworkProfile network_profile =
                               (NetworkProfile) AccessController.doPrivileged(
      new PrivilegedAction() {
        @Override
        public Object run() {
          try {
            NetworkProfile network_profile = client.getNetworkProfile(null);
            network_profile.setNetworkConfiguration(network_config_resource);
            return network_profile;
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    );

    // The created network profile is wrapped with a SecureNetworkAccess
    // that checks the thread account's global_priv.

    // Secure implementation checks off against SuperUserPlatformContextImpl
    return new SecureNetworkAccess(network_profile) {
      @Override
      void checkSecurity() {
        SuperUserPlatformContextImpl.checkSUAccess();
      }
    };

  }

  /**
   * Returns a DBPathSnapshot object representing the latest snapshot of
   * the database path. This can be built into a transaction by using the
   * 'createODBTransaction' methods.
   * <p>
   * The snapshot is lazily created so it may not represent the very latest
   * version available (the snapshot will be at most 'max_time_out_of_date'
   * milliseconds out of date).
   */
  public DBPathSnapshot getLatestDBPathSnapshot(final String path) {
    
    final String key = "odb" + path;
    final long time_now = System.currentTimeMillis();

    // Get the session state,
    SessionState state;
    synchronized (session_map) {
      state = session_map.get(key);
      if (state == null) {
        state = new SessionState();
        state.last_update = -1;
        state.state_object1 = new ODBSession(client, path);
        session_map.put(key, state);
      }
    }

    // Synchronize over the state object
    synchronized (state) {
      ODBSession session = (ODBSession) state.state_object1;
      // Is it time to fetch a new snapshot?
      if (state.last_update < time_now - max_time_out_of_date) {
        // Yes, so fetch the current snapshot
        state.state_object2 = session.getCurrentSnapshot();
        state.last_update = time_now;
      }

      // Return the root address object,
      return new DBPathSnapshot(session, (ODBRootAddress) state.state_object2);
    }

  }

  /**
   * Materializes an ODBTransaction given a DBPathSnapshot.
   */
  public ODBTransaction createODBTransaction(DBPathSnapshot snapshot) {

    ODBTransaction t = snapshot.getSession().createTransaction(
                                                 snapshot.getODBRootAddress());

    // Wrap the transaction,
    return new WrappedODBTransaction(t);

  }

  /**
   * Materializes a read-only ODBTransaction given a DBPathSnapshot.
   */
  public ODBTransaction createReadOnlyODBTransaction(DBPathSnapshot snapshot) {

    // Create the ODBTransaction object on the current snapshot,
    return snapshot.getSession().createReadOnlyTransaction(
                                                 snapshot.getODBRootAddress());

  }

  /**
   * Returns a ODBTransaction object for the latest version of the given path.
   * The transaction is lazily created (the snapshot will be at most 
   * 'max_time_out_of_date' milliseconds out of date).
   */
  public ODBTransaction getODBTransaction(final String path) {
    return createODBTransaction(getLatestDBPathSnapshot(path));
  }

  /**
   * Returns a read-only ODBTransaction object representing the latest version
   * of the given path. The transaction is lazily created (the snapshot will
   * be at most 'max_time_out_of_date' milliseconds out of date).
   */
  public ODBTransaction getReadOnlyODBTransaction(final String path) {
    return createReadOnlyODBTransaction(getLatestDBPathSnapshot(path));
  }

  /**
   * Commits a transaction, and if successful updates the internal state so
   * further requests for transaction for the path return a view of the
   * transaction that includes the changes.
   */
  public ODBRootAddress commitODBTransaction(ODBTransaction transaction)
                                                  throws CommitFaultException {

    // Get the path of the transaction,
    String path = transaction.getSessionPathName();

    final String key = "odb" + path;
    // Commit the transaction,
    ODBRootAddress new_root =
                       ((WrappedODBTransaction) transaction).internalCommit();
    // Note that the above commit may throw a commit fault exception which
    // means we don't procede.
    // If the commit is successful however, the state objects are updated.
    final long time_now = System.currentTimeMillis();

    // If the commit is successful,
    // Get the session state,
    SessionState state;
    synchronized (session_map) {
      state = session_map.get(key);
      if (state == null) {
        state = new SessionState();
        state.last_update = -1;
        state.state_object1 = new ODBSession(client, path);
        session_map.put(key, state);
      }
    }

    // Update the state,
    synchronized (state) {
      // Update the state
      state.state_object2 = new_root;
      state.last_update = time_now;
    }

    return new_root;

  }


  // ----- Inner classes -----

  /**
   * Wrapped ODBTransaction that overrides 'commit' to go through the
   * sessions cache.
   */
  private class WrappedODBTransaction implements ODBTransaction {
    
    private ODBTransaction backed;

    WrappedODBTransaction(ODBTransaction backed) {
      this.backed = backed;
    }

    /**
     * The internal commit method that delegates to the wrapped transaction.
     */
    ODBRootAddress internalCommit() throws CommitFaultException {
      return backed.commit();
    }

    @Override
    public ODBRootAddress commit() throws CommitFaultException {
      // Override commit to use session commit,
      return commitODBTransaction(this);
    }

    @Override
    public boolean removeNamedItem(String name) {
      return backed.removeNamedItem(name);
    }

    @Override
    public String getSessionPathName() {
      return backed.getSessionPathName();
    }

    @Override
    public ODBObject getObject(ODBClass type, Reference ref) {
      return backed.getObject(type, ref);
    }

    @Override
    public List<String> getNamedItemsList() {
      return backed.getNamedItemsList();
    }

    @Override
    public ODBObject getNamedItem(String name) {
      return backed.getNamedItem(name);
    }

    @Override
    public List<String> getClassNamesList() {
      return backed.getClassNamesList();
    }

    @Override
    public ODBClassCreator getClassCreator() {
      return backed.getClassCreator();
    }

    @Override
    public ODBClass getClass(Reference ref) {
      return backed.getClass(ref);
    }

    @Override
    public ODBClass findClass(String class_name) {
      return backed.findClass(class_name);
    }

    @Override
    public void doGarbageCollection() {
      backed.doGarbageCollection();
    }

    @Override
    public ODBObject constructObject(ODBClass clazz, Object... args) {
      return backed.constructObject(clazz, args);
    }

    @Override
    public void addNamedItem(String name, ODBObject item) {
      backed.addNamedItem(name, item);
    }
    
  }

  private static class SessionState {

    long last_update;
    Object state_object1;
    Object state_object2;

  }

}
