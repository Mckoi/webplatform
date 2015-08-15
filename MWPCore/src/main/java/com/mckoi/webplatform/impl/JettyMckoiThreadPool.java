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

package com.mckoi.webplatform.impl;

import com.mckoi.mwpcore.ContextBuilder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 *
 * @author Tobias Downer
 */

public class JettyMckoiThreadPool extends QueuedThreadPool {

  @Override
  public void execute(Runnable job) {
//    // If this thread was executed within a MWP context then the runnable
//    // inherits the context when executed,
//    
//    if (PlatformContextImpl.hasThreadContextDefined()) {
//      ContextBuilder context_builder =
//                              PlatformContextImpl.getCurrentContextBuilder();
//      System.err.println("Task inherits context;");
//      new Error().printStackTrace(System.err);
//      // Inherit context in this,
//      super.execute(new WrappedJob(job, context_builder));
//    }
//    else {
//      // Otherwise,
//      super.execute(job);
//    }

    super.execute(job);

  }

  // ------

  private static class WrappedJob implements Runnable {

    private final Runnable backed_job;
    private final ContextBuilder context_builder;
    
    private WrappedJob(Runnable job, ContextBuilder context_builder) {
      this.backed_job = job;
      this.context_builder = context_builder;
    }

    @Override
    public void run() {
      context_builder.enterContext();
      try {
        backed_job.run();
      }
      finally {
        context_builder.exitContext();
      }
    }

  }

}
