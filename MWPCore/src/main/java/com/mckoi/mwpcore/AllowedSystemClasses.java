/**
 * com.mckoi.mwpcore.AllowedSystemClasses  Feb 28, 2012
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

package com.mckoi.mwpcore;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

/**
 * A class validator implementation that determines system classes that may be
 * safely accessed by a user context.
 *
 * @author Tobias Downer
 */

class AllowedSystemClasses implements ClassNameValidator {

  /**
   * System packages that are allowed.
   */

  // Whitelisted packages (eg. "com.mckoi" will match "com.mckoi.MyObject" but
  // not "com.mckoi.data.MyData").
  private final HashSet<String> SPACKAGE_WHITELIST = new HashSet();
  // Individual whitelisted classes (eg. "com.mckoi.MyObject")
  private final HashSet<String> SCLASS_WHITELIST = new HashSet();
//  // Global whitelisted packages (eg. "com.mckoi" will match
//  // "com.mckoi.MyObject" and "com.mckoi.data.MyData").
//  private static final HashSet<String> SGLOB_PACKAGE_WHITELIST = new HashSet();

  private boolean loaded = false;

  /**
   * Returns true if the class is white listed.
   */
  private boolean isWhiteListedClass(String name, boolean report_fail) {
    String packge;
    int p = name.lastIndexOf('.');
    if (p == -1) {
      packge = ""; //name;
    }
    else {
      packge = name.substring(0, p);
    }

    // Access to the core taglib classes are hard coded,
    if (name.startsWith("org.apache.taglibs.standard.")) {
      return true;
    }

    else if (SPACKAGE_WHITELIST.contains(packge)) {
      return true;
    }
    else if (SCLASS_WHITELIST.contains(name)) {
      return true;
    }
    // Report the failure?
    if (report_fail) {
//      System.err.println("FAILED ON: " + name);
//    new Error().printStackTrace();
    }
    return false;
  }

  /**
   * Returns true if the resource name is white listed.
   */
  private boolean isWhiteListedResource(String name, boolean report_fail) {
    String packge;
    int p = name.lastIndexOf('/');
    if (p == -1) {
      packge = ""; //name;
    }
    else {
      packge = name.substring(0, p);
    }

    // Access to the core taglib classes are hard coded,
    if (name.startsWith("org/apache/taglibs/standard/")) {
      return true;
    }

    // Convert the packge name,
    packge = packge.replace('/', '.');

    if (SPACKAGE_WHITELIST.contains(packge)) {
      return true;
    }
    // Report the failure?
    if (report_fail) {
//      System.err.println("FAILED ON: " + name);
//    new Error().printStackTrace();
    }
    return false;
  }

  /**
   * Loads and parses the classes policy from the given input stream.
   */
  public void loadFrom(Reader reader) throws IOException {
    if (loaded) {
      throw new RuntimeException("Already loaded");
    }
    loaded = true;

    ClassSetInterpreter i = new ClassSetInterpreter(reader);
    i.parse();
  }

  public void loadFrom(URL allowed_sys_classes_url) throws IOException {
    URLConnection c = allowed_sys_classes_url.openConnection();
    c.connect();
    InputStream instream = c.getInputStream();
    Reader reader = new InputStreamReader(instream, "UTF-8");
    
    loadFrom(reader);
  }


  // ----- Implemented from ClassNameValidator -----

  @Override
  public boolean isAcceptedClass(String class_name) {
    return isWhiteListedClass(class_name, true);
  }

  @Override
  public boolean isAllowedResource(String resource_name) {
    return isWhiteListedResource(resource_name, true);
  }

  
  // -----
  
  private class ClassSetInterpreter {

    private final StreamTokenizer t;

    private ClassSetInterpreter(Reader reader) throws IOException {
      t = new StreamTokenizer(reader);
      t.slashSlashComments(true);
      t.slashStarComments(true);
    }

    private RuntimeException parseException(String msg) {
      return new RuntimeException("Line " + t.lineno() + ": " + msg);
    }

    private String next() throws IOException {
      int ttype = t.nextToken();
      if (ttype == StreamTokenizer.TT_EOF) {
        throw parseException("Unexpected EOF");
      }
      else if (ttype == StreamTokenizer.TT_WORD) {
        return t.sval;
      }
      else if (ttype == '\"') {
        return t.sval;
      }
      else if (ttype == StreamTokenizer.TT_NUMBER) {
        return Double.toString(t.nval);
      }
      else if (ttype == StreamTokenizer.TT_EOL) {
        throw parseException("Unexpected EOL");
      }
      else {
        return Character.toString((char) t.ttype);
      }
    }

    private void consume(String tok) throws IOException {
      String val = next();
      if (val.equals(tok)) {
        return;
      }
      throw parseException("Expected: " + tok);
    }

    /**
     * Parse a set.
     */
    private void parseSet(final Set<String> set) throws IOException {
      consume("{");
      int delim_count = 1;
      while (true) {
        // The list item,
        String tok = next();
        // Skip any commas,
        while (tok.equals(",")) {
          ++delim_count;
          tok = next();
        }
        // List end token,
        if (tok.equals("}")) {
          return;
        }

        // Pendantic check;
        // Didn't parse any deliminating commas?
        if (delim_count == 0) {
          throw parseException("Expecting comma");
        }
        delim_count = 0;

        // Otherwise it must be a list item,
        String list_item = tok;
        // Add to the set,
        set.add(list_item);

      }
    }

    /**
     * Parse the input source.
     */
    private void parse() throws IOException {

      while (true) {
        t.nextToken();
        if (t.ttype == StreamTokenizer.TT_EOF) {
          // Done.
          break;
        }

        // Either 'packages' or 'classes'
        if (t.sval.equals("packages")) {
          parseSet(SPACKAGE_WHITELIST);
        }
        else if (t.sval.equals("classes")) {
          parseSet(SCLASS_WHITELIST);
        }
        else {
          throw parseException("Unexpected: " + t.sval);
        }
      }

    }

  }

}
