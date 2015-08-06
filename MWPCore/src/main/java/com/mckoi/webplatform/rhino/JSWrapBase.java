/**
 * com.mckoi.mwpui.JSWrapBase  Nov 16, 2012
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

package com.mckoi.webplatform.rhino;

import com.mckoi.apihelper.ScriptResourceAccess;
import com.mckoi.data.DataFileUtils;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.process.*;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import org.mozilla.javascript.*;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.ModuleSource;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;

/**
 * Base JSWrap utilities.
 *
 * @author Tobias Downer
 */

public class JSWrapBase {

  private static final ModuleScriptProvider SOURCE_LOADER;
  
  static {
    // Create the javascript source loader + cache,
    SOURCE_LOADER =
            new SoftCachingModuleScriptProvider(new JSModuleSourceProvider());
  }

  /**
   * Returns the module source loader.
   */
  public static ModuleScriptProvider getModuleSourceLoader() {
    return SOURCE_LOADER;
  }

  /**
   * Module source loader that uses the PlatformContext to fetch the content of
   * the source files.
   */
  private static class JSModuleSourceProvider implements ModuleSourceProvider {

    private ModuleSource toModuleSource(
            FileName script_fn, FileInfo file_info, Object validator)
                                       throws IOException, URISyntaxException {

//      System.out.print("toModuleSource " + script_fn + " ");

      // Hasn't changed, so return NOT_MODIFIED
      if (validator != null &&
                     validator.equals(new Long(file_info.getLastModified()))) {
//        System.out.println("NOT MODIFIED");
        return NOT_MODIFIED;
      }
//      System.out.println("LOAD");

      // The script uri,
      URI script_uri =
         new URI("mwpfs", null, script_fn.toString(), null);
      // The script base uri,
      URI script_base_uri =
         new URI("mwpfs", null, "/" + script_fn.getRepositoryId() + "/", null);

      // Load from script,
      Reader reader = new BufferedReader(new InputStreamReader(
               DataFileUtils.asInputStream(file_info.getDataFile()), "UTF-8"));

      return new ModuleSource(reader, this,
               script_uri, script_base_uri, file_info.getLastModified());

    }

    private ModuleSource toModuleSource(
            FileName script_fn, Reader reader, Object validator)
                                       throws IOException, URISyntaxException {

//      System.out.print("toModuleSource " + script_fn + " ");

      // Hasn't changed, so return NOT_MODIFIED
      if (validator != null &&
                     validator.equals(new Long(-1))) {
//        System.out.println("NOT MODIFIED");
        return NOT_MODIFIED;
      }
//      System.out.println("LOAD");

      // The script uri,
      URI script_uri =
         new URI("mwpfs", null, script_fn.toString(), null);
      // The script base uri,
      URI script_base_uri =
         new URI("mwpfs", null, "/" + script_fn.getRepositoryId() + "/", null);

      // Load from script,
      return new ModuleSource(reader, this,
               script_uri, script_base_uri, new Long(-1));

    }

    private ModuleSource loadModuleSource(
                    String script_pathname, Object validator)
                                       throws IOException, URISyntaxException {

//      System.out.println("loadModuleSource");
//      System.out.println("  script_pathname = " + script_pathname);

      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      String account_name = ctx.getAccountName();

      // If it's a relative reference then qualify it,
      if (!script_pathname.startsWith("/")) {

        // This is a library reference,
        // First check if there's a resource available for this from the
        // application class loader,

        String resource_pathname = "bin/lib/" + script_pathname + ".js";

        Reader r = ScriptResourceAccess.getResourceScriptReader(
                          ctx.getApplicationClassLoader(), resource_pathname);
        if (r != null) {
          // Ok, it's a resource, 
          String app_name = ctx.getWebApplicationName();
          String dotjs_pathname = "/.cl/" + app_name + "/" + resource_pathname;
          FileName script_fn = new FileName(dotjs_pathname);
          return toModuleSource(script_fn, r, validator);
        }

        // Not found as a resource in the class loader so now consider it
        // a /bin/lib/ reference in the account's file system,

        FileName lib_base_fn = new FileName("/" + account_name + "/bin/lib/");

        script_pathname =
                lib_base_fn.resolve(new FileName(script_pathname)).toString();

      }

      FileName script_fn = new FileName(script_pathname + ".js");
      String repository_id = script_fn.getRepositoryId();
      // If it's the class loader repository,
      if (repository_id.equals(".cl")) {
        // Try and look up the resource,
        String pathname = script_fn.getPathFile();
        // Cut at the first path (the web app name used by the cache),
        int delim = pathname.indexOf('/', 1);
        if (delim != -1) {
          // The first part of the path will be the account name,
          String to_lookup = pathname.substring(delim + 1);

          Reader r = ScriptResourceAccess.getResourceScriptReader(
                            ctx.getApplicationClassLoader(), to_lookup);

          if (r != null) {
            // Ok, it's a resource in this class loader,
            // Make sure the URL has this web app's name in it,
            String app_name = ctx.getWebApplicationName();
            String dotjs_pathname = "/.cl/" + app_name + "/" + to_lookup;
            script_fn = new FileName(dotjs_pathname);
            return toModuleSource(script_fn, r, validator);
          }
        }
      }
      // Otherwise look it up in the available repositories,
      else {
        FileRepository fs = ctx.getFileRepository(repository_id);
        if (fs != null) {
          FileInfo fi;
          fi = fs.getFileInfo(script_fn.getPathFile());
          if (fi != null && fi.isFile()) {
            return toModuleSource(script_fn, fi, validator);
          }
          script_fn = new FileName(script_pathname + ".mjs");
          fi = fs.getFileInfo(script_fn.getPathFile());
          if (fi != null && fi.isFile()) {
            return toModuleSource(script_fn, fi, validator);
          }
        }
      }


      // Not found,
      return null;

    }

