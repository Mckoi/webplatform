/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.mckoi.mwpui.nodejs;

import com.mckoi.network.CommitFaultException;
import com.mckoi.webplatform.FileRepository;
import com.mckoi.webplatform.PlatformContext;
import com.mckoi.webplatform.PlatformContextFactory;

/**
 * Creates, closes and commits file repositories from the repository identifier
 * string.
 *
 * @author Tobias Downer
 */
public class NodeNativeMFileSystems {

  NodeNativeMFileSystems(FileDescriptorsMap fd_map) {
  }

  public void setupFSFunctionsOn(GJSObject ob) throws NoSuchMethodException {
    GJSRuntime.setWrappedFunction(
            this, "mfilesystems", "open", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfilesystems", "close", ob);
    GJSRuntime.setWrappedFunction(
            this, "mfilesystems", "commit", ob);
  }

  /**
   * Casts the argument to a FileRepository object.
   * 
   * @param arg
   * @return 
   */
  static FileRepository toFileRepository(Object arg) {
    GJSObject ob = (GJSObject) arg;
    Object mckoi_fr_ob = ob.getMember("_mckoiFR");
    if (!(mckoi_fr_ob instanceof FileRepository)) {
      return null;
    }
    return (FileRepository) mckoi_fr_ob;
  }

  /**
   * Returns true when there's a callback argument present.
   * 
   * @param args
   * @param index
   * @return 
   */
  private static boolean hasReqArg(Object[] args, int index) {
    return index < args.length && !GJSStatics.UNDEFINED.equals(args[index]);
  }

  /**
   * Posts a deferred function to be immediately called when the process is
   * not currently executing javascript. The 'complete_args' array is the
   * arguments to give the 'oncomplete' function.
   * 
   * @param req_arg
   * @param complete_args
   */
  private static Object postToImmediateQueue(String description,
                          Object thisObj, Object req_arg,
                          Object... complete_args) {

    GJSObject req = (GJSObject) req_arg;
    GJSObject oncomplete = (GJSObject) req.getMember("oncomplete");

    GJSProcessSharedState pstate = GJSRuntime.system().getSharedState();
    pstate.postToIOQueue(thisObj, oncomplete, complete_args);

    return GJSStatics.UNDEFINED;

  }



  public Object open(Object thiz, Object... args) {

    String repository_id = args[0].toString();
    boolean deferred = hasReqArg(args, 1);
    
    try {
      GJSSystem system = GJSRuntime.system();
      PlatformContext ctx = PlatformContextFactory.getPlatformContext();

      FileRepository file_repository = ctx.getFileRepository(repository_id);
      if (file_repository == null) {
        throw new GJavaScriptException(
                          "Repository id not accessible: " + repository_id);
      }

      // Returns an object with a hidden read-only field containing the
      // FileRepository.
      GJSObject file_repo_ob = system.newJavaScriptObject();
      file_repo_ob.setROHiddenMember("_mckoiFR", file_repository);

      if (deferred) {
        return postToImmediateQueue("open", thiz, args[1], null, file_repo_ob);
      }

      return file_repo_ob;

    }
    catch (GJavaScriptException ex) {
      if (deferred) {
        return postToImmediateQueue("open", thiz, args[1], ex);
      }
      throw ex;
    }

  }

  
  public Object close(Object thiz, Object... args) {

    GJSObject file_repo_ob = (GJSObject) args[0];
    boolean deferred = hasReqArg(args, 1);
    
    try {
      FileRepository file_repository = toFileRepository(file_repo_ob);
      // Read-only file system doesn't need to do anything,
      if (file_repository == null) {
        return file_repo_ob;
      }

      // PENDING: Invalidate the repository...



      if (deferred) {
        return postToImmediateQueue("close", thiz, args[1], (Object) null);
      }

      return file_repo_ob;

    }
    catch (GJavaScriptException ex) {
      if (deferred) {
        return postToImmediateQueue("close", thiz, args[1], ex);
      }
      throw ex;
    }

  }


  public Object commit(Object thiz, Object... args) {

    GJSObject file_repo_ob = (GJSObject) args[0];
    boolean deferred = hasReqArg(args, 1);

    try {
      FileRepository file_repository = toFileRepository(file_repo_ob);
      if (file_repository == null) {
        throw new GJavaScriptException("Can not commit read-only file system");
      }

      try {
        file_repository.commit();
      }
      catch (CommitFaultException ex) {
        // Wrap commit fault,
        throw new GJavaScriptException(ex);
      }

      if (deferred) {
        return postToImmediateQueue(
                      "commit", thiz, args[1], (Object) null, file_repo_ob);
      }

      return file_repo_ob;

    }
    catch (GJavaScriptException ex) {
      if (deferred) {
        return postToImmediateQueue("commit", thiz, args[1], ex);
      }
      throw ex;
    }

  }




}
