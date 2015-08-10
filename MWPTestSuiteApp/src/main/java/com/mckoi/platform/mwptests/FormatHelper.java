/*
 * Copyright 2015 Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mckoi.platform.mwptests;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import org.json.JSONException;
import org.json.JSONStringer;

/**
 *
 * @author Tobias Downer
 */
public class FormatHelper {

  /**
   * Turns a Java exception into a JSON object string that has a single key
   * called 'ERROR' and value is the stack trace.
   * 
   * @param ex
   * @return
   * @throws IOException 
   */
  public static String jsonErrorOutput(Throwable ex) throws IOException {
    try {
      StringBuilder ex_out = new StringBuilder();
      Writer so = new StringWriter();
      try (PrintWriter pout = new PrintWriter(so)) {
        ex.printStackTrace(pout);
      }
      String stack_trace = ex_out.toString();
      JSONStringer json_out = new JSONStringer();
      json_out.object();
      json_out.key("ERROR");
      json_out.value(stack_trace);
      json_out.endObject();
      return json_out.toString();
    }
    catch (JSONException json_ex) {
      throw new RuntimeException(json_ex);
    }
  }
  
}
