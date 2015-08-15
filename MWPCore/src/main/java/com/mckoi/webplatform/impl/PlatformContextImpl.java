/**
 * com.mckoi.webplatform.impl.PlatformContextImpl  May 18, 2010
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

package com.mckoi.webplatform.impl;

import com.mckoi.appcore.SystemStatics;
import com.mckoi.mwpcore.ClassNameValidator;
import com.mckoi.mwpcore.ContextBuilder;
import com.mckoi.mwpcore.DBSessionCache;
import com.mckoi.mwpcore.MWPUserClassLoader;
import com.mckoi.network.MckoiDDBAccess;
import com.mckoi.network.NetworkAccess;
import com.mckoi.network.PathNotAvailableException;
import com.mckoi.odb.ODBList;
import com.mckoi.odb.ODBObject;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.AppServiceProcessClient;
import com.mckoi.process.InstanceProcessClient;
import com.mckoi.process.ProcessInstance;
import com.mckoi.process.impl.ProcessClientService;
import com.mckoi.webplatform.*;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Provides secure access to various Mckoi Web Platform related data sources.
 *
 * @author Tobias Downer
 */

public final class PlatformContextImpl implements PlatformContext {

  /**
   * The permission for setting and clearing the thread context.
   */
  private final static MckoiDDBWebPermission
                              PLATFORM_CONTEXT_THREAD_PERMISSION =
             new MckoiDDBWebPermission("platformContextImpl.setThreadContext");

  /**
   * Thread local member representing contextual information about the
   * current runtime.
   */
  private final static ThreadLocal<ThreadContext> thread_context =
                                                          new ThreadLocal<>();

  private static final Class[] make_classes;

  static {
    // To prevent class circularity errors we make sure certain classes are
    // created.
    make_classes = new Class[] {
      JettyMckoiWebAppClassLoader.class
    };
  }


  /**
   * Constructor.
   */
  public PlatformContextImpl() {
  }

  /**
   * Permission check.
   */
  private static void checkSecurity() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkPermission(PLATFORM_CONTEXT_THREAD_PERMISSION);
    }
  }

  /**
   * Returns the thread context for the current thread. If no thread context
   * has been defined then an exception is generated.
   */
  private static ThreadContext getCurrentThreadContext() {
    ThreadContext c = thread_context.get();
    // If null, throw a security exception
    if (c == null) {
      throw new SecurityException("No context defined for thread.");
    }
    return c;
  }

  /**
   * Returns true if this thread already has a context defined for it.
   */
  static boolean hasThreadContextDefined() {
    return thread_context.get() != null;
  }

  /**
   * Creates a thread context for the current thread with the given details.
   */
  private static void setCurrentThreadContext(
            boolean is_app_service_context,
            PlatformContextBuilder context_builder,
            DBSessionCache sessions_cache,
            ProcessClientService process_client_service,
            ProcessInstance process_instance,
            String account_name, String vhost, String protocol,
            String webapp_name,
            LoggerService logger,
            MWPUserClassLoader user_class_loader,
            ClassLoader app_class_loader,
            ClassNameValidator allowed_system_classes) {

    // Permission check (prevent non SYSTEM code sources from doing this)
    checkSecurity();

    ThreadContext c = thread_context.get();
    // If there is already one defined for this thread, generate an error
    if (c != null) {
      throw new SecurityException("Context already defined for thread.");
    }
    // Create the context,
    c = new ThreadContext(is_app_service_context,
                          context_builder,
                          sessions_cache,
                          process_client_service,
                          process_instance,
                          account_name,
                          vhost, protocol,
                          webapp_name,
                          logger,
                          user_class_loader, app_class_loader,
                          allowed_system_classes);
    thread_context.set(c);

  }

  /**
   * Creates a thread context for a process application context, with the
   * given details.
   */
  public static void setCurrentThreadContextForProcessService(
              DBSessionCache session_cache,
              ProcessInstance process_instance,
              String account_name, String vhost, String protocol,
              String webapp_name,
              LoggerService logger,
              MWPUserClassLoader user_class_loader,
              ClassLoader app_class_loader,
              ClassNameValidator allowed_system_classes) {

    setCurrentThreadContext(false, null, session_cache, null,
                            process_instance,
                            account_name, vhost, protocol,
                            webapp_name,
                            logger, user_class_loader, app_class_loader,
                            allowed_system_classes);

  }
  
  /**
   * Creates a thread context for an application server context, with the
   * given details.
   */
  static void setCurrentThreadContextForAppService(
          PlatformContextBuilder context_builder,
          String account_name, String vhost, String protocol) {

    setCurrentThreadContext(
                    true,
                    context_builder,
                    context_builder.getSessionsCache(),
                    context_builder.getProcessClientService(),
                    null,
                    account_name, vhost, protocol,
                    null, null, null, null, null);

  }

