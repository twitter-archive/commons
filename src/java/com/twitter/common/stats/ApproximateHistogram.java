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

import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;

/**
 * <p>
 * Implements Histogram structure for computing approximate quantiles.
 * The implementation is based on the following paper:
 *
 * <pre>
 * [MP80]  Munro & Paterson, "Selection and Sorting with Limited Storage",
 *         Theoretical Computer Science, Vol 12, p 315-323, 1980.
 * </pre>
 * </p>
 * <p>
 * You could read a detailed description of the same algorithm here:
 *
 * <pre>
 * [MRL98] Manku, Rajagopalan & Lindsay, "Approximate Medians and other
 *         Quantiles in One Pass and with Limited Memory", Proc. 1998 ACM
 *         SIGMOD, Vol 27, No 2, p 426-435, June 1998.
 * </pre>
 * </p>
 * <p>
 * There's a good explanation of the algorithm in the Sawzall source code
 * See: http://szl.googlecode.com/svn-history/r36/trunk/src/emitters/szlquantile.cc
 * </p>
 * Here's a schema of the tree:
 * <pre>
 *      [4]     level 3, weight=rootWeight=8
 *       |
 *      [3]     level 2, weight=4
 *       |
 *      [2]     level 1, weight=2
 *     /   \
 *   [0]   [1]  level 0, weight=1
 * </pre>
 * <p>
 * {@code [i]} represents {@code buffer[i]}
 * The depth of the tree is limited to a maximum value
 * Every buffer has the same size
 * </p>
 * <p>
 * We add element in {@code [0]} or {@code [1]}.
 * When {@code [0]} and {@code [1]} are full, we collapse them, it generates a temporary buffer
 * of weight 2, if {@code [2]} is empty, we put the collapsed buffer into {@code [2]} otherwise
 * we collapse {@code [2]} with the temporary buffer and put it in {@code [3]} if it's empty and
 * so on...
 * </p>
 */
public final class ApproximateHistogram implements Histogram {
  @VisibleForTesting
  public static final Precision DEFAULT_PRECISION = new Precision(0.02, 100 * 1000);
  @VisibleForTesting
  public static final Amount<Long, Data> DEFAULT_MAX_MEMORY = Amount.of(12L, Data.KB);
  @VisibleForTesting static final long ELEM_SIZE = 8; // sizeof long

  // See above
  @VisibleForTesting long[][] buffer;
  @VisibleForTesting long count = 0L;
  @VisibleForTesting int leafCount = 0; // number of elements in the bottom two leaves
  @VisibleForTesting int currentTop = 1;
  @VisibleForTesting int[] indices; // member for optimization reason
  private boolean leavesSorted = true;
  private int rootWeight = 1;
  private long[][] bufferPool; // pool of 2 buffers (used for merging)
  private int bufferSize;
  private int maxDepth;

  /**
   * Private init method that is called only by constructors.
   * All allocations are done in this method.
   *
   * @param bufSize size of each buffer
   * @param depth maximum depth of the tree of buffers
   */
  @VisibleForTesting
  void init(int bufSize, int depth) {
    bufferSize = bufSize;
    maxDepth = depth;
    bufferPool = new long[2][bufferSize];
    indices = new int[depth + 1];
    buffer = new long[depth + 1][bufferSize];
    // only allocate the first 2 buffers, lazily allocate the others.
    allocate(0);
    allocate(1);
    Arrays.fill(buffer, 2, buffer.length, null);
    clear();
  }

  @VisibleForTesting
  ApproximateHistogram(int bufSize, int depth) {
    init(bufSize, depth);
  }

  /**
   * Constructor with precision constraint, it will allocated as much memory as require to match
   * this precision constraint.
   * @param precision the requested precision
   */
  public ApproximateHistogram(Precision precision) {
    Preconditions.checkNotNull(precision);
    int depth = computeDepth(precision.getEpsilon(), precision.getN());
    int bufSize = computeBufferSize(depth, precision.getN());
    init(bufSize, depth);
  }

