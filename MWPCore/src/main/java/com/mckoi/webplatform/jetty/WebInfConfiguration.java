
package com.mckoi.webplatform.jetty;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.PatternMatcher;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * An implementation of org.eclipse.jetty.webapp.WebInfConfiguration that
 * works in the Mckoi Web Platform.
 *
 * @author Tobias Downer
 */

public class WebInfConfiguration
                        extends org.eclipse.jetty.webapp.WebInfConfiguration {

    private static final Logger LOG = Log.getLogger(org.eclipse.jetty.webapp.WebInfConfiguration.class);

    @Override
    public void preConfigure(final WebAppContext context) throws Exception
    {
        //Make a temp directory for the webapp if one is not already set
        resolveTempDirectory(context);

        //Extract webapp if necessary
        unpack (context);


        //Apply an initial ordering to the jars which governs which will be scanned for META-INF
        //info and annotations. The ordering is based on inclusion patterns.
        String tmp = (String)context.getAttribute(WEBINF_JAR_PATTERN);
        Pattern webInfPattern = (tmp==null?null:Pattern.compile(tmp));
//        tmp = (String)context.getAttribute(CONTAINER_JAR_PATTERN);
//        Pattern containerPattern = (tmp==null?null:Pattern.compile(tmp));

        // ISSUE -
        // This seems dodgy to me. I'm not sure why we need to be
        // querying the class loader hierarchy for jars.
        // Going to comment it out.

//        //Apply ordering to container jars - if no pattern is specified, we won't
//        //match any of the container jars
//        PatternMatcher containerJarNameMatcher = new PatternMatcher ()
//        {
//            public void matched(URI uri) throws Exception
//            {
//                context.getMetaData().addContainerJar(Resource.newResource(uri));
//            }
//        };

//        ClassLoader loader = context.getClassLoader();
//        while (loader != null && (loader instanceof URLClassLoader))
//        {
//            URL[] urls = ((URLClassLoader)loader).getURLs();
//            if (urls != null)
//            {
//                URI[] containerUris = new URI[urls.length];
//                int i=0;
//                for (URL u : urls)
//                {
//                    try
//                    {
//                        containerUris[i] = u.toURI();
//                    }
//                    catch (URISyntaxException e)
//                    {
//                        containerUris[i] = new URI(u.toString().replaceAll(" ", "%20"));
//                    }
//                    i++;
//                }
//                containerJarNameMatcher.match(containerPattern, containerUris, false);
//            }
//            loader = loader.getParent();
//        }

        //Apply ordering to WEB-INF/lib jars
        PatternMatcher webInfJarNameMatcher = new PatternMatcher ()
        {
            @Override
            public void matched(URI uri) throws Exception
            {
                context.getMetaData().addWebInfJar(Resource.newResource(uri));
            }
        };
        List<Resource> jars = findJars(context);

        //Convert to uris for matching
        URI[] uris = null;
        if (jars != null)
        {
            uris = new URI[jars.size()];
            int i=0;
            for (Resource r: jars)
            {
                uris[i++] = r.getURI();
            }
        }
        webInfJarNameMatcher.match(webInfPattern, uris, true); //null is inclusive, no pattern == all jars match
       
        //No pattern to appy to classes, just add to metadata
        context.getMetaData().setWebInfClassesDirs(findClassDirs(context));
    }


    @Override
    public void configure(WebAppContext context) throws Exception
    {
        //cannot configure if the context is already started
        if (context.isStarted())
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Cannot configure webapp "+context+" after it is started");
            return;
        }

        Resource web_inf = context.getWebInf();

        // Add WEB-INF classes and lib classpaths
        if (web_inf != null && web_inf.isDirectory() && context.getClassLoader() instanceof WebAppClassLoader)
        {
            // Look for classes directory
            Resource classes= web_inf.addPath("classes/");
            if (classes.exists())
                ((WebAppClassLoader)context.getClassLoader()).addClassPath(classes);

            // Look for jars
            Resource lib= web_inf.addPath("lib/");
            if (lib.exists() || lib.isDirectory())
                ((WebAppClassLoader)context.getClassLoader()).addJars(lib);
        }

        // Look for extra resource
        @SuppressWarnings("unchecked")
        Set<Resource> resources = (Set<Resource>)context.getAttribute(RESOURCE_DIRS);
        if (resources!=null && !resources.isEmpty())
        {
            Resource[] collection=new Resource[resources.size()+1];
            int i=0;
            collection[i++]=context.getBaseResource();
            for (Resource resource : resources)
                collection[i++]=resource;
            context.setBaseResource(new ResourceCollection(collection));
        }
    }

    @Override
    public void deconfigure(WebAppContext context) throws Exception
    {
        //if we're not persisting the temp dir contents delete it
        if (!context.isPersistTempDirectory())
        {
            IO.delete(context.getTempDirectory());
        }
        
        //if it wasn't explicitly configured by the user, then unset it
        Boolean tmpdirConfigured = (Boolean)context.getAttribute(TEMPDIR_CONFIGURED);
        if (tmpdirConfigured != null && !tmpdirConfigured) 
            context.setTempDirectory(null);

        //reset the base resource back to what it was before we did any unpacking of resources
        context.setBaseResource(_preUnpackBaseResource);
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.webapp.AbstractConfiguration#cloneConfigure(org.eclipse.jetty.webapp.WebAppContext, org.eclipse.jetty.webapp.WebAppContext)
     */
    @Override
    public void cloneConfigure(WebAppContext template, WebAppContext context) throws Exception
    {
        // No temp directory in the Mckoi web platform version, so this is
        // not necessary,
//        File tmpDir=File.createTempFile(WebInfConfiguration.getCanonicalNameForWebAppTmpDir(context),"",template.getTempDirectory().getParentFile());
//        if (tmpDir.exists())
//        {
//            IO.delete(tmpDir);
//        }
//        tmpDir.mkdir();
//        tmpDir.deleteOnExit();
//        context.setTempDirectory(tmpDir);
    }


    /* ------------------------------------------------------------ */
    /**
     * Get a temporary directory in which to unpack the war etc etc.
     * The algorithm for determining this is to check these alternatives
     * in the order shown:
     *
     * <p>A. Try to use an explicit directory specifically for this webapp:</p>
     * <ol>
     * <li>
     * Iff an explicit directory is set for this webapp, use it. Do NOT set
     * delete on exit.
     * </li>
     * <li>
     * Iff javax.servlet.context.tempdir context attribute is set for
     * this webapp && exists && writeable, then use it. Do NOT set delete on exit.
     * </li>
     * </ol>
     *
     * <p>B. Create a directory based on global settings. The new directory
     * will be called "Jetty_"+host+"_"+port+"__"+context+"_"+virtualhost
     * Work out where to create this directory:
     * <ol>
     * <li>
     * Iff $(jetty.home)/work exists create the directory there. Do NOT
     * set delete on exit. Do NOT delete contents if dir already exists.
     * </li>
     * <li>
     * Iff WEB-INF/work exists create the directory there. Do NOT set
     * delete on exit. Do NOT delete contents if dir already exists.
     * </li>
     * <li>
     * Else create dir in $(java.io.tmpdir). Set delete on exit. Delete
     * contents if dir already exists.
     * </li>
     * </ol>
     */
    public void resolveTempDirectory (WebAppContext context)
    {

        // Do nothing, because in the Mckoi web platform wars are upacked
        // by default.

//        //If a tmp directory is already set, we're done
//        File tmpDir = context.getTempDirectory();
//        if (tmpDir != null && tmpDir.isDirectory() && tmpDir.canWrite())
//        {
//            context.setAttribute(TEMPDIR_CONFIGURED, Boolean.TRUE);
//            return; // Already have a suitable tmp dir configured
//        }
//
//
//        // No temp directory configured, try to establish one.
//        // First we check the context specific, javax.servlet specified, temp directory attribute
//        File servletTmpDir = asFile(context.getAttribute(WebAppContext.TEMPDIR));
//        if (servletTmpDir != null && servletTmpDir.isDirectory() && servletTmpDir.canWrite())
//        {
//            // Use as tmpDir
//            tmpDir = servletTmpDir;
//            // Ensure Attribute has File object
//            context.setAttribute(WebAppContext.TEMPDIR,tmpDir);
//            // Set as TempDir in context.
//            context.setTempDirectory(tmpDir);
//            return;
//        }
//
//        try
//        {
//            // Put the tmp dir in the work directory if we had one
//            File work =  new File(System.getProperty("jetty.home"),"work");
//            if (work.exists() && work.canWrite() && work.isDirectory())
//            {
//                makeTempDirectory(work, context, false); //make a tmp dir inside work, don't delete if it exists
//            }
//            else
//            {
//                File baseTemp = asFile(context.getAttribute(WebAppContext.BASETEMPDIR));
//                if (baseTemp != null && baseTemp.isDirectory() && baseTemp.canWrite())
//                {
//                    // Use baseTemp directory (allow the funky Jetty_0_0_0_0.. subdirectory logic to kick in
//                    makeTempDirectory(baseTemp,context,false);
//                }
//                else
//                {
//                    makeTempDirectory(new File(System.getProperty("java.io.tmpdir")),context,true); //make a tmpdir, delete if it already exists
//                }
//            }
//        }
//        catch(Exception e)
//        {
//            tmpDir=null;
//            Log.ignore(e);
//        }
//
//        //Third ... Something went wrong trying to make the tmp directory, just make
//        //a jvm managed tmp directory
//        if (context.getTempDirectory() == null)
//        {
//            try
//            {
//                // Last resort
//                tmpDir=File.createTempFile("JettyContext","");
//                if (tmpDir.exists())
//                    IO.delete(tmpDir);
//                tmpDir.mkdir();
//                tmpDir.deleteOnExit();
//                context.setTempDirectory(tmpDir);
//            }
//            catch(IOException e)
//            {
//                Log.warn("tmpdir",e); System.exit(1);
//            }
//        }
    }


    public void makeTempDirectory (File parent, WebAppContext context, boolean deleteExisting)
    throws IOException
    {

        // Do nothing, temp directory is not necessary in the Mckoi
        // Web Platform.

//        if (parent != null && parent.exists() && parent.canWrite() && parent.isDirectory())
//        {
//            String temp = getCanonicalNameForWebAppTmpDir(context);
//            File tmpDir = new File(parent,temp);
//
//            if (deleteExisting && tmpDir.exists())
//            {
//                if (!IO.delete(tmpDir))
//                {
//                    if(Log.isDebugEnabled())Log.debug("Failed to delete temp dir "+tmpDir);
//                }
//
//                //If we can't delete the existing tmp dir, create a new one
//                if (tmpDir.exists())
//                {
//                    String old=tmpDir.toString();
//                    tmpDir=File.createTempFile(temp+"_","");
//                    if (tmpDir.exists())
//                        IO.delete(tmpDir);
//                    Log.warn("Can't reuse "+old+", using "+tmpDir);
//                }
//            }
//
//            if (!tmpDir.exists())
//                tmpDir.mkdir();
//
//            //If the parent is not a work directory
//            if (!isTempWorkDirectory(tmpDir))
//            {
//                tmpDir.deleteOnExit();
//                //TODO why is this here?
//                File sentinel = new File(tmpDir, ".active");
//                if(!sentinel.exists())
//                    sentinel.mkdir();
//            }
//
//            if(Log.isDebugEnabled())
//                Log.debug("Set temp dir "+tmpDir);
//            context.setTempDirectory(tmpDir);
//        }
    }


    public void unpack (WebAppContext context) throws IOException
    {

        // This is not necessary with the Mckoi web platform because the
        // apps are.

//        Resource web_app = context.getBaseResource();
        _preUnpackBaseResource = context.getBaseResource();

//        if (web_app == null)
//        {
//            String war = context.getWar();
//            if (war!=null && war.length()>0)
//                web_app = context.newResource(war);
//            else
//                web_app=context.getBaseResource();
//
//            // Accept aliases for WAR files
//            if (web_app.getAlias() != null)
//            {
//                Log.debug(web_app + " anti-aliased to " + web_app.getAlias());
//                web_app = context.newResource(web_app.getAlias());
//            }
//
//            if (Log.isDebugEnabled())
//                Log.debug("Try webapp=" + web_app + ", exists=" + web_app.exists() + ", directory=" + web_app.isDirectory());
//
//            // Is the WAR usable directly?
//            if (web_app.exists() && !web_app.isDirectory() && !web_app.toString().startsWith("jar:"))
//            {
//                // No - then lets see if it can be turned into a jar URL.
//                Resource jarWebApp = JarResource.newJarResource(web_app);
//                if (jarWebApp.exists() && jarWebApp.isDirectory())
//                    web_app= jarWebApp;
//            }
//
//            // If we should extract or the URL is still not usable
//            if (web_app.exists()  && (
//                    (context.isCopyWebDir() && web_app.getFile() != null && web_app.getFile().isDirectory()) ||
//                    (context.isExtractWAR() && web_app.getFile() != null && !web_app.getFile().isDirectory()) ||
//                    (context.isExtractWAR() && web_app.getFile() == null) ||
//                    !web_app.isDirectory())
//                            )
//            {
//                // Look for sibling directory.
//                File extractedWebAppDir = null;
//
//                if (war!=null)
//                {
//                    // look for a sibling like "foo/" to a "foo.war"
//                    File warfile=Resource.newResource(war).getFile();
//                    if (warfile!=null)
//                    {
//                        File sibling = new File(warfile.getParent(),warfile.getName().substring(0,warfile.getName().length()-4));
//                        if (sibling.exists() && sibling.isDirectory() && sibling.canWrite())
//                            extractedWebAppDir=sibling;
//                    }
//                }
//
//                if (extractedWebAppDir==null)
//                    // Then extract it if necessary to the temporary location
//                    extractedWebAppDir= new File(context.getTempDirectory(), "webapp");
//
//                if (web_app.getFile()!=null && web_app.getFile().isDirectory())
//                {
//                    // Copy directory
//                    Log.info("Copy " + web_app + " to " + extractedWebAppDir);
//                    web_app.copyTo(extractedWebAppDir);
//                }
//                else
//                {
//                    if (!extractedWebAppDir.exists())
//                    {
//                        //it hasn't been extracted before so extract it
//                        extractedWebAppDir.mkdir();
//                        Log.info("Extract " + web_app + " to " + extractedWebAppDir);
//                        Resource jar_web_app = JarResource.newJarResource(web_app);
//                        jar_web_app.copyTo(extractedWebAppDir);
//                    }
//                    else
//                    {
//                        //only extract if the war file is newer
//                        if (web_app.lastModified() > extractedWebAppDir.lastModified())
//                        {
//                            IO.delete(extractedWebAppDir);
//                            extractedWebAppDir.mkdir();
//                            Log.info("Extract " + web_app + " to " + extractedWebAppDir);
//                            Resource jar_web_app = JarResource.newJarResource(web_app);
//                            jar_web_app.copyTo(extractedWebAppDir);
//                        }
//                    }
//                }
//                web_app = Resource.newResource(extractedWebAppDir.getCanonicalPath());
//            }
//
//            // Now do we have something usable?
//            if (!web_app.exists() || !web_app.isDirectory())
//            {
//                Log.warn("Web application not found " + war);
//                throw new java.io.FileNotFoundException(war);
//            }
//
//            context.setBaseResource(web_app);
//
//            if (Log.isDebugEnabled())
//                Log.debug("webapp=" + web_app);
//        }
//
//
//
//        // Do we need to extract WEB-INF/lib?
//        if (context.isCopyWebInf())
//        {
//            Resource web_inf= web_app.addPath("WEB-INF/");
//
//            if (web_inf instanceof ResourceCollection ||
//                    web_inf.exists() &&
//                    web_inf.isDirectory() &&
//                    (web_inf.getFile()==null || !web_inf.getFile().isDirectory()))
//            {
//                File extractedWebInfDir= new File(context.getTempDirectory(), "webinf");
//                if (extractedWebInfDir.exists())
//                    IO.delete(extractedWebInfDir);
//                extractedWebInfDir.mkdir();
//                Resource web_inf_lib = web_inf.addPath("lib/");
//                File webInfDir=new File(extractedWebInfDir,"WEB-INF");
//                webInfDir.mkdir();
//
//                if (web_inf_lib.exists())
//                {
//                    File webInfLibDir = new File(webInfDir, "lib");
//                    if (webInfLibDir.exists())
//                        IO.delete(webInfLibDir);
//                    webInfLibDir.mkdir();
//
//                    Log.info("Copying WEB-INF/lib " + web_inf_lib + " to " + webInfLibDir);
//                    web_inf_lib.copyTo(webInfLibDir);
//                }
//
//                Resource web_inf_classes = web_inf.addPath("classes/");
//                if (web_inf_classes.exists())
//                {
//                    File webInfClassesDir = new File(webInfDir, "classes");
//                    if (webInfClassesDir.exists())
//                        IO.delete(webInfClassesDir);
//                    webInfClassesDir.mkdir();
//                    Log.info("Copying WEB-INF/classes from "+web_inf_classes+" to "+webInfClassesDir.getAbsolutePath());
//                    web_inf_classes.copyTo(webInfClassesDir);
//                }
//
//                web_inf=Resource.newResource(extractedWebInfDir.getCanonicalPath());
//
//                ResourceCollection rc = new ResourceCollection(web_inf,web_app);
//
//                if (Log.isDebugEnabled())
//                    Log.debug("context.resourcebase = "+rc);
//
//                context.setBaseResource(rc);
//            }
//        }
    }


    public File findWorkDirectory (WebAppContext context) throws IOException
    {
        return null;
//        if (context.getBaseResource() != null)
//        {
//            Resource web_inf = context.getWebInf();
//            if (web_inf !=null && web_inf.exists())
//            {
//               return new File(web_inf.getFile(),"work");
//            }
//        }
//        return null;
    }


    /**
     * Check if the tmpDir itself is called "work", or if the tmpDir
     * is in a directory called "work".
     * @return true if File is a temporary or work directory
     */
    public boolean isTempWorkDirectory (File tmpDir)
    {
        if (tmpDir == null)
            return false;
        if (tmpDir.getName().equalsIgnoreCase("work"))
            return true;
        File t = tmpDir.getParentFile();
        if (t == null)
            return false;
        return (t.getName().equalsIgnoreCase("work"));
    }

}
