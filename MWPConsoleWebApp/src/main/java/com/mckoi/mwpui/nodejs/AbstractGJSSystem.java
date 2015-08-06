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

/**
 * 
 *
 * @author Tobias Downer
 */
public abstract class AbstractGJSSystem implements GJSSystem {

  private final String[] exec_args_v;
  private final String[] args_v;

  public AbstractGJSSystem(String[] exec_args_v, String[] args_v) {
    this.exec_args_v = exec_args_v;
    this.args_v = args_v;
  }

  @Override
  public String[] getExecArgsv() {
    return exec_args_v;
  }

  @Override
  public String[] getArgsv() {
    return args_v;
  }

  
  
  
}