  /**
   * Constructor with memory constraint, it will find the best possible precision that satisfied
   * the memory constraint.
   * @param maxMemory the maximum amount of memory that the instance will take
   */
  public ApproximateHistogram(Amount<Long, Data> maxMemory, int expectedSize) {
    Preconditions.checkNotNull(maxMemory);
    Preconditions.checkArgument(1024 <= maxMemory.as(Data.BYTES),
        "at least 1KB is required for an Histogram");

    double epsilon = DEFAULT_PRECISION.getEpsilon();
    int n = expectedSize;
    int depth = computeDepth(epsilon, n);
    int bufSize = computeBufferSize(depth, n);
    long maxBytes = maxMemory.as(Data.BYTES);

    // Increase precision if the maxMemory allow it, otherwise reduce precision. (by 5% steps)
    boolean tooMuchMem = memoryUsage(bufSize, depth) > maxBytes;
    double multiplier = tooMuchMem ? 1.05 : 0.95;
    while((maxBytes < memoryUsage(bufSize, depth)) == tooMuchMem) {
      epsilon *= multiplier;
      if (epsilon < 0.00001) {
        // for very high memory constraint increase N as well
        n *= 10;
        epsilon = DEFAULT_PRECISION.getEpsilon();
      }
      depth = computeDepth(epsilon, n);
      bufSize = computeBufferSize(depth, n);
    }
    if (!tooMuchMem) {
      // It's ok to consume less memory than the constraint
      // but we never have to consume more!
      depth = computeDepth(epsilon / multiplier, n);
      bufSize = computeBufferSize(depth, n);
    }

    init(bufSize, depth);
  }

  /**
   * Constructor with memory constraint.
   * @see #ApproximateHistogram(Amount, int)
   */
  public ApproximateHistogram(Amount<Long, Data> maxMemory) {
    this(maxMemory, DEFAULT_PRECISION.getN());
  }

  /**
   * Default Constructor.
   * @see #ApproximateHistogram(Amount)
   */
  public ApproximateHistogram() {
    this(DEFAULT_MAX_MEMORY);
  }

  @Override
  public synchronized void add(long x) {
    // if the leaves of the tree are full, "collapse" recursively the tree
    if (leafCount == 2 * bufferSize) {
      Arrays.sort(buffer[0]);
      Arrays.sort(buffer[1]);
      recCollapse(buffer[0], 1);
      leafCount = 0;
    }

    // Now we're sure there is space for adding x
    if (leafCount < bufferSize) {
      buffer[0][leafCount] = x;
    } else {
      buffer[1][leafCount - bufferSize] = x;
    }
    leafCount++;
    count++;
    leavesSorted = (leafCount == 1);
  }

  @Override
  public synchronized long getQuantile(double q) {
    Preconditions.checkArgument(0.0 <= q && q <= 1.0,
        "quantile must be in the range 0.0 to 1.0 inclusive");
    if (count == 0) {
      return 0L;
    }

    // the two leaves are the only buffer that can be partially filled
    int buf0Size = Math.min(bufferSize, leafCount);
    int buf1Size = Math.max(0, leafCount - buf0Size);
    long sum = 0;
    long target = (long) Math.ceil(count * (1.0 - q));
    int i;

    if (! leavesSorted) {
      Arrays.sort(buffer[0], 0, buf0Size);
      Arrays.sort(buffer[1], 0, buf1Size);
      leavesSorted = true;
    }
    Arrays.fill(indices, bufferSize - 1);
    indices[0] = buf0Size - 1;
    indices[1] = buf1Size - 1;

    do {
      i = biggest(indices);
      indices[i]--;
      sum += weight(i);
    } while (sum < target);
    return buffer[i][indices[i] + 1];
  }

  @Override
  public synchronized long[] getQuantiles(double[] quantiles) {
    return Histograms.extractQuantiles(this, quantiles);
  }

  @Override
  public synchronized void clear() {
    count = 0L;
    leafCount = 0;
    currentTop = 1;
    rootWeight = 1;
    leavesSorted = true;
  }

  /**
   * MergedHistogram is a Wrapper on top of multiple histograms, it gives a view of all the
   * underlying histograms as it was just one.
   * Note: Should only be used for querying the underlying histograms.
   */
  private static class MergedHistogram implements Histogram {
    private final ApproximateHistogram[] histograms;

    private MergedHistogram(ApproximateHistogram[] histograms) {
      this.histograms = histograms;
    }

    @Override
    public void add(long x) {
      /* Ignore, Shouldn't be used */
      assert(false);
    }

    @Override
    public void clear() {
      /* Ignore, Shouldn't be used */
      assert(false);
    }

