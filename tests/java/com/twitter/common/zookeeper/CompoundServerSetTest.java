package com.twitter.common.zookeeper;

import java.net.InetSocketAddress;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Command;
import com.twitter.common.net.pool.DynamicHostSet.HostChangeMonitor;
import com.twitter.common.net.pool.DynamicHostSet.MonitorException;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.thrift.ServiceInstance;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;

public class CompoundServerSetTest extends EasyMockTest {
  private static final Map<String, InetSocketAddress> AUX_PORTS = ImmutableMap.of();
  private static final InetSocketAddress END_POINT =
      InetSocketAddress.createUnresolved("foo", 12345);

  private ServerSet.EndpointStatus mockStatus1;
  private ServerSet.EndpointStatus mockStatus2;
  private ServerSet.EndpointStatus mockStatus3;
  private HostChangeMonitor<ServiceInstance> compoundMonitor;

  private ServerSet serverSet1;
  private ServerSet serverSet2;
  private ServerSet serverSet3;
  private Command stop1;
  private Command stop2;
  private Command stop3;
  private CompoundServerSet compoundServerSet;

  private ServiceInstance instance1;
  private ServiceInstance instance2;
  private ServiceInstance instance3;

  private void triggerChange(ServiceInstance... hostChanges) {
    compoundMonitor.onChange(ImmutableSet.copyOf(hostChanges));
  }

  private void triggerChange(
      Capture<HostChangeMonitor<ServiceInstance>> capture,
      ServiceInstance... hostChanges) {

    capture.getValue().onChange(ImmutableSet.copyOf(hostChanges));
  }

  @Before
  public void setUpMocks() throws Exception {
    control = createControl();
    compoundMonitor = createMock(new Clazz<HostChangeMonitor<ServiceInstance>>() { });

    mockStatus1 = createMock(ServerSet.EndpointStatus.class);
    mockStatus2 = createMock(ServerSet.EndpointStatus.class);
    mockStatus3 = createMock(ServerSet.EndpointStatus.class);

    serverSet1 = createMock(ServerSet.class);
    serverSet2 = createMock(ServerSet.class);
    serverSet3 = createMock(ServerSet.class);

    stop1 = createMock(Command.class);
    stop2 = createMock(Command.class);
    stop3 = createMock(Command.class);

    instance1 = createMock(ServiceInstance.class);
    instance2 = createMock(ServiceInstance.class);
    instance3 = createMock(ServiceInstance.class);

    compoundServerSet = new CompoundServerSet(ImmutableList.of(serverSet1, serverSet2, serverSet3));
  }

  @Test
  public void testJoin() throws Exception {
    expect(serverSet1.join(END_POINT, AUX_PORTS, 0)).andReturn(mockStatus1);
    expect(serverSet2.join(END_POINT, AUX_PORTS, 0)).andReturn(mockStatus2);
    expect(serverSet3.join(END_POINT, AUX_PORTS, 0)).andReturn(mockStatus3);

    mockStatus1.leave();
    mockStatus2.leave();
    mockStatus3.leave();

    control.replay();

    compoundServerSet.join(END_POINT, AUX_PORTS, 0).leave();
  }

  @Test(expected = Group.JoinException.class)
  public void testJoinFailure() throws Exception {
    // Throw exception for the first serverSet join.
    expect(serverSet1.join(END_POINT, AUX_PORTS))
        .andThrow(new Group.JoinException("Group join exception", null));

    control.replay();
    compoundServerSet.join(END_POINT, AUX_PORTS);
  }

  @Test(expected = ServerSet.UpdateException.class)
  public void testStatusUpdateFailure() throws Exception {
    expect(serverSet1.join(END_POINT, AUX_PORTS)).andReturn(mockStatus1);
    expect(serverSet2.join(END_POINT, AUX_PORTS)).andReturn(mockStatus2);
    expect(serverSet3.join(END_POINT, AUX_PORTS)).andReturn(mockStatus3);

    mockStatus1.leave();
    mockStatus2.leave();
    expectLastCall().andThrow(new ServerSet.UpdateException("Update exception"));
    mockStatus3.leave();

    control.replay();

    compoundServerSet.join(END_POINT, AUX_PORTS).leave();
  }

  @Test
  public void testMonitor() throws Exception {
    Capture<HostChangeMonitor<ServiceInstance>> set1Capture = createCapture();
    Capture<HostChangeMonitor<ServiceInstance>> set2Capture = createCapture();
    Capture<HostChangeMonitor<ServiceInstance>> set3Capture = createCapture();

    expect(serverSet1.watch(
        EasyMock.<HostChangeMonitor<ServiceInstance>>capture(set1Capture)))
        .andReturn(stop1);
    expect(serverSet2.watch(
        EasyMock.<HostChangeMonitor<ServiceInstance>>capture(set2Capture)))
        .andReturn(stop2);
    expect(serverSet3.watch(
        EasyMock.<HostChangeMonitor<ServiceInstance>>capture(set3Capture)))
        .andReturn(stop3);

    triggerChange(instance1);
    triggerChange(instance1, instance2);
    triggerChange(instance1, instance2, instance3);
    triggerChange(instance1, instance3);
    triggerChange(instance1, instance2, instance3);
    triggerChange(instance3);
    triggerChange();

    control.replay();
    compoundServerSet.watch(compoundMonitor);

    // No new instances.
    triggerChange(set1Capture);
    triggerChange(set2Capture);
    triggerChange(set3Capture);
    // Add one instance from each serverset
    triggerChange(set1Capture, instance1);
    triggerChange(set2Capture, instance2);
    triggerChange(set3Capture, instance3);
    // Remove instance2
    triggerChange(set2Capture);
    // instance1 in both serverset1 and serverset2
    triggerChange(set2Capture, instance1, instance2);
    // Remove instances from serversets.
    triggerChange(set1Capture);
    triggerChange(set2Capture);
    triggerChange(set3Capture);
  }

  @Test(expected = MonitorException.class)
  public void testMonitorFailure() throws Exception {
    serverSet1.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject());
    expectLastCall().andThrow(new MonitorException("Monitor exception", null));

    control.replay();
    compoundServerSet.watch(compoundMonitor);
  }

  @Test
  public void testInitialChange() throws Exception {
    // Ensures that a synchronous change notification during the call to monitor() is properly
    // reported.
    serverSet1.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject());
    expectLastCall().andAnswer(new IAnswer<Command>() {
      @Override public Command answer() {
        @SuppressWarnings("unchecked")
        HostChangeMonitor<ServiceInstance> monitor =
            (HostChangeMonitor<ServiceInstance>) getCurrentArguments()[0];
        monitor.onChange(ImmutableSet.of(instance1, instance2));
        return stop1;
      }
    });
    compoundMonitor.onChange(ImmutableSet.of(instance1, instance2));
    expect(serverSet2.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject()))
        .andReturn(stop2);
    expect(serverSet3.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject()))
        .andReturn(stop3);

    control.replay();

    compoundServerSet.watch(compoundMonitor);
  }

  @Test
  public void testStopMonitoring() throws Exception {
    expect(serverSet1.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject()))
        .andReturn(stop1);
    expect(serverSet2.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject()))
        .andReturn(stop2);
    expect(serverSet3.watch(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject()))
        .andReturn(stop3);

    stop1.execute();
    stop2.execute();
    stop3.execute();

    control.replay();
    compoundServerSet.watch(compoundMonitor).execute();
  }
}
