package com.twitter.common.application.modules;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Provider;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.application.modules.LifecycleModule.LaunchException;
import com.twitter.common.application.modules.LifecycleModule.ServiceRunner;
import com.twitter.common.application.modules.LocalServiceRegistry.LocalService;
import com.twitter.common.base.Commands;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author William Farner
 */
public class LocalServiceRegistryTest extends EasyMockTest {

  private static final Function<InetSocketAddress, Integer> INET_TO_PORT =
      new Function<InetSocketAddress, Integer>() {
        @Override public Integer apply(InetSocketAddress address) {
          return address.getPort();
        }
      };

  private static final String A = "a";
  private static final String B = "b";
  private static final String C = "c";

  private ServiceRunner runner1;
  private ServiceRunner runner2;
  private Provider<Set<ServiceRunner>> serviceProvider;
  private ShutdownRegistry shutdownRegistry;
  private LocalServiceRegistry registry;

  @Before
  public void setUp() {
    runner1 = createMock(ServiceRunner.class);
    runner2 = createMock(ServiceRunner.class);
    serviceProvider = createMock(new Clazz<Provider<Set<ServiceRunner>>>() { });
    shutdownRegistry = createMock(ShutdownRegistry.class);
    registry = new LocalServiceRegistry(serviceProvider, shutdownRegistry);
  }

  @Test
  public void testCreate() throws LaunchException {
    expect(serviceProvider.get()).andReturn(ImmutableSet.of(runner1, runner2));
    expect(runner1.launch()).andReturn(primary(1));
    expect(runner2.launch()).andReturn(auxiliary(A, 2));
    shutdownRegistry.addAction(Commands.NOOP);
    expectLastCall().times(2);

    control.replay();

    checkPorts(Optional.of(1), ImmutableMap.of(A, 2));
  }

  private LocalService primary(int port) {
    return LocalService.primaryService(port, Commands.NOOP);
  }

  private LocalService auxiliary(String name, int port) {
    return LocalService.auxiliaryService(name, port, Commands.NOOP);
  }

  private LocalService auxiliary(Set<String> names, int port) {
    return LocalService.auxiliaryService(names, port, Commands.NOOP);
  }

  @Test
  public void testNoPrimary() throws LaunchException {
    expect(serviceProvider.get()).andReturn(ImmutableSet.of(runner1));
    expect(runner1.launch()).andReturn(auxiliary(A, 2));
    shutdownRegistry.addAction(Commands.NOOP);
    expectLastCall().times(1);

    control.replay();

    assertFalse(registry.getPrimarySocket().isPresent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMultiplePrimaries() throws LaunchException {
    expect(serviceProvider.get()).andReturn(ImmutableSet.of(runner1, runner2));
    expect(runner1.launch()).andReturn(primary(1));
    expect(runner2.launch()).andReturn(primary(2));
    shutdownRegistry.addAction(Commands.NOOP);
    expectLastCall().times(2);

    control.replay();

    registry.getPrimarySocket();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDuplicateName() throws LaunchException {
    expect(serviceProvider.get()).andReturn(ImmutableSet.of(runner1, runner2));
    expect(runner1.launch()).andReturn(auxiliary(A, 1));
    expect(runner2.launch()).andReturn(auxiliary(A, 2));
    shutdownRegistry.addAction(Commands.NOOP);
    expectLastCall().times(2);

    control.replay();

    registry.getPrimarySocket();
  }

  @Test
  public void testAllowsPortReuse() throws LaunchException {
    expect(serviceProvider.get()).andReturn(ImmutableSet.of(runner1, runner2));
    expect(runner1.launch()).andReturn(auxiliary(A, 2));
    expect(runner2.launch()).andReturn(auxiliary(B, 2));
    shutdownRegistry.addAction(Commands.NOOP);
    expectLastCall().times(2);

    control.replay();

    checkPorts(Optional.<Integer>absent(), ImmutableMap.of(A, 2, B, 2));
  }

  @Test
  public void testMultiNameBreakout() throws LaunchException {
    expect(serviceProvider.get()).andReturn(ImmutableSet.of(runner1, runner2));
    expect(runner1.launch()).andReturn(auxiliary(A, 2));
    expect(runner2.launch()).andReturn(auxiliary(ImmutableSet.of(B, C), 6));
    shutdownRegistry.addAction(Commands.NOOP);
    expectLastCall().times(2);

    control.replay();

    checkPorts(Optional.<Integer>absent(), ImmutableMap.of(A, 2, B, 6, C, 6));
  }

  private void checkPorts(Optional<Integer> primary, Map<String, Integer> expected) {
    Optional<InetSocketAddress> registeredSocket = registry.getPrimarySocket();
    Optional<Integer> registeredPort = registeredSocket.isPresent()
        ? Optional.of(registeredSocket.get().getPort()) : Optional.<Integer>absent();

    assertEquals(primary, registeredPort);
    assertEquals(expected, Maps.transformValues(registry.getAuxiliarySockets(), INET_TO_PORT));
  }
}
