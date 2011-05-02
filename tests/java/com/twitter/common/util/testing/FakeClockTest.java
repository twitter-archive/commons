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

package com.twitter.common.util.testing;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author John Sirois
 */
public class FakeClockTest {
  private FakeClock fakeClock;

  @Before
  public void setUp() {
    fakeClock = new FakeClock();
  }

  @Test
  public void testNow() throws InterruptedException {
    assertEquals("A fake clock should start out at time 0", 0, fakeClock.nowMillis());

    fakeClock.setNowMillis(42L);
    assertEquals("A fake clock's time should only be controled by setNow", 42L, fakeClock.nowMillis());

    long start = System.currentTimeMillis();
    Thread.sleep(10L);
    assertTrue(System.currentTimeMillis() - start > 0);
    assertEquals("A fake clock's time should only be controled by setNow", 42L, fakeClock.nowMillis());
  }

  @Test
  public void testWaitFor() {
    fakeClock.waitFor(42L);
    assertEquals(42L, fakeClock.nowMillis());

    fakeClock.waitFor(42L);
    assertEquals(84L, fakeClock.nowMillis());
  }

  @Test
  public void testAdvance() {
    fakeClock.advance(Amount.of(42L, Time.MILLISECONDS));
    assertEquals(42L, fakeClock.nowMillis());

    fakeClock.advance(Amount.of(42L, Time.NANOSECONDS));
    assertEquals(42000042L, fakeClock.nowNanos());

    fakeClock.advance(Amount.of(-42L, Time.MILLISECONDS));
    assertEquals(42L, fakeClock.nowNanos());

    try {
      fakeClock.advance(Amount.of(-43L, Time.NANOSECONDS));
      fail("Should not allow clock to be set to negative time");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }
}
