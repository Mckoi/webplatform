/**
 * com.mckoi.process.AsyncServletProcessUtil  Nov 12, 2012
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

import com.mckoi.process.ProcessResultNotifier.CleanupHandler;
import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;

/**
 * A convenience utility that can create a ProcessResultNotifier used in an
 * asynchronous servlet as defined in the Servlet 3.0 ServletRequest
 * specification. If there are no messages available from a ChannelConsumer,
 * this sets up an asynchronous callback on the ServletRequest when the consume
 * method returns 'null'.
 * <p>
 * A Servlet dispatch will happen to notify the servlet that broadcast
 * messages are available, or a time out was reached. On new messages being
 * available, the 'CONSUME_STATUS_KEY' attribute will be set to
 * "available". The user servlet code would then call 'consumeFromChannel'
 * again to fetch the new messages. If a timeout occurs then a dispatch will
 * happen and the 'CONSUME_STATUS_KEY' attribute will be set to
 * "timeout".
 * <p>
 * An example of using this method;
 * <code>
 *   protected void doPost(HttpServletRequest request,
 *                         HttpServletResponse response)
 *                                    throws ServletException, IOException {
 * 
 *     // Get the channel status,
 *     Object ch_status =
 *                    request.getAttribute(ChannelConsumer.CONSUME_STATUS);
 *     // If null then this is an initial call.
 *     // If "available" then it signifies messages are ready to be consumed.
 *     if (ch_status == null || ch_status.equals("available")) {
 *       // Create the consumer,
 *       ChannelConsumer consumer = ....
 *       // Consume one message,
 *       Collection&lt;ProcessMessage&gt; messages =
 *                consumer.consumeFromChannel(1,
 *                           AsyncServletProcessUtil.createNotifier(request));
 *       // Return if no messages available,
 *       if (messages == null) {
 *         return;
 *       }
 *       // Otherwise handle the messages,
 * 
 *       ....
 * 
 *     }
 *     else if (ch_status.equals("timeout")) {
 *       // Handle the timeout condition,
 * 
 *       ....
 * 
 *     }
 *   }
 * </code>
 * <p>
 * This method uses the Asynchronous API in the Servlet 3.0 specification.
 * By default the timeout callback will occur after 3 minutes. If a
 * different timeout value is desired, javax.servlet.AsyncContext can
 * be used to change the timeout value. For example,
 * <pre>
 *       // Consume one message,
 *       Collection&lt;ProcessMessage&gt; messages =
 *                consumer.consumeFromChannel(1,
 *                           AsyncServletProcessUtil.createNotifier(request));
 *       // Return if no messages available,
 *       if (messages == null) {
 *         // Set timeout to 15 seconds,
 *         request.getAsyncContext().setTimeout(15 * 1000);
 *         return;
 *       }
 * </pre>
 *
 * @author Tobias Downer
 */

public class AsyncServletProcessUtil {

  /**
   * The attribute key for the status of a consume operation set in the
   * Servlet context.
   */
  public final static String CONSUME_STATUS_KEY =
                    "com.mckoi.process.AsyncServletProcessUtil.consume_status";

  /**
   * When the CONSUME_STATUS_KEY attribute is set to CONSUME_STATUS_TIMEOUT
   * it means the timeout limit was reached (default timeout is 3 minutes) or
   * the process consumer channel timeout was reached.
   */
  public final static String CONSUME_STATUS_TIMEOUT = "timeout";

  /**
   * When the CONSUME_STATUS_KEY attribute is set to CONSUME_STATUS_AVAILABLE
   * it means there is one or more messages waiting to be consumed on the
   * channel.
   */
  public final static String CONSUME_STATUS_AVAILABLE = "available";

  /**
   * When the CONSUME_STATUS_KEY attribute is set to CONSUME_STATUS_IOERROR
   * it means the connection to the process through the channel consumer
   * failed. Most typically this will happen because the network failed while
   * the servers remained running.
   */
  public final static String CONSUME_STATUS_IOERROR = "ioerror";

  /**
   * Miscellaneous error.
   */
  public final static String CONSUME_STATUS_MISC_ERROR = "miscerror";

  /**
   * Creates a ProcessResultNotifier that will put the given ServletRequest
   * into asynchronous mode (see the Servlet 3.0 spec) and perform a call-back
   * dispatch when new messages are made available on the associated broadcast
   * channel.
   * 
   * @param request
   * @return 
   */
  public static ProcessResultNotifier createNotifier(
                                                final ServletRequest request) {
    
    return new ProcessResultNotifier() {

      @Override
      public void init(CleanupHandler timeout_handler) {
        // Otherwise there are no messages waiting so put the servlet request
        // in asynchronous mode,
        AsyncContext async_context = request.startAsync();
        // Add a listener that performs a callback dispatch on the request when
        // the async_context either times out or completes,
        async_context.addListener(new PMAsyncListener(timeout_handler));
        // Default timeout is set to 3 minutes,
        async_context.setTimeout(3 * 60 * 1000);
      }

      @Override
      public void notifyMessages(Status status) {
        // Dispatch 'available' status attribute,
        if (status == Status.MESSAGES_WAITING) {
          request.setAttribute(CONSUME_STATUS_KEY, CONSUME_STATUS_AVAILABLE);
        }
        // Dispatch timeout,
        // Note that this happens only when the client process API decides it
        //   has been waiting for a message for too long. Typically this will
        //   happen after 20 minutes.
        //
        // This shouldn't get called under default operation because the
        //   servlet context timeout happens after 3 minutes.
        else if (status == Status.TIMEOUT) {
          request.setAttribute(CONSUME_STATUS_KEY, CONSUME_STATUS_TIMEOUT);
        }
        // Dispatch other errors,
        else if (status == Status.IO_ERROR) {
          request.setAttribute(CONSUME_STATUS_KEY, CONSUME_STATUS_IOERROR);
        }
        // Default,
        else {
          request.setAttribute(CONSUME_STATUS_KEY, CONSUME_STATUS_MISC_ERROR);
        }
        request.getAsyncContext().dispatch();
      }
      
    };
    
  }

  /**
   * An AsyncListener that performs a callback dispatch when either the
   * context completes or times out (setting an appropriate attribute on the
   * request).
   */
  private static final class PMAsyncListener implements AsyncListener {

    private final CleanupHandler cleanup_handler;

    private PMAsyncListener(CleanupHandler cleanup_handler) {
      this.cleanup_handler = cleanup_handler;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
      // Dispatch a 'timeout' status attribute,
      // On timeout we remove the notifier from the queue,
      cleanup_handler.detach();
      // And dispatch,
      ServletRequest request = event.getSuppliedRequest();
      request.setAttribute(CONSUME_STATUS_KEY, CONSUME_STATUS_TIMEOUT);
      event.getAsyncContext().dispatch();
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
      onTimeout(event);
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
    }

  };

}
