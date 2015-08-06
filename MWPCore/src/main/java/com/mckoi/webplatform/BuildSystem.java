/**
 * com.mckoi.webplatform.BuildSystem  Apr 17, 2011
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

import com.mckoi.util.StyledPrintWriter;
import java.util.List;

/**
 * The API for an object that manages web application projects in a file
 * repository. The directory layout for web application projects are based
 * on the standard Maven webapp archtype.
 * <p>
 * Implementations must be able to compile projects to the Java web framework
 * standards (ie. compile JSP, Servlets, etc from source).
 *
 * @author Tobias Downer
 */

public interface BuildSystem {

  /**
   * Creates a web application project directory at the given path in the
   * file repository. Returns true if the project was successfully created.
   * The repository must be committed for the changes to be made.
   * <p>
   * Note that this method will only create the directory structure and provide
   * a simple example project at the project path location. It will not made
   * any changes to the system files for presenting the project by the
   * web application infrastructure.
   */
  boolean createWebApplicationProject(
              FileRepository repository, String project_path,
              StyledPrintWriter result_out);

  /**
   * Builds a web application workspace project at the given path in the file
   * repository. Returns true if the project was successfully built, false
   * otherwise. The repository must be committed for the changes to be made.
   * <p>
   * This builds every file in the source directory, and copies any
   * resources (such as class libraries) into the build/ subdirectory of the
   * project directory structure.
   */
  boolean buildWebApplicationProject(
              FileRepository repository,
              String project_path,
              StyledPrintWriter result_out,
              List<BuildError> errors);

  /**
   * Builds the JSP pages in an application's build directory in the given
   * repository. Returns true if the application's JSP pages were successfully
   * built, false otherwise. The repository must be committed for the changes
   * to be made.
   * <p>
   * This is intended as a way to complete a web application binary
   * installation that contains JSP pages. It converts all the .jsp pages into
   * .java files and writes the source files into the WEB-INF/classes
   * directory, then compiles the .java files into .class files.
   */
  boolean buildWebApplicationJSPPages(
              FileRepository repository,
              String build_path,
              StyledPrintWriter result_out,
              List<BuildError> errors);

  /**
   * Manipulates the data structures in the file repository such that the
   * web application server infrastructure will force a reload of the web
   * application that is being presented at the given path. This should be
   * used after a successful build so that any updates made will propagate
   * uniformly across the web server infrastructure.
   * <p>
   * When Servlets are first presented by a web server, the infrastructure
   * loads the class into memory from the image in the file repository at the
   * time the class is loaded. The infrastructure will not reload unless it is
   * told there is a change by calling this method. If this method is not
   * called then a version of a class will be presented from the web server
   * that was loaded before the change.
   * <p>
   * Using this method effectively flushes any previously loaded classes from
   * all the web servers.
   * <p>
   * Returns true if the 'reload' was successful, or false otherwise. If the
   * reload was not successful the most likely problem was that the
   * reloaded project was not found in the web applications list.
   * 
   * @param repository the application repository.
   * @param project_path the project path relative to repository root. (eg.
   *   "/apps/console/")
   * @param result_out for message output (can be null).
   * @return true if the FileRepository was updated.
   */
  boolean reloadWebApplicationAt(
              FileRepository repository,
              String project_path,
              StyledPrintWriter result_out);

  /**
   * Rebuilds the 'webapps.properties' file in the system directory of the
   * user's file repository. This ensures that any changes made to the
   * 'webapps.xml' file are propagated across the server infrastructure. It
   * is necessary to call this after the webapps.xml file is updated.
   * <p>
   * Returns true if the build was successful. Otherwise returns false and
   * a description any problems are output to the given PrintWriter.
   */
  boolean buildWebApplicationConfig(
              FileRepository repository, StyledPrintWriter out);



}
