/**
 * com.mckoi.webplatform.rhino.JSProcessUnavailableException  Dec 7, 2012
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

package com.mckoi.webplatform.rhino;

import com.mckoi.process.ProcessUnavailableException;

/**
 * A runtime exception thrown when a process service is unavailable.
 *
 * @author Tobias Downer
 */

public class JSProcessUnavailableException extends RuntimeException {

  public JSProcessUnavailableException(ProcessUnavailableException cause) {
    super(cause);
  }

}
