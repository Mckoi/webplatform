/**
 * com.mckoi.mwpbase.HTMLWriter  Apr 15, 2011
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Convenience class that constructs a HTML block element that contains
 * sub-elements. Used to create formatted output to a function.
 *
 * @author Tobias Downer
 */

public class HTMLWriter {

  /**
   * The constructed HTML string.
   */
  private final StringWriter html_out_string;

  /**
   * Constructor.
   */
  public HTMLWriter() {
    html_out_string = new StringWriter();
  }

  /**
   * Returns the HTML as a string.
   */
  @Override
  public String toString() {
    return html_out_string.toString();
  }

  /**
   * Returns a HTML format version of the string.
   */
  public static String toHTMLEntity(String text) {
    if (text == null) {
      return null;
    }
    int sz = text.length();
    StringBuilder output = new StringBuilder(sz + 128);
    for (int i = 0; i < sz; ++i) {
      char c = text.charAt(i);
      if (c == '"') {
        output.append("&quot;");
      }
      else if (c == '&') {
        output.append("&amp;");
      }
      else if (c == '<') {
        output.append("&lt;");
      }
      else if (c == '>') {
        output.append("&gt;");
      }
      else {
        output.append(c);
      }
    }
    return output.toString();
  }

  /**
   * Sanity checks the string and ensures it doesn't contain any special
   * markup.
   */
  public static String sanityCheck(String ele_str) {
    int sz = ele_str.length();
    for (int i = 0; i < sz; ++i) {
      char c = ele_str.charAt(i);
      if (c == '\\' || c == '/' ||
          c == '\'' || c == '"' ||
          c == '>' || c == '<') {
        throw new RuntimeException("Invalid character '" + c + "'");
      }
    }
    return ele_str;
  }

  private static char[] hexc = new char[] {
              '0', '1', '2', '3', '4', '5', '6', '7',
              '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

  private static void percentEncode(StringBuilder b, int c) {
    b.append('%');
    int c1 = (c & 0x0F0) >> 4;
    int c2 = c & 0x00F;
    b.append(hexc[c1]);
    b.append(hexc[c2]);
  }

  /**
   * Encodes a URI string in a similar way to the JavaScript encodeURI method.
   * For example, 'hello world' is encoded to 'hello%20world'.
   */
  public static String URIEncode(String val) {
    StringBuilder b = null;
    int sz = val.length();
    for (int i = 0; i < sz; ++i) {
      char c = val.charAt(i);
      // Characters that remain the same,
      if ((c >= 'a' && c <= 'z') ||
          (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') ||
          c == '.' || c == '-' || c == '~' || c == '_' ||

          c == '!' || c == '@' || c == '#' ||
          c == '$' || c == '&' || c == '*' || c == '(' ||
          c == ')' || c == '=' || c == ':' || c == '/' ||
          c == ',' || c == ';' || c == '?' || c == '+' ||
          c == '\'') {
        if (b != null) {
          b.append(c);
        }
      }
      else {
        if (b == null) {
          b = new StringBuilder();
          b.append(val.substring(0, i));
        }
        percentEncode(b, c);
      }
    }
    if (b == null) {
      return val;
    }
    else {
      return b.toString();
    }
  }

  /**
   * Encodes a URI string in a similar way to the JavaScript encodeURIComponent
   * method.
   */
  public static String URIEncodeComponent(String val) {
    StringBuilder b = null;
    int sz = val.length();
    for (int i = 0; i < sz; ++i) {
      char c = val.charAt(i);
      // Characters that remain the same,
      if ((c >= 'a' && c <= 'z') ||
          (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') ||
          c == '.' || c == '-' || c == '~' || c == '_' ||

          c == '~' || c == '!' || c == '*' || c == '(' ||
          c == ')' || c == '\'') {
        if (b != null) {
          b.append(c);
        }
      }
      else {
        if (b == null) {
          b = new StringBuilder();
          b.append(val.substring(0, i));
        }
        percentEncode(b, c);
      }
    }
    if (b == null) {
      return val;
    }
    else {
      return b.toString();
    }
  }


  /**
   * Appends an inherited format block of text and a new-line.
   */
  public void println(Object str) {
    html_out_string.append(toHTMLEntity(str.toString()));
    // Output the newline,
    html_out_string.append("<br />");
  }

  /**
   * Appends an inherited format block of text.
   */
  public void print(Object str) {
    html_out_string.append(toHTMLEntity(str.toString()));
  }

  /**
   * Appends a block of text that is a span with the specified style and a
   * new-line.
   */
  public void println(Object str, String style) {
    html_out_string.append("<span class='");
    html_out_string.append(sanityCheck(style));
    html_out_string.append("'>");
    html_out_string.append(toHTMLEntity(str.toString()));
    html_out_string.append("</span><br />");
  }

  /**
   * Appends a block of text that is a span with the specified style.
   */
  public void print(Object str, String style) {
    html_out_string.append("<span class='");
    html_out_string.append(sanityCheck(style));
    html_out_string.append("'>");
    html_out_string.append(toHTMLEntity(str.toString()));
    html_out_string.append("</span>");
  }

  /**
   * Appends a new-line element.
   */
  public void println() {
    html_out_string.append("<br />");
  }

  /**
   * Prints the given string as a HTML block by parsing the string line by
   * line.
   */
  public void printAsHtml(String unformatted) {
    try {
      BufferedReader text_in = new BufferedReader(new StringReader(unformatted));
      String line = text_in.readLine();
      while (line != null) {
        println(line);
        line = text_in.readLine();
      }
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
  }

  /**
   * Prints the given string as a HTML block by parsing the string line by
   * line.
   */
  public void printAsHtml(String unformatted, String style) {
    try {
      BufferedReader text_in = new BufferedReader(new StringReader(unformatted));
      String line = text_in.readLine();
      while (line != null) {
        println(line, style);
        line = text_in.readLine();
      }
    }
    catch (IOException e) {
      // Should be impossible,
      throw new RuntimeException(e);
    }
  }

}
