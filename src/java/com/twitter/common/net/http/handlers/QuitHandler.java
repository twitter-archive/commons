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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * A servlet that provides a way to remotely signal the process to initiate a clean shutdown
 * sequence.
 */
public class QuitHandler extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(QuitHandler.class.getName());

  /**
   * A {@literal @Named} binding key for the QuitHandler listener.
   */
  public static final String QUIT_HANDLER_KEY =
      "com.twitter.common.net.http.handlers.QuitHandler.listener";

  private final Runnable quitListener;

  /**
   * Constructs a new QuitHandler that will notify the given {@code quitListener} when the servlet
   * is accessed.  It is the responsibility of the listener to initiate a clean shutdown of the
   * process.
   *
   * @param quitListener Runnable to notify when the servlet is accessed.
   */
  @Inject
  public QuitHandler(@Named(QUIT_HANDLER_KEY) Runnable quitListener) {
    this.quitListener = Preconditions.checkNotNull(quitListener);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    LOG.info(String.format("Received quit HTTP signal from %s (%s)",
        req.getRemoteAddr(), req.getRemoteHost()));

    resp.setContentType("text/plain");
    PrintWriter writer = resp.getWriter();
    try {
      writer.println("Notifying quit listener.");
      writer.close();
      new Thread(quitListener).start();
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Quit failed.", e);
    }
  }
}
