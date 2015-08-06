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

package com.mckoi.mwpui.apihelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses a string that would have been piped to a Unix terminal and produces
 * a collection of UnixTerminalStyledText objects that describe the runs of
 * text and how they should be styled, appropriate to be formatted in a
 * tree document format such as HTML.
 *
 * @author Tobias Downer
 */
public class UnixTerminalFormatter {

  private final List<String> style_stack = new ArrayList();
  private String last_style = null;
  
  private final static Map<String, String> STYLE_MAP;
  static {
    Map<String, String> sm = new HashMap();
    sm.put("1",  "+smbold");
    sm.put("3",  "+smitalic");
    sm.put("4",  "+smunderline");
    sm.put("7",  "+sminverse");
    sm.put("22", "-smbold");
    sm.put("23", "-smitalic");
    sm.put("24", "-smunderline");
    sm.put("27", "-sminverse");
    
    // Unix foreground colors (terminated with 39)
    sm.put("39", "!f");
    sm.put("30", "fuxblack");
    sm.put("31", "fuxred");
    sm.put("32", "fuxgreen");
    sm.put("33", "fuxyellow");
    sm.put("34", "fuxblue");
    sm.put("35", "fuxmagenta");
    sm.put("36", "fuxcyan");
    sm.put("37", "fuxlightgray");
    sm.put("90", "fuxdarkgray");
    sm.put("91", "fuxlightred");
    sm.put("92", "fuxlightgreen");
    sm.put("93", "fuxlightyellow");
    sm.put("94", "fuxlightblue");
    sm.put("95", "fuxlightmagenta");
    sm.put("96", "fuxlightcyan");
    sm.put("97", "fuxwhite");

    // Unix background colors (terminated with 49)
    sm.put("49", "!b");
    sm.put("40", "buxblack");
    sm.put("41", "buxred");
    sm.put("42", "buxgreen");
    sm.put("43", "buxyellow");
    sm.put("44", "buxblue");
    sm.put("45", "buxmagenta");
    sm.put("46", "buxcyan");
    sm.put("47", "buxlightgray");
    sm.put("100", "buxdarkgray");
    sm.put("101", "buxlightred");
    sm.put("102", "buxlightgreen");
    sm.put("103", "buxlightyellow");
    sm.put("104", "buxlightblue");
    sm.put("105", "buxlightmagenta");
    sm.put("106", "buxlightcyan");
    sm.put("107", "buxwhite");

    STYLE_MAP = Collections.unmodifiableMap(sm);
  }
  
  
  /**
   * Parses a string that contains textual content. This parser looks for
   * newline '\n' and Unix style formatting codes and outputs a list that
   * iterates over the text content between the styles.
   * 
   * @param content
   * @return 
   */
  public Iterator<UnixTerminalStyledText> parse(String content) {
    return new UnixTerminalTextIterator(content);
  }

  public class UnixTerminalTextIterator implements Iterator<UnixTerminalStyledText> {

    private final String content;
    private int index;

    public UnixTerminalTextIterator(String content) {
      this.content = content;
      this.index = 0;
    }

    @Override
    public boolean hasNext() {
      return index < content.length();
    }

