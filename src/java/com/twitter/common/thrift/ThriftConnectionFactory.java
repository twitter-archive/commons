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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ConnectionFactory;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A connection factory for thrift transport connections to a given host.  This connection factory
 * is lazy and will only create a configured maximum number of active connections - where a
 * {@link ConnectionFactory#create(com.twitter.common.quantity.Amount) created} connection that has
 * not been {@link #destroy destroyed} is considered active.
 *
 * @author John Sirois
 */
public class ThriftConnectionFactory
    implements ConnectionFactory<Connection<TTransport, InetSocketAddress>> {

  public enum TransportType {
    BLOCKING, FRAMED, NONBLOCKING;

    /**
     * Async clients implicitly use a framed transport, requiring the server they connect to to do
     * the same. This prevents specifying a nonblocking client without a framed transport, since
     * that is not compatible with thrift and would simply cause the client to blow up when making a
     * request. Instead, you must explicitly say useFramedTransport(true) for any buildAsync().
     */
    public static TransportType get(boolean framedTransport, boolean nonblocking) {
      if (nonblocking) {
        Preconditions.checkArgument(framedTransport,
            "nonblocking client requires a server running framed transport");
        return NONBLOCKING;
      }

      return framedTransport ? FRAMED : BLOCKING;
    }
  }

  private static InetSocketAddress asEndpoint(String host, int port) {
    MorePreconditions.checkNotBlank(host);
    Preconditions.checkArgument(port > 0);
    return InetSocketAddress.createUnresolved(host, port);
  }

  private InetSocketAddress endpoint;
  private final int maxConnections;
  private final TransportType transportType;
  private final Amount<Long, Time> socketTimeout;
  private final Closure<Connection<TTransport, InetSocketAddress>> postCreateCallback;
  private boolean sslTransport = false;

  private final Set<Connection<TTransport, InetSocketAddress>> activeConnections =
      Sets.newSetFromMap(
          Maps.<Connection<TTransport, InetSocketAddress>, Boolean>newIdentityHashMap());
  private volatile int lastActiveConnectionsSize = 0;

  private final Lock activeConnectionsWriteLock = new ReentrantLock(true);

  /**
   * Creates a thrift connection factory with a plain socket (non-framed transport).
   * This is the same as calling {@link #ThriftConnectionFactory(String, int, int, boolean)} with
   * {@code framedTransport} set to {@code false}.
   *
   * @param host Host to connect to.
   * @param port Port to connect on.
   * @param maxConnections Maximum number of connections for this host:port.
   */
  public ThriftConnectionFactory(String host, int port, int maxConnections) {
    this(host, port, maxConnections, TransportType.BLOCKING);
  }

  /**
   * Creates a thrift connection factory.
   * If {@code framedTransport} is set to {@code true}, {@link TFramedTransport} will be used,
   * otherwise a raw {@link TSocket} will be used.
   *
   * @param host Host to connect to.
   * @param port Port to connect on.
   * @param maxConnections Maximum number of connections for this host:port.
   * @param framedTransport Whether to use framed or blocking transport.
   */
  public ThriftConnectionFactory(String host, int port, int maxConnections,
      boolean framedTransport) {

    this(asEndpoint(host, port), maxConnections, TransportType.get(framedTransport, false));
  }

  /**
   * Creates a thrift connection factory.
   * If {@code framedTransport} is set to {@code true}, {@link TFramedTransport} will be used,
   * otherwise a raw {@link TSocket} will be used.
   *
   * @param endpoint Endpoint to connect to.
   * @param maxConnections Maximum number of connections for this host:port.
   * @param framedTransport Whether to use framed or blocking transport.
   */
  public ThriftConnectionFactory(InetSocketAddress endpoint, int maxConnections,
      boolean framedTransport) {

    this(endpoint, maxConnections, TransportType.get(framedTransport, false));
  }

  /**
   * Creates a thrift connection factory.
   * If {@code framedTransport} is set to {@code true}, {@link TFramedTransport} will be used,
   * otherwise a raw {@link TSocket} will be used.
   * If {@code nonblocking} is set to {@code true}, {@link TNonblockingSocket} will be used,
   * otherwise a raw {@link TSocket} will be used.
   * Timeouts are ignored when nonblocking transport is used.
   *
   * @param host Host to connect to.
   * @param port Port to connect on.
   * @param maxConnections Maximum number of connections for this host:port.
   * @param transportType Whether to use normal blocking, framed blocking, or non-blocking
   *    (implicitly framed) transport.
   */
  public ThriftConnectionFactory(String host, int port, int maxConnections,
      TransportType transportType) {
    this(host, port, maxConnections, transportType, null);
  }

  /**
   * Creates a thrift connection factory.
   * If {@code framedTransport} is set to {@code true}, {@link TFramedTransport} will be used,
   * otherwise a raw {@link TSocket} will be used.
   * If {@code nonblocking} is set to {@code true}, {@link TNonblockingSocket} will be used,
   * otherwise a raw {@link TSocket} will be used.
   * Timeouts are ignored when nonblocking transport is used.
   *
   * @param host Host to connect to.
   * @param port Port to connect on.
   * @param maxConnections Maximum number of connections for this host:port.
   * @param transportType Whether to use normal blocking, framed blocking, or non-blocking
   *          (implicitly framed) transport.
   * @param socketTimeout timeout on thrift i/o operations, or null to default to connectTimeout o
   *          the blocking client.
   */
  public ThriftConnectionFactory(String host, int port, int maxConnections,
      TransportType transportType, Amount<Long, Time> socketTimeout) {
    this(asEndpoint(host, port), maxConnections, transportType, socketTimeout);
  }

  public ThriftConnectionFactory(InetSocketAddress endpoint, int maxConnections,
      TransportType transportType) {
    this(endpoint, maxConnections, transportType, null);
  }

  /**
   * Creates a thrift connection factory.
   * If {@code framedTransport} is set to {@code true}, {@link TFramedTransport} will be used,
   * otherwise a raw {@link TSocket} will be used.
   * If {@code nonblocking} is set to {@code true}, {@link TNonblockingSocket} will be used,
   * otherwise a raw {@link TSocket} will be used.
   * Timeouts are ignored when nonblocking transport is used.
   *
   * @param endpoint Endpoint to connect to.
   * @param maxConnections Maximum number of connections for this host:port.
   * @param transportType Whether to use normal blocking, framed blocking, or non-blocking
   *          (implicitly framed) transport.
   * @param socketTimeout timeout on thrift i/o operations, or null to default to connectTimeout o
   *          the blocking client.
   */
  public ThriftConnectionFactory(InetSocketAddress endpoint, int maxConnections,
      TransportType transportType, Amount<Long, Time> socketTimeout) {
	  this(endpoint, maxConnections, transportType, socketTimeout,
        Closures.<Connection<TTransport, InetSocketAddress>>noop(), false);
  }

  public ThriftConnectionFactory(InetSocketAddress endpoint, int maxConnections,
      TransportType transportType, Amount<Long, Time> socketTimeout,
	  Closure<Connection<TTransport, InetSocketAddress>> postCreateCallback,
	  boolean sslTransport) {
    Preconditions.checkArgument(maxConnections > 0, "maxConnections must be at least 1");
    if (socketTimeout != null) {
      Preconditions.checkArgument(socketTimeout.as(Time.MILLISECONDS) >= 0);
    }

    this.endpoint = Preconditions.checkNotNull(endpoint);
    this.maxConnections = maxConnections;
    this.transportType = transportType;
    this.socketTimeout = socketTimeout;
    this.postCreateCallback = Preconditions.checkNotNull(postCreateCallback);
    this.sslTransport = sslTransport;
  }

  @Override
  public boolean mightCreate() {
    return lastActiveConnectionsSize < maxConnections;
  }

  /**
   * FIXME:  shouldn't this throw TimeoutException instead of returning null
   *         in the timeout cases as per the ConnectionFactory.create javadoc?
   */
  @Override
  public Connection<TTransport, InetSocketAddress> create(Amount<Long, Time> timeout)
      throws TTransportException, IOException {

    Preconditions.checkNotNull(timeout);
    if (timeout.getValue() == 0) {
      return create();
    }

    try {
      long timeRemainingNs = timeout.as(Time.NANOSECONDS);
      long start = System.nanoTime();
      if(activeConnectionsWriteLock.tryLock(timeRemainingNs, TimeUnit.NANOSECONDS)) {
        try {
          if (!willCreateSafe()) {
            return null;
          }

          timeRemainingNs -= (System.nanoTime() - start);

          return createConnection((int) TimeUnit.NANOSECONDS.toMillis(timeRemainingNs));
        } finally {
          activeConnectionsWriteLock.unlock();
        }
      } else {
        return null;
      }
    } catch (InterruptedException e) {
      return null;
    }
  }

  private Connection<TTransport, InetSocketAddress> create()
      throws TTransportException, IOException {
    activeConnectionsWriteLock.lock();
    try {
      if (!willCreateSafe()) {
        return null;
      }

      return createConnection(0);
    } finally {
      activeConnectionsWriteLock.unlock();
    }
  }

  private Connection<TTransport, InetSocketAddress> createConnection(int timeoutMillis)
      throws TTransportException, IOException {
    TTransport transport = createTransport(timeoutMillis);
    if (transport == null) {
      return null;
    }

    Connection<TTransport, InetSocketAddress> connection =
        new TTransportConnection(transport, endpoint);
    postCreateCallback.execute(connection);
    activeConnections.add(connection);
    lastActiveConnectionsSize = activeConnections.size();
    return connection;
  }

  private boolean willCreateSafe() {
    return activeConnections.size() < maxConnections;
  }

  @VisibleForTesting
  TTransport createTransport(int timeoutMillis) throws TTransportException, IOException {
    TSocket socket = null;
    if (transportType != TransportType.NONBLOCKING) {
      // can't do a nonblocking create on a blocking transport
      if (timeoutMillis <= 0) {
        return null;
      }

      if (sslTransport) {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl_socket = (SSLSocket) factory.createSocket(endpoint.getHostName(), endpoint.getPort());
        ssl_socket.setSoTimeout(timeoutMillis);
        return new TSocket(ssl_socket);
      } else {
        socket = new TSocket(endpoint.getHostName(), endpoint.getPort(), timeoutMillis);
      }
    }

    try {
      switch (transportType) {
        case BLOCKING:
          socket.open();
          setSocketTimeout(socket);
          return socket;
        case FRAMED:
          TFramedTransport transport = new TFramedTransport(socket);
          transport.open();
          setSocketTimeout(socket);
          return transport;
        case NONBLOCKING:
          try {
            return new TNonblockingSocket(endpoint.getHostName(), endpoint.getPort());
          } catch (IOException e) {
            throw new IOException("Failed to create non-blocking transport to " + endpoint, e);
          }
      }
    } catch (TTransportException e) {
      throw new TTransportException("Failed to create transport to " + endpoint, e);
    }

    throw new IllegalArgumentException("unknown transport type " + transportType);
  }

  private void setSocketTimeout(TSocket socket) {
    if (socketTimeout != null) {
      socket.setTimeout(socketTimeout.as(Time.MILLISECONDS).intValue());
    }
  }

  @Override
  public void destroy(Connection<TTransport, InetSocketAddress> connection) {
    activeConnectionsWriteLock.lock();
    try {
      boolean wasActiveConnection = activeConnections.remove(connection);
      Preconditions.checkArgument(wasActiveConnection,
          "connection %s not created by this factory", connection);
      lastActiveConnectionsSize = activeConnections.size();
    } finally {
      activeConnectionsWriteLock.unlock();
    }

    // We close the connection outside the critical section which means we may have more connections
    // "active" (open) than maxConnections for a very short time
    connection.close();
  }

  @Override
  public String toString() {
    return String.format("%s[%s]", getClass().getSimpleName(), endpoint);
  }
}
