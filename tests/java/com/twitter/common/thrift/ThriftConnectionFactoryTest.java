// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.thrift;

import com.twitter.common.thrift.testing.MockTSocket;
import com.twitter.common.net.pool.Connection;
import com.twitter.common.net.pool.ObjectPool;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author John Sirois
 */
public class ThriftConnectionFactoryTest {

  @Test
  public void testPreconditions() {
    try {
      new ThriftConnectionFactory(null, 1, 1);
      fail("a non-null host should be required");
    } catch (NullPointerException e) {
      // expected
    }

    try {
      new ThriftConnectionFactory(" ", 1, 1);
      fail("a non-blank host should be required");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      new ThriftConnectionFactory("localhost", 0, 1);
      fail("a valid concrete remote port should be required");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      new ThriftConnectionFactory("localhost", 65536, 1);
      fail("a valid port should be required");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      new ThriftConnectionFactory("localhost", 65535, 0);
      fail("a non-zero value for maxConnections should be required");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testMaxConnections() throws TTransportException, IOException {
    ThriftConnectionFactory thriftConnectionFactory = createConnectionFactory(2);

    Connection<TTransport, InetSocketAddress> connection1 =
        thriftConnectionFactory.create(ObjectPool.NO_TIMEOUT);
    assertOpenConnection(connection1);

    Connection<TTransport, InetSocketAddress> connection2 =
        thriftConnectionFactory.create(ObjectPool.NO_TIMEOUT);
    assertOpenConnection(connection2);
    assertThat(connection1, not(sameInstance(connection2)));

    assertNull("Should've reached maximum connections",
        thriftConnectionFactory.create(ObjectPool.NO_TIMEOUT));

    thriftConnectionFactory.destroy(connection1);
    assertClosedConnection(connection1);

    Connection<TTransport, InetSocketAddress> connection3 =
        thriftConnectionFactory.create(ObjectPool.NO_TIMEOUT);
    assertOpenConnection(connection3);
    assertThat(connection3, allOf(not(sameInstance(connection1)), not(sameInstance(connection2))));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInactiveConnectionReturn() {
    createConnectionFactory(1).destroy(new TTransportConnection(new MockTSocket(),
        InetSocketAddress.createUnresolved(MockTSocket.HOST, MockTSocket.PORT)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullConnectionReturn() {
    createConnectionFactory(1).destroy(null);
  }

  private void assertOpenConnection(Connection<TTransport, InetSocketAddress> connection) {
    assertNotNull(connection);
    assertTrue(connection.isValid());
    assertTrue(connection.get().isOpen());
  }

  private void assertClosedConnection(Connection<TTransport, InetSocketAddress> connection) {
    assertFalse(connection.isValid());
    assertFalse(connection.get().isOpen());
  }

  private ThriftConnectionFactory createConnectionFactory(int maxConnections) {
    return new ThriftConnectionFactory("foo", 1234, maxConnections) {
      @Override TTransport createTransport(int timeoutMillis) throws TTransportException {
        TTransport transport = new MockTSocket();
        transport.open();
        return transport;
      }
    };
  }
}
