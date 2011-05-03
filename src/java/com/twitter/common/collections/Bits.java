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

package com.twitter.common.collections;

import com.google.common.base.Preconditions;

/**
 * Convenience class for doing bit-level operations on ints and longs.
 *
 * @author William Farner
 */
public final class Bits {

  private static final int LSB = 0;
  private static final int INT_MSB = 31;
  private static final int LONG_MSB = 63;

  private Bits() {
    // Utility.
  }

  /**
   * Tests whether a bit is set in an int value.
   *
   * @param value The bit field to test.
   * @param bit The index of the bit to test, where bit 0 is the LSB.
   * @return {@code true} if the bit is set, {@code false} otherwise.
   */
  public static boolean isBitSet(int value, int bit) {
    Preconditions.checkState(bit >= LSB);
    Preconditions.checkState(bit <= INT_MSB);
    int mask = 1 << bit;
    return (value & mask) != 0;
  }

  /**
   * Tests whether a bit is set in a long value.
   *
   * @param value The bit field to test.
   * @param bit The index of the bit to test, where bit 0 is the LSB.
   * @return {@code true} if the bit is set, {@code false} otherwise.
   */
  public static boolean isBitSet(long value, int bit) {
    Preconditions.checkState(bit >= LSB);
    Preconditions.checkState(bit <= LONG_MSB);
    long mask = 1L << bit;
    return (value & mask) != 0;
  }

  /**
   * Sets a bit in an int value.
   *
   * @param value The bit field to modify.
   * @param bit The index of the bit to set, where bit 0 is the LSB.
   * @return The original value, with the indexed bit set.
   */
  public static int setBit(int value, int bit) {
    Preconditions.checkState(bit >= LSB);
    Preconditions.checkState(bit <= INT_MSB);
    int mask = 1 << bit;
    return value | mask;
  }

  /**
   * Sets a bit in a long value.
   *
   * @param value The bit field to modify.
   * @param bit The index of the bit to set, where bit 0 is the LSB.
   * @return The original value, with the indexed bit set.
   */
  public static long setBit(long value, int bit) {
    Preconditions.checkState(bit >= LSB);
    Preconditions.checkState(bit <= LONG_MSB);
    long mask = 1L << bit;
    return value | mask;
  }

  /**
   * Clears a bit in an int value.
   *
   * @param value The bit field to modify.
   * @param bit The index of the bit to clear, where bit 0 is the LSB.
   * @return The original value, with the indexed bit clear.
   */
  public static int clearBit(int value, int bit) {
    Preconditions.checkState(bit >= LSB);
    Preconditions.checkState(bit <= INT_MSB);
    int mask = ~setBit(0, bit);
    return value & mask;
  }

  /**
   * Clears a bit in a long value.
   *
   * @param value The bit field to modify.
   * @param bit The index of the bit to clear, where bit 0 is the LSB.
   * @return The original value, with the indexed bit clear.
   */
  public static long clearBit(long value, int bit) {
    Preconditions.checkState(bit >= LSB);
    Preconditions.checkState(bit <= LONG_MSB);
    long mask = ~setBit(0L, bit);
    return value & mask;
  }
}
