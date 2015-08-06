/**
 * com.mckoi.mwpcore.SecureMckoiDDBAccess  Sep 29, 2012
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

import com.mckoi.data.KeyObjectTransaction;
import com.mckoi.data.TreeReportNode;
import com.mckoi.network.CommitFaultException;
import com.mckoi.network.DataAddress;
import com.mckoi.network.DiscoveredNodeSet;
import com.mckoi.network.MckoiDDBAccess;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * An implementation of MckoiDDBAccess that wraps another MckoiDDBAccess and
 * performs a security check before any function is used.
 *
 * @author Tobias Downer
 */

abstract class SecureMckoiDDBAccess implements MckoiDDBAccess {

  private final MckoiDDBAccess ddb_client;
  
  SecureMckoiDDBAccess(MckoiDDBAccess ddb_client) {
    this.ddb_client = ddb_client;
  }

  abstract void checkSecurity();

  @Override
  public void setMaximumTransactionNodeCacheHeapSize(long size_in_bytes) {
    checkSecurity();
    ddb_client.setMaximumTransactionNodeCacheHeapSize(size_in_bytes);
  }

  @Override
  public String[] queryAllNetworkPaths() {
    checkSecurity();
    return ddb_client.queryAllNetworkPaths();
  }

  @Override
  public DataAddress performCommit(String path_name, DataAddress proposal) throws CommitFaultException {
    checkSecurity();
    return ddb_client.performCommit(path_name, proposal);
  }

  @Override
  public long getMaximumTransactionNodeCacheHeapSize() {
    checkSecurity();
    return ddb_client.getMaximumTransactionNodeCacheHeapSize();
  }

  @Override
  public DataAddress[] getHistoricalSnapshots(String path_name, long time_start, long time_end) {
    checkSecurity();
    return ddb_client.getHistoricalSnapshots(path_name, time_start, time_end);
  }

  @Override
  public DataAddress getCurrentSnapshot(String path_name) {
    checkSecurity();
    return ddb_client.getCurrentSnapshot(path_name);
  }

  @Override
  public String getConsensusFunction(String path_name) {
    checkSecurity();
    return ddb_client.getConsensusFunction(path_name);
  }

  @Override
  public DataAddress flushTransaction(KeyObjectTransaction transaction) {
    checkSecurity();
    return ddb_client.flushTransaction(transaction);
  }

  @Override
  public void disposeTransaction(KeyObjectTransaction transaction) {
    checkSecurity();
    ddb_client.disposeTransaction(transaction);
  }

  @Override
  public void discoverNodesInSnapshot(PrintWriter warning_log, DataAddress root_node, DiscoveredNodeSet discovered_node_set) {
    checkSecurity();
    ddb_client.discoverNodesInSnapshot(warning_log, root_node, discovered_node_set);
  }

  @Override
  public KeyObjectTransaction createTransaction(DataAddress root_node) {
    checkSecurity();
    return ddb_client.createTransaction(root_node);
  }

  @Override
  public KeyObjectTransaction createEmptyTransaction() {
    checkSecurity();
    return ddb_client.createEmptyTransaction();
  }

  @Override
  public DataAddress createEmptyDatabase() {
    checkSecurity();
    return ddb_client.createEmptyDatabase();
  }

  @Override
  public TreeReportNode createDiagnosticGraph(KeyObjectTransaction t) throws IOException {
    checkSecurity();
    return ddb_client.createDiagnosticGraph(t);
  }

}
