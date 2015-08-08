/**
 * com.mckoi.webplatform.impl.LogSystemImpl  Nov 9, 2011
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
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.webplatform.LogEventsSet;
import com.mckoi.webplatform.LogSystem;
import com.mckoi.webplatform.MWPRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * An implementation of LogSystem.
 *
 * @author Tobias Downer
 */

class LogSystemImpl implements LogSystem {

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  private final String account_name;
  private final LoggerService log_system;

  /**
   * Constructor.
   */
  LogSystemImpl(DBSessionCache sessions_cache,
               String account_name, LoggerService log_system) {
    this.sessions_cache = sessions_cache;
    this.account_name = account_name;
    this.log_system = log_system;
  }



  /**
   * Returns the set of all log types in this context.
   */
  @Override
  public List<String> getLogTypes() {
    // Query the database for the set of logs.

    // The path where the log files are located.
    // TODO: Should logs be on their own partition?
    String log_path = "ufs" + account_name;

    // Create a transaction for the path where the logs are stored,
    ODBTransaction log_transaction =
                        sessions_cache.getODBTransaction(log_path);

    // Query the list of log types and return as a Java string.

    // Get the log root,
    ODBObject log_root = log_transaction.getNamedItem("logroot");
    // Get the list of logs,
    ODBList log_list = log_root.getList("logs");
    // Cap at 32 types
    int sz = Math.min(32, (int) log_list.size());
    // Turn it into an ArrayList,
    ArrayList<String> log_types = new ArrayList<>(sz);
    for (int i = 0; i < sz; ++i) {
      // Get the type name
      String type_name = log_list.getObject(i).getString("name");
      log_types.add(type_name);
    }

    // Return the list,
    return log_types;

  }

  /**
   * Returns a log events viewer for the given log type on this account.
   */
  @Override
  public LogEventsSet getLogEventsSet(String log_type) {

    // Sanity check on the log_type,
    LoggerService.checkLogName(log_type);

    // The path where the log files are located.
    // TODO: Should logs be on their own partition?
    String log_path = "ufs" + account_name;

    // Create a transaction for the path where the logs are stored,
    ODBTransaction log_transaction =
                               sessions_cache.getODBTransaction(log_path);

    // Create and return the events set object,
    return new AccountLogEventsSet(log_transaction, log_type);

  }

  /**
   * {@inheritDoc }
   */
  @Override
  public void log(Level lvl, String log_type, String message, String... args) {
    // Check the log type,
    if (log_type.equals("app") || log_type.startsWith("app.")) {
      log_system.log(lvl, log_type, message, args);
    }
    else {
      throw new MWPRuntimeException("Unable to log to: {0}", log_type);
    }
  }

}
