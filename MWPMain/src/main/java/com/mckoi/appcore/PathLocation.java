/**
 * com.mckoi.appcore.PathLocation  Mar 9, 2012
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

import com.mckoi.network.ServiceAddress;

/**
 * The location of a path on a network, represented by a root leader address
 * and root server addresses.
 *
 * @author Tobias Downer
 */

public class PathLocation {

  private final ServiceAddress root_leader;
  private final ServiceAddress[] root_servers;

  public PathLocation(ServiceAddress root_leader,
                      ServiceAddress[] root_servers) {
    this.root_leader = root_leader;
    this.root_servers = root_servers.clone();
  }

  public ServiceAddress getRootLeader() {
    return root_leader;
  }

  public ServiceAddress[] getRootServers() {
    return root_servers.clone();
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(root_leader.displayString());
    b.append(" [ ");
    for (ServiceAddress ra : root_servers) {
      b.append(ra.displayString());
      b.append(" ");
    }
    b.append("]");
    return b.toString();
  }

}
