/**
 * com.mckoi.webplatform.BuildSystemImpl  Jul 9, 2010
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

import com.mckoi.appcore.UserApplicationsSchema;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.util.StyledPrintWriter;
import com.mckoi.webplatform.BuildError;
import com.mckoi.webplatform.BuildSystem;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.buildtools.WebAppProjectBuilder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

/**
 * This object supports the API for building and compiling web application
 * projects.
 *
 * @author Tobias Downer
 */

final class BuildSystemImpl implements BuildSystem {

  /**
   * The name of the account.
   */
  private final String account_name;

  /**
   * Constructor.
   */
  BuildSystemImpl(String account_name) {
    this.account_name = account_name;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean createWebApplicationProject(
          FileRepository repository, String project_path,
          StyledPrintWriter result_out) {

    return WebAppProjectBuilder.createProject(
                                      repository, project_path, result_out);

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean buildWebApplicationProject(
          final FileRepository repository,
          final String project_path,
          final StyledPrintWriter result_out,
          final List<BuildError> errors) {

    // Wrap the build function in a Privileged action,
    return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
      @Override
      public Boolean run() {
        return WebAppProjectBuilder.buildProject(
                   repository, project_path, result_out, errors);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean buildWebApplicationJSPPages(
          final FileRepository repository,
          final String build_path,
          final StyledPrintWriter result_out,
          final List<BuildError> errors) {

    // Wrap the build function in a Privileged action,
    return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
      @Override
      public Boolean run() {
        return WebAppProjectBuilder.buildProjectJSP(
                   repository, build_path, result_out, errors);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean reloadWebApplicationAt(
          final FileRepository repository,
          final String project_path,
          final StyledPrintWriter result_out) {

    // Cast to a FileSystem implementation
    // (NOTE: this will break custom implementations of FileRepository)
    FileRepositoryImpl repository_impl = (FileRepositoryImpl) repository;
    FileSystem file_sys = repository_impl.getBacked();

    FileName project_fname =
                      new FileName(repository.getRepositoryId(), project_path);

    UserApplicationsSchema.reloadSystemWebAppsAt(
                              file_sys, project_fname.toString(), result_out);

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean buildWebApplicationConfig(final FileRepository repository,
                                           final StyledPrintWriter result_out) {

    // Cast to a FileSystem implementation
    // (NOTE: this will break custom implementations of FileRepository)
    FileRepositoryImpl repository_impl = (FileRepositoryImpl) repository;
    FileSystem file_sys = repository_impl.getBacked();
    boolean b =
            UserApplicationsSchema.buildSystemWebApps(file_sys, result_out);
    return b;

  }

}
