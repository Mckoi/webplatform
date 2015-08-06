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

package com.mckoi.mwpui.nodejs;

import com.mckoi.mwpui.CommandWriter;
import com.mckoi.mwpui.apihelper.UnixTerminalFormatter;
import com.mckoi.mwpui.apihelper.UnixTerminalStyledText;
import java.util.Iterator;

/**
 *
 * @author Tobias Downer
 */
public class NodeNativeMwpConsole {

  private final UnixTerminalFormatter formatter = new UnixTerminalFormatter();


  public Object write(Object thiz, Object... args) {

    if (args.length < 2) {
      return Boolean.TRUE;
    }
    String dest = args[0].toString();
    String content = args[1].toString();

    CommandWriter out = GJSRuntime.system().getSharedState().getCommandWriter();

    Iterator<UnixTerminalStyledText> section_iterator = formatter.parse(content);
    while (section_iterator.hasNext()) {
      UnixTerminalStyledText section = section_iterator.next();

      if (section.endsInNewline()) {
        if (!section.hasText()) {
          out.println();
        }
        else {
          if (section.hasStyles()) {
            out.println(section.getText(), section.getStyles());
          }
          else {
            out.println(section.getText());
          }
        }
      }
      else {
        if (section.hasStyles()) {
          out.print(section.getText(), section.getStyles());
        }
        else {
          out.print(section.getText());
        }
      }
    }

    return Boolean.TRUE;

  }
  
}
