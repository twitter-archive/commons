package com.twitter.common.stats;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import com.twitter.common.junit.annotations.TestParallel;
import com.twitter.common.objectsize.ObjectSizeCalculator;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestParallel
public class ApproximateHistogramTest {
  final int b = 10;
  final int h = 3;

  @Test
  public void testCollapse() {
    ApproximateHistogram hist = new ApproximateHistogram();
    long[] buf1 = {2,5,7};
    long[] buf2 = {3,8,9};
    long[] expected = {3,7,9};
    long[] result = new long[3];
    // [2,5,7] weight 2 and [3,8,9] weight 3
    // weight x array + concat = [2,2,5,5,7,7,3,3,3,8,8,8,9,9,9]
    // sort = [2,2,3,3,3,5,5,7,7,8,8,8,9,9,9]
    // select every nth elems = [3,7,9]  (n = sum weight / 2, ie. 5/3 = 2)
    // [2,2,3,3,3,5,5,7,7,8,8,8,9,9,9]
    //  . . ^ . . . . ^ . . . . ^ . .
    //  [-------] [-------] [-------] we make 3 packets of 5 elements and take the middle

    hist.collapse(buf1, 2, buf2, 3, result);
    assertArrayEquals(result, expected);

    long[] buf3 = {2, 5, 7, 9};
    long[] buf4 = {3, 8, 9, 12};
    long[] expected2 = {3, 7, 9, 12};
    long[] result2 = new long[4];
    hist.collapse(buf3, 2, buf4, 2, result2);
    assertArrayEquals(expected2, result2);
  }

  @Test
  public void testRecCollapse() {
    long[] empty = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    long[] full = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    ApproximateHistogram hist = new ApproximateHistogram(b, h);
    assertArrayEquals(empty, hist.buffer[0]);
    assertArrayEquals(empty, hist.buffer[1]);

    addToHist(hist, b);
    assertArrayEquals(full, hist.buffer[0]);
    assertArrayEquals(empty, hist.buffer[1]);

    addToHist(hist, b);
    assertArrayEquals(full, hist.buffer[0]);
    assertArrayEquals(full, hist.buffer[1]);

    hist.add(1);
    assertEquals(2, hist.currentTop);
    // Buffers are not cleared so we can't check that!
    assertArrayEquals(full, hist.buffer[2]);

    addToHist(hist, 2*b);
    assertEquals(3, hist.currentTop);
    assertArrayEquals(full, hist.buffer[3]);
  }

  @Test
  public void testReachingMaxDepth() {
    ApproximateHistogram hist = new ApproximateHistogram(b, h);

    addToHist(hist, 8 * b);
    assertEquals(3, hist.currentTop);

    hist.add(1);
    assertEquals(3, hist.currentTop);
  }

  @Test
  public void testMem() {
    for (int b = 10; b < 100; b += 10) {
      for (int h = 4; h < 16; h += 4) {
        ApproximateHistogram hist = new ApproximateHistogram(b, h);
        long actualSize = ObjectSizeCalculator.getObjectSize(hist);
        long estimatedSize = ApproximateHistogram.memoryUsage(b, h);
        assertEquals(actualSize, estimatedSize);
      }
    }
  }

  @Test
  public void testMemConstraint() {
    ImmutableList.Builder<Amount<Long, Data>> builder = ImmutableList.builder();
    builder.add(Amount.of(1L, Data.KB));
    builder.add(Amount.of(4L, Data.KB));
    builder.add(Amount.of(8L, Data.KB));
    builder.add(Amount.of(16L, Data.KB));
    builder.add(Amount.of(32L, Data.KB));
    builder.add(Amount.of(64L, Data.KB));
    builder.add(Amount.of(256L, Data.KB));
    builder.add(Amount.of(1L, Data.MB));
    builder.add(Amount.of(16L, Data.MB));
    builder.add(Amount.of(32L, Data.MB));
    List<Amount<Long, Data>> sizes = builder.build();

    for (Amount<Long, Data> maxSize: sizes) {
      ApproximateHistogram hist = new ApproximateHistogram(maxSize);
      long size = ObjectSizeCalculator.getObjectSize(hist);
      assertTrue(size < maxSize.as(Data.BYTES));
    }
  }

