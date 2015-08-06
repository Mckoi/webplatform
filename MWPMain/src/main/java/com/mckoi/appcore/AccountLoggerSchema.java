/**
 * com.mckoi.appcore.AccountLoggerSchema  Mar 12, 2012
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

package com.mckoi.appcore;

import com.mckoi.odb.*;

/**
 * Static methods for defining the account logger schema.
 *
 * @author Tobias Downer
 */

public class AccountLoggerSchema {

  /**
   * Creates the schema for logged objects.
   */
  public static void defineSchema(ODBTransaction t)
                                              throws ClassValidationException {

    ODBClassCreator class_creator = t.getClassCreator();

    // The root object of the log system,
    class_creator.createClass("L.Root")
       .defineString("version", true)
       .defineString("name", true)
       .defineString("description", true)
       .defineList("logs", "L.Log",
                   ODBOrderSpecification.lexicographic("name"), false);

    // The main class for a log
    class_creator.createClass("L.Log")
       .defineString("name")
       // Log entries sorted by their reference (by time).
       .defineList("entries", "L.Entry",
                   ODBOrderSpecification.lexicographic("time"), true)
       // The log dictionary,
       .defineList("dict_word", "L.Word",
                   ODBOrderSpecification.lexicographic("word"), true)
       .defineList("dict_ref", "L.Word", false);

    // A Log entry,
    class_creator.createClass("L.Entry")
       .defineString("time")
       .defineString("value");

    // A word in the dictionary,
    class_creator.createClass("L.Word")
       .defineString("word");

    // Validate and complete the class assignments,
    class_creator.validateAndComplete();

  }

  /**
   * Initialize the log system.
   */
  public static void setup(ODBTransaction t) {

    // Create the log root,
    ODBClass root_class = t.findClass("L.Root");

    // (version, name, description, logs)
    ODBObject root_ob = t.constructObject(root_class,
            "1.0", "System Logger", "System Log Object", null);

    // Create the named item,
    t.addNamedItem("logroot", root_ob);

  }

}
