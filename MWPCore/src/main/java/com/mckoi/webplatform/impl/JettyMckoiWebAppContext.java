/**
 * com.mckoi.webplatform.impl.JettyMckoiWebAppContext  Jul 14, 2009
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

import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.mwpcore.MWPUserClassLoader;
import com.mckoi.odb.ODBTransaction;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.MWPRuntimeException;
import com.mckoi.webplatform.util.HttpUtils;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import javax.naming.NamingException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.apache.tomcat.InstanceManager;
import org.eclipse.jetty.continuation.ContinuationThrowable;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.Holder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * A Jetty context that uses a MckoiDDB network as the resource location of
 * class data.
 *
 * @author Tobias Downer
 */
class JettyMckoiWebAppContext extends WebAppContext {

  /**
   * The context builder.
   */
  private final PlatformContextBuilder context_builder;

  /**
   * The account name for this context.
   */
  private final String account_name;

  /**
   * The unique web application name of this context.
   */
  private final String webapp_name;

  /**
   * The LoggerService that handles event logging.
   */
  private final LoggerService account_log;

  /**
   * The vhost string expression (eg. '*.mckoi.com').
   */
  private final String vhost_expr;

  /**
   * The vhost protocol expression (eg. 'http', 'https', '*').
   */
  private final String vhost_protocol_expr;

  /**
   * The path context for this webapp (eg. http://myapp.com/[context_path]/...)
   */
  private final String context_path;

  /**
   * The webapp path (the path in the account's filesystem to the files).
   */
  private final String webapp_path;

  /**
   * The MWPUserClassLoader for this web app.
   */
  private final MWPUserClassLoader user_cl;


  /**
   * Constructor, sets up a web application context to the details provided.
   *
   * @param account_name the name of the account the context is in.
   * @param vhost_expr the domain to match (eg. '*.mckoi.com').
   * @param vhost_protocol_expr the protocol to match (eg. 'https', '*').
   * @param context_path the path context
   *                                (eg. http://mckoi.com/[context_path]/...)
   * @param webapp_path the web application resource location (eg.
   *   '/apps/console/')
   */
  JettyMckoiWebAppContext(
                        PlatformContextBuilder context_builder,
                        String account_name,
                        String vhost_expr, String vhost_protocol_expr,
                        String context_path, String webapp_path,
                        LoggerService account_log,
                        String webapp_name) {

    super(null, null, null, null);

    this.context_builder = context_builder;
    
    this.webapp_name = webapp_name;

    // Create a user class loader,
    this.user_cl = context_builder.getClassLoaderSet().createUserClassLoader(
                              context_builder.getClassNameValidator(), false);

    // This is for Tomcat Jasper support,
    this.getServletContext().setAttribute(
            InstanceManager.class.getName(), new MckoiTomcatInstanceManager());

    this.setSessionHandler(new SessionHandler());
    this.setSecurityHandler(new org.eclipse.jetty.security.ConstraintSecurityHandler());
    
    // Set up the servlet handler,
    ServletHandler servlet_handler = new ServletHandler();
    // Set our custom servlet handler,
    this.setServletHandler(servlet_handler);

    // Install our logging filter,
    FilterHolder filter_holder =
                       servlet_handler.newFilterHolder(Holder.Source.EMBEDDED);
    filter_holder.setFilter(new MckoiLogFilter());
    EnumSet<DispatcherType> dispatchers =
                     EnumSet.of(DispatcherType.ASYNC, DispatcherType.REQUEST);
    servlet_handler.addFilterWithMapping(filter_holder, "*", dispatchers);

    // The descriptor for compiled JSP pages,
    // If it exists,
    this.addOverrideDescriptor(webapp_path + "WEB-INF/mwp_jsp_web.xml");

    if (context_path.endsWith("/")) {
      context_path = context_path.substring(0, context_path.length() - 1);
    }

    this.account_name = account_name;
    this.account_log = account_log;
    this.vhost_expr = vhost_expr;
    this.vhost_protocol_expr = vhost_protocol_expr;
    this.context_path = context_path;
    this.webapp_path = webapp_path;

    this.setCopyWebInf(false);
    this.setCopyWebDir(false);

    this.setDisplayName(vhost_expr + " " + account_name);

    // Set the default descriptor
    setDefaultsDescriptor("com/mckoi/webplatform/jetty/webdefault.xml");

    // This is the set of configuration classes appropriate for the Mckoi
    // platform. We remove 'JettyWebXmlConfiguration', and modify web inf,
    // web xml and annotation configuration to work in our sandboxed
    // implementation.
    this.setConfigurationClasses(new String[] {
        "com.mckoi.webplatform.jetty.WebInfConfiguration",
//        "org.eclipse.jetty.webapp.WebXmlConfiguration",
        "com.mckoi.webplatform.jetty.WebXmlConfiguration",
        "org.eclipse.jetty.webapp.MetaInfConfiguration",
        "org.eclipse.jetty.webapp.FragmentConfiguration",

//        // Servlet 3.0 annotation configuration
        "com.mckoi.webplatform.jetty.AnnotationConfiguration"
//        "org.eclipse.jetty.annotations.AnnotationConfiguration"

//        "org.eclipse.jetty.webapp.JettyWebXmlConfiguration",

        // This is probably not needed anymore in latest Jasper,
//        "org.eclipse.jetty.webapp.TagLibConfiguration"

    });

  }

