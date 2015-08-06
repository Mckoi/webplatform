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
public class GeneralNodeStates {
  
  /**
   * The general stats object that's passed to the native layer by the Node
   * API. Use this to construct stat reply objects.
   */
  private GJSObject fs_stats_ob;

  /**
   * Sets the file system stats object.
   * 
   * @param ob 
   */
  public void setFsStatsObject(GJSObject ob) {
    this.fs_stats_ob = ob;
  }
  
  /**
   * Returns the file system stats object.
   * 
   * @return 
   */
  public GJSObject getFsStatsObject() {
    return fs_stats_ob;
  }
  
}
