/**
 * com.mckoi.process.impl.ProcessInstanceImpl  Mar 28, 2012
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

package com.mckoi.process.impl;

import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.ContextBuilder;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPUserClassLoader;
import com.mckoi.mwpcore.ThreadUsageStatics;
import com.mckoi.odb.ODBData;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.process.*;
import com.mckoi.process.ProcessInputMessage.Type;
import com.mckoi.process.ProcessResultNotifier.CleanupHandler;
import com.mckoi.webplatform.impl.LoggerService;
import com.mckoi.webplatform.impl.PlatformContextImpl;
import com.mckoi.webplatform.util.MonotonicTime;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

/**
 * A server side process instance.
 *
 * @author Tobias Downer
 */

final class ProcessInstanceImpl implements ProcessInstance {

  /**
   * The process log.
   */
  public static final Logger PROCESS_LOG =
                                     Logger.getLogger("com.mckoi.process.Log");

  /**
   * The noop ContextBuilder.
   */
  private static final ContextBuilder NO_CONTEXT;
  static {
    NO_CONTEXT = ContextBuilder.NO_CONTEXT;
  }

  /**
   * The backed process service.
   */
  private final ProcessServerService process_service;

  /**
   * The ProcessId object for this instance.
   */
  private final ProcessId process_id;
  private final String process_id_str;

  /**
   * The account application for this process instance.
   */
  private volatile AccountApplication account_application;
  
  /**
   * The fully qualified process class name.
   */
  private volatile String process_name;

  /**
   * The class loader initialization lock.
   */
  private final Object CL_LOCK = new Object();

  /**
   * A Reentrant lock used whenever user code is executed on this process
   * instance. This lock ensures multiple threads will not access the same
   * process instance simultaneously.
   */
  private final ReentrantLock USER_CODE_LOCK = new ReentrantLock();


  /**
   * True if code is currently being executed.
   */
  private boolean currently_executing_code = false;

  /**
   * The function queue for this instance, and associated lock.
   */
  private final FunctionQueue function_queue = new FunctionQueue();

  /**
   * A map of ProcessChannel to the object managing it.
   */
  private final Map<ProcessChannel, PIChannelListener> listener_map =
                                                              new HashMap<>();

  /**
   * Cached logger service.
   */
  private volatile LoggerService logger_service;

  
  /**
   * The user ProcessOperation object.
   */
  private ProcessOperation cached_process_object;

  /**
   * The process type as reported by the 'getType' method from the user
   * ProcessOperation object.
   */
  private ProcessOperation.Type process_type;

  /**
   * The cached process class loader.
   */
  private ProcessMckoiAppClassLoader cached_process_class_loader;

  /**
   * The time the process object was created.
   */
  private MonotonicTime process_object_create_timestamp;

  /**
   * True if this process instance is terminated.
   */
  private volatile boolean terminated = false;

  /**
   * True if this process instance is suspended.
   */
  private volatile boolean suspended;

  /**
   * True if the process instance class loader is out of date.
   */
  private volatile boolean version_out_of_date = false;

  /**
   * Set to true when a user operation is called on this instance.
   */
  private volatile boolean interacted_since_flush = false;

  /**
   * Timestamp when the last 'function' method was called.
   */
  private volatile long time_of_last_function;
  private volatile MonotonicTime monotonic_time_of_last_function;

  /**
   * The number of times the 'function' method has been called.
   */
  private volatile int function_call_count = 0;

  /**
   * The total number of nano seconds this process instance has used.
   */
  private volatile long function_cpu_time_nano = 0;

  /**
   * When true, this instance can not be cleaned up.
   */
  private final AtomicInteger prevent_remove = new AtomicInteger(0);

  /**
   * Atomic counter for 'function' calls where 'consumeMessage' is not called.
   */
  private final AtomicInteger no_consume_counter = new AtomicInteger(0);

  /**
   * A synchronized lock used when the instance is resumed and the
   * account_application and process_name values are set.
   */
  private final Object RESUME_LOCK = new Object();

  /**
   * A reference to BroadcastInstance that we can access atomically.
   */
  private volatile BroadcastInstance broadcast_instance;

  /**
   * The StateMap object for this instance.
   */
  private final StateMapImpl state_map;

  /**
   * Constructor.
   */
  ProcessInstanceImpl(ProcessServerService process_service,
                      ProcessId process_id,
                      AccountApplication account_app, String process_name) {
    // Null checks,
    if (process_service == null ||
        process_id == null ||
        account_app == null ||
        process_name == null) throw new NullPointerException();

    this.process_service = process_service;
    this.process_id = process_id;
    this.process_id_str = process_id.getStringValue();
    this.account_application = account_app;
    this.process_name = process_name;
    this.suspended = false;
    timestampLastFunctionCall();

    this.state_map = new StateMapImpl();

  }

  /**
   * Constructor for a resumable process (starts suspended).
   */
  ProcessInstanceImpl(ProcessServerService process_service,
                      ProcessId process_id) {

    // Null checks,
    if (process_service == null ||
        process_id == null) throw new NullPointerException();

    this.process_service = process_service;
    this.process_id = process_id;
    this.process_id_str = process_id.getStringValue();
    this.suspended = true;
    timestampLastFunctionCall();

    this.state_map = new StateMapImpl();

  }

  private void timestampLastFunctionCall() {
    time_of_last_function = System.currentTimeMillis();
    monotonic_time_of_last_function = MonotonicTime.now();
  }

  /**
   * Returns the BroadcastInstance.
   */
  private BroadcastInstance getBroadcastInstance() {
    if (broadcast_instance == null) {
      // Instantiate it,
      synchronized (RESUME_LOCK) {
        if (broadcast_instance == null) {
          broadcast_instance = new BroadcastInstance();
        }
      }
    }
    return broadcast_instance;
  }

  /**
   * Returns a string describing this process for debugging purposes.
   */
  String getPrintString() {
    StringBuilder b = new StringBuilder();
    if (account_application == null) {
      b.append("NOT RESUMED");
    }
    else {
      b.append(account_application.getApplicationName());
      b.append("(");
      b.append(account_application.getAccountName());
      b.append(") ");
      b.append(process_name);
    }
    return b.toString();
  }

  /**
   * Returns the process name (throws SuspendedProcessException if the instance
   * needs to be resumed).
   */
  String getProcessName() throws SuspendedProcessException {
    if (process_name == null) {
      throw new SuspendedProcessException();
    }
    return process_name;
  }

  /**
   * Returns the account application (throws SuspendedProcessException if the
   * instance needs to be resumed).
   */
  AccountApplication getAccountApplication() throws SuspendedProcessException {
    if (account_application == null) {
      throw new SuspendedProcessException();
    }
    return account_application;
  }

  /**
   * Returns true if this process instance is terminated.
   */
  boolean isTerminated() {
    return terminated;
  }

  /**
   * Returns true if this process instance is currently suspended.
   */
  boolean isSuspended() {
    return suspended;
  }

  /**
   * Returns true if this process instance is currently out of date.
   */
  boolean isOutOfDate() {
    return version_out_of_date;
  }

  /**
   * Returns the timestamp of the last time a 'function' call was made on
   * this instance.
   */
  long lastAccessTimestamp() {
    return time_of_last_function;
  }

  /**
   * Returns the number of times the 'function' method has been called since
   * the instance was created, or -1 if the Integer.MAX_VALUE limit number of
   * calls reached.
   */
  int functionCallCount() {
    return function_call_count;
  }

  /**
   * The number of nano seconds of cpu time spent within the 'function' user-
   * code. This is CPU time monitored for the thread, so should be an accurate
   * picture of CPU utilization regardless of how busy the machine is.
   */
  long functionCPUTimeNanos() {
    return function_cpu_time_nano;
  }

