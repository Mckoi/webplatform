/**
 * com.mckoi.webplatform.MckoiDDBPath  Mar 4, 2012
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

/**
 * Represents a path accessible through the MckoiDDBAccess interface.
 *
 * @author Tobias Downer
 */

public final class MckoiDDBPath implements Comparable<MckoiDDBPath> {

  /**
   * The path name.
   */
  private final String path_name;

  /**
   * The path type (consensus function)
   */
  private final String consensus_function;

  /**
   * Constructor.
   */
  public MckoiDDBPath(String path_name, String consensus_function) {
    // Null checks,
    if (path_name == null) throw new NullPointerException();
    if (consensus_function == null) throw new NullPointerException();

    this.path_name = path_name;
    this.consensus_function = consensus_function;
  }

  public String getPathName() {
    return path_name;
  }

  public String getConsensusFunction() {
    return consensus_function;
  }

  @Override
  public String toString() {
    return path_name + " (" + consensus_function + ")";
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final MckoiDDBPath other = (MckoiDDBPath) obj;
    if (path_name.equals(other.path_name) &&
        consensus_function.equals(other.consensus_function)) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 41 * hash + (path_name.hashCode());
    hash = 41 * hash + (consensus_function.hashCode());
    return hash;
  }

  @Override
  public int compareTo(MckoiDDBPath o) {
    int c = path_name.compareTo(o.path_name);
    if (c == 0) {
      c = consensus_function.compareTo(o.consensus_function);
    }
    return c;
  }
  
}
