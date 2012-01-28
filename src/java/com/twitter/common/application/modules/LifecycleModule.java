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

package com.twitter.common.application.modules;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

import com.twitter.common.application.Lifecycle;
import com.twitter.common.application.LocalServiceRegistry;
import com.twitter.common.application.LocalServiceRegistry.Port;
import com.twitter.common.application.ServiceLauncher;
import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.ShutdownRegistry.ShutdownRegistryImpl;
import com.twitter.common.application.ShutdownStage;
import com.twitter.common.application.StartupRegistry;
import com.twitter.common.application.StartupStage;
import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;

/**
 * Binding module for startup and shutdown controller and registries.
 *
 * Bindings provided by this module:
 * <ul>
 *   <li>{@code @StartupStage ExceptionalCommand} - Command to execute all startup actions.
 *   <li>{@code ShutdownRegistry} - Registry for adding shutdown actions.
 *   <li>{@code @ShutdownStage Command} - Command to execute all shutdown commands.
 * </ul>
 *
 * If you would like to register a startup action that starts a local network service, please
 * consider using {@link LocalServiceRegistry}.
 *
 * @author William Farner
 */
public class LifecycleModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Lifecycle.class).in(Singleton.class);

    bind(Key.get(ExceptionalCommand.class, StartupStage.class)).to(StartupRegistry.class);
    bind(StartupRegistry.class).in(Singleton.class);

    bind(ShutdownRegistry.class).to(ShutdownRegistryImpl.class);
    bind(Key.get(Command.class, ShutdownStage.class)).to(ShutdownRegistryImpl.class);
    bind(ShutdownRegistryImpl.class).in(Singleton.class);
    bindStartupAction(binder(), ShutdownHookRegistration.class);

    bind(LocalServiceRegistry.class).in(Singleton.class);

    // Ensure that there is least an empty set for port names and service launchers.
    Multibinder.newSetBinder(binder(), String.class, Port.class);
    Multibinder.newSetBinder(binder(), ServiceLauncher.class);
  }

  /**
   * Binds a startup action that will register and announce a local service.
   *
   * @param binder Binder to bind against.
   * @param portName Port name to register.
   * @param launcher Launcher class that will announce the port.
   */
  public static void bindServiceLauncher(Binder binder, String portName,
      Class<? extends ServiceLauncher> launcher) {
    Multibinder.newSetBinder(binder, String.class, Port.class).addBinding().toInstance(portName);
    bindStartupAction(binder, launcher);
    Multibinder.newSetBinder(binder, ServiceLauncher.class).addBinding().to(launcher);
    binder.bind(launcher).in(Singleton.class);
  }

  /**
   * Adds a startup action to the startup registry binding.
   *
   * @param binder Binder to bind against.
   * @param actionClass Class to bind (and instantiate via guice) for execution at startup.
   */
  public static void bindStartupAction(Binder binder,
      Class<? extends ExceptionalCommand> actionClass) {

    Multibinder.newSetBinder(binder, ExceptionalCommand.class, StartupStage.class)
        .addBinding().to(actionClass);
  }

  /**
   * Startup command to register the shutdown registry as a process shutdown hook.
   */
  private static class ShutdownHookRegistration implements Command {
    private final Command shutdownCommand;

    @Inject ShutdownHookRegistration(@ShutdownStage Command shutdownCommand) {
      this.shutdownCommand = Preconditions.checkNotNull(shutdownCommand);
    }

    @Override public void execute() {
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override public void run() {
          shutdownCommand.execute();
        }
      }, "ShutdownRegistry-Hook"));
    }
  }

  /**
   * An action that is responsible for launching a local service, and registering it with the
   * local service registry.
   *
   * @param <E> Exception type thrown during service launch.
   */
  public static abstract class RegisteringServiceLauncher<E extends Exception>
      implements ServiceLauncher<E> {

    private final LocalServiceRegistry serviceRegistry;

    public RegisteringServiceLauncher(LocalServiceRegistry serviceRegistry) {
      this.serviceRegistry = Preconditions.checkNotNull(serviceRegistry);
    }

    @Override
    public final void execute() throws E {
      serviceRegistry.announce(getPortName(), launchAndGetPort(), isPrimaryService());
    }

    /**
     * Initiates a launch of the service and returns the port that the service is listening on.
     *
     * @return The service's listening port.
     */
    public abstract int launchAndGetPort() throws E;
  }
}
