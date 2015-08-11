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

'use strict';

/* global eval, GUIWidgets */

// The mwpenv global object,
this.MWPENV = {};

(function() {

  var pendingscripts = {};
  var loadedscripts = {};
  var listeners = [];

  MWPENV.applications = {};
  MWPENV.terminal_apps = {};
  MWPENV.css_files = {};

  // Set to true for testing only. If true, we dynamically insert a
  // <script> element to load scripts. This doesn't work on all
  // browsers. If false, an HTTTP request and 'eval' is used, which
  // works on all browsers.
  MWPENV.useScriptElementForLoad = true;
//  var addTimestampForLoad = true;
//  var useScriptElementForLoad = false;
  MWPENV.addTimestampForLoad = false;

  // This string is appended to all css and js resources loaded dynamically if
  // 'addTimestampForLoad' is false. If you are having caching problems then
  // change this version number to see if it fixes things.
  MWPENV.timestampString = "000h";

  MWPENV.ts_code = new Date().getTime();

  // General utility
  MWPENV.ajaxAsyncLoad = function(get_post, source, post_string,
                                callback_success, callback_fail) {
    var xmlhttp = new XMLHttpRequest();
    xmlhttp.open(get_post, source, true);
    xmlhttp.send(post_string);
    xmlhttp.onreadystatechange = function() {
      if (xmlhttp.readyState === 4) {
        if (xmlhttp.status === 200) {
          callback_success(xmlhttp.responseText);
        }
        else {
          callback_fail(xmlhttp.status);
        }
      }
    };
  };

  MWPENV.globalEval = function(src) {
    // For IE
    if (window.execScript) window.execScript(src);
    // Every other browser,
    else eval.call(null, src);
  };

  var notify = function(evt, arg1, arg2, arg3) {
    // Copy the list locally,
    var ll = listeners.slice(0, listeners.length);
    for (var i = 0; i < ll.length; ++i) {
      ll[i](evt, arg1, arg2, arg3);
    }
  };

  MWPENV.notifyScriptPending = function(src_location) {
    pendingscripts[src_location] = "P";
    notify("script_pending", src_location);
  };
  MWPENV.notifyScriptLoaded = function(src_location, src_code) {
    delete pendingscripts[src_location];
    loadedscripts[src_location] = "L";
    notify("script_loaded", src_location, src_code);
  };
  MWPENV.notifyScriptFail = function(src_location, status) {
    delete pendingscripts[src_location];
    notify("script_fail", src_location, status);
  };

  MWPENV.isScriptPending = function(src_location) {
    return pendingscripts[src_location] !== undefined;
  };
  MWPENV.isScriptLoaded = function(src_location) {
    return loadedscripts[src_location] !== undefined;
  };

  MWPENV.addListener = function(listener) {
    var i = listeners.length;
    while (i--) {
      if (listeners[i] === listener) {
        return;
      }
    }
    listeners.push(listener);
  };
  MWPENV.removeListener = function(listener) {
    var i = listeners.length;
    while (i--) {
      if (listeners[i] === listener) {
        listeners.splice(i, 1);
        return;
      }
    }
  };

  MWPENV.onScriptLoad = function(src_location, action) {
    if (!MWPENV.isScriptLoaded(src_location)) {
      var loadlistener = function(evt, arg1) {
        if (evt === "script_loaded") {
          if (arg1 === src_location) {
            action();
            MWPENV.removeListener(loadlistener);
          }
        }
      };
      MWPENV.addListener(loadlistener);
    }
    // Already loaded,
    else {
      action();
    }
  };

  MWPENV.requireScript = function(scriptsource) {
    if (!MWPENV.isScriptPending(scriptsource) &&
        !MWPENV.isScriptLoaded(scriptsource)) {

      MWPENV.notifyScriptPending(scriptsource);

      if (MWPENV.useScriptElementForLoad === true) {
        var load_success2 = function(sourcecode) {
          if (!MWPENV.isScriptLoaded(scriptsource)) {
            MWPENV.notifyScriptLoaded(scriptsource, sourcecode);
          }
        };
        var oHead = document.getElementsByTagName('head')[0];
        var oScript = mdom.create('script');
        oScript.type = 'text/javascript';

        if (MWPENV.addTimestampForLoad)
          oScript.src = scriptsource + "?" + MWPENV.ts_code;
        else
          oScript.src = scriptsource + "?" + MWPENV.timestampString;

        // most browsers
        oScript.onload = load_success2;
        // IE 6 & 7
        oScript.onreadystatechange = function() {
          if (this.readyState === 'loaded' ||
              this.readyState === 'complete') {
            load_success2();
          }
        };
        oHead.appendChild(oScript);
      }
      else {
        var load_success = function(sourcecode) {
          if (!MWPENV.isScriptLoaded(scriptsource)) {
            MWPENV.globalEval(sourcecode);
            MWPENV.notifyScriptLoaded(scriptsource, sourcecode);
          }
        };
        var load_fail = function(status) {
          MWPENV.notifyScriptFail(scriptsource, status);
        };
        MWPENV.ajaxAsyncLoad("GET", scriptsource, null, load_success, load_fail);
      }
    }
  };

  /**
   * Loads a template file from the source file. A template file can be
   * installed into a document element.
   */
  MWPENV.loadTemplate = function(source_file, onload, onerror) {
    if (!onerror) {
      onerror = function(status) {
        console.log("Load Template (%s) failed: %s", source_file, status);
        throw new Error("Template load error: " + source_file);
      };
    }
    MWPENV.ajaxAsyncLoad("GET", source_file, null, onload, onerror);
  };

  /**
   * Installs the given template to the given dom element. The template
   * parameter is an object returned by the 'loadTemplate' function. The
   * dom_ele is an element in the document hierarchy. Any content in the
   * dom_ele is deleted by this call.
   */
  MWPENV.installTemplate = function(template, dom_ele) {
    if (!dom_ele) dom_ele = document.body;
    dom_ele.innerHTML = template;
  };

  // Loads all the scripts dynamically and calls the callback function when
  // all the scripts are loaded.
  MWPENV.loadScripts = function(scripts_array, callback) {
    var loaded = {};

    var l = function(evt, arg1) {
      if (evt === "script_loaded") {
        loaded[arg1] = "L";
        check();
      }
    };
    MWPENV.addListener(l);
    for (var i = 0; i < scripts_array.length; ++i) {
      var scriptsource = scripts_array[i];
      if (!MWPENV.isScriptLoaded(scriptsource)) {
        MWPENV.requireScript(scriptsource);
      }
      else {
        loaded[scriptsource] = "L";
      }
    }

    var check = function() {
      var count = 0;
      for (var i = 0; i < scripts_array.length; ++i) {
        if (loaded[scripts_array[i]] === "L") ++count;
      }
      if (count === scripts_array.length) {
        MWPENV.removeListener(l);
        callback();
      }
    };

    check();

  };

  MWPENV.loadScript = function(script_name, callback) {
    MWPENV.loadScripts([ script_name ], callback);
  };

  // Adds a CSS entry to the document head if it hasn't already been added.
  MWPENV.addCSS = function(css_fname) {
    if (MWPENV.css_files[css_fname] === undefined) {
      var headID = document.getElementsByTagName("head")[0];
      var newCSS = mdom.create("link");
      newCSS.rel = "stylesheet";

      var css_cache_name = css_fname;
      if (MWPENV.addTimestampForLoad)
        css_cache_name = css_cache_name + "?" + MWPENV.ts_code;
      else
        css_cache_name = css_cache_name + "?" + MWPENV.timestampString;

      newCSS.href = css_cache_name;
      headID.appendChild(newCSS);
      MWPENV.css_files[css_fname] = "L";
    }
  };

  // General 'array contains' method,'
  MWPENV.arrayContains = function(a, obj) {
    var i = a.length;
    while (i--) {
      if (a[i] === obj) return true;
    }
    return false;
  };

})();

