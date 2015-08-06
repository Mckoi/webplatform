/**
 * com.mckoi.webplatform.LogSystem  Nov 9, 2011
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

package com.mckoi.webplatform;

import java.util.List;
import java.util.logging.Level;

/**
 * A LogSystem provides various services for interacting with the logging
 * system. These services include viewing the log messages and generating
 * log messages.
 *
 * @author Tobias Downer
 */

public interface LogSystem {

  /**
   * Returns the set of all log types in this context.
   */
  List<String> getLogTypes();

  /**
   * Returns a log events viewer for the given log type on this account.
   */
  LogEventsSet getLogEventsSet(String log_type);

  /**
   * Outputs a message to the log. The log level, log type, message format,
   * and message arguments must all be provided. The message format is a
   * string that uses the Java Message.format class to represent the
   * message. eg. "Page error: {0}\n{1}". The arguments represents the
   * strings that make up the message. eg.
   * { "Number format exception", "Stack Trace.." }.
   * <p>
   * Note that some log types may not be allowed to be used. Some log types
   * are reserved for the system only, such as the hit log.
   */
  void log(Level lvl, String log_type, String message, String... args);

}
