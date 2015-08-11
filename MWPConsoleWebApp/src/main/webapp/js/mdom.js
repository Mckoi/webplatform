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

// Browser DOM extentions for the Mckoi UI.
// This wraps the native DOM system and provides some additional convenience
// functions for constructing DOM trees.

(function() {

  // Make the 'isArray' function.
  var isArray;
  if(!Array.isArray) {
    isArray = function (vArg) {
      return Object.prototype.toString.call(vArg) === "[object Array]";
    };
  }
  else {
    isArray = Array.isArray;
  }

  var px_map =
    { top:true, left:true, right:true, bottom:true,
      width:true, height:true,
      paddingTop:true, paddingRight:true, paddingBottom:true, paddingLeft:true
    };

  // Returns true if the given style type wants a 'px' output,
  function isPixelStyle(style) {
    return px_map[style];
  }

  function mdom_createElement(tag) {
    var d = tag.indexOf(".");
    var dom;
    if (d === -1) {
      dom = document.createElement(tag);
    }
    else {
      dom = document.createElement(tag.substring(0, d));
      dom.className = tag.substring(d + 1);
    }
    return dom;
  }

  //
  // Traverses the children of a DOM tree and finds and returns the element
  // with the given id.
  //
  var findId = function(dom, id_name) {
    if (dom.id === id_name) {
      return dom;
    }
    else {
      var nodes = dom.childNodes;
      for (var i = nodes.length - 1; i >= 0; --i) {
        var found = findId(nodes[i], id_name);
        if (found) return found;
      }
    }
    return null;
  };

  //
  // Traverses the children of a DOM tree and finds and returns all the
  // elements with the given class name.
  //
  var findAllClassName = function(dom, cl_name) {
    var found;
    if (dom.className === cl_name) {
      found = [ dom ];
    }
    else {
      found = [];
    }
    var nodes = dom.childNodes;
    for (var i = nodes.length - 1; i >= 0; --i) {
      var subfound = findAllClassName(nodes[i], cl_name);
      var sz = subfound.length;
      if (sz < 16) {
        for (var n = 0; n < sz; ++n) {
          found.push(subfound[n]);
        }
      }
      else {
        found = found.concat(subfound);
      }
    }
    return found;
  };



  var MDom = function() {
  };
  
  MDom.isArray = isArray;
  MDom.findId = findId;
  MDom.findAllClassName = findAllClassName;
  
  MDom.create = function(obj) {
    var obtype = typeof obj;
    if (obtype === 'string') {
      return mdom_createElement(obj);
    }
    if (obtype === 'object') {
      // Get the declaration,
      var tag = obj.tag;
      // Get the style object,
      var style = obj.style;

      // Create the element from the tag,
      var dom = mdom_createElement(tag);

      // If there are styles then set them here,
      if (style) {
        // Set the styles,
        MDom.setStyle(dom, style);
      }

      // Misc attributes,
      var attr;

      attr = obj.unselectable;
      if (attr) dom.setAttribute("unselectable", attr);
      attr = obj.spellcheck;
      if (attr) dom.setAttribute("spellcheck", attr);
      attr = obj.cl;
      if (attr) dom.className = attr;
      attr = obj.id;
      if (attr) dom.id = attr;

      return dom;
    }
    
    throw "MDom: Invalid create";
  };



  MDom.setStyle = function(dom, style_obj) {
    // Set the styles,
    for (var s in style_obj) {
      var val = style_obj[s];
      var valtype = typeof val;
      if (valtype !== 'undefined') {
        if (valtype === 'number' && isPixelStyle(s)) {
          val = val + "px";
        }
        dom.style[s] = val;
      }
    }
    return dom;
  };


  // Expose the 'MDom' function globally,
  window.mdom = MDom;
  
})();
