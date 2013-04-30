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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
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
import com.twitter.common.args.ArgFilters;
import com.twitter.common.args.ArgScanner;
import com.twitter.common.args.ArgScanner.ArgScanException;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.base.ExceptionalCommand;

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
 */
public final class AppLauncher {

  private static final Logger LOG = Logger.getLogger(AppLauncher.class.getName());

  private static final String APP_CLASS_NAME = "app_class";
  @NotNull
  @CmdLine(name = APP_CLASS_NAME,
           help = "Fully-qualified name of the application class, which must implement Runnable.")
  private static final Arg<Class<? extends Application>> APP_CLASS = Arg.create();

  @CmdLine(name = "guice_stage",
           help = "Guice development stage to create injector with.")
  private static final Arg<Stage> GUICE_STAGE = Arg.create(Stage.DEVELOPMENT);

  private static final Predicate<Field> SELECT_APP_CLASS =
      ArgFilters.selectCmdLineArg(AppLauncher.class, APP_CLASS_NAME);

  @Inject @StartupStage private ExceptionalCommand startupCommand;
  @Inject private Lifecycle lifecycle;

  private AppLauncher() {
    // This should not be invoked directly.
  }

  private void run(Application application) {
    try {
      configureInjection(application);

      LOG.info("Executing startup actions.");
      // We're an app framework and this is the outer shell - it makes sense to handle all errors
      // before exiting.
      // SUPPRESS CHECKSTYLE:OFF IllegalCatch
      try {
        startupCommand.execute();
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "Startup action failed, quitting.", e);
        throw Throwables.propagate(e);
      }
      // SUPPRESS CHECKSTYLE:ON IllegalCatch

      try {
        application.run();
      } finally {
        LOG.info("Application run() exited.");
      }
    } finally {
      if (lifecycle != null) {
        lifecycle.shutdown();
      }
    }
  }

  private void configureInjection(Application application) {
    Iterable<Module> modules = ImmutableList.<Module>builder()
        .add(new LifecycleModule())
        .add(new AppLauncherModule())
        .addAll(application.getModules())
        .build();

    Injector injector = Guice.createInjector(GUICE_STAGE.get(), Modules.combine(modules));
    injector.injectMembers(this);
    injector.injectMembers(application);
  }

  public static void main(String... args) throws IllegalAccessException, InstantiationException {
    // TODO(John Sirois): Support a META-INF/MANIFEST.MF App-Class attribute to allow java -jar
    parseArgs(ArgFilters.SELECT_ALL, Arrays.asList(args));
    new AppLauncher().run(APP_CLASS.get().newInstance());
  }

  /**
   * A convenience for main wrappers.  Equivalent to:
   * <pre>
   *   AppLauncher.launch(appClass, ArgFilters.SELECT_ALL, Arrays.asList(args));
   * </pre>
   *
   * @param appClass The application class to instantiate and launch.
   * @param args The command line arguments to parse.
   * @see ArgFilters
   */
  public static void launch(Class<? extends Application> appClass, String... args) {
    launch(appClass, ArgFilters.SELECT_ALL, Arrays.asList(args));
  }

  /**
   * A convenience for main wrappers.  Equivalent to:
   * <pre>
   *   AppLauncher.launch(appClass, argFilter, Arrays.asList(args));
   * </pre>
   *
   * @param appClass The application class to instantiate and launch.
   * @param argFilter A filter that selects the {@literal @CmdLine} {@link Arg}s to enable for
   *     parsing.
   * @param args The command line arguments to parse.
   * @see ArgFilters
   */
  public static void launch(Class<? extends Application> appClass, Predicate<Field> argFilter,
      String... args) {
    launch(appClass, argFilter, Arrays.asList(args));
  }

  /**
   * Used to launch an application with a restricted set of {@literal @CmdLine} {@link Arg}s
   * considered for parsing.  This is useful if the classpath includes annotated fields you do not
   * wish arguments to be parsed for.
   *
   * @param appClass The application class to instantiate and launch.
   * @param argFilter A filter that selects the {@literal @CmdLine} {@link Arg}s to enable for
   *     parsing.
   * @param args The command line arguments to parse.
   * @see ArgFilters
   */
  public static void launch(Class<? extends Application> appClass, Predicate<Field> argFilter,
      List<String> args) {
    Preconditions.checkNotNull(appClass);
    Preconditions.checkNotNull(argFilter);
    Preconditions.checkNotNull(args);

    parseArgs(Predicates.<Field>and(Predicates.not(SELECT_APP_CLASS), argFilter), args);
    try {
      new AppLauncher().run(appClass.newInstance());
    } catch (InstantiationException e) {
      throw new IllegalStateException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void parseArgs(Predicate<Field> filter, List<String> args) {
    try {
      if (!new ArgScanner().parse(filter, args)) {
        System.exit(0);
      }
    } catch (ArgScanException e) {
      exit("Failed to scan arguments", e);
    } catch (IllegalArgumentException e) {
      exit("Failed to apply arguments", e);
    }
  }

  private static void exit(String message, Exception error) {
    LOG.log(Level.SEVERE, message + "\n" + error, error);
    System.exit(1);
  }
}
