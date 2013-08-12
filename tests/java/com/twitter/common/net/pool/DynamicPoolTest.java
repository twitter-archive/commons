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

package com.twitter.common.net.pool;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.base.Function;
import com.twitter.common.collections.Pair;
import com.twitter.common.thrift.testing.MockTSocket;
import com.twitter.common.net.loadbalancing.LoadBalancerImpl;
import com.twitter.common.net.loadbalancing.RandomStrategy;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.thrift.TTransportConnection;
import com.twitter.common.thrift.Util;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.common.zookeeper.ServerSet;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.common.zookeeper.ServerSetImpl;
import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;
import org.apache.thrift.transport.TTransport;
import org.apache.zookeeper.ZooDefs;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;

/**
 * @author John Sirois
 */
public class DynamicPoolTest extends BaseZooKeeperTest {

  private IMocksControl control;
  private Function<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>
      poolFactory;
  private DynamicPool connectionPool;
  private LinkedBlockingQueue<Pair<Set<ObjectPool<Connection<TTransport, InetSocketAddress>>>,
      Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>>> poolRebuilds;

  private ServerSet serverSet;

  @Before
  public void mySetUp() throws Exception {
    control = createControl();

    @SuppressWarnings("unchecked")
    Function<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>> poolFactory =
        control.createMock(Function.class);
    this.poolFactory = poolFactory;

    LoadBalancerImpl<InetSocketAddress> lb =
        LoadBalancerImpl.create(new RandomStrategy<InetSocketAddress>());

    poolRebuilds =
        new LinkedBlockingQueue<Pair<Set<ObjectPool<Connection<TTransport, InetSocketAddress>>>,
            Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>>>();
    serverSet = new ServerSetImpl(createZkClient(), ZooDefs.Ids.OPEN_ACL_UNSAFE, "/test-service");
    Closure<Collection<InetSocketAddress>> onBackendsChosen = Closures.noop();
    Amount<Long, Time> restoreInterval = Amount.of(1L, Time.MINUTES);
    connectionPool = new DynamicPool<ServiceInstance, TTransport, InetSocketAddress>(
        serverSet, poolFactory, lb, onBackendsChosen, restoreInterval, Util.GET_ADDRESS,
        Util.IS_ALIVE) {
      @Override
      void poolRebuilt(Set<ObjectPool<Connection<TTransport, InetSocketAddress>>> deadPools,
          Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>> livePools) {
        super.poolRebuilt(deadPools, livePools);
        poolRebuilds.offer(Pair.of(deadPools, livePools));
      }
    };
  }

  @Test
  public void testConstructionBlocksOnInitialPoolBuild() {
    assertNotNull(Iterables.getOnlyElement(poolRebuilds));
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testNoEndpointsAvailable() throws Exception {
    connectionPool.get();
  }

  private EndpointStatus join(String host) throws JoinException, InterruptedException {
    return serverSet.join(
        InetSocketAddress.createUnresolved(host, 42), ImmutableMap.<String, InetSocketAddress>of());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testPoolRebuilds() throws Exception {
    ConnectionFactory<Connection<TTransport, InetSocketAddress>> connectionFactory =
        control.createMock(ConnectionFactory.class);

    TTransport transport = new MockTSocket();
    Connection<TTransport, InetSocketAddress> connection =
        new TTransportConnection(transport, InetSocketAddress.createUnresolved("jake", 1137));

    expect(connectionFactory.create(EasyMock.isA(Amount.class))).andReturn(connection);
    ConnectionPool<Connection<TTransport, InetSocketAddress>> fooPool =
        new ConnectionPool<Connection<TTransport, InetSocketAddress>>(connectionFactory);
    expect(poolFactory.apply(InetSocketAddress.createUnresolved("foo", 42))).andReturn(fooPool);

    control.replay();

    Pair<Set<ObjectPool<Connection<TTransport, InetSocketAddress>>>,
        Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>>
        rebuild1 = poolRebuilds.take();
    assertTrue("Should not have any dead pools on initial rebuild", rebuild1.getFirst().isEmpty());
    assertNoLivePools(rebuild1);

    EndpointStatus fooStatus = join("foo");

    Pair<Set<ObjectPool<Connection<TTransport, InetSocketAddress>>>,
        Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>>
        rebuild2 = poolRebuilds.take();
    assertTrue("The NULL pool should never be tracked as dead", rebuild2.getFirst().isEmpty());
    assertEquals(transport, connectionPool.get().get());

    fooStatus.leave();

    Pair<Set<ObjectPool<Connection<TTransport, InetSocketAddress>>>,
        Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>>
        rebuild3 = poolRebuilds.take();
    assertSame("Expected foo pool to be discarded", fooPool,
        Iterables.getOnlyElement(rebuild3.getFirst()));
    assertNoLivePools(rebuild1);

    control.verify();
  }

  private void assertNoLivePools(Pair<Set<ObjectPool<Connection<TTransport, InetSocketAddress>>>,
      Map<InetSocketAddress, ObjectPool<Connection<TTransport, InetSocketAddress>>>> rebuild)
      throws TimeoutException {

    assertTrue("Expected no live pools to be set", rebuild.getSecond().isEmpty());
    try {
      connectionPool.get();
      fail("Expected server set to be exhausted with no endpoints");
    } catch (ResourceExhaustedException e) {
      // expected
    }
  }
}