//  /**
//   * Creates a thread context for the current thread with the given details.
//   */
//  static void setCurrentThreadContext(
//                         boolean is_app_service_context,
//                         DBSessionCache sessions_cache,
//                         ProcessClientService process_client_service,
//                         ProcessInstance process_instance,
//                         String account_name, String vhost, String protocol) {
//    setCurrentThreadContext(
//                    is_app_service_context,
//                    sessions_cache, process_client_service,
//                    process_instance,
//                    account_name, vhost, protocol,
//                    null, null, null, null, null);
//  }

  /**
   * Sets the LoggerService for the current thread.
   */
  static void setCurrentThreadLogger(LoggerService log_system) {
    getCurrentThreadContext().log_system = log_system;
  }

  /**
   * Sets the user class loader for this context.
   */
  static void setUserClassLoader(MWPUserClassLoader user_class_loader) {
    getCurrentThreadContext().user_class_loader = user_class_loader;
  }
  
  /**
   * Sets the application class loader for this context.
   */
  static void setApplicationClassLoader(ClassLoader app_class_loader) {
    getCurrentThreadContext().app_class_loader = app_class_loader;
  }

  /**
   * Sets the system classes that are allowed to be access to this thread
   * context.
   */
  static void setAllowedSystemClasses(ClassNameValidator system_class_validator) {
    getCurrentThreadContext().system_class_validator = system_class_validator;
  }

  /**
   * Sets the web application name of the current context.
   */
  static void setWebApplicationName(String webapp_name) {
    getCurrentThreadContext().webapp_name = webapp_name;
  }

  /**
   * Returns the web application name of the current thread context or null if
   * no current context.
   */
  static String getCurrentThreadWebApplicationName() {
    ThreadContext c = thread_context.get();
    return (c == null) ? null : c.webapp_name;
  }

  /**
   * Sets the 'log_request' flag, which determines whether this http or https
   * request should be added to the user's "http" log.
   * @param log_request true to enable logging of the request.
   */
  static void setLogThisRequest(boolean log_request) {
    getCurrentThreadContext().log_request = log_request;
  }

  /**
   * Returns the 'log_request' flag for this request.
   */
  static boolean getLogThisRequest() {
    return getCurrentThreadContext().log_request;
  }

  /**
   * Removes the thread context for the current thread. Generates a security
   * exception if removing thread context is not allowed.
   */
  public static void removeCurrentThreadContext() {

    // Permission check (prevent non SYSTEM code sources from doing this)
    checkSecurity();

    ThreadContext c = thread_context.get();
//    // If there is not one defined for this thread, generate an error
//    if (c == null) {
//      throw new SecurityException("No context defined for thread.");
//    }
    // Remove the context,
    thread_context.remove();

  }

  /**
   * Returns the account name of the current thread context or null if no
   * current context.
   */
  static String getCurrentThreadAccountName() {
    ThreadContext c = thread_context.get();
    return (c == null) ? null : c.account_name;
  }

  /**
   * Returns the MWPUserClassLoader currently set for this context.
   * @return 
   */
  public static MWPUserClassLoader getUserClassLoader() {
    return getCurrentThreadContext().user_class_loader;
  }

  /**
   * Returns the system class name validator. NOTE: We expose this under a
   * public interface. Considering the information, not a big deal imo.
   * @return 
   */
  public static ClassNameValidator getAllowedSystemClasses() {
    return getCurrentThreadContext().system_class_validator;
  }

  @Override
  public Version getVersion() {
    // The current version,
    return Version.VER_1;
  }

  /**
   * Returns the account name of the current thread context.
   * @return 
   */
  @Override
  public String getAccountName() {
    String account_name = getCurrentThreadAccountName();
    if (account_name == null) {
      throw new SecurityException("No context defined for thread.");
    }
    return account_name;
  }

  @Override
  public String getWebApplicationName() {
    String webapp_name = getCurrentThreadWebApplicationName();
    if (webapp_name == null) {
      throw new SecurityException("No context defined for thread.");
    }
    return webapp_name;
  }

  /**
   * Returns the 'globalpriv' value set for this account. This is used to
   * determine the privs the account has.
   */
  static String getAccountGlobalPriv() {

    ThreadContext c = getCurrentThreadContext();
    String global_priv = (String) c.getProperty("globalpriv");

    if (global_priv == null) {
      String account_name = c.account_name;
      if (account_name == null) {
        throw new SecurityException("No context defined for thread.");
      }

      // Query the system platform database,
      ODBTransaction system_db =
                 c.sessions_cache.getODBTransaction(SystemStatics.SYSTEM_PATH);
      ODBObject accounts = system_db.getNamedItem("accounts");
      ODBList account_idx = accounts.getList("accountIdx");
      ODBObject account = account_idx.getObject(account_name);
      String json = account.getString("value");
      // If the value isn't set then return 'normal' (the default),
      if (json == null || json.trim().length() == 0) {
        global_priv = "normal";
      }
      else {
        try {
          JSONObject json_ob = new JSONObject(json);
          global_priv = json_ob.getString("globalpriv");
        }
        catch (JSONException e) {
          throw new RuntimeException(e);
        }
      }

      // Set the global priv,
      c.setProperty("globalpriv", global_priv);
    }
    
    return global_priv;
  }

  /**
   * Returns true if the given repository id is valid (either references a
   * repository that is available to the account, or is an overridden
   * repository id).
   */
  boolean isRepositoryAccessible(String repository_id) {
    if (repository_id == null) {
      return false;
    }
    ThreadContext c = getCurrentThreadContext();
    String account_name = c.account_name;
    // Always able to access account repository,
    if (repository_id.equals(account_name)) {
      return true;
    }
//    // '.sys' repository id only available to super user,
//    if (repository_id.equals(".sys")) {
//      // Only allow access if super user,
//      if (getAccountGlobalPriv().equals("superuser")) {
//        return true;
//      }
//    }
    // Any overridden file repository objects are accessible,
    if (c.getMWPFSURLFileRepository(repository_id) != null) {
      return true;
    }

    // If super user,
    if (getAccountGlobalPriv().equals("superuser")) {
      // We have access to all file repositories that has a path starting with
      // 'ufs',
      try {
        ODBTransaction fs_t =
                    c.sessions_cache.getODBTransaction("ufs" + repository_id);
        return true;
      }
      catch (PathNotAvailableException ex) {
        return false;
      }
    }

    return false;
  }

  /**
   * Returns a RepositoryInfo object that describes properties of a repository.
   * If this returns null then the given repository is not available to this
   * account, or the repository does not exist.
   * <p>
   * NOTE: This does not handle overridden file repositories.
   */
  private RepositoryInfo getRepositoryInfo(String repository_id) {
    // Priv check on the repository id,
    if (!isRepositoryAccessible(repository_id)) {
      return null;
    }
//    if (repository_id.equals(".sys")) {
//      // The system repository,
//      return new RepositoryInfo(".sys", "sysplatform", "fs");
//    }

    return new RepositoryInfo(repository_id, "ufs" + repository_id, "accountfs");

  }

  /**
   * Returns a FileRepository object used to access the file system of the
   * account of this context. This is the same as
   * 'getFileRepository(getAccountName())'.
   * 
   * @return 
   */
  @Override
  public FileRepository getFileRepository() {
    ThreadContext c = getCurrentThreadContext();
    return getFileRepository(c.account_name);
  }

  /**
   * Returns the FileRepository with the given repository id, or null if the
   * repository is not accessible to this account, or the repository does not
   * exist.
   * 
   * @param repository_id
   * @return 
   */
  @Override
  public FileRepository getFileRepository(String repository_id) {

    ThreadContext c = getCurrentThreadContext();

    // Fetch the repository info,
    RepositoryInfo rep_info = getRepositoryInfo(repository_id);
    if (rep_info != null) {
      // Return a FileRepositoryImpl instance,
      ODBTransaction fs_t =
                    c.sessions_cache.getODBTransaction(rep_info.getPathName());
      return new FileRepositoryImpl(
                    rep_info.getRepositoryId(), fs_t, rep_info.getNamedRoot());
    }
    return null;
  }

  /**
   * Returns the FileRepository for the given FileName, or null if the
   * repository is not accessible to this account, the repository does not
   * exist, or there's no repository in the FileName object.
   * 
   * @param file_name
   * @return 
   */
  @Override
  public FileRepository getFileRepositoryFor(FileName file_name) {
    return getFileRepository(file_name.getRepositoryId());
  }


  /**
   * Returns a SessionAuthenticator object used to authenticate users in this
   * context.
   * 
   * @return 
   */
  @Override
  public SessionAuthenticator getSessionAuthenticator() {
    ThreadContext c = getCurrentThreadContext();
    // Get the session authenticator property,
    SessionAuthenticatorImpl authenticator =
                                (SessionAuthenticatorImpl) c.getProperty("sa");
    if (authenticator == null) {
      authenticator = new SessionAuthenticatorImpl(
                               c.sessions_cache, c.vhost_name, c.account_name);
      c.setProperty("sa", authenticator);
    }
    return authenticator;
  }

  /**
   * Returns a UserManager object used to manager the users on an account.
   * 
   * @return 
   */
  @Override
  public UserManager getUserManager() {
    ThreadContext c = getCurrentThreadContext();
    String account_name = c.account_name;

    // The path where the user manager db is located.
    String account_path = "ufs" + account_name;

    // Create a transaction for the path where the user manager is located,
    ODBTransaction usermanager_transaction =
                              c.sessions_cache.getODBTransaction(account_path);

    return new AccountUserManager(usermanager_transaction, account_name);

  }

  /**
   * Returns an object that builds/updates internal structures based on the
   * state of the source files in the file system.
   * 
   * @return 
   */
  @Override
  public BuildSystem getBuildSystem() {
    ThreadContext c = getCurrentThreadContext();
    BuildSystemImpl builder = (BuildSystemImpl) c.getProperty("bs");
    if (builder == null) {
      builder = new BuildSystemImpl(c.account_name);
      c.setProperty("bs", builder);
    }
    return builder;
  }

  @Override
  public AppServiceProcessClient getAppServiceProcessClient() {
    ThreadContext c = getCurrentThreadContext();
    if (c.is_app_service_context) {
      AppServiceProcessClient process_client =
                                (AppServiceProcessClient) c.getProperty("ap");
      if (process_client == null) {
        final PlatformContextBuilder cb = c.context_builder;
        final String server_name = c.vhost_name;
        final boolean is_secure = c.protocol.equals("https");
        // Create a contextifier that sets the context all notified events
        // in this process client are given.
        ContextBuilder contextifier = new ContextBuilder() {
          @Override
          public void enterContext() {
            cb.enterWebContext(
                PlatformContextBuilder.CONTEXT_GRANT, server_name, is_secure);
          }
          @Override
          public void exitContext() {
            cb.exitWebContext(PlatformContextBuilder.CONTEXT_GRANT);
          }
        };
        process_client =
                c.process_client_service.getAppServiceProcessClientFor(
                                                c.account_name, contextifier);
        c.setProperty("ap", process_client);
      }
      return process_client;
    }
    else {
      throw new IllegalStateException(
              "The context is not an application service");
    }
  }

  @Override
  public InstanceProcessClient getInstanceProcessClient() {
    ThreadContext c = getCurrentThreadContext();
    if (c.process_instance != null) {
      return c.process_instance.getInstanceProcessClient();
    }
    else {
      throw new IllegalStateException(
              "The context is not a process operation");
    }
  }

  @Override
  public DDBResourceAccess getDDBResourceAccess() {
    ThreadContext c = getCurrentThreadContext();
    DDBResourceAccess mcaccess = (DDBResourceAccess) c.getProperty("ma");
    if (mcaccess == null) {
      mcaccess = new DDBResourceAccessImpl(c.sessions_cache, c.account_name);
      c.setProperty("ma", mcaccess);
    }
    return mcaccess;
  }

  @Override
  public LogControl getLogControl() {
    return new LogControl() {
      @Override
      public void setAutomaticLogging(boolean b) {
        getCurrentThreadContext().log_request = b;
      }
      @Override
      public boolean isAutomaticLogging() {
        return getCurrentThreadContext().log_request;
      }
    };
  }

  /**
   * Returns the log system object for this context.
   * 
   * @return 
   */
  @Override
  public LogSystem getLogSystem() {
    ThreadContext c = getCurrentThreadContext();
    LogSystemImpl logger = (LogSystemImpl) c.getProperty("ls");
    if (logger == null) {
      logger = new LogSystemImpl(
                              c.sessions_cache, c.account_name, c.log_system);
      c.setProperty("ls", logger);
    }
    return logger;
  }

  /**
   * Returns the platform context ClassLoader. This is the class loader used
   * to load user-code and includes all the classes defined in the application
   * context.
   * 
   * @return 
   */
  @Override
  public ClassLoader getContextClassLoader() {
    return getUserClassLoader();
  }

  /**
   * Returns the base class loader for this context (usually this will be the
   * parent of the application class loader). This does not include the
   * classes defined by the user application, but does include any static
   * libraries that all applications are provided.
   * 
   * @return 
   */
  @Override
  public ClassLoader getApplicationClassLoader() {
    return getCurrentThreadContext().app_class_loader;
  }

