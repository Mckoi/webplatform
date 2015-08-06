/**
 * com.mckoi.mwpbase.FileUtils  Apr 28, 2011
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2010  Diehl and Associates, Inc.
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

package com.mckoi.mwpui;

import com.mckoi.odb.util.FileInfo;
import com.mckoi.webplatform.FileRepository;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Various file utility methods.
 *
 * @author Tobias Downer
 */

public class FileUtils {

  /**
   * Turns a path/file specification into a reference to an object in the
   * file repository if it's possible to do so. For example, '.' is turned
   * into '$pwd', 'myd/test' is turned into '$pwd/myd/test/' if 'test' is
   * a directory or left as '$pwd/myd/test' otherwise.
   * <p>
   * This will always return a string of some sort, even if the object doesn't
   * exist.
   */
  public static String resolvePathSpec(
             FileRepository repository, String pwd, String loc) {

    // If 'loc' ends with "/" we will never treat the location as a file.
    boolean is_definitely_path = loc.endsWith("/");

    // If loc doesn't start with '/' then we prepend the $pwd environment var,
    if (!loc.startsWith("/")) {
      if (pwd == null) pwd = "/";
      // Make sure 'pwd' is an absolute path,
      if (!pwd.startsWith("/")) {
        pwd = "/" + pwd;
      }
      if (!pwd.endsWith("/")) {
        pwd = pwd + "/";
      }
      loc = pwd + loc;
    }

    // Get rid of the '/' at the front,
    loc = loc.substring(1);

    ArrayList<String> paths_list;

    if (loc.length() == 0) {
      paths_list = new ArrayList(0);
    }
    else {
      // Split it into path items,
      String[] paths = loc.split("\\/");
      paths_list = new ArrayList(paths.length);
      paths_list.addAll(Arrays.asList(paths));

      // Normalize the location string by removing any '..' and '.' from
      // it.
      int i = 0;
      while (i < paths_list.size()) {
        String pitem = paths_list.get(i);
        if (pitem.equals(".")) {
          paths_list.remove(i);
        }
        else if (pitem.equals("..")) {
          paths_list.remove(i);
          if (i > 0) {
            paths_list.remove(i - 1);
            --i;
          }
        }
        else {
          ++i;
        }
      }
    }

    // Create a normalized path string,
    StringBuilder normalized_path = new StringBuilder(loc.length());
    for (String pitem : paths_list) {
      normalized_path.append("/");
      normalized_path.append(pitem);
    }

    // If it's definitely a path,
    if (is_definitely_path) {
      normalized_path.append("/");
      return normalized_path.toString();
    }
    // Maybe a path or not, we need to query the repository and see,
    else {
      // The string as a file,
      String as_file = normalized_path.toString();
      // Test if it's a directory,
      normalized_path.append("/");
      String as_path = normalized_path.toString();
//      System.out.println(as_path);
      FileInfo f_info = repository.getFileInfo(as_path);
      if (f_info != null) {
        return as_path;
      }
      // Otherwise assume it's a file,
      return as_file;
    }

  }

  /**
   * Turns a path/file specification into a reference to an object in the
   * file repository if it's possible to do so. For example, '.' is turned
   * into '$pwd', 'myd/test' is turned into '$pwd/myd/test/' if 'test' is
   * a directory or left as '$pwd/myd/test' otherwise.
   * <p>
   * This will always return a string of some sort, even if the object doesn't
   * exist.
   */
  public static String resolvePathSpec(
             FileRepository repository, EnvironmentVariables env, String loc) {

    String pwd = null;
    if (!loc.startsWith("/")) {
      pwd = env.get("pwd");
    }

    return resolvePathSpec(repository, pwd, loc);

  }

  /**
   * Filters the list by the given query specification. For example, given
   * a '*.java' query the returned list will contain only the items that end
   * with .java.
   */
  public static List<FileInfo> filterFileInfoList(
                    List<FileInfo> list, boolean directories, String query) {
    // Trivial case,
    if (list == null || list.isEmpty()) {
      return list;
    }

    // Process the query string,
    // The pre and post parts of the query string,
    int query_sz = query.length();
    StringBuilder pre = new StringBuilder(query_sz);
    int post_i = query_sz;
    for (int i = 0; i < query_sz; ++i) {
      char c = query.charAt(i);
      if (c == '*' || c == '?') {
        post_i = i;
        break;
      }
      else {
        pre.append(c);
      }
    }

    // Turn the post part into a regular expression,
    boolean infi_group = false;
    int rmin = 0;
    StringBuilder pattern = new StringBuilder(query.length());
    for (int i = post_i; i < query_sz; ++i) {
      char c = query.charAt(i);
      if (c == '*') {
        infi_group = true;
      }
      else if (c == '?') {
        rmin += 1;
      }
      else {

        while (rmin > 0) {
          pattern.append(".");
          --rmin;
        }
        if (infi_group) {
          pattern.append(".*");
          infi_group = false;
        }

        if (Character.isLetter(c) || Character.isDigit(c)) {
          pattern.append(c);
        }
        else {
          pattern.append("\\Q");
          pattern.append(c);
          pattern.append("\\E");
        }
      }
    }

    while (rmin > 0) {
      pattern.append(".");
      --rmin;
    }
    if (infi_group) {
      pattern.append(".*");
      infi_group = false;
    }

//    System.out.println("regexp pattern = " + pattern.toString());

    Pattern post_pattern = Pattern.compile(pattern.toString());

    // Query the tail of the list if there's a pre string,
    int pos = 0;
    String pre_string = pre.toString();
    if (pre_string.length() > 0) {
      pos = Collections.binarySearch(
                                 list, pre_string, new FileInfoComparator());
      if (pos < 0) {
        pos = -(pos + 1);
      }
    }

    // The result of the scan,
    int sz = list.size();
    ArrayList<FileInfo> out_list = new ArrayList();
    int pre_string_sz = pre_string.length();

    while (pos < sz) {
      FileInfo sitem = list.get(pos);
      String item_name = sitem.getItemName();
      // Break condition if the prefix is not part of the item name,
      if (pre_string_sz != 0 && !item_name.startsWith(pre_string)) {
        break;
      }

      // If it's a directory, remove the appending '/'
      if (directories) {
        item_name = item_name.substring(0, item_name.length() - 1);
      }

      if (item_name.length() >= pre_string_sz) {
        if (post_pattern.matcher(
                             item_name.substring(pre_string_sz)).matches()) {
          out_list.add(sitem);
        }
      }

      ++pos;
    }

    // Return the output list,
    return out_list;

  }

  // ----------

  /**
   * Comparator for FileInfo names.
   */
  private static class FileInfoComparator implements Comparator {

    @Override
    public int compare(Object o1, Object o2) {
      // 'o2' will be a String, 'o1' will be a FileInfo,
      FileInfo f = (FileInfo) o1;
      String item_name = f.getItemName();
      String name = (String) o2;
      return item_name.compareTo(name);
    }

  }



}
