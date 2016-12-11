/**
 * com.mckoi.mwpcore.MWPDBSessionCache  Mar 21, 2012
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

import com.mckoi.appcore.DBSessionCache;
import com.mckoi.network.*;
import com.mckoi.webplatform.impl.SuperUserPlatformContextImpl;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

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

public final class MWPDBSessionCache extends DBSessionCache {

  /**
   * Constructor.
   */
  public MWPDBSessionCache(MckoiDDBClient client,
                           NetworkConfigResource network_config_resource,
                           int max_time_out_of_date) {
    super(client, network_config_resource, max_time_out_of_date);
  }

  /**
   * Returns a MckoiDDBAccess object that checks it is able to access its
   * function by calling 'PlatformContextImpl.checkSUAccess()'.
   */
  public MckoiDDBAccess getSecureDDBAccess() {
    // Secure implementation checks off against SuperUserPlatformContextImpl
    return new SecureMckoiDDBAccess(getMckoiDDBClient()) {
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
            NetworkProfile network_profile = getMckoiDDBClient().getNetworkProfile(null);
            network_profile.setNetworkConfiguration(getNetworkConfigResource());
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

}
