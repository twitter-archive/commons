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

package com.twitter.common.application;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;

import com.twitter.common.application.modules.AppLauncherModule;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgScanner;
import com.twitter.common.args.ArgScanner.ArgScanException;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;

/**
 * An application launcher that sets up a framework for pluggable binding modules.  This class
 * should be called directly as the main class, with a command line argument {@code -app_class}
 * which is the canonical class name of the application to execute.
 *
 * If your application uses command line arguments all {@link Arg} fields annotated with
 * {@link CmdLine} will be discovered and command line arguments will be validated against this set,
 * parsed and applied.
 *
 * A bootstrap module will be automatically applied ({@link AppLauncherModule}), which provides
 * overridable default bindings for things like quit/abort hooks and a health check function.
 * A {@link LifecycleModule} is also automatically applied to perform startup and shutdown
 * actions.
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
           help = "Guice development stage to create injector with.")
  private static final Arg<Stage> GUICE_STAGE = Arg.create(Stage.DEVELOPMENT);

  @Inject @StartupStage private ActionController startupController;
  @Inject private Lifecycle lifecycle;

  private void run(Application application) {
    configureInjection(application);

    LOG.info("Executing startup actions.");
    startupController.execute();

    try {
      application.run();
    } finally {
      LOG.info("Application run() exited.");
      lifecycle.shutdown();
    }
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
    try {
      ArgScanner.parse(args);
    } catch (ArgScanException e) {
      exit("Failed to scan arguments", e);
    } catch (IllegalArgumentException e) {
      exit("Failed to apply arguments", e);
    }

    new AppLauncher().run(APP_CLASS.get().newInstance());
  }

  private static void exit(String message, Exception error) {
    LOG.log(Level.SEVERE, message, error);
    System.exit(1);
  }
}
