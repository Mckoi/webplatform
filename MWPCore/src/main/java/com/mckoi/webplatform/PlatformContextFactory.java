/**
 * com.mckoi.webplatform.PlatformContextFactory  Apr 17, 2011
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

import com.mckoi.webplatform.impl.PlatformContextImpl;
import com.mckoi.webplatform.impl.SuperUserPlatformContextImpl;

/**
 * The factory method for producing PlatformContext objects to the API.
 *
 * @author Tobias Downer
 */

public final class PlatformContextFactory {

  /**
   * Create a PlatformContext instance for access to the Mckoi Web Platform
   * API.
   */
  public static PlatformContext getPlatformContext() {
    return new PlatformContextImpl();
  }

  /**
   * Returns a SuperUserPlatformContext instance for access to functions in the
   * Mckoi Web Platform API that only privileged accounts are able to use (for
   * example, modification of the web platform infrastructure).
   * <p>
   * Throws a SecurityException if the account is not permitted to create or
   * use the object.
   */
  public static SuperUserPlatformContext getSuperUserPlatformContext() {
    return new SuperUserPlatformContextImpl();
  }

}
