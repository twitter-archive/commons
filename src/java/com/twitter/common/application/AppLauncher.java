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

package com.twitter.common.application;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;

import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgScanner;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.parsers.NonParameterizedTypeParser;
import com.twitter.common.application.modules.AppLauncherModule;
import com.twitter.common.application.modules.LifecycleModule;

/**
 * An application launcher that sets up a framework for pluggable binding modules.  This class
 * should be called directly as the main class, with a command line argument {@code -app_class}
 * which is the canonical class name of the application to execute.
 *
 * Command line arguments will be processed and applied globally within the {@code com.twitter}
 * package, using {@link ArgScanner}.
 *
 * A bootstrap module will be automatically applied ({@link AppLauncherModule}), which provides
 * overridable default bindings for things like quit/abort hooks and a health check function.
 *
 * @author William Farner
 */
public final class AppLauncher {

  private static final Logger LOG = Logger.getLogger(AppLauncher.class.getName());

  @NotNull
  @CmdLine(name = "app_class",
           help = "Fully-qualified name of the application class, which must implement Runnable.")
  private static final Arg<Class<? extends Application>> APP_CLASS = Arg.create();

  @CmdLine(name = "guice_stage",
           help = "Guice development stage to create injector with.",
           parser = StageParser.class)
  private static final Arg<Stage> GUICE_STAGE = Arg.create(Stage.DEVELOPMENT);

  // TODO(William Farner): Add a generic enum args parser to handle this.
  public static class StageParser extends NonParameterizedTypeParser<Stage> {
    public StageParser() {
      super(Stage.class);
    }
    @Override public Stage doParse(String raw) throws IllegalArgumentException {
      return Stage.valueOf(raw);
    }
  }

  @Inject @StartupStage private ActionController startupController;

  private void run(Application application) {
    configureInjection(application);

    LOG.info("Executing startup actions.");
    startupController.execute();

    application.run();
    LOG.info("Application run() exited.");
  }

  private void configureInjection(Application application) {
    Iterable<Module> modules = ImmutableList.<Module>builder()
        .add(new LifecycleModule())
        .add(new AppLauncherModule())
        .addAll(application.getModules())
        .build();

    Injector injector = Guice.createInjector(GUICE_STAGE.get(),
        Modules.override(Modules.combine(modules)).with(application.getOverridingModules()));
    injector.injectMembers(this);
    injector.injectMembers(application);
  }

  public static void main(String[] args) throws IllegalAccessException, InstantiationException {
    LOG.info("Scanning arguments.");
    try {
      ArgScanner.parse(Arrays.asList("com.twitter"), args);
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Failed to apply arguments:\n" + e.getMessage());
      System.exit(1);
    }

    new AppLauncher().run(APP_CLASS.get().newInstance());
  }
}
