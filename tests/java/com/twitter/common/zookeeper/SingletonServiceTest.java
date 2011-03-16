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

package com.twitter.common.zookeeper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.twitter.common.base.Closure;
import com.twitter.common.base.ExceptionalCommand;
import com.twitter.common.collections.Pair;
import com.twitter.common.testing.EasyMockTest;
import com.twitter.common.zookeeper.Candidate.Leader;
import com.twitter.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.thrift.Status;
import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;

/**
 * @author William Farner
 */
public class SingletonServiceTest extends EasyMockTest {
  private static final int PORT_A = 1234;
  private static final int PORT_B = 8080;

  private SingletonService.LeadershipListener listener;
  private ServerSet serverSet;
  private ServerSet.EndpointStatus endpointStatus;
  private Candidate candidate;
  private ExceptionalCommand<Group.JoinException> abdicate;

  private SingletonService service;

  @Before
  @SuppressWarnings("unchecked")
  public void mySetUp() throws IOException {
    listener = createMock(SingletonService.LeadershipListener.class);
    serverSet = createMock(ServerSet.class);
    candidate = createMock(Candidate.class);
    endpointStatus = createMock(ServerSet.EndpointStatus.class);
    abdicate = createMock(ExceptionalCommand.class);

    service = new SingletonService(serverSet, candidate);
  }

  private void newLeader(final String hostName) throws Exception {
    service.lead(InetSocketAddress.createUnresolved(hostName, PORT_A),
        ImmutableMap.of("http-admin", InetSocketAddress.createUnresolved(hostName, PORT_B)),
        Status.STARTING, listener);
  }

  private Pair<InetSocketAddress, Map<String, InetSocketAddress>> getEndpoints(String host) {
    return new Pair<InetSocketAddress, Map<String, InetSocketAddress>>(
        InetSocketAddress.createUnresolved(host, PORT_A),
        ImmutableMap.of("http-admin", InetSocketAddress.createUnresolved(host, PORT_B)));
  }

  @Test
  public void testLead() throws Exception {
    Pair<InetSocketAddress, Map<String, InetSocketAddress>> endpoints = getEndpoints("foo");

    Capture<Leader> leaderCapture = new Capture<Leader>();

    expect(candidate.offerLeadership(capture(leaderCapture))).andReturn(null);
    expect(serverSet.join(endpoints.getFirst(), endpoints.getSecond(), Status.STARTING))
        .andReturn(endpointStatus);
    listener.onLeading(endpointStatus);
    endpointStatus.update(Status.ALIVE);
    endpointStatus.update(Status.STOPPED);

    control.replay();

    newLeader("foo");
    endpointStatus.update(Status.ALIVE);
    endpointStatus.update(Status.STOPPED);

    // This actually elects the leader.
    leaderCapture.getValue().onElected(abdicate);
  }

  @Test
  public void testLeadJoinFailure() throws Exception {
    Pair<InetSocketAddress, Map<String, InetSocketAddress>> endpoints = getEndpoints("foo");

    Capture<Leader> leaderCapture = new Capture<Leader>();

    expect(candidate.offerLeadership(capture(leaderCapture))).andReturn(null);
    expect(serverSet.join(endpoints.getFirst(), endpoints.getSecond(), Status.STARTING))
        .andThrow(new Group.JoinException("Injected join failure.", new Exception()));

    control.replay();

    newLeader("foo");

    // This actually elects the leader.
    leaderCapture.getValue().onElected(abdicate);
  }

  @Test
  public void testLeadMulti() throws Exception {
    List<Capture<Leader>> captures = Lists.newArrayList();

    for (int i = 0; i < 5; i++) {
      Pair<InetSocketAddress, Map<String, InetSocketAddress>> endpoints = getEndpoints("foo" + i);

      Capture<Leader> leaderCapture = new Capture<Leader>();
      captures.add(leaderCapture);

      expect(candidate.offerLeadership(capture(leaderCapture))).andReturn(null);
      expect(serverSet.join(endpoints.getFirst(), endpoints.getSecond(), Status.STARTING))
          .andReturn(endpointStatus);
      listener.onLeading(endpointStatus);
      endpointStatus.update(Status.ALIVE);
      endpointStatus.update(Status.STOPPED);
    }

    control.replay();

    for (int i = 0; i < 5; i++) {
      final String leaderName = "foo" + i;
      newLeader(leaderName);
      endpointStatus.update(Status.ALIVE);
      endpointStatus.update(Status.STOPPED);

      // This actually elects the leader.
      captures.get(i).getValue().onElected(abdicate);
    }
  }
}
