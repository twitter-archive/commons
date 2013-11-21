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

package com.twitter.common.stats.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.twitter.common.stats.Histogram;
import com.twitter.common.stats.Histograms;

public class RealHistogram implements Histogram {
  private final List<Long> buffer = new ArrayList<Long>();

  @Override public void add(long x) {
    buffer.add(x);
  }

  @Override public void clear() {
    buffer.clear();
  }

  @Override public long getQuantile(double quantile) {
    Collections.sort(buffer);
    return buffer.get((int) (quantile * buffer.size()));
  }

  @Override public long[] getQuantiles(double[] quantiles) {
    return Histograms.extractQuantiles(this, quantiles);
  }
}
