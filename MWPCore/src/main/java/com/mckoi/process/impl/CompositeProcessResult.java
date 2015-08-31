/**
 * com.mckoi.process.impl.CompositeProcessResult  Nov 25, 2012
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

import com.mckoi.process.ProcessInputMessage;
import com.mckoi.process.ProcessResult;
import com.mckoi.process.ProcessResultNotifier;
import com.mckoi.process.ProcessResultNotifier.Status;
import com.mckoi.process.ResultTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of ProcessResult that will fire any notifiers only when
 * all results are available.
 *
 * @author Tobias Downer
 */

public abstract class CompositeProcessResult implements ProcessResult {

  private final int call_id;
  private final List<ProcessResult> results;

  private final ArrayList<ProcessResultNotifier> notifiers = new ArrayList<>(2);

  private final Object NOTIFIED_LOCK = new Object();
  private boolean notified = false;

  public CompositeProcessResult(int call_id, List<ProcessResult> results) {
    this.call_id = call_id;
    this.results = results;
  }

  
  /**
   * Either returns a list of all the ProcessMessage objects representing the
   * result of all the queries, or returns null and the given notifier will
   * be called when all the results are available.
   * 
   * @param notifier
   * @return 
   */
  protected List<ProcessInputMessage> getAllResults(
                                        final ProcessResultNotifier notifier) {

    // Handle null case,
    if (notifier == null) {
      return getAllResults();
    }

    // Notifier that is triggered only when all results are available,
    final CompositeNotifier composite_notifier = new CompositeNotifier();

    List<ProcessInputMessage> out = new ArrayList<>(results.size());
    
    notifier.lock();
    try {
    
      synchronized (notifiers) {
        // Call 'getResult' on every result item,
        for (ProcessResult result : results) {
          ProcessInputMessage result_msg = result.getResult(composite_notifier);
          if (out != null && result_msg != null) {
            out.add(result_msg);
          }
          else {
            out = null;
          }
        }

        // Set notifier if necessary,
        if (out == null) {
          notifiers.add(notifier);
        }
      }

      if (out == null) {
        notifier.init(new ProcessResultNotifier.CleanupHandler() {
          @Override
          public void detach() {
            synchronized (notifiers) {
              notifiers.remove(notifier);
              composite_notifier.cleanup();
            }
          }
        });
      }

      return out;

    }
    finally {
      notifier.unlock();
    }
  }

  /**
   * Returns a list of all the ProcessMessage objects representing the result
   * of all the queries, or returns null if the result is not currently
   * available.
   * 
   * @return 
   */
  protected List<ProcessInputMessage> getAllResults() {
    final List<ProcessInputMessage> out = new ArrayList<>(results.size());
    // Call 'getResult' on every result item,
    for (ProcessResult result : results) {
      ProcessInputMessage result_msg = result.getResult();
      if (result_msg != null) {
        out.add(result_msg);
      }
      else {
        return null;
      }
    }
    return out;
  }

  // Notifies when result comes in,
  private void notifyResult(Status status) {
    // Make sure to notify only once,
    synchronized (NOTIFIED_LOCK) {
      if (notified) {
        return;
      }
      notified = true;
    }
    // If there's something to notify then dispatch it,
    List<ProcessResultNotifier> to_notify;
    synchronized (notifiers) {
      to_notify = new ArrayList<>(notifiers.size());
      to_notify.addAll(notifiers);
      // Unblock any waiting threads,
      notifiers.notifyAll();
    }
    // Trigger any notifiers,
    for (ProcessResultNotifier n : to_notify) {
      n.lock();
      try {
        n.notifyMessages(status);
      }
      finally {
        n.unlock();
      }
    }
  }

  /**
   * A notifier that's called back on when a result comes in.
   */
  private class CompositeNotifier extends ProcessResultNotifier {

    private List<CleanupHandler> cleanup_handlers;

    private void cleanup() {
      if (cleanup_handlers != null) {
        for (CleanupHandler h : cleanup_handlers) {
          h.detach();
       } 
      }
    }

    @Override
    public void init(CleanupHandler cleanup_handler) {
      // Remember the cleanup handler and call it during 'cleanup'
      if (!cleanup_handler.equals(NOOP_CLEANUP_HANDLER)) {
        if (cleanup_handlers == null) {
          cleanup_handlers = new ArrayList<>();
        }
        cleanup_handlers.add(cleanup_handler);
      }
    }

    @Override
    public void notifyMessages(Status status) {
      List<ProcessInputMessage> msg = getAllResults();
      if (msg != null) {
        notifyResult(status);
      }
    }

  }

  /**
   * Called when a completed results set is available, and should format into
   * the result ProcessMessage.
   * 
   * @param results
   * @return
   */
  protected abstract ProcessInputMessage formatAsProcessMessage(
                                            List<ProcessInputMessage> results);

  // -----

  @Override
  public ProcessInputMessage blockUntilResult(long timeout)
                          throws ResultTimeoutException, InterruptedException {

    if (timeout < 0) {
      throw new IllegalArgumentException("timeout < 0");
    }

    List<ProcessInputMessage> avail_results;
    synchronized (notifiers) {
      avail_results = getAllResults();
      if (avail_results == null) {
        notifiers.wait(timeout);
      }
      avail_results = getAllResults();
      // If no reply, then assume timeout
      if (avail_results == null) {
        throw new ResultTimeoutException();
      }
    }
    return formatAsProcessMessage(avail_results);

  }

  @Override
  public ProcessInputMessage getResult(ProcessResultNotifier notifier) {
    List<ProcessInputMessage> ret_set = getAllResults(notifier);
    if (ret_set != null) {
      return formatAsProcessMessage(ret_set);
    }
    return null;
  }

  @Override
  public ProcessInputMessage getResult() {
    List<ProcessInputMessage> ret_set = getAllResults();
    if (ret_set != null) {
      return formatAsProcessMessage(ret_set);
    }
    return null;
  }

  @Override
  public int getCallId() {
    return call_id;
  }

}
