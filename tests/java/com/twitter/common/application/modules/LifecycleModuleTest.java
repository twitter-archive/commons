package com.twitter.common.application.modules;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.junit.Test;

import com.twitter.common.application.ShutdownRegistry.ShutdownRegistryImpl;
import com.twitter.common.application.modules.LifecycleModule.LaunchException;
import com.twitter.common.application.modules.LifecycleModule.ServiceRunner;
import com.twitter.common.application.modules.LocalServiceRegistry.LocalService;
import com.twitter.common.base.Command;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static com.twitter.common.application.modules.LifecycleModule.bindLocalService;
import static com.twitter.common.net.InetSocketAddressHelper.getLocalAddress;

/**
 * @author William Farner
 */
public class LifecycleModuleTest extends EasyMockTest {

  private static class SystemModule extends AbstractModule {
    @Override protected void configure() {
      install(new LifecycleModule());
      bind(UncaughtExceptionHandler.class).toInstance(new UncaughtExceptionHandler() {
        @Override public void uncaughtException(Thread thread, Throwable throwable) {
          fail("Uncaught exception.");
        }
      });
    }
  }

  @Test
  public void testNoServices() {
    control.replay();

    Injector injector = Guice.createInjector(new SystemModule());

    LocalServiceRegistry registry = injector.getInstance(LocalServiceRegistry.class);
    assertEquals(Optional.<InetSocketAddress>absent(), registry.getPrimarySocket());
    assertEquals(ImmutableMap.<String, InetSocketAddress>of(), registry.getAuxiliarySockets());
  }

  @Test
  public void testNoRunner() throws Exception {
    final Command primaryShutdown = createMock(Command.class);
    final Command auxShutdown = createMock(Command.class);

    primaryShutdown.execute();
    auxShutdown.execute();

    Module testModule = new AbstractModule() {
      @Override protected void configure() {
        bindLocalService(binder(), LocalService.primaryService(99, primaryShutdown));
        bindLocalService(binder(), LocalService.auxiliaryService("foo", 100, auxShutdown));
      }
    };

    Injector injector = Guice.createInjector(new SystemModule(), testModule);
    LocalServiceRegistry registry = injector.getInstance(LocalServiceRegistry.class);

    control.replay();

    assertEquals(Optional.of(getLocalAddress(99)), registry.getPrimarySocket());
    assertEquals(ImmutableMap.of("foo", getLocalAddress(100)), registry.getAuxiliarySockets());

    injector.getInstance(ShutdownRegistryImpl.class).execute();
  }

  @Test
  public void testOrdering() throws Exception {
    final ServiceRunner runner = createMock(ServiceRunner.class);
    Command shutdown = createMock(Command.class);

    expect(runner.launch()).andReturn(LocalService.primaryService(100, shutdown));
    shutdown.execute();

    Module testModule = new AbstractModule() {
      @Override protected void configure() {
        LifecycleModule.runnerBinder(binder()).addBinding().toInstance(runner);
      }
    };

    Injector injector = Guice.createInjector(new SystemModule(), testModule);
    LocalServiceRegistry registry = injector.getInstance(LocalServiceRegistry.class);

    control.replay();

    assertEquals(Optional.of(getLocalAddress(100)), registry.getPrimarySocket());
    injector.getInstance(ShutdownRegistryImpl.class).execute();
  }

  @Test(expected = IllegalStateException.class)
  public void testFailedLauncher() throws Exception {
    final ServiceRunner runner = createMock(ServiceRunner.class);

    expect(runner.launch()).andThrow(new LaunchException("Injected failure."));

    Module testModule = new AbstractModule() {
      @Override protected void configure() {
        LifecycleModule.runnerBinder(binder()).addBinding().toInstance(runner);
      }
    };

    Injector injector = Guice.createInjector(new SystemModule(), testModule);
    LocalServiceRegistry registry = injector.getInstance(LocalServiceRegistry.class);

    control.replay();

    assertEquals(Optional.of(getLocalAddress(100)), registry.getPrimarySocket());
  }
}
