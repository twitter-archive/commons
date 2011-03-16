// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.net.http.handlers;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A servlet that provides a way to remotely terminate the running process immediately.
 *
 * @author William Farner
 */
public class AbortHandler extends HttpServlet {

  /**
   * A {@literal @Named} binding key for the QuitHandler listener.
   */
  public static final String ABORT_HANDLER_KEY =
      "com.twitter.common.net.http.handlers.AbortHandler.listener";

  private static final Logger LOG = Logger.getLogger(AbortHandler.class.getName());

  private final Runnable abortListener;

  /**
   * Constructs a new AbortHandler that will notify the given {@code abortListener} when the servlet
   * is accessed.  It is the responsibility of the listener to initiate an immediate shutdown of
   * the system.
   *
   * @param abortListener Runnable to notify when the servlet is accessed.
   */
  @Inject
  public AbortHandler(@Named(ABORT_HANDLER_KEY) Runnable abortListener) {
    this.abortListener = Preconditions.checkNotNull(abortListener);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    LOG.info(String.format("Received abort HTTP signal from %s (%s)",
        req.getRemoteAddr(), req.getRemoteHost()));

    resp.setContentType("text/plain");
    PrintWriter writer = resp.getWriter();
    try {
      writer.println("Aborting process NOW!");
      writer.close();
      abortListener.run();
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Abort failed.", e);
    }
  }
}
