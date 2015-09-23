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

package com.twitter.common.metrics.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.twitter.common.objectsize.ObjectSizeCalculator;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.stats.ApproximateHistogram;
import com.twitter.common.stats.Histogram;
import com.twitter.common.stats.testing.RealHistogram;

import static com.twitter.common.metrics.Histogram.DEFAULT_QUANTILES;

final class MetricsPrecisionDemo {

  private MetricsPrecisionDemo() { }

  private void testHistogram(Histogram h, RealHistogram real, List<Long> data) {
    initializeWith(h, data);
    initializeWith(real, data);
    long histSize = ObjectSizeCalculator.getObjectSize(h);
    long realSize = ObjectSizeCalculator.getObjectSize(real);
    System.out.println("Real Histogram size: " + realSize + "B, Approx histogram size: "
        + histSize + "B");

    double[] quantiles = DEFAULT_QUANTILES;
    for (int i = 0; i < quantiles.length; i++) {
      double q = quantiles[i];
      long realQuantile = real.getQuantile(q);
      long metricsQuantile = h.getQuantile(q);


      double delta = 1000 * Math.abs(metricsQuantile - realQuantile) / realQuantile;
      System.out.println("Quantile: " + q + "\tReal: " + realQuantile
          + " \tApprox: " + metricsQuantile
          + " (∂: " + (delta / 10.0) + "%)");
    }
  }

  private Histogram initializeWith(Histogram h, List<Long> data) {
    for (Long x: data) {
      h.add(x);
    }
    return h;
  }

  private List<Long> getRandomData(int size, long min, long max) {
    Random rnd = new Random(1);
    List<Long> data = new ArrayList<Long>();
    for (int i = 0; i < size; i++) {
      data.add(min + (rnd.nextLong() % (max - min)));
    }
    return data;
  }

  private List<Long> getRealData(int n) {
    List<Long> data = new ArrayList<Long>();

    ClassLoader cl = getClass().getClassLoader();
    InputStreamReader input =
        new InputStreamReader(cl.getResourceAsStream("resources/real_latencies.data"));

    BufferedReader reader = new BufferedReader(input);
    try {
      String line = reader.readLine();
      int i = 0;
      while (data.size() < n) {
        if (line == null || "".equals(line)) {
          data.add(data.get(i++));
        } else {
          long x = (long) (1000 * Double.parseDouble(line));
          data.add(x);
          line = reader.readLine();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return data;
  }

  public static void main(String[] args) {
    MetricsPrecisionDemo demo = new MetricsPrecisionDemo();

    for (int n = 100; n <= 1000 * 1000; n *= 10) {
      for (long mem = 4; mem < 16; mem += 2) {
        Amount<Long, Data> maxMemory = Amount.of(mem, Data.KB);

        System.out.println("\nnumber of elements:" + n + "  maxMemory:" + maxMemory);
        System.out.println("Real data");
        demo.testHistogram(new ApproximateHistogram(maxMemory), new RealHistogram(),
            demo.getRealData(n));
        System.out.println("Random data");
        demo.testHistogram(new ApproximateHistogram(maxMemory), new RealHistogram(),
            demo.getRandomData(n, 100000, 200000));
      }
    }
  }
}
