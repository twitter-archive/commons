package com.twitter.common.zookeeper.guice;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.modules.LocalServiceRegistry;
import com.twitter.common.zookeeper.ZooKeeperClient;

/**
 * TODO(Anand) Write a generic ModuleTest baseclass using the spi package com.google.inject.spi
 *
 * @author Anand Madhavan
 */
public class ServerSetModuleTest {
  // Mock out all the required bindings
  @Mock
  ShutdownRegistry shutdownRegistry;
  @Mock
  ZooKeeperClient zooKeeperClient;
  @Mock
  LocalServiceRegistry localServiceRegistry;

  @Test
  public void testInjection() {
    MockitoAnnotations.initMocks(this);
    // Ensure that creation of an injector with ServerSetModule works...
    Guice.createInjector(ImmutableList.of(new ServerSetModule(), new AbstractModule() {
      // Override bindings for ones that the module needs
      @Override
      protected void configure() {
        bind(ZooKeeperClient.class).toInstance(zooKeeperClient);
        bind(ShutdownRegistry.class).toInstance(shutdownRegistry);
        bind(LocalServiceRegistry.class).toInstance(localServiceRegistry);
      }
    }));
  }
}
