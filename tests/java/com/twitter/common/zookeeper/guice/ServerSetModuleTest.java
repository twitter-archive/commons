package com.twitter.common.zookeeper.guice;

import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.util.Providers;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.ShutdownRegistry.ShutdownRegistryImpl;
import com.twitter.common.application.modules.LifecycleModule.ServiceRunner;
import com.twitter.common.application.modules.LocalServiceRegistry;
import com.twitter.common.zookeeper.ServerSet;
import com.twitter.common.zookeeper.ZooKeeperClient;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

public class ServerSetModuleTest extends BaseZooKeeperTest {

  private IMocksControl control;

  private ServerSet serverSet;
  private ShutdownRegistry shutdownRegistry;
  private ZooKeeperClient zooKeeperClient;
  private LocalServiceRegistry localServiceRegistry;

  @Before
  public void mySetUp() {
    control = EasyMock.createControl();
    serverSet = control.createMock(ServerSet.class);

    shutdownRegistry = new ShutdownRegistryImpl();
    zooKeeperClient = createZkClient();
    Set<ServiceRunner> localServices = ImmutableSet.of();
    localServiceRegistry = new LocalServiceRegistry(Providers.of(localServices), shutdownRegistry);
  }

  @After
  public void verify() {
    control.verify();
  }

  @Test
  public void testInjection() {
    control.replay();

    Guice.createInjector(ImmutableList.of(ServerSetModule.builder().build(), new AbstractModule() {
      @Override protected void configure() {
        bind(ServerSet.class).toInstance(serverSet);
        bind(ZooKeeperClient.class).toInstance(zooKeeperClient);
        bind(ShutdownRegistry.class).toInstance(shutdownRegistry);
        bind(LocalServiceRegistry.class).toInstance(localServiceRegistry);
      }
    }));
  }
}
