package com.twitter.common.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

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
  private static final int elemSize = 8; // sizeof long

  // See above
  private List<List<Long>> buffer;
  private int bufferSize;
  private int maxDepth;
  private int rootWeight = 1;
  private long count = 0L;

  /**
   * Private init method that is called only by constructors
   *
   * @param bufSize size of each buffer
   * @param b maximum depth of the tree of buffers
   */
  private void init(int bufSize, int maxDepth) {
    this.bufferSize = bufSize;
    this.maxDepth = maxDepth;

    buffer = new ArrayList<List<Long>>(maxDepth);
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
    count += 1;

    // if the leaves of the tree are full, "collapse" recursively the tree
    if (buffer.get(0).size() == bufferSize && buffer.get(1).size() == bufferSize) {
      Collections.sort(buffer.get(0));
      Collections.sort(buffer.get(1));
      recCollapse(buffer.get(0), 1);
      buffer.get(0).clear();
    }

    // Now we're sure there is space for adding x
    int i = 1;
    if (buffer.get(0).size() < bufferSize) {
      i = 0;
    }
    buffer.get(i).add(x);
  }

  @Override
  public synchronized long[] getQuantiles(double[] qs) {
    long[] output = new long[qs.length];
    if (count == 0) {
      Arrays.fill(output, 0L);
      return output;
    }

    int io = 0;
    long qsum = 0;
    long[] qss = quantilesSums(qs);
    int iq = 0;

    Collections.sort(buffer.get(0));
    Collections.sort(buffer.get(1));

    int[] indices = new int[buffer.size()];
    Arrays.fill(indices, 0);
    while (io < output.length || qsum < count) {
      int i = smallest(indices);
      long x = buffer.get(i).get(indices[i] - 1);
      qsum += weight(i);
      while (iq < qss.length && qss[iq] <= qsum) {
        output[io] = x;
        io += 1;
        iq += 1;
      }
    }
    return output;
  }

  @Override
  public synchronized void clear() {
    count = 0L;
    buffer.clear();
    buffer.add(new ArrayList<Long>(bufferSize));
    buffer.add(new ArrayList<Long>(bufferSize));
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
    long n = maxMemory.as(Data.BYTES) - 100 - (elemSize * bufferSize);
    if (n < 0) {
      bm = 2;
    } else {
      bm = (int) (n / (16 + elemSize * bufferSize));
    }
    if (bm < 2) {
      bm = 2;
    }
    return bm;
  }

  /**
   * return the weight of the level ie. 2^(i-1) except for the two tree leaves (weight=1) and for
   * the root
   */
  private int weight(int level) {
    int w = 0;
    if (level < 2) {
      w = 1;
    } else if (level == maxDepth) {
      w = rootWeight;
    } else {
      w = 1 << (level - 1);
    }
    return w;
  }

  private long[] quantilesSums(double[] qs) {
    long[] qss = new long[qs.length];
    int i = 0;
    while (i < qss.length) {
      qss[i] = (long) (qs[i] * count);
      i += 1;
    }
    return qss;
  }

  /**
   * Return the level of the smallest element, and update the indices array
   * the indices array represent (for each level of the tree) the index of the next value to read
   */
  private int smallest(int[] indices) {
    int iSmallest = 0;
    long smallest = Long.MAX_VALUE;
    for (int i = 0; i < buffer.size(); i++) {
      long head = Long.MAX_VALUE;
      if (!buffer.get(i).isEmpty() && indices[i] < buffer.get(i).size()) {
        head = buffer.get(i).get(indices[i]);
      }
      if (head < smallest) {
        smallest = head;
        iSmallest = i;
      }
    }
    indices[iSmallest] += 1;
    return iSmallest;
  }

  private void recCollapse(List<Long> buf, int level) {
    assert isSorted(buf);

    // if we reach the root, we can't add more buffer
    if (level == maxDepth) {
      // weight() return the weight of the root, in that case we need the weight of merge result
      int mergeWeight = 1 << (level - 1);
      List<Long> merged = collapse(buf, mergeWeight, buffer.get(level), rootWeight);
      buffer.set(level, merged);
      rootWeight += mergeWeight;
    } else {
      int currentTop = buffer.size() - 1;
      List<Long> merged = collapse(buf, 1, buffer.get(level), 1);
      if (level == currentTop) {
        // if we reach the top, add a new buffer
        buffer.add(merged);
        rootWeight *= 2;
      } else if (buffer.get(level + 1).isEmpty()) {
        // if the upper buffer is empty, use it
        buffer.set(level + 1, merged);
      } else {
        // it the upper buffer isn't empty, collapse with it
        recCollapse(merged, level + 1);
      }
      // now that the values have been collapsed, clean the buffer
      buffer.get(level).clear();
    }
  }

  /**
   * collapse two sorted Arrays of different weight
   * ex: [2,5,7] weight 2 and [3,8,9] weight 3
   *     weight x array + concat = [2,2,5,5,7,7,3,3,3,8,8,8,9,9,9]
   *     sort = [2,2,3,3,3,5,5,7,7,8,8,8,9,9,9]
   *     select every nth elems = [3,7,9]  (n = sum weight / 2)
   */
  private List<Long> collapse(
      List<Long> left,
      int leftWeight,
      List<Long> right,
      int rightWeight) {
    assert left.size() == right.size();
    assert isSorted(left);
    assert isSorted(right);

    int i = 0;
    int j = 0;
    int cnt = 0;
    List<Long> output = new ArrayList<Long>(left.size());

    while (i < left.size() || j < right.size()) {
      long smallest = 0;
      int weight = 0;
      if (i < left.size() && (j == right.size() || left.get(i) < right.get(j))) {
        smallest = left.get(i);
        weight = leftWeight;
        i += 1;
      } else {
        smallest = right.get(j);
        weight = rightWeight;
        j += 1;
      }
      int totalWeight = leftWeight + rightWeight;
      for (int t = 0; t < weight; t++) {
        if (cnt % totalWeight == totalWeight / 2) {
          output.add(smallest);
        }
        cnt += 1;
      }
    }
    assert isSorted(output);
    return output;
  }

  /**
   * Only used by assert during development of the algorithm
   */
  private boolean isSorted(List<Long> list) {
    boolean sorted = true;
    int i = 0;
    while (sorted && i < list.size() - 1) {
      if (list.get(i) > list.get(i + 1)) {
        sorted = false;
      }
      i += 1;
    }
    return sorted;
  }
}
