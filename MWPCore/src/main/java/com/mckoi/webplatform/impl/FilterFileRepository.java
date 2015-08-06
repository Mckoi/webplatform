/**
 * com.mckoi.webplatform.impl.FilterFileRepository  Nov 3, 2012
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

package com.mckoi.webplatform.impl;

import com.mckoi.data.DataFile;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.webplatform.FileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * A filtered file repository that shows a new repository id.
 *
 * @author Tobias Downer
 */

class FilterFileRepository implements FileRepository {

  private final String repository_id;
  private final FileRepository backed;
  
  FilterFileRepository(String new_repository_id, FileRepository backed) {
    this.backed = backed;
    this.repository_id = new_repository_id;
  }

  @Override
  public String getRepositoryId() {
    return repository_id;
  }

  @Override
  public void updateFileInfo(String file_name, String mime_type_new, long last_modified_new) {
    backed.updateFileInfo(file_name, mime_type_new, last_modified_new);
  }

  @Override
  public void touchFile(String file_name, long last_modified) {
    backed.touchFile(file_name, last_modified);
  }

//  @Override
//  public boolean synchronizeFile(InputStream file_in1, long local_file_len, long local_modified_time, String mime_type, String remote_file) throws IOException {
//    return backed.synchronizeFile(file_in1, local_file_len, local_modified_time, mime_type, remote_file);
//  }

  @Override
  public void renameFile(String file_name_current, String file_name_new) {
    backed.renameFile(file_name_current, file_name_new);
  }

  @Override
  public void renameDirectory(String path_name_current, String path_name_new) {
    backed.renameDirectory(path_name_current, path_name_new);
  }

  @Override
  public boolean removeDirectory(String path_name) {
    return backed.removeDirectory(path_name);
  }

  @Override
  public void makeDirectory(String path_name) {
    backed.makeDirectory(path_name);
  }

  @Override
  public List<FileInfo> getSubDirectoryList(String dir) {
    return backed.getSubDirectoryList(dir);
  }

  @Override
  public List<FileInfo> getFileList(String dir) {
    return backed.getFileList(dir);
  }

  @Override
  public FileInfo getFileInfo(String item_name) {
    return backed.getFileInfo(item_name);
  }

  @Override
  public List<FileInfo> getDirectoryFileInfoList(String dir) {
    return backed.getDirectoryFileInfoList(dir);
  }

  @Override
  public DataFile getDataFile(String file_name) {
    return backed.getDataFile(file_name);
  }

  @Override
  public boolean deleteFile(String file_name) {
    return backed.deleteFile(file_name);
  }

  @Override
  public void createFile(String file_name, String mime_type, long last_modified) {
    backed.createFile(file_name, mime_type, last_modified);
  }

  @Override
  public void copyFile(String file_name_source, String file_name_dest) {
    backed.copyFile(file_name_source, file_name_dest);
  }

  @Override
  public void commit() throws CommitFaultException {
    backed.commit();
  }

}
