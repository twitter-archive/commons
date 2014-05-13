// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

import com.google.common.base.Function;
import com.google.common.base.Supplier;

import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;
import com.twitter.common.util.testing.FakeClock;

import junit.framework.Assert;

/**
 * Test the Windowed abstract class by making a very simple implementation.
 */
public class WindowedTest {

  private class WindowedBox extends Windowed<Integer[]> {
    WindowedBox(Amount<Long, Time > window, int slices, Clock clock) {
      super(Integer[].class, window, slices,
          new Supplier<Integer[]>() {
            @Override public Integer[] get() {
              Integer[] box = new Integer[1];
              box[0] = 0;
              return box;
            }
          },
          new Function<Integer[], Integer[]>() {
            @Override public Integer[] apply(Integer[] xs) {
              xs[0] = 0;
              return xs;
            }
          }, clock);
    }

    void increment() {
      getCurrent()[0] += 1;
    }

    int sum() {
      int s = 0;
      for (Integer[] box : getTenured()) {
        s += box[0];
      }
      return s;
    }
  }

  @Test
  public void testWindowed() {
    Amount<Long, Time > window = Amount.of(1L, Time.MINUTES);
    int slices = 3;
    Amount<Long, Time > delta = Amount.of(
        Amount.of(1L, Time.MINUTES).as(Time.NANOSECONDS) / 3, Time.NANOSECONDS);
    FakeClock clock = new FakeClock();
    WindowedBox win = new WindowedBox(window, slices, clock);
    // [0][0][0][0]
    clock.advance(Amount.of(1L, Time.NANOSECONDS));

    win.increment();
    // [0][0][0][1]
    Assert.assertEquals(0, win.sum());

    clock.advance(delta);
    win.increment();
    win.increment();
    Assert.assertEquals(1, win.sum());
    // [0][0][1][2]

    clock.advance(delta);
    win.increment();
    win.increment();
    win.increment();
    Assert.assertEquals(3, win.sum());
    // [0][1][2][3]

    clock.advance(delta);
    win.increment();
    win.increment();
    win.increment();
    win.increment();
    Assert.assertEquals(6, win.sum());
    // [1][2][3][4]

    clock.advance(delta);
    win.increment();
    win.increment();
    win.increment();
    win.increment();
    win.increment();
    Assert.assertEquals(9, win.sum());
    // [2][3][4][5]
  }

}
