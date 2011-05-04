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

package com.twitter.common.util;

import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author William Farner
 */
public class SamplerTest {

  private IMocksControl control;

  private Random random;

  @Before
  public void setUp() throws Exception {
    control = createControl();

    random = control.createMock(Random.class);
  }

  @After
  public void verify() {
    control.verify();
  }

  @Test
  public void testThresholdWorks() {
    for (int i = 0; i <= 100; i++) {
      expect(random.nextDouble()).andReturn(0.01 * i);
    }

    control.replay();

    Sampler sampler = new Sampler(25, random);

    for (int i = 0; i <= 100; i++) {
      assertThat(sampler.select(), is(i < 25));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsNegativePercent() {
    control.replay();

    new Sampler(-10, random);
  }
}
