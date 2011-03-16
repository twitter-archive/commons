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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import com.twitter.common.base.Command;
import com.twitter.common.application.ActionRegistry;
import com.twitter.common.application.StartupStage;
import com.twitter.common.util.BuildInfo;

/**
 * Binding module for the bare minimum requirements for the
 * {@link com.twitter.common.application.AppLauncher}.
 *
 * @author William Farner
 */
public class AppLauncherModule extends AbstractModule {

  private static final Logger LOG = Logger.getLogger(AppLauncherModule.class.getName());

  @Override
  protected void configure() {
    bind(BuildInfo.class).in(Singleton.class);

    // Bind the default uncaught exception handler.
    UncaughtExceptionHandler exceptionHandler = new UncaughtExceptionHandler() {
      @Override public void uncaughtException(Thread t, Throwable e) {
        LOG.log(Level.SEVERE, "Uncaught exception from " + t, e);
      }
    };
    bind(UncaughtExceptionHandler.class).toInstance(exceptionHandler);

    requestStaticInjection(Init.class);
  }

  public static class Init {
    @Inject
    private static void applyUncaughtExceptionHandler(
        @StartupStage ActionRegistry startupRegistry,
        final UncaughtExceptionHandler uncaughtExceptionHandler) {
      startupRegistry.addAction(new Command() {
        @Override public void execute() {
          Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
        }
      });
    }
  }
}
