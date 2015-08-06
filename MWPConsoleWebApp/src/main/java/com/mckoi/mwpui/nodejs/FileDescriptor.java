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

import com.mckoi.data.DataFile;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.webplatform.FileRepository;

/**
 * File descriptor concrete object.
 *
 * @author Tobias Downer
 */
public class FileDescriptor {

  private final FileRepository fs;
  private final FileInfo file_info;
  private final boolean can_write;
  private DataFile data_file;

  FileDescriptor(FileRepository fs, FileInfo file_info, boolean can_write) {
    this.fs = fs;
    this.file_info = file_info;
    this.can_write = can_write;
  }

  public DataFile getDataFile() {
    if (data_file == null) {
      data_file = file_info.getDataFile();
      data_file.position(0);
    }
    return data_file;
  }
  public FileInfo getFileInfo() {
    return file_info;
  }
  public boolean canWrite() {
    return can_write;
  }

}
