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

package com.twitter.common.zookeeper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.testing.TearDown;

import org.apache.thrift.protocol.TProtocol;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.net.pool.DynamicHostSet;
import com.twitter.common.thrift.TResourceExhaustedException;
import com.twitter.common.thrift.Thrift;
import com.twitter.common.thrift.ThriftFactory;
import com.twitter.common.thrift.ThriftFactory.ThriftFactoryException;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import com.twitter.thrift.Endpoint;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;

import static org.junit.Assert.*;

/**
 *
 * TODO(William Farner): Change this to remove thrift dependency.
 *
 * @author John Sirois
 */
public class ServerSetImplTest extends BaseZooKeeperTest {
  private static final Logger LOG = Logger.getLogger(ServerSetImpl.class.getName());
  private static final List<ACL> ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;
  private static final String SERVICE = "/twitter/services/puffin_hosebird";

  private LinkedBlockingQueue<ImmutableSet<ServiceInstance>> serverSetBuffer;
  private DynamicHostSet.HostChangeMonitor<ServiceInstance> serverSetMonitor;

  @Before
  public void mySetUp() throws IOException {
    serverSetBuffer = new LinkedBlockingQueue<ImmutableSet<ServiceInstance>>();
    serverSetMonitor = new DynamicHostSet.HostChangeMonitor<ServiceInstance>() {
      @Override public void onChange(ImmutableSet<ServiceInstance> serverSet) {
        serverSetBuffer.offer(serverSet);
      }
    };
  }

  private ServerSetImpl createServerSet() throws IOException {
    return new ServerSetImpl(createZkClient(), ACL, SERVICE);
  }

  @Test
  public void testLifecycle() throws Exception {
    ServerSetImpl client = createServerSet();
    client.monitor(serverSetMonitor);
    assertChangeFiredEmpty();

    ServerSetImpl server = createServerSet();
    EndpointStatus status = server.join(InetSocketAddress.createUnresolved("foo", 1234),
        makePortMap("http-admin", 8080), Status.ALIVE);

    ServiceInstance serviceInstance = new ServiceInstance(new Endpoint("foo", 1234),
        ImmutableMap.of("http-admin", new Endpoint("foo", 8080)), Status.ALIVE);

    assertChangeFired(serviceInstance);

    status.update(Status.STOPPING);
    assertChangeFired(serviceInstance.deepCopy().setStatus(Status.STOPPING));

    expireSession(server.getZkClient());
    assertChangeFiredEmpty();

    // We should've auto re-joined in our previous state.
    assertChangeFired(serviceInstance.deepCopy().setStatus(Status.STOPPING));

    // Membership does not change during our monitor's expiration so we should not be notified.
    expireSession(client.getZkClient());

    status.update(Status.STOPPED);
    assertChangeFired(serviceInstance.deepCopy().setStatus(Status.STOPPED));

    // Neither membership nor status changed, so we should not be notified.
    status.update(Status.STOPPED);

    status.update(Status.DEAD);
    assertChangeFiredEmpty();

    assertTrue(serverSetBuffer.isEmpty());
  }

  @Test
  public void testMembershipChanges() throws Exception {
    ServerSetImpl client = createServerSet();
    client.monitor(serverSetMonitor);
    assertChangeFiredEmpty();

    ServerSetImpl server = createServerSet();

    EndpointStatus foo = join(server, "foo");
    assertChangeFired("foo");

    expireSession(client.getZkClient());

    EndpointStatus bar = join(server, "bar");

    // We should've auto re-monitored membership, but not been notifed of "foo" since this was not a
    // change, just "foo", "bar" since this was an addition.
    assertChangeFired("foo", "bar");

    foo.update(Status.DEAD);
    assertChangeFired("bar");

    EndpointStatus baz = join(server, "baz");
    assertChangeFired("bar", "baz");

    baz.update(Status.DEAD);
    assertChangeFired("bar");

    bar.update(Status.DEAD);
    assertChangeFiredEmpty();

    assertTrue(serverSetBuffer.isEmpty());
  }

  @Test
  public void testOrdering() throws Exception {
    ServerSetImpl client = createServerSet();
    client.monitor(serverSetMonitor);
    assertChangeFiredEmpty();

    Map<String, InetSocketAddress> server1Ports = makePortMap("http-admin1", 8080);
    Map<String, InetSocketAddress> server2Ports = makePortMap("http-admin2", 8081);
    Map<String, InetSocketAddress> server3Ports = makePortMap("http-admin3", 8082);

    ServerSetImpl server1 = createServerSet();
    ServerSetImpl server2 = createServerSet();
    ServerSetImpl server3 = createServerSet();

    ServiceInstance instance1 = new ServiceInstance(new Endpoint("foo", 1000),
        ImmutableMap.of("http-admin1", new Endpoint("foo", 8080)), Status.ALIVE);
    ServiceInstance instance2 = new ServiceInstance(new Endpoint("foo", 1001),
        ImmutableMap.of("http-admin2", new Endpoint("foo", 8081)), Status.ALIVE);
    ServiceInstance instance3 = new ServiceInstance(new Endpoint("foo", 1002),
        ImmutableMap.of("http-admin3", new Endpoint("foo", 8082)), Status.ALIVE);

    EndpointStatus status1 = server1.join(InetSocketAddress.createUnresolved("foo", 1000),
        server1Ports, Status.ALIVE);
    assertEquals(ImmutableList.of(instance1), ImmutableList.copyOf(serverSetBuffer.take()));

    EndpointStatus status2 = server2.join(InetSocketAddress.createUnresolved("foo", 1001),
        server2Ports, Status.ALIVE);
    assertEquals(ImmutableList.of(instance1, instance2),
        ImmutableList.copyOf(serverSetBuffer.take()));

    EndpointStatus status3 = server3.join(InetSocketAddress.createUnresolved("foo", 1002),
        server3Ports, Status.ALIVE);
    assertEquals(ImmutableList.of(instance1, instance2, instance3),
        ImmutableList.copyOf(serverSetBuffer.take()));

    status2.update(Status.DEAD);
    assertEquals(ImmutableList.of(instance1, instance3),
        ImmutableList.copyOf(serverSetBuffer.take()));
  }

