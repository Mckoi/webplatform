/**
 * com.mckoi.process.impl.ProcessClientService  Mar 31, 2012
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

import com.mckoi.appcore.SystemStatics;
import com.mckoi.mwpcore.ContextBuilder;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.ODBClass;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.process.*;
import com.mckoi.process.ProcessResultNotifier.Status;
import com.mckoi.util.ByteArrayUtil;
import com.mckoi.util.Cache;
import com.mckoi.webplatform.impl.LoggerService;
import com.mckoi.webplatform.impl.PlatformContextImpl;
import com.mckoi.webplatform.util.LogUtils;
import com.mckoi.webplatform.util.MonotonicTime;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * A ProcessClientService supports implementations of ProcessClient. A JVM
 * should only need one instance of ProcessClientService. This object
 * manages message communication between process clients and the process
 * services on a network.
 * <p>
 * Amongst other features, this maintains a buffer of messages sent to this
 * client from process servers.
 *
 * @author Tobias Downer
 */

public final class ProcessClientService {

  private static final Logger LOG = ProcessServerService.PROCESS_LOG;

  /**
   * The time we wait for a reply from the server before we time out.
   */
  private static final int FUNCTION_TIMEOUT_MS = 15000;

  /**
   * The DBSessionCache used to query the sysprocessxx paths to query and
   * manage process state information.
   */
  private final DBSessionCache sessions_cache;

  /**
   * The map of all connections to process clients.
   */
  private final Map<ProcessServiceAddress, ProcessClientConnection> connections;

  /**
   * The NIO selector for connections.
   */
  private Selector selector = null;

  /**
   * The ProcessEnvironment.
   */
  private final PEnvironment process_env;

  /**
   * The thread pool used for dispatching messages.
   */
  private final ExecutorService thread_pool;

  /**
   * The NetworkInterface used for outgoing communications with the DDB network.
   */
  private final NetworkInterface network_interface;

  /**
   * The output message queue and lock.
   */
  private final QueueList output_queue;
  
  /**
   * The input message queue and lock.
   */
  private final QueueList input_queue;

  /**
   * The broadcast queues.
   */
  private final Map<ProcessChannel, BroadcastQueue> broadcast_queues;

  /**
   * Whenever a process terminated message is received, the process id is
   * added to this set, which is used during the maintenance task.
   */
  private final ConcurrentMap<ProcessId, Boolean> process_id_terminated_set =
                                                    new ConcurrentHashMap<>();

  /**
   * The list of pending function results.
   */
  private final LinkedList<ProcessResultImpl> process_result_list;

  /**
   * Reentrant lock for registering with the selector.
   */
  private final ReentrantLock selector_lock = new ReentrantLock();

  /**
   * Last time the process path list was checked.
   */
  private volatile MonotonicTime last_checked = null;
  private volatile List<String> process_paths_list;
  private volatile List<ProcessServiceAddress> process_machines_list;

  // Random number generator,
  private final SecureRandom rng = ProcessServerService.SECURE_RNG;

  private final Timer process_client_timer;

  private Selector write_selector;
  private NIOWriteSelector nio_write_selector;

  /**
   * A cache used for machine name queries.
   */
  private final Cache machine_name_cache;

  /**
   * A cache used to hold ProcessInfoImpl queries.
   */
  private final Cache process_info_cache;

  /**
   * Constructor.
   * 
   * @param sessions_cache
   * @param shared_thread_pool
   * @param net_if the network interface used to communicate with the DDB
   *   network (used for link local IP resolution).
   */
  public ProcessClientService(DBSessionCache sessions_cache,
                              ExecutorService shared_thread_pool,
                              NetworkInterface net_if) {
    // Null check,
    if (sessions_cache == null) throw new NullPointerException();
    if (shared_thread_pool == null) throw new NullPointerException();

    // The cache of process info queries,
    this.machine_name_cache = new Cache(401, 400, 20);
    this.process_info_cache = new Cache(401, 400, 20);
//  DEBUG VALUE
//    this.process_info_cache = new Cache(17, 14, 20);

    this.output_queue = new QueueList();
    this.input_queue = new QueueList();
    this.broadcast_queues = new HashMap<>(512);
    this.process_result_list = new LinkedList<>();

    this.sessions_cache = sessions_cache;
    this.connections = new HashMap<>(256);
    this.process_env = new PCSProcessEnvironment();
    this.thread_pool = shared_thread_pool;

    this.network_interface = net_if;

    // The process timer thread,
    process_client_timer = new Timer("Mckoi Process Client Timer");
    process_client_timer.schedule(new MaintenanceTimerTask(), 30 * 1000);

  }

  /**
   * The maintenance task being run on the process client.
   */
  private int last_queue_size = -1;
  private class MaintenanceTimerTask extends TimerTask {
    @Override
    public void run() {
      try {

//        // Report,
//        System.out.println(report());

        List<BroadcastQueue> to_maintain;
        List<ProcessChannel> to_maintain_keys;
        if (last_queue_size == -1) {
          to_maintain = new ArrayList<>(256);
          to_maintain_keys = new ArrayList<>(256);
        }
        else {
          int sz = Math.min(256, last_queue_size + 4);
          to_maintain = new ArrayList<>(sz);
          to_maintain_keys = new ArrayList<>(sz);
        }
        synchronized (broadcast_queues) {
          Set<ProcessChannel> channels = broadcast_queues.keySet();
          last_queue_size = channels.size();
          for (ProcessChannel chan : channels) {
            to_maintain.add(broadcast_queues.get(chan));
            to_maintain_keys.add(chan);
          }
        }

        // Actually, twenty minutes and 15 seconds,
        // NOTE: This is the period at which a notifier will be sent a
        //   Status.TIMEOUT. A notifier may be cleaned up before the timeout
        //   is reached by the user code.
        final MonotonicTime twenty_mins_ago =
                     MonotonicTime.now(-((20 * 60 * 1000) + (15 * 1000)));

        Iterator<BroadcastQueue> bq_it = to_maintain.iterator();
        Iterator<ProcessChannel> pc_it = to_maintain_keys.iterator();

        int count = 0;

        while (bq_it.hasNext()) {

          BroadcastQueue queue = bq_it.next();
          ProcessChannel process_channel = pc_it.next();

          int clean_count;

          // Time out any notifiers older than twenty minutes,
          queue.timeoutNotifiersOlderThan(thread_pool, twenty_mins_ago);
          // Clear the broadcast queue of any messages that are expired,
          // ( expired messages typically will has sit on the queue for ~2
          //   minutes )
          clean_count = queue.cleanExpiredBroadcastMessages();

          // If the process id for this broadcast queue is terminated then
          // we clean all its listeners,
          if (process_id_terminated_set.containsKey(
                                             process_channel.getProcessId())) {
            queue.close();
          }
          else {

            // If the queue has listener locks then ensure we are receiving
            // broadcast requests for this channel,
            if (queue.hasListenerLocks()) {
              ensureReceivingBroadcast(process_channel);
            }

          }

          count += clean_count;

          // NOTE: Nested synchronized is ok here (and needed)
          synchronized (queue) {
            if (queue.isEmpty()) {
              synchronized (broadcast_queues) {
                broadcast_queues.remove(process_channel);
              }
            }
          }

        }

        // Clear the terminated set,
        process_id_terminated_set.clear();

        if (count > 0) {
          LOG.log(Level.FINE, "Cleaned {0} expired broadcast messages",
                              new Object[] { count });
        }

        // Try and reconnect to known failed connections,
        synchronized (connections) {
          Collection<ProcessClientConnection> pcs_list = connections.values();
          for (final ProcessClientConnection c : pcs_list) {
            // If the connection is known failed then submit a task that
            // attempts to re-establish the connection.
            // 
            if (c.isKnownFailed()) {
              thread_pool.submit(new Runnable() {
                @Override
                public void run() {
                  c.attemptEstablishConnection();
                }
              });
            }
          }
        }

      }
      catch (Throwable e) {
        e.printStackTrace(System.err);
        LOG.log(Level.SEVERE, "Exception during maintenance task", e);
      }
      // Schedule next maintenance
      finally {
        // Schedule next task for 8 seconds from end of this task,
        process_client_timer.schedule(new MaintenanceTimerTask(), 8 * 1000);
      }
    }
  };

  /**
   * Reports information about this process client.
   */
  String report() {

    StringBuilder b = new StringBuilder();

    b.append("ProcessClientService report\n");
    synchronized (input_queue) {
      b.append(" input_queue = ").append(input_queue.size()).append("\n");
    }
    synchronized (output_queue) {
      b.append(" output_queue = ").append(output_queue.size()).append("\n");
    }
    b.append("Connections\n");
    synchronized (connections) {
      Collection<ProcessClientConnection> conns = connections.values();
      for (ProcessClientConnection c : conns) {
        String s = c.nio_connection.report();
        b.append(" ").append(s).append("\n");
      }
    }

    return b.toString();
  }

  /**
   * Opens this service.
   * @throws java.io.IOException
   */
  public void open() throws IOException {

    // Selector for managing OP_WRITE
    write_selector = Selector.open();
    nio_write_selector = new NIOWriteSelector(write_selector);
    nio_write_selector.start("Client");

    // Selector for all other IO,
    selector = Selector.open();
    
    Thread input_thread = new Thread(input_runnable, "Process Client Input");
    input_thread.setDaemon(true);
    input_thread.start();
    Thread output_thread = new Thread(output_runnable, "Process Client Output");
    output_thread.setDaemon(true);
    output_thread.start();
  }

  /**
   * Closes this service.
   * @throws java.io.IOException
   */
  public void close() throws IOException {
    selector.close();
    nio_write_selector.stop();
    write_selector.close();
  }

  /**
   * The input thread runnable.
   */
  private final Runnable input_runnable = new Runnable() {
    @Override
    public void run() {
      // This simply cycles through every OP_READ key on the selector and
      // pushes any pending messages through to the PEnvironment.
      try {
        while (true) {
          // Block until we have something to read,
          selector_lock.lock();
          selector_lock.unlock();
          int key_count = selector.select();

          if (key_count > 0) {
            Set<SelectionKey> keys = selector.selectedKeys();
            for (SelectionKey key : keys) {
              int r_ops = key.readyOps();
              // If it's a read operation from a socket channel,
              if ((r_ops & SelectionKey.OP_READ) != 0) {
                // Get the connection,
                NIOConnection conn = (NIOConnection) key.attachment();
                // Read data from channel
                boolean close = conn.readDataFromChannel();
                // If the stream closed,
                if (close) {
                  conn.close();
                }
              }
            }
            // Clear the keys,
            keys.clear();
          }
        }
      }
      catch (IOException e) {
        // This is bad, something happened in the selector
        LOG.log(Level.SEVERE, "Input thread closed", e);
        e.printStackTrace(System.err);
      }
    }
  };