    @Override
    public ModuleSource loadSource(String module_id, Scriptable paths,
                     Object validator) throws IOException, URISyntaxException {

//      System.out.println("loadSource");
//      System.out.println("  module_id = " + module_id);
//      System.out.println("  paths = " + paths);
//      System.out.println("  validator = " + validator);

      return loadModuleSource(module_id, validator);

    }

    @Override
    public ModuleSource loadSource(URI uri, URI base_uri,
                     Object validator) throws IOException, URISyntaxException {

//      System.out.println("loadSource");
//      System.out.println("  uri = " + uri);
//      System.out.println("  base_uri = " + base_uri);
//      System.out.println("  validator = " + validator);
      
      return loadModuleSource(uri.getPath(), validator);
      
    }
    
  }


  /**
   * A generic context factory.
   */
  public static JSWrapContextFactory generic_context_factory =
                                                    new JSWrapContextFactory();

  /**
   * A context factory that sets the application class loader to the Mckoi
   * Web Platform class loader, sets the JavaScript language to 1.8 and sets
   * up an appropriate security controller.
   */
  public static class JSWrapContextFactory extends ContextFactory {

    public JSWrapContextFactory() {
    }

    @Override
    protected void onContextCreated(Context c) {
      super.onContextCreated(c);

      c.setLanguageVersion(Context.VERSION_1_8);
      // Set the security controller,
      c.setSecurityController(new SecurityController() {
        @Override
        public GeneratedClassLoader createClassLoader(
                              ClassLoader parentLoader, Object securityDomain) {
          return new ScriptClassLoader(parentLoader);
        }
        @Override
        public Object getDynamicSecurityDomain(Object securityDomain) {
          return securityDomain;
        }
      });
    }

    @Override
    public Context enterContext() {
      Context c = super.enterContext();
      // Set the application class loader,
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();
      c.setApplicationClassLoader(ctx.getApplicationClassLoader());
      return c;
    }

  }

//  public static Context enterJSContext(ContextFactory context_factory) {
//    Context c;
//    if (context_factory != null) {
//      c = context_factory.enterContext();
//    }
//    else {
//      c = Context.enter();
//    }
//
////    c.setWrapFactory(wrap_factory);
//
//    return c;
//  }
//
//  public static void exitJSContext() {
//    Context.exit();
//  }

  // ----------

  public static Function getFunction(Scriptable scope, String fun_name) {
    Object get = scope.get(fun_name, scope);
    if (get instanceof Function) {
      return (Function) get;
    }
    return null;
  }

  // ----- Static process.js conveniences -----

  /**
   * Converts a ScriptableObject array into a Java Object[] array.
   */
  public static Object[] getJavaObjectFromJSArray(ScriptableObject obj_arr) {

    // Get the length,
    int len = ((Number) obj_arr.get("length", obj_arr)).intValue();
    // Convert into a string array,
    Object[] args = new Object[len];
    for (int i = 0; i < len; ++i) {
      Object val = obj_arr.get(i, obj_arr);
      if (    val != null
           && !val.equals(Undefined.instance)
           && !val.equals(Scriptable.NOT_FOUND)) {
        args[i] = val.toString();
      }
    }

    return args;

  }

