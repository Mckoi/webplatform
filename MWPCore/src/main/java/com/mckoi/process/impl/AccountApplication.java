/**
 * com.mckoi.process.impl.AccountApplication  Mar 28, 2012
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

package com.mckoi.process.impl;

/**
 * An object that represents an application on an account.
 *
 * @author Tobias Downer
 */

class AccountApplication implements Comparable<AccountApplication> {

  /**
   * The account name.
   */
  private final String account_name;

  /**
   * The application name.
   */
  private final String app_name;

  /**
   * Constructor.
   */
  AccountApplication(String account_name, String app_name) {
    // Null check,
    if (account_name == null) throw new NullPointerException();
    if (app_name == null) throw new NullPointerException();

    this.account_name = account_name;
    this.app_name = app_name;
  }
  
  /**
   * Returns the account.
   */
  public String getAccountName() {
    return account_name;
  }
  
  /**
   * Returns the application name.
   */
  public String getApplicationName() {
    return app_name;
  }

  // -----

  @Override
  public int compareTo(AccountApplication o) {
    int c = account_name.compareTo(o.account_name);
    if (c == 0) {
      c = app_name.compareTo(o.app_name);
    }
    return c;
  }

  @Override
  public boolean equals(Object obj) {
    final AccountApplication other = (AccountApplication) obj;
    if (!account_name.equals(other.account_name)) {
      return false;
    }
    if (!app_name.equals(other.app_name)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 43 * hash + (account_name.hashCode());
    hash = 43 * hash + (app_name.hashCode());
    return hash;
  }

}
