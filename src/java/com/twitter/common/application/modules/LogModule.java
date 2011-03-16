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

package com.twitter.common.application.modules;

import java.io.File;
import java.util.logging.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import com.twitter.common.logging.LogUtil;
import com.twitter.common.net.http.handlers.LogPrinter;
import com.twitter.common.stats.StatImpl;
import com.twitter.common.stats.Stats;

/**
 * Binding module for logging-related bindings, such as the log directory.
 *
 * Bindings provided by this module:
 * <ul>
 *   <li>{@code @Named(LogPrinter.LOG_DIR_KEY) File} - Log directory.
 * </ul>
 *
 * Default bindings that may be overridden:
 * <ul>
 *   <li>Log directory: directory where application logs are written.  May be overridden by binding
 *       to: {@code bind(File.class).annotatedWith(Names.named(LogPrinter.LOG_DIR_KEY))}.
 * </ul>
 *
 * @author William Farner
 */
public class LogModule extends AbstractModule {

  private static final Logger LOG = Logger.getLogger(LogModule.class.getName());

  @Override
  protected void configure() {
    // Bind the default log directory.
    final File logDir = LogUtil.getLogManagerLogDir();
    LOG.info("From logging properties, parsed log directory " + logDir.getAbsolutePath());
    bind(File.class).annotatedWith(Names.named(LogPrinter.LOG_DIR_KEY)).toInstance(logDir);

    requestStaticInjection(Init.class);
  }

  public static class Init {
    @Inject
    public static void exportLogDir(@Named(LogPrinter.LOG_DIR_KEY) final File logDir) {
      Stats.exportStatic(new StatImpl<String>("logging_dir") {
        @Override public String read() {
          return logDir.getAbsolutePath();
        }
      });
    }
  }
}
