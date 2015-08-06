/**
 * com.mckoi.process.JavaProcessOperation  Nov 30, 2012
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

import com.mckoi.apihelper.BroadcastSWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * An abstract implementation of ProcessOperation that provides various
 * convenience methods for implementing a ProcessOperation in the Java
 * environment. It provides a mechanism for handling broadcast listeners and
 * callback closures when interacting with other processes.
 * <p>
 * This implementation assumes that once a process operation of this class
 * is created, a function call with the message arguments '.init' will be
 * invoked by the creator. Additionally, if a message with arguments of
 * '.kill' is received, the kill signal will be set and it is expected for
 * this process to stop if it is currently executing.
 * <p>
 * This implementation will implicitly create a reply message when the
 * 'handleFunctionInvoke' method returns. If the handleFunctionInvoke throws
 * a runtime exception then this will return a failure message with the
 * exception to the callee. If 'handleFunctionInvoke' returns null then a
 * success message with a null message will be returned.
 *
 * @author Tobias Downer
 */

public abstract class JavaProcessOperation implements ProcessOperation {

  /**
   * The current instance.
   */
  private ProcessInstance instance;

  /**
   * Reply map,
   */
  protected Map<Integer, OnReplyClosure> call_id_closures;

  /**
   * Channel listeners.
   */
  protected Map<ProcessChannel, ProcessChannelListener> channel_listeners;

  /**
   * Returns the current instance state map.
   */
  public StateMap getStateMap() {
    return instance.getStateMap();
  }

  /**
   * Returns the current instance process id.
   */
  public ProcessId getId() {
    return instance.getId();
  }

  /**
   * Closes the current instance.
   */
  public void close() {
    instance.close();
  }

  /**
   * Broadcasts a message to the given broadcast channel of this process
   * instance.
   */
  public void broadcastMessage(int channel_number, ProcessMessage message) {
    instance.broadcastMessage(channel_number, message);
  }

  /**
   * Returns a Broadcaster that sends messages to the given channel number of
   * this instance.
   */
  public BroadcastSWriter.Broadcaster getBroadcaster(final int channel_num) {
    return new BroadcastSWriter.Broadcaster() {
      @Override
      public void broadcastMessage(ProcessMessage msg) {
        JavaProcessOperation.this.broadcastMessage(channel_num, msg);
      }
    };
  }

  /**
   * Invokes a function on the given process id, and when a reply is received
   * the given closure is called.
   */
  public void invokeFunction(ProcessId process_id,
                             ProcessMessage msg, OnReplyClosure on_reply) {

    boolean reply_expected = on_reply != null;
    InstanceProcessClient process_client = instance.getInstanceProcessClient();
    // Invoke the function,
    int call_id =
            process_client.invokeFunction(process_id, msg, reply_expected);
    // If reply expected then add to the call id closures,
    if (reply_expected) {
      if (call_id_closures == null) {
        call_id_closures = new HashMap();
      }
      call_id_closures.put(call_id, on_reply);
    }

  }

  /**
   * Invokes a function on the given process id. Does not expect a reply to
   * the function call.
   */
  public void invokeFunction(ProcessId process_id, ProcessMessage msg) {
    invokeFunction(process_id, msg, null);
  }

  /**
   * Schedules a timed callback message on this process operation, and provides
   * the given ProcessMessage to the OnReplyClosure when the callback happens.
   * The callback will happen after 'time_wait_ms' milliseconds has passed.
   */
  public void scheduleCallback(
              long time_wait_ms, ProcessMessage msg, OnReplyClosure on_call) {

    // Schedule callback,
    int call_id = instance.scheduleCallback(time_wait_ms, msg);
    if (call_id_closures == null) {
      call_id_closures = new HashMap();
    }
    call_id_closures.put(call_id, on_call);

  }

  /**
   * Schedules a timed callback on this process operation. The callback will
   * happen after 'time_wait_ms' milliseconds has passed.
   */
  public void scheduleCallback(long time_wait_ms, OnReplyClosure on_call) {
    scheduleCallback(time_wait_ms,
                     ByteArrayProcessMessage.nullMessage(), on_call);
  }

  /**
   * Sets the broadcast listener for given process channel. If there is
   * already a listener set for this channel then an IllegalStateException is
   * thrown. If 'listener' is null then any broadcast listener set for the
   * given channel is removed.
   * <p>
   * Once a broadcast listener is set, the listener will be notified of all
   * messages broadcast on the given process channel.
   */
  public void setProcessChannelListener(ProcessChannel process_channel,
                                        ProcessChannelListener listener)
                                          throws ProcessUnavailableException {

    if (channel_listeners == null) {
      if (listener == null) {
        return;
      }
      channel_listeners = new HashMap();
    }

    ProcessChannelListener current_listener =
                                        channel_listeners.get(process_channel);

    if (current_listener == null) {
      if (listener != null) {
        channel_listeners.put(process_channel, listener);
        InstanceProcessClient pclient = instance.getInstanceProcessClient();
        pclient.addChannelListener(process_channel);
      }
    }
    else {
      if (listener == null) {
        InstanceProcessClient pclient = instance.getInstanceProcessClient();
        pclient.removeChannelListener(process_channel);
        channel_listeners.remove(process_channel);
      }
      else {
        throw new IllegalStateException(
                                "Listener already set on " + process_channel);
      }
    }

  }