  /**
   * The output thread runnable.
   */
  private final Runnable output_runnable = new Runnable() {
    @Override
    public void run() {
      // Consume from the output_queue and send the messages to the respective
      // servers.
      while (true) {
        QueueMessage queue_item = null;
        synchronized (output_queue) {
          do {
            queue_item = output_queue.getFirst();
            if (queue_item == null) {
              try {
                output_queue.wait();
              }
              catch (InterruptedException e) { /* ignore */ }
            }
            else {
              output_queue.clear();
            }
          } while (queue_item == null);
        }

        // 'queue_item' is a bunch of messages we need to dispatch to process
        // servers.
        // The set of touched connections,
        Map<ProcessClientConnection, QueueList> touched_connections =
                                                              new HashMap<>();
        synchronized (connections) {

          while (queue_item != null) {
            // Fetch the next in the list here (because it gets changed as a
            // side effect).
            QueueMessage next_queue_item = queue_item.getNext();

            // The machine this message is destined for,
            ProcessServiceAddress machine_name = queue_item.getMachine();
            // If there's a connection object for this machine,
            ProcessClientConnection conn = getCurrentConnect(machine_name);
            // Add to the set of touched connections,
            QueueList conn_messages = touched_connections.get(conn);
            if (conn_messages == null) {
              conn_messages = new QueueList();
              touched_connections.put(conn, conn_messages);
            }

            // Put the message in the queue for this connection,
            conn_messages.add(queue_item);

            // Next message,
            queue_item = next_queue_item;
          }

        }

//        System.out.println("touched_connections.keySet().size() = " + touched_connections.keySet().size());

        // Dispatch message writes on the thread pool
        for (final ProcessClientConnection conn : touched_connections.keySet()) {
          // The messages to add to this connection,
          QueueList add_queue = touched_connections.get(conn);

          // Synchronize over the queue and add the messages being dispatched,
          conn.putAllMessagesInQueue(add_queue.getFirst());

          // Dispatch a flush operation on this connection from the thread
          // pool,
          thread_pool.submit(new Runnable() {
            @Override
            public void run() {
              conn.flushPendingMessages();
            }
          });

        }

      }
    }
  };

  /**
   * Creates a unique call id value.
   */
  int generateCallId() {
    while (true) {
      int val = rng.nextInt();
      if (val < -100 || val > 100) {
        return val;
      }
    }
  }

  /**
   * Creates a process initialization message.
   */
  private PMessage createInitProcessMessage(
            ProcessId process_id,
            String account_name, String app_name, String process_name) {

    // Pick a random call_id,
    int call_id = generateCallId();
    // NOTE: Command code is 1
    byte[] header =
            ProcessServerService.createHeader(process_id, call_id,
                                             CommConstants.FUNCTION_INIT_CC);

    Object[] args = new Object[] {
      account_name, app_name, process_name
    };

    return PMessage.encodeArgsList(header, args);
  }

