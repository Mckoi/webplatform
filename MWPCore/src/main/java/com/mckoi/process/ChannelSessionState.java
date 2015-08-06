/**
 * com.mckoi.process.ChannelSessionState  Nov 15, 2012
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
 * An encoded String that describes a ProcessChannel and a sequence value that
 * describes a serialization of the state of a broadcast channel.
 *
 * @author Tobias Downer
 */

public class ChannelSessionState implements Comparable<ChannelSessionState> {

  private final String state_string;

  /**
   * Constructs the session state from the given string.
   */
  public ChannelSessionState(String state_string) {
    if (state_string == null) throw new NullPointerException();
    this.state_string = state_string;
  }

  
  
  /**
   * Encodes a session state string given a process id, channel number,
   * and sequence value.
   */
  public static ChannelSessionState encodeSessionState(
                         ProcessChannel process_channel, long sequence_value) {

    // The format will be '[process id][hex channel]:[hex sequence value]'
    ProcessId id = process_channel.getProcessId();
    int channel = process_channel.getChannel();
    StringBuilder b = new StringBuilder();
    b.append(id.getStringValue());
    b.append(Integer.toString(channel, 16));
    b.append(":");
    b.append(Long.toString(sequence_value, 16));
    
    return new ChannelSessionState(b.toString());

  }

  public ProcessChannel getProcessChannel() {
    int delim = state_string.indexOf(":", 24);
    if (delim == -1) {
      throw new IllegalStateException("Invalid format");
    }
    return new ProcessChannel(state_string.substring(0, delim));
    
//    String process_id_str = state_string.substring(0, 24);
//    String rest = state_string.substring(24);
//    int delim = rest.indexOf(":");
//    if (delim == -1) {
//      throw new RuntimeException("Invalid format");
//    }
//    String channel_str = rest.substring(0, delim);
//
//    ProcessId process_id = ProcessId.fromString(process_id_str);
//    int channel = Integer.parseInt(channel_str, 16);
//
//    return new ProcessChannel(process_id, channel);
  }

  public String getStateStringMinusSequence() {
    int delim = state_string.indexOf(":", 24);
    if (delim == -1) {
      throw new RuntimeException("Invalid format");
    }
    return state_string.substring(0, delim);
  }

  public long getSequenceValue() {
    int delim = state_string.indexOf(":", 24);
    if (delim == -1) {
      throw new RuntimeException("Invalid format");
    }
    String seq_str = state_string.substring(delim + 1);

    return Long.parseLong(seq_str, 16);
  }

  @Override
  public String toString() {
    return state_string;
  }

  @Override
  public boolean equals(Object obj) {
    return state_string.equals(((ChannelSessionState) obj).state_string);
  }

  @Override
  public int hashCode() {
    return state_string.hashCode();
  }

  @Override
  public int compareTo(ChannelSessionState o) {
    // This will compare sequence values when the rest of the id is the same,
    // This is so that a newer state will compare greater than an older state
    // in all cases.
    String str_this = getStateStringMinusSequence();
    String str_o = o.getStateStringMinusSequence();
    int c = str_this.compareTo(str_o);
    if (c != 0) {
      return c;
    }
    // Compare sequence values,
    long sval_this = getSequenceValue();
    long sval_o = o.getSequenceValue();
    if (sval_this > sval_o) {
      return 1;
    }
    else if (sval_this < sval_o) {
      return -1;
    }
    return 0;
  }

}