  /**
   * Returns the user class loader for this web app.
   */
  public MWPUserClassLoader getUserClassLoader() {
    return user_cl;
  }

  @Override
  public Resource newResource(String urlOrPath) throws IOException {
//    System.out.println("JettyMckoiWebAppContext.newResource('" + urlOrPath + "')");
    if (urlOrPath == null) {
      return null;
    }
    return new MckoiDDBFileResource(urlOrPath);
  }

  @Override
  public Resource newResource(URL url) throws IOException {
    if (url == null) {
      return null;
    }
    String url_str = HttpUtils.decodeURLFileName(url);
    int delim = url_str.indexOf("/", 1);
    return newResource(url_str.substring(delim));
  }

  // ----- Overridden methods that are not applicable in our implementation -----





  @Override
  public String[] getServerClasses() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String[] getSystemClasses() {
    throw new UnsupportedOperationException();
  }



  // -----

  @Override
  protected void doStart() throws Exception {

    try {
      // Set the virtual host that matches this,
      if (vhost_expr != null && vhost_expr.trim().length() > 0) {
        setVirtualHosts(new String[] { vhost_expr });
      }

      // Set the context path,
      setContextPath(context_path);
      // Set the base resource,
      setBaseResource(new MckoiDDBFileResource(webapp_path));
      // The directory in the local filesystem for temporary files
      setTempDirectory(new File(context_builder.getLocalTempDir(), account_name));

      // Create the class loader. The parent is the user class loader,
      JettyMckoiWebAppClassLoader class_loader =
              new JettyMckoiWebAppClassLoader(
                      user_cl, this, context_builder.getClassNameValidator());
      setClassLoader(class_loader);

      // Defer to the super implementation,
      super.doStart();

      // Did we fail to start?
      if (getUnavailableException() != null) {
        Throwable e = getUnavailableException();
        // Print the exception to a string,
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        account_log.log(Level.SEVERE, "webapp",
              "Failed to start app context: {0} vhost: {1} context_path: {2}\n{3}\n{4}",
              webapp_path, vhost_expr, context_path,
              e.getMessage(), sw.toString());
      }
      else {
        // Log that the app context was started,
        account_log.log(Level.INFO, "webapp",
                        "App context started: {0} vhost: {1} context_path: {2}",
                        webapp_path, vhost_expr, context_path);
      }

    }
    catch (Exception e) {
      // Print the exception to a string,
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      pw.flush();

      // Log the error,
      account_log.log(Level.SEVERE, "webapp",
            "Failed to start app context: {0} vhost: {1} context_path: {2}\n{3}\n{4}",
            webapp_path, vhost_expr, context_path, e.getMessage(), sw.toString());

      // Re-throw the exception,
      throw e;
    }

  }

