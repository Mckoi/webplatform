/**
 * com.mckoi.webplatform.jasper.servlet.MckoiJasperOptions  Nov 4, 2012
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

package com.mckoi.webplatform.jasper.servlet;

import com.mckoi.webplatform.impl.PlatformContextImpl;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.file.Matcher;
import org.apache.tomcat.util.scan.Constants;
import org.apache.tomcat.util.scan.StandardJarScanner;

/**
 * A custom implementation of org.apache.jasper.Options used by the Mckoi
 * Web Platform for compiling JSP files.
 *
 * @author Tobias Downer
 */

public class MckoiJasperOptions implements Options {

  /**
    * The JSP class ID for Internet Exploder.
    */
  public static final String DEFAULT_IE_CLASS_ID =
          "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

  /**
    * Cache for the TLD locations
    */
  protected TldLocationsCache tldLocationsCache = null;

  protected JspConfig jspConfig;
  protected TagPluginManager tagPluginManager;

  protected String compilerTargetVM = "1.6";
  protected String compilerSourceVM = "1.6";

  protected String javaEncoding = "UTF-8";

  protected boolean smapSuppressed = true;
  protected boolean smapDumped = false;
  protected boolean caching = true;
  protected Map<String, TagLibraryInfo> cache = null;
  protected String ieClassId = DEFAULT_IE_CLASS_ID;
  protected boolean trimSpaces = false;
  protected boolean genStringAsCharArray = false;
  protected boolean xpoweredBy;
  protected boolean mappedFile = false;
  protected boolean poolingEnabled = true;
  protected String classPath = null;
  protected String compiler = null;
  protected boolean classDebugInfo = true;
  protected boolean errorOnUseBeanInvalidClassAttribute = true;

  protected final File scratchDir;


  /**
    * Create an EmbeddedServletOptions object using data available from
    * ServletConfig and ServletContext. 
    */
  public MckoiJasperOptions(ServletConfig config, ServletContext context) {

    // Set the JarScanner,
    context.setAttribute(JarScanner.class.getName(), new MckoiJarScanner());

    tldLocationsCache = TldLocationsCache.getInstance(context);
    jspConfig = new JspConfig(context);
    tagPluginManager = new TagPluginManager(context);

    // Set the scratch dir for jsp files (try not to use this!)
    if (context != null) {
      File temp_dir = (File) context.getAttribute(ServletContext.TEMPDIR);
      if (temp_dir != null) {
        scratchDir = new File(temp_dir, "jsp");
      }
      else {
        scratchDir = null;
      }
    }
    else {
      scratchDir = null;
    }

  }

  /**
   * Set this if you are using this Options for precompilation.
   */
  public void setupForPrecompilation() {
    cache = new HashMap();
  }

  // ----- Implemented from Options -----

  @Override
  public boolean genStringAsCharArray() {
    return genStringAsCharArray;
  }

  @Override
  public boolean isCaching() {
    return cache != null;
  }

  @Override
  public Map<String, TagLibraryInfo> getCache() {
    return cache;
  }

  @Override
  public int getCheckInterval() {
    return 0;
  }

  @Override
  public boolean getClassDebugInfo() {
    return classDebugInfo;
  }

  @Override
  public String getClassPath() {
    return classPath;
//    if(classPath != null) return classPath;
//    // Get the Java class path,
//    String cp = AccessController.doPrivileged(new PrivilegedAction<String>() {
//      @Override
//      public String run() {
//        return System.getProperty("java.class.path");
//      }
//    });
//
//    System.out.println("$$$ ClassPath = " + cp);
//    new Error().printStackTrace(System.out);
//    return cp;
  }

  @Override
  public String getCompiler() {
    return compiler;
  }

  @Override
  public String getCompilerClassName() {
    return null;
  }

  @Override
  public String getCompilerSourceVM() {
    return compilerSourceVM;
  }

  @Override
  public String getCompilerTargetVM() {
    return compilerTargetVM;
  }

  @Override
  public boolean getDevelopment() {
    return false;
  }

  @Override
  public boolean getDisplaySourceFragment() {
    return true;
  }

  @Override
  public boolean getErrorOnUseBeanInvalidClassAttribute() {
    return errorOnUseBeanInvalidClassAttribute;
  }

  @Override
  public boolean getFork() {
    return false;
  }

  @Override
  public String getIeClassId() {
    return ieClassId;
  }

  @Override
  public String getJavaEncoding() {
    return javaEncoding;
  }

  @Override
  public JspConfig getJspConfig() {
    return jspConfig;
  }

  @Override
  public boolean getKeepGenerated() {
    return true;
  }

  @Override
  public boolean getMappedFile() {
    return mappedFile;
  }

  @Override
  public int getModificationTestInterval() {
    return 0;
  }

  @Override
  public boolean getRecompileOnFail() {
    return false;
  }

  @Override
  public File getScratchDir() {
    return scratchDir;
  }

//    @Override
//    public boolean getSendErrorToClient() {
//      return true;
//    }

  @Override
  public TagPluginManager getTagPluginManager() {
    return tagPluginManager;
  }

  @Override
  public TldLocationsCache getTldLocationsCache() {
    return tldLocationsCache;
  }

