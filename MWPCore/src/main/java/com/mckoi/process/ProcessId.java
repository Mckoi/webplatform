/**
 * com.mckoi.process.ProcessId  Mar 26, 2012
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

import com.mckoi.appcore.SystemStatics;

/**
 * A process id is a globally unique string that represents, and can be used
 * to locate, a process running in the system. The process id string is a
 * value that can not be reverse engineered to determine information about the
 * underlying network topology or the location the process is being run in
 * the system. Implementations control this by; a) making the system have to
 * lookup network topology related to the id key via a private database,
 * b) encrypting/hashing away information such as partitionality and
 * temporality of the lookup database key.
 * 
 * Encapsulates the information in the process identifier (the command_id
 * references the process system path it belongs to and the id value (a
 * temporal ordered value)).
 *
 * @author Tobias Downer
 */

public final class ProcessId implements Comparable<ProcessId> {

  /**
   * The system process path id.
   */
  private final byte path_val;

  /**
   * The high process id.
   */
  private final long high_id;
  
  /**
   * The low process id.
   */
  private final long low_id;

  /**
   * Constructs the ProcessId from binary data. 'path_val' is the partition
   * where the process information is located. 'high_id' is the high part of
   * the process id. 'low_id' is the low part of the process id.
   */
  public ProcessId(byte path_val, long high_id, long low_id) {
    this.path_val = path_val;
    this.high_id = high_id;
    this.low_id = low_id;
  }

  // ----- Getters -----

  public byte getPathValue() {
    return path_val;
  }

  public long getHighLong() {
    return high_id;
  }

  public long getLowLong() {
    return low_id;
  }

  /**
   * Returns the process id as a string, which can be used as a globally
   * unique identifier. The id string returned here will be 24 characters
   * in length.
   */
  public String getStringValue() {
    StringBuilder b = new StringBuilder();
    // The path value,
    int pval = ((int) path_val) & 0x0FF;
    if (pval < 16) {
      b.append('0');
    }
    b.append(Integer.toHexString(pval));
    SystemStatics.encodeLongBase64(high_id, b);
    SystemStatics.encodeLongBase64(low_id, b);
    return b.toString();
  }

  /**
   * Given a process id string (from 'getStringValue'), returns a ProcessId
   * of the decoded string.
   */
  public static ProcessId fromString(String process_id_string) {
    String path_val_str = process_id_string.substring(0, 2);
    String code = process_id_string.substring(2);
    byte path_val = (byte) Integer.parseInt(path_val_str, 16);
    long high_v = SystemStatics.decodeLongBase64(code);
    long low_v = SystemStatics.decodeLongBase64(code.substring(11));
    return new ProcessId(path_val, high_v, low_v);
  }

  /**
   * Returns the name of the process path this process id is managed in.
   */
  public String getProcessPath() {
    StringBuilder b = new StringBuilder();
    b.append("sysprocess");
    // The path value,
    int pval = ((int) path_val) & 0x0FF;
    if (pval < 16) {
      b.append('0');
    }
    b.append(Integer.toHexString(pval));
    return b.toString();
  }

  @Override
  public String toString() {
    return getStringValue();
  }

  // -----

  @Override
  public int compareTo(ProcessId o) {
    return getStringValue().compareTo(o.getStringValue());
  }

  @Override
  public boolean equals(Object obj) {
    final ProcessId other = (ProcessId) obj;
    if ( this.path_val == other.path_val &&
         this.high_id == other.high_id &&
         this.low_id == other.low_id ) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 29 * hash + this.path_val;
    hash = 29 * hash + (int) (this.high_id ^ (this.high_id >>> 32));
    hash = 29 * hash + (int) (this.low_id ^ (this.low_id >>> 32));
    return hash;
  }

}
