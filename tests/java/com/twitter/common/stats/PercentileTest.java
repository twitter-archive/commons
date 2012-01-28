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
  private static final double[] PERCENTILES = new double[] {0, 10, 50, 90, 99, 99.9, 99.99, 100};

  private Percentile<Integer> percentiles;

  @Before
  public void setUp() {
    percentiles = new Percentile<Integer>("test", SAMPLE_RATE, PERCENTILES);
  }

  @Test
  public void testNoData() {
    checkPercentiles(percentiles, 0, 0, 0, 0, 0, 0, 0, 0);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testSingleValue() {
    percentiles.record(10);
    checkPercentiles(percentiles, 10, 10, 10, 10, 10, 10, 10, 10);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testConstant() {
    for (int i = 0; i < 100; i++) {
      percentiles.record(10);
    }

    checkPercentiles(percentiles, 10, 10, 10, 10, 10, 10, 10, 10);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testLinear() {
    for (int i = 0; i < 10001; i++) {
      percentiles.record(i);
    }

    checkPercentiles(percentiles, 0, 1000, 5000, 9000, 9900, 9990, 9999, 10000);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testMultipleSampleWindows() {
    Percentile<Integer> mypercentile = new Percentile<Integer>("test", 2, null, PERCENTILES);

    for (int i = 0; i < 10000; i++) {
      mypercentile.record(i);
    }
    // Large number filler so that our percentile hit an integer index.
    mypercentile.record(90000);
    checkPercentiles(mypercentile, 0, 1000, 5000, 9000, 9900, 9990, 9999, 90000);

    for (int i = 10000; i < 20000; i++) {
      mypercentile.record(i);
    }
    checkPercentiles(mypercentile, 0, 2000, 10000, 18000, 19800, 19980, 19998, 90000);

    for (int i = 20000; i < 30000; i++) {
      mypercentile.record(i);
    }
    // Previous filler is flushed from the sample queue. Refill.
    mypercentile.record(90000);
    checkPercentiles(mypercentile, 10000, 12000, 20000, 28000, 29800, 29980, 29998, 90000);
  }


  @Test
  public void testNullSampler() {
    int N = 10001;
    Percentile<Integer> mypercentile = new Percentile<Integer>("test", 1, null, PERCENTILES);
    for (int i = 0; i < N; i++) {
      mypercentile.record(i);
    }
    assertThat(mypercentile.samples.size(), is(N));
    checkPercentiles(mypercentile, 0, 1000, 5000, 9000, 9900, 9990, 9999, 10000);
    checkValuesAreFlushed(mypercentile);
  }

  @Test
  public void testReverseLinear() {
    for (int i = 0; i < 10001; i++) {
      percentiles.record(i);
    }

    checkPercentiles(percentiles, 0, 1000, 5000, 9000, 9900, 9990, 9999, 10000);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testShuffledSteps() {
    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < 1000; i++) {
      for (int j = 0; j < 10; j++) {
        values.add(i);
      }
    }
    values.add(2000);
    Collections.shuffle(values);
    for (int sample : values) {
      percentiles.record(sample);
    }

    checkPercentiles(percentiles, 0, 100, 500, 900, 990, 999, 999, 2000);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testNegativeValues() {
    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < 1000; i++) {
      for (int j = 0; j < 10; j++) {
        values.add(-1 * i);
      }
    }
    values.add(-2000);
    Collections.shuffle(values);
    for (int sample : values) {
      percentiles.record(sample);
    }

    checkPercentiles(percentiles, -2000, -900, -500, -100, -10, -1, 0, 0);
    checkValuesAreFlushed(percentiles);
  }

  @Test
  public void testPercentileInterpolates() {
    for (int i = 0; i < 9999; i++) {
      percentiles.record(i);
    }

    checkPercentiles(percentiles, 0, 999.8, 4999, 8998.2, 9898.02, 9988.002, 9997.0002, 9998);
    checkValuesAreFlushed(percentiles);
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
    checkPercentiles(percentiles, 1, 1, 1, 1, 1, 1, 1, 1);
    checkValuesAreFlushed(percentiles);
  }

  private void checkPercentiles(Percentile<Integer> input_percentiles, double... values) {
    assertThat(values.length, is(PERCENTILES.length));

    for (int i = 0; i < values.length; i++) {
      checkPercentile(input_percentiles, PERCENTILES[i], values[i]);
    }
  }

  private void checkValuesAreFlushed(Percentile<Integer> input_percentiles, double... values) {
    // Check that the values were flushed.
    for (int i = 0; i < values.length; i++) {
      checkPercentile(input_percentiles, PERCENTILES[i], 0);
    }
    assertThat(percentiles.samples.isEmpty(), is(true));
  }

  private void checkPercentile(Percentile<Integer> input_percentiles,
                               double percentile, double value) {
    assertEquals(value, input_percentiles.getPercentile(percentile).sample(), EPSILON);
  }
}
