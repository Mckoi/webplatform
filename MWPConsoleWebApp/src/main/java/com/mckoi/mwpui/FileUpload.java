/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mckoi.mwpui;

import com.mckoi.data.DataFileUtils;
import com.mckoi.network.CommitFaultException;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

/**
 * A File uploader request handler.
 *
 * @author Tobias Downer
 */
public class FileUpload {
   
  
  public static void processUpload(
              HttpServletRequest request, PlatformContext ctx,
              FileName normal_fn, PrintWriter out)
                                         throws ServletException, IOException {

    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload();

    try {

      // Make sure the input file is cast as a directory,
      normal_fn = normal_fn.asDirectory();

      // The location string,
      String location = normal_fn.getPathFile();

      // The context file repository,
      FileRepository fs = ctx.getFileRepositoryFor(normal_fn);

      boolean fs_updated = false;

      byte[] buf = new byte[16384];

      // Parse the request
      FileItemIterator iter = upload.getItemIterator(request);
      while (iter.hasNext()) {
        FileItemStream item = iter.next();
        String name = item.getFieldName();
        InputStream stream = item.openStream();
        if (item.isFormField()) {
//          System.out.println("Form field " + name + " with value detected.");
        }
        else {
//          System.out.println("File field " + name + " with file name "
//              + item.getName() + " detected.");

          // The destination file,

          String absolute_fname = location + item.getName();
          FileInfo finfo = fs.getFileInfo(absolute_fname);

          // If the file doesn't exist, create it
          if (finfo == null) {
            fs.createFile(absolute_fname,
                          item.getContentType(), System.currentTimeMillis());
            finfo = fs.getFileInfo(absolute_fname);
          }
          // If it does exist,
          else {
            finfo.setLastModified(System.currentTimeMillis());
          }

          fs_updated = true;

          OutputStream file_out =
                    DataFileUtils.asSimpleDifferenceOutputStream(
                                                        finfo.getDataFile());

          // Process the input stream
          while (true) {
            int len = stream.read(buf, 0, buf.length);
            if (len == -1) {
              break;
            }
            file_out.write(buf, 0, len);
          }

          file_out.flush();
          file_out.close();

        }
      }

      // If the file system changed,
      if (fs_updated) {
        // Commit it,
        try {
          fs.commit();
        }
        catch (CommitFaultException e) {
          out.print("FAIL:Exception\n");
          e.printStackTrace(out);
          return;
        }
      }

      out.print("OK\n");

    }
    catch (FileUploadException e) {
      out.print("FAIL:Exception\n");
      e.printStackTrace(out);
    }
  }

}
