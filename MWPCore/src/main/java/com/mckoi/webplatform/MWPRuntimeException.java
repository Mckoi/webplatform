/**
 * com.mckoi.webplatform.MWPRuntimeException  Jul 19, 2010
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

import java.text.MessageFormat;

/**
 * A message formatted runtime exception.
 *
 * @author Tobias Downer
 */

public class MWPRuntimeException extends RuntimeException {

  public MWPRuntimeException(String msg) {
    super(MessageFormat.format(msg, new Object[0]));
  }

  public MWPRuntimeException(Throwable e) {
    super(e);
  }

  public MWPRuntimeException(String msg, Throwable e) {
    super(MessageFormat.format(msg, new Object[0]), e);
  }

  public MWPRuntimeException(String msg, Object params) {
    this(MessageFormat.format(msg, new Object[] { params }));
  }
  public MWPRuntimeException(String msg, Object param1, Object param2) {
    this(MessageFormat.format(msg, new Object[] { param1, param2 }));
  }
  public MWPRuntimeException(String msg, Object param1, Object param2, Object param3) {
    this(MessageFormat.format(msg, new Object[] { param1, param2, param3 }));
  }

  public MWPRuntimeException(String msg, Object params, Throwable e) {
    this(MessageFormat.format(msg, new Object[] { params }), e);
  }
  public MWPRuntimeException(String msg, Object param1, Object param2, Throwable e) {
    this(MessageFormat.format(msg, new Object[] { param1, param2 }), e);
  }
  public MWPRuntimeException(String msg, Object param1, Object param2, Object param3, Throwable e) {
    this(MessageFormat.format(msg, new Object[] { param1, param2, param3 }), e);
  }

  public MWPRuntimeException(String msg, Object[] params) {
    this(MessageFormat.format(msg, params));
  }
  public MWPRuntimeException(String msg, Object[] params, Throwable e) {
    super(MessageFormat.format(msg, params), e);
  }

}
