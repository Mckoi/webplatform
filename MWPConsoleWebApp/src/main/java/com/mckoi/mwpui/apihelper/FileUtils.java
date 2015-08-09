/**
 * com.mckoi.mwpui.apihelper.FileUtils  Oct 11, 2012
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

package com.mckoi.mwpui.apihelper;

import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Various file utility methods for supporting file APIs..
 *
 * @author Tobias Downer
 */

public class FileUtils {

  /**
   * Given a FileRepository and FileName directory, returns a sorted
   * (lexicographically) list of all files in the directory. Returns an empty
   * list if no files in the directory. Throws an exception if the FileName
   * is a file reference.
   * <p>
   * Assumes the given FileName repository id is the same as the given
   * repository id.
   * 
   * @param fs
   * @param name
   * @return 
   */
  public static List<FileInfo> getFileList(FileRepository fs, FileName name) {

    if (!name.isDirectory()) {
      throw new RuntimeException("Not a directory reference");
    }
    
    String path_file = name.getPathFile();
    List<FileInfo> fi_list = fs.getFileList(path_file);
    return fi_list;

  }

  /**
   * Given a FileRepository and FileName directory, returns a sorted
   * (lexicographically) list of all sub-directories in the directory. Returns
   * an empty list if no sub-directories in the directory. Throws an exception
   * if the FileName is a file reference.
   * <p>
   * Assumes the given FileName repository id is the same as the given
   * repository id.
   * 
   * @param fs
   * @param name
   * @return 
   */
  public static List<FileInfo> getDirectoryList(
                                            FileRepository fs, FileName name) {
    
    if (!name.isDirectory()) {
      throw new RuntimeException("Not a directory reference");
    }
    
    String path_file = name.getPathFile();
    List<FileInfo> fi_list = fs.getSubDirectoryList(path_file);
    return fi_list;

  }

  /**
   * Converts an array of FileInfo objects into a list of FileInfo objects
   * sorted lexicographically.
   * 
   * @param arr
   * @return 
   */
  public static List<FileInfo> asSortedFileInfoList(FileInfo[] arr) {

    ArrayList<FileInfo> out_list = new ArrayList<>(arr.length);
    out_list.addAll(Arrays.asList(arr));
    Collections.sort(out_list, new FileInfoNameComparator());
    return out_list;

  }

  /**
   * Assuming list1 and list2 are sorted lexicographically, produces a sorted
   * list of unique items that are in either list.
   * 
   * @param list1
   * @param list2
   * @return 
   */
  public static Collection<FileInfo> mergeFileLists(
                      Collection<FileInfo> list1, Collection<FileInfo> list2) {

    Iterator<FileInfo> i1 = list1.iterator();
    Iterator<FileInfo> i2 = list2.iterator();

    // Trivial cases,
    if (!i1.hasNext()) {
      return list2;
    }
    if (!i2.hasNext()) {
      return list1;
    }

    ArrayList<FileInfo> out_list = new ArrayList<>(128);

    FileInfo top1 = null;
    FileInfo top2 = null;
    Comparator<FileInfo> c = new FileInfoNameComparator();

    int iteration = 0;

    while (true) {

      if (top1 == null && i1.hasNext()) {
        top1 = i1.next();
      }
      if (top2 == null && i2.hasNext()) {
        top2 = i2.next();
      }
      
      // Insert the smallest one,
      if (top1 == null) {
        if (top2 == null) {
          // End condition,
          return out_list;
        }
        else {
          out_list.add(top2);
          top2 = null;
        }
      }
      else {
        if (top2 == null) {
          out_list.add(top1);
          top1 = null;
        }
        else {
          // Neither are null, so compare,
          int comp = c.compare(top1, top2);
          if (comp < 0) {
            // top1 is less than top2,
            out_list.add(top1);
            top1 = null;
          }
          else if (comp > 0) {
            // top1 is greater than top2,
            out_list.add(top2);
            top2 = null;
          }
          else {
            // Equal, so add only one to the output list,
            out_list.add(top1);
            top1 = null;
            top2 = null;
          }
        }
      }

      ++iteration;
      if (iteration > 2048) {
        throw new RuntimeException("File list size limit reached (2048)");
      }

    }

  }