// Utilities
this.MWPUTILS = {};
(function() {

  // Regex for a quoted string
  var QUOTED = /^\"(.*)\"$/;

  function toHTMLEntityInternal(html_str) {
    return html_str.replace(/&/g, "&amp;").replace(/>/g, "&gt;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
  }

  MWPUTILS.toHTMLEntity = function(html_str) {
    return toHTMLEntityInternal(html_str);
  };

})();






//// Loads the given script dynamically if it's not already loaded.
//this.requireScript = MWPENV.requireScript;

  




// Self invoking anonymous function contains state
(function() {


  // Given an icon type, returns the color for it.

  var ICON_DB = {
    "console":"#588BC4",
    "log":"#D1CF58",
    "edit":"#54CC58"
  };

  var iconToColor = function(icon) {
    var c = ICON_DB[icon];
    if (!c) {
      c = "#BBBCBF";
    }
    return c;
  };


  // -----

  // Trims whitespace from a string.
  function trim(str) {
    return str.replace(/^\s*/, "").replace(/\s*$/, "");
  }

  // Current time (in milliseconds)
  function currentTS() {
    return new Date().getTime();
  }

  // The selector UI element,
  function SelectorUI() {

    this.div = null;

    var selector_div = null;
    var current_sel = null;

    // Initialize,
    this.init = function() {
      selector_div = mdom.create({
        tag: "div.mwpconsole_selector",
        style: {position: "relative", height: "100%", width: "26px"}
      });
      
      this.div.appendChild(selector_div);
    };

    // Adds a pane to the selector,
    this.addTab = function(frame_name, label, icon, select_action) {

      var outer = mdom.create({
            tag: "div", style: {padding: "3px 0 1px 0"}});
      outer.xmckoi = {"frame": frame_name};

      var tblock = mdom.create({
        tag: "div.mwpconsole_sitem",
        style: {position: "relative",
                 height: "16px", width: "10px", left: "7px",
                 backgroundColor: iconToColor(icon)}
      });

      tblock.onmousedown = function(e) {
        if (!e) e = window.event;
        // IE Hack: Focus the window because IE doesn't do this for us!
        window.focus();
        e.preventDefault();
        e.cancelBubble = true;
        if (e.stopPropagation) e.stopPropagation();

        // Perform the select action,
        if (select_action) {
          select_action(frame_name);
        }

        return false;
      };

      outer.appendChild(tblock);
      selector_div.appendChild(outer);

    };

    // Returns the selector div for the frame name,
    function getSelectorItem(frame_name) {
      var childn = selector_div.childNodes;
      for (var i = 0; i < childn.length; ++i) {
        var node = childn[i];
        var selector_frame = node.xmckoi["frame"];
        if (selector_frame === frame_name) {
          return node;
        }
      }
      return null;
    }

    // Removes a pane from the selector,
    this.removeTab = function(frame_name) {
      var item = getSelectorItem(frame_name);
      if (item) {
        selector_div.removeChild(item);
      }
    };

    // Selects a pane from the selector,
    this.selectTab = function(frame_name) {
      var tblock;
      if (current_sel) {
        current_sel.style.padding="3px 0 1px 0";
        tblock = current_sel.firstChild;
        tblock.className="mwpconsole_sitem";
        tblock.style.height = "16px";
        tblock.style.width = "20px";
        tblock.style.left = "7px";
      }
      var outer = getSelectorItem(frame_name);
      outer.style.padding="2px 0 0 0";
      tblock = outer.firstChild;
      tblock.className="mwpconsole_sitem mwpconsole_sitem_selected";
      tblock.style.height = "18px";
      tblock.style.width = "23px";
      tblock.style.left = "4px";
      current_sel = outer;
    };

  }


  // The panel group UI element,
  function PanelGroup() {

    // frame name to console pane,
    this.panel_map = {};
    // console panes in order they were added,
    this.frame_order = [];
    this.panel_order = [];

    // The frame selector UI element,
    this.frame_selector = null;

    // the window div,
    this.window_div = null;
    // the current panel,
    var current_panel = null;

    // Sets the current main pane,
    this.setCurrentPane = function(frame_name) {
      var to_set = this.panel_map[frame_name];
      if (to_set && current_panel !== to_set) {
        current_panel = to_set;
        if (this.window_div.lastChild) {
          this.window_div.removeChild(this.window_div.lastChild);
        }
        this.window_div.appendChild(to_set.getBaseDOM());
        if (to_set.doFocus) {
          to_set.doFocus();
        }
        this.frame_selector.selectTab(frame_name);
      }
    };

    // Adds a pane,
    this.addPane = function(frame_name, label, icon, pane) {
      this.panel_map[frame_name] = pane;
      this.frame_order.push(frame_name);
      this.panel_order.push(pane);
      var this_pg = this;
      // Add to the selector,
      this.frame_selector.addTab(frame_name, label, icon, function() {
        this_pg.setCurrentPane(frame_name);
      });
    };

    // Removes a pane,
    this.removePane = function(frame_name) {
      delete this.panel_map[frame_name];
      var i = -1;
      for (i = 0; i < this.frame_order.length; ++i) {
        if (this.frame_order[i] === frame_name) {
          this.frame_order.splice(i, 1);
          this.panel_order.splice(i, 1);
          break;
        }
      }
      // Set the current pane,
      var c = this.frame_order.length;
      if (c > 0) {
        if (i >= c) --i;
        if (i < 0) i = 0;
        // Set the current pane,
        this.setCurrentPane(this.frame_order[i]);
      }
      else {
        current_panel = null;
        if (this.window_div.lastChild) {
          this.window_div.removeChild(this.window_div.lastChild);
        }
      }
      // Remove the selector,
      this.frame_selector.removeTab(frame_name);
    };

    // Returns the pane
    this.getPane = function(frame_name) {
      return this.panel_map[frame_name];
    };

    // Adds a new console pane
    this.addConsolePane = function(frame_name, label, icon) {
      // Add a new panel,
      var new_pane = new FramePanel();
      new_pane.setupForConsole();
      this.addPane(frame_name, label, icon, new_pane);
      this.setCurrentPane(frame_name);
      return new_pane;
    };

  }


  // The console pane UI element,
  function FramePanel() {

    var pane_div;
    var main_div;

    var this_pane = this;

    // The action function for this frame,
    var action_function = null;
    // The close function for this frame,
    var close_function = null;
    // The interact function for this frame,
    var interact_function = null;

    var promptline = null;
    var prompttext = null;
    var promptinput = null;
    var curline = null;

    var line_empty = true;
    var prompt_focus = false;

    var cur_prompt_text = "";
    var prompt_visible = false;

    // If true, the prompt is a password type
    var password = false;

    // The history of commands entered,
    var commands_history = [];
    var comm_history_i = -1;

    // The currently active 'a' elements,
    var active_a_els = [];
    // The currently active 'input' elements,
    var active_input_els = [];

    // Scrolls to the end of the page.
    var timeout_uid = null;
    function scrollToElement(ele) {
      if (timeout_uid !== null) {
        clearTimeout(timeout_uid);
      }
      timeout_uid = setTimeout(function() {
        if (ele) ele.scrollIntoView();
      }, 50);
    }
    this.scrollToEnd = function() {
      var lastc = main_div.lastChild;
      if (lastc) scrollToElement(lastc);
    };

    // Performs a tab completion 'activation'. This will possibly callback on
    // the server.
    var last_tab_complete_prompt = null;
    var last_tab_complete_db = null;
    var last_tab_index = -1;
    
    function doTabOperation() {
      if (last_tab_complete_db === null) return;
      var sz = last_tab_complete_db.length - 1;
      if (sz === 0) return;
      ++last_tab_index;
      if (last_tab_index >= sz) last_tab_index = 0;
      var complete_str = last_tab_complete_db[last_tab_index + 1];
      if (complete_str.slice(-1) === "/")
          complete_str = complete_str.substring(0, complete_str.length - 1);
      var start_str = last_tab_complete_db[0];
      promptinput.value = "" + start_str + complete_str;
    };
    
    function doTabCompletion(prompt_text) {
      // Do we need to query?
      if (last_tab_complete_db === null) {
        if (last_tab_complete_prompt === null) {
          // Set the last tab complete prompt text,
          last_tab_complete_prompt = prompt_text;
          // Send the tab complete call if prompt text is different that the
          // previous call to this,
          interact_function("tabcomplete " + prompt_text);
        }
      }
      else {
        // We have a db ready, so do the tab operation,
        doTabOperation();
      }
    };

    // Returns the DOM element,
    this.getDOM = function() {
      return main_div;
    };

    this.getBaseDOM = function() {
      return pane_div;
    };

    var createSimpleDiv = function() {
      var el = mdom.create({
        tag: "div",
        style: {position: "absolute",
                 width: "100%", height: "100%", top: "0px", left: "0px"}
      });
      return el;
    };

    var setupPaneGeneral = function() {
      if (!pane_div) {
        pane_div = createSimpleDiv();
      }
      else {
        // Clear the pane_div,
        while (pane_div.lastChild) {
          pane_div.removeChild(pane_div.lastChild);
        } 
      }

      // The default focus div.
      // Catch 'focus' on this element if you want to move focus to a default
      // element on the app's pane.
      var fdiv = createSimpleDiv();
      fdiv.id = "defaultfocus";
      fdiv.tabIndex = "-1";
      fdiv.style.outline = "none";
      fdiv.style.width = "0px";
      fdiv.style.height = "0px";

      pane_div.appendChild(fdiv);

      // The overlay div.
      var odiv = createSimpleDiv();
      odiv.id = "paneoverlay";
      odiv.className = "mwpoverlay";
      odiv.style.display = "none";
      odiv.style.zIndex = "500";

      pane_div.appendChild(odiv);

      main_div = mdom.create("div");
      pane_div.appendChild(main_div);
      
    };

    function getSelectedText() {
      var text = "";
      if (typeof window.getSelection !== "undefined") {
        text = window.getSelection().toString();
      }
      else if (typeof document.selection !== "undefined" &&
                  document.selection.type === "Text") {
        text = document.selection.createRange().text;
      }
      return text;
    }

    // Move focus to prompt if nothing is selected on a click event.
    var mainPanelClick = function(e) {
      if (!e) e = window.event;
      if (e && e.target) {
        var click_tag = e.target.tagName;
        if ( prompt_visible && !prompt_focus &&
                (click_tag === 'DIV' || click_tag === 'SPAN') ) {
          var selected_text = getSelectedText();
          if (selected_text === '') {
            internalPromptFocus(false);
          }
        }
      }
    };

    this.setupForConsole = function() {
      setupPaneGeneral();
      
      main_div.className="mwpconsole_base";
      main_div.style.position = "relative";
      main_div.style.height = "100%";
      main_div.style.overflowX = "auto";
      main_div.style.overflowY = "auto";
      
      // Capture click events on the panel,
      main_div.onclick = mainPanelClick;
    };

    this.setupForApp = function() {
      setupPaneGeneral();

      main_div.className="mwpapp_base";
      main_div.style.position = "relative";
      main_div.style.height = "100%";
      main_div.style.overflowX = "visible";
      main_div.style.overflowY = "visible";
    };

    this.init = function(
                 in_action_function, in_close_function, in_interact_function) {

      // Set the action function,
      action_function = in_action_function;
      close_function = in_close_function;
      interact_function = in_interact_function;

      // Set up the divs,
      promptline = mdom.create({
        tag: "div",
        cl: "base", id: "promptline",
        style: {position: "relative", backgroundColor: "transparent"}
      });
      prompttext = mdom.create({
        tag: "div",
        cl: "base", id: "prompttext",
        style: {display: "inline", position: "absolute"}
      });

      promptline.appendChild(prompttext);

      // The prompt <input> element
      promptinput = mdom.create({
        tag: "input.promptc",
        spellcheck: "false",
        style: {
          backgroundColor: "transparent",
          zIndex: "1",
          position: "absolute",
          top: "0px", left: "0px", paddingLeft: "0px", width: "100%",
          boxSizing: "border-box", MozBoxSizing: "border-box", webkitBoxSizing: "border-box"
        }
      });

      promptinput.onfocus = function() { 
        prompt_focus = true;
      };
      promptinput.onblur = function() { 
        prompt_focus = false;
      };

      promptline.appendChild(promptinput);
      promptline.appendChild(mdom.create("br"));
      promptline.appendChild(mdom.create("br"));

      // When 'ENTER' is pressed on the input frame,
      promptinput.onkeypress = function(e) {
        e = e || window.event;

        // If the prompt doesn't have focus then return
        if (!prompt_focus) return true;

        // Only don't reset if tab was pressed,
        if (e.keyCode !== 9) {
          last_tab_complete_db = null;
          last_tab_complete_prompt = null;
        }

        // If 'enter' pressed,
        if (e.keyCode === 13) {
          var command = promptinput.value;
          if (password === false && trim(command) !== "") {
            var sz = commands_history.length;
            if (sz === 0 || commands_history[sz - 1] !== command) {
              // Record the input on the prompt line,
              commands_history.push(command);
            }
          }
          comm_history_i = -1;

          // Repeat the prompt text out to the panel view,
          this_pane.printpromptline(command);
          this_pane.clearprompt();

          // Call the action function,
          if (action_function) {
            action_function(command);
          }
          return false;
        }
        return true;
      };
      
      promptinput.onkeydown = function(e) {
        var sz;
        e = e || window.event;

        // If the prompt doesn't have focus then return
        if (!prompt_focus) return true;

        // If 'tab' pressed (tab-completion)
        if (e.keyCode === 9) {
          // No modifiers,
          if (e.shiftKey === false && e.ctrlKey === false &&
              e.altKey === false) {

            // Perform the tab completion,
            doTabCompletion(promptinput.value);

            // Consume this,
            if (e.preventDefault) e.preventDefault();
            return false;
          }
        }

        // backspace and delete reset tab completion,
        if (e.keyCode === 8 || e.keyCode === 46 ||
            e.keyCode === 37 || e.keyCode === 39) {
          // Reset tab completion,
          last_tab_complete_db = null;
          last_tab_complete_prompt = null;
        }
        
        // If 'up' pressed,
        if (e.keyCode === 38) {
          sz = commands_history.length;
          if (comm_history_i === -1) comm_history_i = sz;
          comm_history_i -= 1;
          if (comm_history_i < 0) comm_history_i = 0;
          if (comm_history_i >= sz) comm_history_i = sz - 1;
          if (comm_history_i >= 0) {
            promptinput.value = commands_history[comm_history_i];
          }
          // Reset tab completion,
          last_tab_complete_db = null;
          last_tab_complete_prompt = null;
          return false;
        }
        // If 'down' pressed
        else if (e.keyCode === 40) {
          sz = commands_history.length;
          if (comm_history_i === -1) comm_history_i = sz;
          comm_history_i += 1;
          if (comm_history_i < 0) comm_history_i = 0;
          if (comm_history_i >= sz) comm_history_i = sz - 1;
          if (comm_history_i >= 0) {
            promptinput.value = commands_history[comm_history_i];
          }
          // Reset tab completion,
          last_tab_complete_db = null;
          last_tab_complete_prompt = null;
          return false;
        }
        // If CTRL pressed,
        else if (e.ctrlKey === true) {
          // CTRL - c
          if (e.keyCode === 67) {
            // If there's no selection on the input,
            // NOTE: Doesn't work on IE <= 8
            if (promptinput.selectionStart === promptinput.selectionEnd) {
              // Capture this (sends kill signal),
              if (interact_function) {
                interact_function("kill");
              }
              if (e.preventDefault) e.preventDefault();
              return false;
            }
          }
        }
        
        return true;
      };

    };

    this.removeprompt = function() {
      if (promptline) {
        main_div.removeChild(promptline);
      }
      prompt_visible = false;
    };

    function internalPromptFocus(scroll_to_prompt) {
      var prompt_text_wid = prompttext.offsetWidth;
      promptinput.style.left = "0px";
      promptinput.style.paddingLeft = prompt_text_wid + "px";
      // Chrome HACK; set width of <input> to 100px then 100% after
      // 50 ms to force correct layout.
      setTimeout(function() {promptinput.style.width = "100%";}, 50);
      promptinput.style.width = "100px";
      promptinput.focus();
      prompt_visible = true;
      if (scroll_to_prompt) {
        this_pane.scrollToEnd();
      }
    }

    this.addprompt = function(prompt_text) {
      cur_prompt_text = prompt_text;
      if (prompt_text || prompt_text === '') {
        prompttext.innerHTML = "";
        prompttext.appendChild(document.createTextNode(prompt_text));
      }
      main_div.appendChild(promptline);
      internalPromptFocus(true);
      prompt_visible = true;

//      var prompt_text_wid = prompttext.offsetWidth;
//      promptinput.style.left = "0px";
//      promptinput.style.paddingLeft = prompt_text_wid + "px";
//      // Chrome HACK; set width of <input> to 100px then 100% after
//      // 50 ms to force correct layout.
//      setTimeout(function() { promptinput.style.width = "100%"; }, 50);
//      promptinput.style.width = "100px";
//      promptinput.focus();
//      prompt_visible = true;
//      this.scrollToEnd();
    };

    this.clearprompt = function() {
      promptinput.value = "";
      this.addprompt("");
      this.setpromptpassword(false);
    };

    this.cls = function() {
      main_div.innerHTML = '';
      curline = null;
      line_empty = true;
      active_a_els = [];
      active_input_els = [];
    };

    this.closeActive = function() {
      // Close the pane,
      if (close_function) {
        close_function();
      }
    };

    this.newline = function() {
      if (curline !== null && line_empty) {
        curline.innerHTML = "<br/>";
      }
      curline = mdom.create("div.base");
      if (prompt_visible) {
        main_div.insertBefore(curline, main_div.lastChild);
      }
      else {
        main_div.appendChild(curline);
      }
      line_empty = true;
      scrollToElement(curline);
    };

    this.printdom = function(dom, style) {
      if (curline === null) {
        this.newline();
      }
      if (dom) {
        line_empty = false;
        var node;
        if (style && style !== '') {
          node = mdom.create( {tag: "span", cl: style});
          node.appendChild(dom);
        }
        else {
          node = dom;
        }
        curline.appendChild(node);
      }
      scrollToElement(curline);
    };

    this.print = function(text, style) {
      var dom;
      if (text && text !== '') {
        dom = document.createTextNode(text);
      }
      this.printdom(dom, style);
    };

    this.println = function(text, style) {
      this.print(text, style);
      this.newline();
    };

    this.printhtml = function(html, style) {
      var dom;
      if (html && html !== '') {
        dom = mdom.create("span");
        dom.innerHTML = html;
        processDOMElements(dom);
      }
      this.printdom(dom, style);
    };

    this.printlnhtml = function(html, style) {
      this.printhtml(html, style);
      this.newline();
    };

    this.printException = function(stack_trace) {
      if (stack_trace && stack_trace !== '') {
        var le = stack_trace.indexOf("\n");
        if (le === -1) {
          this.println(stack_trace, "error");
        }
        else {
          
          var icon1_inf = {icon : "right", fill_color : "white"};
          var icon2_inf = {icon : "down", fill_color : "white"};
          var expand_button = GUIWidgets.createToggleIconButton(1.2);
          expand_button.setClassName("expandable_text");
          expand_button.addIcon(icon1_inf, 1.2);
          expand_button.addIcon(icon2_inf, 1.2);

          var dom = mdom.create("div.stacktrace");
          var head_dom = mdom.create("div.error");

          var button = expand_button.toDOM(head_dom);
          var button_outer = mdom.create(
                {tag: "span", style: {margin: "0 8px 0 2px"}});
          button_outer.appendChild(button);

          head_dom.appendChild(button_outer);
          head_dom.appendChild(document.createTextNode(stack_trace.substring(0, le)));

          var body_dom = mdom.create(
                {tag: "div.error", style: {display: "none"}});
          body_dom.appendChild(document.createTextNode(stack_trace.substring(le + 1)));

          dom.appendChild(head_dom);
          dom.appendChild(body_dom);
          this.printdom(dom);

          // Give some logic to the button,
          expand_button.setAction( function() {
            var i = expand_button.rotateVisibleWidget();
            body_dom.style.display = ((i === 1) ? "block" : "none");
            scrollToElement(dom);
          });
          
        }
      }
    };

    var error_block_head_dom = null;
    var error_block_body_dom = null;

    this.openErrorBlock = function(title) {
      var icon1_inf = {icon : "right", fill_color : "white"};
      var icon2_inf = {icon : "down", fill_color : "white"};
      var expand_button = GUIWidgets.createToggleIconButton(1.2);
      expand_button.setClassName("expandable_text");
      expand_button.addIcon(icon1_inf, 1.2);
      expand_button.addIcon(icon2_inf, 1.2);

      var dom = mdom.create("div.stacktrace");
      var head_dom = mdom.create("div.error");

      var button = expand_button.toDOM(head_dom);
      var button_outer = mdom.create(
            {tag: "span", style: {margin: "0 8px 0 2px"}});
      button_outer.appendChild(button);

      head_dom.appendChild(button_outer);
      if (title !== '') {
        head_dom.appendChild(document.createTextNode(title + '\n'));
      }

      var body_dom = mdom.create(
            {tag: "div.error", style: {display: "none"}});

      dom.appendChild(head_dom);
      dom.appendChild(body_dom);
      this.printdom(dom);

      // Give some logic to the button,
      expand_button.setAction(function() {
        var i = expand_button.rotateVisibleWidget();
        body_dom.style.display = ((i === 1) ? "block" : "none");
        scrollToElement(dom);
      });

      error_block_head_dom = head_dom;
      error_block_body_dom = body_dom;

    };
    this.addErrorBlockTitle = function(line) {
      if (error_block_head_dom) {
        error_block_head_dom.appendChild(document.createTextNode(line + '\n'));
      }
    };
    this.addErrorBlockExtended = function(line) {
      if (error_block_body_dom) {
        error_block_body_dom.appendChild(document.createTextNode(line + '\n'));
      }
    };
    this.closeErrorBlock = function() {
      error_block_head_dom = null;
      error_block_body_dom = null;
    };

    this.printpromptline = function(text) {
      if (password === true) {
        this.println(cur_prompt_text);
      }
      else {
        this.println(cur_prompt_text + text);
      }
    };

    // Sets prompt into password mode (repeats chars as '*')
    this.setpromptpassword = function(status) {
      if (status === true) {
        promptinput.setAttribute("type", "password");
        password = true;
      }
      else {
        promptinput.setAttribute("type", "text");
        password = false;
      }
    };

    // Receives tab completion information from the process,
    this.doInteractReply = function(msg) {
//      console.log("Received interact reply: " + msg);
      if (msg.slice(0, 13) === "*tabcomplete ") {
        // Evan the JSON,
        last_tab_complete_db = eval(msg.substring(13));
        last_tab_index = -1;
        doTabOperation();
//        console.log("To eval = " + msg.substring(13));
      }
    };

    // Grab the focus of the panel
    this.doFocus = function() {
      if (prompt_visible) {
        internalPromptFocus(true);
      }
    };

    // Traverses child DOM elements recursively and process the 'a' and
    // 'input' tags so that they are set up correctly in our javascript
    // environment.
    function processDOMElements(el) {
      var children = el.childNodes;
      if (children) {
        for (var i = 0; i < children.length; ++i) {
          var child = children[i];
          var tag = child.nodeName.toLowerCase();
          if (tag === 'a') {
            if (child.className === 'terminal_ahref') {
              // Add to the list of elements,
              var href_val = child.getAttribute("href");
              child.setAttribute("href", "#");
              child.onclick = function(e) {
                // If clicked, we callback on the same function with the
                // arguments given.

                if (action_function) {
                  action_function(decodeURIComponent(href_val));
                }

                // Return false indicating link action not performed,
                return false;
              };

              active_a_els.push(child);
            }
          }
//          else if (tag === 'input') {
//            // Set the value of the field from the ENV,
//            var iname = child.name;
//            if (iname && ENV.cparams[iname]) {
//              child.value = ENV.cparams[iname];
//            }
//            active_input_els.push(child);
//          }
          // Recurse
          processDOMElements(child);
        }
      }
    }




  }












  // Turns an object (associative array) into a string appropriate for using
  // in a POST
  function encodeAssociativeArray(params) {
    var pv = "";
    for (var param in params) {
      var pval = params[param];
      // Sanity checks,
      if (param.indexOf('=') !== -1 || param.indexOf('\n') !== -1) {
        throw "Invalid character in associative array key";
      }
      pv = pv + param + "=" + pval.length + "|" + pval;
    }
    return pv;
  }

  // An asynchronous AJAX POST on the given uri,
  function doAJAXPost(uri, args, callback_success, callback_fail) {
    var post_ob = encodeAssociativeArray(args);
    var xmlhttp = new XMLHttpRequest();
    xmlhttp.open("POST", uri, true);
    xmlhttp.setRequestHeader("Content-type", "text/plain");// "application/x-www-form-urlencoded");
    xmlhttp.onreadystatechange = function() {
      if (xmlhttp.readyState === 4) {
        if (xmlhttp.status === 200) {
          callback_success(xmlhttp.responseText);
        }
        else {
          callback_fail(xmlhttp.status);
        }
      }
    };
    xmlhttp.send(post_ob);
  }


  function explodeLines(str) {
    var out = [];
    var delim = 0;
    while (true) {
      var ndelim = str.indexOf('\n', delim);
      if (ndelim === -1) {
        out.push(str.substring(delim));
        return out;
      }
      out.push(str.substring(delim, ndelim));
      delim = ndelim + 1;
    }
  }





  // Prints exception,
  function printException(pane, except_lines) {
    for (var i = 1; i < except_lines.length; ++i) {
      pane.println(except_lines[i], "error");
    }
  }

  // Load and invoke a script on the given pane
  function invokeScript(script_file, invoke_fun, fun_args,
                          panel_group, pane, frame_name, process_id_str) {

    // The server communicator,
    var comm = {};
    // Send a single message,
    comm.send = function(command_str, ok_action) {
      sendMap(panel_group, frame_name, process_id_str,
              {"c":command_str}, ok_action);
    };
    // Send a set of messages,
    comm.sendSet = function(msg_arr, complete_action) {
      var p = 0;
      var send_pos = function() {
        if (p >= msg_arr.length) {
          complete_action();
        }
        else {
          ++p;
          sendMap(panel_group, frame_name, process_id_str,
                  {"c":msg_arr[p - 1]}, send_pos);
        }
      };
      send_pos();
    };

    // Write message receive command to the comm object,
    pane.doMessageProcess = function(command_string) {
      if (comm.onreceive) comm.onreceive(command_string);
    };

    // On load, evaluate the script function and pass the appropriate args,
    var doInvoke = function() {
      // Invoke it,
      var fun = eval(invoke_fun);
      // The communication ob,
      fun(pane, comm, frame_name, process_id_str, fun_args);
    };

    MWPENV.onScriptLoad(script_file, doInvoke);
    MWPENV.requireScript(script_file);

  }

  // Parses a terminal block string,
  function processTerminalBlock(panel_group, process_id_str, frame_name, cmd) {

    var pane = panel_group.getPane(frame_name);

    // Otherwise process the message as console,
    var p = 0;
    var d1, d2, d3, slen, cend, style, text, msgsz, mst;

    var consumetextstyle = function() {
      d1 = cmd.indexOf('|', p);
      d2 = cmd.indexOf('|', d1 + 1);
      slen = parseInt(cmd.substring(d1 + 1, d2));
      cend = d2 + 1 + slen;
      style = cmd.substring(p, d1);
      text = cmd.substring(d2 + 1, cend);
      p = cend;
    };

    var consumetext = function() {
      var d = cmd.indexOf('|', p);
      slen = parseInt(cmd.substring(p, d));
      cend = d + 1 + slen;
      text = cmd.substring(d + 1, cend);
      p = cend;
      return text;
    };

    while (p < cmd.length) {

      var ch = cmd.charAt(p);
      ++p;
      if (ch === '$') { // control code
        var code = cmd.charAt(p);
        ++p;

        if (code === 'C') { // cls
          pane.cls();
        }
        else if (code === 'p') { // prompt type password,
          pane.setpromptpassword(true);
        }
        else if (code === '_') { // close active element of frame,
          pane.closeActive();
        }
        else {
          pane.println("Unknown control code: " + code, "error");
        }

      }

      else if (ch === '^') { // Message receive
        d1 = cmd.indexOf('|', p);
        msgsz = parseInt(cmd.substring(p, d1));
        // If the pane has a message processor,
        if (pane.doMessageProcess) {
          mst = d1 + 1;
          pane.doMessageProcess(cmd.substring(mst, mst + msgsz));
        }
        p = d1 + 1 + msgsz;
      }
      
      else if (ch === 't') { // interact-reply receive,
        d1 = cmd.indexOf('|', p);
        msgsz = parseInt(cmd.substring(p, d1));
        // If the pane has a message processor,
        if (pane.doInteractReply) {
          mst = d1 + 1;
          pane.doInteractReply(cmd.substring(mst, mst + msgsz));
        }
        p = d1 + 1 + msgsz;
      }

      else if (ch === '#') { // new frame
        d1 = cmd.indexOf('|', p);
        d2 = cmd.indexOf('|', d1 + 1);
        d3 = cmd.indexOf('|', d2 + 1);
        var label = cmd.substring(p, d1);
        var new_frame = cmd.substring(d1 + 1, d2);
        var icon = cmd.substring(d2 + 1, d3);
        p = d3 + 1;

        var new_pane = panel_group.addConsolePane(new_frame, label, icon);

        // The action function,
        var action_function = function(command) {
          // Send the command to the server,
          sendCommand(panel_group, new_frame, process_id_str, command);
        };
        var close_function = function() {
          panel_group.removePane(new_frame);
        };
        var interact_function = function(feature) {
          sendInteractSignal(panel_group, new_frame, process_id_str, feature);
        };

        // Set up the action and close function for this pane,
        new_pane.init(action_function, close_function, interact_function);

      }

      else if (ch === 'f') { // switch to frame,
        d1 = cmd.indexOf('|', p);
        var frame_val = cmd.substring(p, d1);
        p = d1 + 1;
        // Switch to the given frame,
        panel_group.setCurrentPane(frame_val);
      }

      else if (ch === '+') { // print
        consumetextstyle();
        pane.print(text, style);
      }

      else if (ch === '!') { // println
        consumetextstyle();
        pane.println(text, style);
      }

      else if (ch === 'h') { // print (html)
        consumetextstyle();
        pane.printhtml(text, style);
      }

      else if (ch === 'H') { // println (html)
        consumetextstyle();
        pane.printlnhtml(text, style);
      }

      else if (ch === 'e') { // exception block
        var ex_msg = consumetext();
        pane.printException(ex_msg);
      }

      else if (ch === '>') { // change prompt
        var prompt = consumetext();
        pane.addprompt(prompt);
      }

      else if (ch === 's') { // run script
        d1 = cmd.indexOf('|', p);
        d2 = cmd.indexOf('|', d1 + 1);
        d3 = cmd.indexOf('|', d2 + 1);
        var script_file = cmd.substring(p, d1);
        var invoke_fun = cmd.substring(d1 + 1, d2);
        var str_len = parseInt(cmd.substring(d2 + 1, d3));
        var command_str = cmd.substring(d3 + 1, d3 + 1 + str_len);
        p = d3 + 1 + str_len;

        invokeScript(script_file, invoke_fun, command_str,
                     panel_group, pane, frame_name, process_id_str);
      }

      else if (ch === 'E') {
        var subcode = cmd.charAt(p);
        ++p;
        var line = consumetext();
        if (subcode === 's') {
          pane.openErrorBlock(line);
        }
        else if (subcode === 't') {
          pane.addErrorBlockTitle(line);
        }
        else if (subcode === 'e') {
          pane.addErrorBlockExtended(line);
        }
        else if (subcode === 'f') {
          pane.closeErrorBlock();
        }
      }

      else {
        pane.println("Unknown command: " + ch, "error");
        return;
      }
    }

  }

  // Parses the incoming commands,
  function processConsoleCommands(panel_group, process_id_str, cmd) {
    var p = 0;
    var end = cmd.length;

    var console_pane = panel_group.getPane("0");

    while (p < end) {

      // Parse out the block header,
      var initd1 = cmd.indexOf('|', p);
      var initd2 = cmd.indexOf('|', initd1 + 1);

      // PENDING: Pipe the instructions appropriately,
      var block_name = cmd.substring(p, initd1);
      var block_sz = parseInt(cmd.substring(initd1 + 1, initd2));
      
      p = initd2 + 1;
      var block_end = p + block_sz;

      // Dispatch the packet to the pane,
      if (block_name.charAt(0) === 'F') {
        var frame_name = block_name.substring(1);
        processTerminalBlock(panel_group, process_id_str,
                             frame_name, cmd.substring(p, block_end));
      }
      else {
        console_pane.println("Unknown block name: " + block_name, "error");
      }

      p = block_end;

    }

  }

  function doConsoleUpdateLoop(panel_group, process_id_str, session_state) {
    doAJAXPost(
        // The AJAX message consumer query,
        "M",
        {"ss":session_state},
        // Success message,
        function(success_text) {
          var console_pane = panel_group.getPane("0");
          var lines = explodeLines(success_text);
          if (lines[0] === "OK") {
            var new_session_state = lines[1];
            var pdelim = lines[0].length + 1 + lines[1].length + 1;
            processConsoleCommands(panel_group, process_id_str,
                                   success_text.substring(pdelim));
            // Recurse AJAX,
            doConsoleUpdateLoop(panel_group, process_id_str, new_session_state);
          }
          else if (lines[0] === "FAIL:Exception") {
            console_pane.println("Server communication exception", "error");
            printException(console_pane, lines);
            setTimeout(function() {
                     doConsoleUpdateLoop(panel_group, process_id_str,
                                         session_state);}, 4000);
          }
          else {
            console_pane.println("Invalid server response", "error");
          }
        },
        // Fail message,
        function(fail_code) {
          var console_pane = panel_group.getPane("0");
          console_pane.println(
                          "Server communication error: " + fail_code, "error");
          setTimeout(function() {
                     doConsoleUpdateLoop(panel_group, process_id_str,
                                         session_state);}, 4000);
        }
    );
  }

  // Sends a series of arguments to the server,
  function sendMap(panel_group, frame, process_id_str, args, ok_action) {
    var map = {
      "p":process_id_str,
      "f":frame
    };
    for (var arg in args) {
      if (arg !== "p" && arg !== "f") {
        map[arg] = args[arg];
      }
    }

    doAJAXPost(
      // The AJAX message consumer query,
      "F", map,
      // Success message,
      function(success_text) {
        var console_pane = panel_group.getPane("0");
        var lines = explodeLines(success_text);
        if (lines[0] === "OK") {
          // Display any immediate response,
          var pdelim = lines[0].length + 1;
//          var pane = panel_group.getPane(frame);
          var result_txt = success_text.substring(pdelim);
          processConsoleCommands(panel_group, process_id_str, result_txt);
          if (ok_action) {
            ok_action(result_txt);
          }
        }
        else if (lines[0] === "FAIL:Exception") {
          console_pane.println(
                        "(" + frame + ") Exception on command", "error");
          printException(console_pane, lines);
        }
        else {
          console_pane.println(
                        "(" + frame + ") Invalid server response", "error");
        }
      },
      // Fail message,
      function(fail_code) {
        var console_pane = panel_group.getPane("0");
        console_pane.println(
              "(" + frame + ") Server communication error: " + fail_code,
              "error");
      }
    );
  }

  // Sends command to server,
  function sendCommand(panel_group, frame, process_id_str, command_line) {
    sendMap(panel_group, frame, process_id_str, {"c":command_line});
  }

  // Sends an interact signal to the server,
  function sendInteractSignal(panel_group, frame, process_id_str, feature) {
    sendMap(panel_group, frame, process_id_str, {"s":"int", "sf":feature});
  }

  // Starts the console,
  function startConsole(process_id_str, session_state) {

    var body = document.body;

    body.innerHTML = "";

    // From here on, user must confirm leaving the page,
    window.onbeforeunload = function() {
      return "Leaving the page will sign you out.";
    };

    // The side option element,
    var selector_div = mdom.create({
      tag: "div",
      style: {position: "absolute",
               top: "0px", left: "0px", right: "28px", height: "100%"}
    });

    // The active window element,
    var active_div = mdom.create({
      tag: "div",
      style: {position: "absolute",
               top: "0px", left: "28px", right: "0px", height: "100%"}
    });

    body.appendChild(selector_div);
    body.appendChild(active_div);

    // The selector UI element,
    var selector = new SelectorUI();
    selector.div = selector_div;
    selector.init();

    // The group of panels,
    var panel_group = new PanelGroup();
    panel_group.frame_selector = selector;
    panel_group.window_div = active_div;

    // Add the main console item
    panel_group.addConsolePane("0", "Console", "console");
    var pane = panel_group.getPane("0");

    // The action and close functions,
    var action_function = function(command) {
      // Send the command to the server,
      sendCommand(panel_group, "0", process_id_str, command);
    };
    var close_function = function() {
      panel_group.removePane("0");
    };
    var interact_function = function(feature) {
      sendInteractSignal(panel_group, "0", process_id_str, feature);
    };
    // Handle terminal commands,
    pane.init(action_function, close_function, interact_function);

    setTimeout(function() {
      // Enter the asynchronous loop,
      doConsoleUpdateLoop(panel_group, process_id_str, session_state);
    }, 250);

  }



  // --

  function loginFunction() {

    var error_div;
    var form;
    var child_window;

    function tryLogin(user, pass) {
      // Use AJAX POST to authenticate the user,
      doAJAXPost(
          "Auth",
          {"user":user,
            "pass":pass,
            "action":"main"},
          // Success message,
          function(success_text) {
            disableForm(form, false);
            var lines = explodeLines(success_text);
            // If authorization failed,
            if (lines[0] === "FAIL:AUTH") {
              error_div.innerHTML =
                "Authorization failed. " +
                "Make sure you entered the correct username and password.<br/>";
            }
            else if (lines[0] === "OK") {
              var process_id_str = lines[1];
              var session_state = lines[2];
              startConsole(process_id_str, session_state);
            }
          },
          // Fail message,
          function(fail_code) {
            disableForm(form, false);
            error_div.innerHTML =
                "Server error: " + fail_code + "<br/>";
          }
      );
    }

    function disableForm(form, status) {
      if (form) {
        form.user.disabled = status;
        form.pass.disabled = status;
        form.btn.disabled = status;
      }
    }

//    // Display the login div,
//    document.getElementById("mwplogin").style.display = "block";

    // Load the login template,
    MWPENV.loadTemplate('templates/login.html', function(template) {
      // Install it,
      MWPENV.installTemplate(template, document.body);
      // Set it up,
      
      form = document.getElementById("login_form");
      error_div = document.getElementById("error_report");

      form.elements.namedItem("user").focus();

      // The popup link id,
      var link_popup_a = document.getElementById("link_popup_a");

      // If this is a popup window then remove the link to make a popup,
      if (opener) {
        link_popup_a.style.display = "none";
      }

      form.onsubmit = function(e) {

        error_div.innerHTML = "";
        // Disable the form inputs,
        disableForm(form, true);

        tryLogin(form.user.value, form.pass.value, true);

        // Prevent the default action,
        return false;
      };

      link_popup_a.onclick = function(e) {
        // Open popup,
        child_window = window.open(
            "mwp.html", "Mckoi Web Platform",
            "height=700,width=1000,left=10,right=10,resizable=yes,scrollbars=yes,toolbar=yes,menubar=no,location=no,directories=no,status=no");
        // Remove the link to make the popup,

        // Make a new page with a link that focuses the window when clicked,
        document.body.innerHTML = "<a href='#' id='link_popup_b'>Click here to go to the Mckoi Web Platform window</a>";
        document.getElementById("link_popup_b").onclick = function(e) {
          child_window.focus();
          return false;
        };
        return false;
      };
      
    });

//    // If you want to login automatically then you can use the following
//    // code,
//    tryLogin("admin", "zxc");
//    return;

  }

  window.onload = loginFunction;

})();
