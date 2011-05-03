// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.net.http.handlers;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A handler that responds to all requests in HTML format.
 *
 * @author William Farner
 */
public abstract class TextResponseHandler extends HttpServlet {
  private final String textContentType;

  public TextResponseHandler() {
    this("text/plain");
  }

  public TextResponseHandler(String textContentType) {
    this.textContentType = textContentType;
  }

  /**
   * Returns the lines to be printed as the body of the response.
   *
   * @return An iterable collection of lines to respond to the request with.
   */
  public abstract Iterable<String> getLines(HttpServletRequest request);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    resp.setContentType(textContentType);
    resp.setStatus(HttpServletResponse.SC_OK);
    PrintWriter responseBody = resp.getWriter();
    for (String line : getLines(req)) {
      responseBody.println(line);
    }
    responseBody.close();
  }
}