  @Override
  public boolean getTrimSpaces() {
    return trimSpaces;
  }

  @Override
  public boolean isPoolingEnabled() {
    return poolingEnabled;
  }

  @Override
  public boolean isSmapDumped() {
    return smapDumped;
  }

  @Override
  public boolean isSmapSuppressed() {
    return smapSuppressed;
  }

  @Override
  public boolean isXpoweredBy() {
    return xpoweredBy;
  }

  @Override
  public int getMaxLoadedJsps() {
    return -1;
  }

  @Override
  public int getJspIdleTimeout() {
    return -1;
  }

  
  // ----- JarScanner -----

  /**
   * A lot of the code here has been copied from StandardJarScanner and
   * modified to suite the Mckoi Web Platform environment.
   */
  private static class MckoiJarScanner extends StandardJarScanner {

    public MckoiJarScanner() {
    }

    private void process(JarScannerCallback callback, URL url)
                                                           throws IOException {

      URLConnection conn = url.openConnection();
      if (conn instanceof JarURLConnection) {
        callback.scan((JarURLConnection) conn);
      }
      else {
        String urlStr = url.toString();
        
        if (urlStr.startsWith("mwpfs:") ||
            urlStr.startsWith("file:") || urlStr.startsWith("jndi:")) {
          if (urlStr.endsWith(Constants.JAR_EXT)) {
            URL jarURL = new URL("jar:" + urlStr + "!/");
            callback.scan((JarURLConnection) jarURL.openConnection());
          }
          else {
            File f;
            try {
              f = new File(url.toURI());
              if (f.isFile() && isScanAllFiles()) {
                // Treat this file as a JAR
                URL jarURL = new URL("jar:" + urlStr + "!/");
                callback.scan((JarURLConnection) jarURL.openConnection());
              }
              else if (f.isDirectory() && isScanAllDirectories()) {
                File metainf = new File(f.getAbsoluteFile() +
                                        File.separator + "META-INF");
                if (metainf.isDirectory()) {
                  callback.scan(f);
                }
              }
            }
            catch (URISyntaxException e) {
              // Wrap the exception and re-throw
              throw new IOException(e);
            }
          }
        }
      }

      
      
    }

    @Override
    public void scan(ServletContext context, ClassLoader classloader,
                     JarScannerCallback callback, Set<String> jarsToSkip) {
      
      Set<String> ignoredJars;
      if (jarsToSkip == null) {
        ignoredJars = Collections.EMPTY_SET;
      }
      else {
        ignoredJars = jarsToSkip;
      }
      Set<String[]> ignoredJarsTokens = new HashSet<String[]>();
      for (String pattern: ignoredJars) {
        ignoredJarsTokens.add(Matcher.tokenizePathAsArray(pattern));
      }

      // Scan WEB-INF/lib
      Set<String> dirList = context.getResourcePaths(Constants.WEB_INF_LIB);
      if (dirList != null) {
        Iterator<String> it = dirList.iterator();
        while (it.hasNext()) {
          String path = it.next();
          if (path.endsWith(Constants.JAR_EXT) &&
              !Matcher.matchPath(ignoredJarsTokens,
                  path.substring(path.lastIndexOf('/')+1))) {
            // Need to scan this JAR
            URL url = null;
            try {
              // File URLs are always faster to work with so use them
              // if available.
              String realPath = context.getRealPath(path);
              if (realPath == null) {
                url = context.getResource(path);
              }
              else {
                url = (new File(realPath)).toURI().toURL();
              }
              process(callback, url);
            }
            catch (IOException e) {
              e.printStackTrace(System.err);
            }
          }
          else {
          }
        }
      }

      // Scan the classpath
      if (isScanClassPath()) {

        ClassLoader loader = PlatformContextImpl.getUserClassLoader();

        while (loader != null) {
          if (loader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) loader).getURLs();
            for (int i=0; i<urls.length; i++) {
              // Extract the jarName if there is one to be found
              String jarName = getJarName(urls[i]);

              // Skip JARs known not to be interesting and JARs
              // in WEB-INF/lib we have already scanned
              if (jarName != null &&
                !(Matcher.matchPath(ignoredJarsTokens, jarName) ||
                    urls[i].toString().contains(
                            Constants.WEB_INF_LIB + jarName))) {
                try {
                  process(callback, urls[i]);
                }
                catch (IOException ioe) {
                  ioe.printStackTrace(System.err);
                }
              }
              else {
              }
            }
          }
          // We don't search the parents. We know there won't be any other
          // interesting .jars past the user class loader,
//          loader = loader.getParent();
          loader = null;
        }

      }

    }

    /*
     * Extract the JAR name, if present, from a URL
     */
    private String getJarName(URL url) {

      String name = null;

      String path = url.getPath();
      int end = path.indexOf(Constants.JAR_EXT);
      if (end != -1) {
        int start = path.lastIndexOf('/', end);
        name = path.substring(start + 1, end + 4);
      }
      else if (isScanAllDirectories()){
        int start = path.lastIndexOf('/');
        name = path.substring(start + 1);
      }

      return name;
    }

  }

}
