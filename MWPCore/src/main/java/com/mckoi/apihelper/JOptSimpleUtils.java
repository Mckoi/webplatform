/**
 * com.mckoi.apihelper.JOptSimpleUtils  Dec 4, 2012
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

package com.mckoi.apihelper;

import com.mckoi.lib.joptsimple.OptionDescriptor;
import com.mckoi.lib.joptsimple.OptionParser;
import com.mckoi.util.StyledPrintWriter;
import java.util.*;

/**
 * Utility methods for joptsimple APIs.
 *
 * @author Tobias Downer
 */

public class JOptSimpleUtils {

  /**
   * Prints the options out to a styled writer.
   */
  public static void printHelpOn(OptionParser parser, StyledPrintWriter out) {

    // Get the options,
    Map<String, ? extends OptionDescriptor> options =
                                                parser.getRecognizedOptions();

    // Make a unique set of options,
    List<DescValue> descriptors = new ArrayList();
    for (OptionDescriptor desc : options.values()) {
      DescValue desc_value = new DescValue(desc);
      if (!descriptors.contains(desc_value)) {
        descriptors.add(desc_value);
      }
    }

    // Sort it,
    Collections.sort(descriptors);

    StringBuilder col1 = new StringBuilder();
    StringBuilder col2 = new StringBuilder();
    
    for (DescValue value : descriptors) {
      OptionDescriptor od = value.getOptionDescriptor();
      
      Collection<String> switches = od.options();
      col1.setLength(0);
      col2.setLength(0);

      boolean first = true;
      for (String s : switches) {
        if (!first) {
          col1.append(", ");
        }
        if (s.length() > 1) {
          col1.append("--");
        }
        else {
          col1.append("-");
        }
        col1.append(s);
        first = false;
      }

      // If this switch accepts arguments,
      if (od.acceptsArguments()) {
        col1.append(' ');
        char surround_style_open = '<';
        char surround_style_close = '>';
        // Change style if not arguments not required,
        if (!od.requiresArgument()) {
          surround_style_open = '[';
          surround_style_close = ']';
        }
        col1.append(surround_style_open);
        
        String arg_type = od.argumentTypeIndicator();
        String arg_desc = od.argumentDescription();
        if (arg_type == null) arg_type = "";
        if (arg_desc == null || arg_desc.equals("")) arg_desc = "arg";
        if (arg_type.length() > 0) {
          col1.append(arg_type);
          if (arg_desc.length() > 0);
          col1.append(": ");
        }
        col1.append(arg_desc);
        col1.append(surround_style_close);
      }

      // Make the description column,
      String desc = od.description();
      if (desc == null) desc = "";
      col2.append(desc);

      final int COL1_WIDTH = 30;
      final int COL2_WIDTH = 60;

      // Format into columns,
      String[] col1_lines = TextUtils.splitIntoLines(
              col1.toString(), COL1_WIDTH - 1, -1);
      String[] col2_lines = TextUtils.splitIntoLines(
              col2.toString(), COL2_WIDTH, 0);

      // Lay out both the columns to our styled writer,
      int i = 0;
      boolean first_line = true;
      while (true) {
        boolean col1_end = false;
        boolean is_col2 = (i < col2_lines.length);
        if (i < col1_lines.length) {
          String section;
          if (!first_line) {
            section = " " + col1_lines[i];
          }
          else {
            section = col1_lines[i];
          }
          // If there is a column 2 for this line
          if (is_col2) {
            // Pad it out appropriately,
            out.print(section + TextUtils.pad(COL1_WIDTH - section.length()));
          }
          else {
            out.println(section);
          }
        }
        else {
          if (is_col2) {
            out.print(TextUtils.pad(COL1_WIDTH));
          }
          col1_end = true;
        }
        if (is_col2) {
          String section = col2_lines[i];
          out.println(section, "info");
        }
        else {
          if (col1_end) {
            // If both columns have ended,
            break;
          }
        }
        ++i;
        first_line = false;
      }
      
    }

  }

  /**
   * Splits a command line string into a String[] array.
   */
  public static String[] toArgs(String cline) {
    return TextUtils.splitCommandLineAndUnquote(cline);
  }


  public static class DescValue implements Comparable<DescValue> {

    private final String primary_option;
    private final OptionDescriptor option_descriptor;

    public DescValue(OptionDescriptor descriptor) {
      this.option_descriptor = descriptor;
      this.primary_option = descriptor.options().iterator().next();
    }

    public String getPrimaryOption() {
      return primary_option;
    }

    public OptionDescriptor getOptionDescriptor() {
      return option_descriptor;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final DescValue other = (DescValue) obj;
      if ((this.primary_option == null) ? (other.primary_option != null) : !this.primary_option.equals(other.primary_option)) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 71 * hash + (this.primary_option != null ? this.primary_option.hashCode() : 0);
      return hash;
    }

    @Override
    public int compareTo(DescValue o) {
      return primary_option.compareTo(o.primary_option);
    }

  }
  
  
}
