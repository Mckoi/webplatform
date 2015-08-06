/**
 * com.mckoi.process.impl.ProcessChannel  Apr 24, 2012
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
 * A process id and channel number of a broadcast channel. This object is used
 * when listening to a channel on a process.
 *
 * @author Tobias Downer
 */

public class ProcessChannel {

  private final ProcessId process_id;
  private final int channel_num;

  /**
   * Constructor.
   */
  public ProcessChannel(ProcessId process_id, int channel_num) {
    if (process_id == null) throw new NullPointerException();
    this.process_id = process_id;
    this.channel_num = channel_num;
  }

  /**
   * Constructs the channel from an encoded string (the decoded form is
   * returned via 'toString()').
   */
  public ProcessChannel(String encoded_str) {
    String process_id_str = encoded_str.substring(0, 24);
    String channel_str = encoded_str.substring(24);

    this.process_id = ProcessId.fromString(process_id_str);
    this.channel_num = Integer.parseInt(channel_str, 16);
  }

  /**
   * The ProcessId of the broadcast channel.
   */
  public ProcessId getProcessId() {
    return process_id;
  }

  /**
   * Returns the broadcast channel number.
   */
  public int getChannel() {
    return channel_num;
  }

  // -----

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(process_id.getStringValue());
    b.append(Integer.toString(channel_num, 16));
    return b.toString();
  }

  @Override
  public boolean equals(Object obj) {
    final ProcessChannel other = (ProcessChannel) obj;
    if (!process_id.equals(other.process_id)) {
      return false;
    }
    if (channel_num != other.channel_num) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 97 * hash + process_id.hashCode();
    hash = 97 * hash + channel_num;
    return hash;
  }

}