  /**
   * Creates a process query message.
   */
  private PMessage createProcessQueryMessage(ProcessId process_id) {

    // Pick a random call_id,
    int call_id = generateCallId();
    // NOTE: Command code is 7
    byte[] header =
        ProcessServerService.createHeader(process_id, call_id,
                                         CommConstants.PROCESS_QUERY);

    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream(20);
      // Write the header,
      bout.write(header);
      // Return it as a PMessage,
      byte[] b = bout.toByteArray();
      return new PMessage(b);
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }

  }

  /**
   * Creates a broadcast request message.
   */
  private PMessage createBroadcastRequestMessage(
                ProcessId process_id, int channel_num, long min_sequence_val) {

    // NOTE: Command code is 4
    byte[] header = ProcessServerService.createHeader(process_id, 0,
                                           CommConstants.BROADCAST_REQUEST_CC);

    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream(20 + 8);
      // Write the header,
      bout.write(header);
      // Write the channel number,
      DataOutputStream dout = new DataOutputStream(bout);
      dout.writeInt(channel_num);
      dout.writeLong(min_sequence_val);
      dout.flush();

      // Return it as a PMessage,
      byte[] b = bout.toByteArray();
      return new PMessage(b);
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }

  }

  /**
   * Returns the current connection for the given machine.
   */
  private ProcessClientConnection getCurrentConnect(
                                              ProcessServiceAddress machine) {
    synchronized (connections) {
      ProcessClientConnection conn = connections.get(machine);
      if (conn == null) {
        conn = new ProcessClientConnection(machine);
        connections.put(machine, conn);
      }
      return conn;
    }
  }

  /**
   * Returns the time the current connection to the given machine was
   * established. This is used to determine if information related to a
   * connection is currently stale (the connect time differs from when the
   * interface was created).
   */
  private MonotonicTime getCurrentConnectTime(ProcessServiceAddress machine) {
    return getCurrentConnect(machine).getLastConnectedTime();
  }

  /**
   * Returns true if the current connection to the given machine is in a
   * failed state.
   */
  private boolean isCurrentConnectKnownFailed(ProcessServiceAddress machine) {
    return getCurrentConnect(machine).isKnownFailed();
  }

  /**
   * Generates an exception (InvalidProcessException) if the given process
   * channel is not owned by the given account.
   */
  private void checkAccountOwnership(
                    String account_name, ProcessChannel process_channel)
                                          throws ProcessUnavailableException {

    // Find out information about the process,
    ProcessInfoImpl process_info = getProcessInfo(process_channel.getProcessId());

    // If the account name doesn't match,
    if (!account_name.equals(
                     process_info.getAccountApplication().getAccountName())) {
      throw new InvalidProcessException("Not owned by account");
    }
    
  }

  /**
   * Fails any notifiers that are currently sitting waiting for a message
   * from the given machine address. This is called when a connection with a
   * machine is unexpectedly closed.
   * 
   * @param machine_addr 
   */
  private void failNotifiersWaitingOnMachine(
                                          ProcessServiceAddress machine_addr) {

    final List<BroadcastQueue> fail_set = new ArrayList<>();

    synchronized (broadcast_queues) {
      Set<Map.Entry<ProcessChannel, BroadcastQueue>> entries =
                                                  broadcast_queues.entrySet();
      for (Map.Entry<ProcessChannel, BroadcastQueue> entry : entries) {
        BroadcastQueue queue = entry.getValue();
        ProcessServiceAddress remote_addr = queue.getRemoteMachine();
        if (remote_addr.equals(machine_addr)) {
          queue.resetConnectTime();
          fail_set.add(queue);
        }
      }
    }

    // Fail all notifiers,
    for (BroadcastQueue queue : fail_set) {
      queue.failAllNotifiers(thread_pool);
    }

  }

  /**
   * Fail all pending process results waiting on the given machine.
   * 
   * @param machine_addr 
   */
  private void failPendingProcessResults(ProcessServiceAddress machine_addr) {

    final List<ProcessResultImpl> fail_set = new ArrayList<>();

    synchronized (process_result_list) {
      for (ProcessResultImpl pr : process_result_list) {
        if (pr.isFromRemote(machine_addr)) {
          fail_set.add(pr);
        }
      }
    }

    // Notify failures,
    for (ProcessResultImpl pr : fail_set) {
      pr.notifyResult(Status.IO_ERROR);
    }

  }

  /**
   * This method handles failure of a connection to the given machine. This
   * will reset the queues and add broadcast request messages for any waiting
   * notifiers to the output queue so the broadcast listener will renew when
   * the connection next succeeds.
   * 
   * @param machine_addr 
   */
  private void failBroadcastQueuesOn(ProcessServiceAddress machine_addr) {

    List<Map.Entry<ProcessChannel, BroadcastQueue>> reestablish_set =
                                                              new ArrayList<>();

    // Reset connect times,
    synchronized (broadcast_queues) {
      Set<Map.Entry<ProcessChannel, BroadcastQueue>> entries =
                                                  broadcast_queues.entrySet();
      for (Map.Entry<ProcessChannel, BroadcastQueue> entry : entries) {
        BroadcastQueue queue = entry.getValue();
        ProcessServiceAddress remote_addr = queue.getRemoteMachine();
        if (remote_addr.equals(machine_addr)) {
          queue.resetConnectTime();
          if (!queue.isEmpty()) {
            reestablish_set.add(entry);
          }
        }
      }
    }

    // Add broadcast request messages,
    for (Map.Entry<ProcessChannel, BroadcastQueue> entry : reestablish_set) {
      ProcessChannel process_channel = entry.getKey();
      BroadcastQueue broadcast_queue = entry.getValue();
      long min_sequence_val = broadcast_queue.getLastSequenceValue();
      ProcessId process_id = process_channel.getProcessId();
      PMessage pmsg = createBroadcastRequestMessage(
                   process_id, process_channel.getChannel(), min_sequence_val);
      putMessageOnOutput(new QueueMessage(machine_addr, pmsg));
    }

  }

  /**
   * Fails all notifiers waiting on events from the given machine address by
   * issuing a Status.IO_ERROR event. This is used when a connection closes or
   * the connection fails to establish.
   * 
   * @param machine_addr 
   */
  private void failNotifierOnMachineAddress(ProcessServiceAddress machine_addr) {
    failBroadcastQueuesOn(machine_addr);
    // Fail all pending process results,
    failPendingProcessResults(machine_addr);
  }

  /**
   * Returns the QueueList for the given process channel.
   */
  private BroadcastQueue getProcessChannelQueue(ProcessChannel process_channel) {
    synchronized (broadcast_queues) {
      BroadcastQueue queue = broadcast_queues.get(process_channel);
      if (queue == null) {
        queue = new BroadcastQueue();
        broadcast_queues.put(process_channel, queue);
      }
      return queue;
    }
  }

  /**
   * Puts all the messages into the broadcast queue in this client.
   */
  private void putMessagesInBroadcastQueue(
                           ProcessChannel process_channel, QueueList in_msgs) {

    // Puts this message into the respective process channel queue,
    // Do we have an active queue for it?
    BroadcastQueue queue = getProcessChannelQueue(process_channel);

    // Put the messages in the queue,
    queue.putMessagesInQueue(in_msgs, thread_pool);

  }

  /**
   * Removes the given notifier from the broadcast message queue.
   */
  void removeNotifier(String account_name, ProcessChannel process_channel,
                      ProcessResultNotifier notifier)
                                          throws ProcessUnavailableException {

    // Check the ownership of the process matches 'account_name'
    checkAccountOwnership(account_name, process_channel);

    // Make sure we are synchronized over the queue before we perform the
    // meta operation,
    BroadcastQueue queue = getProcessChannelQueue(process_channel);
    // Remove the notifier,
    queue.removeNotifier(notifier);

  }

  /**
   * Returns the next message from the broadcast queue (the next message
   * after the sequence number).
   */
  PMessage getMessageFromBroadcast(
                    String account_name,
                    ProcessChannel process_channel, long sequence_value)
                                          throws ProcessUnavailableException {

    // Check the ownership of the process matches 'account_name'
    checkAccountOwnership(account_name, process_channel);

    // Do we have an active queue?
    BroadcastQueue queue;
    synchronized (broadcast_queues) {
      queue = broadcast_queues.get(process_channel);
      if (queue == null) {
        // Return if no queue,
        return null;
      }
    }

    return queue.getMessageFromBroadcast(sequence_value);

  }

  /**
   * Returns the next messages from the broadcast queue (the next messages
   * after the sequence number). The number of messages consumed from the queue
   * will not exceed 'consume_limit'. If there are no messages left of the
   * queue to consume, the given 'notifier' will be called when either there
   * are messages pending or consume limit is reached.
   */
  List<PMessage> getMessagesFromBroadcast(
          String account_name, ContextBuilder contextifier,
          ProcessChannel process_channel, long sequence_value,
          int consume_limit, ProcessResultNotifier notifier)
                                          throws ProcessUnavailableException {

    // Check the ownership of the process matches 'account_name'
    checkAccountOwnership(account_name, process_channel);

    // Do we have an active queue?
    BroadcastQueue queue = getProcessChannelQueue(process_channel);

    // Get the messages from the queue,
    return queue.getMessagesFromBroadcast(
                        sequence_value, consume_limit, notifier, contextifier);

  }

  /**
   * Puts a message on the output queue.
   */
  private void putMessageOnOutput(QueueMessage queue_msg) {
    synchronized (output_queue) {
      output_queue.add(queue_msg);
      // Notify the dispatcher thread blocking on it,
      output_queue.notifyAll();
    }
  }

  /**
   * When messages are received from the process service, this method is
   * called which dispatches the messages on the various queues.
   */
  private void putAllMessagesOnInput(
            ProcessServiceAddress machine_addr, Collection<PMessage> messages) {

    // Input messages chain,
    QueueList inputs = null;
    // Make a map of broadcast channels,
    Map<ProcessChannel, QueueList> to_broadcast = null;
    // To acknowledge,
    List<ProcessChannel> to_ack = null;

    // This will iterate through the incoming messages and sort them into
    // 'input' message (replies to immediate functions) and broadcast
    // messages (messages put on a broadcast channel). The broadcast messages
    // are put into the 'to_broadcast' map and then placed in the respective
    // buffer in this object.

    // For each message,
    for (PMessage msg : messages) {
      // The message as a byte buffer,
      ByteBuffer bb = msg.asByteBuffer();
      // The command code,
      byte command_code = bb.get(0);
      // Handle the message depending on the command code,

      // Reply to a process function,
      if (command_code == CommConstants.CALL_REPLY_CC) {
        // Command code 14 is a standard 'input' message,
        if (inputs == null) {
          inputs = new QueueList();
        }
        inputs.add(new QueueMessage(machine_addr, msg));
      }

      // Channel broadcast input,
      else if (command_code == CommConstants.BROADCAST_MESSAGE_CC) {
        // Command code 15 is a channel broadcast message,
        ProcessId process_id = PMessage.createProcessIdFromBuffer(bb);
        // The channel number
        int channel_number = bb.getInt(16);
        // Turn it into a ProcessChannel
        ProcessChannel process_channel =
                                new ProcessChannel(process_id, channel_number);
        // Put the message in the appropriate queue,
        if (to_broadcast == null) {
          to_broadcast = new HashMap<>();
        }
        QueueList chan_queue = to_broadcast.get(process_channel);
        if (chan_queue == null) {
          chan_queue = new QueueList();
          to_broadcast.put(process_channel, chan_queue);
        }
        chan_queue.add(new QueueMessage(machine_addr, msg));
      }

      // Function cleanup control code,
      else if (command_code == CommConstants.CALL_CLEANUP_CC) {
        if (inputs == null) {
          inputs = new QueueList();
        }
        inputs.add(new QueueMessage(machine_addr, msg));
      }

      // Broadcast request acknowledge,
      else if (command_code == CommConstants.ACK_BROADCAST_REQUEST_CC) {
        ProcessId process_id = PMessage.createProcessIdFromBuffer(bb);
        // The channel number,
        int channel_number = bb.getInt(16);
        ProcessChannel process_channel =
                                new ProcessChannel(process_id, channel_number);

        LOG.log(Level.FINE,
              "Broadcast Request Acknowledge on channel {0}",
              new Object[] { process_channel });

        // Put in the ack list,
        if (to_ack == null) {
          to_ack = new ArrayList<>();
        }
        to_ack.add(process_channel);
      }
      
      // Process the 'is terminated' response,
      else if (command_code == CommConstants.NOTIFY_TERMINATED_CC) {
        // This is used by the maintenance task to determine the channels
        // we no longer care about,
        ProcessId process_id = PMessage.createProcessIdFromBuffer(bb);
        process_id_terminated_set.put(process_id, Boolean.TRUE);
      }

      else {
        throw new RuntimeException("Unknown command code: " + command_code);
      }

    }

    // If there are acknowledge messages,
    if (to_ack != null) {
      // The current connect time to the machine,
      MonotonicTime connect_time = getCurrentConnectTime(machine_addr);
      for (ProcessChannel c : to_ack) {
        // Set them to receiving,
        BroadcastQueue broadcast_queue = getProcessChannelQueue(c);
        broadcast_queue.setRemoteMachine(machine_addr);
        broadcast_queue.setConnectTime(connect_time);
      }
    }

    // The process results to notify,
    List<ProcessResultImpl> to_notify = null;

    // If there are input messages,
    if (inputs != null) {
      // Add all the input messages to the queue under synchronization on the
      // input_queue,
      QueueMessage msg = inputs.getFirst();
      while (msg != null) {
        QueueMessage next_msg = msg.getNext();

        ProcessServiceAddress machine = msg.getMachine();
        ProcessResultImpl ares = null;
        // If there's a notifier for this,
        synchronized (process_result_list) {
          Iterator<ProcessResultImpl> it = process_result_list.iterator();
          while (it.hasNext()) {
            ProcessResultImpl result = it.next();
            // If it matches, remove from the list,
            if ( result.matches(msg, machine) ) {
              // If it's command code 14 then we tag this to notify.
              // Otherwise it must be command code 16 (cleanup)
              byte command_code = msg.getMessage().getCommandCode();
              if (command_code == CommConstants.CALL_REPLY_CC) {
                ares = result;
              }
              else if (command_code != CommConstants.CALL_CLEANUP_CC) {
                // Otherwise throw an error if unexpected command code,
                throw new RuntimeException(
                                   "Unexpected command code: " + command_code);
              }
              it.remove();
              break;
            }
          }
        }

        // If the reply was on the process_result_list then we set the PMessage
        if (ares != null) {
          ares.setPMessage(msg.getMessage());
          if (to_notify == null) {
            to_notify = new ArrayList<>();
          }
          to_notify.add(ares);
        }
        // If not consumed then add to the input_queue,
        else {
          synchronized (input_queue) {
            input_queue.add(msg);
          }
        }

        msg = next_msg;
      }
      synchronized (input_queue) {
        input_queue.notifyAll();
      }
    }

    // If there are broadcast messages,
    if (to_broadcast != null) {
      // For each process channel,
      for (ProcessChannel process_channel : to_broadcast.keySet()) {
        // Place the messages in the respective queue
        QueueList chan_queue = to_broadcast.get(process_channel);
        putMessagesInBroadcastQueue(process_channel, chan_queue);
      }
    }

    // If there are results to notify,
    if (to_notify != null) {
      for (ProcessResultImpl process_result : to_notify) {
        process_result.notifyResult(Status.MESSAGES_WAITING);
      }
    }

  }

  /**
   * Blocks until a message with the given call_id is received on the input
   * queue. When found, returns the message and removes it from the queue.
   */
  private PMessage blockUntilReply(
                ProcessServiceAddress machine, ProcessId pid, int call_id)
                    throws InterruptedException {

    synchronized (input_queue) {
      final MonotonicTime start_time = MonotonicTime.now();
      QueueMessage msg = input_queue.getFirst();
      while (true) {
        // Reached the end of the queue so we need to block,
        if (msg == null) {
          // If timed out,
          if (MonotonicTime.millisSince(start_time) > FUNCTION_TIMEOUT_MS) {
            // Failure,
            return ProcessServerService.failMessage(pid, call_id,
                              "UNAVAILABLE-TIMEOUT", new PTimeoutException());
          }
          input_queue.wait(FUNCTION_TIMEOUT_MS);
          // After wait, go back to the head of the queue to search for the
          // call_id.
          msg = input_queue.getFirst();
        }
        else {
          // Call id and machine matches, so remove this,
          if (msg.matches(pid, call_id) && msg.getMachine().equals(machine)) {
            // Remove this,
            input_queue.remove(msg);
            // Return the PMessage,
            return msg.getMessage();
          }
          msg = msg.getNext();
        }
      }
    }
  }

  /**
   * Blocks until the connection initialization message for the given machine
   * is found in the queue.
   */
  private QueueMessage blockUntilConnectionInit(ProcessServiceAddress machine)
                                                  throws InterruptedException {
    synchronized (input_queue) {
      final MonotonicTime start_time = MonotonicTime.now();
      QueueMessage msg = input_queue.getFirst();
      while (true) {
        // Reached the end of the queue so we need to block,
        if (msg == null) {
          // If timed out,
          if (MonotonicTime.millisSince(start_time) > FUNCTION_TIMEOUT_MS) {
            throw new PRuntimeException("Reply timeout");
          }
          input_queue.wait(FUNCTION_TIMEOUT_MS);
          // After wait, go back to the head of the queue to seach for the
          // call_id.
          msg = input_queue.getFirst();
        }
        else {
          if (msg.getMachine().equals(machine)) {
            ByteBuffer bb = msg.getMessage().asByteBuffer();
            long l1 = bb.getLong(0) & 0x000FFFFFFFFFFFFFFL;
            long l2 = bb.getLong(8);
            int call_id = bb.getInt(16);
            // This is the signature for the initialization message,
            if (call_id == 1 && l1 == 0) {
              // Remove this,
              input_queue.remove(msg);
              return msg;
            }
          }
          msg = msg.getNext();
        }
      }
    }
  }

  /**
   * Sends a message to the the given machine and blocks until a reply is
   * received from the process.
   */
  private PMessage sendMessage(ProcessServiceAddress machine, PMessage msg)
                                                  throws InterruptedException {

    // Get the call id from the message being sent,
    final int call_id = msg.getCallId();
    final ProcessId pid = msg.getProcessId();

    // Put the message to send on the out-going message queue,
    putMessageOnOutput(new QueueMessage(machine, msg));
    // Block until we receive a message that replies to this message,
    PMessage reply = blockUntilReply(machine, pid, call_id);

    // Return the message,
    return reply;
  }

  /**
   * Ensures this client service is receiving broadcast messages for the
   * given process channel. If this service is not currently receiving
   * broadcast messages, sends a request to the server to begin forwarding
   * the broadcast data. If this service is currently receiving the broadcast
   * messages, returns immediately.
   */
  private void ensureReceivingBroadcast(ProcessChannel process_channel) {

    // Do we have an active queue?
    BroadcastQueue queue = getProcessChannelQueue(process_channel);

    long min_sequence_val = queue.getLastSequenceValue();

    // Returns the canonical machine name handling the process id
    ProcessServiceAddress machine =
                            getMachineNameFor(process_channel.getProcessId());

    // Get the connect time,
    MonotonicTime connect_time = getCurrentConnectTime(machine);
    MonotonicTime queue_connect_time = queue.getConnectTime();

    // If we are not receiving on the queue or 4 minutes has passed since the
    // last request,
    boolean not_received_on_queue =
               queue_connect_time == null || connect_time == null ||
             ( queue_connect_time.equals(connect_time) );

    boolean timeout_on_request = queue.requestExpired();
    if (not_received_on_queue || timeout_on_request) {

      // Send a request to the process that we are still interested in
      // broadcast messages that originate from it,
      queue.setRequestNotExpired();
      ProcessId process_id = process_channel.getProcessId();
      PMessage pmsg = createBroadcastRequestMessage(
                   process_id, process_channel.getChannel(), min_sequence_val);
      putMessageOnOutput(new QueueMessage(machine, pmsg));
      
      LOG.log(Level.FINE,
              "Enqueued Broadcast Request message. Channel {0} to Machine {1}",
              new Object[] { process_channel, machine });

    }

    // Note; We don't block waiting for a reply when we put this message on
    //  the output queue.

  }

  /**
   * Adds a broadcast queue listener lock on the given process channel. While
   * at least one listener lock is on a queue, it will be renewed every
   * maintenance cycle.
   */
  void addBroadcastQueueListenerLock(ProcessChannel process_channel) {

    // Get the queue,
    BroadcastQueue queue = getProcessChannelQueue(process_channel);
    // Add a listener lock on it,
    queue.addListenerLock();

  }

  /**
   * Removes a broadcast queue listener lock on the given process channel.
   */
  void removeBroadcastQueueListenerLock(ProcessChannel process_channel) {

    // Get the queue,
    BroadcastQueue queue = getProcessChannelQueue(process_channel);
    // Add a listener lock on it,
    queue.removeListenerLock();

  }

  /**
   * Creates a ProcessServiceAddress given a machine_str stored in the process
   * records.
   */
  private ProcessServiceAddress createAddressFromMachineString(
                                                          String machine_str) {
    int delim = machine_str.lastIndexOf("$");
    if (delim == -1) {
      // Use default port,
      return new ProcessServiceAddress(machine_str);
    }
    return new ProcessServiceAddress(machine_str.substring(0, delim));
//    return new ProcessServiceAddress(machine_str,
//                          ProcessServerService.DEFAULT_PROCESS_TCP_PORT);
  }

  /**
   * Returns the list of process paths. This caches the list and checks the
   * system path every 2 minutes.
   */
  private List<String> getSystemProcessPaths() {

    final MonotonicTime current_time = MonotonicTime.now();
    if (process_paths_list == null ||
        last_checked == null ||
        MonotonicTime.millisDif(current_time, last_checked) > (2 * 60 * 1000)) {

      last_checked = current_time;
      ODBTransaction syst =
                  sessions_cache.getODBTransaction(SystemStatics.SYSTEM_PATH);
      ODBObject paths_ob = syst.getNamedItem("paths");
      ODBList path_idx = paths_ob.getList("pathIdx");
      ODBList sysprocess_names = path_idx.tail("sysprocess00");
      // Extract all the sysprocess path names,
      List<String> pp_list = new ArrayList<>();
      for (ODBObject sysprocess_name : sysprocess_names) {
        String path_name = sysprocess_name.getString("path");
        // If it's a sysprocess path,
        if (path_name.startsWith("sysprocess")) {
          // Add to the list,
          pp_list.add(path_name);
        }
        else {
          break;
        }
      }

      ODBObject roles_ob = syst.getNamedItem("roles");
      ODBList roleserver_idx = roles_ob.getList("roleserverIdx");
      ODBList processes_rs_idx = roleserver_idx.tail("process.");
      // All the process servers,
      List<ProcessServiceAddress> pm_list = new ArrayList<>();
      for (ODBObject role : processes_rs_idx) {
        String roleserver = role.getString("roleserver");
        // If it's a process role,
        if (roleserver.startsWith("process.")) {
          // Add the server to the list,

          // PENDING: Handle process services on alternative ports,
          ProcessServiceAddress addr =
                          new ProcessServiceAddress(role.getString("server"));

          pm_list.add(addr);
        }
        else {
          break;
        }
      }

      // Set the process machines
      process_machines_list = Collections.unmodifiableList(pm_list);

      // Set the list,
      process_paths_list = Collections.unmodifiableList(pp_list);

    }

    return process_paths_list;
  }

  /**
   * Returns a random process id.
   */
  private String randomProcessId(int path_val) {
    long high_id = System.currentTimeMillis();
    byte[] buf = new byte[8];
    rng.nextBytes(buf);
    buf[0] = 0;
    long low_id = ByteArrayUtil.getLong(buf, 0);
    ProcessId process_id = new ProcessId((byte) path_val, high_id, low_id);
    return process_id.getStringValue();
  }

  /**
   * Return the complete list of process servers.
   */
  private List<ProcessServiceAddress> getAllProcessMachines() {
    // Make sure the list is populated,
    getSystemProcessPaths();
    return process_machines_list;
  }

  /**
   * Populates the given process path with some new ids. Optionally takes a
   * set of machines that we don't populate ids on (can be null).
   */
  private void populateWithIds(String process_path, ODBTransaction t,
                               Set<ProcessServiceAddress> no_populate)
                                          throws ProcessUnavailableException {

    // Make 'process_machines', a list of available process servers,
    List<ProcessServiceAddress> process_machines = process_machines_list;
    if (no_populate != null && !no_populate.isEmpty()) {
      List<ProcessServiceAddress> mod_list = new ArrayList<>();
      Iterator<ProcessServiceAddress> i = process_machines.iterator();
      while (i.hasNext()) {
        ProcessServiceAddress addr = i.next();
        if (!no_populate.contains(addr)) {
          mod_list.add(addr);
        }
      }
      process_machines = mod_list;
    }
    int process_machines_sz = process_machines.size();
    // There are no available addresses!
    if (process_machines_sz == 0) {
      throw new ProcessUnavailableException(
                    "No process servers currently available",
                    ProcessUnavailableException.Reason.UNAVAILABLE,
                    null);
    }

    ODBObject root_ob = t.getNamedItem("root");
    ODBList available_ids = root_ob.getList("available_processIdx");
    ODBList all_ids = root_ob.getList("all_processIdx");

    // Work out the process path value
    String ppval_hex = process_path.substring(process_path.length() - 2);
    int path_val = Integer.parseInt(ppval_hex, 16);

    ODBClass process_class = t.findClass("Process");
    for (int i = 0; i < 64; ++i) {
      String process_id = randomProcessId(path_val);
      // Pick a random server from the list,
      ProcessServiceAddress machine =
              process_machines.get(rng.nextInt(process_machines_sz));
      String value = "";
      
      // [id, machine, value, state]
      ODBObject process_ob = t.constructObject(process_class,
                        process_id, machine.getMachineAddress(), value, null);

      // Add to the 'all_ids' and 'available_ids' list,
      all_ids.add(process_ob);
      available_ids.add(process_ob);
    }

  }


  // ----- Cached info -----

  /**
   * Loads the ODB process object from the database. Throws
   * InvalidProcessException if the process is not found.
   */
  private ODBObject loadProcessOb(ProcessId process_id) {

    // Not in the cache so perform the query,
    String process_id_str = process_id.getStringValue();

    // Make the sysprocess,
    StringBuilder pp_sb = new StringBuilder();
    pp_sb.append("sysprocess");

    // Look up the process id in the sysplatformxx paths,
    int path_val = ((int) process_id.getPathValue()) & 0x0FF;
    String path_val_str = Integer.toHexString(path_val);
    if (path_val_str.length() == 1) {
      pp_sb.append('0');
    }
    pp_sb.append(path_val_str);

    // Look up the process id in the process path,
    ODBTransaction ppt = sessions_cache.getODBTransaction(pp_sb.toString());
    ODBObject root_ob = ppt.getNamedItem("root");

    ODBList process_idx = root_ob.getList("all_processIdx");
    ODBObject process_ob = process_idx.getObject(process_id_str);

    if (process_ob == null) {
      throw new InvalidProcessException("Invalid id: " + process_id_str);
    }

    return process_ob;

  }

  /**
   * Returns the canonical machine name handling the given process id. If the
   * process id is not found in the system then a runtime exception is
   * generated. This information is cached. If the information is not in the
   * cache then we query the database for the information.
   */
  private ProcessServiceAddress getMachineNameFor(ProcessId process_id) {

    if (process_id == null) throw new RuntimeException();

    ProcessServiceAddress machine;
    synchronized (machine_name_cache) {
      machine = (ProcessServiceAddress) machine_name_cache.get(process_id);
    }
    
    // In the cache,
    if (machine != null) {
      return machine;
    }
    
    // Load the process ODB object from the DB,
    ODBObject process_ob = loadProcessOb(process_id);

    // The process machine,
    ProcessServiceAddress process_machine =
              createAddressFromMachineString(process_ob.getString("machine"));

    // Put it in the cache,
    synchronized (machine_name_cache) {
      machine_name_cache.put(process_id, process_machine);
    }

    // Return it,
    return process_machine;

  }

  /**
   * Returns a ProcessInfoImpl object for a process id. If the process is not
   * found in the system then a runtime exception is generated. Otherwise
   * the ProcessInfoImpl object contains the process details. This stores the
   * information behind a cache. If the information is not in the cache then
   * we query the database/process server for the information.
   */
  private ProcessInfoImpl getProcessInfo(ProcessId process_id)
                                          throws ProcessUnavailableException {

    if (process_id == null) throw new RuntimeException();

    ProcessInfoImpl process_info;
    synchronized (process_info_cache) {
      process_info = (ProcessInfoImpl) process_info_cache.get(process_id);
    }

    // In the cache,
    if (process_info != null) {
      return process_info;
    }

    // Load the process ODB object from the DB,
    ODBObject process_ob = loadProcessOb(process_id);

    // The process machine,
    ProcessServiceAddress process_machine =
              createAddressFromMachineString(process_ob.getString("machine"));

    // Is the 'value' field populated?
    String process_value = process_ob.getString("value");

    String account_name;
    String application_name;
    String process_name;

    if (process_value != null && !process_value.equals("")) {
      // Yes, so parse it,
      try {
        JSONTokener json = new JSONTokener(process_value);
        JSONObject json_ob = (JSONObject) json.nextValue();
        account_name = json_ob.getString("acn");     // account name
        application_name = json_ob.getString("apn"); // application name
        process_name = json_ob.getString("pn");      // process name
      }
      catch (JSONException e) {
        throw new PRuntimeException(e);
      }
    }
    else {
      // Not populated so query the machine for the details of the process,

      // Create the function message,
      PMessage fun_msg = createProcessQueryMessage(process_id);

      try {

        // Send the message and block until reply,
        PMessage reply = sendMessage(process_machine, fun_msg);

        // The message will have the characters 'S;' if function success,

        if (reply.isFailMessage()) {

          ProcessFunctionError originating_ex = reply.messageFailError();

          if (originating_ex.getErrorType().startsWith("UNAVAILABLE")) {
            throw new ProcessUnavailableException(
                    "Process Unavailable",
                    ProcessUnavailableException.Reason.UNAVAILABLE,
                    process_machine);
          }

          // PENDING: we need to make a distinction here between 'process not
          //   found' and exceptions caused by erroneous state.
          throw new InvalidProcessException();

        }
        else if (reply.isSuccessMessage()) {

          // Extract the returned details,
          Object[] args = reply.asArgsList(22);
          account_name = (String) args[0];
          application_name = (String) args[1];
          process_name = (String) args[2];

        }
        else {
          throw new PRuntimeException("Error in format of reply message");
        }

      }
      catch (InterruptedException e) {
        throw new PRuntimeException("Interrupted");
      }
      
    }
    
    // Make the ProcessInfoImpl object,
    process_info = new ProcessInfoImpl(
                    process_machine,
                    new AccountApplication(account_name, application_name),
                    process_name);

    // Put it in the cache,
    synchronized (process_info_cache) {
      process_info_cache.put(process_id, process_info);
    }

    // Return it,
    return process_info;

  }

  /**
   * Returns the process info for the given process id, and checks that the
   * process is owned by the given account_name. If it's not owned by the
   * account, or the process is invalid, returns null.
   * 
   * @param account_name
   * @param process_id
   * @return 
   * @throws com.mckoi.process.ProcessUnavailableException 
   */
  public ProcessInfo getProcessInfo(String account_name, ProcessId process_id)
                                          throws ProcessUnavailableException {
    try {
      final ProcessInfoImpl pi =
                        ProcessClientService.this.getProcessInfo(process_id);
      if (!pi.getAccountApplication().getAccountName().equals(account_name)) {
        return null;
      }
      return new ProcessInfo() {
        @Override
        public String getApplicationName() {
          return pi.getAccountApplication().getApplicationName();
        }
        @Override
        public String getProcessClassName() {
          return pi.getProcessName();
        }
      };
    }
    // Return null on invalid process,
    catch (InvalidProcessException e) {
      return null;
    }
  }

  
  
  
  // ----- Support for ProcessClient methods -----

  /**
   * Returns a com.mckoi.process.ProcessClient implementation for
   * the given account.
   * 
   * @param account_name
   * @return 
   */
  public ProcessClient getProcessClientFor(String account_name) {
    return new PCSProcessClient(account_name);
  }

  /**
   * Returns a com.mckoi.process.AppServiceProcessClient implementation for
   * the given account.
   * 
   * @param account_name
   * @param contextifier the context environment that all notified events are
   *   given.
   * @return 
   */
  public AppServiceProcessClient getAppServiceProcessClientFor(
                          String account_name, ContextBuilder contextifier) {
    return new PCSAppServiceProcessClient(account_name, contextifier);
  }

  public ProcessId createProcess(String account_name,
                                 String webapp_name, String process_class)
                                          throws ProcessUnavailableException {

    // Query the system process paths for a process id,
    // Get the list of all process paths,
    List<String> sys_process_paths = getSystemProcessPaths();
    int size = sys_process_paths.size();
    if (size == 0) {
      // Oops,
      throw new PRuntimeException("No process paths");
    }
    // Pick a random one
    int process_path_num = 0;
    if (size > 1) {
      process_path_num = rng.nextInt(size);
    }
    // The process path name (eg. 'sysprocess00')
    String process_path = sys_process_paths.get(process_path_num);

    ProcessId process_id;
    {
      // Find a process id that's available,
      ODBTransaction t = sessions_cache.getODBTransaction(process_path);
      ODBObject root_ob = t.getNamedItem("root");

      int process_populate_count = 0;
      int checked_count = 0;
      String process_id_string;
      boolean commit_needed = false;

      Set<ProcessServiceAddress> dont_populate_machines = null;

      while (true) {

        // The list of available ids,
        ODBList available_ids = root_ob.getList("available_processIdx");
        // If it's empty, we need to add a bunch of ids to the list immediately,
        if (checked_count > 8 || available_ids.isEmpty()) {
          ++process_populate_count;
          checked_count = 0;
          commit_needed = true;
          populateWithIds(process_path, t, dont_populate_machines);
          available_ids = root_ob.getList("available_processIdx");
        }

        // Pick a random id from the available list,
        int av_sz = (int) available_ids.size();
        int id_index = rng.nextInt(av_sz);
        ODBObject process_ob = available_ids.getObject(id_index);

        // Get the machine,
        ProcessServiceAddress machine =
              createAddressFromMachineString(process_ob.getString("machine"));
        // Is this machine currently failed?
        if (isCurrentConnectKnownFailed(machine)) {
          // Ok, so try a different machine,
          // Add this machine to the set of machine we don't populate on,
          if (dont_populate_machines == null) {
            dont_populate_machines = new HashSet<>();
          }
          dont_populate_machines.add(machine);
        }
        else {
          // Try and allocate the process_id on this machine,
          process_id_string = process_ob.getString("id");
          process_id = ProcessId.fromString(process_id_string);
          // Send an 'init process' message,
          try {
            // Create the function message,
            PMessage fun_msg = createInitProcessMessage(
                         process_id, account_name, webapp_name, process_class);
            // Send the message and block until reply,
            PMessage reply = sendMessage(machine, fun_msg);

            if (reply.isFailMessage()) {
              // If it's a failure, we try with a different id,

//              ProcessFunctionError error = reply.messageFailError();
//              if (error.getErrorType().startsWith("UNAVAILABLE")) {
//                throw new ProcessUnavailableException(
//                        "Process Unavailable",
//                        ProcessUnavailableException.Reason.UNAVAILABLE,
//                        machine, error);
//              }
            }
            // If it's success, we break
            else if (reply.isSuccessMessage()) {
              break;
            }
          }
          catch (InterruptedException e) {
            // Ignore
          }
        }

        // Failed, so repeat
        ++checked_count;

        // Otherwise, if global checked count is over some threshold then
        // we fail with an exception.
        if (process_populate_count > 4) {

          throw new ProcessUnavailableException(
                        "Unable to allocate a Process ID",
                        ProcessUnavailableException.Reason.UNAVAILABLE,
                        machine);

        }

      }

      // Ok, we've successfully allocated the process id in 'process_id_string'

      // Commit the transaction if necessary,
      if (commit_needed) {
        try {
          t.commit();
        }
        catch (CommitFaultException e) {
          // A commit fault exception would most likely indicate a rollback
          // occurred. A very unlikely situation would be a process_id was
          // generated with the same id (statistically this should be impossible).
          // Either case, will need to rethrow,
          throw new PRuntimeException(e);
        }
      }
    }

    // Ok, now we have a process_id for the instance and we've successfully
    // communicated with the process service and initialized it.

    // Return the process_id,
    return process_id;
    
  }

  /**
   * Invokes the function with the given process id provided the process
   * owner matches the account name given. The process owner is verified by
   * either parsing the process record, or querying the process server before
   * invoking the function
   * 
   * @param account_name
   * @param contextifier
   * @param process_id
   * @param msg
   * @param reply_expected
   * @return
   */
  public ProcessResult invokeFunction(
               String account_name, ContextBuilder contextifier,
               ProcessId process_id, ProcessMessage msg,
               boolean reply_expected) {

    int call_id = generateCallId();

    // Find out information about the process,
    ProcessInfoImpl process_info;
    try {
      process_info = getProcessInfo(process_id);
    }
    catch (ProcessUnavailableException e) {
      // If the process is currently unavailable,
      // fail by returning a ProcessResultImpl exception,
      ProcessResultImpl process_ret;
      // Create the reply object,
      process_ret = new ProcessResultImpl(
              thread_pool, account_name, contextifier,
              e.getLocation(), call_id, process_id);
      process_ret.setPMessage(
            ProcessServerService.failMessage(process_id, call_id,
                                             "UNAVAILABLE", e));
      return process_ret;
    }

    // If the account name doesn't match,
    if (!account_name.equals(
                      process_info.getAccountApplication().getAccountName())) {
      throw new InvalidProcessException("Not owned by account");
    }

    // Pick a random call_id,
    byte command_code = reply_expected ?
                          CommConstants.CALL_RET_EXPECTED_CC :
                          CommConstants.CALL_RET_NOT_EXPECTED_CC;
    // NOTE: Command code is 2 or 3 depending on whether reply is expected or
    //   not
    byte[] header =
          ProcessServerService.createHeader(process_id, call_id, command_code);

    PMessage fun_msg;

    // Create the function message,
    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream(20 + msg.size());
      // Write the header,
      bout.write(header);
      // Writer the message body,
      msg.writeTo(bout);
      // Return it as a PMessage,
      byte[] b = bout.toByteArray();
      fun_msg = new PMessage(b);
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }

    // Get the machine,
    ProcessServiceAddress machine = process_info.getMachine();

    // Add to the list of process result notifiers,
    ProcessResultImpl process_result = null;
    if (reply_expected) {
      // Create the reply object,
      process_result = new ProcessResultImpl(thread_pool,
                    account_name, contextifier, machine, call_id, process_id);
      synchronized (process_result_list) {
        process_result_list.add(process_result);
      }
    }

    // Put the message to send on the out-going message queue,
    putMessageOnOutput(new QueueMessage(machine, fun_msg));

    // Return the result,
    return process_result;

  }

  /**
   * 
   * @param account_name
   * @param process_id
   * @param signal
   */
  public void sendSignal(String account_name,
                         ProcessId process_id, String[] signal) {

    // Null pointer exception,
    if (signal == null) throw new NullPointerException();

    int call_id = generateCallId();

    // Find out information about the process,
    ProcessInfoImpl process_info;
    try {
      process_info = getProcessInfo(process_id);
    }
    catch (ProcessUnavailableException e) {
      // If the process is unavailable, do nothing (signals are never
      // guarenteed)
      // PENDING: Should we convey this failure to the user in some way?
      return;
    }

    // If the account name doesn't match,
    if (!account_name.equals(
                      process_info.getAccountApplication().getAccountName())) {
      throw new InvalidProcessException("Not owned by account");
    }

    // Pick a random call_id,
    byte command_code = CommConstants.SEND_SIGNAL;
    byte[] header =
          ProcessServerService.createHeader(process_id, call_id, command_code);

    // Encode the signal,
    Object[] out_args = signal;
    PMessage fun_msg = PMessage.encodeArgsList(header, out_args);

    // Get the machine,
    ProcessServiceAddress machine = process_info.getMachine();

    // Put the message to send on the out-going message queue,
    putMessageOnOutput(new QueueMessage(machine, fun_msg));

  }

  /**
   * 
   * @param account_name
   * @param contextifier
   * @param session_state
   * @return 
   * @throws com.mckoi.process.ProcessUnavailableException 
   */
  public ChannelConsumer getChannelConsumer(
                      String account_name, ContextBuilder contextifier,
                      ChannelSessionState session_state)
                                          throws ProcessUnavailableException {

    // Creates a ChannelConsumerImpl object from the session state string,
    ChannelConsumerImpl consumer = ChannelConsumerImpl.fromSessionState(
                              this, account_name, contextifier, session_state);

    // Check the process is owned by the account,
    checkAccountOwnership(account_name, consumer.getProcessChannel());

    // Ensure this client is receiving broadcast messages from the channel,
    ensureReceivingBroadcast(consumer.getProcessChannel());

    return consumer;
    
  }

  /**
   * 
   * @param account_name
   * @param contextifier
   * @param process_channel
   * @return 
   * @throws com.mckoi.process.ProcessUnavailableException 
   */
  public ChannelConsumer getChannelConsumer(
                  String account_name, ContextBuilder contextifier,
                  ProcessChannel process_channel)
                                          throws ProcessUnavailableException {

    ChannelConsumerImpl consumer = new ChannelConsumerImpl(this,
                                  account_name, contextifier, process_channel);

    // Check the process is owned by the account,
    checkAccountOwnership(account_name, process_channel);

    // Ensure this client is receiving broadcast messages from the channel,
    ensureReceivingBroadcast(process_channel);

    return consumer;

  }

  /**
   * 
   * @param account_name
   * @param contextifier
   * @param process_channel
   * @param sequence_value
   * @return 
   * @throws com.mckoi.process.ProcessUnavailableException 
   */
  public ChannelConsumer getChannelConsumer(
                  String account_name, ContextBuilder contextifier,
                  ProcessChannel process_channel, long sequence_value)
                                          throws ProcessUnavailableException {
    
    ChannelConsumerImpl consumer = new ChannelConsumerImpl(this,
                  account_name, contextifier, process_channel, sequence_value);

    // Check the process is owned by the account,
    checkAccountOwnership(account_name, process_channel);

    // Ensure this client is receiving broadcast messages from the channel,
    ensureReceivingBroadcast(process_channel);

    return consumer;

  }

  /**
   * 
   * @param account_name
   * @param contextifier
   * @param query
   * @return 
   */
  public ProcessResult invokeServersQuery(
        String account_name, ContextBuilder contextifier, ServersQuery query) {

    // Get the query arguments,
    Object[] args = query.getQueryArgs();

    // The command,
    String cmd = (String) args[0];

    // Pick a random call_id,
    final int call_id = generateCallId();

    // The behaviour here changes depending on the type of query,
    if (query.isLocal()) {

      String json_result;

      // All process servers,
      if (cmd.equals("all_process_srvs")) {
        List<ProcessServiceAddress> all_process_machines =
                                                      getAllProcessMachines();
        try {
          JSONObject empty_ob = new JSONObject();
          JSONObject json_ob = new JSONObject();
          for (ProcessServiceAddress machine : all_process_machines) {
            json_ob.put(machine.getMachineAddress(), empty_ob);
          }
          json_result = json_ob.toString();
        }
        catch (JSONException e) {
          throw new PRuntimeException(e);
        }
      }
      else {
        throw new PRuntimeException("Unknown local command: " + cmd);
      }

      // Turn it into a ProcessResult and return it,
      ProcessResultImpl process_ret;
      // Create the reply object,
      process_ret = new ProcessResultImpl(
                thread_pool, account_name, contextifier, null, call_id, null);

      byte[] header = ProcessServerService.createSuccessHeader(
                              null, call_id, CommConstants.CALL_REPLY_CC);
      PMessage msg =
                PMessage.encodeArgsList(header, new Object[] { json_result });
      process_ret.setPMessage(msg);

      return process_ret;

    }
    // Remote call,
    else {

      // Query all the process servers and make a summary object,

      // Pick a random call_id,
      byte command_code = CommConstants.SERVICE_QUERY;
      byte[] header =
                ProcessServerService.createHeader(null, call_id, command_code);

      // Create a PMessage that in the input args with the account name prepended
      // to.

      Object[] out_args = new Object[args.length + 1];
      System.arraycopy(args, 0, out_args, 1, args.length);
      out_args[0] = account_name;
      PMessage fun_msg = PMessage.encodeArgsList(header, out_args);

      // Get the list of all machines to query,
      List<ProcessServiceAddress> process_machine_to_query;
      if (cmd.equals("close_pid")) {
        // If it's 'close_pid' then we only target the relevant machine that
        // is hosting the process.
        ProcessId process_id = ProcessId.fromString((String) args[1]);
        ProcessServiceAddress process_machine = getMachineNameFor(process_id);
        process_machine_to_query = Collections.singletonList(process_machine);
      }
      else {
        process_machine_to_query = getAllProcessMachines();
      }
      List<ProcessResult> results =
              new ArrayList<>(process_machine_to_query.size());

      for (ProcessServiceAddress machine : process_machine_to_query) {

        // Add to the list of process result notifiers,
        ProcessResultImpl process_ret;
        // Create the reply object,
        process_ret = new ProcessResultImpl(
              thread_pool, account_name, contextifier, machine, call_id, null);
        // If we know the machine failed,
        if (isCurrentConnectKnownFailed(machine)) {
          PMessage fail_msg = ProcessServerService.failMessage(
                        null, call_id, "UNAVAILABLE", new PTimeoutException());
          process_ret.setPMessage(fail_msg);
        }
        else {
          synchronized (process_result_list) {
            process_result_list.add(process_ret);
          }
        }

        // Put the message to send on the out-going message queue,
        putMessageOnOutput(new QueueMessage(machine, fun_msg));

        // Add to the list of returned obs,
        results.add(process_ret);

      }

      // Return the composite command ProcessResult
      return new ServersQueryProcessResult(
                        generateCallId(), process_machine_to_query, results);

    }

  }





  
  
  // ----- Inner classes -----

  /**
   * ServersQueryProcessResult implementation.
   */
  static class ServersQueryProcessResult extends CompositeProcessResult {

    private final List<ProcessServiceAddress> machines;

    public ServersQueryProcessResult(int call_id,
          List<ProcessServiceAddress> machines, List<ProcessResult> results) {
      super(call_id, results);
      this.machines = machines;
    }

    /**
     * Convert the results into a process message.
     */
    @Override
    protected ProcessInputMessage formatAsProcessMessage(
                                          List<ProcessInputMessage> results) {

      StringBuilder json_out = new StringBuilder();
      json_out.append("{");
      int sz = results.size();
      boolean first = true;
      for (int i = 0; i < sz; ++i) {
        if (!first) {
          json_out.append(",");
        }
        ProcessInputMessage msg = results.get(i);
        String json_str;
        ProcessInputMessage.Type type = msg.getType();
        // Handle normal return or exception return,
        if (type == ProcessInputMessage.Type.RETURN) {
          ProcessMessage process_msg = msg.getMessage();
          Object[] args = ByteArrayProcessMessage.decodeArgsList(process_msg);
          json_str = (String) args[0];
        }
        else if (type == ProcessInputMessage.Type.RETURN_EXCEPTION) {
          json_str = "\"unavailable\"";
        }
        else {
          throw new PRuntimeException("Unexpected type: " + type);
        }
        json_out.append(JSONObject.quote(machines.get(i).getMachineAddress()));
        json_out.append(":");
        json_out.append(json_str);
        first = false;
      }

      json_out.append("}");

      // Turn it into a ProcessMessage and pass it back,
      Object[] args = new Object[] { json_out.toString() };
      ProcessMessage process_msg = ByteArrayProcessMessage.encodeArgs(args);

      return new PSuccessInputMessageImpl(getCallId(), process_msg);
    }

  }

  /**
   * A ProcessInputMessage implementation of ProcessResultImpl.
   */
  static class PSuccessInputMessageImpl implements ProcessInputMessage {

    private final ProcessMessage message;
    private final int call_id;

    PSuccessInputMessageImpl(int call_id, ProcessMessage message) {
      this.message = message;
      this.call_id = call_id;
    }

    @Override
    public Type getType() {
      return Type.RETURN;
    }

    @Override
    public ProcessMessage getMessage() {
      return message;
    }

    @Override
    public ChannelSessionState getBroadcastSessionState() {
      return null;
    }

    @Override
    public int getCallId() {
      return call_id;
    }

    @Override
    public ProcessFunctionError getError() {
      return null;
    }

  }

  /**
   * A ProcessInputMessage implementation of ProcessResultImpl.
   */
  static class PFailureInputMessageImpl implements ProcessInputMessage {

    private final ProcessFunctionError error;
    private final int call_id;

    PFailureInputMessageImpl(int call_id, ProcessFunctionError error) {
      this.error = error;
      this.call_id = call_id;
    }

    @Override
    public ProcessInputMessage.Type getType() {
      return ProcessInputMessage.Type.RETURN_EXCEPTION;
    }

    @Override
    public ProcessMessage getMessage() {
      return null;
    }

    @Override
    public ChannelSessionState getBroadcastSessionState() {
      return null;
    }

    @Override
    public int getCallId() {
      return call_id;
    }

    @Override
    public ProcessFunctionError getError() {
      return error;
    }

  }

  /**
   * ProcessResult implementation.
   */
  static class ProcessResultImpl implements ProcessResult {

    private final ExecutorService thread_pool;
    // The account that invoked the function that this is the result of,
    private final String invoker_account_name;
    private final ContextBuilder contextifier;

    private final ProcessServiceAddress machine;
    private final int call_id;
    private final ProcessId process_id;

    private final ArrayList<ProcessResultNotifier> notifiers =
                                                        new ArrayList<>(2);
    private PMessage reply = null;

    private final AtomicBoolean notified = new AtomicBoolean(false);

    private ProcessResultImpl(ExecutorService thread_pool,
            String account_name, ContextBuilder contextifier,
            ProcessServiceAddress machine, int call_id, ProcessId process_id) {

      this.thread_pool = thread_pool;
      this.invoker_account_name = account_name;
      this.contextifier = contextifier;

      this.machine = machine;
      this.call_id = call_id;
      this.process_id = process_id;
    }

    public void setPMessage(PMessage msg) {
      synchronized (notifiers) {
        reply = msg;
        notifiers.notifyAll();
      }
    }

    private ProcessInputMessage replyAsProcessMessage(PMessage pmsg) {
      // Return with either a success or failure implementation of
      // ProcessInputMessage
      if (pmsg.isFailMessage()) {
        // Failure will always cause an exception in this case,
        return new PFailureInputMessageImpl(
                                        call_id, pmsg.messageFailError());
      }
      else if (pmsg.isSuccessMessage()) {
        // Return as process message (skip the header),
        ProcessMessage msg = pmsg.asProcessMessage(22);
        return new PSuccessInputMessageImpl(call_id, msg);
      }
      else {
        throw new PRuntimeException("Error in format of reply message");
      }
    }

    @Override
    public ProcessInputMessage getResult(final ProcessResultNotifier notifier) {
      PMessage pmsg;

      // Handle the case when notifier is null,
      if (notifier == null) {
        synchronized (notifiers) {
          pmsg = reply;
        }
      }
      // When there is a notifier,
      else {
        // Lock against the notifier,
        notifier.lock();
        try {
          synchronized (notifiers) {
            if (reply == null) {
              notifiers.add(notifier);
            }
            pmsg = reply;
          }

          if (pmsg == null) {
            notifier.init(ProcessResultNotifier.NOOP_CLEANUP_HANDLER);
          }
        }
        finally {
          notifier.unlock();
        }
      }

      // If no reply, return null,
      if (pmsg == null) {
        return null;
      }
      // Otherwise wrap as a process message,
      return replyAsProcessMessage(pmsg);
    }

    @Override
    public ProcessInputMessage getResult() {
      return getResult(null);
    }

    // Notifies when result comes in,
    private void notifyResult(final Status status) {
      // If there's something to notify then dispatch it,
      List<ProcessResultNotifier> to_notify;
      synchronized (notifiers) {
        to_notify = new ArrayList<>(notifiers.size());
        to_notify.addAll(notifiers);
      }
      // Dispatch to the thread pool,
      final List<ProcessResultNotifier> set = to_notify;
      thread_pool.submit(new Runnable() {
        @Override
        public void run() {

          // Only allow the first notification to happen.
          if (!notified.compareAndSet(false, true)) {
            return;
          }
          
          for (ProcessResultNotifier n : set) {
            contextifier.enterContext();
            try {
              n.lock();
              try {
                n.notifyMessages(status);
              }
              catch (RuntimeException e) {
                // Oops,
                // 'notifyMessages' will potentially be calling into
                // user-code here. We log this to the account's log system if
                // the context was defined.
                if (PlatformContextImpl.hasThreadContextDefined()) {
                  LoggerService logger =
                                    PlatformContextImpl.getCurrentThreadLogger();
                  logger.secureLog(Level.SEVERE, "process",
                          "Exception during message notification\n{0}",
                          LogUtils.stringStackTrace(e));
                }
                else {
                  LOG.log(Level.SEVERE,
                          "Exception during message notification", e);
                  e.printStackTrace(System.err);
                }
              }
              finally {
                n.unlock();
              }
            }
            finally {
              contextifier.exitContext();
            }
          }
        }
      });
    }

    @Override
    public ProcessInputMessage blockUntilResult(long timeout)
                         throws ResultTimeoutException, InterruptedException {

      if (timeout < 0) {
        throw new IllegalArgumentException("timeout < 0");
      }

      PMessage pmsg;
      synchronized (notifiers) {
        if (reply == null) {
          notifiers.wait(timeout);
        }
        pmsg = reply;
        // If no reply, then assume timeout
        if (pmsg == null) {
          throw new ResultTimeoutException();
        }
      }
      return replyAsProcessMessage(pmsg);
    }

    @Override
    public int getCallId() {
      return call_id;
    }

    /**
     * Returns true if this process result is based on the given machine
     * address.
     * 
     * @param machine_addr
     * @return 
     */
    private boolean isFromRemote(ProcessServiceAddress machine_addr) {
      return machine.equals(machine_addr);
    }

    /**
     * Returns true if this ProcessResult matches the given query message.
     */
    private boolean matches(QueueMessage msg, ProcessServiceAddress machine) {
      boolean result;
      // This is a server query,
      if (this.process_id == null) {
        result = ( msg.matchesCallId(this.call_id) &&
                   machine.equals(this.machine) );
      }
      else {
        result = ( msg.matches(this.process_id, this.call_id) &&
                   machine.equals(this.machine) );
      }
      return result;
    }

  }

  /**
   * The ProcessClient implementation.
   */
  private class PCSProcessClient implements ProcessClient {

    private final String account_name;

    private PCSProcessClient(String account_name) {
      this.account_name = account_name;
    }

    String getAccountName() {
      return account_name;
    }

    @Override
    public ProcessId createProcess(String webapp_name, String process_class)
                                          throws ProcessUnavailableException {
      return ProcessClientService.this.createProcess(
              getAccountName(), webapp_name, process_class);
    }

    @Override
    public ProcessInfo getProcessInfo(ProcessId process_id)
                                          throws ProcessUnavailableException {
      return ProcessClientService.this.getProcessInfo(
              getAccountName(), process_id);
    }

    @Override
    public void sendSignal(ProcessId process_id, String[] signal) {
      ProcessClientService.this.sendSignal(
              getAccountName(), process_id, signal);
    }

  }

  /**
   * AppServiceProcessClient implementation.
   */
  private class PCSAppServiceProcessClient extends PCSProcessClient
                                          implements AppServiceProcessClient {

    private final ContextBuilder contextifier;

    private PCSAppServiceProcessClient(
                            String account_name, ContextBuilder contextifier) {
      super(account_name);
      this.contextifier = contextifier;
    }

    @Override
    public ProcessResult invokeFunction(
            ProcessId process_id, ProcessMessage msg, boolean reply_expected) {
      return ProcessClientService.this.invokeFunction(
            getAccountName(), contextifier, process_id, msg, reply_expected);
    }

    @Override
    public ChannelConsumer getChannelConsumer(ChannelSessionState session_state)
                                          throws ProcessUnavailableException {
      return ProcessClientService.this.getChannelConsumer(
            getAccountName(), contextifier, session_state);
    }

    @Override
    public ChannelConsumer getChannelConsumer(ProcessChannel process_channel)
                                          throws ProcessUnavailableException {
      return ProcessClientService.this.getChannelConsumer(
            getAccountName(), contextifier, process_channel);
    }

    @Override
    public ChannelConsumer getChannelConsumer(
                ProcessChannel process_channel, long sequence_value)
                                          throws ProcessUnavailableException {
      return ProcessClientService.this.getChannelConsumer(
            getAccountName(), contextifier, process_channel, sequence_value);
    }

    // ------

    @Override
    public ProcessResult invokeServersQuery(ServersQuery query) {
      return ProcessClientService.this.invokeServersQuery(
            getAccountName(), contextifier, query);
    }

  }

  /**
   * ProcessEnvironment implementation.
   */
  private class PCSProcessEnvironment implements PEnvironment {

    PCSProcessEnvironment() {
    }

    @Override
    public ExecutorService getThreadPool() {
      return thread_pool;
    }

    @Override
    public boolean channelConnectionValid(SocketChannel sc) {
      // Should never be used in this implementation,
      throw new UnsupportedOperationException();
    }

    @Override
    public void initializeConnection(NIOConnection connection) {
      // Should never be used in this implementation,
      throw new UnsupportedOperationException();
    }

    @Override
    public void connectionClosed(NIOConnection connection) {
      LOG.log(Level.WARNING, "Connection Closed - dispatching to notifiers");
      ProcessServiceAddress machine_addr = connection.getStateMachineName();
      if (machine_addr != null) {
        LOG.log(Level.WARNING, "Resetting connection");
        synchronized (connections) {
          ProcessClientConnection pcc = connections.get(machine_addr);
          if (pcc != null) {
            pcc.resetConnection();
          }
        }
        // Fail all notifiers with an IO_ERROR status event.
        failNotifierOnMachineAddress(machine_addr);
      }
    }

    @Override
    public void handleMessages(NIOConnection connection) {
      // Consume all the messages from the queue,
      Collection<PMessage> messages = connection.consumeAllFromQueue();
      // Machine name,
      ProcessServiceAddress machine_addr = connection.getStateMachineName();
      // Add all the messages to the input queue,
      putAllMessagesOnInput(machine_addr, messages);
    }

  }

  /**
   * Returns a human parsable exception message.
   */
  private static String exceptionMessage(Throwable e) {
    String ex_message = e.getMessage();
    String clazz_name = e.getClass().getName();
    if (ex_message == null) {
      return clazz_name;
    }
    StringBuilder b = new StringBuilder();
    b.append(clazz_name);
    b.append(": ");
    b.append(ex_message);
    return b.toString();
  }

  /**
   * A process client connection object represents a connection to a process
   * server.
   */
  private class ProcessClientConnection {

    /**
     * The machine name this is connected to.
     */
    private final ProcessServiceAddress machine_addr;

    /**
     * The messages pending to be sent to this connection.
     */
    private final QueueList queue;

    /**
     * The NIO connection.
     */
    private NIOConnection nio_connection;

    /**
     * The time the connection was established.
     */
    private volatile MonotonicTime time_connected = null;

    /**
     * A fail check time, or null if connection is established.
     */
    private volatile MonotonicTime fail_checkpoint = null;

    private final AtomicInteger flush_lock = new AtomicInteger(0);

    ProcessClientConnection(ProcessServiceAddress machine_name) {
      if (machine_name == null) throw new NullPointerException();
      this.machine_addr = machine_name;
      this.queue = new QueueList();
    }

    private void putAllMessagesInQueue(QueueMessage msg) {
      synchronized (queue) {
        while (msg != null) {
          QueueMessage next_msg = msg.getNext();
          queue.add(msg);
          msg = next_msg;
        }
      }
    }

    private boolean isQueueEmpty() {
      synchronized (queue) {
        return queue.isEmpty();
      }
    }

    /**
     * Returns the time this connection was last established.
     */
    MonotonicTime getLastConnectedTime() {
      return time_connected;
    }

    /**
     * Resets this connection clearing all the broadcast listeners and
     * forces handshake with service.
     */
    private void resetConnection() {
      time_connected = null;
      nio_connection = null;
    }

    /**
     * Returns true if this connection is known to be unavailable. This means
     * it returns true if a connection was tried but failed. Returns false 
     * if either no connection attempt has been made yet or the connection is
     * currently available.
     */
    private boolean isKnownFailed() {
      return fail_checkpoint != null;
    }

    /**
     * Attempts to establish this connection. Returns true if the connection
     * was successfully established, or false otherwise. Note that it is
     * possible for this to return false positive (returns true but no
     * connection is established).
     */
    private boolean attemptEstablishConnection() {
      flushPendingMessages();
      return (fail_checkpoint == null);
    }

    /**
     * Fail all messages currently on the input queue. This would typically
     * happen because of a connection failure.
     */
    private void failAllMessages() {

      QueueList to_fail = new QueueList();
      int fail_count = 0;

      // We only create a failure message for certain types of calls,
      // specifically;
      //
      //   CommConstants.PROCESS_QUERY
      //   CommConstants.SEND_SIGNAL
      //   CommConstants.SERVICE_QUERY
      //   CommConstants.CALL_RET_EXPECTED_CC
      //   CommConstants.FUNCTION_INIT_CC
      //
      // These types of functions all provide a 'call_id' therefore we
      // can convey the failure back to the callee.
      //
      // Anything else we leave on the queue so it'll be resent next time a
      // connection is established.

      // Remove all timed out messages from the queue,
      synchronized (queue) {

        QueueMessage msg = queue.getFirst();
        while (msg != null) {
          QueueMessage next_msg = msg.getNext();

          // The command code,
          PMessage call_msg = msg.getMessage();
          byte command_code = call_msg.getCommandCode();
          // Only remove from the queue the types of messages we can
          // immediately respond a failure status for,
          if (command_code == CommConstants.PROCESS_QUERY ||
              command_code == CommConstants.SEND_SIGNAL ||
              command_code == CommConstants.SERVICE_QUERY ||
              command_code == CommConstants.CALL_RET_EXPECTED_CC ||
              command_code == CommConstants.FUNCTION_INIT_CC) {

            // Remove,
            queue.remove(msg);
            to_fail.add(msg);
            ++fail_count;

          }

          // Next message,
          msg = next_msg;
        }

      }

      List<PMessage> fail_messages = new ArrayList<>(Math.max(fail_count, 8));

      // For each timed out message,
      QueueMessage msg = to_fail.getFirst();
      while (msg != null) {
        QueueMessage next_msg = msg.getNext();

        // The call_id,
        PMessage call_msg = msg.getMessage();
        int call_id = call_msg.getCallId();
        // The process_id,
        ProcessId process_id = call_msg.getProcessId();

        // Make a failure message,
        PMessage fail_msg = ProcessServerService.failMessage(
                      process_id, call_id, "UNAVAILABLE",
                      new PTimeoutException());

        // Add to the list,
        fail_messages.add(fail_msg);

        // Go to next message,
        msg = next_msg;
      }

      // If there are fail messages,
      if (!fail_messages.isEmpty()) {
        // Put them on the input queue,
        putAllMessagesOnInput(machine_addr, fail_messages);
      }

    }

    /**
     * Signifies there are messages that need to be flushed to the machine.
     * This will establish a connection if necessary.
     */
    void flushPendingMessages() {
      // Return if there is another thread currently flushing messages on this
      // connection,
      if (flush_lock.getAndIncrement() > 0) {
        return;
      }
      while (true) {
        flush_lock.set(1);

        // NOTE, we know only one thread will ever be in here at the same time,

        try {

queue_empty_loop:
          while (!isQueueEmpty() || fail_checkpoint != null) {

            // Are we in fail immediately mode? If a connection failed to
            // establish within the last 5 seconds then yes,
            MonotonicTime fail_ch_value = fail_checkpoint;
            if ( fail_ch_value != null &&
                 MonotonicTime.millisSince(fail_ch_value) < 5000 ) {
              // If we failed to connect, empty the queue of all messages
              // and put failure messages on the output queue.
              failAllMessages();
              break queue_empty_loop;
            }

            // Establish a connection if necessary,
            while (nio_connection == null) {
              try {

                LOG.log(Level.INFO,
                        "Attempting to connect to: {0}", machine_addr);

                // The address to connect to,
                InetAddress addr =
                      InetAddress.getByName(machine_addr.getMachineAddress());
                
                // If InetAddress is IPv6 then set its scope,
                if (addr instanceof Inet6Address) {
                  Inet6Address ipv6_addr = (Inet6Address) addr;
                  // The current scope,
                  NetworkInterface current_interface = ipv6_addr.getScopedInterface();
                  // If the current scope is not set then append the current
                  // network interface assignment to it.
                  if (current_interface == null) {
                    addr = Inet6Address.getByAddress(
                                  null, addr.getAddress(), network_interface);
                  }
                  // If it has a scope then check it's the same scope as our
                  // network interface.
                  else if (!current_interface.equals(network_interface)) {
                    String err_msg = MessageFormat.format(
                        "Machine address has a scope that does not match " +
                        "assigned network interface: {0}", addr);
                    LOG.log(Level.SEVERE, err_msg);
                    throw new IOException(err_msg);
                  }
                }
                
                int port = machine_addr.getMachinePort();

                // Connect to the process server,
                SocketChannel channel = SocketChannel.open();
                channel.connect(new InetSocketAddress(addr, port));
                Socket socket = channel.socket();
                socket.setTcpNoDelay(true);
                channel.configureBlocking(false);

                // Create the NIO connection to the process,
                nio_connection =
                        new NIOConnection(selector, process_env, channel,
                                          nio_write_selector);
                // Initial connection state is '10',
                nio_connection.setStateLong((long) 10);
                nio_connection.setStateMachineName(machine_addr);

                // Register reads,
                int ops = SelectionKey.OP_READ;
                // This bit of locking mastery allows us to push the selector out
                // of its internal selector lock allowing the connection to be
                // registered with it.
                selector_lock.lock();
                try {
                  selector.wakeup();
                  SelectionKey sel_key =
                               channel.register(selector, ops, nio_connection);
                  nio_connection.setSelectionKey(sel_key);
                }
                finally {
                  selector_lock.unlock();
                }

                // Wait until we receive the initialization message for this
                // connection,
                try {
                  QueueMessage init_msg = blockUntilConnectionInit(machine_addr);
                  // Send the same message as a reply,
                  nio_connection.sendFirstMessage(init_msg.getMessage());
                  nio_connection.flushSendMessages();
                  nio_connection.setStateLong((long) 0);

                  MonotonicTime time_now = MonotonicTime.now();
                  time_connected = time_now;
                  fail_checkpoint = null;

                  LOG.log(Level.INFO,
                          "Connection success: {0}", machine_addr);

                }
                catch (InterruptedException ex) { /* ignore */ }

              }
              catch (IOException e) {
                LOG.log(Level.SEVERE, "Connection on {0} failed: {1}",
                        new Object[] { machine_addr, exceptionMessage(e) });
                resetConnection();

                // Set timestamp
                MonotonicTime mono_ts = MonotonicTime.now();
                fail_checkpoint = mono_ts;

                // If we failed to connect, empty the queue of all messages
                // and put failure messages on the output queue.
                failAllMessages();

// [NOTE: The behaviour below will periodically poll the server until the
//        message times out rather than failing immediately on exception]
//                // If we fail to connect, look at the messages in the queue
//                // and fail any messages that we haven't sent out within
//                // a timeout period.
//                failTimedOutMessages();

                // Break if the queue is empty,
                if (isQueueEmpty()) {
                  break queue_empty_loop;
                }

                // Wait 8 seconds until we try to connect again
                try {
                  Thread.sleep(8000);
                }
                catch (InterruptedException e2) { /* ignore */ }
              }

            } // while (nio_connection == null)

            // Write all the pending messages,
            QueueMessage msg;
            QueueMessage not_flushed_msg;
            synchronized (queue) {
              msg = queue.getFirst();
              not_flushed_msg = msg;
              queue.clear();
            }

            // If there are messages to flush on the connection,
            if (msg != null) {
              try {
                // Send messages if connection initialized,
                if (nio_connection != null &&
                    nio_connection.getStateLong() == 0) {
                  while (msg != null) {
                    boolean performed_flush =
                            nio_connection.sendFirstMessage(msg.getMessage());
                    msg = msg.getNext();
                    if (performed_flush) {
                      not_flushed_msg = msg;
                    }
                  }
                  nio_connection.flushSendMessages();
                }
                else {
                  // Couldn't send the message because connection state isn't 0, so
                  // put back on the queue,
                  putAllMessagesInQueue(msg);
                }
              }
              catch (IOException e) {
                // Add all the messages we didn't send back into the queue,
                putAllMessagesInQueue(not_flushed_msg);
                // If we failed to send a message, reset 'nio_connection' and
                // repeat until we send all the pending messages,
                LOG.log(Level.SEVERE,
                        "Send message to {0} failed: {1}",
                        new Object[] { machine_addr, exceptionMessage(e) });
                resetConnection();
              }
            }

          } // while (!isQueueEmpty())

        }
        catch (Throwable e) {
          LOG.log(Level.SEVERE, "Exception while handling pending messages", e);
          e.printStackTrace(System.err);
        }

        // Return if no flushes happened,
        if (flush_lock.compareAndSet(1, 0)) {
          return;
        }
      }

    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ProcessClientConnection)) return false;
      final ProcessClientConnection other = (ProcessClientConnection) obj;
      return machine_addr.equals(other.machine_addr);
    }

    @Override
    public int hashCode() {
      return machine_addr.hashCode();
    }

  }

}
