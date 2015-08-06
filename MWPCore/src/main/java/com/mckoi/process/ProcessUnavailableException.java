/**
 * com.mckoi.process.ProcessUnavailableException  Dec 7, 2012
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

package com.mckoi.process;

/**
 * An exception thrown when a process is not available because of either a
 * network or machine failure, or other downtime.
 *
 * @author Tobias Downer
 */

public class ProcessUnavailableException extends Throwable {

  private final Reason reason;
  private final ProcessServiceAddress location;
  
  public ProcessUnavailableException(String message,
                                  Reason reason, ProcessServiceAddress addr,
                                  Throwable cause) {
    super(message, cause);
    this.reason = reason;
    this.location = addr;
  }

  public ProcessUnavailableException(String message,
                                  Reason reason, ProcessServiceAddress addr) {
    super(message);
    this.reason = reason;
    this.location = addr;
  }

  /**
   * The reason for this unavailable exception.
   */
  public Reason getReason() {
    return reason;
  }
  
  /**
   * The address of the service that is down.
   */
  public ProcessServiceAddress getLocation() {
    return location;
  }

  /**
   * The types of unavailable reasons,
   */
  public enum Reason {

    // The process is reported as being unavailable,
    UNAVAILABLE,
    
    // A query failed to respond within an acceptible timeout period,
    TIMEOUT,
    // The service is known to be unavailable because of periodic heartbeat
    // checks,
    NO_HEARTBEAT,

  }

}
