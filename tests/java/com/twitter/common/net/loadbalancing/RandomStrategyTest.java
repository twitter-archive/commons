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

import java.util.Collection;
import java.util.HashSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.base.Closure;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.capture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author William Farner
 */
public class RandomStrategyTest extends EasyMockTest {

  private static final String BACKEND_1 = "backend1";
  private static final String BACKEND_2 = "backend2";
  private static final String BACKEND_3 = "backend3";

  private Closure<Collection<String>> onBackendsChosen;

  private LoadBalancingStrategy<String> randomStrategy;

  @Before
  public void setUp() {
    onBackendsChosen = createMock(new Clazz<Closure<Collection<String>>>() {});

    randomStrategy = new RandomStrategy<String>();
  }

  @Test(expected = ResourceExhaustedException.class)
  public void testNoBackends() throws ResourceExhaustedException {
    control.replay();

    randomStrategy.nextBackend();
  }

  @Test
  public void testEmptyBackends() throws ResourceExhaustedException {
    Capture<Collection<String>> capture = createCapture();
    onBackendsChosen.execute(capture(capture));
    control.replay();

    randomStrategy.offerBackends(Sets.<String>newHashSet(), onBackendsChosen);

    try {
      randomStrategy.nextBackend();
      fail("Expected ResourceExhaustedException to be thrown");
    } catch (ResourceExhaustedException e) {
      // expected
    }

    assertTrue(capture.hasCaptured());
    assertTrue(capture.getValue().isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testRandomSelection() throws ResourceExhaustedException {
    Capture<Collection<String>> capture = createCapture();
    onBackendsChosen.execute(capture(capture));
    control.replay();

    HashSet<String> backends = Sets.newHashSet(BACKEND_1, BACKEND_2, BACKEND_3);
    randomStrategy.offerBackends(backends, onBackendsChosen);

    assertTrue(ImmutableSet.of(BACKEND_1, BACKEND_2, BACKEND_3)
        .contains(randomStrategy.nextBackend()));
    assertTrue(capture.hasCaptured());
    assertEquals(backends, Sets.newHashSet(capture.getValue()));
  }
}
