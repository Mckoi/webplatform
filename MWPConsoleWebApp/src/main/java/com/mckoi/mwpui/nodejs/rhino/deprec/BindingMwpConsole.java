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

package com.mckoi.mwpui.nodejs.rhino.deprec;

import com.mckoi.mwpui.CommandWriter;
import com.mckoi.mwpui.JSWrapSCommand;
import com.mckoi.mwpui.NodeJSWrapSCommand;
import com.mckoi.mwpui.apihelper.UnixTerminalFormatter;
import com.mckoi.mwpui.apihelper.UnixTerminalStyledText;
import java.util.Collection;
import java.util.Iterator;
import org.mozilla.javascript.*;

/**
 * A binding to Mckoi Web Platform console functions.
 *
 * @author Tobias Downer
 */
public class BindingMwpConsole extends NativeObject {

  private final CommandWriter out;
  private final UnixTerminalFormatter formatter = new UnixTerminalFormatter();

  public BindingMwpConsole(CommandWriter out) {
    this.out = out;
  }

  @Override
  public String getClassName() {
    return "mwpconsole";
  }

  public BindingMwpConsole init(Scriptable scope) {
    ScriptRuntime.setBuiltinProtoAndParent(
                                  this, scope, TopLevel.Builtins.Object);
    int ro_attr = PERMANENT | READONLY;

    // Define the function invoke callback function,
    defineProperty("write", new WriteFun().init(scope), ro_attr);

    return this;
  }

  // ------

  public class WriteFun extends RhinoFunction {
    
    @Override
    public RhinoFunction init(Scriptable scope) {
      setFunctionName("write");
      return super.init(scope);
    }

    @Override
    public Object call(Context cx, Scriptable scope,
                                      Scriptable thisObj, Object[] args) {
      
      if (args.length < 2) {
        return Boolean.TRUE;
      }
      String dest = JSWrapSCommand.jsToString(args[0]);
      String content = JSWrapSCommand.jsToString(args[1]);

//      // 'dest' will be either err or out.
//      String global_style = null;
//      if (dest.equals("err")) {
//        global_style = "error";
//      }

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
      
//      // Look for terminal color style codes,
//      if (content.indexOf("\u001b[") >= 0) {
//        out.print("Oh, we found some control codes to work out!", "error");
//        out.print(" size = " + sz, "error");
//      }
//      // Otherwise output as straight colored format,
//      else {
//        // If there's a line break then 'println' it.
//        int last = 0;
//        for (int i = 0; i < sz; ++i) {
//          if (content.charAt(i) == '\n') {
//            String part = content.substring(last, i);
//            last = i + 1;
//            if (part.length() > 0) {
//              out.println(part, global_style);
//            }
//            else {
//              out.println();
//            }
//          }
//        }
//        if (last < sz) {
//          String part = content.substring(last, sz);
//          out.print(part, global_style);
//        }
//      }

      return Boolean.TRUE;
    }

  }

}
