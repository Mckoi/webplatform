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

// Command line tools that need client side logic

// Load point;
//   'pane' object is FramePanel
//     (use pane.getDOM() to set the DOM)
//   'comm' object has;
//     comm.send(command_string) - sends a command to the server's frame with
//             the given args.
//   'frame_name' is String
//   'process_id_str' is String
//   'args_str' is String

var MWPUpload = function(pane, comm, frame_name, process_id_str, args_str) {

  var dest_path = args_str;

  var form = mdom.create("form");
  form.className = "mwp_upload_form";
  form.setAttribute("enctype", "multipart/form-data");
  form.setAttribute("method", "post");
  form.setAttribute("action", "fileupload");

  var desc = mdom.create("div");
  desc.className = "base mwp_upload_desc";
  desc.innerHTML = "Upload file(s) to path: " +
                          MWPUTILS.toHTMLEntity(dest_path) + "<br />";

  var input = mdom.create("input");
  input.setAttribute("type", "file");
  input.setAttribute("name", "filesToUpload");
  input.setAttribute("id", "filesToUpload");
  // HTML5 Attribute for multiple file selection,
  input.setAttribute("multiple", "multiple");
  // We need both to support both webkit and moz,
  input.setAttribute("size", "60");
  input.style.width = "600px";

  var status = mdom.create("div");
  status.className = "base mwp_upload_status";
  status.innerHTML = "<br />";

  // Event handlers,
  input.onchange = function() {
    var fd;
    if (form.getFormData) fd = form.getFormData();
    else fd = new FormData(form);

    var uploadProgress = function(evt) {
      var loaded_bytes = evt.loaded;
      var loaded_total = evt.total;
      status.innerHTML = "Progress: " +
                            loaded_bytes + " / " + loaded_total + "<br />";
    }
    var uploadComplete = function(evt) {
      status.innerHTML = "Upload Completed";
    }
    var uploadFailed = function(evt) {
      status.innerHTML = "Upload Failed";
    }
    var uploadCanceled = function(evt) {
      status.innerHTML = "Upload Canceled";
    }

    var xhr = new XMLHttpRequest();
    // event listeners
    xhr.upload.addEventListener("progress", uploadProgress, false);
    xhr.upload.addEventListener("load", uploadComplete, false);
    xhr.upload.addEventListener("error", uploadFailed, false);
    xhr.upload.addEventListener("abort", uploadCanceled, false);

    // The request string,
    var request_string =
          "C?e=ul&f=" + encodeURIComponent(frame_name) +
          "&loc=" + encodeURIComponent(dest_path);

    xhr.open("POST", request_string, true);
    // Send it,
    xhr.setRequestHeader("MckoiProcessID", process_id_str);
    xhr.send(fd);

  }

  form.appendChild(desc);
  form.appendChild(input);
  form.appendChild(status);

  pane.printdom(form);
  pane.newline();

//  alert("args_str = " + args_str);
  
}


var MWPDownload = function(pane, comm, frame_name, process_id_str, args_str) {

  var dest_path = args_str;

  var form = mdom.create("form");
  form.className = "mwp_download_form";
  form.setAttribute("target", "_blank");
  form.setAttribute("method", "post");
  form.setAttribute("action", "C?e=dl&f=" + encodeURIComponent(frame_name));

  form.innerHTML =
      "<div class='base mwp_download_desc'>" +
      "Download path as ZIP file: " +
                              MWPUTILS.toHTMLEntity(dest_path) + "<br />" +
      "</div>" +
      "<input type='hidden' name='pid' value='" +
                              MWPUTILS.toHTMLEntity(process_id_str) + "' />" +
      "<input type='hidden' name='loc' value='" +
                              MWPUTILS.toHTMLEntity(dest_path) + "' />"
  ;

  var input = mdom.create("input");
  input.setAttribute("type", "submit");
  input.setAttribute("value", "Download Zip");

  form.appendChild(input);

  pane.printdom(form);
  pane.newline();

}

