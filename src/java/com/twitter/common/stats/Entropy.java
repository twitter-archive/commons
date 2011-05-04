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

/**
 * Calculate the entropy of a discrete distribution of <T>.
 *
 * @author Gilad Mishne
 */
public class Entropy<T> {
  private final CounterMap<T> counts = new CounterMap<T>();
  private int total = 0;

  private static double Log2(double n) {
    return Math.log(n) / Math.log(2);
  }

  public Entropy(Iterable<T> elements) {
    Preconditions.checkNotNull(elements);
    for (T element : elements) {
      counts.incrementAndGet(element);
      total++;
    }
  }

  public double entropy() {
    double entropy = 0;
    for (int count: counts.values()) {
      double prob = (double) count / total;
      entropy -= prob * Log2(prob);
    }
    return entropy;
  }

  public double perplexity() {
    return Math.pow(2, entropy());
  }
}