  /**
   * Returns true if the process is currently ready to be flushed. A process
   * may not be flushable if any of the following conditions are met;
   * 1) The instance is not initialized, 2) The instance was initialized too
   * recently, 3) The process type is not flushable, 4) No interaction with
   * the process has occurred, 5) The process is terminated.
   */
  boolean isCurrentlyFlushable() {
    // Not flushable if terminated,
    if (isTerminated()) {
      return false;
    }
    synchronized (CL_LOCK) {
      // Not initialized,
      if (cached_process_object == null) {
        return false;
      }
      // Static processes are not flushable,
      if (process_type == ProcessOperation.Type.STATIC) {
        return false;
      }
      
      // Not interacted with since the last flush,
      if (!interacted_since_flush) {
        return false;
      }

      // Not flushable if trying to flush within 4 seconds of construction,
      if (MonotonicTime.millisSince(process_object_create_timestamp) < (4 * 1000)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if this instance is currently stale (the 'function' method
   * hasn't recently been called on this process).
   */
  boolean isCurrentlyStale() {
    return MonotonicTime.millisSince(monotonic_time_of_last_function) >
                              ProcessServerService.STALE_PROCESS_TIMEOUT;
  }

  /**
   * Returns true if this instance is disposable. An instance is disposable
   * if it hasn't been interacted with in some long period of time (typically
   * at least 4 minutes).
   */
  boolean isDisposable() {
    return MonotonicTime.millisSince(monotonic_time_of_last_function) >
                              ProcessServerService.DISPOSABLE_PROCESS_TIMEOUT;
  }

  /**
   * Adds a lock that prevents this instance from being removed when stale.
   */
  void preventRemoveLock() {
    prevent_remove.incrementAndGet();
  }

  /**
   * Removes a lock that prevents this instance from being removed when stale.
   */
  void preventRemoveUnlock() {
    prevent_remove.decrementAndGet();
  }

  /**
   * False only when this instance has no 'prevent remove' locks.
   */
  boolean isPreventRemoveLocked() {
    return prevent_remove.get() != 0;
  }


  /**
   * Returns the ProcessMckoiAppClassLoader to use. This will consistently
   * return the same class loader for this instance even if the application
   * has changed and it's out of date. For the class loader to be 'in date'
   * it must be reloaded.
   */
  private ProcessMckoiAppClassLoader getProcessClassLoader()
                                                            throws PException {

    // Fetch the class loader for the application. Processes can share the
    // same classloader if it's the same account/application.

    synchronized (CL_LOCK) {
      // Get the class loader from the service,
      ProcessMckoiAppClassLoader class_loader =
             process_service.getClassLoaderForApplication(account_application);
      // If the cached version is null,
      if (cached_process_class_loader == null) {
        cached_process_class_loader = class_loader;
      }
      else {
        // Is it a different class loader?
        if (class_loader != cached_process_class_loader) {
          // Ok, the cached class loader is out of date so we mark the
          // instance as out of date,
          version_out_of_date = true;
          process_service.notifyOutOfDateProcessInstances(this);
//          ProcessServerService.PROCESS_LOG.log(
//                    Level.INFO, "Instance class loader version out of date");
        }
      }
      return cached_process_class_loader;
    }

  }

  /**
   * Returns the ProcessOperation object (user code loaded from the class loader) for
   * this instance.
   */
  private ProcessOperation getProcessObject(ClassLoader cl)
                                 throws PException, ProcessUserCodeException,
                                        ClassNotFoundException {
    // Do we have the cached user process?
    synchronized (CL_LOCK) {
      if (cached_process_object == null) {
        // process_object is null, so create it,
        try {

          // Instantiate the process object via the class loader,
          Class<?> process_class = Class.forName(process_name, true, cl);

          if (!ProcessOperation.class.isAssignableFrom(process_class)) {
            // Fail if process not a com.mckoi.process.ProcessOperation instance.
            throw new PException(
                "Class not assignable from ProcessOperation: " + process_name);
          }

          cached_process_object = (ProcessOperation) process_class.newInstance();
          try {
            process_type = cached_process_object.getType();
          }
          catch (Throwable e) {
            // Wrap any exception thrown in a ProcessUserCodeException
            throw new ProcessUserCodeException(e);
          }
          process_object_create_timestamp = MonotonicTime.now();

        }
        // Wrap these exceptions in 'ProcessUserCodeException' so they end
        // up being reported in the account's log.
        catch (IllegalAccessException ex) {
          throw new ClassNotFoundException(process_name, ex);
        }
        catch (InstantiationException ex) {
          throw new ClassNotFoundException(process_name, ex);
        }
      }
      // Return the process object,
      return cached_process_object;
    }
  }

  /**
   * Returns a string that contains parsable information about this process
   * instance (where the instance is located, etc).
   */
  private String createProcessValue(AccountApplication account_app)
                                                            throws PException {
    try {
      JSONStringer json = new JSONStringer();
      json.object();
      json.key("acn").value(account_app.getAccountName());
      json.key("apn").value(account_app.getApplicationName());
      json.key("pn").value(process_name);
      json.endObject();
      return json.toString();
    }
    catch (JSONException e) {
      throw new PException(e);
    }
  }

  /**
   * Adds a broadcast request for the given channel by the given connection.
   * This request should be valid for at least 4 minutes, but ideally for
   * as long as the instance is valid. This is called when a client requests
   * to receive the broadcast messages.
   * <p>
   * When this is called, all messages in the broadcast queue with a sequence
   * number greater than the given value are sent to the connection.
   */
  void addBroadcastRequest(int channel_num,
          NIOConnection connection, long min_sequence_val) throws IOException {

    // Record the time so we can't dispose the instance for at least another
    // 4 minutes.
    timestampLastFunctionCall();

    getBroadcastInstance().addBroadcastRequest(
                                  channel_num, connection, min_sequence_val);

  }

  /**
   * Returns all the broadcast messages between 'min_seq' and 'max_seq'
   * inclusively in the order they were added to the queue. Will not include
   * messages with a sequence value that's in the 'sent_sequences' set. If
   * a message is returned in a list then the sequence value of the message
   * is also added to the 'sent_sequences' set.
   */
  List<PMessage> allBroadcastMessagesBetween(
                long min_seq_val, long max_seq_val, Set<Long> sent_sequences) {

    return getBroadcastInstance().allBroadcastMessagesBetween(
                                   min_seq_val, max_seq_val, sent_sequences);

  }

  /**
   * Returns all the NIOConnection objects currently listening on this
   * process.
   */
  List<NIOConnection> getBroadcastListeners() {
    return getBroadcastInstance().getBroadcastListeners();
  }

  /**
   * Returns the number of broadcast listeners currently on this instance.
   */
  int getBroadcastListenersSize() {
    if (broadcast_instance != null) {
      return getBroadcastInstance().getBroadcastListenersSize();
    }
    return 0;
  }

  /**
   * Immediately cleans the instance of all broadcast messages older than
   * 2 minutes. Does nothing if this instance is not broadcasting messages,
   * or there's nothing to clean. If messages from the broadcast queue are
   * cleaned, returns the number of elements removed.
   */
  int cleanExpiredBroadcastMessages() {
    if (broadcast_instance != null) {
      return getBroadcastInstance().cleanBroadcastQueue();
    }
    return 0;
  }

  /**
   * Cleans all connections that have not been refreshed on the instance
   * within a timeout period.
   */
  int cleanExpiredConnections() {
    if (broadcast_instance != null) {
      return getBroadcastInstance().cleanExpiredConnections();
    }
    return 0;
  }

  /**
   * Cleans the function queue of all elements.
   */
  void cleanFunctionQueue() {
    function_queue.clean();
  }

  /**
   * Removes the given connection from the broadcast list. This should only
   * be called if the connection is crashed.
   */
  void removeNIOConnection(NIOConnection conn) {
    if (broadcast_instance != null) {
      getBroadcastInstance().removeNIOConnection(conn);
    }
  }

//  /**
//   * Returns true if this instance is currently listening to the given
//   * ProcessChannel.
//   */
//  private boolean isListeningToChannel(ProcessChannel channel) {
//    return listener_map.containsKey(channel);
//  }

  /**
   * Adds a signal to the start of the function queue.
   */
  void putSignalOnQueue(FunctionQueueItem item) {
    function_queue.addFirstToFunctionQueue(item);
  }

  /**
   * Puts a signal on the queue from an anonymous source.
   */
  void putAnonymousSignalOnQueue(ProcessMessage signal_message) {

    final FunctionQueueItem function_item =
        new FunctionQueueItem(-1,
             ProcessInputMessage.Type.SIGNAL_INVOKE, signal_message,
             null, null, null, false);

    putSignalOnQueue(function_item);

  }

  /**
   * Pushes the given function queue item onto the function queue of this
   * instance.
   */
  void pushToFunctionQueue(FunctionQueueItem item) {
    function_queue.pushToFunctionQueue(item);
  }

  /**
   * Returns true if the function queue of this instance is currently empty.
   */
  boolean isFunctionQueueEmpty() {
    return function_queue.isFunctionQueueEmpty();
  }

  /**
   * Pushes all the ProcessMessages and type to the function queue on this
   * instance. This also ensures that a dispatcher will execute the
   * 'function' method on the process operation if necessary.
   */
  private void pushToFunctionQueue(
              ProcessInputMessage.Type type,
              ChannelSessionState broadcast_session_state,
              int call_id,
              Collection<ProcessMessage> msgs) {

    if (msgs == null || msgs.isEmpty()) {
      return;
    }
    for (ProcessMessage msg : msgs) {

      // Make the queue item and add it,
      function_queue.pushToFunctionQueue(new FunctionQueueItem(
              call_id, type, msg, broadcast_session_state, null, null, false));

    }

    // Notify process service that the queue changed,
    process_service.notifyMessagesAvailable(process_id);

  }

  private void pushToFunctionQueue(
              ProcessInputMessage.Type type,
              ChannelSessionState broadcast_session_state,
              Collection<ProcessMessage> msgs) {
    pushToFunctionQueue(type, broadcast_session_state, -1, msgs);
  }

  private void pushToFunctionQueue(
              ProcessInputMessage.Type type,
              int call_id,
              Collection<ProcessMessage> msgs) {
    pushToFunctionQueue(type, null, call_id, msgs);
  }

  /**
   * Internal thread-safe method that makes this instance suspended.
   */
  private void internalMakeSuspended() {
    suspended = true;
    synchronized (CL_LOCK) {
      cached_process_object = null;
      cached_process_class_loader = null;
      // Reset the version out of date flag,
      version_out_of_date = false;
    }
  }

  /**
   * Increments a counter on this object that's reset every time we consume
   * message.
   */
  int incNoConsumeCount() {
    return no_consume_counter.incrementAndGet();
  }

  /**
   * A ProcessResultNotifier that is self renewing. Which means, whenever
   * a notification arrives it consumes all messages available and renews
   * the notifier to be triggered when new messages arrive.
   */
  private class PIChannelListener extends ProcessResultNotifier {

    private final ChannelConsumer consumer;
    private CleanupHandler cleanup_handler;

    PIChannelListener(ChannelConsumer consumer) {
      this.consumer = consumer;
    }

    /**
     * Pushes all the messages from the consumer onto the function queue.
     */
    private void broadcastToFunctionQueue() throws ProcessUnavailableException {
      // Don't push broadcast messages if this is terminated,
      if (isTerminated()) {
        return;
      }
      while (true) {
        Collection<ProcessMessage> msgs =
                                  consumer.consumeFromChannel(100, this);
        // Break if the end reached,
        if (msgs == null) {
          break;
        }
        pushToFunctionQueue(Type.BROADCAST, consumer.getSessionState(), msgs);
      }
    }

    @Override
    public void init(CleanupHandler cleanup_handler) {
      this.cleanup_handler = cleanup_handler;
    }

    @Override
    public void notifyMessages(Status status) {
      // Delegate this to the thread pool,
      process_service.getThreadPool().submit(new Runnable() {
        @Override
        public void run() {
          try {
            broadcastToFunctionQueue();
          }
          catch (ProcessUnavailableException e) {
            // PENDING: Should we do something with this?
            e.printStackTrace(System.err);
          }
        }
      });
    }

  }

  private void addBroadcastChannelListener(
                         String account_name, ProcessChannel process_channel)
                                          throws ProcessUnavailableException {

    if (!listener_map.containsKey(process_channel)) {

      ProcessClientService client_service =
                                     process_service.getProcessClientService();
      // Consume all messages until our notifier is registered,
      final ChannelConsumer consumer =
              client_service.getChannelConsumer(
                                  account_name, NO_CONTEXT, process_channel);

      // Create a notifier that self renews,
      final PIChannelListener pi_channel_listener =
                                              new PIChannelListener(consumer);
      // Add to the listener map
      listener_map.put(process_channel, pi_channel_listener);

      // Add the notifier to the broadcast queue in client service
      pi_channel_listener.broadcastToFunctionQueue();

      // Tell the broadcast queue that there's a listener lock on it,
      client_service.addBroadcastQueueListenerLock(process_channel);

    }

  }

  private void addBroadcastChannelListener(
                      String account_name, ChannelSessionState session_state)
                                          throws ProcessUnavailableException {

    ProcessClientService client_service =
                                     process_service.getProcessClientService();
    // Consume all messages until our notifier is registered,
    final ChannelConsumer consumer =
                client_service.getChannelConsumer(
                                    account_name, NO_CONTEXT, session_state);

    // Get the process channel,
    ProcessChannel process_channel = consumer.getProcessChannel();

    if (!listener_map.containsKey(process_channel)) {

      // Create a notifier that self renews,
      final PIChannelListener pi_channel_listener =
                                              new PIChannelListener(consumer);
      // Add to the listener map
      listener_map.put(process_channel, pi_channel_listener);

      // Add the notifier to the broadcast queue in client service
      pi_channel_listener.broadcastToFunctionQueue();

      // Tell the broadcast queue that there's a listener lock on it,
      client_service.addBroadcastQueueListenerLock(process_channel);

    }

  }

  private void removeBroadcastChannelListener(ProcessChannel process_channel) {

    if (listener_map.containsKey(process_channel)) {

      ProcessClientService client_service =
                                     process_service.getProcessClientService();

      // Remove the entry from the listener map
      PIChannelListener pi_channel_listener =
                                          listener_map.remove(process_channel);

      // Tell the broadcast queue that there's no longer a lock on it,
      client_service.removeBroadcastQueueListenerLock(process_channel);

      // Remove the notifier from the broadcast queue in client service
      pi_channel_listener.cleanup_handler.performCleanup();

    }

  }


  /**
   * Executes the given Runnable with the account context. If 'timeout_ms' is
   * positive then this function will wait at least the number of milliseconds
   * for any other threads to finish before executing the code. If the timeout
   * is reached then a PTimeoutException is thrown.
   * <p>
   * Note that this method ensures that the user code is only ever executed on
   * a single thread at a time. Consider the 'run' method to be synchronized.
   * <p>
   * If 'timeout_ms' is negative then the function will wait forever until
   * any other threads have finished with this instance.
   */
  private Object executeUserCode(int timeout_ms, UserCodeRunner r)
                     throws PException, SuspendedProcessException,
                            ProcessUserCodeException, ClassNotFoundException {

    // If this is null then throw suspended exception,
    if (account_application == null) {
      throw new SuspendedProcessException();
    }

    // Fetch the logger service for this account,
    String account_name = account_application.getAccountName();
    String webapp_name = account_application.getApplicationName();

    // Set up the platform specific code,
    String vhost = "";
    String protocol = "process";

    // Lock against our rentrant lock for this instance,
    if (timeout_ms < 0) {
      USER_CODE_LOCK.lock();
    }
    else {
      // Try aquiring the lock within the timeout period. If can't then
      // generate an exception.
      try {
        boolean lock_success =
                     USER_CODE_LOCK.tryLock(timeout_ms, TimeUnit.MILLISECONDS);
        if (!lock_success) {
          throw new PTimeoutException();
        }
      }
      catch (InterruptedException e) {
        throw new PException(e);
      }
    }
    try {

      // Fetch the class loader,
      ProcessMckoiAppClassLoader app_class_loader = getProcessClassLoader();

      // Cache the logger service object,
      if (logger_service == null) {
        logger_service =
                      process_service.getLoggerServiceForAccount(account_name);
      }
      // NOTE; this must happen before we have a chance to enter any user
      //   code.

      // Set the platform context for this process,
      DBSessionCache session_cache = process_service.getDBSessionCache();
      ClassNameValidator allowed_classes =
                                     process_service.getAllowedSystemClasses();
      
      MWPUserClassLoader user_class_loader =
                                         app_class_loader.getUserClassLoader();

      Thread.currentThread().setContextClassLoader(
                                   ProcessInstanceImpl.class.getClassLoader());
      PlatformContextImpl.setCurrentThreadContextForProcessService(
              session_cache, this, account_name, vhost, protocol, webapp_name,
              logger_service, user_class_loader, app_class_loader,
              allowed_classes);

      try {

        // Set the version transaction used for the URL handler,
        ODBTransaction version_t = session_cache.createODBTransaction(
                                             app_class_loader.getDBPathSnapshot());
        PlatformContextImpl context = new PlatformContextImpl();
//        context.setURLPathOverride("ufs" + account_name, version_t);
        context.setMWPFSURLPathOverride(account_name, version_t);

        // Initialize the process_object if need to,
        ProcessOperation process_object;
        try {
          process_object = getProcessObject(app_class_loader);
        }
        catch (ClassNotFoundException e) {
          // If the class isn't found then close this instance,
          close();
          throw e;
        }

        // 'process_object' is now created with the class loader,

        // Set the interacted flag,
        interacted_since_flush = true;

        // Call the user code and return the result,
        return r.run(process_object);

      }
      // Make sure we reset the thread context before we return,
      finally {
        PlatformContextImpl.removeCurrentThreadContext();
      }

    }
    finally {
      USER_CODE_LOCK.unlock();
    }

  }




  private void callUserFunctionExecute1()
                    throws PException, IOException, SuspendedProcessException,
                           ProcessUserCodeException, ClassNotFoundException {

    // Execute the function as user code,
    executeUserCode(-1, new UserCodeRunner() {
      @Override
      public Object run(ProcessOperation process_object)
                        throws PException, SuspendedProcessException,
                                ProcessUserCodeException {

        // If the process is suspended, throw an exception. This should
        // cause the caller to try and resume before calling this function
        // again.
        if (suspended) {
          throw new SuspendedProcessException();
        }

        // Record the time we performed a function,
        timestampLastFunctionCall();

        // Record the entry time,
        long nano_start = ThreadUsageStatics.getCurrentThreadCPUTimeNanos();

        // Perform the function (takes us into user code),
        try {
          process_object.function(ProcessInstanceImpl.this);
          return null;
        }
        catch (Throwable e) {
          // Wrap any exception thrown in a ProcessUserCodeException
          throw new ProcessUserCodeException(e);
        }
        finally {

          // The time we exit,
          long nano_time_taken =
              (ThreadUsageStatics.getCurrentThreadCPUTimeNanos() - nano_start);

          // Update analytics,
          function_cpu_time_nano += nano_time_taken;
          if (function_call_count >= 0) {
            function_call_count += 1;
          }
          else {
            function_call_count = -1;
          }

          // Record the time we performed a function,
          timestampLastFunctionCall();

          // If process_type is 'STATIC' then we close the process
          // immediately.
          if (process_type == ProcessOperation.Type.STATIC) {
            close();
          }

        }

      }

    });

  }

  /**
   * Calls the user-code 'function' method if there are messages currently
   * on the function queue. If there are no messages, or if currently the
   * 'function' method is being executed, returns and does nothing.
   * <p>
   * This method must be repeatedly called until the function queue is
   * empty.
   * <p>
   * Returns false if no call was made to user-code (because the function is
   * already being run). Returns true if a call to user-code was made.
   */
  boolean callUserFunctionExecute()
                    throws PException, IOException, SuspendedProcessException,
                           ProcessUserCodeException {

    // Are we currently executing a user function?
    function_queue.lock();
    try {
      // If not currently executing code and the function queue is empty,
      if (!currently_executing_code &&
          !function_queue.isFunctionQueueEmpty()) {
        currently_executing_code = true;
      }
      // Return from this function if currently executing code or function
      // queue is empty,
      else {
        return false;
      }
    }
    finally {
      function_queue.unlock();
    }

    boolean exception_thrown = false;
    try {

      // Makes sure we don't exit on 'no consume' if it's the first try,
      boolean first_try = true;

      while (true) {

        function_queue.lock();
        try {
          if (function_queue.isFunctionQueueEmpty()) {
            currently_executing_code = false;
            PROCESS_LOG.log(Level.FINE, "-R:empty queue ({0})", first_try);
            return !first_try;
          }

          // Increment the no consume count,
          int no_consume_count = incNoConsumeCount();

          // Break of no_consume count is over 16,
          if ((!first_try && no_consume_count > 16)) {
            currently_executing_code = false;
            PROCESS_LOG.log(Level.FINE, "-R:no_consume_count > 16");
            return true;
          }

        }
        finally {
          function_queue.unlock();
        }

        // Reset first try,
        first_try = false;

        try {
          callUserFunctionExecute1();
        }
        catch (ClassNotFoundException e) {
          exception_thrown = true;
          // Reply to all messages on the queue with this error,
          boolean first_error = true;
          while (true) {
            ProcessInputMessage msg = internalConsumeMessage();
            if (msg == null) {
              break;
            }
            sendFailure(msg, e, first_error);
            first_error = false;
          }
          // Clean the function queue,
          function_queue.clean();
          // And throw as a user code exception,
          PROCESS_LOG.log(Level.FINE, "-R:ClassNotFoundException");
          throw new ProcessUserCodeException(e);
        }
        catch (ProcessUserCodeException e) {
          exception_thrown = true;
          PROCESS_LOG.log(Level.FINE, "-R:ProcessUserCodeException");
          throw e;
        }
        catch (SuspendedProcessException e) {
          exception_thrown = true;
          PROCESS_LOG.log(Level.FINE, "-R:SuspendedProcessException");
          throw e;
        }
        catch (IOException e) {
          exception_thrown = true;
          PROCESS_LOG.log(Level.FINE, "-R:IOException");
          throw e;
        }
        catch (PException e) {
          exception_thrown = true;
          PROCESS_LOG.log(Level.FINE, "-R:PException");
          throw e;
        }
        catch (Error e) {
          exception_thrown = true;
          PROCESS_LOG.log(Level.FINE, "-R:Error");
          throw e;
        }
        catch (RuntimeException e) {
          exception_thrown = true;
          PROCESS_LOG.log(Level.FINE, "-R:RuntimeException");
          throw e;
        }

      }

    }
    finally {
      if (exception_thrown) {
        function_queue.lock();
        try {
          currently_executing_code = false;
        }
        finally {
          function_queue.unlock();
        }
      }
    }

//    return true;

  }

  /**
   * Executes a flush operation on this process instance. This is called by
   * the system flush dispatcher thread only.
   * <p>
   * This will throw a PTimeoutException and no flush will occur if an
   * exclusive lock could not be aquired on this instance within 1 second.
   */
  boolean executeFlush(final ODBObject process_odb)
                           throws PException, SuspendedProcessException,
                                  ProcessUserCodeException {

    // Flush the state data,
    try {

      // Get the account application,
      AccountApplication account_app = getAccountApplication();

      if (state_map.tryLock(100)) {
        // Get the state data object for this instance,
        ODBData state_odbdata = process_odb.getData("state");
        // Make sure the process object's 'value' is set correctly,
        if (process_odb.getString("value").equals("")) {
          process_odb.setString("value", createProcessValue(account_app));
        }
        interacted_since_flush = false;
        return state_map.serialize(state_odbdata);
      }
      else {
        // This will happen if the user code holds on to the lock for too long,
        throw new PTimeoutException("Flush serialize time out");
      }
    }
    catch (InterruptedException e) {
      throw new PException(e);
    }
    finally {
      state_map.unlock();
    }

  }
  
  /**
   * Executes a suspend operation on this process instance. This is called by
   * the system flush dispatcher thread only.
   * <p>
   * This will throw a PTimeoutException and no suspend will occur if an
   * exclusive lock could not be acquired on this instance within 10 seconds.
   */
  boolean executeSuspend(final ODBObject process_odb)
                    throws PException, SuspendedProcessException,
                           ProcessUserCodeException {

    try {

      // Note we timeout after 10 seconds if we aren't able to suspend.
      Boolean suspend = (Boolean) executeUserCode(10000, new UserCodeRunner() {
        @Override
        public Object run(ProcessOperation process_object)
                              throws PException, ProcessUserCodeException,
                                    SuspendedProcessException {

          // If it's not stale, we don't allow suspend,
          // This prevents us suspending a process that has had a function call
          // on it recently.
          if (!isCurrentlyStale()) {
            return false;
          }

          // Assert we are not suspended,
          if (suspended) {
            throw new SuspendedProcessException();
          }

          // If the operation is not dormant then fail the suspend,
          if (!process_object.isDormant()) {
            return false;
          }

          // Get the state data object for this instance,
          ODBData state_data = process_odb.getData("state");

          // Call the user code 'suspend' function and return the result,
          boolean something_changed = false;
          try {
  //          suspend_result = process_object.suspend(state_data);
            process_object.suspend(state_map);
            // NOTE: We don't serialize under a lock because we know there's no
            //   thread inside the user code, therefore it's safe to do this.
            something_changed |= state_map.serialize(state_data);
          }
          catch (Throwable e) {
            // Wrap any exception thrown in a ProcessUserCodeException
            throw new ProcessUserCodeException(e);
          }

          // Make sure the process object's 'value' is set correctly,
          if (process_odb.getString("value").equals("")) {
            process_odb.setString("value",
                                  createProcessValue(account_application));
            something_changed |= true;
          }

          // Mark the instance as suspended,
          // Log,
          if (ProcessServerService.PROCESS_LOG.isLoggable(Level.FINER)) {
            ProcessServerService.PROCESS_LOG.log(
                                Level.FINER, "Suspended: {0}", process_id_str);
          }
          // Clear the internal state,
          internalMakeSuspended();

          // Return true if something changed,
          return something_changed;
        }

      });

      // Reset the 'interacted_since_flush' flag after a successfull suspend
      interacted_since_flush = false;

      return suspend;

    }
    catch (ClassNotFoundException e) {
      // If class not found, set suspended to true,
      internalMakeSuspended();
      return false;
    }

  }

  /**
   * Executes a full resume operation on this instance. This will check if the
   * process state information is already set in this process instance. If
   * it's not it will load it from the given process ODBObject.
   * <p>
   * Note that this can be safely called from multiple threads concurrently.
   * If the instance is already resumed then nothing changes.
   * <p>
   * Returns true if the process was resumed. False if the process failed to
   * resume (this can happen if the instance is not suspended).
   */
  boolean executeResume(final ODBObject process_odb)
                                throws PException, ProcessUserCodeException {

    // If we don't have the process information already set, we load it
    // from the given 'process_odb'.

    synchronized (RESUME_LOCK) {
      if (account_application == null) {
        // Get the value from the process,
        String process_value = process_odb.getString("value");
        if (process_value.equals("")) {
          throw new PException("Process 'value' not defined");
        }
        try {
          JSONTokener json = new JSONTokener(process_value);
          JSONObject json_ob = (JSONObject) json.nextValue();
          String accname = json_ob.getString("acn");  // account name
          String appname = json_ob.getString("apn");  // application name
          String pname = json_ob.getString("pn");     // process name

          // Set the account details for this instance,
          account_application = new AccountApplication(accname, appname);
          process_name = pname;

        }
        catch (JSONException e) {
          throw new PException(e);
        }
      }
    }

    // Note we timeout after 10 seconds if we aren't able to resume.
    try {
      Boolean resume = (Boolean) executeUserCode(10000, new UserCodeRunner() {
        @Override
        public Object run(ProcessOperation process_object)
                                 throws PException, ProcessUserCodeException {

          // If it's not suspended,
          // Note that this doesn't generate an exception because we can
          // resume from anywhere,
          if (!suspended) {
            return false;
          }

          // Get the state data object for this instance,
          ODBData state_data = process_odb.getData("state");

          // Call the user code 'resume' function and return the result,
          try {
            // Deserialize from the check-point data,
            state_map.deserialize(state_data);
            process_object.resume(ProcessInstanceImpl.this);
          }
          catch (Throwable e) {
            // Wrap any exception thrown in a ProcessUserCodeException
            throw new ProcessUserCodeException(e);
          }

          // Make sure the process object's 'value' is set correctly,
          if (process_odb.getString("value").equals("")) {
            process_odb.setString("value",
                                  createProcessValue(account_application));
          }
          // Mark the instance as not suspended,
          suspended = false;
          // Log,
          if (ProcessServerService.PROCESS_LOG.isLoggable(Level.FINER)) {
            ProcessServerService.PROCESS_LOG.log(
                                  Level.FINER, "Resumed: {0}", process_id_str);
          }

          // Return the result of the suspend,
          return true;
        }

      });

      // Reset the 'interacted_since_flush' flag after a successfull resume
      interacted_since_flush = false;

      return resume;

    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    catch (SuspendedProcessException e) {
      // Shouldn't be possible,
      throw new RuntimeException(e);
    }

  }

  /**
   * Executes a reload operation on this process instance. A reload will
   * perform a suspend followed immediately by a resume across a reloaded
   * class loader.
   */
  boolean executeReload(final ODBObject process_odb)
                              throws PException, ProcessUserCodeException {

    // Note we timeout after 1 second if we aren't able to reload.
    try {
      Boolean reload = (Boolean) executeUserCode(1000, new UserCodeRunner() {
        @Override
        public Object run(ProcessOperation process_object)
                                  throws PException, ProcessUserCodeException,
                                         ClassNotFoundException {

          // Assert we are not suspended,
          if (suspended) {
            throw new PException("Process instance is already suspended");
          }

          // If the operation is not dormant then fail the reload,
          if (!process_object.isDormant()) {
            return false;
          }

          // --- SUSPEND ---

          // Get the state data object for this instance,
          ODBData state_data = process_odb.getData("state");

          // Call the user code 'suspend' function and return the result,
          try {
            process_object.suspend(state_map);
            state_map.serialize(state_data);
          }
          catch (Throwable e) {
            // Wrap any exception thrown in a ProcessUserCodeException
            throw new ProcessUserCodeException(e);
          }

          // Make sure the process object's 'value' is set correctly,
          if (process_odb.getString("value").equals("")) {
            process_odb.setString("value",
                                  createProcessValue(account_application));
          }
          // Mark the instance as suspended,
          // Clear the internal state,
          internalMakeSuspended();

          // --- NEW STATE ---

          // Get the account name,
          String account_name = account_application.getAccountName();
          // Fetch the new class loader,
          // NOTE; this loads 'cached_process_class_loader'
          ProcessMckoiAppClassLoader class_loader = getProcessClassLoader();
          // To create the transaction,
          DBSessionCache session_cache = process_service.getDBSessionCache();

          // Set the version transaction used for the URL handler,
          ODBTransaction version_t = session_cache.createODBTransaction(
                                             class_loader.getDBPathSnapshot());
          PlatformContextImpl context = new PlatformContextImpl();
//          context.setURLPathOverride("ufs" + account_name, version_t);
          context.setMWPFSURLPathOverride(account_name, version_t);

          // Initialize the process_object if need to,
          // NOTE; this loads 'cached_process_object'
          process_object = getProcessObject(class_loader);
          
          // --- RESUME ---

          // Call the user code 'resume' function and return the result,
          try {
            // Deserialize from the check-point data,
            state_map.deserialize(state_data);
            process_object.resume(ProcessInstanceImpl.this);
          }
          catch (Throwable e) {
            // Wrap any exception thrown in a ProcessUserCodeException
            throw new ProcessUserCodeException(e);
          }

          // Mark the instance as not suspended,
          suspended = false;

          // Log,
          if (ProcessServerService.PROCESS_LOG.isLoggable(Level.FINER)) {
            ProcessServerService.PROCESS_LOG.log(
                                Level.FINER, "Reloaded: {0}", process_id_str);
          }

          // Return the result of the suspend,
          return true;
        }

      });

      // Reset the 'interacted_since_flush' flag after a successfull resume
      interacted_since_flush = false;

      return reload;

    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    catch (SuspendedProcessException e) {
      // Shouldn't be possible,
      throw new RuntimeException(e);
    }

  }

  // ---------- Implemented from ProcessInstance ---------

  @Override
  public ProcessId getId() {
    return process_id;
  }

  @Override
  public void close() {
    terminated = true;
  }

  @Override
  public int scheduleCallback(long time_wait, final ProcessMessage msg) {

    // Use the 'process_service' schedule callback timer to push a
    // 'TIMED_CALLBACK' message into the queue,

    // Create a unique call_id,
    final int call_id =
                   process_service.getProcessClientService().generateCallId();

    final ProcessMessage nullfix_msg =
                  (msg == null) ? ByteArrayProcessMessage.nullMessage() : msg;

    TimerTask callback_task = new TimerTask() {
      @Override
      public void run() {
        try {
          // If the process is not terminated then do a timed callback,
          if (!isTerminated()) {
            pushToFunctionQueue(Type.TIMED_CALLBACK, call_id,
                                Collections.singleton(nullfix_msg));
          }
        }
        catch (Throwable e) {
          e.printStackTrace(System.err);
        }
      }
    };

    // Immediate callback,
    if (time_wait <= 0) {
      callback_task.run();
    }
    else {
      // Otherwise schedule via a timer,
      process_service.doScheduleCallback(callback_task, time_wait);
    }

    // Return the call id,
    return call_id;

  }

  private ProcessInputMessage internalConsumeMessage() {
    FunctionQueueItem item = function_queue.removeFirst();
    if (item == null) {
      return null;
    }
    return new PFunctionHandlerImpl(this, item);
  }

  @Override
  public ProcessInputMessage consumeMessage() {
    // Reset the 'no consume counter',
    no_consume_counter.set(0);
    return internalConsumeMessage();
  }

  @Override
  public String[] consumeSignal() {
    // Reset the 'no consume counter',
    no_consume_counter.set(0);
    // Remove the first signal,
    FunctionQueueItem item = function_queue.removeFirstSignal();
    if (item == null) {
      return null;
    }
    // Decode the signal string,
    String[] args =
              ByteArrayProcessMessage.decodeStringArgsList(item.getMessage());
    return args;
  }

  private PFunctionHandlerImpl castProcessInputMessage(
                                                    ProcessInputMessage msg) {
    // Sanity checks,
    if (!(msg instanceof PFunctionHandlerImpl)) {
      throw new IllegalStateException("msg was not returned by 'consumeMessage'");
    }
    PFunctionHandlerImpl pim = (PFunctionHandlerImpl) msg;
    if (pim.process_instance != this) {
      throw new IllegalStateException("msg does not originate from this instance");
    }
    if (pim.reply_sent == true) {
      throw new IllegalStateException("'send*' already called");
    }
    return pim;
  }

  @Override
  public void sendReply(ProcessInputMessage msg, ProcessMessage return_msg) {

    PFunctionHandlerImpl pim = castProcessInputMessage(msg);

    // If it's not a function invoke,
    if (pim.getType() != ProcessInputMessage.Type.FUNCTION_INVOKE) {
      throw new IllegalStateException("Not a function invoke input message");
    }

    // Wrap the returned message in a 'success' message,
    int nsize = return_msg.size() + 22;
    ByteArrayOutputStream bout = new ByteArrayOutputStream(nsize);
    // Write the header,
    int call_id = pim.function_item.getCallId();
    // NOTE: Command code is 14
    byte[] header =
          ProcessServerService.createHeader(process_id, call_id,
                                            CommConstants.CALL_REPLY_CC);
    bout.write(header, 0, header.length);
    // Write the success characters,
    bout.write((byte) 'S');
    bout.write((byte) ';');
    // Write the returned message content,
    // SECURITY: Note we wrap the ByteArrayOutputStream here because we
    // are calling user controlled code here. We don't want to expose
    // information about the process_id, call_id, etc to the user.
    try {
      return_msg.writeTo(new SecureWrappedOutputStream(bout));
      bout.flush();
      // Put the reply on the output queue,
      // Turn it into a return pmsg,
      PMessage return_pmsg = new PMessage(bout.toByteArray());

      // Send the return message,
      pim.send(return_pmsg);

    }
    catch (IOException e) {
      // Rethrow as PRuntimeException,
      throw new PRuntimeException(e);
    }

  }

  @Override
  public void sendFailure(
                    ProcessInputMessage msg, final Throwable e, boolean log) {

    PFunctionHandlerImpl pim = castProcessInputMessage(msg);

    // If it's not a function invoke,
    if (pim.getType() != ProcessInputMessage.Type.FUNCTION_INVOKE) {
      throw new IllegalStateException("Not a function invoke input message");
    }

    // Should we log?
    try {
      if (log) {
        // Yes, so put it in the account's log system,
        // This is a privileged action since this can be called from
        // user-code.
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
          @Override
          public Object run() {
            process_service.logAccountException(ProcessInstanceImpl.this, e);
            return null;
          }
        });
      }
    }
    // Any exceptions here are put in the process log,
    catch (Throwable ex2) {
      ProcessServerService.PROCESS_LOG.log(
                       Level.SEVERE, "Exception when logging a failure", ex2);
    }

    // Create the failure message,
    PMessage message = ProcessServerService.failMessage(
                        process_id, pim.function_item.getCallId(), "USER", e);
    // Send the failure message,
    pim.send(message);

  }

  @Override
  public void sendFailure(ProcessInputMessage msg, Throwable exception) {
    sendFailure(msg, exception, true);
  }


  @Override
  public void broadcastMessage(int channel_number, ProcessMessage message) {

    BroadcastInstance bci = getBroadcastInstance();

    // Make a unique sequence value for this message,
    long sequence_uid = bci.createUniqueSequenceValue();

    // Copy it into a message,
    byte[] header = ProcessServerService.createHeader(
              process_id, channel_number, CommConstants.BROADCAST_MESSAGE_CC);

    ByteArrayOutputStream bout =
                                new ByteArrayOutputStream(message.size() + 28);
    DataOutputStream dout = new DataOutputStream(bout);
    try {
      dout.write(header);
      dout.writeLong(sequence_uid);
      message.writeTo(new SecureWrappedOutputStream(dout));
      dout.flush();
    }
    catch (IOException e) {
      // Shouldn't be possible,
      throw new PRuntimeException(e);
    }

    // Turn it into a PMessage,
    byte[] msg_buf = bout.toByteArray();
    PMessage msg = new PMessage(msg_buf);

    // Now push the generated PMessage onto the message queue for the channel,
    bci.pushMessageToBroadcastQueue(msg);

    // Notify the connections that are interested in this process,
    // We pipe the notification through the 'process_service' so we can buffer
    // the notifications together.
    process_service.notifyNewBroadcastMessage(this, sequence_uid);

  }

  @Override
  public StateMap getStateMap() {
    // Return the state map implementation,
    return state_map;
  }

  @Override
  public InstanceProcessClient getInstanceProcessClient() {
    ProcessClientService process_client_service =
                                    process_service.getProcessClientService();

    // NOTE; this will be called from user-code so 'account_application' will
    //  never be 'null' here.
    String account_name = account_application.getAccountName();

    // Return the instance process client
    return new InstanceProcessClientImpl(account_name, process_client_service);

  }

  // -----

  @Override
  public final boolean equals(Object obj) {
    // Just a reminder that we need these as the default implementation,
    return super.equals(obj);
  }

  @Override
  public final int hashCode() {
    // Just a reminder that we need these as the default implementation,
    return super.hashCode();
  }

  // -----

  private static class BroadcastInstance {

    /**
     * A unique sequence value. Initialized to System.currentTimeMillis and
     * incremented for each broadcast message.
     */
    private final AtomicLong broadcast_seq_value;

    /**
     * The broadcast queue for this process. Every message broadcast will be
     * put on this queue.
     */
    private final QueueList broadcast_queue;

    /**
     * The list of all connections we are sending broadcast messages to for
     * this instance.
     */
    private final List<NIOConnection> connection_list;

    /**
     * Timestamps of the requests on this instance by the given connection.
     * Used to determine when a connection is stale.
     */
    private final List<MonotonicTime> connection_timestamp_list;

    /**
     * Last time the queue was cleaned.
     */
    private volatile MonotonicTime last_queue_clean = null;


    private BroadcastInstance() {
      broadcast_seq_value = new AtomicLong(System.currentTimeMillis());
      broadcast_queue = new QueueList();
      connection_list = new ArrayList<>(2);
      connection_timestamp_list = new ArrayList<>(2);
    }

    /**
     * Creates a unique sequence value for a broadcast message from this
     * process instance.
     */
    private long createUniqueSequenceValue() {
      return broadcast_seq_value.incrementAndGet();
    }

    /**
     * Immediately cleans the broadcast connections that have not been
     * renewed within 24 minutes. We use 24 mins because the client API
     * guarantees a 20 minute window when a consumer listens for events in
     * which it will be notified. We add 4 minutes of lead time in which the
     * client waits between polls.
     */
    private int cleanExpiredConnections() {
      MonotonicTime twenty_four_mins_ago = MonotonicTime.now(-(24 * 60 * 1000));
      int count = 0;
      synchronized (connection_list) {
        Iterator<NIOConnection> conn_it = connection_list.iterator();
        Iterator<MonotonicTime> connts_it = connection_timestamp_list.iterator();
        while (conn_it.hasNext()) {
          NIOConnection connection = conn_it.next();
          MonotonicTime ts = connts_it.next();
          if (MonotonicTime.isInPastOf(ts, twenty_four_mins_ago)) {
            conn_it.remove();
            connts_it.remove();
            ++count;
          }
        }
      }
      return count;
    }

    /**
     * Removes the connection from the list of connections associated with this
     * instance. If the connection isn't found then nothing changes and no
     * exception occurs.
     */
    private void removeNIOConnection(NIOConnection in_conn) {
      synchronized (connection_list) {
        Iterator<NIOConnection> conn_it = connection_list.iterator();
        Iterator<MonotonicTime> connts_it = connection_timestamp_list.iterator();
        while (conn_it.hasNext()) {
          NIOConnection connection = conn_it.next();
          MonotonicTime ts = connts_it.next();
          if (connection == in_conn) {
            conn_it.remove();
            connts_it.remove();
            return;
          }
        }
      }
    }

    /**
     * Immediately cleans the broadcast queue of all broadcast messages older
     * than 2 minutes. Returns the number of messages removed from the
     * broadcast queue.
     */
    private int cleanBroadcastQueue() {
      final MonotonicTime monotonic_time_now = MonotonicTime.now();
      MonotonicTime two_mins_ago =
                MonotonicTime.millisSubtract(monotonic_time_now, 2 * 60 * 1000);
      last_queue_clean = monotonic_time_now;
      int count = 0;
      synchronized (broadcast_queue) {
        QueueMessage msg = broadcast_queue.getFirst();
        while (msg != null) {

          MonotonicTime bm_timestamp = msg.getQueueTimestamp();

          // Preserve all the messages sooner than 2 mins ago,
          if (MonotonicTime.isInFutureOf(bm_timestamp, two_mins_ago)) {
            // If count is 0 then there's nothing to clear,
            if (count == 0) {
              return 0;
            }
            // Break the loop,
            break;
          }

          msg = msg.getNext();
          ++count;

        }
        // If 'msg' is null, we hit the end of the queue so clear the entire
        // queue,
        if (msg == null) {
          broadcast_queue.clear();
        }
        else {
          broadcast_queue.setToTail(msg);
        }
      }
      return count;
    }

    /**
     * Cleans the queue (only checks every minute). Returns the number of
     * elements cleaned.
     */
    private int periodicClean() {
      if (last_queue_clean == null ||
          MonotonicTime.millisSince(last_queue_clean) > (60 * 1000)) {
        return cleanBroadcastQueue();
      }
      else {
        return 0;
      }
    }

    /**
     * Returns all the broadcast messages between 'min_seq' and 'max_seq'
     * inclusively in the order they were added to the queue. Will not include
     * messages with a sequence value that's in the 'sent_sequences' set. If
     * a message is returned in a list then the sequence value of the message
     * is also added to the 'sent_sequences' set.
     */
    private List<PMessage> allBroadcastMessagesBetween(
                  long min_seq, long max_seq, Set<Long> sent_sequences) {

      List<PMessage> to_send = new ArrayList<>();
      synchronized (broadcast_queue) {

        // Search the queue backwards,
        QueueMessage msg = broadcast_queue.getLast();

        while (msg != null) {
          PMessage pmsg = msg.getMessage();
          // Get the sequence value of the message,
          long msg_seq_val = pmsg.getSequenceValue();

          // If it's less than we sent all the messages after,
          if (msg_seq_val < min_seq) {
            break;
          }

          // Add the message to send,
          if ( msg_seq_val <= max_seq &&
                            ( !sent_sequences.contains(msg_seq_val) ) ) {
            sent_sequences.add(msg_seq_val);
            to_send.add(pmsg);
          }

          // Go to the previous message,
          msg = msg.getPrevious();
        }

      }

      // Reverse the list,
      Collections.reverse(to_send);
      // And return it,
      return to_send;

    }

    /**
     * Returns a list of all PMessage from the broadcast queue with a sequence
     * number greater than the given. The returned list will be ordered by
     * newest message first (so read the list in reverse for chronological
     * order).
     */
    private List<PMessage> allBroadcastMessagesAfter(long min_sequence_val) {

      List<PMessage> to_send = new ArrayList<>();
      synchronized (broadcast_queue) {

        // Search the queue backwards,
        QueueMessage msg = broadcast_queue.getLast();

        while (msg != null) {
          PMessage pmsg = msg.getMessage();

          // Get the sequence value of the message,
          long msg_seq_val = pmsg.getSequenceValue();
          // If it's less than we sent all the messages after,
          if (msg_seq_val <= min_sequence_val) {
            break;
          }

          // Add the message to send,
          to_send.add(pmsg);

          // Go to the previous message,
          msg = msg.getPrevious();
        }

      }

      return to_send;

    }

    /**
     * Puts a message on the broadcast queue.
     */
    private void pushMessageToBroadcastQueue(PMessage msg) {
      synchronized (broadcast_queue) {
        // Add to the broadcast queue,
        broadcast_queue.add(new QueueMessage(null, msg));
        // Clean the queue if necessary,
        periodicClean();
      }
    }

    /**
     * Pushes all messages greater than the given sequence value out to the
     * given connection. This blocks if the output buffers are full.
     */
    private void sendBroadcastMessagesTo(
            NIOConnection connection, long min_sequence_val) throws IOException {

      List<PMessage> to_send = allBroadcastMessagesAfter(min_sequence_val);

      // Send the messages (backwards iteration),
      if (!to_send.isEmpty()) {
        ListIterator<PMessage> li = to_send.listIterator(to_send.size());
        while (li.hasPrevious()) {
          PMessage msg = li.previous();
          connection.sendFirstMessage(msg);
        }
        connection.flushSendMessages();
      }

    }

    /**
     * Adds a broadcast request for the given channel by the given connection.
     * This request should be valid for at least 2 minutes, but ideally for
     * as long as the instance is valid. This is called when a client requests
     * to receive the broadcast messages.
     * <p>
     * When this is called, all messages in the broadcast queue with a sequence
     * number greater than the given value are sent to the connection.
     */
    private void addBroadcastRequest(
                         int channel_num, NIOConnection connection,
                                   long min_sequence_val) throws IOException {

      final MonotonicTime monotonic_time_now = MonotonicTime.now();

      // Add the connection to the list of listeners,
      synchronized (connection_list) {
        boolean add_connection = true;
        // Search the list,
        int i = 0;
        for (NIOConnection c : connection_list) {
          // Already broadcasting to this connection,
          if (c == connection) {
            // Update the timestamp,
            connection_timestamp_list.set(i, monotonic_time_now);
            add_connection = false;
            break;
          }
          ++i;
        }
        // Do we add this connection?
        if (add_connection) {
          connection_list.add(connection);
          connection_timestamp_list.add(monotonic_time_now);
        }
      }

      // Any messages in the queue greater than the given sequence value are
      // sent on the connection immediately,

      sendBroadcastMessagesTo(connection, min_sequence_val);

    }

    /**
     * Returns all the NIOConnection objects currently listening on this
     * process.
     */
    private List<NIOConnection> getBroadcastListeners() {
      List<NIOConnection> output_list;
      synchronized (connection_list) {
        output_list = new ArrayList<>(connection_list.size());
        output_list.addAll(connection_list);
      }
      return output_list;
    }

    /**
     * Returns the number of broadcast listeners currently on this instance.
     */
    private int getBroadcastListenersSize() {
      synchronized (connection_list) {
        return connection_list.size();
      }
    }

  }

  /**
   * A notifier that will immediately push the result into the function queue
   * of this instance.
   */
  private static class PushToFunctionQueueNotifier
                                              extends ProcessResultNotifier {

    private final FunctionQueue function_queue;
    private final ProcessResult result;
    private final int call_id;
    private final ProcessServerService process_service;
    private final ProcessId process_id;

    PushToFunctionQueueNotifier(FunctionQueue function_queue,
                                ProcessResult process_result, int call_id,
                                ProcessServerService process_service,
                                ProcessId process_id) {
      this.function_queue = function_queue;
      this.result = process_result;
      this.call_id = call_id;
      this.process_service = process_service;
      this.process_id = process_id;
    }

    @Override
    public void init(CleanupHandler cleanup_handler) {
    }

    @Override
    public void notifyMessages(Status status) {

      // If messages waiting,
      if (status.equals(Status.MESSAGES_WAITING)) {

        // Get the result.
        ProcessInputMessage result_msg = result.getResult();
        ProcessInputMessage.Type result_type = result_msg.getType();
        ProcessFunctionError error = result_msg.getError();
        ProcessMessage msg = result_msg.getMessage();

        // Push onto the queue
        function_queue.pushToFunctionQueue(new FunctionQueueItem(
                      call_id, result_type, msg, null, error, null, false));

      }
      // Otherwise, either an IO error or timeout. We post an appropriate error
      // onto the function queue,
      else {

        String fail_msg = (status == Status.IO_ERROR) ? "ioerror" : "timeout";
        ProcessFunctionError error = new ProcessFunctionError("ERROR", fail_msg);
        ProcessMessage msg = ByteArrayProcessMessage.nullMessage();

        function_queue.pushToFunctionQueue(new FunctionQueueItem(
                call_id, Type.RETURN_EXCEPTION, msg, null, error, null, false));

      }

      // And notify process service that the queue changed,
      process_service.notifyMessagesAvailable(process_id);

    }
  }

  /**
   * An implementation of InstanceProcessClient provided by this instance.
   */
  private class InstanceProcessClientImpl implements InstanceProcessClient {

    private final String account_name;
    private final ProcessClientService client_service;

    public InstanceProcessClientImpl(String account_name,
                                     ProcessClientService client_service) {
      this.account_name = account_name;
      this.client_service = client_service;
    }

    private int pushMessageOnCallback(
                                ProcessResult result, boolean reply_expected) {
      if (reply_expected) {
        final int call_id = result.getCallId();

        ProcessId instance_process_id = ProcessInstanceImpl.this.process_id;
        ProcessResultNotifier notifier = new PushToFunctionQueueNotifier(
                    function_queue, result, call_id,
                    process_service, instance_process_id);

        ProcessInputMessage result_msg = result.getResult(notifier);
        // If we received a message then immediately push it on the queue,
        if (result_msg != null) {
          ProcessInputMessage.Type result_type = result_msg.getType();
          ProcessFunctionError error = result_msg.getError();
          ProcessMessage process_msg = result_msg.getMessage();

          // Push onto the queue
          function_queue.pushToFunctionQueue(new FunctionQueueItem(
              call_id, result_type, process_msg, null, error, null, false));

          // And notify process service that the queue changed,
          process_service.notifyMessagesAvailable(process_id);
        }
        // Otherwise the notifier will handle it,

        // Regardless, return the call_id,
        return call_id;

      }
      else {
        // 'ignore_reply' will make this return -1
        return -1;
      }
    }

    @Override
    public ProcessId createProcess(String webapp_name, String process_class)
                                          throws ProcessUnavailableException {
      // Delegate,
      return client_service.createProcess(
                                     account_name, webapp_name, process_class);
    }

    @Override
    public ProcessInfo getProcessInfo(ProcessId process_id)
                                          throws ProcessUnavailableException {
      // Delegate,
      return client_service.getProcessInfo(account_name, process_id);
    }

    @Override
    public void sendSignal(ProcessId process_id, String[] signal) {
      // Delegate,
      client_service.sendSignal(account_name, process_id, signal);
    }

    @Override
    public int invokeFunction(
           ProcessId process_id, ProcessMessage msg, boolean reply_expected) {

      // Invoke the function,
      final ProcessResult result = client_service.invokeFunction(
                                        account_name, NO_CONTEXT,
                                        process_id, msg, reply_expected);
      return pushMessageOnCallback(result, reply_expected);

    }

    @Override
    public void addChannelListener(ProcessChannel process_channel)
                                          throws ProcessUnavailableException {
      addBroadcastChannelListener(account_name, process_channel);
    }

    @Override
    public void addChannelListener(ChannelSessionState session_state)
                                          throws ProcessUnavailableException {
      addBroadcastChannelListener(account_name, session_state);
    }

    @Override
    public void removeChannelListener(ProcessChannel process_channel) {
      removeBroadcastChannelListener(process_channel);
    }

    // -----

    @Override
    public int invokeServersQuery(ServersQuery query) {

      // Invoke the function,
      final ProcessResult ret = client_service.invokeServersQuery(
                                            account_name, NO_CONTEXT, query);
      return pushMessageOnCallback(ret, true);

    }

  }

  /**
   * Implementation of ProcessInputMessage. It is guaranteed that any calls
   * to the functions here will be under the user code lock for this
   * instance.
   */
  private static class PFunctionHandlerImpl implements ProcessInputMessage {

    private final ProcessInstanceImpl process_instance;
    private final FunctionQueueItem function_item;
    private boolean reply_sent = false;

    private PFunctionHandlerImpl(ProcessInstanceImpl process_instance,
                                 FunctionQueueItem item) {
      if (process_instance == null) throw new NullPointerException();
      if (item == null) throw new NullPointerException();
      this.process_instance = process_instance;
      this.function_item = item;
    }

    private void send(PMessage pmsg) {
      try {
        // Only send if reply is expected,
        if (function_item.getReplyExpected()) {
          // The connection to reply to,
          NIOConnection connection = function_item.getConnection();

          // If replying to a connection,
          if (connection != null) {
            try {
              // Send the reply message,
              connection.sendFirstMessage(pmsg);
              connection.flushSendMessages();
            }
            catch (IOException e) {
              // On IOException, close the connection,
              ProcessServerService.PROCESS_LOG.log(Level.SEVERE,
                "Closed connection due to IOException in delegated task", e);
              connection.close();
            }
          }
        }
      }
      finally {
        reply_sent = true;
      }
    }

    @Override
    public Type getType() {
      return function_item.getType();
    }

    @Override
    public ProcessMessage getMessage() {
      return function_item.getMessage();
    }

    @Override
    public ChannelSessionState getBroadcastSessionState() {
      return function_item.getBroadcastSessionState();
    }

    @Override
    public int getCallId() {
      return function_item.getCallId();
    }

    @Override
    public ProcessFunctionError getError() {
      return function_item.getError();
    }

    @Override
    protected void finalize() throws Throwable {
      super.finalize();

      // If this is a function invoke and a reply was not sent, send a control
      // code 16 which will clean up any resources associated with this
      // message.
      if (getType() == ProcessInputMessage.Type.FUNCTION_INVOKE && !reply_sent) {

        try {
          // Only reply if reply is expected,
          if (function_item.getReplyExpected()) {
            ProcessId process_id = process_instance.process_id;

            // Wrap the returned message in a 'success' message,
            int nsize = 22;
            ByteArrayOutputStream bout = new ByteArrayOutputStream(nsize);
            // Write the header,
            int call_id = getCallId();
            // NOTE: Command code is 16
            byte[] header = ProcessServerService.createHeader(
                           process_id, call_id, CommConstants.CALL_CLEANUP_CC);
            bout.write(header, 0, header.length);
            // Write the returned message content,
            bout.flush();
            // Put the reply on the output queue,
            // Turn it into a return pmsg,
            PMessage return_pmsg = new PMessage(bout.toByteArray());

            // Send the return message,
            send(return_pmsg);

          }

        }
        finally {
          reply_sent = true;
        }
      }
    }

  }

  private static interface UserCodeRunner {
    public Object run(ProcessOperation process_object)
                    throws PException, SuspendedProcessException,
                           ProcessUserCodeException, ClassNotFoundException;
  }

  /**
   * A wrapped OutputStream for security reasons.
   */
  private static class SecureWrappedOutputStream extends OutputStream {
    
    private final OutputStream backed;

    private SecureWrappedOutputStream(OutputStream backed) {
      this.backed = backed;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      backed.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
      backed.write(b);
    }

    @Override
    public void write(int b) throws IOException {
      backed.write(b);
    }

    @Override
    public void flush() throws IOException {
      // Our secure implementation is no-op
    }

    @Override
    public void close() throws IOException {
      // Our secure implementation is no-op
    }
    
  }

}