    @Override
    public UnixTerminalStyledText next() {

      StringBuilder content_run = null;

      // Scan to the next delimiter
      int text_start = index;

      while (true) {

        String delimiter_token = goToNext();
        if (delimiter_token == null) {
          // End reached,
          String part = content.substring(text_start, index);
          return textRun(content_run == null ? part : content_run.append(part));
        }
        
        if (delimiter_token.equals("\n")) {
          // End of line, so create a terminal run,
          String part = content.substring(text_start,
                                           index - delimiter_token.length());
          return textRunLn(content_run == null ? part : content_run.append(part));
        }

        // Is it a style we can translate?
        if (delimiter_token.length() > 3 &&
            delimiter_token.endsWith("m")) {

          // Yes, it's a style code,
          String stylecode =
                    delimiter_token.substring(2, delimiter_token.length() - 1);
          String[] mc_styles;
          if (stylecode.indexOf(';') >= 0) {
            mc_styles = stylecode.split(";");
          }
          else {
            mc_styles = new String[] { stylecode };
          }
          // Translate style codes,
          for (int i = 0; i < mc_styles.length; ++i) {
            mc_styles[i] = STYLE_MAP.get(mc_styles[i]);
          }

          boolean stack_changed = false;
          // For each code,
          for (String mc_style : mc_styles) {
            // Unknown code so keep consuming,
            if (mc_style == null) {
              continue;
            }

            char s0 = mc_style.charAt(0);

            // Change the style stack as necessary,
            if (s0 == '!') {
              // Foreground/Background terminator,
              stack_changed = stack_changed |
                              removeFromStackPre(mc_style.charAt(1), true);
            }
            else if (s0 == '-') {
              stack_changed = stack_changed |
                              removeFromStack(mc_style.substring(1));
            }
            else if (s0 == '+') {
              stack_changed = stack_changed |
                              addToStack(mc_style.substring(1));
            }
            else {
              // Otherwise, we just add it,
              if (addToStack(mc_style)) {
                // If we added, remove any previous styles of this type
                removeFromStackPre(mc_style.charAt(0), false);
                stack_changed = true;
              }
            }
          }
          
          String part = content.substring(text_start,
                                           index - delimiter_token.length());

          // If the stack didn't change then put the part in a buffer and
          // continue.
          if (!stack_changed) {
            if (part.length() > 0) {
              if (content_run == null) {
                content_run = new StringBuilder();
              }
              content_run.append(part);
            }
          }
          else {
            if (content_run != null || part.length() > 0) {
              // Return the text run,
              return textRun(content_run == null ? part : content_run.append(part));
            }
            last_style = createStyles();
          }

          text_start = index;

        }

      }
      
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    /**
     * Consume a character.
     */
    private char consume() {
      char c = content.charAt(index);
      ++index;
      return c;
    }

    private boolean canConsume() {
      return index < content.length();
    }

    private boolean isNumeric(char c) {
      return (c >= '0' && c <= '9');
    }
    
    /**
     * Moves the index to the character after the next delimiter token from the
     * current index. Returns the delimiter token found. Returns null if the
     * end of the content was reached and it didn't end with a delimiter token.
     */
    private String goToNext() {
      while (canConsume()) {
        char c = consume();
        if (c == '\n') {
          // Newline token,
          return "\n";
        }
        else if (c == '\u001b') {
          int mark = index - 1;
          // Unix terminal command. Is it a style?
          if (!canConsume()) {
            return content.substring(mark, index);
          }
          c = consume();
          if (c != '[' || !canConsume()) {
            return content.substring(mark, index);
          }
          c = consume();
          // '?', number, ';' means it continues, otherwise terminates,
          if (c == '?' || c == ';' || isNumeric(c)) {
            while (canConsume()) {
              // Only consume numbers. Anything else terminates.
              c = consume();
              if (!isNumeric(c) && c != ';') {
                return content.substring(mark, index);
              }
            }
          }
          return content.substring(mark, index);
        }
      }
      // Reached end,
      return null;
    }

    /**
     * Removes from the stack any styles starting with the given character.
     * @param c
     * @return true if the stack was modified.
     */
    private boolean removeFromStackPre(char c, boolean can_delete_last) {
      Iterator<String> i = style_stack.iterator();
      while (i.hasNext()) {
        if (i.next().charAt(0) == c) {
          // Don't remove the last element,
          if (!i.hasNext() && !can_delete_last) {
            return false;
          }
          i.remove();
          return true;
        }
      }
      return false;
    }

    /**
     * Adds the given style to the stack, if it doesn't already exist.
     * @param mc_style
     * @return true if the stack was modified.
     */
    private boolean addToStack(String mc_style) {
      Iterator<String> i = style_stack.iterator();
      while (i.hasNext()) {
        if (i.next().equals(mc_style)) {
          return false;
        }
      }
      style_stack.add(mc_style);
      return true;
    }

    /**
     * Removes the given style from the stack, if it's on there.
     * @param mc_style
     * @return true if the stack was modified.
     */
    private boolean removeFromStack(String mc_style) {
      Iterator<String> i = style_stack.iterator();
      while (i.hasNext()) {
        if (i.next().equals(mc_style)) {
          i.remove();
          return true;
        }
      }
      return false;
    }

    private String createStyles() {
      if (style_stack.isEmpty()) {
        return null;
      }
      StringBuilder b = new StringBuilder();
      boolean first = true;
      for (String style : style_stack) {
        if (!first) {
          b.append(' ');
        }
        b.append(style);
        first = false;
      }
      return b.toString();
    }
    
    private UnixTerminalStyledText textRun(CharSequence string) {
      String cur_style = last_style;
      last_style = createStyles();
      UnixTerminalStyledText run =
                        new TextRun(string.toString(), cur_style, false);
      return run;
    }

    private UnixTerminalStyledText textRunLn(CharSequence string) {
      String cur_style = last_style;
      last_style = createStyles();
      UnixTerminalStyledText run =
                        new TextRun(string.toString(), cur_style, true);
      return run;
    }

  }

  private static class TextRun implements UnixTerminalStyledText {

    private final String content;
    private final String styles;
    private final boolean newline;

    public TextRun(String content, String styles, boolean newline) {
      this.content = content;
      this.styles = styles;
      this.newline = newline;
    }

    @Override
    public boolean endsInNewline() {
      return newline;
    }

    @Override
    public boolean hasText() {
      return content.length() > 0;
    }

    @Override
    public boolean hasStyles() {
      return styles != null;
    }

    @Override
    public String getText() {
      return content;
    }

    @Override
    public String getStyles() {
      return styles;
    }
    
  }
  
}