    @Override
    public long getQuantile(double quantile) {
      Preconditions.checkArgument(0.0 <= quantile && quantile <= 1.0,
          "quantile must be in the range 0.0 to 1.0 inclusive");

      long count = initIndices();
      if (count == 0) {
        return 0L;
      }

      long sum = 0;
      long target = (long) Math.ceil(count * (1.0 - quantile));
      int iHist = -1;
      int iBiggest = -1;
      do {
        long biggest = Long.MIN_VALUE;
        for (int i = 0; i < histograms.length; i++) {
          ApproximateHistogram hist = histograms[i];
          int indexBiggest = hist.biggest(hist.indices);
          if (indexBiggest >= 0) {
            long value = hist.buffer[indexBiggest][hist.indices[indexBiggest]];
            if (iBiggest == -1 || biggest <= value) {
              iBiggest = indexBiggest;
              biggest = value;
              iHist = i;
            }
          }
        }
        histograms[iHist].indices[iBiggest]--;
        sum += histograms[iHist].weight(iBiggest);
      } while (sum < target);

      ApproximateHistogram hist = histograms[iHist];
      int i = hist.indices[iBiggest];
      return hist.buffer[iBiggest][i + 1];
    }

    @Override
    public synchronized long[] getQuantiles(double[] quantiles) {
      return Histograms.extractQuantiles(this, quantiles);
    }

    /**
     * Initialize the indices array for each Histogram and return the global count.
     */
    private long initIndices() {
      long count = 0L;
      for (int i = 0; i < histograms.length; i++) {
        ApproximateHistogram h = histograms[i];
        int[] indices = h.indices;
        count += h.count;
        int buf0Size = Math.min(h.bufferSize, h.leafCount);
        int buf1Size = Math.max(0, h.leafCount - buf0Size);

        if (! h.leavesSorted) {
          Arrays.sort(h.buffer[0], 0, buf0Size);
          Arrays.sort(h.buffer[1], 0, buf1Size);
          h.leavesSorted = true;
        }
        Arrays.fill(indices, h.bufferSize - 1);
        indices[0] = buf0Size - 1;
        indices[1] = buf1Size - 1;
      }
      return count;
    }
  }

  /**
   * Return a MergedHistogram
   * @param histograms array of histograms to merged together
   * @return a new Histogram
   */
  public static Histogram merge(ApproximateHistogram[] histograms) {
    return new MergedHistogram(histograms);
  }

  /**
   * We compute the "smallest possible b" satisfying two inequalities:
   *    1)   (b - 2) * (2 ^ (b - 2)) + 0.5 <= epsilon * N
   *    2)   k * (2 ^ (b - 1)) >= N
   *
   * For an explanation of these inequalities, please read the Munro-Paterson or
   * the Manku-Rajagopalan-Linday papers.
   */
  @VisibleForTesting static int computeDepth(double epsilon, long n) {
    int b = 2;
    while ((b - 2) * (1L << (b - 2)) + 0.5 <= epsilon * n) {
      b += 1;
    }
    return b;
  }

  @VisibleForTesting static int computeBufferSize(int depth, long n) {
    return (int) (n / (1L << (depth - 1)));
  }

  /**
   * Return an estimation of the memory used by an instance.
   * The size is due to:
   * - a fix cost (76 bytes) for the class + fields
   * - bufferPool: 16 + 2 * (16 + bufferSize * ELEM_SIZE)
   * - indices: 16 + sizeof(Integer) * (depth + 1)
   * - buffer: 16 + (depth + 1) * (16 + bufferSize * ELEM_SIZE)
   *
   * Note: This method is tested with unit test, it will break if you had new fields.
   * @param bufferSize the size of a buffer
   * @param depth the depth of the tree of buffer (depth + 1 buffers)
   */
  @VisibleForTesting
  static long memoryUsage(int bufferSize, int depth) {
    return 176 + (24 * depth) + (bufferSize * ELEM_SIZE * (depth + 3));
  }

  /**
   * Return the level of the biggest element (using the indices array 'ids'
   * to track which elements have been already returned). Every buffer has
   * already been sorted at this point.
   * @return the level of the biggest element or -1 if no element has been found
   */
  @VisibleForTesting
  int biggest(final int[] ids) {
    long biggest = Long.MIN_VALUE;
    final int id0 = ids[0], id1 = ids[1];
    int iBiggest = -1;

    if (0 < leafCount && 0 <= id0) {
      biggest = buffer[0][id0];
      iBiggest = 0;
    }
    if (bufferSize < leafCount && 0 <= id1) {
      long x = buffer[1][id1];
      if (x > biggest) {
        biggest = x;
        iBiggest = 1;
      }
    }
    for (int i = 2; i < currentTop + 1; i++) {
      if (!isBufferEmpty(i) && 0 <= ids[i]) {
        long x = buffer[i][ids[i]];
        if (x > biggest) {
          biggest = x;
          iBiggest = i;
        }
      }
    }
    return iBiggest;
  }