  /**
   * Sets the broadcast listener for given process channel using the given
   * state object. The messages notified on the listener will never have
   * happened before the given sequence state. If there is already a listener
   * set for this channel then an IllegalStateException is thrown. If
   * 'listener' is null then any broadcast listener set for the given channel
   * is removed.
   * <p>
   * Once a broadcast listener is set, the listener will be notified of all
   * messages broadcast on the given process channel.
   */
  public void setProcessChannelListener(
        ChannelSessionState session_state, ProcessChannelListener listener)
                                          throws ProcessUnavailableException {

    if (channel_listeners == null) {
      if (listener == null) {
        return;
      }
      channel_listeners = new HashMap();
    }

    ProcessChannel process_channel = session_state.getProcessChannel();
    ProcessChannelListener current_listener =
                                        channel_listeners.get(process_channel);

    if (current_listener == null) {
      if (listener != null) {
        channel_listeners.put(process_channel, listener);
        InstanceProcessClient pclient = instance.getInstanceProcessClient();
        pclient.addChannelListener(session_state);
      }
    }
    else {
      if (listener == null) {
        channel_listeners.remove(process_channel);
        InstanceProcessClient pclient = instance.getInstanceProcessClient();
        pclient.removeChannelListener(process_channel);
      }
      else {
        throw new IllegalStateException(
                                "Listener already set on " + process_channel);
      }
    }

  }
  
  /**
   * Removes any broadcast listener set on the given process channel.
   */
  public void removeProcessChannelListener(ProcessChannel process_channel)
                                          throws ProcessUnavailableException {
    setProcessChannelListener(process_channel, null);
  }



  @Override
  public Type getType() {
    // Default type if TRANSIENT,
    return Type.TRANSIENT;
  }

  /**
   * Called when a signal is sent to this process operation.
   */
  public void handleSignal(String[] signal) {
    // Default is to do nothing when a signal is received,
  }

  /**
   * Handles a function invoke call on this process. Returns the ProcessMessage
   * to respond to the function invoke, or null if replying a null message. A
   * reply is only sent if the client is expecting a reply.
   */
  public abstract ProcessMessage handleFunctionInvoke(ProcessInputMessage msg);

  /**
   * Performs a resume on this process operation. By default this does nothing.
   * Override this to perform custom resume behaviour.
   */
  public void doResume(StateMap state) {
    // Default is to do nothing,
  }

  /**
   * Performs a suspend on this process operation. By default this does nothing.
   * Override this to perform custom suspend behaviour.
   */
  public void doSuspend(StateMap state) {
    // Default is to do nothing,
  }

  /**
   * Handles function callback (message types; RETURN, RETURN_EXCEPTION,
   * TIMED_CALLBACK).
   */
  protected void handleFunctionCallback(ProcessInputMessage msg) {

    // Route this message to any closures that are interested in it,
    if (call_id_closures != null) {
      // Get the call_id,
      int call_id = msg.getCallId();

      OnReplyClosure closure = call_id_closures.remove(call_id);
      if (closure != null) {
        // Execute the closure,
        closure.run(msg);
      }
    }

  }

  /**
   * Handles broadcast event (message type; BROADCAST).
   */
  protected void handleBroadcastEvent(ProcessInputMessage msg) {

    if (channel_listeners != null) {

      // Get the ProcessChannel object,
      ProcessChannel ch = msg.getBroadcastSessionState().getProcessChannel();

      // Notify any listeners of this event,
      ProcessChannelListener listener = channel_listeners.get(ch);
      if (listener != null) {
        listener.messageReceived(msg);
      }

    }

  }

  // ----------

  @Override
  public final void function(ProcessInstance instance) {

    // Set the instance,
    this.instance = instance;

    // Consume and dispatch the message as appropriate,
 
    // Any pending signals are handled first,
    while (true) {
      String[] signal = instance.consumeSignal();
      if (signal == null) {
        break;
      }
      handleSignal(signal);
    }

    // Process messages,
    while (true) {

      ProcessInputMessage msg = instance.consumeMessage();
      if (msg == null) {
        // Loop end condition,
        break;
      }

      ProcessInputMessage.Type type = msg.getType();

      // Function invoke,
      if (type == ProcessInputMessage.Type.FUNCTION_INVOKE) {

        try {
          ProcessMessage reply_msg = handleFunctionInvoke(msg);
          if (reply_msg == null) {
            reply_msg = ByteArrayProcessMessage.nullMessage();
          }
          instance.sendReply(msg, reply_msg);
        }
        catch (RuntimeException e) {
          // Send and log failure message,
          instance.sendFailure(msg, e, true);
        }
        catch (Error e) {
          // Send and log failure message,
          instance.sendFailure(msg, e, true);
        }

      }
      // Broadcast messages,
      else if (type == ProcessInputMessage.Type.BROADCAST) {

        // Handle receiving a broadcast event,
        handleBroadcastEvent(msg);

      }
      // Return or timed callbacks,
      else if (type == ProcessInputMessage.Type.RETURN ||
               type == ProcessInputMessage.Type.RETURN_EXCEPTION ||
               type == ProcessInputMessage.Type.TIMED_CALLBACK) {

        // Handle function callback event,
        handleFunctionCallback(msg);

      }
    }

  }

  @Override
  public final void resume(ProcessInstance instance) {
    // Set the instance,
    this.instance = instance;
    // Perform the resume,
    doResume(instance.getStateMap());
  }

  @Override
  public final void suspend(StateMap state) {
    // Perform the suspend,
    doSuspend(state);
  }

}
