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

import com.google.common.base.Preconditions;

public class PrintableHistogram {
  private double[] bucketBoundaries;
  private int[] bucketCounts;
  private int totalCount = 0;

  /**
   * Creates a histogram with the given bucket boundaries.  The boundaries
   * 0 and infinity are implicitly added.
   *
   * @param buckets Boundaries for histogram buckets.
   */
  public PrintableHistogram(double ... buckets) {
    Preconditions.checkState(buckets[0] != 0);

    bucketBoundaries = new double[buckets.length + 2];
    bucketBoundaries[0] = 0;
    bucketCounts = new int[buckets.length + 2];
    for (int i = 0; i < buckets.length; i++) {
      if (i > 0) {
        Preconditions.checkState(buckets[i] > buckets[i - 1],
            "Bucket %f must be greater than %f.", buckets[i], buckets[i - 1]);
      }
      bucketCounts[i] = 0;
      bucketBoundaries[i + 1] = buckets[i];
    }

    bucketBoundaries[bucketBoundaries.length - 1] = Integer.MAX_VALUE;
  }

  public void addValue(double value) {
    addValue(value, 1);
  }

  public void addValue(double value, int count) {
    Preconditions.checkState(value >= 0);
    Preconditions.checkState(count >= 0);
    Preconditions.checkState(bucketBoundaries.length > 1);
    int bucketId = -1;
    for (double boundary : bucketBoundaries) {
      if (value <= boundary) {
        break;
      }
      bucketId++;
    }

    bucketId = Math.max(0, bucketId);
    bucketId = Math.min(bucketCounts.length - 1, bucketId);
    bucketCounts[bucketId] += count;
    totalCount += count;
  }

  public double getBucketRatio(int bucketId) {
    Preconditions.checkState(bucketId >= 0);
    Preconditions.checkState(bucketId < bucketCounts.length);
    return (double) bucketCounts[bucketId] / totalCount;
  }

  public String toString() {
    StringBuilder display = new StringBuilder();
    display.append("Histogram: ");
    for (int bucketId = 0; bucketId < bucketCounts.length - 1; bucketId++) {
      display.append(String.format("\n(%g - %g]\n\t",
          bucketBoundaries[bucketId], bucketBoundaries[bucketId + 1]));
      for (int i = 0; i < getBucketRatio(bucketId) * 100; i++) {
        display.append('#');
      }
      display.append(
          String.format(" %.2g%% (%d)", getBucketRatio(bucketId) * 100, bucketCounts[bucketId]));
    }

    return display.toString();
  }
}
