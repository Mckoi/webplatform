/**
 * com.mckoi.mwpbase.CommandWriter  Sep 27, 2011
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

import com.mckoi.util.StyledPrintWriter;
import java.util.Map;

/**
 * A command writer is used to output information to the client terminal
 * display. A CommandWriter provides an interface for issuing a series of
 * instructions that the client performs for updating the UI on the client.
 *
 * @author Tobias Downer
 */

public interface CommandWriter extends StyledPrintWriter {

  /**
   * Clears the terminal window and resets the position any display elements
   * are show to the top of the terminal window. This may not do anything
   * in some terminal modes (where the user has decided the history must
   * be preserved).
   */
  void cls();

  /**
   * Outputs a series of HTML elements at the current cursor position.
   */
  @Override
  void print(Object str);

  /**
   * Outputs a series of HTML elements at the current cursor position and
   * closes the HTML block element.
   */
  @Override
  void println(Object str);

  /**
   * Outputs a series of HTML elements at the current cursor position and
   * encloses the elements with a 'span' tag with the given class style.
   */
  @Override
  void print(Object str, String style);

  /**
   * Outputs a series of HTML elements at the current cursor position and
   * encloses the elements with a 'span' tag with the given class style. Also
   * closes the HTML block element.
   */
  @Override
  void println(Object str, String style);

  /**
   * Outputs an empty line of default height and closes the HTML block element.
   */
  @Override
  void println();

  /**
   * Prints the given exception with the 'error' style.
   */
  @Override
  void printException(Throwable e);

  /**
   * Flush the current commands to the display.
   */
  @Override
  void flush();

  /**
   * Prints an extended collapsible error message to the console. Such an error
   * message has three parts, the "message_line", the "title_block" and an
   * "extended_block". The "message_line" is always displayed. The
   * "title_block" is initially displayed below the "message_line", and the UI
   * provides a widget to toggle the "title_block" with the "extended_block".
   * The "extended_block" contains a detailed stack trace.
   * <p>
   * Either a String or Throwable object types can be used for title_block
   * and extended_block. In the case of a Throwable being used, the stack
   * trace of the exception is converted into a String.
   * 
   * @param message_line
   * @param title_block either a String or a Throwable object. Can be null.
   * @param extended_block either a String or a Throwable object. Can be null.
   */
  void printExtendedError(
            String message_line, Object title_block, Object extended_block);

  /**
   * Creates a HTML element that's written to the client's console window as
   * straight HTML.
   * 
   * @param html_text
   * @return 
   */
  Object createHtml(String html_text);

  /**
   * Creates the HTML for a link to an external application. This is used to
   * create a link in the console that, when clicked on, will open a new window
   * or tab in the browser at the given location.
   * 
   * @param label
   * @param relative_url_query
   * @return 
   */
  Object createApplicationLink(String label, String relative_url_query);

  /**
   * The HTML for a link that, when clicked, will call back on the same frame
   * with the given 'callback_command' as the command line input. This allows
   * for the creation of navigational UI elements where, when the user clicks
   * on a link the state is changed (ie. traversal through a list).
   * <p>
   * A callback command invoked this way will always be preceeded by a hash
   * ('#') character. A hash character should not be included in the
   * 'callback_command' string.
   * 
   * @param label
   * @param callback_command
   * @return 
   */
  Object createCallbackLink(String label, String callback_command);

  /**
   * Produces the HTML for an input field. An input field is a UI element
   * that the user can enter a line of text, such as a username, password,
   * description, etc.
   * <p>
   * The content of an input field is put into the command's
   * EnvironmentVariable object when a link is navigated to or a button
   * pressed.
   * 
   * @param var
   * @param size
   * @param limit
   * @return 
   */
  Object createInputField(String var, int size, int limit);

  /**
   * Produces the HTML for an input field. An input field is a UI element
   * that the user can enter a line of text, such as a username, password,
   * description, etc.
   * <p>
   * The content of an input field is put into the command's
   * EnvironmentVariable object when a link is navigated to or a button
   * pressed.
   * 
   * @param var
   * @param size
   * @return 
   */
  Object createInputField(String var, int size);

  /**
   * Produces the HTML for a button that, when clicked, will call back to the
   * same command this writer is linked to. The given params are applied to the
   * environment variables on the callback for this button.
   * 
   * @param label
   * @param params
   * @return 
   */
  Object createCallbackButton(String label, Map<String, String> params);

  /**
   * Asks the client to ensure the given 'javascript_file' script is loaded
   * and to run the given 'invocation_function'. The purpose of this is to
   * allow implementation of client side user interface elements. The
   * client side script is provided a DOM to lay out its own components,
   * a function that's called when messages are broadcast to this frame,
   * and a function that can be invoked by the program to send messages to
   * the server-side program.
   * 
   * @param javascript_file
   * @param invocation_function
   * @param command_str
   */
  void runScript(String javascript_file,
                 String invocation_function, String command_str);

  /**
   * Asks the client to switch to the frame with the given name. This is used
   * to programmatically switch the client to a new window. For example, an
   * application may want the user to only have one particular application open
   * at once, and if the application is invoked twice the client should switch
   * to the existing application using this command.
   * <p>
   * The 'frame_value' is the number of the frame to switch to.
   * 
   * @param frame_value
   */
  void switchToFrame(String frame_value);

  /**
   * Sends a general response to a custom script running on the client. This
   * is used by the server to send custom messages to a client script. The
   * format of the message is entirely dependent on the application.
   * <p>
   * Messages should be limited to no more than about 40k characters. Anything
   * larger should be split into multiple messages. Messages sent using this
   * will be received by the client in the same order they are sent.
   * 
   * @param message
   */
  void sendGeneral(String message);

}
