package com.twitter.common.stats;

import java.util.Arrays;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;

/**
 * Implements Histogram structure for computing approximate quantiles.
 * The implementation is based on the following paper:
 *
 * [MP80]  Munro & Paterson, "Selection and Sorting with Limited Storage",
 *         Theoretical Computer Science, Vol 12, p 315-323, 1980.
 *
 * You could read a detailed description of the same algorithm here:
 *
 * [MRL98] Manku, Rajagopalan & Lindsay, "Approximate Medians and other
 *         Quantiles in One Pass and with Limited Memory", Proc. 1998 ACM
 *         SIGMOD, Vol 27, No 2, p 426-435, June 1998.
 *
 * There's a good explanation of the algorithm in the Sawzall source code
 * See: http://szl.googlecode.com/svn-history/r36/trunk/src/emitters/szlquantile.cc
 *
 * Here's a schema of the tree:
 *
 *      [4]     level 3, weight=rootWeight=8
 *       |
 *      [3]     level 2, weight=4
 *       |
 *      [2]     level 1, weight=2
 *     /   \
 *   [0]   [1]  level 0, weight=1
 *
 * [i] represent buffer[i]
 * The depth of the tree is limited to a maximum value
 * Every buffer has the same size
 *
 * We add element in [0] or [1].
 * When [0] and [1] are full, we collapse them, it generates a temporary buffer of weight 2,
 * if [2] is empty, we put the collapsed buffer into [2] otherwise we collapse [2] with
 * the temporary buffer and put it in [3] if it's empty and so on...
 */
public final class ApproximateHistogram implements Histogram {
  private static final Logger LOG = Logger.getLogger(Histogram.class.getName());
  private static final Precision DEFAULT_PRECISION = new Precision(0.0001, 1000 * 1000);
  private static final Amount<Long, Data> DEFAULT_MAX_MEMORY = Amount.of(4L, Data.KB);
  private static final int ELEMSIZE = 8; // sizeof long

  // See above
  @VisibleForTesting long[][] buffer;
  @VisibleForTesting long count = 0L;
  @VisibleForTesting int leafCount = 0; // number of elements in the bottom two leaves
  @VisibleForTesting int currentTop = 1;
  @VisibleForTesting int[] indices; // member for optimization reason
  private int rootWeight = 1;
  private long[][] bufferPool; // pool of buffers used for merging
  private int bufferSize;
  private int maxDepth;

  /**
   * Private init method that is called only by constructors
   *
   * @param bufSize size of each buffer
   * @param b maximum depth of the tree of buffers
   */
  @VisibleForTesting
  void init(int bufSize, int maxDepth) {
    this.bufferSize = bufSize;
    this.maxDepth = maxDepth;

    clear();
  }

  /**
   * Constructor without memory constraint
   * @param precision the requested precision
   */
  public ApproximateHistogram(Precision precision) {
    Preconditions.checkNotNull(precision);
    int b = computeB(precision.getEpsilon(), precision.getN());
    int bufSize = computeBufferSize(b, precision.getN());
    init(bufSize, b);
  }

  /**
   * Constructor without precision constraint
   * @param maxMemory the maximum memory that the instance will take
   */
  public ApproximateHistogram(Amount<Long, Data> maxMemory) {
    Preconditions.checkNotNull(maxMemory);
    int b = computeB(DEFAULT_PRECISION.getEpsilon(), DEFAULT_PRECISION.getN());
    int bufSize = computeBufferSize(b, DEFAULT_PRECISION.getN());
    int maxDepth = computeMaxDepth(maxMemory, bufSize);
    init(bufSize, maxDepth);
  }

