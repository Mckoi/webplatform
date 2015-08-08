/**
 * com.mckoi.webplatform.FileRepository  May 16, 2010
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
import com.mckoi.odb.ClassValidationException;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.odb.util.FileInfo;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * This class provides various methods for managing an account's file system,
 * such as uploading and download files and web app distributions.
 * <p>
 * Security: By default this object does not permit operations that require
 *   privileged actions such as communicating over a network. However, if a
 *   class extends this and also implements TrustedObject then secure
 *   operations are permitted.
 *
 * @author Tobias Downer
 */

final class FileRepositoryImpl 
                              implements com.mckoi.webplatform.FileRepository {

  /**
   * The backed file system.
   */
  private final com.mckoi.odb.util.FileSystemImpl backed;
  
  /**
   * The repository id.
   */
  private final String repository_id;

  /**
   * Constructor.
   */
  FileRepositoryImpl(String repository_id,
                     ODBTransaction transaction, String named_root) {
    if (repository_id == null) throw new NullPointerException();
    this.repository_id = repository_id;
    backed = new com.mckoi.odb.util.FileSystemImpl(transaction, named_root);
  }

  /**
   * Returns the backed 'com.mckoi.odb.util.FileSystemImpl' object.
   */
  com.mckoi.odb.util.FileSystemImpl getBacked() {
    return backed;
  }

  /**
   * Defines the class schema for the file system at the source location of
   * the ODBTransaction. This will generate an exception if the classes
   * already exist.
   */
  static void defineSchema(ODBTransaction t) throws ClassValidationException {
    com.mckoi.odb.util.FileSystemImpl.defineSchema(t);
  }

  /**
   * Sets up file system to an initial know (empty) state. The
   * repository must be committed after this is called. If the file system
   * is already setup then an exception is generated.
   */
  void setup(String filesystem_name, String filesystem_description) {
    backed.setup(filesystem_name, filesystem_description);
  }


  @Override
  public String getRepositoryId() {
    return repository_id;
  }

  /**
   * Creates an empty path directory if one doesn't exist. Recurses through
   * the path specification until all the directories have been created.
   */
  @Override
  public void makeDirectory(String path_name) {
    backed.makeDirectory(path_name);
  }

  /**
   * Deletes an empty path directory. Generates an exception if the directory
   * has files so can not be deleted, otherwise returns true if the directory
   * was deleted, false otherwise.
   */
  @Override
  public boolean removeDirectory(String path_name) {
    return backed.removeDirectory(path_name);
  }

  /**
   * Creates a file from the repository with the given name, mime type and
   * timestamp. The file is created empty. If the file doesn't exist,
   * generates an exception.
   */
  @Override
  public void createFile(String file_name,
                         String mime_type, long last_modified) {
    backed.createFile(file_name, mime_type, last_modified);
  }

  /**
   * Deletes a file from the repository with the given name. Returns true if
   * a file with the given name was found and deleted, otherwise returns
   * false.
   */
  @Override
  public boolean deleteFile(String file_name) {
    return backed.deleteFile(file_name);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void touchFile(String file_name, long last_modified) {
    backed.touchFile(file_name, last_modified);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateFileInfo(String file_name, String mime_type,
                             long last_modified) {
    backed.updateFileInfo(file_name, mime_type, last_modified);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public List<FileInfo> getDirectoryFileInfoList(String dir) {
    List<com.mckoi.odb.util.FileInfo> backed_list =
                                          backed.getDirectoryFileInfoList(dir);
    if (backed_list != null) {
      return new WrappedFileList(backed_list);
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<FileInfo> getFileList(String dir) {
    List<com.mckoi.odb.util.FileInfo> backed_list = backed.getFileList(dir);
    if (backed_list != null) {
      return new WrappedFileList(backed_list);
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<FileInfo> getSubDirectoryList(String dir) {
    List<com.mckoi.odb.util.FileInfo> backed_list =
                                           backed.getSubDirectoryList(dir);
    if (backed_list != null) {
      return new WrappedFileList(backed_list);
    }
    return null;
  }

  /**
   * Returns information about the given file object (file or directory)
   * stored in the repository, or null if the file isn't found.
   */
  @Override
  public FileInfo getFileInfo(String item_name) {
    com.mckoi.odb.util.FileInfo backed_fi = backed.getFileInfo(item_name);
    if (backed_fi != null) {
      return new WrappedFileInfo(backed_fi);
    }
    return null;
  }

  /**
   * Returns a DataFile of the contents of the given file name.
   */
  @Override
  public DataFile getDataFile(String file_name) {
    return backed.getDataFile(file_name);
  }

  @Override
  public void renameDirectory(String path_name_current, String path_name_new) {
    backed.renameDirectory(path_name_current, path_name_new);
  }

  /**
   * Renames a file object. This method will rename the file only - the
   * paths must be the same. For example, '/from/path/file.txt' can not be
   * renamed to '/to/path/file.txt'.
   */
  @Override
  public void renameFile(String file_name_current, String file_name_new) {
    backed.renameFile(file_name_current, file_name_new);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void copyFile(String file_name_source, String file_name_dest) {
    backed.copyFile(file_name_source, file_name_dest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit() throws CommitFaultException {
    backed.commit();
  }



  // ----- Inner classes -----

  private class WrappedFileInfo implements FileInfo {
    
    private final com.mckoi.odb.util.FileInfo backed;

    WrappedFileInfo(com.mckoi.odb.util.FileInfo backed) {
      this.backed = backed;
    }

    @Override
    public void setMimeType(String mime_type) {
      backed.setMimeType(mime_type);
    }

    @Override
    public void setLastModified(long last_modified) {
      backed.setLastModified(last_modified);
    }

    @Override
    public boolean isFile() {
      return backed.isFile();
    }

    @Override
    public boolean isDirectory() {
      return backed.isDirectory();
    }

    @Override
    public String getPathName() {
      return backed.getPathName();
    }

    @Override
    public String getMimeType() {
      return backed.getMimeType();
    }

    @Override
    public long getLastModified() {
      return backed.getLastModified();
    }

    @Override
    public String getItemName() {
      return backed.getItemName();
    }

    @Override
    public DataFile getDataFile() {
      return backed.getDataFile();
    }

    @Override
    public String getAbsoluteName() {
      return backed.getAbsoluteName();
    }

  }

  private class WrappedFileList extends AbstractList<FileInfo>
                                                      implements RandomAccess {

    private final List<com.mckoi.odb.util.FileInfo> backed;

    WrappedFileList(List<com.mckoi.odb.util.FileInfo> backed) {
      this.backed = backed;
    }

    @Override
    public int size() {
      return backed.size();
    }

    @Override
    public boolean isEmpty() {
      return backed.isEmpty();
    }

    @Override
    public FileInfo get(int index) {
      return new WrappedFileInfo(backed.get(index));
    }

  }

}
