/**
 * com.mckoi.webplatform.PlatformContext  Apr 17, 2011
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

import com.mckoi.odb.util.FileName;
import com.mckoi.process.AppServiceProcessClient;
import com.mckoi.process.InstanceProcessClient;

/**
 * Provides secure access to various Mckoi Web Platform related data sources.
 *
 * @author Tobias Downer
 */

public interface PlatformContext {

  /**
   * Returns the current version of the platform context.
   * 
   * @return 
   */
  public Version getVersion();

  /**
   * Returns the account name of the current thread context.
   * 
   * @return 
   */
  public String getAccountName();

  /**
   * Returns the name of this web application context.
   * 
   * @return 
   */
  public String getWebApplicationName();

  /**
   * Returns a LogControl object for this web application context.
   * 
   * @return 
   */
  public LogControl getLogControl();

  /**
   * Returns a FileRepository object used to access the file system of the
   * account of this context. This is the same as
   * 'getFileRepository(getAccountName())'.
   * 
   * @return 
   */
  public FileRepository getFileRepository();

  /**
   * Returns the FileRepository with the given repository id, or null if the
   * repository is not accessible to this account, the repository does not
   * exist, or there's no repository in the FileName object.
   * 
   * @param repository_id
   * @return 
   */
  public FileRepository getFileRepository(String repository_id);

  /**
   * Returns the FileRepository for the given FileName, or null if the
   * repository is not accessible to this account, the repository does not
   * exist, or there's no repository in the FileName object.
   * 
   * @param file_name
   * @return 
   */
  public FileRepository getFileRepositoryFor(FileName file_name);

  /**
   * Returns a SessionAuthenticator object used to authenticate users in this
   * context.
   * 
   * @return 
   */
  public SessionAuthenticator getSessionAuthenticator();

  /**
   * Returns a UserManager object used to manager the users on an account.
   * 
   * @return 
   */
  public UserManager getUserManager();

  /**
   * Returns an object that builds/updates internal structures based on the
   * state of the source files in the file system.
   * 
   * @return 
   */
  public BuildSystem getBuildSystem();

  /**
   * Returns a DDBResourceAccess object that is used to access data stored via
   * the MckoiDDB data model API's. This method may fail if the user is not
   * permitted to access the system in this way. The returned object may also
   * be restricted in what can be done with the data.
   * 
   * @return 
   */
  public DDBResourceAccess getDDBResourceAccess();

  // ----- Class loaders -----

  /**
   * Returns the platform context ClassLoader. This is the class loader used
   * to load user-code and includes all the classes defined in the application
   * context.
   * 
   * @return 
   */
  public ClassLoader getApplicationClassLoader();

  /**
   * Returns the base class loader for this context (usually this will be the
   * parent of the application class loader). This does not include the
   * classes defined by the user application, but does include any static
   * libraries that all applications are provided.
   * 
   * @return 
   */
  public ClassLoader getContextClassLoader();

  // ----- Process Clients -----

  /**
   * Returns an AppServiceProcessClient object, used to communicate with
   * processes on the network from an application service.
   * <p>
   * This method will work depending on the situation of the context -
   * specifically it is only available to application services.
   * 
   * @return 
   */
  public AppServiceProcessClient getAppServiceProcessClient();

  /**
   * Returns an InstanceProcessClient object, used to communicate with
   * other processes on the network.
   * <p>
   * This method will work depending on the situation of the context -
   * specifically it is only available to a process operation.
   * 
   * @return 
   */
  public InstanceProcessClient getInstanceProcessClient();

  // ----- Logging System -----

  /**
   * Returns the log system for this context.
   * 
   * @return 
   */
  public LogSystem getLogSystem();

  // ----- Versions Enum -----

  /**
   * An enum of versions (returned by 'getCurrentVersion')
   */
  public enum Version {

    VER_1

  }

}
