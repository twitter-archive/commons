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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Equivalent to {@link HealthHandler}, but will log a warning every time the servlet is called.
 * This is intended to be used during the cut-over period to retire the /healthz endpoint.
 *
 * @author William Farner
 */
public class WarningHealthHandler extends HttpServlet {
  private static Logger LOG = Logger.getLogger(HealthHandler.class.getName());

  private final HealthHandler healthHandler;

  @Inject
  public WarningHealthHandler(HealthHandler healthHandler) {
    this.healthHandler = Preconditions.checkNotNull(healthHandler);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    LOG.warning(
        "The /healthz HTTP endpoint is deprecated and will disappear, please use /health instead!");
    healthHandler.doGet(req, resp);
  }
}