  @Override
  protected void doStop() throws Exception {

    // Log that the app context was stopped,
    account_log.log(Level.INFO, "webapp",
                    "App context stopped: {0} vhost: {1} context_path: {2}",
                    webapp_path, vhost_expr, context_path);

    super.doStop();

  }


  private static String truncate(String str, int size) {
    if (str == null) {
      return null;
    }
    return (str.length() > size) ? str.substring(0, size) : str;
  }

  /**
   * Logs an exception generated by the servlet.
   */
  private void logAppException(ServletRequest req, Throwable e) {
    // Print the exception to a string,
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    pw.flush();

    // Runtime exceptions get written to the log,
    account_log.log(Level.SEVERE, "app",
              "Handle failed with exception. Webapp: {0} vhost: {1} context: {2}\n{3}",
              webapp_path, vhost_expr, context_path, sw.toString());
  }


  @Override
  public void doHandle(String target, Request jetty_request,
                     HttpServletRequest request, HttpServletResponse response)
                                        throws IOException, ServletException {

    // Remember the 'log_request' flag to be reset to later,
    boolean original_log_request = PlatformContextImpl.getLogThisRequest();

    try {
    
      // Check the protocol matches,
      if (!vhost_protocol_expr.equals("*")) {
        // Not catch all,
        String request_proto;
        if (request.isSecure()) {
          request_proto = "https";
        }
        else {
          request_proto = "http";
        }
        // No match so return,
        if (!request_proto.equals(vhost_protocol_expr)) {
          return;
        }
      }

      // Sets the web application name,
      PlatformContextImpl.setWebApplicationName(webapp_name);
      // Reset the log flag,
      PlatformContextImpl.setLogThisRequest(true);

      // Remember start time for analytics,
      long time_start = System.currentTimeMillis();

      // A wrapper for the response object,
      MckoiResponseWrapped mckoi_response = new MckoiResponseWrapped(response);

      // NOTE: Exception caching here doesn't work because user exceptions are
      //   caught in org.eclipse.jetty.servlet.ServletHandler.

      // We set any properties in ServletContext
      ServletContext servlet_context = request.getServletContext();
      MWPContext mwp_context =
                new MWPContext(PlatformContextImpl.getCurrentContextBuilder());
      servlet_context.setAttribute(MWPContext.ATTRIBUTE_KEY, mwp_context);

      // Delegate the rest to Jetty,
      super.doHandle(target, jetty_request, request, mckoi_response);

      long time_end = System.currentTimeMillis();

      // The time took for this process,
      long time_took = (time_end - time_start);
      Long time_count = (Long)
            jetty_request.getAttribute("com.mckoi.webplatform.impl.TimeCount");
      if (time_count == null) {
        time_count = time_took;
      }
      else {
        time_count = time_count + time_took;
      }

      // --- EXTERNAL REQUEST LOGGING

      // If the request was not handled by this context, and the user code
      // has disabled logging, we do not log
      if (jetty_request.isHandled() && mckoi_response.isCommitted() &&
          PlatformContextImpl.getLogThisRequest() == true) {

        int response_status = mckoi_response.implGetStatus();
        // Do not log 404 (not found) and 304 (not modified),
        if ( response_status == Response.SC_NOT_FOUND ||
             response_status == Response.SC_NOT_MODIFIED ) {
          // Don't log,
        }
        else {

          String local_host = request.getServerName();
          String request_uri = request.getRequestURI();   // eg. '/admin/blah'
          String method = request.getMethod();            // eg. 'GET'
          String query_string = request.getQueryString();
          String path_info = request.getPathInfo();
          String protocol = request.getProtocol();
          String remote_addr = request.getRemoteAddr();
          String referrer = request.getHeader("Referer");
          String user_agent = request.getHeader("User-agent");

          // Truncate large strings to prevent some types of flooding attacks
          // and bad programming.
          local_host = truncate(local_host, 200);
          request_uri = truncate(request_uri, 200);
          query_string = truncate(query_string, 200);
          referrer = truncate(referrer, 200);
          user_agent = truncate(user_agent, 200);

          StringBuilder response_status_str = new StringBuilder();
          response_status_str.append(response_status);

          // Create the log event,
          LogPageEventImpl event = new LogPageEventImpl(System.currentTimeMillis(), 10);
          event.setValue(0, remote_addr);
          event.setValue(1, method);
          event.setValue(2, local_host);
          event.setValue(3, request_uri);
          event.setValue(4, query_string);
          event.setValue(5, protocol);
          event.setValue(6, response_status_str.toString());
          event.setValue(7, referrer);
          event.setValue(8, user_agent);
          event.setValue(9, Long.toString(time_count));

          // Write the entry to the log,
          account_log.log("http", event);

        }

      }
      else {
        // Maintain count of time taken for this request,
        jetty_request.setAttribute(
                             "com.mckoi.webplatform.impl.TimeCount", time_count);
      }

    }
    finally {
      // Reset the 'log_request' flag,
      PlatformContextImpl.setLogThisRequest(original_log_request);
    }

  }

