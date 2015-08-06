/**
 * com.mckoi.process.ProcessOperation  Mar 2, 2012
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

package com.mckoi.process;

/**
 * A process operation that runs on some computation resource in the web
 * platform cluster. A process operation is associated with an account and
 * all functions on it are executed with a class loader context from the
 * user's file repository. A running process has much the same privs as a
 * web application servlet.
 * <p>
 * While a process is running, it can accept function calls from any number
 * of external callees. A callee may, for example, execute a function on the
 * process that changes its state, performs a timed operation, or performs a
 * wide array of other Web Platform API operations such as spawning a new
 * process. The process will typically use the JVM heap to manage state so
 * a process is ideal for managing frequently changing state without database
 * interaction.
 * <p>
 * A process should be considered volatile, meaning when the hardware running
 * the process fails then the heap state will be lost. On failure, web platform
 * implementations should start a copy of the process with the same
 * construction arguments on alternative working/connected hardware. To
 * account for failure/recovery, a process can periodically flush its state
 * to the database so that it can be recovered to a check point when
 * restarted.
 * <p>
 * A process can be a very light-weight object such that it needs to be
 * possible for there to be a unique process per each user session. This
 * allows for a user session to maintain some volatile state - the classic
 * example of this being the contents of a shopping cart for a store web
 * application.
 * <p>
 * The death of a process can happen for a number of reasons; 1) A callee has
 * not sent a message to the process for some time therefore it times out.
 * 2) A callee sends a message that disposes (kills) the process. 3) The
 * process ends because it's no longer needed.
 * <p>
 * A process may also be a complex function that interacts extensively with
 * the database and multiple user sessions. For example, a process may manage
 * a chat room with many users, a server-side application session (eg. a
 * console), the timing and state of events in an interactive game.
 * <p>
 * Clients and processes interact with each other via an asynchronous
 * messaging system. Once a process is created, one or more clients may
 * send messages to the process if they know the process id. Respectively, the
 * process may choose to send a message to the client at any time. Multi-cast
 * message output from a process is also supported through the broadcasting
 * mechanism. Process system implementation should optimize multi-cast
 * broadcasting so that traffic is minimized.
 * <p>
 * There are several types of processes that are treated by the system in
 * different ways. A description of the different types follows;
 * <p>
 * <ol>
 * <li>STATIC - A static process exists only for the time it takes to
 *   execute a function of the process. A static process will not suspend,
 *   resume or flush because it does not maintain any internal state. A STATIC
 *   process is used for batch processing operations (for example, a part of a
 *   map-reduce function). A STATIC process will not be usable again after it
 *   has executed the first function called on it.
 * </li>
 * <li>TRANSIENT - A transient process lives for as long as there are
 *   interactions with the process. When there is no interaction with a
 *   transient process, after a period of non-interaction the 'suspend' method
 *   is called and its state is made reclaimable by the GC. If an interaction
 *   is later needed with the process after it has been suspended, the process
 *   is resumed. A TRANSIENT process will be resumable.
 * </li>
 * <li>PERMANENT - A permanent process lives for as long as the process service
 *   on the server is up. A PERMANENT process will suspend/resume when the
 *   process service is gracefully shut down/start up, provided the process
 *   is resumable. A permanent process will also flush if the process is
 *   flushable. If a permanent process is not resumable and the process
 *   service shuts down or fails, all the state will be lost. A permanent
 *   process is useful when very dynamic/complex state needs to be managed.
 * </li>
 * </ol>
 *
 * @author Tobias Downer
 */

public interface ProcessOperation {

  /**
   * The Type enum for the different types of process operations.
   */
  public enum Type {

    /**
    * The STATIC process type (see class docs).
    */
    STATIC,
    
    /**
     * The TRANSIENT process type (see class docs).
     */
    TRANSIENT,
    
    /**
     * The PERMANENT process type (see class docs).
     */
    PERMANENT,

  }

  /**
   * Returns the process type (either STATIC, TRANSIENT or PERMANENT). See
   * the class documentation for information on how the system handles each
   * type of process.
   * 
   * @return 
   */
  public Type getType();

  /**
   * Returns true if this process is currently dormant, or false if it's
   * active. The process service may decide to suspend a dormant process if
   * it hasn't processed a message within some period of time or the code has
   * a new revision. If this method returns false then the process service will
   * not suspend the process for dormancy or code revision.
   * <p>
   * Note that this method returning false will not completely prevent the
   * 'suspend' method from being called. Suspend will still be called if the
   * process service is gracefully shut down.
   * <p>
   * This function is only ever checked by the process service immediately
   * before a suspend would happen because of dormancy/code revision.
   * <p>
   * An important use of this method is to keep a process active while it's
   * waiting on IO.
   * 
   * @return true if the process is dormant, false if active.
   */
  public boolean isDormant();

  /**
   * Suspends the process in preparation for it to be restarted on another
   * node, or when there is no activity on a process and it can be flushed
   * to the database so local resources can be reclaimed, or when there is a
   * code revision and the process needed to be resumed in a new class loader.
   * <p>
   * One important function of this method is to facilitate a controlled
   * relocation of the process. For example, if a machine on the network is
   * gracefully shut down then the suspend function is used on every process
   * on that machine.
   * <p>
   * In many cases, implementations of this will not need to do anything. Some
   * implementations may like to use this method to make sure any recent state
   * is stored in the StateMap. Note that a suspend call will not happen if
   * a process service catastrophically fails.
   * <p>
   * Suspend will only be called on a ProcessOperation if the type is
   * suspendable.
   * 
   * @param state
   */
  void suspend(StateMap state);

  /**
   * Resumes this process. This is called after a process is suspended.
   * Typically, this will load any check-pointed state from the 'state'
   * StateMap and set up the internal state as appropriate.
   * <p>
   * Note that this may sometimes be called even through the process has not
   * previously been suspended. For example, if there is a hardware problem
   * and a process service does not shut down gracefully, this will be called
   * to bring the process back up from the last check-pointed state.
   * <p>
   * Resume will only be called on a ProcessOperation if the type is
   * suspendable.
   * 
   * @param instance
   */
void resume(ProcessInstance instance);

  /**
   * Called by the process service when there is an item waiting on the
   * input message queue to be processed by this instance. Any client may
   * add a message to the input message queue for this process (provided the
   * client knows the process id). This object can consume messages and reply
   * to a function via the ProcessInstance object.
   * 
   * @param instance
   */
  void function(ProcessInstance instance);

}