  /**
   * Constructor with default arguments
   * @see #ApproximateHistogram(Amount<Long, Data>)
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
  }

  @Override
  public synchronized long[] getQuantiles(double[] qs) {
    long[] output = new long[qs.length];
    if (count == 0) {
      Arrays.fill(output, 0L);
      return output;
    }

    // the two leaves are the only buffer that can be partially filled
    int buf0Size = Math.min(bufferSize, leafCount);
    int buf1Size = Math.max(0, leafCount - buf0Size);
    long sum = 0;
    int i, id, io = 0;

    Arrays.sort(buffer[0], 0, buf0Size);
    Arrays.sort(buffer[1], 0, buf1Size);
    Arrays.fill(indices, 0);

    while (io < output.length) {
      i = smallest(buf0Size, buf1Size, indices);
      id = indices[i];
      indices[i]++;
      sum += weight(i);
      while (io < qs.length && (long) (qs[io] * count) <= sum) {
        output[io] = buffer[i][id];
        io++;
      }
    }
    return output;
  }

  @Override
  public synchronized void clear() {
    count = 0L;
    leafCount = 0;
    rootWeight = 1;
    bufferPool = new long[2][bufferSize];
    indices = new int[bufferSize];
    // All the buffers of the tree are allocated
    buffer = new long[maxDepth + 1][bufferSize];
    for (int i = 0; i < maxDepth; i++) {
      buffer[i] = new long[bufferSize];
    }
  }

  /**
   * We compute the "smallest possible k" satisfying two inequalities:
   *    1)   (b - 2) * (2 ^ (b - 2)) + 0.5 <= epsilon * N
   *    2)   k * (2 ^ (b - 1)) >= N
   *
   * For an explanation of these inequalities, please read the Munro-Paterson or
   * the Manku-Rajagopalan-Linday papers.
   */
  private int computeB(double epsilon, long n) {
    int b = 2;
    while ((b - 2) * (1L << (b - 2)) + 0.5 <= epsilon * n) {
      b += 1;
    }
    return b;
  }

  private int computeBufferSize(int b, long n) {
    return (int) (n / (0x1L << (b - 1)));
  }

  /**
   * Return the maximum depth of the graph to comply with the memory constraint
   * @param bufferSize the size of each buffer
   */
  private int computeMaxDepth(Amount<Long, Data> maxMemory, int bufferSize) {
    int bm = 0;
    long n = maxMemory.as(Data.BYTES) - 100 - (ELEMSIZE * bufferSize);
    if (n < 0) {
      bm = 2;
    } else {
      bm = (int) (n / (16 + ELEMSIZE * bufferSize));
    }
    if (bm < 2) {
      bm = 2;
    }
    return bm;
  }

  /**
   * Return the level of the smallest element (using the indices array 'ids'
   * to track which elements have been already returned). Every buffers has
   * already been sorted at this point.
   */
  @VisibleForTesting
  int smallest(final int buf0Size, final int buf1Size, final int[] ids) {
    long smallest = Long.MAX_VALUE, x = Long.MAX_VALUE;
    final int id0 = ids[0], id1 = ids[1];
    int iSmallest = 0;

    if (0 < leafCount && id0 < buf0Size) {
      smallest = buffer[0][id0];
    }
    if (bufferSize < leafCount && id1 < buf1Size) {
      x = buffer[1][id1];
      if (x < smallest) {
        smallest = x;
        iSmallest = 1;
      }
    }
    for (int i = 2; i < currentTop + 1; i++) {
      if (!isBufferEmpty(i) && ids[i] < bufferSize) {
        x = buffer[i][ids[i]];
      }
      if (x < smallest) {
        smallest = x;
        iSmallest = i;
      }
    }
    return iSmallest;
  }

  /**
   * Based on the number of elements inserted we can easily know if a buffer
   * is empty or not
   */
  private boolean isBufferEmpty(int level) {
    if (level == currentTop) {
      return false; // root buffer (if present) is always full
    } else {
      return (count / (bufferSize * weight(level))) % 2 == 1;
    }
  }

  /**
   * return the weight of the level ie. 2^(i-1) except for the two tree
   * leaves (weight=1) and for the root
   */
  private int weight(int level) {
    int w;
    if (level < 2) {
      w = 1;
    } else if (level == maxDepth) {
      w = rootWeight;
    } else {
      w = 1 << (level - 1);
    }
    return w;
  }

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
  void collapse(
    long[] left,
    int leftWeight,
    long[] right,
    int rightWeight,
    long[] output) {

    int totalWeight = leftWeight + rightWeight;
    int halfTotalWeight = (totalWeight / 2) - 1;
    int cnt = 0;
    int i = 0;
    int j = 0;
    int k = 0;

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
 * Optimized version of collapse for colapsing two array of the same weight
 * (which is what we want most of the time)
 */
  private void collapse1(
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
