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

package com.twitter.common.net.loadbalancing;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.net.loadbalancing.LoadBalancingStrategy.ConnectionResult;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;
import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class SubsetStrategyTest extends EasyMockTest {

  private static final String BACKEND_1 = "backend1";
  private static final String BACKEND_2 = "backend2";
  private static final String BACKEND_3 = "backend3";

  private Closure<Collection<String>> onBackendsChosen;
  private LoadBalancingStrategy<String> wrappedStrategy;

  private LoadBalancingStrategy<String> subsetStrategy;

  @Before
  public void setUp() {
    wrappedStrategy = createMock(new Clazz<LoadBalancingStrategy<String>>() {});
    onBackendsChosen = createMock(new Clazz<Closure<Collection<String>>>() {});

    subsetStrategy = new SubsetStrategy<String>(2, wrappedStrategy);
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testNoBackends() throws ResourceExhaustedException {
    expect(wrappedStrategy.nextBackend()).andThrow(new ResourceExhaustedException("No backends."));

    control.replay();

    subsetStrategy.nextBackend();
  }

  @Test
  public void testForwardsSubsetBackends() {
    Capture<Set<String>> backendCapture = createCapture();
    wrappedStrategy.offerBackends(capture(backendCapture), eq(onBackendsChosen));
    control.replay();

    subsetStrategy.offerBackends(Sets.newHashSet(BACKEND_1, BACKEND_2, BACKEND_3),
        onBackendsChosen);

    assertThat(backendCapture.getValue().size(), is(2));
  }

  @Test
  public void testForwardsOnlySubsetRequests() {
    Capture<Set<String>> backendCapture = createCapture();
    wrappedStrategy.offerBackends(capture(backendCapture), eq(onBackendsChosen));

    control.replay();

    Set<String> allBackends = Sets.newHashSet(BACKEND_1, BACKEND_2, BACKEND_3);
    subsetStrategy.offerBackends(allBackends, onBackendsChosen);
    Set<String> backends = backendCapture.getValue();
    assertThat(backends.size(), is(2));

    // One backend should have been unused, makes sure the appropriate calls are ignored for it.
    String unusedBackend = Iterables.getOnlyElement(Sets.difference(allBackends, backends));
    subsetStrategy.addRequestResult(unusedBackend, RequestResult.SUCCESS, 0L);
    subsetStrategy.addConnectResult(unusedBackend, ConnectionResult.FAILED, 0L);
    subsetStrategy.connectionReturned(unusedBackend);
  }
}
