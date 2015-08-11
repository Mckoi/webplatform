/* 
 * Copyright (C) 2000-2012 Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/* global GUIWidgets, MWPENV */

'use strict';

// Client side Editor UI

// Load point;
//   'pane' object is FramePanel
//     (use pane.getDOM() to set the DOM)
//   'comm' object has;
//     comm.send(command_string) - sends a command to the server's frame with
//             the given args.
//   'frame_name' is String
//   'process_id_str' is String
//   'args_str' is String

var MckoiEditor = function(pane, comm, frame_name, process_id_str, args_str) {

  // Location of the code mirror directory,
  var CODEMIRROR_LOC = "js/codemirror2_35";

  // File types,
  
  var XML_JS = CODEMIRROR_LOC + "/mode/xml/xml.js";
  var JAVASCRIPT_JS = CODEMIRROR_LOC + "/mode/javascript/javascript.js";
  var CSS_JS = CODEMIRROR_LOC + "/mode/css/css.js";
  var CLIKE_JS = CODEMIRROR_LOC + "/mode/clike/clike.js";
  var HTMLMIXED_JS = CODEMIRROR_LOC + "/mode/htmlmixed/htmlmixed.js";

  // Database of file types/mimes and libraries that support them,

  var TYPE_MAP = {

    "text":{
      "requires":[],
      "ext":["txt"],
      "mime":["text/plain"]
    },

    "jscript":{
      "requires":[JAVASCRIPT_JS],
      "ext":["js"],
      "mime":["text/javascript","application/x-javascript"]
    },

    "json":{
      "requires":[JAVASCRIPT_JS],
      "ext":["json"],
      "mime":["application/json","application/x-json"]
    },

    "java":{
      "requires":[CLIKE_JS],
      "ext":["java"],
      "mime":["text/x-java","text/java"]
    },

    "jsp":{
      "requires":[XML_JS,
                  JAVASCRIPT_JS,
                  CSS_JS,
                  HTMLMIXED_JS,
                  CODEMIRROR_LOC + "/mode/htmlembedded/htmlembedded.js",
                 ],
      "ext":["jsp"],
      "mime":["application/x-jsp"]
    },

    "xml":{
      "requires":[XML_JS],
      "ext":["xml"],
      "mime":["application/xml","text/xml"]
    },

    "css":{
      "requires":[CSS_JS],
      "ext":["css"],
      "mime":["text/css"]
    },

    "php":{
      "requires":[XML_JS,
                  CLIKE_JS,
                  CSS_JS,
                  CODEMIRROR_LOC + "/mode/php/php.js"],
      "ext":["php"],
      "mime":["text/x-php","application/x-httpd-php"]
    },

    "ruby":{
      "requires":[CODEMIRROR_LOC + "/mode/ruby/ruby.js"],
      "ext":["rb"],
      "mime":["text/x-ruby"]
    },

    "python":{
      "requires":[CODEMIRROR_LOC + "/mode/python/python.js"],
      "ext":["py"],
      "mime":["text/x-python"]
    },

    "html":{
      "requires":[XML_JS],
      "ext":["html","htm"],
      "mime":["text/html"]
    },

    "groovy":{
      "requires":[CODEMIRROR_LOC + "/mode/groovy/groovy.js"],
      "ext":["groovy"],
      "mime":["text/x-groovy"]
    }

  };

  // --- Init ---

  // Set any CSS,
  MWPENV.addCSS("css/codemirror.css");
  MWPENV.addCSS("css/cm2theme/dusk.css");  // This is the default theme,

  var main_scripts = [
    CODEMIRROR_LOC + "/lib/codemirror.js",
  ];
  
  // Support libraries to load separately,
  var support_scripts = [
    CODEMIRROR_LOC + "/lib/util/dialog.js",
    CODEMIRROR_LOC + "/lib/util/searchcursor.js",
    CODEMIRROR_LOC + "/lib/util/search.js"
  ];
  

  // Nest libs behind main_scripts. This ensures we load codemirror before
  // the dependent libraries.
  MWPENV.loadScripts(main_scripts, function() {
    MWPENV.loadScripts(support_scripts, function() {
      // Invoke the editor after the libraries are loaded,
      var editor = new Main();
      editor.run();
    });
  });

  // -----

  function Main() {

    var code_mirror;
    var title_label;
    var mime_pulldown;

    var title_text;
    var mime_map;

    var default_theme;
    var current_theme;

    var needs_save = false;

    var lock_comm = 0;


    // Assign global save action to CodeMirror,
    CodeMirror.commands.save = function(cm) {
      if (cm.mckoiSaveAction) {
        cm.mckoiSaveAction();
      }
    };
    CodeMirror.commands.autoCommentSection = function(cm) {
      if (cm.mckoiAutoCommentAction) {
        cm.mckoiAutoCommentAction();
      }
    };

    // Assign code mirror defaults for PC,
    CodeMirror.keyMap.pcDefault["F3"] = "findNext";
    CodeMirror.keyMap.pcDefault["Shift-F3"] = "findPrev";
    CodeMirror.keyMap.pcDefault["Ctrl-F3"] = "find";
    // Auto comment key,
    CodeMirror.keyMap.basic["Ctrl-/"] = "autoCommentSection";


    var lockComm = function() {
      ++lock_comm;
    };
    
    var unlockComm = function() {
      --lock_comm;
    };
    
    var commLocked = function() {
      return (lock_comm > 0)
    };

    // The list of all syntax types,
    var getTextTypesList = function() {
      var out = [];
      for (var type in TYPE_MAP) {
        out.push(type);
      }
      return out;
    };

    var setTitleText = function(title) {
      title_label.setText(title);
      title_text = title;
    };

    var setMimeType = function(mime) {
      // The default,
      var m_key = "text";
      var m_map = TYPE_MAP[m_key];
      for (var key in TYPE_MAP) {
        var map = TYPE_MAP[key];
        if (map) {
          var mime_types = map["mime"];
          if (MWPENV.arrayContains(mime_types, mime)) {
            m_key = key;
            m_map = map;
            break;
          }
        }
      }
      // Ok, 'm_map' is the object,
      mime_pulldown.select(m_key);
      mime_map = m_map;
    };

    // Modal dialog that enables when closed,
    var modalDialog = function(dinf) {
      var dialog = GUIWidgets.createStandardDialog(dinf);
      var original_close = dialog.close;
      // Overridden close,
      dialog.close = function() {
        original_close();
        setEnabled(true);
      };
      return dialog;
    };

    // Disables/Enables controls
    var setEnabled = function(enabled) {
      if (enabled) {
        code_mirror.setOption("readOnly", false);
      }
      else {
        code_mirror.setOption("readOnly", "nocursor");
      }
    };

    // Set the theme,
    var setTheme = function(theme_name) {
      if (theme_name !== current_theme) {
        // Add the CSS with the theme name,
        MWPENV.addCSS("css/cm2theme/" + theme_name + ".css");
        code_mirror.setOption("theme", theme_name);
        current_theme = theme_name;
      }
    };

    // On text change event,
    var contentChanged = function() {
      if (!needs_save) {
        needs_save = true;
        title_label.setBold(true);
      }
    };

    // Resets the document 'needs save' indicator.
    var resetNeedsSave = function() {
      if (needs_save) {
        needs_save = false;
        title_label.setBold(false);
      }
    };

    var close_action = function(widget, div) {
      // Return if communication locked,
      if (commLocked()) return;

      setEnabled(false);
      // Close immediately if we don't need to save,
      if (!needs_save) {
        comm.send("close");
      }
      // Otherwise open confirmation dialog.
      else {
        var dinf = {
          type:"question",
          msg:"File is not saved. Exit without saving?",
          confirm_label:"Exit"
        };
        var dialog = modalDialog(dinf);
        dialog.confirm_action = function() { comm.send("close"); };
        dialog.show();
      }
    };

    var save_action = function(widget, div) {
      // Return if communication locked,
      if (commLocked()) return;

      lockComm();
      var msgs = [];
      setEnabled(false);
      var content = code_mirror.getValue();
      var p = 0;
      var sz = content.length;
      msgs.push('sv_init "' + title_text + '" "' +
                mime_map.mime[0] + '" "' + content.length + '"');
      while (p < sz) {
        var psz = Math.min(sz - p, 8192);
        var putstr = "sv_put ";
        if (p + psz === sz) {
          putstr = "sv_fput ";
        }
        msgs.push(putstr + content.substring(p, p + psz));
        p += psz;
      }

//      var dinf = {
//        type:"statement",
//        msg:"Saving..."
//      };
//      var save_dialog = modalDialog(dinf);
//      save_dialog.show();

      var complete_action = function() {
        setEnabled(true);
        focusTextArea();
        unlockComm();
      };
      comm.sendSet(msgs, complete_action);
    };

    var about_action = function(widget, div) {
      setEnabled(false);
      var dinf = {
        type:"html",
        width:450,
        height:220,
        html_content:
          "<div class='dialoghtml'>" +
          "<h3>Mckoi Web Platform Editor</h3>" +
          "<p>Integrated text editor for the <a href='http://www.mckoi.com/' target='_blank'>Mckoi Web Platform</a> by Tobias Downer.<br/>" +
          "Includes <a href='http://codemirror.net/' target='_blank'>Code Mirror 2</a> (MIT-style license) by Marijn Haverbeke.<br/>" +
          "</p></div>"
      };
      var dialog = modalDialog(dinf);
      
      dialog.show();
    };

//    var help_action = function(widget, div) {
//      // PENDING
//    }

    var auto_comment_action = function() {
      // Mime type of the editor,
      var mime_type = code_mirror.getOption("mode");
      // This commenting only works for C like and javascript,
      if ( !( mime_type === TYPE_MAP.java.mime[0] ||
              mime_type === TYPE_MAP.jscript.mime[0] ||
              mime_type === TYPE_MAP.json.mime[0] ) ) {
        return;
      }

      var from_pos = code_mirror.getCursor(true);
      var to_pos = code_mirror.getCursor(false);

      var type;

      var end_line = ((to_pos.line !== from_pos.line && to_pos.ch === 0) ?
                      to_pos.line : to_pos.line + 1);
      var i_pos = {};
      for (var line = from_pos.line; line < end_line; ++line) {
        i_pos.line = line;
        i_pos.ch = 0;
        var line_str;
        if (!type) {
          line_str = code_mirror.getLine(line);
          if (line_str.slice(0, 2) !== "//") {
            type = "com";
          }
          else {
            type = "unc";
          }
        }
        if (type === "com") {
          code_mirror.replaceRange("//", i_pos);
        }
        else {
          line_str = code_mirror.getLine(line);
          if (line_str.slice(0, 2) === "//") {
            var rm_pos = { line: line, ch: 2 };
            code_mirror.replaceRange("", i_pos, rm_pos);
          }
        }
      }
    };

    // called when file type is changed,
    var change_file_type = function(item_string) {
      // Set to plain text, then load required libs, etc.
      code_mirror.setOption("mode", "text/plain");
      var m_map = TYPE_MAP[item_string];
      if (m_map) {
        mime_map = m_map;
        var base_mime = m_map.mime[0];
        var requires = m_map.requires;
        var ri = 0;
        // Load each lib in the order they are presented,
        var f = function() {
          if (ri < requires.length) {
            MWPENV.loadScript(requires[ri], function() {
              ++ri;
              f();
            });
          }
          else {
            // After loaded,
            code_mirror.setOption("mode", base_mime);
          }
        };
        f();
      }
    };

    var focusTextArea = function() {
      code_mirror.refresh();
      code_mirror.focus();
    };

    // Run function,
    var setup = function() {

      var file_name = "";
      var src_type = "text";

      // Set up the pane for a standard application,
      pane.setupForApp();

      // The container element,
      var container_e = mdom.create({
        tag: "div",
        style: { position: "relative", height: "100%" }
      });
      

////    var HELP_ICON =  { icon : "help",   fill_color : "white",
////                       mask_border_size : 1.1, mask_border_color : "black",
////                       mask_color : "#3030c0"};
////    var CLOSE_ICON = { icon : "close2", fill_color : "white",
////                       mask_border_size : 1.1, mask_border_color : "black",
////                       mask_color : "#c03030"};
//      var HELP_ICON =  { icon : "help",   fill_color : "#303030" };
////                       mask_border_size : 1.1, mask_border_color : "white",
////                         mask_color : "#e2e2e2"};
//      var CLOSE_ICON = { icon : "close2", fill_color : "#303030" };
////                       mask_border_size : 1.1, mask_border_color : "white",
////                         mask_color : "#e2e2e2"};

//      var CONTEXT_ICON = { icon : "file_base", fill_color : "#303030" };
///                           mask_color : "#e2e2e2" };
//      var SAVE_ICON =    { icon : "disk",      fill_color : "#303030" };
///                           mask_color : "#e2e2e2" };

      // The main icons on the context bar,
//      var HELP_ICON =    { style : "icon_main", icon : "help" };
      var CLOSE_ICON =   { style : "icon_close", icon : "close3" };
      var CONTEXT_ICON = { style : "icon_main", icon : "file_base" };
      var SAVE_ICON =    { style : "icon_main", icon : "disk" };

      // The icon elements in the menus,
      var HELP_ICON2 =   { style : "icon_element", icon : "help" };
      var SAVE_ICON2 =   { style : "icon_element", icon : "disk" };

//      var help_button = GUIWidgets.createIconButton(HELP_ICON, 1.5);
//      var close_button = GUIWidgets.createIconButton(CLOSE_ICON, 1.5);
//
//      // Set the actions for these buttons,
//      help_button.setAction(help_action);
//      close_button.setAction(close_action);

//      var help_button = GUIWidgets.createButton(24, 22);
//      help_button.setIcon(HELP_ICON, 1.125);
//      help_button.setAction(about_action);

      var close_button = GUIWidgets.createButton(24, 22);
      close_button.setClassName("close_button");
      close_button.setIcon(CLOSE_ICON, 1.125);
      close_button.setAction(close_action);

      var save_button = GUIWidgets.createButton(24, 22);
      save_button.setIcon(SAVE_ICON, 1.125);
      save_button.setAction(save_action);


      // The pulldown menu,
      var context_pulldown = GUIWidgets.createPulldown(32, 22);
      context_pulldown.setIcon(CONTEXT_ICON, 1.125);

//      var help_topics = GUIWidgets.createMenu();
//      help_topics.addItem(HELP_ICON2, "Tutorial", null);
//      help_topics.addItem(HELP_ICON2, "Reference", null);
//      help_topics.addItem(HELP_ICON2, "Shortcuts", null);
//
//      var help_menu = GUIWidgets.createMenu();
//      help_menu.addItem(HELP_ICON2, [ "View Help", "F1" ], null); //help_topics);
//      help_menu.addItem(null,       [ "About" ], null);

      var default_theme_action = function() {
        setTheme(default_theme);
      };
      var change_theme_action = function(widget) {
        var theme_name = widget.getLabelText()[0].toLowerCase();
        setTheme(theme_name);
      };

      var theme_menu = GUIWidgets.createMenu();
      theme_menu.addItem(null, [ "Default" ], default_theme_action);
      theme_menu.addSeparator();
      theme_menu.addItem(null, [ "Ambiance" ], change_theme_action);
      theme_menu.addItem(null, [ "Blackboard" ], change_theme_action);
      theme_menu.addItem(null, [ "Cobalt" ], change_theme_action);
      theme_menu.addItem(null, [ "Dusk" ], change_theme_action);
      theme_menu.addItem(null, [ "Eclipse" ], change_theme_action);
      theme_menu.addItem(null, [ "Elegant" ], change_theme_action);
      theme_menu.addItem(null, [ "Monokai" ], change_theme_action);
      theme_menu.addItem(null, [ "Neat" ], change_theme_action);
      theme_menu.addItem(null, [ "Night" ], change_theme_action);
      theme_menu.addItem(null, [ "Vibrant-Ink" ], change_theme_action);
      theme_menu.addItem(null, [ "Xq-Dark" ], change_theme_action);

      var top_menu = GUIWidgets.createMenu();
      top_menu.addItem(SAVE_ICON2, [ "Save", "Ctrl+S" ], save_action);
//      top_menu.addItem(SAVE_ICON2, [ "Save As" ], save_as_action);
      top_menu.addSeparator();
      top_menu.addItem(null,       [ "Theme" ], theme_menu);
      top_menu.addSeparator();
//      top_menu.addItem(HELP_ICON2, [ "Help", "F1" ], help_action);
      top_menu.addItem(null,       [ "About" ], about_action);
      top_menu.addSeparator();
      top_menu.addItem(null,       [ "Exit" ], close_action);

      context_pulldown.setMenu(top_menu);

      // The mime type menu,
      mime_pulldown = GUIWidgets.createDropdownList(70, 22);
      mime_pulldown.setList(getTextTypesList());
      mime_pulldown.select(src_type);
      // When selected, change the file type,
      mime_pulldown.onselect = change_file_type;

      var title_box = GUIWidgets.createPanel(null, 22);
      title_label = GUIWidgets.createLabel(file_name, null, 20);
      title_box.addLTWidget(title_label, 8, 4);
      title_box.setClassName("textcomponent");

      // Lay out the widgets on the panel
      var top_panel = GUIWidgets.createPanel("100%", 28);
      top_panel.setClassName("topbargradient");

      top_panel.addLTWidget(context_pulldown, 12, 2);
      top_panel.addLTWidget(save_button,      48, 2);
      top_panel.addLTWidget(mime_pulldown,    76, 2);
      top_panel.addLTRWidget(title_box,       150, 2, 34);

//      top_panel.addRTWidget(help_button,      30, 1);
      top_panel.addRTWidget(close_button,      4, 1);


      // The central element,
      var central_e = mdom.create({
        tag: "div",
        style: { position: "absolute",
                 top: 30, bottom: 0, right: 0, left: 0
        }
      });
      

      // The code mirror 2 editor component,
      code_mirror = CodeMirror(central_e,
        { theme : "dusk",
          lineNumbers : true,
          matchBrackets : true,
          mode : "text/plain",
          onChange : function() {
            contentChanged();
          }
        });
      // Set the default theme,
      setTheme("dusk");

      // Various bindings,
      code_mirror.mckoiSaveAction = function() { save_action(); };
      code_mirror.mckoiAutoCommentAction = function() { auto_comment_action(); };

      container_e.appendChild(top_panel.toDOM());
      container_e.appendChild(central_e);
      pane.getDOM().appendChild(container_e);

      // Override the pane focus behaviour,
      pane.doFocus = function() {
        focusTextArea();
      };

      // Set the value of code_mirror to the file content,
      code_mirror.setValue("");

      focusTextArea();

    };

    // Handles reply/event from server,
    var processReceive = function(command) {
      // First three characters are the code string,
      var code = command.substring(0, 3);
      // Initialize the component,
      if (code === "ini") {
        setup();
      }
      // Set the default theme,
      else if (code === "th-") {
        default_theme = command.substring(3);
        setTheme(default_theme);
      }
      // Disable the component (user can not do anything),
      else if (code === "dis") {
        setEnabled(false);
      }
      // Enables the component,
      else if (code === "ena") {
        setEnabled(true);
      }
      // Clear the content,
      else if (code === "cc-") {
        code_mirror.setValue("");
      }
      // Add to the end of the content,
      else if (code === "ac-") {
        code_mirror.setValue(code_mirror.getValue() + command.substring(3));
      }
      // Sets the title (the file name),
      else if (code === "st-") {
        setTitleText(command.substring(3));
      }
      // Set the mime type,
      else if (code === "sm-") {
        setMimeType(command.substring(3));
      }
      // Load complete,
      else if (code === "lc-") {
        resetNeedsSave();
        code_mirror.clearHistory();
      }
      // Confirm saved,
      else if (code === "cs-") {
        resetNeedsSave();
      }
    };

    // Send init to server, then wait for instructions on how to proceed,
    this.run = function() {
      // action when receiving a command,
      comm.onreceive = processReceive;
      // Send the init command to the frame,
      comm.send("init");
    };

  }

};
