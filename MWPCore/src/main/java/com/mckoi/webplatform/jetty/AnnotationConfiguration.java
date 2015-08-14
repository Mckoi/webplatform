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

package com.mckoi.webplatform.jetty;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.annotations.AnnotationParser;
import org.eclipse.jetty.annotations.ClassNameResolver;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * An implementation of org.eclipse.jetty.annotations.AnnotationConfiguration
 * for the Mckoi web platform.
 * <p>
 * We override this class to prevent it from trying to convert a resource
 * into a java.io.File to parse out the name of the file. This does not work
 * in the Mckoi Web Platform.
 *
 * @author Tobias Downer
 */
public final class AnnotationConfiguration
              extends org.eclipse.jetty.annotations.AnnotationConfiguration {

    private static final Logger LOG = Log.getLogger(
                  org.eclipse.jetty.annotations.AnnotationConfiguration.class);

    @Override
    protected AnnotationParser createAnnotationParser() {
        // Bit of a hack here.
        // The Jetty AnnotationParser class converts the resource into a
        // java.io.File to parse out the last part of the file name. This will not
        // work in the Mckoi system because 'getFile' always returns null, and
        // it generates an error. We fix the class.
        return new MWPAnnotationParser();
    }

    /**
     * Perform scanning of classes for annotations
     * 
     * @param context
     * @throws Exception
     */
    protected void scanForAnnotations (WebAppContext context)
    throws Exception
    {
        AnnotationParser parser = createAnnotationParser();
        _parserTasks = new ArrayList<ParserTask>();

        long start = 0; 


        if (LOG.isDebugEnabled())
            LOG.debug("Annotation scanning commencing: webxml={}, metadatacomplete={}, configurationDiscovered={}, multiThreaded={}, maxScanWait={}", 
                      context.getServletContext().getEffectiveMajorVersion(), 
                      context.getMetaData().isMetaDataComplete(),
                      context.isConfigurationDiscovered(),
                      isUseMultiThreading(context),
                      getMaxScanWait(context));

             
        parseContainerPath(context, parser);
        //email from Rajiv Mordani jsrs 315 7 April 2010
        //    If there is a <others/> then the ordering should be
        //          WEB-INF/classes the order of the declared elements + others.
        //    In case there is no others then it is
        //          WEB-INF/classes + order of the elements.
        parseWebInfClasses(context, parser);
        parseWebInfLib (context, parser); 
        
        start = System.nanoTime();
        
        //execute scan, either effectively synchronously (1 thread only), or asynchronously (limited by number of processors available) 
        final MultiException me = new MultiException();
    
        for (final ParserTask p:_parserTasks)
        {
            try
            {
                p.call();
            }
            catch (Exception e)
            {
                me.add(e);
            }
        }
       
        if (LOG.isDebugEnabled())
        {
            for (ParserTask p:_parserTasks)
                LOG.debug("Scanned {} in {}ms", p.getResource(), TimeUnit.MILLISECONDS.convert(p.getStatistic().getElapsed(), TimeUnit.NANOSECONDS));

            LOG.debug("Scanned {} container path jars, {} WEB-INF/lib jars, {} WEB-INF/classes dirs in {}ms for context {}",
                    _containerPathStats.getTotal(), _webInfLibStats.getTotal(), _webInfClassesStats.getTotal(),
                    (TimeUnit.MILLISECONDS.convert(System.nanoTime()-start, TimeUnit.NANOSECONDS)),
                    context);
        }

        me.ifExceptionThrow();   
    }

  // ---- 
  
  /**
   * Our modified AnnotationParser parses out the class name.
   */
  private static class MWPAnnotationParser extends AnnotationParser {

    private static final Logger LOG = Log.getLogger(AnnotationParser.class);

    /**
     * Parse all classes in a directory
     * 
     * @param dir
     * @param resolver
     * @throws Exception
     */
    protected void parseDir (Set<? extends Handler> handlers, Resource dir, ClassNameResolver resolver)
    throws Exception
    {
        //skip dirs whose name start with . (ie hidden)
        if (!dir.isDirectory() || !dir.exists() || dir.getName().startsWith("."))
            return;

        if (LOG.isDebugEnabled()) {LOG.debug("Scanning dir {}", dir);};

        MultiException me = new MultiException();
        
        String[] files=dir.list();
        for (int f=0;files!=null && f<files.length;f++)
        {
            Resource res = dir.addPath(files[f]);
            if (res.isDirectory()) {
                parseDir(handlers, res, resolver);
            }
            else
            {
                //we've already verified the directories, so just verify the class file name
                String fullname = res.getName();
                File res_file = res.getFile();
                String filename = (res_file == null) ? fullname : res_file.getName();
                if (isValidClassFileName(filename))
                {
                    try
                    {
                        String name = res.getName();
                        if ((resolver == null)|| (!resolver.isExcluded(name) && (!isParsed(name) || resolver.shouldOverride(name))))
                        {
                            Resource r = Resource.newResource(res.getURL());
                            if (LOG.isDebugEnabled()) {LOG.debug("Scanning class {}", r);};
                            scanClass(handlers, dir, r.getInputStream());
                        }
                    }                  
                    catch (Exception ex)
                    {
                        if (LOG.isDebugEnabled()) LOG.debug("Error scanning file "+files[f], ex);
                        me.add(new RuntimeException("Error scanning file "+files[f],ex));
                    }
                }
                else
                {
                   if (LOG.isDebugEnabled()) LOG.debug("Skipping scan on invalid file {}", res);
                }
            }
        }

        me.ifExceptionThrow();
    }

    /**
     * Check that the given path represents a valid class file name.
     * The check is fairly cursory, checking that:
     * <ul>
     * <li> the name ends with .class</li>
     * <li> it isn't a dot file or in a hidden directory </li>
     * <li> the name of the class at least begins with a valid identifier for a class name </li>
     * </ul>
     * @param name
     * @return
     */
    private boolean isValidClassFileName (String name)
    {
        //no name cannot be valid
        if (name == null || name.length()==0)
            return false;

        //skip anything that is not a class file
        if (!name.toLowerCase(Locale.ENGLISH).endsWith(".class"))
        {
            if (LOG.isDebugEnabled()) LOG.debug("Not a class: {}",name);
            return false;
        }

        //skip any classfiles that are not a valid java identifier
        int c0 = 0;      
        int ldir = name.lastIndexOf('/', name.length()-6);
        c0 = (ldir > -1 ? ldir+1 : c0);
        if (!Character.isJavaIdentifierStart(name.charAt(c0)))
        {
            if (LOG.isDebugEnabled()) LOG.debug("Not a java identifier: {}"+name);
            return false;
        }
   
        return true;
    }

  }

}
