/**
 * com.mckoi.mwpbase.ServerCommand  Sep 27, 2011
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2010  Diehl and Associates, Inc.
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

package com.mckoi.mwpui;

import com.mckoi.process.ProcessInputMessage;

/**
 * A command executed on the server. A command may transition through
 * several states during its life.
 * <p>
 * For example, a command starts in an initial state where a dialog is
 * presented to the user. The user clicks on the 'next' link which executes
 * this same command but in state 2 where additional information is requested.
 *
 * @author Tobias Downer
 */

public interface ServerCommand {

  /**
   * String returned by 'init' functions that are to proceed in a separate
   * window on the client.
   */
  public static final String WINDOW_MODE = "W";

  /**
   * String returned by 'init' functions that are to proceed in the parent's
   * console window.
   */
  public static final String CONSOLE_MODE = "C";

  /**
   * Returns a string that represents the context of this server command
   * encoded as '[frame name]-[stack val]'. This is a unique string that
   * can be used to reference this server command.
   */
  String getReference();

  /**
   * Returns a string that describes the icon that should be used by the
   * client if this command is running in window mode. The string is
   * formatted as such: "icon_name:fill_color:mask_color", for example;
   * "file_base:#904000:#000000"
   * <p>
   * May return null if the default icon is to be used.
   */
  String getIconString();

  /**
   * Called on the command's invocation. The returned string indicates the
   * mode in which the command operates on the client. Returning "W"
   * indicates the command operates in its own terminal window. Returning
   * "C" indicates the command operates in the parents terminal context.
   */
  String init(ServerCommandContext ctx, EnvironmentVariables var);

  /**
   * Processes the command and outputs the terminal information to
   * the CommandWriter. Returns a status code to the client. The status code
   * determines the mode in which the client should continue.
   * <p>
   * Returning "STOP" indicates the client must 'terminate' any client side
   * resources associated with the command and return control to the program
   * that invoked the command. For example, if a command is run that outputs
   * a report and then returns.
   * <p>
   * Returning "STOP:['var1','var2']" indicates the client must 'terminate'
   * any client side resources associated with the command and return control
   * to the program that invoked the command. In addition, a JSON string array
   * follows the 'STOP:' with the list of environment variables that must be
   * exported to the parent environment system.
   * <p>
   * Returning "PROMPT" indicates the client must wait on input that is
   * entered by the user at the cursor. When the client hits 'enter', the
   * input text is placed into a field in the environment variables and a
   * callback is made to the same command.
   * <p>
   * Returning "WAIT" indicates the client should wait indefinitely. In such
   * a state the command will have provided other ways for the user to change
   * state by providing interactive UI elements that change the program
   * state.
   * <p>
   * Returning "WAIT:[milliseconds]:[params]" indicates the client should
   * wait the number of milliseconds given and then call back on the same
   * server command with the given parameters. This wait/callback is
   * interrupted and canceled if any other callback UI elements are invoked
   * (eg. a link is clicked on). This is used to provide updating
   * functionality. For example; a log display that is refreshed on the client
   * screen periodically.
   * <p>
   * If this method throws a runtime exception the exception message and stack
   * trace will be returned back to the client. If any exception is thrown by
   * this method then it is the same as returning 'STOP'.
   */
  String process(ServerCommandContext ctx,
                 EnvironmentVariables vars, CommandWriter out);

  /**
   * Performs an interactive function on this program. The purpose of this is
   * to support the implementation of interactive client-side features such as
   * tab-completion. An interactive function is invoked by the client much the
   * same way as the 'process' function is called. There are important
   * differences between this and 'process' however; a) 'interact' has no way
   * to access the console display itself, b) 'interact' is expected to not
   * do anything if a particular interactive feature is not supported, c) the
   * result of 'interact' is an arbitrary string.
   * <p>
   * For example, tab-completion is a popular feature in shell systems and to
   * support a feature the client might send an 'interact' command with
   * feature = 'tabcomplete'. On receiving the request, the 'interact' function
   * processes 'cline' from the environment vars and works out a list of
   * possible candidates to complete the command. The candidates list might be
   * returned as a JSON string and the user is presented with the list of
   * possible completion options.
   * 
   * @param ctx the ServerCommandContext.
   * @param vars current environment variables for this program.
   * @param feature the interactive feature identifier string.
   * @return the result of the interact feature to pass back to the client.
   *   Returns null if the feature is not supported.
   */
  String interact(ServerCommandContext ctx,
                  EnvironmentVariables vars, String feature);

  /**
   * Handles a timed or function callback, or broadcast message that this
   * command has informed the context that it is interested in. This would
   * typically be used to implement closures.
   * <P>
   * For example, a ServerCommand may use the
   * 'ServerCommandContext.addCallIdCallback' method and associate the call_id
   * with this server command. When the context receives a message with this
   * call_id, it will call this 'handle' method with the message.
   * <p>
   * This method returns a status code in exactly the same format at the status
   * code returned by 'process'. If this method returns 'STOP' then this
   * server command will terminate.
   * <p>
   * Additionally, this method may return 'null' which indicates that the
   * current state of the command as returned by the last 'process' method
   * should not change.
   */
  String handle(ServerCommandContext ctx,
                EnvironmentVariables vars, CommandWriter out,
                ProcessInputMessage input_message);

  /**
   * Returns true if this server command is holding active state that can not
   * currently be reconstructed via a process suspend/resume. For example, a
   * process may be holding complex game state, and moving the process to
   * a new class loader would cause important state to be lost.
   * <p>
   * A process with any active commands will not reload on project revision
   * or when the process has not received a message for a long time.
   * <p>
   * By default this should return false.
   * 
   * @return 
   */
  boolean isActive();

}
