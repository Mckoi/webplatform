/**
 * com.mckoi.mwpbase.ZipDownload  Nov 3, 2011
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

import com.mckoi.data.DataFile;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Processes a zip download operation.
 *
 * @author Tobias Downer
 */

public class ZipDownload {

  private static void error(HttpServletResponse response, String error_msg)
                                         throws ServletException, IOException {
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();
    out.println("Unable to perform the Zip function. Reason;");
    out.println(error_msg);
    out.close();
  }


  private static void makeZip(FileRepository filesystem, final String path,
                      byte[] buf, ZipOutputStream zip_out) throws IOException {

    List<FileInfo> file_list = filesystem.getFileList(path);

    for (FileInfo file : file_list) {
      DataFile dfile = file.getDataFile();
      long sz = dfile.size();

      String fname = file.getAbsoluteName();
      // Remove prepending '/'
      if (fname.startsWith("/")) {
        fname = fname.substring(1);
      }
      long last_modified = file.getLastModified();

      // Open the zip entry,
      ZipEntry zip_entry = new ZipEntry(fname);
      zip_entry.setTime(last_modified);
      zip_entry.setSize(sz);
      zip_out.putNextEntry(zip_entry);

      // Pipe into the zip,
      while (sz > 0) {
        int to_read = (int) Math.min(buf.length, sz);
        dfile.get(buf, 0, to_read);
        zip_out.write(buf, 0, to_read);
        sz -= to_read;
      }

      // Close the zip entry,
      zip_out.closeEntry();
    }

    // Recurse over the sub-directories, if there are any,
    List<FileInfo> subdir_list = filesystem.getSubDirectoryList(path);
    for (FileInfo subdir : subdir_list) {
      String sub_path = subdir.getAbsoluteName();
      ZipDownload.makeZip(filesystem, sub_path, buf, zip_out);
    }

  }



  public static void process(
                      HttpServletRequest request, HttpServletResponse response,
                      PlatformContext ctx, FileName normal_fn)
                                         throws ServletException, IOException {

    // PENDING: handle 'fs_path'

    // Make sure we are cast as a directory,
    normal_fn = normal_fn.asDirectory();

    // The file system,
    FileRepository filesystem = ctx.getFileRepositoryFor(normal_fn);

    // Check the path exists,
    String location = normal_fn.getPathFile();
    FileInfo dir = filesystem.getFileInfo(location);
    if (dir == null || !dir.isDirectory()) {
      error(response, "Path not found");
      return;
    }

    long ts = System.currentTimeMillis();

    // Set the mime type and the file name,
    response.setContentType("application/zip");
    response.setHeader("Content-Disposition",
                           "attachment; filename=mwp_download_" + ts + ".zip");

    OutputStream response_out = response.getOutputStream();
    ZipOutputStream zip_out = new ZipOutputStream(response_out);

    byte[] buf = new byte[4096];
    makeZip(filesystem, location, buf, zip_out);

    // Flush the zip file (write the zip directory, etc).
    zip_out.flush();
    zip_out.finish();

    response_out.close();

  }


}
