/**
 * com.mckoi.mwpcore.GeneralLogFormatter  Mar 21, 2012
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A general formatter for logging data.
 *
 * @author Tobias Downer
 */

public class GeneralLogFormatter extends Formatter {

  private final SimpleDateFormat formatter;
  private final Date date = new Date();
  private final String line_separator = System.getProperty("line.separator");

  public GeneralLogFormatter() {
    formatter = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss.SSS");
  }

  @Override
  public String format(LogRecord record) {
    StringBuilder b = new StringBuilder();
    date.setTime(record.getMillis());
    b.append(formatter.format(date));
    b.append(" ");
    b.append(record.getLevel().getName());
    b.append(" ");
    b.append(formatMessage(record));
    b.append(line_separator);
    if (record.getThrown() != null) {
      b.append("#");
      try {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        record.getThrown().printStackTrace(pw);
        pw.close();
        b.append(sw.toString());
      }
      catch (Exception ex) {
      }
    }
    return b.toString();
  }

}
