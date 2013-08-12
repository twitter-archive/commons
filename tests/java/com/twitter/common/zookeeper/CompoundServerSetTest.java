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

import com.twitter.common.net.pool.DynamicHostSet.HostChangeMonitor;
import com.twitter.common.net.pool.DynamicHostSet.MonitorException;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;

/**
 * Tests CompoundServerSet (Tests the composite logic). ServerSetImplTest takes care of testing
 * the actual serverset logic.
 */
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

    mockStatus1.update(Status.DEAD);
    mockStatus2.update(Status.DEAD);
    mockStatus3.update(Status.DEAD);

    control.replay();

    ServerSet.EndpointStatus status = compoundServerSet.join(END_POINT, AUX_PORTS, 0);
    status.update(Status.DEAD);
  }

  @Test(expected = Group.JoinException.class)
  public void testJoinFailure() throws Exception {
    // Throw exception for the first serverSet join.
    expect(serverSet1.join(END_POINT, AUX_PORTS, Status.ALIVE))
        .andThrow(new Group.JoinException("Group join exception", null));

    control.replay();
    compoundServerSet.join(END_POINT, AUX_PORTS, Status.ALIVE);
  }

  @Test(expected = ServerSet.UpdateException.class)
  public void testStatusUpdateFailure() throws Exception {
    expect(serverSet1.join(END_POINT, AUX_PORTS, Status.ALIVE)).andReturn(mockStatus1);
    expect(serverSet2.join(END_POINT, AUX_PORTS, Status.ALIVE)).andReturn(mockStatus2);
    expect(serverSet3.join(END_POINT, AUX_PORTS, Status.ALIVE)).andReturn(mockStatus3);

    mockStatus1.update(Status.DEAD);
    mockStatus2.update(Status.DEAD);
    expectLastCall().andThrow(new ServerSet.UpdateException("Update exception"));
    mockStatus3.update(Status.DEAD);

    control.replay();

    ServerSet.EndpointStatus status = compoundServerSet.join(END_POINT, AUX_PORTS, Status.ALIVE);
    status.update(Status.DEAD);
  }

  @Test
  public void testMonitor() throws Exception {
    Capture<HostChangeMonitor<ServiceInstance>> set1Capture = createCapture();
    Capture<HostChangeMonitor<ServiceInstance>> set2Capture = createCapture();
    Capture<HostChangeMonitor<ServiceInstance>> set3Capture = createCapture();

    serverSet1.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>capture(set1Capture));
    serverSet2.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>capture(set2Capture));
    serverSet3.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>capture(set3Capture));

    triggerChange(instance1);
    triggerChange(instance1, instance2);
    triggerChange(instance1, instance2, instance3);
    triggerChange(instance1, instance3);
    triggerChange(instance1, instance2, instance3);
    triggerChange(instance3);
    triggerChange();

    control.replay();
    compoundServerSet.monitor(compoundMonitor);

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
    serverSet1.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject());
    expectLastCall().andThrow(new MonitorException("Monitor exception", null));

    control.replay();
    compoundServerSet.monitor(compoundMonitor);
  }

  @Test
  public void testInitialChange() throws Exception {
    // Ensures that a synchronous change notification during the call to monitor() is properly
    // reported.
    serverSet1.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject());
    expectLastCall().andAnswer(new IAnswer<Void>() {
      @Override public Void answer() {
        @SuppressWarnings("unchecked")
        HostChangeMonitor<ServiceInstance> monitor =
            (HostChangeMonitor<ServiceInstance>) getCurrentArguments()[0];
        monitor.onChange(ImmutableSet.of(instance1, instance2));
        return null;
      }
    });
    compoundMonitor.onChange(ImmutableSet.of(instance1, instance2));
    serverSet2.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject());
    serverSet3.monitor(EasyMock.<HostChangeMonitor<ServiceInstance>>anyObject());

    control.replay();

    compoundServerSet.monitor(compoundMonitor);
  }
}