  /**
   * Filters the given sorted (lexicographically) FileInfo list by the given
   * wild card specification. The wild card string represents a search filter
   * where a '*' character will match against any number of characters and a
   * '?' character will match against a single character.
   * <p>
   * Returns a Collection of FileInfo objects from the source list. The
   * collection arranges the FileInfo objects lexicographically.
   * 
   * @param list
   * @param expr
   * @return 
   */
  public static Collection<FileInfo> filterByWildCardExpr(
                               final List<FileInfo> list, final String expr) {

    // Trivial case,
    if (list == null || list.isEmpty()) {
      return list;
    }

    // Process the query string,
    // The pre and post parts of the query string,
    int query_sz = expr.length();
    StringBuilder pre = new StringBuilder(query_sz);
    int post_i = query_sz;
    for (int i = 0; i < query_sz; ++i) {
      char c = expr.charAt(i);
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
    StringBuilder pattern = new StringBuilder(expr.length());
    for (int i = post_i; i < query_sz; ++i) {
      char c = expr.charAt(i);
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
//      infi_group = false;
    }

    final String post_pattern_str = pattern.toString();

    // Query the tail of the list if there's a pre string,
    int pos = 0;
    final String pre_string = pre.toString();
    if (pre_string.length() > 0) {
      pos = Collections.binarySearch(
                                 list, pre_string, new FileInfoToStringNameComparator());
      if (pos < 0) {
        pos = -(pos + 1);
      }
    }
    else {
      // If our search pattern is '.*' then just return the original list,
      if (post_pattern_str.equals(".*")) {
        return list;
      }
    }

    final Pattern post_pattern = Pattern.compile(post_pattern_str);
    final int pre_string_sz = pre_string.length();

    // Ok, we now have;
    //    A position to start the scan (pos)
    //    A String that always prepends a match (pre_string)
    //    A Matcher for anything past the pre-string (post_pattern)

    // The filter for matching a file with the wild card,
    FileInfoFilter match_filter = new FileInfoFilter() {

      @Override
      public boolean match(FileInfo file) {
        String item_name = file.getItemName();
        // If it's a directory, remove the appending '/'
        if (file.isDirectory()) {
          item_name = item_name.substring(0, item_name.length() - 1);
        }

        if (item_name.length() >= pre_string_sz) {
          if (post_pattern.matcher(
                              item_name.substring(pre_string_sz)).matches()) {
            return true;
          }
        }
        return false;
      }

    };
    
    // The filter for when we know that no other elements past the point will
    // match.
    FileInfoFilter end_filter = new FileInfoFilter() {

      @Override
      public boolean match(FileInfo file) {
        String item_name = file.getItemName();
        return (pre_string_sz != 0 && !item_name.startsWith(pre_string));
      }

    };
    
    // Return the filtered collection,
    return new FilteredFileInfoCollection(
                    list.subList(pos, list.size()), match_filter, end_filter);

  }

  /**
   * Sorts the given Collection of FileInfo by 'timestamp', 'size' or 'mime'
   * type. This has a limit on the size of items that may be sorted, and
   * throws a RuntimeException if it is exceeded. This will not modify the
   * original list or collection.
   * 
   * @param collection
   * @param type
   * @return 
   */
  public static List<FileInfo> sortFileInfoListBy(
                               Collection<FileInfo> collection, String type) {

    // First copy into an array list,
    ArrayList<FileInfo> out_list = new ArrayList<>(128);
    int i = 0;
    for (FileInfo fi : collection) {
      out_list.add(fi);
      ++i;
      if (i > 2048) {
        throw new RuntimeException("File list size limit reached (2048)");
      }
    }

    Comparator<FileInfo> c;
    switch (type) {
      case "name":
        c = new FileInfoNameComparator();
        break;
      case "timestamp":
        c = new FileInfoModifyTimeComparator();
        break;
      case "mime":
        c = new FileInfoMimeComparator();
        break;
      case "size":
        c = new FileInfoSizeComparator();
        break;
      default:
        throw new RuntimeException("Unknown sort type");
    }

    // Sort the list and return,
    Collections.sort(out_list, c);
    return out_list;

  }

  // -----

  /**
   * A FileInfo Collection implementation that matches FileInfo objects against
   * some criteria.
   */
  public static class FilteredFileInfoCollection
                                        extends AbstractCollection<FileInfo> {

    private final List<FileInfo> list;
    private final int list_size;
    private final FileInfoFilter match_filter;
    private final FileInfoFilter end_filter;

    /**
     * Constructor.
     * 
     * @param list
     * @param match_filter
     * @param end_filter
     */
    public FilteredFileInfoCollection(List<FileInfo> list,
                     FileInfoFilter match_filter, FileInfoFilter end_filter) {

      // Assert
      if (list == null) throw new NullPointerException();
      if (match_filter == null) throw new NullPointerException();
      
      this.list = list;
      this.list_size = list.size();
      this.match_filter = match_filter;
      this.end_filter = end_filter;
    }

    

    @Override
    public Iterator<FileInfo> iterator() {
      return new Iterator<FileInfo>() {

        int index = 0;
        int next_match = -1;

        private void findNextMatch() {
          if (next_match == -1) {
            while (index < list_size) {
              FileInfo f = list.get(index);
              // End condition,
              if (end_filter != null && end_filter.match(f)) {
                next_match = -2;
                return;
              }
              ++index;
              if (match_filter.match(f)) {
                next_match = index - 1;
                return;
              }
            }
            // End condition,
            next_match = -2;
          }
        }

        @Override
        public boolean hasNext() {
          findNextMatch();
          return next_match >= 0;
        }

        @Override
        public FileInfo next() {
          findNextMatch();
          if (next_match >= 0) {
            FileInfo fi = list.get(next_match);
            next_match = -1;
            return fi;
          }
          throw new NoSuchElementException();
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

      };
    }

    @Override
    public int size() {
      // NOTE: This could be costly!
      int count = 0;
      for (FileInfo f : list) {
        if (end_filter != null && end_filter.match(f)) {
          return count;
        }
        if (match_filter.match(f)) {
          ++count;
        }
      }
      return count;
    }

    @Override
    public boolean isEmpty() {
      // NOTE: This could be costly!
      // Look for the first matching entry,
      for (FileInfo f : list) {
        if (end_filter != null && end_filter.match(f)) {
          return false;
        }
        if (match_filter.match(f)) {
          return true;
        }
      }
      return false;
    }

  }

  /**
   * A filter that either matches the given FileInfo or does not. Returns
   * true if the filter matches.
   */
  public static interface FileInfoFilter {
    boolean match(FileInfo file);
  }

  /**
   * Comparator for FileInfo names.
   */
  public static class FileInfoToStringNameComparator
                                              implements Comparator<Object> {

    @Override
    public int compare(Object o1, Object o2) {
      // 'o2' will be a String, 'o1' will be a FileInfo,
      FileInfo f = (FileInfo) o1;
      String item_name = f.getItemName();
      String name = (String) o2;
      return item_name.compareTo(name);
    }

  }

  /**
   * Comparator for FileInfo names.
   */
  public static class FileInfoNameComparator implements Comparator<FileInfo> {

    @Override
    public int compare(FileInfo o1, FileInfo o2) {
      return o1.getItemName().compareTo(o2.getItemName());
    }

  }

  /**
   * Comparator for FileInfo timestamps.
   */
  public static class FileInfoModifyTimeComparator
                                             implements Comparator<FileInfo> {

    @Override
    public int compare(FileInfo o1, FileInfo o2) {
      long time1 = o1.getLastModified();
      long time2 = o2.getLastModified();
      if (time1 > time2) {
        return 1;
      }
      if (time1 < time2) {
        return -1;
      }
      return 0;
    }

  }

  /**
   * Comparator for FileInfo mime types (lexicographical).
   */
  public static class FileInfoMimeComparator implements Comparator<FileInfo> {

    @Override
    public int compare(FileInfo o1, FileInfo o2) {
      String m1 = o1.getMimeType();
      String m2 = o2.getMimeType();
      if (m1 == null) m1 = "";
      if (m2 == null) m2 = "";
      return m1.compareTo(m2);
    }

  }

  /**
   * Comparator for FileInfo size.
   */
  public static class FileInfoSizeComparator implements Comparator<FileInfo> {

    @Override
    public int compare(FileInfo o1, FileInfo o2) {
      // Directories don't have a size, so handle these appropriately,
      boolean o1_isdir = o1.isDirectory();
      boolean o2_isdir = o2.isDirectory();
      if (o1_isdir) {
        if (o2_isdir) {
          return 0;
        }
        else {
          return -1;
        }
      }
      else if (o2_isdir) {
        return 1;
      }

      // Files with a size,
      long size1 = o1.getDataFile().size();
      long size2 = o2.getDataFile().size();
      if (size1 > size2) {
        return 1;
      }
      if (size1 < size2) {
        return -1;
      }
      return 0;
    }

  }

}
