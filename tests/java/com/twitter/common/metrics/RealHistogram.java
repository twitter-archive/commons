package com.twitter.common.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.twitter.common.stats.Histograms;

class RealHistogram implements com.twitter.common.stats.Histogram {
  private List<Long> buffer = new ArrayList<Long>();

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
