/**
 * com.mckoi.webplatform.TaskQueue  Apr 17, 2011
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

import java.util.List;

/**
 * A task queue that is used to query scheduled tasks and add and remove tasks
 * from a queue to be run on the Mckoi Web Platform. Tasks are added to the
 * end of the queue and consumed from the start when resources are available
 * to execute the task. A TaskQueue may have a maximum rate at which tasks are
 * consumed.
 * <p>
 * The machine node where a task is performed is determined in an
 * implementation specific way.
 * <p>
 * On a network cluster, the TaskQueue must be resilient to machine failures.
 * If a task does not successfully run because of a machine failure it is
 * retried on another machine, or if a task fails to run the failure should be
 * clearly visible in a log.
 *
 * @author Tobias Downer
 */

public interface TaskQueue {

  /**
   * Adds a new task to the queue. The task to run is specified by a class
   * name that will be run under a class loader that is the same as the
   * application that posted the queue.
   */


  /**
   * Returns a snapshot of the name of all tasks currently on the queue.
   */
  List<String> getAllPendingTasks();







}
