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

/**
 * Various GUI widgets for the Mckoi web platform. Requires 'mdom.js'.
 */

var GUIWidgets = (function() {

  // The SVG namespace,
  var SVGNS = "http://www.w3.org/2000/svg";

  var ERROR_ICON_INF =
    { icon : "error", fill_color : "#e03030",
      mask_color : "#f0f0ff", mask_border_size : 1.25
    };
  var WARNING_ICON_INF =
    { icon : "warning", fill_color : "#EDCD18",
      mask_color : "#000020",
      mask_border_color : "#544804" ,mask_border_size : 1.25
    };






  // Global mouse over and mouse down values.
  var moused_over = null;
  var moused_down = null;
  var focused_widget = null;


  function actionFocusWidget(widget, div) {
    focused_widget = [widget, div];
  }
  function actionBlurWidget(widget) {
    if (focused_widget !== null && widget === focused_widget[0]) {
      focused_widget = null;
    }
  }

  /**
   * Mouse event handlers.
   */
  function doMouseOver(widget, evt_div, e) {
    if (!e) e = window.event;
    // Are we already moused over this?
    if (!moused_over || moused_over[1] !== evt_div) {
//      console.log("RAW OVER");
      if (widget.eventOnMouseOver) widget.eventOnMouseOver(evt_div, e);
      moused_over = [widget, evt_div];
    }
//    e.cancelBubble = true;
//    if (e.stopPropagation) e.stopPropagation();
  }
  function doMouseOut(widget, evt_div, e) {
    // This returns if the element we left is a child of the item
    if (!e) e = window.event;
    // Did we come from an element outside this item?
    var reltg = (e.relatedTarget) ? e.relatedTarget : e.toElement;
    if (reltg != null) {
      while (reltg != null && reltg.nodeName != 'BODY') {
        if (reltg == evt_div) {
          return;
        }
        reltg = reltg.parentNode;
      }
    }
    // Are we already moused over this?
    if (moused_over && moused_over[1] === evt_div) {
//      console.log("RAW OUT");
      if (widget.eventOnMouseOut) widget.eventOnMouseOut(evt_div, e);
      moused_over = null;
      moused_down = null;
    }
//    e.cancelBubble = true;
//    if (e.stopPropagation) e.stopPropagation();
  }
  function doMouseDown(widget, evt_div, e) {
//    console.log("MOUSE DOWN");
//    console.log(evt_div);
    if (!e) e = window.event;
    // IE Hack: Focus the window because IE doesn't do this for us!
    window.focus();
    if (widget.eventOnMouseDown) widget.eventOnMouseDown(evt_div, e);

    e.preventDefault();
    e.cancelBubble = true;
    if (e.stopPropagation) e.stopPropagation();

    moused_down = [widget, evt_div];
    // If there's a focused widget, call blur on it,
//    console.log("FOCUSED = " + focused_widget + " DO WE BLUR?");
    if (focused_widget !== null && focused_widget[0] !== widget) {
//      console.log("CALLING BLUR");
//      console.log(focused_widget[1]);
      focused_widget[1].blur();
    }
//    else {
//      console.log("BLURRING ACTIVE ELEMENT: " + document.activeElement);
//      if (document.activeElement) document.activeElement.blur();
//    }
    if (focused_widget === null || focused_widget[0] !== widget) {
//      console.log("CALLING FOCUS");
//      console.log(evt_div);
      if (widget.isFocusable && widget.isFocusable()) {
        evt_div.focus();
      }
    }
  }
  function doMouseUp(widget, evt_div, e) {
//    console.log("MOUSE UP");
//    console.log(evt_div);
    if (!e) e = window.event;
    if (widget.eventOnMouseUp) widget.eventOnMouseUp(evt_div, e);
    
    e.preventDefault();
    e.cancelBubble = true;
    if (e.stopPropagation) e.stopPropagation();

    moused_down = null;
  }

  function doFocus(widget, evt_div, e) {
    if (!e) e = window.event;
//    console.log("FOCUS");
//    console.log(evt_div);

    focused_widget = [widget, evt_div];
    if (widget.eventOnFocus) widget.eventOnFocus(evt_div, e);


//    focused_widget.push([widget, evt_div]);
//    if (widget.eventOnFocus) {
//      var focused = false;
//      for (var i = 0; i < focused_widget.length - 1; ++i) {
//        if (focused_widget[i][1] === evt_div) focused = true;
//      }
//      if (!focused) widget.eventOnFocus(evt_div, e);
//    }
  }
  function doBlur(widget, evt_div, e) {
    if (!e) e = window.event;
//    console.log("BLUR");
//    console.log(evt_div);

    if (focused_widget !== null && widget === focused_widget[0]) {
      focused_widget = null;
    }
    if (widget.eventOnBlur) widget.eventOnBlur(evt_div, e);

//    var blurred = -1;
//    var count = 0;
//    for (var i = 0; i < focused_widget.length; ++i) {
//      if (focused_widget[i][1] === evt_div) { blurred = i; count += 1; }
//    }
//    if (blurred > -1) {
//      focused_widget.splice(blurred, 1);
//    }
//    if (widget.eventOnBlur && count == 1) widget.eventOnBlur(evt_div, e);
  }



  /**
   * Cross browser compatible 'createElementNS'
   */
  function bfixCreateElementNS(namespace, tag) {
    if (typeof document.createElementNS != 'undefined') {
      return document.createElementNS(namespace, tag);
    }
    return document.createElement(tag);
  }

  /**
   * Traverses the children of a DOM tree and find the first element with a
   * tab index and focus it.
   */
  function focusOnRecurse(el, min_ti) {
    var tindex = null;
    if (el.getAttribute) tindex = el.getAttribute("tabindex");
    if (tindex) {
      if (tindex == -1 && !min_ti[2]) {
        min_ti[2] = el;
      }
      else if (tindex >= 0 && (!min_ti[0] || tindex < min_ti[0])) {
        min_ti[0] = tindex;
        min_ti[1] = el;
      }
    }
    var nodes = el.childNodes;
    for (var i = 0; i < nodes.length; ++i) {
      focusOnRecurse(nodes[i], min_ti);
    }
    return min_ti;
  }
  function focusOn(el) {
    var result = focusOnRecurse(el, []);
    if (result[0]) {
      result[1].focus();
    }
    else if (result[2]) {
      result[2].focus();
    }
    else {
      // Nothing to focus on!
    }
  }


  /**
   * Inheric all the public properties from the given object source.
   */
  function inherit(dest_ob, src_ob) {
    for (var i in src_ob) dest_ob[i] = src_ob[i];
  }

  /**
   * A simple panel on which components can be added at relative positions.
   * @constructor
   */
  function Panel(width, height) {

    var widgets_list = [];
    var style_class;

    /**
     * Sets the panel class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }
    this.getClassName = function() {
      return style_class;
    }

    this.getWidth = function() {
      return width;
    }

    this.getHeight = function() {
      return height;
    }

    /**
     * Returns a DIV component for this panel.
     */
    this.toDOM = function(parent_dom) {
      var div = mdom.create(
        { tag:"div",
          cl:style_class,
          unselectable:"on",
          style: { position:"absolute", display:"block",
                   width:width, height:height
                 }
        }
      );

      for (var i = 0; i < widgets_list.length; i += 2) {
        var widget = widgets_list[i];
        var pos = widgets_list[i + 1];

        var wid = widget.toDOM(div);
        // Position the widget
        wid.style.position = "absolute";
        if (pos[0]) wid.style.top = pos[0] + "px";
        if (pos[1]) wid.style.right = pos[1] + "px";
        if (pos[2]) wid.style.bottom = pos[2] + "px";
        if (pos[3]) wid.style.left = pos[3] + "px";

        div.appendChild(wid);
      }

      return div;
    }

    /**
     * Adds a widget aligned against the container's border.
     */
    this.addWidget = function(widget, pos_arr) {
      widgets_list.push(widget);
      widgets_list.push(pos_arr);
    }

    /**
     * Adds a widget aligned to the top/left coordinates of the panel.
     */
    this.addLTWidget = function(widget, left, top) {
      this.addWidget(widget, [top, null, null, left]);
    }

    /**
     * Adds a widget aligned to the top/right coordinates of the panel.
     */
    this.addRTWidget = function(widget, right, top) {
      this.addWidget(widget, [top, right, null, null]);
    }

    /**
     * Adds a widget aligned to the bottom/left coordinates of the panel.
     */
    this.addLBWidget = function(widget, left, bottom) {
      this.addWidget(widget, [null, null, bottom, left]);
    }

    /**
     * Adds a widget aligned to the bottom/right coordinates of the panel.
     */
    this.addRBWidget = function(widget, right, bottom) {
      this.addWidget(widget, [null, right, bottom, null]);
    }

    /**
     * Adds a widget aligned to the top/left and right coordinates of the
     * panel.
     */
    this.addLTRWidget = function(widget, left, top, right) {
      this.addWidget(widget, [top, right, null, left]);
    }

  }

  /**
   * A line widget is an element that does not have a width but allows a
   * widget of known size to be added to the right or left.
   * @constructor
   */
  function LineWidget() {

    var west_widget;
    var east_widget;
    var center_widget;
    var style_class;

    var calc_height;
    var calc_width;

    /**
     * Sets the panel class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }

    this.setEastWidget = function(widget) {
      east_widget = widget;
    }
    this.setWestWidget = function(widget) {
      west_widget = widget;
    }
    this.setCenterWidget = function(widget) {
      center_widget = widget;
    }

    // NOTE: This only works after 'toDOM' called.
    this.getHeight = function() {
      return calc_height;
    }
    this.getWidth = function() {
      return calc_width;
    }

    function layoutWidget(pos, div, widget) {
      // Center align the widget
      var top = parseInt((calc_height - widget.getHeight()) / 2, 10);
      var adiv = widget.toDOM(div);
      adiv.style.position = "absolute";
      adiv.style.left = pos + "px";
      adiv.style.top = top + "px";

      pos += widget.getWidth();
      div.appendChild(adiv);
      return pos;
    }

    this.toDOM = function(parent_dom) {

      calc_width = 0;
      calc_height = 0;
      var whei;
      if (east_widget) {
        calc_width += east_widget.getWidth();
        whei = east_widget.getHeight();
        if (whei > calc_height) calc_height = whei;
      }
      if (west_widget) {
        calc_width += west_widget.getWidth();
        whei = west_widget.getHeight();
        if (whei > calc_height) calc_height = whei;
      }
      if (center_widget) {
        calc_width += center_widget.getWidth();
        whei = center_widget.getHeight();
        if (whei > calc_height) calc_height = whei;
      }

      var div = mdom.create({
        tag: "div",
        cl: style_class,
        style: { width:calc_width, height:calc_height }
      });

      var cl = 0;
      if (west_widget) cl += layoutWidget(cl, div, west_widget);
      if (center_widget) cl += layoutWidget(cl, div, center_widget);
      if (east_widget) cl += layoutWidget(cl, div, east_widget);

      return div;
    }



  }




  /**
   * A clickable widget.
   * @constructor
   */
  function Clickable(width, height) {

    var this_button = this;
    var style_class;

    var normal_widgets = [];
    var visible_w = 0;

    var is_focusable = false;

    var focused = false;
    var mdown = false;
    var selected = false;
    var hovered = false;

    var click_action;

    var backed_div;

    /**
     * Sets the clickable class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }

    /**
     * Set if this widget is focusable.
     */
    this.setFocusable = function(b) {
      is_focusable = b;
    }

    /**
     * Returns true if this widget is focusable.
     */
    this.isFocusable = function() {
      return is_focusable;
    }

    /**
     * Sets the action performed when the button activated.
     */
    this.setAction = function(fun) {
      click_action = fun;
    }

    /**
     * Sets the button content.
     */
    this.setNormalContent = function(widget) {
      normal_widgets[0] = widget;
    }

    /**
     * Adds a possible button content.
     */
    this.addNormalContent = function(widget) {
      normal_widgets.push(widget);
    }

    /**
     * Sets the button icon.
     */
    this.setIcon = function(icon_inf, scale) {
      var icon = new Icon(scale);
      icon.setFromInf(icon_inf);
      normal_widgets[0] = icon;
    }
    
    /**
     * Adds a possible button icon.
     */
    this.addIcon = function(icon_inf, scale) {
      var icon = new Icon(scale);
      icon.setFromInf(icon_inf);
      normal_widgets.push(icon);
    }



    // ----- DOM stuff -----

    function getWidgetSet(div) {
      var content_dom = div.childNodes[0];
      return content_dom.childNodes;
    }

    /**
     * Sets the given widget as the visible one.
     */
    this.setVisibleWidget = function(ind) {
      if (ind == visible_w) return;
      if (backed_div) {
        var widget_set = getWidgetSet(backed_div);
        // Make the current visible w invis,
        widget_set[visible_w].style.display = "none";
        // Make selected visible,
        widget_set[ind].style.display = "block";
      }
      visible_w = ind;
    }
    this.rotateVisibleWidget = function() {
      var ind = visible_w;
      ++ind;
      if (ind >= normal_widgets.length) ind = 0;
      this.setVisibleWidget(ind);
      return ind;
    }

    /**
     * Creates a DOM DIV for the button content.
     */
    this.createContent = function(parent_dom) {
      var widget_count = normal_widgets.length;
      if (widget_count == 0) {
        return mdom.create("span");
      }

      var create_single = function(widget) {
        var button_content = widget;
        var content_div = button_content.toDOM(parent_dom);
        // If the button content has a height, we align it horizontally,
        if (button_content.getHeight) {
          var content_height = button_content.getHeight();
          content_div.style.position = "relative";
          content_div.style.top = parseInt((height - content_height) / 2, 10) + "px";
        }
        return content_div;
      }

      if (widget_count == 1) {
        return create_single(normal_widgets[0]);
      }
      else {
        var d = mdom.create("span");
        for (var i = 0; i < widget_count; ++i) {
          var c = create_single(normal_widgets[i]);
          // Don't display these,'
          c.style.display = ((i == visible_w) ? "block" : "none");
          d.appendChild(c);
        }
        return d;
      }
    }

    var setStyle = function(div, foc, sel, hov) {
      if (style_class) {
        var cn = style_class;
        if (hov) cn += " " + style_class + "_hover";
        if (foc) cn += " " + style_class + "_focus";
        if (sel) cn += " " + style_class + "_select";
        div.className = cn;
      }
    }
    var updateStyle = function(div) {
      setStyle(div, focused, selected || mdown, hovered);
    }
    

    this.eventOnMouseOver = function(div) {
      hovered = true;
      updateStyle(div);
    }
    this.eventOnMouseOut = function(div) {
      hovered = false;
      mdown = false;
      updateStyle(div);
    }
    this.eventOnMouseDown = function(div) {
      mdown = true;
      updateStyle(div);
    }
    this.eventOnMouseUp = function(div) {
      mdown = false;
      updateStyle(div);
      // If there's a click action then call it,
      if (click_action && moused_down && moused_down[0] == this_button) {
        click_action(this_button, div);
      }
    }
    this.eventOnFocus = function(div) {
      focused = true;
      updateStyle(div);
    }
    this.eventOnBlur = function(div) {
      focused = false;
      updateStyle(div);
    }

    this.eventOnSelect = function(div) {
      selected = true;
      updateStyle(div);
    }
    this.eventOnDeselect = function(div) {
      selected = false;
      updateStyle(div);
    }

    this.select = function(div) {
      if (!div) div = backed_div;
      this.eventOnSelect(div);
    }
    this.deselect = function(div) {
      if (!div) div = backed_div;
      this.eventOnDeselect(div);
    }

    this.focus = function(div) {
      if (!div) div = backed_div;
      div.focus();
    }
    this.blur = function(div) {
      if (!div) div = backed_div;
      div.blur();
    }

    /**
     * Turns this widget into a div.
     */
    this.toDOM = function(parent_dom) {
      if (backed_div) throw "toDOM already called";

      var div = mdom.create({
        tag: "div",
        cl: style_class,
        style: { display: "inline-block", textAlign: "center",
                 width: width, height:height }
      });

      if (is_focusable) div.setAttribute("tabindex", "0");

      var content_dom = this.createContent(div);
      div.appendChild(content_dom);

      if (this_button.eventOnMouseOver)
        div.onmouseover = function(e) {doMouseOver(this_button, div, e);};
      if (this_button.eventOnMouseOut)
        div.onmouseout = function(e) {doMouseOut(this_button, div, e);};
      div.onmousedown = function(e) {doMouseDown(this_button, div, e);}
      div.onmouseup = function(e) {doMouseUp(this_button, div, e);}
      div.onfocus = function(e) {doFocus(this_button, div, e);}
      div.onblur = function(e) {doBlur(this_button, div, e);}

      backed_div = div;

      return div;
    }

  }


  /**
   * An icon element. The dimensions of an icon can be calculated
   * programatically.
   * @constructor
   */
  function Icon(scale) {

    if (!scale) scale = 1;

    var icon_path;
    var fill_color;
    var mask_color;
    var mask_border_size; // = 1.1;
    var mask_border_color;
    var sub_icons = [null, null, null, null, null];

    var style_class;

    /**
     * Sets the icon class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }

    this.getWidth = function() {
      return 16 * scale;
    }
    this.getHeight = function() {
      return 16 * scale;
    }

    this.setMaskBorderColor = function(color) {
      mask_border_color = color;
    }

    this.setIcon = function(p_icon_path, p_fill_color, p_mask_color) {
      icon_path = p_icon_path;
      if (p_fill_color) fill_color = p_fill_color;
      if (p_mask_color) mask_color = p_mask_color;
    }

    /**
     * Sets the style of this icon from an information object that describes
     * the icon.
     */
    this.setFromInf = function(icon_inf) {
      icon_path = mwpicons[icon_inf.icon];
      if (icon_inf.style) style_class = icon_inf.style;
      if (icon_inf.fill_color) fill_color = icon_inf.fill_color;
      if (icon_inf.mask_color) mask_color = icon_inf.mask_color;
      if (icon_inf.mask_border_size) {
        mask_border_size = icon_inf.mask_border_size;
        mask_border_color = "black";
      }
      if (icon_inf.mask_border_color) mask_border_color = icon_inf.mask_border_color;
    }

    this.toDOM = function(parent_dom) {

      if (!scale) scale = 1;
      if (typeof(scale) !== 'number') throw "scale must be a number";

      var icon_size = parseInt(16 * scale, 10);

      var el = bfixCreateElementNS(SVGNS, "svg");
      if (style_class) el.setAttribute("class", style_class);
      el.setAttribute("version", "1.1");
      el.setAttribute("width", icon_size);
      el.setAttribute("height", icon_size);
      el.appendChild(bfixCreateElementNS(SVGNS, "desc"));
      el.appendChild(bfixCreateElementNS(SVGNS, "defs"));

      var g_el = bfixCreateElementNS(SVGNS, "g");
      g_el.setAttribute("transform", "scale(" + scale + ")");

      // The mask
      var mask_el;
//      if (mask_color || mask_border_size) {
        mask_el = bfixCreateElementNS(SVGNS, "path");
        mask_el.setAttribute("class", "icon_mask");
        if (mask_color) {
          mask_el.setAttribute("fill", mask_color);
        }
        else {
          mask_el.setAttribute("fill", "none");
        }
        if (mask_border_size) {
          mask_el.setAttribute("stroke", mask_border_color);
          mask_el.setAttribute("stroke-width", mask_border_size);
        }
        else {
          mask_el.setAttribute("stroke", "none");
        }
        mask_el.setAttribute("d", icon_path[0]);
//      }

      // The path
      var path_el = bfixCreateElementNS(SVGNS, "path");
      path_el.setAttribute("class", "icon_path");
      if (fill_color) path_el.setAttribute("fill", fill_color);
      path_el.setAttribute("stroke", "none");
      if (icon_path[1])
        path_el.setAttribute("d", icon_path[1]);
      else
        path_el.setAttribute("d", icon_path[0]);

//      // Sub icon element,
//      var subg_el = bfixCreateElementNS(SVGNS, "g");
//      subg_el.setAttribute("transform", "translate(8, 8) scale(0.50)");
//      var subm_el = bfixCreateElementNS(SVGNS, "path");
//      subm_el.setAttribute("fill", "#000000");
//      subm_el.setAttribute("stroke", "#000000");
//      subm_el.setAttribute("stroke-width", "2");
//      subm_el.setAttribute("d", mwpicons.warning[0]);
//      var sub_el = bfixCreateElementNS(SVGNS, "path");
//      sub_el.setAttribute("fill", "#e0e000");
//      sub_el.setAttribute("stroke", "none");
//      sub_el.setAttribute("d", mwpicons.warning[1]);

//      subg_el.appendChild(subm_el);
//      subg_el.appendChild(sub_el);

      if (mask_el) g_el.appendChild(mask_el);
      g_el.appendChild(path_el);
//      g_el.appendChild(subg_el);
      el.appendChild(g_el);

      return el;
    }

  }

  /**
   * A text label widget with a known width and height.
   * @constructor
   */
  function Label(text, width, height) {

    var style_class;
    var backed_div;
    var align;
    var bold_defined = false;
    var bold;

    /**
     * Sets the clickable class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }

    this.getWidth = function() {
      return width;
    }
    this.getHeight = function() {
      return height;
    }

    this.setText = function(t) {
      text = t;
      if (backed_div) {
        if (backed_div.firstChild) backed_div.removeChild(backed_div.firstChild);
        backed_div.appendChild(document.createTextNode(text));
      }
    }

    this.setBold = function(b) {
      bold_defined = true;
      bold = b;
      if (backed_div) {
        if (bold) backed_div.style.fontWeight = "bold";
        else backed_div.style.fontWeight = "normal";
      }
    }

    this.setLeftAlign = function() {
      align = "left";
    }

    this.toDOM = function(parent_dom) {
      if (backed_div) throw "toDOM already called";

      var div = mdom.create({
        tag: "div",
        cl: style_class,
        style:
        { display: "inline-block", whiteSpace: "pre",
          width: width, height: height,
          textAlign: align,
          fontWeight: bold_defined ? (bold ? "bold" : "normal") : undefined
        }
      });

      div.appendChild(document.createTextNode(text));

      backed_div = div;
      return div;
    }

  }


  /**
   * A pulldown menu widget.
   * @constructor
   */
  function Pulldown(width, height) {

    var this_pulldown = this;
    var superob = new Clickable(width, height);
    inherit(this, superob);

    // The menu
    var menu_widget;

    /**
     * Sets the menu widget.
     */
    this.setMenu = function(menu) {
      menu_widget = menu;
    }

    /**
     * Returns the menu widget.
     */
    this.getMenu = function() {
      return menu_widget;
    }

    /**
     * Overwrite the setIcon function.
     */
    this.setIcon = function(icon_inf, scale) {
      var icon = new Icon(scale);
      icon.setFromInf(icon_inf);

      // The down icon,
      var down = new Icon(.6);
      down.setIcon(mwpicons.down);
      down.setClassName("icon_element");
      down.setMaskBorderColor(null);

      var line = new LineWidget();
      line.setEastWidget(down);
      line.setCenterWidget(icon);

      superob.setNormalContent(line);
    }

    /**
     * Creates the menu DOM that's displayed when the pulldown is
     * activated.
     */
    this.createMenuDOM = function(parent_dom) {
      var menu_div = menu_widget.toDOM(parent_dom);
      menu_div.mwpgui_type = "menu";
      menu_div.setAttribute("tabindex", "-1");
      menu_div.style.position = "absolute";
      menu_div.style.top = height + "px";
      menu_div.style.left = "0px";
      menu_div.style.display = "none";
      return menu_div;
    }

    this.toDOM = function(parent_dom) {
      var button_div = superob.toDOM(parent_dom);

      var div = mdom.create({
        tag: "div",
        style: { display: "inline-block", position: "absolute" }
      });

      button_div.style.position = "relative";

      // The menu div,
      var menu_div = this_pulldown.createMenuDOM(div);

      // Hide the menu on blur
      menu_div.onfocus = function() {
        superob.select(button_div);
        actionFocusWidget(menu_widget, menu_div);
      }
      menu_div.onblur = function() {
        superob.deselect(button_div);
        menu_widget.eventSelectItem(null);
        menu_div.style.display = "none";
        actionBlurWidget(menu_widget);
      }

      button_div.mwp_child_menu_item = [menu_widget, menu_div];

      div.appendChild(button_div);
      div.appendChild(menu_div);

      return div;
    }

    /**
     * Overwrite the mouse down event.
     */
    superob.pulldown_eomd = superob.eventOnMouseDown;
    superob.eventOnMouseDown = function(div) {
      superob.pulldown_eomd(div);

      var m = div.mwp_child_menu_item[1];
      if (m) {
        m.style.display = "block";
        setTimeout(function() {m.focus();}, 20);
      }

    }

  }

  /**
   * A menu item widget is a panel of fixed size with some content.
   * @constructor
   */
  function MenuItem(width, height) {
    var this_item = this;

    var superob = new Panel(width, height);
    inherit(this, superob);

    var iconinf;
    var label_txt;
    var select_fun;

    /**
     * The selection function for this menu item.
     */
    this.getSelectFunction = function() {return select_fun;}
    this.getIconInf = function() {return iconinf;}
    this.getLabelText = function() {return label_txt;}

    /**
     * Sets the function when this item is selected.
     */
    this.setSelectFunction = function(fun) {
      select_fun = fun;
    }

    /**
     * Sets up the item with an icon, label and select_callback.
     */
    this.setTo = function(icon_inf, label_text, select_function) {
      iconinf = icon_inf;
      label_txt = label_text;
      select_fun = select_function;

      var ltext = label_txt[0];
      var itemkey = label_txt[1];

      if (icon_inf) {
        var ic = new Icon(1);
        ic.setFromInf(icon_inf);
        superob.addLTWidget(ic, 3, 1);
      }
      superob.addLTWidget(new Label(ltext, 96, 18), 28, 2);

      if (select_function && select_function.clazz === "MenuWidget") {
        var right_i = new Icon(.75);
        // PENDING: This should be styled,
        right_i.setIcon(mwpicons.right);
        right_i.setClassName("icon_element");
        superob.addLTWidget(right_i, 136, 3);
      }
//      else if (itemkey) {
//        superob.addLTWidget(new Label(itemkey, 48, 18), 130, 2);
//      }
    }

    this.eventOnMouseOver = function(div) {
      div.mwp_menu_widget.eventSelectItem(this, div, "mouseover");
    }
//    this.eventOnMouseOut = function(div) {
//      var style_class = this.getClassName();
//      if (style_class) {
//        div.className = style_class;
//      }
//    }

    this.directMouseDown = function(div, e) {
      // Stop mouse down events from propagating,
      if (!e) e = window.event;
      e.preventDefault();
      e.cancelBubble = true;
      if (e.stopPropagation) e.stopPropagation();
    }
    this.directMouseUp = function(div, e) {
      if (!e) e = window.event;
      e.preventDefault();
      e.cancelBubble = true;
      if (e.stopPropagation) e.stopPropagation();

      // On selection, focus the default focusable which will blur the menu.
      var el = document.getElementById("defaultfocus");
      if (el) el.focus();

      if (select_fun) select_fun(this_item, div);
    }

    this.toDOM = function(parent_dom) {
      var div = superob.toDOM(parent_dom);

      if (this_item.eventOnMouseOver)
        div.onmouseover = function(e) {doMouseOver(this_item, div, e);};
      if (this_item.eventOnMouseOut)
        div.onmouseout = function(e) {doMouseOut(this_item, div, e);};
      div.onmousedown = function(e) {this_item.directMouseDown(div, e);}
      div.onmouseup = function(e) {this_item.directMouseUp(div, e);}

      return div;
    }

  }

  /**
   * A widget that's a list of menu items arranged vertically where the width
   * and height of the widget can be calculated when layed out.
   * @constructor
   */
  function MenuWidget() {

    this.clazz = "MenuWidget";

    var style_class;
    var children = [];

    var padding_top = 1;
    var padding_left = 2;

    var calc_height;
    var calc_width;

    var item_selected;

    /**
     * Sets the clickable class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }

    /**
     * Sets the padding for this widget.
     */
    this.setTLPadding = function(top, left) {
      padding_top = top;
      padding_left = left;
    }

    /**
     * Adds a child element to the widget.
     */
    this.addWidget = function(widget) {
      children.push(widget);
    }

    /**
     * Adds a menu item.
     */
    this.addMenuItem = function(menu_item) {
      this.addWidget(menu_item);
    }

    /**
     * Adds an item where 'label_text' is an array of the text and the code.
     */
    this.addItem = function(icon_inf, label_text, select_function) {
      var item = new MenuItem(150, 20);
      item.setClassName("pdmenuitem");
      item.setTo(icon_inf, label_text, select_function);
      this.addMenuItem(item);
    }

    this.addSeparator = function(w, h) {
      var item;
      if (!w && !h) item = new Panel(150, 8);
      else item = new Panel(w, h);
      item.setClassName("pdmenuseparator");
      this.addWidget(item);
    }

    // NOTE: This only works after 'toDOM' called.
    this.getHeight = function() {
      return calc_height;
    }
    this.getWidth = function() {
      return calc_width;
    }

    /**
     * Called when an item is selected. 'div' is the child DOM selected.
     */
    this.eventSelectItem = function(item_widget, div, evt_type) {
      if (item_selected) {
        var sel_widget = item_selected[0];
        var sel_div = item_selected[1];
        var sel_style_class = sel_widget.getClassName();
        if (sel_style_class) sel_div.className = sel_style_class;
        item_selected = null;
        if (sel_div.mwp_child_menu_item) {
          sel_div.mwp_child_menu_item[1].style.display = "none";
          sel_div.mwp_child_menu_item[0].eventSelectItem(null);
        }
      }
      if (!item_widget) return;

      var style_class = item_widget.getClassName();
      if (style_class)
        div.className = style_class + " " + style_class + "_hover";
      item_selected = [item_widget, div];

      // If there's a nested item open it,
      if (div.mwp_child_menu_item && evt_type === "mouseover")
        div.mwp_child_menu_item[1].style.display = "block";
    }



    /**
     * Return a DOM element for this widget.
     */
    this.toDOM = function(parent_dom) {

      var div = mdom.create({
        tag: "div",
        cl: style_class,
        style: { position: "absolute", display:"block",
                 paddingTop: padding_top, paddingRight: padding_left,
                 paddingBottom: padding_top, paddingLeft: padding_left
               }
      });

      calc_width = 0;
      calc_height = 0;

      // Lay out the children horizontally,
      var pos = padding_top;
      for (var i = 0; i < children.length; ++i) {
        var widget = children[i];

        var adiv = widget.toDOM(div);
        adiv.style.position = "absolute";
        adiv.style.left = padding_left + "px";
        adiv.style.top = pos + "px";
        var child_width = widget.getWidth();
        if (child_width > calc_width) {
          calc_width = child_width;
        }
        adiv.mwp_menu_widget = this;
        div.appendChild(adiv);

        // The function to run when this menu item is selected,
        var select_fun = null;
        if (widget.getSelectFunction) {
          select_fun = widget.getSelectFunction();
        }
        // If it's a menu item,
        if (select_fun && select_fun.clazz &&
            select_fun.clazz === "MenuWidget") {
          // Create the nested menu item dom and append it here,
          var nested = select_fun.toDOM(div);
          nested.style.left = (padding_left + calc_width) + "px";
          nested.style.top = pos + "px";
          nested.style.display = "none";
          div.appendChild(nested);
          adiv.mwp_child_menu_item = [select_fun, nested];
        }

        pos += widget.getHeight();
      }

      calc_height = pos;

      // Set the dimension of the div,
      mdom.setStyle(div, { width: calc_width, height: calc_height });
      return div;
    }

  }

  /**
   * A drop down list of items. We inherit most of the functionality from
   * the pull down object and add a few functions for formatting the list
   * of items.
   */
  function cDropdownList(width, height) {
    var pulldown = new Pulldown(width, height);
    pulldown.setClassName("dropdown");

    // The down icon,
    var down = new Icon(.725);
    down.setIcon(mwpicons.down);
    down.setClassName("icon_element");
    down.setMaskBorderColor(null);

    var selected_label = new Label("", width - 12, 20);
    selected_label.setClassName("ddlistitem");
    selected_label.setLeftAlign();

    var line = new Panel();
    line.addRTWidget(down, 4, 5);
    line.addLTWidget(selected_label, 8, 3);

    pulldown.setNormalContent(line);

    // The list that contains the items in the menu,
    var list_widget = new MenuWidget();
    list_widget.setClassName("ddlist");
    pulldown.setMenu(list_widget);

    // The select function
    var item_select_fun = function(item) {
      pulldown.select(item.item_string);
    }

    // Sets the content of the drop down list,
    pulldown.setList = function(list_array) {
      for (var i = 0; i < list_array.length; ++i) {
        var item = list_array[i];
        var iw = new MenuItem(width, 20);
        iw.item_string = item;
        iw.setSelectFunction(item_select_fun);
        iw.setClassName("ddlistitem");
        iw.addLTWidget(new Label(item, width, 20), 6, 2);
        list_widget.addMenuItem(iw);
      }
    }
    // Selects the item from the list,
    pulldown.select = function(item) {
      selected_label.setText(item);
      // Notify the 'onselect' function in pulldown if there is one
      if (pulldown.onselect) pulldown.onselect(item);
    }

    pulldown.setFocusable(true);

    return pulldown;
  }

  /**
   * The dialog widget class.
   * @constructor
   */
  function DialogWidget(width, height) {

    var this_dialog = this;

    var style_class;
    var component_div;
    var modal;

    var backed_div;

    /**
     * Sets the clickable class style.
     */
    this.setClassName = function(class_name) {
      style_class = class_name;
    }

    /**
     * Sets the DOM element displayed within the dialog area.
     */
    this.setInnerDOM = function(div) {
      component_div = div;
    }

    this.setModal = function(bool) {
      modal = bool;
    }

    /**
     * Resizes the dialog after it has been placed.
     */
    this.resize = function(nw, nh) {
      width = nw;
      height = nh;
      backed_div.style.width = width + "px";
      backed_div.style.height = height + "px";
    }

    /**
     * Sets the width/height to a percentage of the overlay dimension, not
     * smaller than the min value.
     */
    this.setWidthPercent = function(wp, min_w) {
      // The overlay pane for the app,
      var odiv = document.getElementById("paneoverlay");
      var owid = odiv.clientWidth;
      width = (owid * wp) / 100;
      if (width < min_w) width = min_w;
      backed_div.style.width = width + "px";
    }
    this.setHeightPercent = function(hp, min_h) {
      // The overlay pane for the app,
      var odiv = document.getElementById("paneoverlay");
      var ohei = odiv.clientHeight;
      height = (ohei * hp) / 100;
      if (height < min_h) height = min_h;
      backed_div.style.height = height + "px";
    }

    /**
     * Centers the dialog on the 'paneoverlay'
     */
    this.center = function() {
      // The overlay pane for the app,
      var odiv = document.getElementById("paneoverlay");
      var ohei = odiv.clientHeight;
      var owid = odiv.clientWidth;
      var t = (ohei - height) / 2;
      var l = (owid - width) / 2;
      if (t < 0) t = 0;
      if (l < 0) l = 0;
      backed_div.style.top = t + "px";
      backed_div.style.left = l + "px";
    }

    /**
     * Note that this will simply create the dialog element, not show it
     * on the screen.
     */
    this.toDOM = function(parent_DOM) {

      if (backed_div) throw "toDOM called twice";

      var mouter = mdom.create(
        { tag: "div.modalouter",
          style: { position:"absolute",
                   top:"0px", left:"0px", width:width, height:height
          }
        });

      var minner = mdom.create(
        { tag: "div.modalinner",
          style: { position:"absolute",
                   display:"block",
                   top:"7px", left:"7px", right:"7px", bottom:"7px"
          }
        });

      minner.appendChild(component_div);
      mouter.appendChild(minner);

      backed_div = mouter;
      backed_div.mwp_widget = this_dialog;
      return mouter;
    }


    /**
     * Shows the dialog.
     */
    this.show = function() {

      // Create the dialog,
      var dialog_dom = this.toDOM();

      // The overlay pane for the app,
      var odiv = document.getElementById("paneoverlay");

      // Make the overlay pane visible,
      odiv.style.display = "block";

      // Center the dialog,
      this.center();

//      var ohei = odiv.clientHeight;
//      var owid = odiv.clientWidth;
//      var t = (ohei - height) / 2;
//      var l = (owid - width) / 2;
//      if (t < 0) t = 0;
//      if (l < 0) l = 0;
//
//      // Position it and append it to the overlay,
//      dialog_dom.style.top = t + "px";
//      dialog_dom.style.left = l + "px";

      odiv.appendChild(dialog_dom);

      // Find the first element that can receive focus on the dialog, and
      // focus it.
      focusOn(dialog_dom);

    }

    /**
     * Closes the dialog.
     */
    this.close = function() {
      // Remove the dialog from the overlay pane,
      var odiv = document.getElementById("paneoverlay");
      var children = odiv.childNodes;
      for (var i = children.length - 1; i >= 0; --i) {
        var dialog_dom = children[i];
        if (dialog_dom.mwp_widget && dialog_dom.mwp_widget === this_dialog) {
          odiv.removeChild(dialog_dom);
        }
      }
      // Hide the overlay,
      odiv.style.display = "none";
    }

  }


  function cButton(width, height) {
    var button = new Clickable(width, height);
    button.setClassName("button");
    return button;
  }
  function tIconButton(scale) {
   if (!scale) scale = 1;
    var dim = (16 * scale);
    var button = cButton(dim, dim);
    button.setClassName("");
    return button;
  }
  function cIconButton(icon_inf, scale) {
    var button = tIconButton(scale);
    button.setIcon(icon_inf, scale);
    return button;
  }
  function cLabelButton(text, width, height) {
    var button = cButton(width, height);
    var label = new Label(text, width, height);
    label.setClassName("buttonlabel");
    button.setNormalContent(label);
    return button;
  }



  /**
   * Generates a dialog widget with some utility functions for laying
   * out components in it.
   */
  function createHelperDialog(width, height) {

    var dialog = new DialogWidget(width, height);
    var superd_toDOM = dialog.toDOM;
    dialog.setClassName("dialog");
    dialog.setModal(true);

    var c_panel = new Panel("100%", 28);
    c_panel.setClassName("dialogcontrolpanel");

    dialog.getBottomPanel = function() {
      return c_panel;
    }
    dialog.topPanelDOM = function(parent_DOM) {
      return mdom.create("div");
    }

    // Adds a close button to the dialog.
    // Change the 'close_action' function to change the default close
    // action (which is to close the dialog).
    dialog.addCloseButton = function(button_label) {
      if (!button_label) button_label = "Close";
      var close_button = cLabelButton(button_label, 60, 22);
      close_button.setFocusable(true);
      dialog.getBottomPanel().addRTWidget(close_button, 4, 1);
      dialog.close_widget = close_button;
      // Standard actions,
      dialog.close_action = function() { dialog.close(); }
      close_button.setAction(function() { dialog.close_action(); });
    }

    dialog.toDOM = function(parent_DOM) {

      var md = mdom.create("div");
      var bp = dialog.getBottomPanel().toDOM();
      bp.style.bottom = "0px";
      bp.style.left = "0px";

      var top_div = dialog.topPanelDOM(md);

      // Lay out the DOM
      md.appendChild(top_div);
      md.appendChild(bp);

      dialog.setInnerDOM(md);

      var div = superd_toDOM(parent_DOM);
      return div;
    }

    return dialog;
  }


  /**
   * Generates a Dialog widget themed to display the given information.
   */
  function standardDialog(dialog_inf) {

    var type = dialog_inf.type;

    var dialog;
    var wid = dialog_inf.width ? dialog_inf.width : 300;
    var hei = dialog_inf.height ? dialog_inf.height : 98;

    dialog = createHelperDialog(wid, hei);

    // Lay out the standard bottom bar elements,
    if (dialog_inf.confirm_label) {
      var confirm_b = cLabelButton(dialog_inf.confirm_label, 60, 22);
      confirm_b.setFocusable(true);
      dialog.getBottomPanel().addRTWidget(confirm_b, 4 + 64, 1);
      dialog.confirm_button = confirm_b;
      // Standard actions,
      dialog.confirm_action = function() {
        // Do nothing by default,
      }
      confirm_b.setAction(function() { dialog.confirm_action(); });
      dialog.addCloseButton("Cancel");
    }
    else {
      dialog.addCloseButton();
    }

    if (type === "html") {

      dialog.topPanelDOM = function(parent_DOM) {

        // The top panel element
        var tp = mdom.create({
          tag: "div",
          style: {
            position: "absolute",
            top: "0px", left: "0px", right: "0px", bottom: "30px",
            border: "12px solid white", borderTop: "20px solid white",
            backgroundColor: "white"
          }
        });

        // An innerHTML description of the layout
        tp.innerHTML = dialog_inf.html_content;

        return tp;
      }

    }

    else if (type === "statement" || type === "question" ||
             type === "warning" || type === "error") {

      // Is there an extended message for this error?
      if (dialog_inf.extended_msg) {
        var extended_button = cLabelButton("View Error", 75, 22);
        extended_button.setFocusable(true);
        dialog.getBottomPanel().addLTWidget(extended_button, 4, 1);
        dialog.extended_widget = extended_button;
        // Standard actions,
        dialog.extended_action = function() {
          dialog.resize(300, 350);
          dialog.setWidthPercent(80, 300);
          dialog.center();
        }
        extended_button.setAction(function() { dialog.extended_action(); });
      }

      dialog.topPanelDOM = function(parent_DOM) {
        // Lay out the top panel with the icon and message specified in the
        // 'dialog_inf' object
        var icon_widget = new Icon(1.750);
        if (type === "warning") icon_widget.setFromInf(WARNING_ICON_INF);
        else if (type === "error") icon_widget.setFromInf(ERROR_ICON_INF);
        else if (type === "question") icon_widget.setFromInf(WARNING_ICON_INF);
        else if (type === "statement") icon_widget = null;
        else throw "Unknown dialog type: " + type;

        // The top panel element
        var tp = mdom.create({
          tag: "div",
          style: {
            position: "absolute",
            top: "0px", left: "0px", right: "0px", bottom: "30px"
          }
        });
        
        // An innerHTML description of the layout
        tp.innerHTML = "<div style='text-align:center;position:absolute;width:44px;top:19px;left:0px;bottom:0px' id='dialog_icon'></div><div style='position:absolute;top:0px;left:44px;right:0px;height:66px;overflow:auto'><table style='width:100%;height:100%;border-spacing:0px'><tr><td id='dialog_msg'></td></tr></table></div>" +
//          "<div style='position:absolute;top:66px;left:2px;right:2px;bottom:2px' id='dialog_extended'></div>";
          "<div style='position:absolute;top:66px;left:2px;right:2px;bottom:2px;border:1px solid #808080;overflow:auto' id='dialog_extended'></div>";

        // Substitute the dialog ID's with the icon and message element
        // respectively.
        var icon_p = mdom.findId(tp, "dialog_icon");
        var msg_p = mdom.findId(tp, "dialog_msg");

        var msg_div = mdom.create("div.dialogmessage");
        msg_div.appendChild(document.createTextNode(dialog_inf.msg));
        if (dialog_inf.extended_msg) {
          var emsg_p = mdom.findId(tp, "dialog_extended");
          var ext_msg_div = mdom.create("pre.dialogextendedmessage");
          ext_msg_div.appendChild(document.createTextNode(dialog_inf.extended_msg));
          emsg_p.appendChild(ext_msg_div);
        }

        if (icon_widget) {
          icon_p.appendChild(icon_widget.toDOM());
        }
        msg_p.appendChild(msg_div);

        return tp;
      }

    }

    return dialog;

  }



  /**
   * @constructor
   */
  function GUIWidgets() {




    /**
     * Creates a Panel upon which components may be added at any position
     * relative to the top/left or bottom/right of the parent component.
     *
     * object createPanel()
     */
    this.createPanel = function(width, height) {
      return new Panel(width, height);
    }

    /**
     * Creates a label.
     */
    this.createLabel = function(text, width, height) {
      return new Label(text, width, height);
    }

    /**
     * Creates a simple none interactive icon widget.
     */
    this.createIcon = function(scale) {
      return new Icon(scale);
    }

    /**
     * Creates a general button widget of the given dimensions.
     */
    this.createButton = function(width, height) {
      return cButton(width, height);
    }

    /**
     * Creates a button widget that is only the given icon at the given
     * icon scale.
     */
    this.createIconButton = function(icon_inf, scale) {
      return cIconButton(icon_inf, scale);
    }

    /**
     * Creates a toggle icon button ( a button that starts as a right pointing
     * icon and when clicked changes to a down icon and performs a toggle
     * action.
     */
    this.createToggleIconButton = function(scale) {
      return tIconButton(scale);
    }

    /**
     * Creates a label button widget.
     */
    this.createLabelButton = function(text, width, height) {
      return cLabelButton(text, width, height);
    }

    /**
     * Creates a pulldown widget.
     */
    this.createPulldown = function(width, height) {
      var pulldown = new Pulldown(width, height);
      pulldown.setClassName("pulldown");
      pulldown.setFocusable(true);
      return pulldown;
    }

    /**
     * Creates a drop down list widget.
     */
    this.createDropdownList = function(width, height) {
      return cDropdownList(width, height);
    }

    /**
     * Creates a menu option widget.
     */
    this.createMenu = function() {
      var menu = new MenuWidget();
      menu.setClassName("pdmenu");
      return menu;
    }

    /**
     * Creates a menu item widget.
     */
    this.createMenuItem = function(width, height) {
      var menu_item = new MenuItem(width, height);
      menu_item.setClassName("pdmenuitem");
      return menu_item;
    }

    /**
     * Creates a fixed size dialog widget.
     */
    this.createDialog = function(width, height) {
      var dialog = new DialogWidget(width, height);
      dialog.setClassName("dialog");
      return dialog;
    }

    /**
     * Creates a standard modal dialog widget themed to the given type, used
     * to display warnings, errors, help or other general information.
     */
    this.createStandardDialog = function(dialog_inf) {
      return standardDialog(dialog_inf);
    }


  }

  // The exported API,
  return new GUIWidgets();

})()