//  @Override
//  public ContextDispatcher createContextDispatcher() {
//
//    ThreadContext thread_ctx = getCurrentThreadContext();
//
//    boolean is_app_service_context = thread_ctx.is_app_service_context;
//    DBSessionCache sessions_cache = thread_ctx.sessions_cache;
//    ProcessClientService process_client_server = thread_ctx.process_client_service;
//    ProcessInstance process_instance = thread_ctx.process_instance;
//    String account_name = thread_ctx.account_name;
//    String vhost_name = thread_ctx.vhost_name;
//    String protocol = thread_ctx.protocol;
//    String webapp_name = thread_ctx.webapp_name;
//    LoggerService log_system = thread_ctx.log_system;
//    MWPUserClassLoader user_class_loader = thread_ctx.user_class_loader;
//    ClassLoader app_class_loader = thread_ctx.app_class_loader;
//    ClassNameValidator system_class_validator = thread_ctx.system_class_validator;
//
//    return new ContextDispatcherImpl(
//            is_app_service_context,
//            sessions_cache, process_client_server, process_instance,
//            account_name, vhost_name, protocol, webapp_name,
//            log_system, user_class_loader, app_class_loader,
//            system_class_validator
//    );
//  }

  // ----- Super User methods -----
  
  /**
   * Returns a secure MckoiDDBAccess object that checks all its functions
   * against the 'SuperUserPlatformContextImpl.checkSUAccess' method.
   */
  static MckoiDDBAccess getSecureMckoiDDBAccess() {
    ThreadContext c = getCurrentThreadContext();
    return c.sessions_cache.getSecureDDBAccess();
  }

  /**
   * Returns a secure NetworkAccess object that checks all its functions
   * against the 'SuperUserPlatformContextImpl.checkSUAccess' method.
   */
  static NetworkAccess getSecureNetworkAccess() {
    ThreadContext c = getCurrentThreadContext();
    return c.sessions_cache.getSecureNetworkAccess();
  }
  

  // ----- URL transaction handling -----
  // We may choose to override the user file repository under some
  // circumstances (such as using an old version of a class loader).

  /**
   * Sets the FileRepository to use when the MWPFS URL stream handler requests
   * the given repository_id.
   * 
   * @param repository_id
   * @param fs
   */
  public void setMWPFSURLPathOverride(String repository_id, FileRepository fs) {
    // NOTE: Privileged operation,
    checkSecurity();
    if (repository_id == null || fs == null) throw new NullPointerException();
    getCurrentThreadContext().putMWPFSURLFileRepositoryOverride(
                                                           repository_id, fs);
  }

  /**
   * Sets the ODBTransaction to use when the MWPFS URL stream handler requests
   * the given repository_id.
   * 
   * @param repository_id
   * @param t
   */
  public void setMWPFSURLPathOverride(String repository_id, ODBTransaction t) {
    // NOTE: Privileged operation,
    checkSecurity();
    if (repository_id == null || t == null) throw new NullPointerException();
    // Get the RepositoryInfo that details the addressing information for the
    // file system.
//    System.out.println("repository_id = " + repository_id);
    RepositoryInfo repository_info = getRepositoryInfo(repository_id);
    if (repository_info == null) {
      throw new IllegalStateException(
                      "repository_id is not a file system: " + repository_id);
    }
    FileRepository fs = new FileRepositoryImpl(
                            repository_id, t, repository_info.getNamedRoot());
    setMWPFSURLPathOverride(repository_id, fs);
  }


  /**
   * Clears any overridden FileRepository for the repository_id that may have
   * been set by calling 'setMWPFSURLPathOverride'. Note that it is not
   * necessary for this method to be called. Any URL path overrides will be
   * cleared when the thread context is finished (via
   * 'removeCurrentThreadContext()')
   * 
   * @param repository_id
   */
  public void clearMWPFSURLPathOverride(String repository_id) {
    // NOTE: Privileged operation,
    checkSecurity();
    if (repository_id == null) throw new NullPointerException();
    getCurrentThreadContext().putMWPFSURLFileRepositoryOverride(
                                                         repository_id, null);
  }

  /**
   * Given a repository_id, returns a FileRepository object for the path. This
   * is used by MWPFSURLStreamHandler to fetch the file system for a URL
   * request in the 'mwpfs' protocol. The FileRepository returned here may be
   * an overridden version if 'setMWPFSURLPathOverride' has been used to change
   * the transaction object returned.
   */
  FileRepository getMWPFSURLFileRepository(String repository_id) {
    ThreadContext c = getCurrentThreadContext();
    FileRepository fs = c.getMWPFSURLFileRepository(repository_id);
    if (fs == null) {
      // If it's not overridden then create a FileRepository from the session
      // cache.
      RepositoryInfo repository_info = getRepositoryInfo(repository_id);
      ODBTransaction t = c.sessions_cache.getODBTransaction(
                                                repository_info.getPathName());
      fs = new FileRepositoryImpl(
                             repository_id, t, repository_info.getNamedRoot());
    }
    return fs;
  }

  /**
   * Assigns a unique repository_id for the given FileRepository and makes
   * the repository available to the MWPFS url handler.
   * 
   * @param fs
   * @return 
   */
  public String assignMWPFSURLFileRepository(FileRepository fs) {
    checkSecurity();
    if (fs == null) throw new NullPointerException();
    
    // Create a random repository id,
    StringBuilder new_id = new StringBuilder();
    String new_id_str;
    while (true) {
      new_id.setLength(0);
      new_id.append('$');
      new_id.append(fs.getRepositoryId());
      new_id.append(':');
      int v = SecureRandomUtil.RND.nextInt();
      v = v & 0x0FFFFFF;
      new_id = SystemStatics.encodeLongBase64NoPad(v, new_id);
      new_id_str = new_id.toString();
      // Break if there's not an existing override id with the same id,
      if (getCurrentThreadContext().getMWPFSURLFileRepository(
                                                         new_id_str) == null) {
        break;
      }
    }

    // Create a filter repository and set the override,
    FileRepository filter_fs = new FilterFileRepository(new_id_str, fs);
    getCurrentThreadContext().putMWPFSURLFileRepositoryOverride(
                                                        new_id_str, filter_fs);

    return new_id_str;
  }



  // ----- Inner classes -----

  private static class ThreadContext {

    private final boolean is_app_service_context;
    private final PlatformContextBuilder context_builder;
    private final DBSessionCache sessions_cache;
    private final ProcessClientService process_client_service;
    private final ProcessInstance process_instance;
    private final String account_name;
    private String webapp_name;
    private boolean log_request;
    private final String vhost_name;
    private final String protocol;
    private LoggerService log_system;
    private MWPUserClassLoader user_class_loader;
    private ClassLoader app_class_loader;
    private ClassNameValidator system_class_validator;

    private Map<String, Object> properties = null;

    ThreadContext(boolean is_app_service_context,
                  PlatformContextBuilder context_builder,
                  DBSessionCache sessions_cache,
                  ProcessClientService process_client_service,
                  ProcessInstance process_instance,
                  String account_name, String vhost_name,
                  String protocol,
                  String webapp_name,
                  LoggerService log_system,
                  MWPUserClassLoader user_class_loader,
                  ClassLoader app_class_loader,
                  ClassNameValidator system_class_validator) {
      this.is_app_service_context = is_app_service_context;
      this.context_builder = context_builder;
      this.sessions_cache = sessions_cache;
      this.process_client_service = process_client_service;
      this.process_instance = process_instance;
      this.account_name = account_name;
      this.vhost_name = vhost_name;
      this.protocol = protocol;
      this.webapp_name = webapp_name;
      this.log_system = log_system;
      this.user_class_loader = user_class_loader;
      this.app_class_loader = app_class_loader;
      this.system_class_validator = system_class_validator;
      this.log_request = true;
    }

    private Object getProperty(String name) {
      if (properties == null) {
        return null;
      }
      else {
        return properties.get(name);
      }
    }

    private void setProperty(String name, Object ob) {
      if (properties == null) {
        properties = new HashMap<>();
      }
      properties.put(name, ob);
    }

    private void putMWPFSURLFileRepositoryOverride(
                                     String repository_id, FileRepository fs) {
      // Set the URL path override property for this repository id,
      setProperty("urlco." + repository_id, fs);
    }
    
    private FileRepository getMWPFSURLFileRepository(String repository_id) {
      // Check if the path has been overriden. If not, fetch the latest
      // transaction for the path from the sessions_cache.
      FileRepository fs = (FileRepository) getProperty("urlco." + repository_id);
      return fs;
    }

  }

}
