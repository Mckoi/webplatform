/**
 * com.mckoi.mwpcore.ClassNameValidator  Feb 28, 2012
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

package com.mckoi.mwpcore;

/**
 * A simple interface that validates a class name. Class name validation is
 * used to determine which classes are allowed to be accessed under different
 * circumstances.
 *
 * @author Tobias Downer
 */

public interface ClassNameValidator {

  /**
   * Returns true if the given class name is validated. The class name follows
   * the ClassLoader specification for binary class names.
   */
  boolean isAcceptedClass(String class_name);

  /**
   * Returns true if the given resource is validated. The resource name is a
   * file name with '/' separating each path element, following the ClassLoader
   * specification for resource names.
   */
  boolean isAllowedResource(String name);

}