  //TODO(Jake Mannix) move this test method to ServerSetConnectionPoolTest, which should be renamed to
  //DynamicBackendConnectionPoolTest, and refactor assertChangeFired* methods to be used both
  //here and there
  @Test
  public void testThriftWithServerSet() throws Exception {
    final AtomicReference<Socket> clientConnection = new AtomicReference<Socket>();
    final CountDownLatch connected = new CountDownLatch(1);
    final ServerSocket server = new ServerSocket(0);
    Thread service = new Thread(new Runnable() {
      @Override public void run() {
        try {
          clientConnection.set(server.accept());
        } catch (IOException e) {
          LOG.log(Level.WARNING, "Problem accepting a connection to thrift server", e);
        } finally {
          connected.countDown();
        }
      }
    });
    service.setDaemon(true);
    service.start();

    ServerSetImpl serverSetImpl = new ServerSetImpl(createZkClient(), SERVICE);
    serverSetImpl.monitor(serverSetMonitor);
    assertChangeFiredEmpty();
    InetSocketAddress localSocket = new InetSocketAddress(server.getLocalPort());
    EndpointStatus status = serverSetImpl.join(localSocket,
        Maps.<String, InetSocketAddress>newHashMap(), Status.STARTING);
    assertChangeFired(ImmutableMap.<InetSocketAddress, Status>of(localSocket, Status.STARTING));

    Service.Iface svc = createThriftClient(serverSetImpl);
    try {
      svc.getString();
      fail("ServerSet is currently empty, should throw exception here.");
    } catch (TResourceExhaustedException e) {
      assertTrue(true);
    }
    status.update(Status.ALIVE);
    assertChangeFired(ImmutableMap.<InetSocketAddress, Status>of(localSocket, Status.ALIVE));

    svc = createThriftClient(serverSetImpl);
    try {
      String value = svc.getString();
      LOG.info("Got value: " + value + " from server");
      assertEquals(Service.Iface.DONE, value);
    } catch (TResourceExhaustedException e) {
      fail("ServerSet is not empty, should not throw exception here");
    } finally {
      connected.await();
      server.close();
    }
  }

  private Service.Iface createThriftClient(DynamicHostSet<ServiceInstance> serverSet)
      throws ThriftFactoryException {

    final Thrift<Service.Iface> thrift = ThriftFactory.create(Service.Iface.class).build(serverSet);
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        thrift.close();
      }
    });
    return thrift.create();
  }

  private static Map<String, InetSocketAddress> makePortMap(String name, int port) {
    return ImmutableMap.of(name, InetSocketAddress.createUnresolved("foo", port));
  }

  public static class Service {
    public static interface Iface {
      public static final String DONE = "done";
      public String getString() throws TResourceExhaustedException;
    }

    public static class Client implements Iface {
      public Client(TProtocol protocol) {
        assertNotNull(protocol);
      }
      @Override public String getString() {
        return DONE;
      }
    }
  }

  private EndpointStatus join(ServerSet serverSet, String host)
      throws JoinException, InterruptedException {

    return serverSet.join(InetSocketAddress.createUnresolved(host, 42),
        ImmutableMap.<String, InetSocketAddress>of(), Status.ALIVE);
  }

  private void assertChangeFired(Map<InetSocketAddress, Status> hostsStatuses)
      throws InterruptedException {
    assertChangeFired(
        ImmutableSet.copyOf(Iterables.transform(ImmutableSet.copyOf(hostsStatuses.entrySet()),
        new Function<Map.Entry<InetSocketAddress, Status>, ServiceInstance>() {
          @Override public ServiceInstance apply(Map.Entry<InetSocketAddress, Status> e) {
            return new ServiceInstance(new Endpoint(e.getKey().getHostName(), e.getKey().getPort()),
                ImmutableMap.<String, Endpoint>of(), e.getValue());
          }
        })));
  }

  private void assertChangeFired(String... serviceHosts)
      throws InterruptedException {

    assertChangeFired(ImmutableSet.copyOf(Iterables.transform(ImmutableSet.copyOf(serviceHosts),
        new Function<String, ServiceInstance>() {
          @Override public ServiceInstance apply(String serviceHost) {
            return new ServiceInstance(new Endpoint(serviceHost, 42),
                ImmutableMap.<String, Endpoint>of(), Status.ALIVE);
          }
        })));
  }

  protected void assertChangeFiredEmpty() throws InterruptedException {
    assertChangeFired(ImmutableSet.<ServiceInstance>of());
  }

  protected void assertChangeFired(ServiceInstance... serviceInstances)
      throws InterruptedException {
    assertChangeFired(ImmutableSet.copyOf(serviceInstances));
  }

  protected void assertChangeFired(ImmutableSet<ServiceInstance> serviceInstances)
      throws InterruptedException {
    assertEquals(serviceInstances, serverSetBuffer.take());
  }
}
