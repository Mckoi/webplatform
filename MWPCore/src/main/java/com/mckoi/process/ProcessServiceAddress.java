/**
 * com.mckoi.process.ProcessServiceAddress  Dec 7, 2012
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

import com.mckoi.process.impl.ProcessServerService;

/**
 * An object that represents a machine running the MWP process service,
 * addressable from the network. Typically this is a string with the
 * 'private IP' of the process service.
 *
 * @author Tobias Downer
 */

public class ProcessServiceAddress implements Comparable<ProcessServiceAddress> {

  /**
   * The address of the machine.
   */
  private final String machine_addr;
  
  /**
   * Constructor.
   */
  public ProcessServiceAddress(String machine_addr) {
    if (machine_addr == null) {
      throw new NullPointerException();
    }
    this.machine_addr = machine_addr;
  }

  /**
   * The machine address string.
   */
  public String getMachineAddress() {
    return machine_addr;
  }

  /**
   * The machine port number.
   */
  public int getMachinePort() {
    return ProcessServerService.DEFAULT_PROCESS_TCP_PORT;
  }

  @Override
  public String toString() {
    return getEncodedString();
  }

  @Override
  public int compareTo(ProcessServiceAddress o) {
    int c = machine_addr.compareTo(o.machine_addr);
    return c;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 61 * hash + machine_addr.hashCode();
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    final ProcessServiceAddress other = (ProcessServiceAddress) obj;
    if (!machine_addr.equals(other.machine_addr)) {
      return false;
    }
    return true;
  }

  /**
   * Returns a string that is the encoded form of this address.
   */
  public String getEncodedString() {
    StringBuilder b = new StringBuilder();
    b.append(machine_addr);
    return b.toString();
  }

  /**
   * Return a ProcessServiceAddress decoded from the given encoded string
   * value (encoded with 'getEncodedString()').
   */
  public static ProcessServiceAddress decode(String encoded_str) {
    return new ProcessServiceAddress(encoded_str);
  }

}
