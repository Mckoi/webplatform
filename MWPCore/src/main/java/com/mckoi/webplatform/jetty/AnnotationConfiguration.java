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
import java.util.Locale;
import org.eclipse.jetty.annotations.AnnotationParser;
import org.eclipse.jetty.annotations.ClassNameResolver;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;

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
public class AnnotationConfiguration
              extends org.eclipse.jetty.annotations.AnnotationConfiguration {

  @Override
  protected AnnotationParser createAnnotationParser() {
    // Bit of a hack here.
    // The Jetty AnnotationParser class converts the resource into a
    // java.io.File to parse out the last part of the file name. This will not
    // work in the Mckoi system because 'getFile' always returns null, and
    // it generates an error. We fix the class.
    return new MWPAnnotationParser();
  }

  
  // ---- 
  
  /**
   * Our modified AnnotationParser parses out the class name.
   */
  private static class MWPAnnotationParser extends AnnotationParser {
    
    private static final Logger LOG = Log.getLogger(AnnotationParser.class);

    @Override
    public void parse(Resource dir, ClassNameResolver resolver)
                                                        throws Exception {

        if (!dir.isDirectory() || !dir.exists() || dir.getName().startsWith("."))
            return;


        String[] files=dir.list();
        for (int f=0;files!=null && f<files.length;f++)
        {
            try 
            {
                Resource res = dir.addPath(files[f]);
                if (res.isDirectory())
                    parse(res, resolver);
                else
                {
                    String fullname = res.getName();
                    File res_file = res.getFile();
                    String filename = (res_file == null) ? fullname : res_file.getName();

                    if (isValidClassFileName(filename))
                    {
                        if ((resolver == null)|| (!resolver.isExcluded(fullname) && (!isParsed(fullname) || resolver.shouldOverride(fullname))))
                        {
                            Resource r = Resource.newResource(res.getURL());
                            scanClass(r.getInputStream());
                        }

                    }
                }
            }
            catch (Exception ex)
            {
                LOG.warn(Log.EXCEPTION,ex);
            }
        }
    }

    /**
     * Check that the given path represents a valid class file name.
     * The check is fairly cursory, checking that:
     * <ul>
     * <li> the name ends with .class</li>
     * <li> it isn't a dot file or in a hidden directory </li>
     * <li> the name of the class at least begins with a valid identifier for a class name </li>
     * </ul>
     * @param path
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
