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

package com.twitter.common.stats;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.annotation.XmlElement;

import com.google.common.base.Supplier;

/**
 * Test for MovingWindowDelta.
 *
 * @author Feng Zhuge
 */
public class MovingWindowDeltaTest {
  private static final int DEFAULT_WINDOW_SIZE = 5;

  private AtomicLong externalValue = new AtomicLong();

  public Supplier<AtomicLong> getSupplier() {
    return new Supplier<AtomicLong>() {
      @Override
      public AtomicLong get() {
        return externalValue;
      }
    };
  }

  @Test
  public void testOneSample() {
    MovingWindowDelta<AtomicLong> movingWindowDelta = MovingWindowDelta.of(
      "test", getSupplier(), DEFAULT_WINDOW_SIZE);

    externalValue.getAndSet(7l);
    externalValue.getAndSet(11l);

    assertEquals(11l, movingWindowDelta.doSample().longValue());
  }

  @Test
  public void testMultipleSamples() {
    MovingWindowDelta<AtomicLong> movingWindowDelta = MovingWindowDelta.of(
      "test", getSupplier(), DEFAULT_WINDOW_SIZE);

    externalValue.getAndSet(3l);
    assertEquals(3l, movingWindowDelta.doSample().longValue());
    externalValue.getAndSet(8l);
    assertEquals(8l, movingWindowDelta.doSample().longValue());
  }

  @Test
  public void TestExpiringCounts() {
    MovingWindowDelta<AtomicLong> movingWindowDelta = MovingWindowDelta.of(
      "test", getSupplier(), DEFAULT_WINDOW_SIZE);

    long expectedDelta;
    for (long i = 0; i < 100; ++i) {
      expectedDelta = i < DEFAULT_WINDOW_SIZE ? i + 1 : DEFAULT_WINDOW_SIZE;

      externalValue.getAndSet(i + 1);
      assertEquals(expectedDelta, movingWindowDelta.doSample().longValue());
    }
  }

  @Test
  public void TestDifferentValueExpiring() {
    MovingWindowDelta<AtomicLong> movingWindowDelta =
        MovingWindowDelta.of("test", getSupplier(), 5);

    long ret = 0l;
    for (long  i = 0; i < 10; ++i) {
      externalValue.getAndSet(i * i);
      ret = movingWindowDelta.doSample();
    }
    assertEquals(65l, ret);
  }
}