  /**
   * Converts a ScriptableObject array into a Java String[] array.
   */
  public static String[] getJavaStringArrFromJSArray(
                                                    ScriptableObject obj_arr) {

    // Get the length,
    int len = ((Number) obj_arr.get("length", obj_arr)).intValue();
    // Convert into a string array,
    String[] args = new String[len];
    for (int i = 0; i < len; ++i) {
      Object val = obj_arr.get(i, obj_arr);
      if (    val != null
           && !val.equals(Undefined.instance)
           && !val.equals(Scriptable.NOT_FOUND)) {
        args[i] = val.toString();
      }
    }

    return args;

  }

  /**
   * Encodes the arguments into a ProcessMessage of string values.
   */
  public static ProcessMessage encodeProcessMessage(ScriptableObject obj) {

    return ByteArrayProcessMessage.encodeArgs(getJavaObjectFromJSArray(obj));

  }

  /**
   * Decodes a ProcessMessage into a JavaScript array of string values.
   */
  public static NativeArray decodeProcessMessage(ProcessMessage msg) {
    
    Object[] items = ByteArrayProcessMessage.decodeArgsList(msg);
    int sz = items.length;
    for (int i = 0; i < sz; ++i) {
      items[i] = Context.javaToJS(items[i], null);
    }
    NativeArray narr = new NativeArray(items);

    return narr;
  }

  public static NativeArray decodeProcessMessage(ProcessInputMessage msg) {
    return decodeProcessMessage(msg.getMessage());
  }

  // ----------
  
  /**
   * Given a JavaScript file name, creates a new process and initializes it
   * to the script at the location. This will send an '.init' method to the
   * newly created process which in turn will invoke the 'init' JavaScript
   * function in the process.
   */
  public static ProcessId createJavaScriptProcess(
                           PlatformContext ctx,
                           String app_name,
                           String java_script_file_name) {

    // If no app_name given then set it to the context's web application name,
    if (app_name == null) {
      app_name = ctx.getWebApplicationName();
    }

    // Get the client,
    InstanceProcessClient client = ctx.getInstanceProcessClient();

    // The process object to run,
    String process_name = JSWrapProcessOperation.class.getName();

    // Create a process,
    ProcessId process_id;
    try {
      process_id = client.createProcess(app_name, process_name);
    }
    catch (ProcessUnavailableException e) {
      // On failure, wrap on a JSProcessUnavailableException,
      throw new JSProcessUnavailableException(e);
    }

    // Send '.init' message to the process,
    ProcessMessage msg =
            ByteArrayProcessMessage.encodeArgs(".init", java_script_file_name);
    client.invokeFunction(process_id, msg, false);
    // We don't expect a reply to the .init function invoke,

    return process_id;
  }

  
  public static int invokeJavaScriptFunction(
                       PlatformContext ctx, ProcessId process_id,
                       ProcessMessage fun_msg, boolean reply_expected) {

    // Get the client,
    InstanceProcessClient client = ctx.getInstanceProcessClient();

    // Invoke the function on the JavaScript process,
    int call_id = client.invokeFunction(process_id, fun_msg, reply_expected);

    // Return the call id value,
    return call_id;

  }

  public static int invokeJavaScriptServersQuery(
                       PlatformContext ctx, ServersQuery query) {

    // Get the client,
    InstanceProcessClient client = ctx.getInstanceProcessClient();

    // Invoke the servers query,
//    Object[] args = JSWrapProcessOperation.getJavaObjectFromJSArray(js_arr);
    int call_id = client.invokeServersQuery(query);

    // Return the call id value,
    return call_id;

  }
  
  public static void sendJavaScriptSignal(
                        PlatformContext ctx, ProcessId process_id,
                                            ScriptableObject signal_str_arr) {

    // Convert the JavaScript array,
    String[] java_str_arr = getJavaStringArrFromJSArray(signal_str_arr);
    // Get the client,
    InstanceProcessClient client = ctx.getInstanceProcessClient();
    // Send the signal,
    client.sendSignal(process_id, java_str_arr);

  }
  

  // ---------- The class loader ----------

  /**
   * The Rhino class loader.
   */
  public static class ScriptClassLoader extends ClassLoader 
                                              implements GeneratedClassLoader {

    private final ClassLoader parentLoader;

    ScriptClassLoader(ClassLoader parentLoader) {
      this.parentLoader = parentLoader;
    }

    @Override
    public Class<?> defineClass(String name, byte[] data) {
      return super.defineClass(name, data, 0, data.length, null);
    }

    @Override
    public void linkClass(Class<?> cl) {
      resolveClass(cl);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve)
                                               throws ClassNotFoundException {
      Class<?> cl = findLoadedClass(name);
      if (cl == null) {
        if (parentLoader != null) {
          cl = parentLoader.loadClass(name);
        }
        else {
          cl = findSystemClass(name);
        }
      }
      if (resolve) {
        resolveClass(cl);
      }
      return cl;
    }

  }

}
