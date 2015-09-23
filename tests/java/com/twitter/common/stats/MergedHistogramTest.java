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

import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;

import static org.junit.Assert.assertEquals;

public class MergedHistogramTest {

  @Test
  public void testEmptyMergedHistogram() {
    ApproximateHistogram empty[] = new ApproximateHistogram[0];
    Histogram mergedHistogram = ApproximateHistogram.merge(empty);

    assertEquals(0L, mergedHistogram.getQuantile(0.5));
  }

  @Test
  public void testMergedSimilarHistogram() {
    int n = 10;
    ApproximateHistogram histograms[] = new ApproximateHistogram[n];
    for (int i = 0; i < n; i++) {
      ApproximateHistogram h = new ApproximateHistogram();
      h.add(i);
      histograms[i] = h;
    }

    Histogram mergedHistogram = ApproximateHistogram.merge(histograms);
    assertEquals(0L, mergedHistogram.getQuantile(0.0));
    assertEquals(1L, mergedHistogram.getQuantile(0.1));
    assertEquals(5L, mergedHistogram.getQuantile(0.5));
    assertEquals(9L, mergedHistogram.getQuantile(0.9));
    assertEquals(9L, mergedHistogram.getQuantile(0.99));
  }

  @Test
  public void testMergedDifferentHistogram() {
    int n = 10;
    ApproximateHistogram histograms[] = new ApproximateHistogram[n];
    for (int i = 0; i < n; i++) {
      ApproximateHistogram h = new ApproximateHistogram(Amount.of(2L + 4*i, Data.KB));
      h.add(i);
      histograms[i] = h;
    }

    Histogram mergedHistogram = ApproximateHistogram.merge(histograms);
    assertEquals(0L, mergedHistogram.getQuantile(0.0));
    assertEquals(1L, mergedHistogram.getQuantile(0.1));
    assertEquals(5L, mergedHistogram.getQuantile(0.5));
    assertEquals(9L, mergedHistogram.getQuantile(0.9));
    assertEquals(9L, mergedHistogram.getQuantile(0.99));
  }

  @Test
  public void testMergedBigHistogram() {
    int n = 10;
    int m = 5000;
    ApproximateHistogram histograms[] = new ApproximateHistogram[n];
    int x = 0;
    for (int i = 0; i < n; i++) {
      ApproximateHistogram h = new ApproximateHistogram();
      while(x < m * (i + 1)) {
        h.add(x);
        x += 1;
      }
      histograms[i] = h;
    }
    long sum = m * n;

    double maxError = ApproximateHistogram.DEFAULT_PRECISION.getEpsilon() *
        ApproximateHistogram.DEFAULT_PRECISION.getN();
    Histogram mergedHistogram = ApproximateHistogram.merge(histograms);
    for (int i = 1; i < 10; i++) {
      double q = i / 10.0;
      double expected = q * sum;
      assertEquals(expected, mergedHistogram.getQuantile(q), maxError);
    }
  }
}