  /**
   * Based on the number of elements inserted we can easily know if a buffer
   * is empty or not
   */
  @VisibleForTesting
  boolean isBufferEmpty(int level) {
    if (level == currentTop) {
      return false; // root buffer (if present) is always full
    } else {
      long levelWeight = 1 << (level - 1);
      return (((count - leafCount) / bufferSize) & levelWeight) == 0;
    }
  }

  /**
   * Return the weight of the level ie. 2^(i-1) except for the two tree
   * leaves (weight=1) and for the root
   */
  private int weight(int level) {
    if (level == 0) {
      return 1;
    } else if (level == maxDepth) {
      return rootWeight;
    } else {
      return 1 << (level - 1);
    }
  }

  private void allocate(int i) {
    if (buffer[i] == null) {
      buffer[i] = new long[bufferSize];
    }
  }

  /**
   * Recursively collapse the buffers of the tree.
   * Upper buffers will be allocated on first access in this method.
   */
  private void recCollapse(long[] buf, int level) {
    // if we reach the root, we can't add more buffer
    if (level == maxDepth) {
      // weight() return the weight of the root, in that case we need the
      // weight of merge result
      int mergeWeight = 1 << (level - 1);
      int idx = level % 2;
      long[] merged = bufferPool[idx];
      long[] tmp = buffer[level];
      collapse(buf, mergeWeight, buffer[level], rootWeight, merged);
      buffer[level] = merged;
      bufferPool[idx] = tmp;
      rootWeight += mergeWeight;
    } else {
      allocate(level + 1); // lazy allocation (if needed)
      if (level == currentTop) {
        // if we reach the top, add a new buffer
        collapse1(buf, buffer[level], buffer[level + 1]);
        currentTop += 1;
        rootWeight *= 2;
      } else if (isBufferEmpty(level + 1)) {
        // if the upper buffer is empty, use it
        collapse1(buf, buffer[level], buffer[level + 1]);
      } else {
        // it the upper buffer isn't empty, collapse with it
        long[] merged = bufferPool[level % 2];
        collapse1(buf, buffer[level], merged);
        recCollapse(merged, level + 1);
      }
    }
  }

  /**
   * collapse two sorted Arrays of different weight
   * ex: [2,5,7] weight 2 and [3,8,9] weight 3
   *     weight x array + concat = [2,2,5,5,7,7,3,3,3,8,8,8,9,9,9]
   *     sort = [2,2,3,3,3,5,5,7,7,8,8,8,9,9,9]
   *     select every nth elems = [3,7,9]  (n = sum weight / 2)
   */
  @VisibleForTesting
  static void collapse(
    long[] left,
    int leftWeight,
    long[] right,
    int rightWeight,
    long[] output) {

    int totalWeight = leftWeight + rightWeight;
    int halfTotalWeight = (totalWeight / 2) - 1;
    int i = 0, j = 0, k = 0, cnt = 0;

    int weight;
    long smallest;

    while (i < left.length || j < right.length) {
      if (i < left.length && (j == right.length || left[i] < right[j])) {
        smallest = left[i];
        weight = leftWeight;
        i++;
      } else {
        smallest = right[j];
        weight = rightWeight;
        j++;
      }

      int cur = (cnt + halfTotalWeight) / totalWeight;
      cnt += weight;
      int next = (cnt + halfTotalWeight) / totalWeight;

      for(; cur < next; cur++) {
        output[k] = smallest;
        k++;
      }
    }
  }

/**
 * Optimized version of collapse for collapsing two array of the same weight
 * (which is what we want most of the time)
 */
  private static void collapse1(
    long[] left,
    long[] right,
    long[] output) {

    int i = 0, j = 0, k = 0, cnt = 0;
    long smallest;

    while (i < left.length || j < right.length) {
      if (i < left.length && (j == right.length || left[i] < right[j])) {
        smallest = left[i];
        i++;
      } else {
        smallest = right[j];
        j++;
      }
      if (cnt % 2 == 1) {
        output[k] = smallest;
        k++;
      }
      cnt++;
    }
  }
}
