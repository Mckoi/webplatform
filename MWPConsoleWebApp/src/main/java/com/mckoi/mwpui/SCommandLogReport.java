/**
 * com.mckoi.mwpbase.SCommandLogReport  Oct 7, 2011
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

import com.mckoi.webplatform.LogEventsSet;
import com.mckoi.webplatform.LogPageEvent;
import com.mckoi.webplatform.PlatformContext;
import java.text.MessageFormat;
import java.util.List;

/**
 * A server command that presents a log UI to the user.
 *
 * @author Tobias Downer
 */

public class SCommandLogReport extends DefaultSCommand {


  public SCommandLogReport(String reference) {
    super(reference);
  }


  private static String sCheck(String val) {
    if (val == null) {
      return "";
    }
    return val;
  }


  static void renderPageLink(CommandWriter out,
                      long page_number, long page_size, boolean is_current) {

    String label = Long.toString(page_number + 1);
    if (is_current) {
      out.print(label);
    }
    else {
      // Create a link that sets 'pos' to the given page,
      String callback_cmd = "pos " + Long.toString(page_number * page_size);
//      Map<String, String> link_properties =
//           Collections.singletonMap("pos",
//                                    Long.toString(page_number * page_size));
      out.print(out.createCallbackLink(label, callback_cmd));
    }

  }

  /**
   * A simple function for rendering a pagination control for navigating over
   * a series of pages in a list.
   */
  static void renderPaginationControl(CommandWriter out,
                                  long pos, int page_size, long total_size) {
    long page_count = ((total_size - 1) / page_size) + 1;
    long cur_page = pos / page_size;
    long min_page = Math.max(cur_page - 7, 1);
    long max_page = Math.min(min_page + 14, page_count - 1);

    out.print("[");
    renderPageLink(out, 0, page_size, cur_page == 0);
    for (long i = min_page; i < max_page; ++i) {
      out.print(" ");
      renderPageLink(out, i, page_size, i == cur_page);
    }
    if (page_count > 1) {
      out.print(" ");
      renderPageLink(out, page_count - 1, page_size,
                     cur_page == page_count - 1);
    }
    out.print("] Page " + (cur_page + 1) + " of " + page_count);
  }



  private String processViewMode(PlatformContext ctx,
                            EnvironmentVariables vars, CommandWriter out) {

    boolean raw_report = false;
    String log_type = vars.get("type");
    LogEventsSet log_events = ctx.getLogSystem().getLogEventsSet(log_type);
    if (log_events != null) {

      long log_size = log_events.getExactSize();

      // Get the page we are looking at,
      String pos_s = vars.get("pos");
      if (pos_s == null) pos_s = "0";
      // The number of elements on the page,
      String size_s = vars.get("size");
      if (size_s == null) size_s = "20";

      long pos = Long.parseLong(pos_s);
      int size = Integer.parseInt(size_s);

      // Sanity checks,
      if (pos < 0) pos = 0;
      if (pos > log_size) pos = log_size;
      if (size < 0 || size > 200) size = 20;

      out.cls();
      out.print("Viewing log: " + log_type + " ");
      out.print(out.createCallbackLink("[Refresh]", null));
      out.print(" ");
      // The close button,
//      Map<String, String> close_props = Collections.singletonMap("m", "z");
      String callback_cmd = "close";
      out.println(out.createCallbackLink("[Close]", callback_cmd));
      out.println();
      renderPaginationControl(out, pos, size, log_size);
      out.println();
      out.println();

      long end = Math.min(pos + size, log_size);
      for (long n = pos; n < end; ++n) {
        LogPageEvent event = log_events.getEvent(log_size - n - 1);
        out.print(
              FormattingUtils.formatLongDateTimeString(event.getTimestamp()),
              "info");
        out.print(" ");
        // If the log type is a known type then format it
        if (raw_report == false) {
          if (log_type.equals("http")) {
            String[] lv = event.getEventValues();
            out.println( sCheck(lv[9]) + " ms " + sCheck(lv[0]) + " " +
                         sCheck(lv[3]) + " " + sCheck(lv[4]) );
          }
          else if (log_type.equals("webapp")) {
            String[] lv = event.getEventValues();
            String msg_fmt = lv[0];
            Object[] lv2 = new String[lv.length - 1];
            System.arraycopy(lv, 1, lv2, 0, lv.length - 1);
            String msg = MessageFormat.format(msg_fmt, lv2);
            out.println(msg);
          }
          else {
            out.println(event.asString());
          }
        }
        else {
          out.println(event.asString());
        }
      }

      out.println();
      renderPaginationControl(out, pos, size, log_size);
      out.println();
    }
    else {
      out.println("Couldn't find log: " + log_type, "error");
    }

    return "WAIT";

  }




  @Override
  public String init(ServerCommandContext sctx, EnvironmentVariables vars) {

    PlatformContext ctx = sctx.getPlatformContext();

    // Initialization,
    CommandLine cline = new CommandLine(vars.get("cline"));
    String[] args = cline.getDefaultArgs();

    String m;
    if (args.length < 1) {
      m = "pcl";
    }
    else {
      String cmd = args[0];
      if (args.length >= 2 && cmd.equals("v")) {
        m = "v";
        String log_type = args[1];
        vars.put("type", log_type);
        // Check it exists,
        if (!ctx.getLogSystem().getLogTypes().contains(log_type)) {
          m = "err_nl";
        }
      }
      else if (cmd.equals("l")) {
        m = "l";
      }
      else {
        m = "pcl";
      }
    }

    vars.put("m", m);
    if (m.equals("v")) {
      return WINDOW_MODE;
    }
    else {
      return CONSOLE_MODE;
    }
  }

  @Override
  public String process(ServerCommandContext sctx,
                        EnvironmentVariables vars, CommandWriter out) {

    PlatformContext ctx = sctx.getPlatformContext();

    // The command line,
    String cline = vars.get("cline");

    // Represents a callback,
    if (cline.startsWith("#")) {
      if (cline.startsWith("#pos ")) {
        // Change 'pos'
        vars.put("pos", cline.substring(5));
      }
      else if (cline.startsWith("#close")) {
        // Close it!
        vars.put("m", "z");
      }
      else {
        // Just refresh!
      }
    }

    // The mode,
    String m = vars.get("m");

    if (m == null || m.equals("pcl")) {
      out.println("Log Tool", "info");
      out.println("Query the content of a log file.", "info");
      out.println();
      out.println("log [lv] [log name]", "info");
      out.println();
      out.println(" l = list all the logs available.", "info");
      out.println(" v = browse the contents of the given log name.", "info");
      out.println();

      return "STOP";
    }
    // List all the logs,
    else if (m.equals("l")) {
      List<String> log_types = ctx.getLogSystem().getLogTypes();
      int count = 0;
      for (String log_type : log_types) {
        out.println(log_type);
        ++count;
      }
      if (count == 0) {
        out.println("No logs available.", "info");
      }
    }
    // View a particular log,
    else if (m.equals("v")) {
      return processViewMode(ctx, vars, out);
    }
    // Error, log type not found,
    else if (m.equals("err_nl")) {
      out.println("log: log not found: " + vars.get("type"), "error");
    }
    else if (m.equals("z")) {
      // Close
    }

    return "STOP";
  }

  @Override
  public String getIconString() {
    return "log";
  }

}