  @Test
  public void testLowMemoryPrecision() {
    double e = ApproximateHistogram.DEFAULT_PRECISION.getEpsilon();
    int n = ApproximateHistogram.DEFAULT_PRECISION.getN();
    int defaultDepth = ApproximateHistogram.computeDepth(e, n);
    int defaultBufferSize = ApproximateHistogram.computeBufferSize(defaultDepth, n);

    ApproximateHistogram hist = new ApproximateHistogram(Amount.of(1L, Data.KB));
    int depth = hist.buffer.length - 1;
    int bufferSize = hist.buffer[0].length;

    assertTrue(depth > defaultDepth);
    assertTrue(bufferSize < defaultBufferSize);
  }

  @Test
  public void testHighMemoryPrecision() {
    double e = ApproximateHistogram.DEFAULT_PRECISION.getEpsilon();
    int n = ApproximateHistogram.DEFAULT_PRECISION.getN();
    int defaultDepth = ApproximateHistogram.computeDepth(e, n);
    int defaultBufferSize = ApproximateHistogram.computeBufferSize(defaultDepth, n);

    ApproximateHistogram hist = new ApproximateHistogram(Amount.of(1L, Data.MB));
    int depth = hist.buffer.length - 1;
    int bufferSize = hist.buffer[0].length;

    assertTrue(depth < defaultDepth);
    assertTrue(bufferSize > defaultBufferSize);
  }

  private void initIndexArray(ApproximateHistogram hist, int b) {
    Arrays.fill(hist.indices, b - 1);
    int buf0Size = Math.min(b, hist.leafCount);
    int buf1Size = Math.max(0, hist.leafCount - buf0Size);
    hist.indices[0] = buf0Size - 1;
    hist.indices[1] = buf1Size - 1;
  }

  private long getBiggest(ApproximateHistogram hist) {
    int j = hist.biggest(hist.indices);
    int idx = hist.indices[j];
    hist.indices[j] -= 1;
    return hist.buffer[j][idx];
  }

  public void testBiggestIndexFinder() {
    ApproximateHistogram hist = new ApproximateHistogram();
    hist.init(b, h);
    int n = 3;
    for (int i=1; i <= n; i++) {
      hist.add(i);
    }

    initIndexArray(hist, b);
    for (int i=1; i <= n; i++) {
      assertEquals(n - i + 1, getBiggest(hist));
    }

    n = 2 * b;
    for (int i=4; i <= n; i++) {
      hist.add(i);
    }

    initIndexArray(hist, b);
    for (int i=1; i <= n; i++) {
      assertEquals(n - i + 1, getBiggest(hist));
    }

    hist.add(2*b + 1);
    n += 1;

    initIndexArray(hist, b);
    assertEquals(n, getBiggest(hist));

    for (int i=2; i <= n; i += 2) {
      assertEquals(n - i + 1, getBiggest(hist));
    }
  }

  @Test
  public void testIsBufferEmpty() {
    ApproximateHistogram hist = new ApproximateHistogram(b, h);

    for (int i=0; i < 3*b; i++) {
      hist.add(i);
    }
    assertEquals(false, hist.isBufferEmpty(2));
    assertEquals(true, hist.isBufferEmpty(3));

    for (int i=0; i < 2*b; i++) {
      hist.add(i);
    }
    assertEquals(true, hist.isBufferEmpty(2));
    assertEquals(false, hist.isBufferEmpty(3));
  }

  public void testHistogramWithNegative() {
    ApproximateHistogram hist = new ApproximateHistogram();
    hist.add(-1L);
    assertEquals(-1L, hist.getQuantile(0.0));
    assertEquals(-1L, hist.getQuantile(1.0));
  }

  public void testQueryZerothQuantile() {
    // Tests that querying the zeroth quantile does not throw an exception
    ApproximateHistogram hist = new ApproximateHistogram();
    hist.init(b, h);
    addToHist(hist, 10);
    assertEquals(1L, hist.getQuantile(0.0));
  }

  public void testSmallDataCase() {
    // Tests that querying the zeroth quantile does not throw an exception
    ApproximateHistogram hist = new ApproximateHistogram();
    hist.init(b, h);
    addToHist(hist, 1);
    assertEquals(1L, hist.getQuantile(0.5));
  }

  private void addToHist(ApproximateHistogram hist, int n) {
    for (int i=0; i<n ; i++) {
      hist.add(1);
    }
  }
}
