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

import javax.servlet.http.HttpServletRequest;
import java.util.logging.Logger;

/**
 * Equivalent to {@link VarsHandler}, but will log a warning every time the servlet is called.
 * This is intended to be used during the cut-over period to retire the /varz endpoint.
 *
 * @author William Farner
 */
public class WarningVarsHandler extends TextResponseHandler {

  private static Logger LOG = Logger.getLogger(WarningVarsHandler.class.getName());

  private final VarsHandler varsHandler;

  @Inject
  public WarningVarsHandler(VarsHandler varsHandler) {
    this.varsHandler = Preconditions.checkNotNull(varsHandler);
  }

  @Override
  public Iterable<String> getLines(HttpServletRequest request) {
    LOG.warning(
        "The /varz HTTP endpoint is deprecated and will disappear, please use /vars instead!");
    return varsHandler.getLines(request);
  }
}
