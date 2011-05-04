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

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stats;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * @author John Sirois
 */
public class ConnectionPoolTest {
  private IMocksControl control;
  private ConnectionFactory<Connection<String, Integer>> connectionFactory;
  private ReentrantLock poolLock;

  @Before public void setUp() throws Exception {
    control = EasyMock.createControl();

    @SuppressWarnings("unchecked") ConnectionFactory<Connection<String, Integer>> connectionFactory =
        control.createMock(ConnectionFactory.class);
    this.connectionFactory = connectionFactory;

    poolLock = new ReentrantLock();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testReleaseUnmanaged() {
    @SuppressWarnings("unchecked")
    Connection<String, Integer> connection = control.createMock(Connection.class);

    Executor executor = createMockExecutor();
    control.replay();

    try {
      createConnectionPool(executor).release(connection);
    } finally {
      control.verify();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testReleaseUnmanagedIdentity() throws Exception {
    class TestConnection implements Connection<String, Integer> {
      @Override public String get() {
        return "test";
      }

      @Override public boolean isValid() {
        return true;
      }

      @Override public void close() {
        // noop
      }

      @Override public Integer getEndpoint() {
        return 1;
      }

      @Override public boolean equals(Object obj) {
        return obj instanceof TestConnection;
      }
    }

    Executor executor = createMockExecutor();

    TestConnection connection = new TestConnection();
    expect(connectionFactory.create(eq(ObjectPool.NO_TIMEOUT))).andReturn(connection);

    control.replay();

    ConnectionPool<Connection<String, Integer>> connectionPool = createConnectionPool(executor);
    assertSame(connection, connectionPool.get());

    TestConnection equalConnection = new TestConnection();
    assertEquals(equalConnection, connection);
    try {
      connectionPool.release(equalConnection);
    } finally {
      control.verify();
    }
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testExhaustedNull() throws Exception {
    Executor executor = createMockExecutor();
    expect(connectionFactory.create(eq(ObjectPool.NO_TIMEOUT))).andReturn(null);
    control.replay();

    try {
      createConnectionPool(executor).get();
    } finally {
      control.verify();
    }
  }

  @Test(expected = TimeoutException.class)
  public void testExhaustedWillNot() throws Exception {
    Executor executor = createMockExecutor();

    @SuppressWarnings("unchecked")
    Connection<String, Integer> connection = control.createMock(Connection.class);
    expect(connectionFactory.create(eq(ObjectPool.NO_TIMEOUT))).andReturn(connection);

    expect(connectionFactory.mightCreate()).andReturn(false);
    control.replay();

    ConnectionPool<Connection<String, Integer>> connectionPool = createConnectionPool(executor);
    assertSame(connection, connectionPool.get());

    try {
      connectionPool.get(Amount.of(1L, Time.NANOSECONDS));
    } finally {
      control.verify();
    }
  }

  @Test
  public void testCloseDisallowsGets() throws Exception {
    Executor executor = createMockExecutor();
    control.replay();

    ConnectionPool<Connection<String, Integer>> connectionPool = createConnectionPool(executor);
    connectionPool.close();

    try {
      connectionPool.get();
      fail();
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      connectionPool.get(Amount.of(1L, Time.MILLISECONDS));
      fail();
    } catch (IllegalStateException e) {
      // expected
    }

    control.verify();
  }

  @Test
  public void testCloseCloses() throws Exception {
    Executor executor = Executors.newSingleThreadExecutor();

    @SuppressWarnings("unchecked")
    Connection<String, Integer> connection = control.createMock(Connection.class);
    expect(connectionFactory.create(eq(ObjectPool.NO_TIMEOUT))).andReturn(connection);

    @SuppressWarnings("unchecked")
    Connection<String, Integer> connection2 = control.createMock(Connection.class);
    expect(connectionFactory.create(eq(ObjectPool.NO_TIMEOUT))).andReturn(connection2);
    expect(connectionFactory.mightCreate()).andReturn(true);

    connectionFactory.destroy(connection2);
    expect(connection2.isValid()).andReturn(true);

    control.replay();

    ConnectionPool<Connection<String, Integer>> connectionPool = createConnectionPool(executor);

    // This 1st connection is leased out of the pool at close-time and so should not be touched
    Connection<String, Integer> leasedDuringClose = connectionPool.get();

     // this 2nd connection is available when close is called so it should be destroyed
    connectionPool.release(connectionPool.get());
    connectionPool.close();

    control.verify();
    control.reset();

    connectionFactory.destroy(connection);

    control.replay();

    // After a close, releases should destroy connections
    connectionPool.release(leasedDuringClose);

    control.verify();
  }

  @Test
  public void testCreating() throws Exception {
    Amount<Long, Time> timeout = Amount.of(1L, Time.SECONDS);

    Executor executor =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>());

    expect(connectionFactory.mightCreate()).andReturn(true);

    Capture<Amount<Long, Time>> timeout1 = new Capture<Amount<Long, Time>>();
    @SuppressWarnings("unchecked")
    Connection<String, Integer> connection1 = control.createMock(Connection.class);
    expect(connectionFactory.create(capture(timeout1))).andReturn(connection1);

    Capture<Amount<Long, Time>> timeout2 = new Capture<Amount<Long, Time>>();
    @SuppressWarnings("unchecked")
    Connection<String, Integer> connection2 = control.createMock(Connection.class);
    expect(connectionFactory.create(capture(timeout2))).andReturn(connection2);

    control.replay();

    ConnectionPool<Connection<String, Integer>> connectionPool = createConnectionPool(executor);

    assertSame(connection1, connectionPool.get(timeout));
    assertTrue(timeout1.hasCaptured());
    Long timeout1Millis = timeout1.getValue().as(Time.MILLISECONDS);
    assertTrue(timeout1Millis > 0 && timeout1Millis <= timeout.as(Time.MILLISECONDS));

    assertSame(connection2, connectionPool.get(timeout));
    assertTrue(timeout2.hasCaptured());
    Long timeout2Millis = timeout1.getValue().as(Time.MILLISECONDS);
    assertTrue(timeout2Millis > 0 && timeout2Millis <= timeout.as(Time.MILLISECONDS));

    control.verify();
  }

  private Executor createMockExecutor() {
    return control.createMock(Executor.class);
  }

  private ConnectionPool<Connection<String, Integer>> createConnectionPool(Executor executor) {
    return new ConnectionPool<Connection<String, Integer>>(executor, poolLock,
        connectionFactory, Stats.STATS_PROVIDER);
  }
}
