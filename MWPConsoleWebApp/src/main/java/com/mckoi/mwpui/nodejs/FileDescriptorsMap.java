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
package com.mckoi.mwpui.nodejs;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Maps an integer to FileDescriptor object within some sort of state.
 *
 * @author Tobias Downer
 */
public class FileDescriptorsMap {

  private final Map<Integer, FileDescriptor> file_descriptor_map = new HashMap<>();
  // Random number generator for generating file descriptor integers.
  private final Random rng;

  public FileDescriptorsMap() {
    this.rng = new Random(System.currentTimeMillis());
  }

  /**
   * Returns a unique integer for a FileDescriptor.
   * 
   * @param fd
   * @return 
   */
  public int mapFD(FileDescriptor fd) {
    int fd_number = 0;
    // Generate a unique number,
    int try_count = 2048;
    // Cap search incase we filled the pool,
    while (try_count > 0) {
      // Generate a random key,
      fd_number = rng.nextInt(Integer.MAX_VALUE - 128) + 128;
      // Is it already taken?
      if (!file_descriptor_map.containsKey(fd_number)) {
        break;
      }
      --try_count;
    }
    // If id pool exhausted,
    if (fd_number < 128 || try_count == 0) {
      throw new RuntimeException("Unable to generate unique file descriptor id");
    }
    file_descriptor_map.put(fd_number, fd);
    return fd_number;
  }

  /**
   * Returns the FileDescriptor for the given number.
   * 
   * @param fd_id
   * @return 
   */
  public FileDescriptor getFD(int fd_id) {
    return file_descriptor_map.get(fd_id);
  }

  /**
   * Removes a FileDescriptor from the map.
   * 
   * @param fd_id
   */
  public void removeFD(int fd_id) {
    file_descriptor_map.remove(fd_id);
  }

}
