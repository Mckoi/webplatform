/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mckoi.webplatform;

/**
 * The LogControl object controls whether the request that caused this function
 * to execute should automatically store in the logs a record of the event,
 * if the system has been set up to log the event. By default, the log_request
 * flag is true.
 * <p>
 * In some cases it's desirable to disable automatically logging if the
 * application manages logging on its own, or the request is not interesting
 * or happens too frequently to make logging of it worthwhile.
 *
 * @author Tobias Downer
 */
public interface LogControl {

  /**
   * Enables or disables automatically logging of this event, if the system
   * would normally log this event.
   * 
   * @param b false to disable automatic logging of the request event.
   */
  public void setAutomaticLogging(boolean b);

  /**
   * @return true if automatic logging is enabled, false otherwise.
   */
  public boolean isAutomaticLogging();

}
