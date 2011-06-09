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

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;

import com.twitter.common.application.modules.AppLauncherModule;
import com.twitter.common.application.modules.LifecycleModule;
import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgScanner;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.parsers.EnumParser;
import com.twitter.common.args.parsers.ListParser;

/**
 * An application launcher that sets up a framework for pluggable binding modules.  This class
 * should be called directly as the main class, with a command line argument {@code -app_class}
 * which is the canonical class name of the application to execute.
 *
 * If your application uses command line arguments ({@link Arg}} annotated with {@link CmdLine},
 * you should specify {@code -arg_scan_packages} to indicate which java packages should be
 * recursively scanned for arguments to parse, validate, and apply.
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

  private static final String SCAN_PACKAGES_ARG = "arg_scan_packages";
  private static final ListParser SCAN_PACKAGES_PARSER = new ListParser();

  @NotNull
  @CmdLine(name = "app_class",
           help = "Fully-qualified name of the application class, which must implement Runnable.")
  private static final Arg<Class<? extends Application>> APP_CLASS = Arg.create();

  @CmdLine(name = "guice_stage",
           help = "Guice development stage to create injector with.",
           parser = EnumParser.class)
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
    Map<String, String> argumentMap = Maps.newHashMap(ArgScanner.mapArguments(args));

    ImmutableList.Builder<String> scanPackages = ImmutableList.<String>builder()
        .add(AppLauncher.class.getPackage().getName());

    String argSpecifiedPackages = argumentMap.remove(SCAN_PACKAGES_ARG);
    if (argSpecifiedPackages != null) {
      @SuppressWarnings("unchecked")
      List<String> argScanPackages = (List<String>) SCAN_PACKAGES_PARSER.parse(
              new TypeLiteral<List<String>>() {}.getType(), argSpecifiedPackages);
      scanPackages.addAll(argScanPackages);
    }

    LOG.info("Scanning arguments in packages: " + scanPackages);

    try {
      ArgScanner.parse(scanPackages.build(), argumentMap);
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Failed to apply arguments:\n" + e.getMessage());
      System.exit(1);
    }

    new AppLauncher().run(APP_CLASS.get().newInstance());
  }
}
