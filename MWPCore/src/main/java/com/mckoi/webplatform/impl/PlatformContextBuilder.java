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

import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPClassLoaderSet;
import com.mckoi.process.impl.ProcessClientService;
import com.mckoi.webplatform.jetty.websocket.MckoiWebSocketServerFactory;
import java.io.File;
import java.util.Timer;
import org.eclipse.jetty.server.Server;

/**
 * This is a static object per account per classloader that sets up a
 * platform context given a URI request.
 *
 * @author Tobias Downer
 */

public final class PlatformContextBuilder {

  /**
   * The static permission object needed by callees to create and exit contexts.
   */
  final static Object CONTEXT_GRANT = new Object();
  static {
    JettyMckoiRequestHandler.givePlatformContextGrant(CONTEXT_GRANT);
    MckoiWebSocketServerFactory.givePlatformContextGrant(CONTEXT_GRANT);
  }

  /**
   * The Server object.
   */
  private Server server = null;

  /**
   * The session cache.
   */
  private final DBSessionCache sessions_cache;

  /**
   * The ProcessClientService that supports messaging to process services on
   * the network.
   */
  private final ProcessClientService process_client_service;

  /**
   * The Timer used to schedule timed event (such as flushing logs).
   */
  private final Timer system_timer;

  /**
   * The generally allowed system classes.
   */
  private final ClassNameValidator general_allowed_sys_classes;

  /**
   * The MWP class loaders.
   */
  private final MWPClassLoaderSet classloaders;

  /**
   * The location of the local temporary folder.
   */
  private final File local_temp_folder;

  /**
   * Constructor.
   * 
   * @param sessions_cache
   * @param process_client_service
   * @param system_timer
   * @param general_allowed_sys_classes
   * @param classloaders
   * @param local_temp_folder 
   */
  PlatformContextBuilder(
                  DBSessionCache sessions_cache,
                  ProcessClientService process_client_service,
                  Timer system_timer,
                  ClassNameValidator general_allowed_sys_classes,
                  MWPClassLoaderSet classloaders,
                  File local_temp_folder) {

    this.sessions_cache = sessions_cache;
    this.process_client_service = process_client_service;
    this.system_timer = system_timer;
    this.general_allowed_sys_classes = general_allowed_sys_classes;
    this.classloaders = classloaders;
    this.local_temp_folder = local_temp_folder;

  }

  void setServer(JettyMckoiServer server) {
    if (this.server == null) {
      this.server = server;
    }
  }
  
  DBSessionCache getSessionsCache() {
    return sessions_cache;
  }

  ProcessClientService getProcessClientService() {
    return process_client_service;
  }
  
  Timer getSystemTimer() {
    return system_timer;
  }
  
  ClassNameValidator getClassNameValidator() {
    return general_allowed_sys_classes;
  }
  
  MWPClassLoaderSet getClassLoaderSet() {
    return classloaders;
  }
  
  File getLocalTempDir() {
    return local_temp_folder;
  }

}
