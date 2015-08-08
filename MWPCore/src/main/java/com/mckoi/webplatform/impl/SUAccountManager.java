/**
 * com.mckoi.webplatform.SUAccountManager  Jul 25, 2010
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

/**
 * Super User account manager object.
 *
 * @author Tobias Downer
 */

final class SUAccountManager {

  /**
   * The constructor.
   */
  SUAccountManager() {
  }

//  private static void checkAccountPermission(String operation_type) {
//    SuperUserContext.checkAccountPermission();
//  }
//
//  /**
//   * Account names may only contain alpha-numeric characters.
//   */
//  private void checkAccountNameValue(String account_name) {
//    if (account_name.length() <= 3) {
//      throw new MWPRuntimeException(
//                                "Account name ({0}) too short", account_name);
//    }
//    for (int i = 0; i < account_name.length(); ++i) {
//      char c = account_name.charAt(i);
//      // If it's not a letter and not a digit, not a valid name,
//      if (!Character.isLetter(c) && !Character.isDigit(c)) {
//        throw new MWPRuntimeException(
//                "Account name ({0}) contains invalid characters",
//                account_name);
//      }
//    }
//  }
//
//  /**
//   * Administrative operation that adds a new account to the system. This will
//   * create a file system and add an account record to the system tables. This
//   * will not populate the account's file system with any data.
//   * <p>
//   * The machines on which the file system path will be allocated is decided
//   * by the the system manager.
//   * <p>
//   * If an account with the same name already exists an exception is
//   * generated.
//   */
//  public void createAccount(String account_name) {
//    // Check permissions
//    checkAccountPermission("account.create");
//    // Check the account name is valid
//    checkAccountNameValue(account_name);
//
//    // Start a transaction on the system path,
//    SDBTransaction transaction =
//            DBSessionManager.db().getSDBTransaction(SystemStatics.SYSTEM_PATH);
//
//    // The file system path,
//    String file_system_path = "ufs" + account_name;
//    String account_key = "a." + account_name;
//
//    // Does the account file exist?
//    if (transaction.fileExists(account_key)) {
//      throw new MWPRuntimeException("Account exists: {0}", account_name);
//    }
//
//    // Create the account file,
//    transaction.createFile(account_key);
//    SDBFile account_file = transaction.getFile(account_key);
//
//    // Check the file system path can be created,
//    if (system_manager.pathExists(file_system_path)) {
//      throw new MWPRuntimeException("Path exists: {0}", file_system_path);
//    }
//
//    // Ok, good to go, create the table entries for the account, etc.
//    SDBTable accounts_table =
//                           transaction.getTable(SystemStatics.ACCOUNT_TABLE);
//    accounts_table.insert();
//    accounts_table.setValue("account", account_name);
//    accounts_table.setValue("info", "");
//    accounts_table.complete();
//
//    // Create the accounts resource for the account file system,
//    SDBTable resources_table =
//                   transaction.getTable(SystemStatics.ACCOUNT_RESOURCES_TABLE);
//    resources_table.insert();
//    resources_table.setValue("account", account_name);
//    resources_table.setValue("path", file_system_path);
//    resources_table.setValue("resource_name", "$AccountFileRepository");
//    resources_table.setValue("type", "com.mckoi.sdb.SimpleDatabase");
//    resources_table.complete();
//
//    // Try and commit,
//    try {
//      transaction.commit();
//    }
//    catch (CommitFaultException e) {
//      // Rethrow a commit fault (should happen only in unusual situations).
//      throw new MWPRuntimeException(e);
//    }
//
//    // If commit was a success, ask the system manager to create the path for
//    // the file system.
//    system_manager.createPath(file_system_path,
//                              "com.mckoi.sdb.SimpleDatabase");
//
//    // Done.
//
//  }
//
//  /**
//   * Administrative operation that disables an account in the system. A
//   * disabled account may not be accessed.
//   */
//  public void disableAccount(String account_name, String reason) {
//    // Check permissions
//    checkAccountPermission("account.disable");
//
//    // Check the account name is valid
//    checkAccountNameValue(account_name);
//
//    reason = (reason == null) ? "" : reason;
//
//    // Start a transaction on the system path,
//    SDBTransaction transaction =
//            DBSessionManager.db().getSDBTransaction(SystemStatics.SYSTEM_PATH);
//
//    String account_key = "a." + account_name;
//
//    // Does the account file exist?
//    if (!transaction.fileExists(account_key)) {
//      throw new MWPRuntimeException(
//                                 "Account does not exists: {0}", account_name);
//    }
//
//    // Get the account file,
//    SDBFile account_file = transaction.getFile(account_key);
//
//    // Set the content of the file to 'disable'
//    // Clear the content
//    account_file.setSize(0);
//    try {
//      PrintWriter out = new PrintWriter(new OutputStreamWriter(
//                        DataFileUtils.asOutputStream(account_file), "UTF-8"));
//      out.println("DISABLED");
//      out.println(reason);
//    }
//    catch (IOException e) {
//      throw new MWPRuntimeException(e);
//    }
//
//    // Commit, and done.
//    try {
//      transaction.commit();
//    }
//    catch (CommitFaultException e) {
//      // Rethrow a commit fault (should happen only in unusual situations).
//      throw new MWPRuntimeException(e);
//    }
//  }
//
//  /**
//   * Administrative operation that enables an account that has previously
//   * been disabled.
//   */
//  public void enableAccount(String account_name, String reason) {
//    // Check permissions
//    checkAccountPermission("account.enable");
//
//    // Check the account name is valid
//    checkAccountNameValue(account_name);
//
//    reason = (reason == null) ? "" : reason;
//
//    // Start a transaction on the system path,
//    SDBTransaction transaction =
//            DBSessionManager.db().getSDBTransaction(SystemStatics.SYSTEM_PATH);
//
//    String account_key = "a." + account_name;
//
//    // Does the account file exist?
//    if (!transaction.fileExists(account_key)) {
//      throw new MWPRuntimeException(
//                                 "Account does not exists: {0}", account_name);
//    }
//
//    // Get the account file,
//    SDBFile account_file = transaction.getFile(account_key);
//
//    // Clear the content will enable the account,
//    account_file.setSize(0);
//
//    // Commit, and done.
//    try {
//      transaction.commit();
//    }
//    catch (CommitFaultException e) {
//      // Rethrow a commit fault (should happen only in unusual situations).
//      throw new MWPRuntimeException(e);
//    }
//  }
//
//  /**
//   * Returns a ZAccountInfo object that describes details about the given
//   * account name, or 'null' if the account does not exist. The returned
//   * account object is static and will not change if anything about the
//   * account is changed.
//   */
//  public ZAccountInfo getAccountInfo(String account_name) {
//    // Check permissions
//    checkAccountPermission("account.read");
//
//    // Start a transaction on the system path,
//    SDBTransaction transaction =
//            DBSessionManager.db().getSDBTransaction(SystemStatics.SYSTEM_PATH);
//
//    SDBTable account_table = transaction.getTable(SystemStatics.ACCOUNT_TABLE);
//    SDBIndex account_index = account_table.getIndex("account");
//    SDBIndex account_v =
//                     account_index.sub(account_name, true, account_name, true);
//
//    // None found, return false,
//    if (account_v.size() == 0) {
//      return null;
//    }
//
//    SDBRow account_row = account_v.first();
//    if (!account_row.getValue("account").equals(account_name)) {
//      // Oops!
//      throw new MWPRuntimeException(
//                               "getAccountInfo failed for {0}", account_name);
//    }
//
//    // Create the account object,
//    SUAccountInfo account_info = new SUAccountInfo(account_name);
//
//    // Get the info value,
//    account_info.setInfoString(account_row.getValue("info"));
//
//    // Find the resources on the account,
//    SDBTable account_resources =
//                   transaction.getTable(SystemStatics.ACCOUNT_RESOURCES_TABLE);
//    SDBIndex resources = account_resources.getIndex("account").sub(
//                                       account_name, true, account_name, true);
//    for (SDBRow row : resources) {
//      String path = row.getValue("path");
//      String resource_name = row.getValue("resource_name");
//      String path_type = row.getValue("type");
//      ZAccountResource resource =
//                         new SUAccountResource(path, resource_name, path_type);
//      account_info.addResource(resource);
//    }
//
//    // The vhosts owned by this account,
//    SDBTable vhosts = transaction.getTable(SystemStatics.VHOST_TABLE);
//    SDBIndex account_hosts = vhosts.getIndex("account").sub(
//                                       account_name, true, account_name, true);
//    for (SDBRow row : account_hosts) {
//      String domain_string = row.getValue("domain");
//      ZDomain domain = new SUDomain(domain_string);
//      account_info.addDomain(domain);
//    }
//
//  }
//
//  /**
//   * Links a resource with the given account name, where the resource has
//   * a name and path.
//   */
//  public void addAccountResource(String account_name,
//                                 String resource_name, String resource_path) {
//    // Check permissions
//    checkAccountPermission("account.resource.add");
//
//
//
//  }
//
//  /**
//   * Unlinks a resource from the given account name.
//   */
//  public void removeAccountResource(String account_name,
//                                    String resource_name) {
//    // Check permissions
//    checkAccountPermission("account.resource.remove");
//
//
//
//  }
//
//  /**
//   * Adds a domain to an account. It is assumed checks have been performed
//   * to ensure the domain is owned by the account holder. Example;
//   * addAccountDomain("toby", "http", "testaccount.mckoi.com")
//   */
//  public void addAccountDomain(String account_name,
//                               String host_protocol, String host_string) {
//    // Check permissions
//    checkAccountPermission("account.domain.add");
//
//
//
//  }
//
//  /**
//   * Removes a domain from an account. It is assumed checks have been
//   * performed to ensure the domain is owned by the account holder. Example;
//   * addAccountDomain("toby", "http", "testaccount.mckoi.com")
//   */
//  public void removeAccountDomain(String account_name,
//                                  String host_protocol, String host_string) {
//    // Check permissions
//    checkAccountPermission("account.domain.remove");
//
//
//
//  }




}
