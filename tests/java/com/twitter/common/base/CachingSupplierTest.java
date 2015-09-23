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

package com.twitter.common.base;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

/**
 * @author William Farner
 */
public class CachingSupplierTest extends EasyMockTest {

  private static final Amount<Long, Time> ONE_SECOND = Amount.of(1L, Time.SECONDS);

  private Supplier<String> supplier;
  private FakeClock clock;
  private Supplier<String> cache;

  @Before
  public void setUp() {
    supplier = createMock(new Clazz<Supplier<String>>() { });
    clock = new FakeClock();
    cache = new CachingSupplier<String>(supplier, ONE_SECOND, clock);
  }

  @Test
  public void testCaches() {
    expect(supplier.get()).andReturn("foo");
    expect(supplier.get()).andReturn("bar");

    control.replay();

    assertEquals("foo", cache.get());
    assertEquals("foo", cache.get());

    clock.advance(Amount.of(999L, Time.MILLISECONDS));
    assertEquals("foo", cache.get());

    clock.advance(Amount.of(1L, Time.MILLISECONDS));
    assertEquals("foo", cache.get());

    clock.advance(Amount.of(1L, Time.MILLISECONDS));
    assertEquals("bar", cache.get());
  }
}
