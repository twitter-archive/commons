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

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author William Farner
 */
public class PercentileTest {

  private static final double EPSILON = 1e-6;
  private static final float SAMPLE_RATE = 100;
  private static final int[] PERCENTILES = new int[] {10, 50, 90, 99};

  private Percentile<Integer> percentiles;

  @Before
  public void setUp() {
    percentiles = new Percentile<Integer>("test", SAMPLE_RATE, PERCENTILES);
  }

  @Test
  public void testNoData() {
    checkPercentiles(0, 0, 0, 0);
  }

  @Test
  public void testSingleValue() {
    percentiles.record(10);
    checkPercentiles(10, 10, 10, 10);
  }

  @Test
  public void testConstant() {
    for (int i = 0; i < 100; i++) {
      percentiles.record(10);
    }

    checkPercentiles(10, 10, 10, 10);
  }

  @Test
  public void testLinear() {
    for (int i = 0; i < 100; i++) {
      percentiles.record(i + 1);
    }

    checkPercentiles(10, 50, 90, 99);
  }

  @Test
  public void testReverseLinear() {
    for (int i = 0; i < 100; i++) {
      percentiles.record(i + 1);
    }

    checkPercentiles(10, 50, 90, 99);
  }

  @Test
  public void testShuffledSteps() {
    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < 1000; i++) {
      for (int j = 0; j < 10; j++) {
        values.add(i + 1);
      }
    }
    Collections.shuffle(values);
    for (int sample : values) {
      percentiles.record(sample);
    }

    checkPercentiles(100, 500, 900, 990);
  }

  @Test
  public void testNegativeValues() {
    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < 1000; i++) {
      for (int j = 0; j < 10; j++) {
        values.add(-1 * i);
      }
    }
    Collections.shuffle(values);
    for (int sample : values) {
      percentiles.record(sample);
    }

    checkPercentiles(-900, -500, -100, -10);
  }

  @Test
  public void testPercentileInterpolates() {
    for (int i = 0; i < 101; i++) {
      percentiles.record(i + 1);
    }

    checkPercentiles(10.5, 50.5, 90.5, 99.5);
  }

  @Test
  public void testHonorsBufferLimit() {
    for (int i = 0; i < 1000; i++) {
      percentiles.record(0);
    }

    // Now fill the buffer with a constant.
    for (int i = 0; i < Percentile.MAX_BUFFER_SIZE; i++) {
      percentiles.record(1);
    }

    assertThat(percentiles.samples.size(), is(Percentile.MAX_BUFFER_SIZE));
    checkPercentiles(1, 1, 1, 1);
  }

  private void checkPercentiles(double... values) {
    assertThat(values.length, is(PERCENTILES.length));

    for (int i = 0; i < values.length; i++) {
      checkPercentile(PERCENTILES[i], values[i]);
    }

    // Check that the values were flushed.
    for (int i = 0; i < values.length; i++) {
      checkPercentile(PERCENTILES[i], 0);
    }

    assertThat(percentiles.samples.isEmpty(), is(true));
  }

  private void checkPercentile(int percentile, double value) {
    assertEquals(value, percentiles.getPercentile(percentile).sample(), EPSILON);
  }
}
