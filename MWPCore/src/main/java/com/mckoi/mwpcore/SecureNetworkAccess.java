/**
 * com.mckoi.mwpcore.SecureNetworkAccess  Sep 29, 2012
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

import com.mckoi.data.NodeReference;
import com.mckoi.network.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An implementation of NetworkAccess that wraps another NetworkAccess and
 * performs a security check before each call.
 *
 * @author Tobias Downer
 */

abstract class SecureNetworkAccess implements NetworkAccess {

  private final NetworkAccess network;

  SecureNetworkAccess(NetworkAccess network) {
    this.network = network;
  }

  abstract void checkSecurity();

  @Override
  public void stopRoot(ServiceAddress machine) throws NetworkAdminException {
    checkSecurity();
    network.stopRoot(machine);
  }

  @Override
  public void stopManager(ServiceAddress machine) throws NetworkAdminException {
    checkSecurity();
    network.stopManager(machine);
  }

  @Override
  public void stopBlock(ServiceAddress machine) throws NetworkAdminException {
    checkSecurity();
    network.stopBlock(machine);
  }

  @Override
  public void startRoot(ServiceAddress machine) throws NetworkAdminException {
    checkSecurity();
    network.startRoot(machine);
  }

  @Override
  public void startManager(ServiceAddress machine) throws NetworkAdminException {
    checkSecurity();
    network.startManager(machine);
  }

  @Override
  public void startBlock(ServiceAddress machine) throws NetworkAdminException {
    checkSecurity();
    network.startBlock(machine);
  }

  @Override
  public List<ServiceAddress> sortedServerList() {
    checkSecurity();
    return network.sortedServerList();
  }

  @Override
  public void setPathRoot(ServiceAddress root, PathInfo path_info, DataAddress address) throws NetworkAdminException {
    checkSecurity();
    network.setPathRoot(root, path_info, address);
  }

  @Override
  public void removePathFromNetwork(String path_name, ServiceAddress root_server) throws NetworkAdminException {
    checkSecurity();
    network.removePathFromNetwork(path_name, root_server);
  }

  @Override
  public void removeBlockAssociation(BlockId block_id, long server_guid) throws NetworkAdminException {
    checkSecurity();
    network.removeBlockAssociation(block_id, server_guid);
  }

  @Override
  public void registerRoot(ServiceAddress root) throws NetworkAdminException {
    checkSecurity();
    network.registerRoot(root);
  }

  @Override
  public void registerManager(ServiceAddress manager) throws NetworkAdminException {
    checkSecurity();
    network.registerManager(manager);
  }

  @Override
  public void registerBlock(ServiceAddress block) throws NetworkAdminException {
    checkSecurity();
    network.registerBlock(block);
  }

  @Override
  public void refreshNetworkConfig() throws IOException {
    checkSecurity();
    network.refreshNetworkConfig();
  }

  @Override
  public void refresh() {
    checkSecurity();
    network.refresh();
  }

  @Override
  public void processSendBlock(BlockId block_id, ServiceAddress source_block_server, ServiceAddress dest_block_server, long dest_server_sguid) throws NetworkAdminException {
    checkSecurity();
    network.processSendBlock(block_id, source_block_server, dest_block_server, dest_server_sguid);
  }

  @Override
  public long preserveNodesInBlock(ServiceAddress block_server, BlockId block_id, List<NodeReference> nodes_to_preserve) throws NetworkAdminException {
    checkSecurity();
    return network.preserveNodesInBlock(block_server, block_id, nodes_to_preserve);
  }

  @Override
  public boolean isValidMckoiNode(ServiceAddress machine) {
    checkSecurity();
    return network.isValidMckoiNode(machine);
  }

  @Override
  public boolean isMachineInNetwork(ServiceAddress machine) {
    checkSecurity();
    return network.isMachineInNetwork(machine);
  }

  @Override
  public MachineProfile[] getRootServers() {
    checkSecurity();
    return network.getRootServers();
  }

  @Override
  public String getPathStats(PathInfo path_info) throws NetworkAdminException {
    checkSecurity();
    return network.getPathStats(path_info);
  }

  @Override
  public PathInfo getPathInfoForPath(String path_name) throws NetworkAdminException {
    checkSecurity();
    return network.getPathInfoForPath(path_name);
  }

  @Override
  public MachineProfile[] getManagerServers() {
    checkSecurity();
    return network.getManagerServers();
  }

  @Override
  public String getManagerDebugString(ServiceAddress manager_server) throws NetworkAdminException {
    checkSecurity();
    return network.getManagerDebugString(manager_server);
  }

  @Override
  public MachineProfile getMachineProfile(ServiceAddress address) {
    checkSecurity();
    return network.getMachineProfile(address);
  }

  @Override
  public DataAddress[] getHistoricalPathRoots(ServiceAddress root, PathInfo path_info, long timestamp, int max_count) throws NetworkAdminException {
    checkSecurity();
    return network.getHistoricalPathRoots(root, path_info, timestamp, max_count);
  }

  @Override
  public Map<ServiceAddress, String> getBlocksStatus() throws NetworkAdminException {
    checkSecurity();
    return network.getBlocksStatus();
  }

  @Override
  public MachineProfile[] getBlockServers() {
    checkSecurity();
    return network.getBlockServers();
  }

  @Override
  public ServiceAddress[] getBlockServerList(BlockId block_id) throws NetworkAdminException {
    checkSecurity();
    return network.getBlockServerList(block_id);
  }

  @Override
  public BlockId[] getBlockList(ServiceAddress block) throws NetworkAdminException {
    checkSecurity();
    return network.getBlockList(block);
  }

  @Override
  public long getBlockGUID(ServiceAddress block) throws NetworkAdminException {
    checkSecurity();
    return network.getBlockGUID(block);
  }

  @Override
  public long[] getAnalyticsStats(ServiceAddress server) throws NetworkAdminException {
    checkSecurity();
    return network.getAnalyticsStats(server);
  }

  @Override
  public String[] getAllPathNames() throws NetworkAdminException {
    checkSecurity();
    return network.getAllPathNames();
  }

  @Override
  public MachineProfile[] getAllMachineProfiles() {
    checkSecurity();
    return network.getAllMachineProfiles();
  }

  @Override
  public void deregisterRoot(ServiceAddress root) throws NetworkAdminException {
    checkSecurity();
    network.deregisterRoot(root);
  }

  @Override
  public void deregisterManager(ServiceAddress root) throws NetworkAdminException {
    checkSecurity();
    network.deregisterManager(root);
  }

  @Override
  public void deregisterBlock(ServiceAddress block) throws NetworkAdminException {
    checkSecurity();
    network.deregisterBlock(block);
  }

  @Override
  public void addPathToNetwork(String path_name, String consensus_fun, ServiceAddress root_leader, ServiceAddress[] root_servers) throws NetworkAdminException {
    checkSecurity();
    network.addPathToNetwork(path_name, consensus_fun, root_leader, root_servers);
  }

  @Override
  public void addBlockAssociation(BlockId block_id, long server_guid) throws NetworkAdminException {
    checkSecurity();
    network.addBlockAssociation(block_id, server_guid);
  }

}
