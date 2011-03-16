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

package com.twitter.common.service.registration;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.twitter.common.zookeeper.testing.BaseZooKeeperTest;

/**
 * @author Patrick Chan
 */
public class ServerSet2Test extends BaseZooKeeperTest {
  private static final Logger LOG = Logger.getLogger(ServerSet2Test.class.getName());

  private LinkedBlockingQueue<Object> eventQ;
  private ServerSetListener serverSetListener;

  @Before
  public void mySetUp() throws IOException {
    eventQ = new LinkedBlockingQueue<Object>();
    serverSetListener = new ServerSetListener() {
      @Override public void onChange(Set<Server> serverSet) {
        eventQ.offer(serverSet);
      }
      @Override public void onConnect(boolean connected) {
        eventQ.offer(connected);
      }
    };
  }

  private ServerSet createServerSet() throws IOException {
    return new ZkServerSet("localhost", getPort(), "/test/science/serverset");
  }

  Server createServer(int port)  throws Exception {
    return new Server(port, new HashMap<String, String>());
  }

  @Test
  public void testMembershipChanges() throws Exception {
    ServerSet client = createServerSet();
    client.setListener(serverSetListener);
    assertConnected(false);
    assertConnected(true);

    ServerSet ss1 = createServerSet();
    ServerSet ss2 = createServerSet();
    ServerSet ss3 = createServerSet();

    ss1.join(createServer(11));
    assertChangeFired(11);

    ss2.join(createServer(22));
    assertChangeFired(11, 22);

    expireSession(((ZkServerSet) client).getZkClient());
    assertConnected(false);
    ss3.join(createServer(33));

    // Not sure the order of the following two asserts are stable
    assertChangeFired(11, 22, 33);
    assertConnected(true);

    ss1.unjoin(createServer(11));
    assertChangeFired(22, 33);

    ss3.unjoin(createServer(33));
    assertChangeFired(22);

    ss2.unjoin(createServer(22));
    assertChangeFiredEmpty();
    assertTrue(eventQ.isEmpty());
  }

  private void assertChangeFired(int... ports) throws Exception {
    Set<Server> set = new HashSet<Server>();
    for (int p : ports) {
      set.add(createServer(p));
    }
    assertChangeFired(set);
  }

  protected void assertChangeFiredEmpty() throws InterruptedException {
    assertChangeFired(new HashSet<Server>());
  }

  protected void assertChangeFired(Server... servers) throws InterruptedException {
    Set<Server> set = new HashSet<Server>();
    for (Server s : servers) {
      set.add(s);
    }
    assertChangeFired(set);
  }

  @SuppressWarnings("unchecked")
  protected void assertChangeFired(Set<Server> serverSet) throws InterruptedException {
    Object o = eventQ.poll(90, TimeUnit.SECONDS);
    assertThat(o, instanceOf(Set.class));

    Set<Server> ss = (Set<Server>) o;
    assertEquals(serverSet, ss);
  }

  protected void assertConnected(boolean isConnected) throws InterruptedException {
    Object o = eventQ.poll(90, TimeUnit.SECONDS);
    assertThat(o, instanceOf(Boolean.class));

    Boolean b = (Boolean) o;
    assertEquals(b, isConnected);
  }
}