  @Override
  public String getClassPath() {
    String gcp = super.getClassPath();
//    System.out.println("getClassPath: " + gcp);
    return gcp;
  }






  // ----- Inner classes -----


  /**
   * A wrapped response object so that the user application can not cast
   * it to the underlying Jetty implementation. Also the wrapped servlet
   * response object can capture the status code for log reporting.
   */
  private class MckoiResponseWrapped extends HttpServletResponseWrapper {

    /**
     * Remember the response status code.
     */
    private int impl_status_code = 200;

    private MckoiResponseWrapped(HttpServletResponse backed) {
      super(backed);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      impl_status_code = sc;
      super.sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
      impl_status_code = sc;
      super.sendError(sc);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      impl_status_code = 302;
      super.sendRedirect(location);
    }

    @Override
    public void setStatus(int sc) {
      impl_status_code = sc;
      super.setStatus(sc);
    }

    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
      impl_status_code = sc;
      super.setStatus(sc, sm);
    }


    /**
     * Expose the status code.
     */
    private int implGetStatus() {
      return impl_status_code;
    }

  }



  private class MckoiDDBFileResource extends Resource {

    // Set to true to print debugging information to System.out
    private static final boolean DBG_OUTPUT = false;

    private final String resource_path;

    MckoiDDBFileResource(String path) {
//      System.out.println("File Resource created for: " + path);
      if (!path.startsWith("/")) {
        throw new RuntimeException("Not absolute reference: " + path);
      }
      this.resource_path = path;
    }


    
    private FileRepository getFileSystem() {
      ODBTransaction fs_t =
              context_builder.getSessionsCache().getODBTransaction(
                                                        "ufs" + account_name);
      return new FileRepositoryImpl(account_name, fs_t, "accountfs");
    }


    @Override
    public Resource addPath(String path)
                                   throws IOException, MalformedURLException {

//      System.out.println("addPath; " + resource_path + " + " + path);

      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      String new_path = resource_path;
      if (isDirectory()) {
        if (resource_path.endsWith("/")) {
          new_path = new_path + path;
        }
        else {
          new_path = new_path + "/" + path;
        }
      }
      else {
        throw new MWPRuntimeException("Directory not found: {0}", resource_path);
      }

      return new MckoiDDBFileResource(new_path);
    }

    @Override
    public boolean delete() throws SecurityException {
      throw new SecurityException();
    }

    @Override
    public boolean exists() {
      if (resource_path.endsWith("/")) {
        return isDirectory();
      }
      else {
        FileInfo finfo = getFileSystem().getFileInfo(resource_path);
        return finfo != null;
      }
    }

    @Override
    public File getFile() {
      if (DBG_OUTPUT) {
        System.out.println("@@ (" + resource_path + ").getFile()");
//        new Error().printStackTrace(System.out);
      }
      return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      if (DBG_OUTPUT) {
        System.out.println("@@ (" + resource_path + ").getInputStream()");
      }
      return DataFileUtils.asInputStream(
                                  getFileSystem().getDataFile(resource_path));
    }

    @Override
    public String getName() {
      int last_from = resource_path.length();
      if (resource_path.endsWith("/")) {
        --last_from;
      }
      int n = resource_path.lastIndexOf("/", last_from);
      if (n == -1) {
        return resource_path;
      }
      else {
        return resource_path.substring(n + 1);
      }
    }

    @Override
    public URL getURL() {
      URL url;
      try {
        url = new URL("mwpfs", null, -1, "/" + account_name + resource_path);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      if (DBG_OUTPUT) {
        System.out.println("@@ (" + resource_path + ").getURL() = " + url);
      }
      return url;
    }

    @Override
    public boolean isDirectory() {
      // If we don't end with "/" , we can't be a directory,
      if (!resource_path.endsWith("/")) {
        return false;
      }

      FileInfo finfo = getFileSystem().getFileInfo(resource_path);
      if (finfo == null) {
        return false;
      }
      else {
        return finfo.getMimeType().equals("$dir");
      }
    }

    @Override
    public long lastModified() {
      FileInfo finfo = getFileSystem().getFileInfo(resource_path);
      if (finfo == null) {
        throw new MWPRuntimeException("Resource {0} not found", resource_path);
      }
      long last_modified = finfo.getLastModified();
      if (DBG_OUTPUT) {
        System.out.println("@@ (" + resource_path + ").lastModified() = " + last_modified);
      }
      return last_modified;
    }

    @Override
    public long length() {
      if (isDirectory()) {
        return 0;
      }
      else {
        DataFile dfile = getFileSystem().getDataFile(resource_path);
        if (dfile == null) {
          throw new MWPRuntimeException("Resource {0} not found", resource_path);
        }
        long size = dfile.size();
        if (DBG_OUTPUT) {
          System.out.println("@@ (" + resource_path + ").length() = " + size);
        }
        return size;
      }
    }

    @Override
    public String[] list() {
      List<FileInfo> dir_entries =
                       getFileSystem().getDirectoryFileInfoList(resource_path);
      if (dir_entries == null) {
        throw new MWPRuntimeException("Resource {0} not found", resource_path);
      }
      int sz = dir_entries.size();
      String[] result = new String[sz];
      int i = 0;
      for (FileInfo file : dir_entries) {
        result[i] = file.getItemName();
        ++i;
      }
//      System.out.println("@@ (" + resource_path + ").list() = " + Arrays.toString(result));
      return result;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean renameTo(Resource dest) throws SecurityException {
      throw new SecurityException();
    }

    @Override
    public void writeTo(OutputStream out, long start, long count) throws IOException {
      DataFile file = getFileSystem().getDataFile(resource_path);
      if (file == null) {
        throw new FileNotFoundException(
                MessageFormat.format("Resource {0} not found", resource_path));
      }
      file.position(start);
      // PENDING: We should possibly add this functionality to DataFile so
      //   we don't need to have this buffer.
      byte[] buf = new byte[1024];
      while (count > 0) {
        int to_read = (int) Math.min(buf.length, count);
        file.get(buf, 0, to_read);
        out.write(buf, 0, to_read);
        count -= to_read;
      }
    }

    @Override
    public ReadableByteChannel getReadableByteChannel() throws IOException {
      DataFile file = getFileSystem().getDataFile(resource_path);
      if (file == null) {
        throw new FileNotFoundException(
                MessageFormat.format("Resource {0} not found", resource_path));
      }
      return new DataFileReadableByteChannel(file);
    }

    @Override
    public boolean isContainedIn(Resource r) throws MalformedURLException {
//      System.out.println("$$$$$ isContainedIn called");

      // It's not clear from the Jetty source exactly what this method is
      // used for. The other resource implementations in Jetty all seem to
      // return 'false', so we'll do the same.
      return false;
    }

    @Override
    public String toString() {
      return "mwpfs:/" + account_name + resource_path;
    }

  }

  /**
   * A non-blocking implementation of ReadableByteChannel that reads the content
   * of the file into a ByteBuffer.
   */
  private static class DataFileReadableByteChannel implements ReadableByteChannel {

    private volatile boolean is_open;
    private final DataFile dfile;

    public DataFileReadableByteChannel(DataFile dfile) {
      this.dfile = dfile;
      this.is_open = true;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      if (!is_open) {
        throw new ClosedChannelException();
      }
      int read_length = dst.remaining();
      long position = dfile.position();
      long size = dfile.size();
      // End of stream,
      if (position >= size) {
        return -1;
      }
      // What we're going to read,
      final int read_amount = (int) Math.min(read_length, size - position);
      int count = read_amount;
      // PENDING: We should possibly add this functionality to DataFile so
      //   we don't need to have this buffer.
      byte[] buf = new byte[1024];
      while (is_open && count > 0) {
        int to_read = Math.min(buf.length, count);
        dfile.get(buf, 0, to_read);
        dst.put(buf, 0, to_read);
        count -= to_read;
      }
      if (!is_open) {
        throw new AsynchronousCloseException();
      }
      return read_amount;
    }

    @Override
    public boolean isOpen() {
      return is_open;
    }

    @Override
    public void close() throws IOException {
      is_open = false;
    }

  }

  /**
   * Our filter that catches exceptions and logs them to the user's log
   * event.
   */
  private class MckoiLogFilter implements Filter {

    public MckoiLogFilter() {
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                      FilterChain chain) throws IOException, ServletException {

      Throwable th = null;

      try {

        chain.doFilter(request, response);

      }
      catch(EofException e) {
        // Rethrow, handled by Jetty,
        throw e;
      }
      catch(RuntimeIOException e) {
        // Rethrow, handled by Jetty,
        throw e;
      }
      catch(ContinuationThrowable e) {   
        // Rethrow, handled by Jetty,
        throw e;
      }
      catch (ServletException e) {
        // Log this exception and don't rethrow,
        th = e;
      }
      catch (RuntimeException e) {
        // Log this exception and don't rethrow,
        th = e;
      }

      // If there's an exception then log it in the Mckoi Web Platform
      // exception handler,
      if (th != null) {
        // Log the exception,
        logAppException(request, th);

        // If the response isn't committed then turn it into an error response,
        if (!response.isCommitted()) {
          request.setAttribute(Dispatcher.ERROR_EXCEPTION_TYPE, th.getClass());
          request.setAttribute(Dispatcher.ERROR_EXCEPTION, th);
          if (response instanceof HttpServletResponse) {
            HttpServletResponse http_response = (HttpServletResponse) response;
            if (th instanceof UnavailableException) {
              UnavailableException ue = (UnavailableException)th;
              if (ue.isPermanent()) {
                http_response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                        th.getMessage());
              }
              else {
                http_response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                                        th.getMessage());
              }
            }
            else {
              http_response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                      th.getMessage());
            }
          }
        }
      }

    }

    @Override
    public void destroy() {
    }

  }

  /**
   * Instance manager for interfacing with Tomcat Jasper (the JSP engine). This
   * appears to be for taglib stuff in Jasper, but not 100% certain.
   */
  private class MckoiTomcatInstanceManager implements InstanceManager {

    @Override
    public Object newInstance(String class_name)
             throws IllegalAccessException, InvocationTargetException,
                    NamingException, InstantiationException,
                    ClassNotFoundException {

      return Class.forName(class_name, true, getClassLoader()).newInstance();

//      System.err.println("InstanceManager.newInstance(string: " + class_name + ")");

    }

    @Override
    public Object newInstance(String class_name, ClassLoader cl)
             throws IllegalAccessException, InvocationTargetException,
                    NamingException, InstantiationException,
                    ClassNotFoundException {

      return Class.forName(class_name, true, getClassLoader()).newInstance();

//      System.err.println("InstanceManager.newInstance(string: " + class_name + ", classloader: " + cl + ")");

    }

    @Override
    public void newInstance(Object o)
             throws IllegalAccessException, InvocationTargetException,
                     NamingException {

//      System.err.println("InstanceManager.newInstance(object: " + o + ")");

    }

    @Override
    public void destroyInstance(Object o)
             throws IllegalAccessException, InvocationTargetException {

//      System.err.println("InstanceManager.destroyInstance(object: " + o + ")");

    }

  }

}
