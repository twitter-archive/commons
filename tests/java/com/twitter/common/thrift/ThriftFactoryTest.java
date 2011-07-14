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

package com.twitter.common.thrift;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.TearDown;
import com.google.common.testing.junit4.TearDownTestCase;

import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingTransport;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.net.pool.DynamicHostSet;
import com.twitter.common.thrift.ThriftFactoryTest.GoodService.AsyncIface;
import com.twitter.thrift.ServiceInstance;

/**
 * @author John Sirois
 */
public class ThriftFactoryTest extends TearDownTestCase {

  private static final Logger LOG = Logger.getLogger(ThriftFactoryTest.class.getName());
  private IMocksControl control;

  static class GoodService {
    public interface Iface {
      String doWork() throws TResourceExhaustedException;
    }

    public interface AsyncIface {
      void doWork(AsyncMethodCallback<String> callback);
    }

    public static final String DONE = "done";

    public static class Client implements Iface {
      public Client(TProtocol protocol) {
        assertNotNull(protocol);
      }

      @Override public String doWork() throws TResourceExhaustedException {
        return DONE;
      }
    }

    public static class AsyncClient extends TAsyncClient implements AsyncIface {
      public AsyncClient(TProtocolFactory factory, TAsyncClientManager manager,
          TNonblockingTransport transport) {
        super(factory, manager, transport);
        assertNotNull(factory);
        assertNotNull(manager);
        assertNotNull(transport);
      }

      @Override public void doWork(AsyncMethodCallback<String> callback) {
        callback.onComplete(DONE);
      }
    }
  }

  static class BadService {
    public interface Iface {
      void doWork();
    }
    public interface AsyncIface {
      void doWork(AsyncMethodCallback<Void> callback);
    }

    public static class Client implements Iface {
      @Override public void doWork() {
        throw new UnsupportedOperationException();
      }
    }
  }

  private ImmutableSet<InetSocketAddress> endpoints;

  @Before
  public void setUp() throws Exception {
    control = EasyMock.createControl();
    endpoints = ImmutableSet.of(new InetSocketAddress(5555));
  }

  @Test(expected = NullPointerException.class)
  public void testNullServiceInterface() {
    ThriftFactory.create(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadServiceInterface() {
    ThriftFactory.create(GoodService.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadServiceImpl() throws ThriftFactory.ThriftFactoryException {
    ThriftFactory.<BadService.Iface>create(BadService.Iface.class)
        .build(endpoints);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadAsyncServiceImpl() throws ThriftFactory.ThriftFactoryException {
    ThriftFactory.<BadService.AsyncIface>create(BadService.AsyncIface.class)
        .useFramedTransport(true)
        .buildAsync(endpoints);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoBackends() {
    ThriftFactory.create(GoodService.Iface.class)
        .build(ImmutableSet.<InetSocketAddress>of());
  }

  @Test
  public void testCreate() throws Exception {
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

    try {
      final Thrift<GoodService.Iface> thrift = ThriftFactory.create(GoodService.Iface.class)
          .withMaxConnectionsPerEndpoint(1)
          .build(ImmutableSet.of(new InetSocketAddress(server.getLocalPort())));
      addTearDown(new TearDown() {
        @Override public void tearDown() {
          thrift.close();
        }
      });

      GoodService.Iface client = thrift.create();

      assertEquals(GoodService.DONE, client.doWork());
    } finally {
      connected.await();
      server.close();
    }

    Socket socket = clientConnection.get();
    assertNotNull(socket);
    socket.close();
  }

  @Test(expected = TResourceExhaustedException.class)
  public void testCreateEmpty() throws Exception {
    @SuppressWarnings("unchecked")
    DynamicHostSet<ServiceInstance> emptyHostSet = control.createMock(DynamicHostSet.class);
    final Thrift<GoodService.Iface> thrift = ThriftFactory.create(GoodService.Iface.class)
        .withMaxConnectionsPerEndpoint(1)
        .build(emptyHostSet);
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        thrift.close();
      }
    });
    GoodService.Iface client = thrift.create();

    // This should throw a TResourceExhaustedException
    client.doWork();
  }

  @Test
  public void testCreateAsync()
      throws IOException, InterruptedException, ThriftFactory.ThriftFactoryException {
    final String responseHolder[] = new String[] {null};
    final CountDownLatch done = new CountDownLatch(1);
    AsyncMethodCallback<String> callback = new AsyncMethodCallback<String>() {
      @Override
      public void onComplete(String response) {
        responseHolder[0] = response;
        done.countDown();
      }

      @Override
      public void onError(Throwable throwable) {
        responseHolder[0] = throwable.toString();
        done.countDown();
      }
    };

    final Thrift<AsyncIface> thrift = ThriftFactory.create(GoodService.AsyncIface.class)
        .withMaxConnectionsPerEndpoint(1)
        .useFramedTransport(true)
        .buildAsync(ImmutableSet.of(new InetSocketAddress(1234)));
    addTearDown(new TearDown() {
      @Override public void tearDown() {
        thrift.close();
      }
    });
    GoodService.AsyncIface client = thrift.builder()
        .blocking()
        .create();

    client.doWork(callback);
    assertTrue("wasn't called back in time, callback got " + responseHolder[0],
        done.await(5000, TimeUnit.MILLISECONDS));
    assertEquals(GoodService.DONE, responseHolder[0]);
  }
}
