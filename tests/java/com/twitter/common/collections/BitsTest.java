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


import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author William Farner
 */
public class BitsTest {

  private static final List<Long> BASIC_LONGS = Arrays.asList(
      0x0000000000000000L,
      0x00000000FFFFFFFFL,
      0xFFFFFFFF00000000L,
      0xFFFFFFFFFFFFFFFFL
  );

  private static final List<Long> SINGLE_BIT_LONGS = Arrays.asList(
      0x0000000000000001L,
      0x0000000000000002L,
      0x0000000000000004L,
      0x0000000000000008L,
      0x0000000000000010L,
      0x0000000000000020L,
      0x0000000000000040L,
      0x0000000000000080L,
      0x0000000000000100L,
      0x0000000000000200L,
      0x0000000000000400L,
      0x0000000000000800L,
      0x0000000000001000L,
      0x0000000000002000L,
      0x0000000000004000L,
      0x0000000000008000L,
      0x0000000000010000L,
      0x0000000000020000L,
      0x0000000000040000L,
      0x0000000000080000L,
      0x0000000000100000L,
      0x0000000000200000L,
      0x0000000000400000L,
      0x0000000000800000L,
      0x0000000001000000L,
      0x0000000002000000L,
      0x0000000004000000L,
      0x0000000008000000L,
      0x0000000010000000L,
      0x0000000020000000L,
      0x0000000040000000L,
      0x0000000080000000L,
      0x0000000100000000L,
      0x0000000200000000L,
      0x0000000400000000L,
      0x0000000800000000L,
      0x0000001000000000L,
      0x0000002000000000L,
      0x0000004000000000L,
      0x0000008000000000L,
      0x0000010000000000L,
      0x0000020000000000L,
      0x0000040000000000L,
      0x0000080000000000L,
      0x0000100000000000L,
      0x0000200000000000L,
      0x0000400000000000L,
      0x0000800000000000L,
      0x0001000000000000L,
      0x0002000000000000L,
      0x0004000000000000L,
      0x0008000000000000L,
      0x0010000000000000L,
      0x0020000000000000L,
      0x0040000000000000L,
      0x0080000000000000L,
      0x0100000000000000L,
      0x0200000000000000L,
      0x0400000000000000L,
      0x0800000000000000L,
      0x1000000000000000L,
      0x2000000000000000L,
      0x4000000000000000L,
      0x8000000000000000L
  );

  private static final List<Long> RANDOM_LONGS = Arrays.asList(
      0x88BA910280684BFAL,
      0xAD4376223ACCDA29L,
      0xE5992FBC3D222B2BL,
      0x93385280AC0EE09CL,
      0x7BFCB384F2B88BD1L,
      0xABD0E19C726DC54EL,
      0xA0C0A9D1C38073E1L,
      0x957A232B46A01071L,
      0x04CCBDFE1F714EB4L,
      0xACDC6DACDF25C070L,
      0xCE9AC78F31BA17FAL,
      0xEAE5F04361A46FFFL,
      0x1B18F8BE5089C1EDL,
      0xD8E0EED9F397496DL,
      0xD6A1F134843B4AC9L,
      0x186F1C907FBA5B3CL,
      0x8A1CB91A7929357AL,
      0xB6F5B84FFBFE21F3L,
      0xD8F2E84C73735997L,
      0xFE9C4FBDAB495B31L,
      0x92AB7DEB113D3E8FL,
      0x5CBA4C59FC1C7605L,
      0xBADD2C1E2D9A7621L,
      0x54ADAEDF528B347DL,
      0x1C131C0F1FC1AA11L,
      0x00D79CEBA636527CL,
      0x7A6B6E39E6765118L,
      0xF021A4E5DD3845D0L,
      0x6966FAE6CA243F1BL,
      0xF738DC1B00956B83L,
      0x09D616F4502784A0L,
      0xEFDA3B2B2EF13671L
  );

  private static final List<Integer> BASIC_INTS = Arrays.asList(
      0x00000000,
      0xFFFFFFFF
  );

  private static final List<Integer> SINGLE_BIT_INTS = Arrays.asList(
      0x00000001,
      0x00000002,
      0x00000004,
      0x00000008,
      0x00000010,
      0x00000020,
      0x00000040,
      0x00000080,
      0x00000100,
      0x00000200,
      0x00000400,
      0x00000800,
      0x00001000,
      0x00002000,
      0x00004000,
      0x00008000,
      0x00010000,
      0x00020000,
      0x00040000,
      0x00080000,
      0x00100000,
      0x00200000,
      0x00400000,
      0x00800000,
      0x01000000,
      0x02000000,
      0x04000000,
      0x08000000,
      0x10000000,
      0x20000000,
      0x40000000,
      0x80000000
  );

  private static final List<Integer> RANDOM_INTS = Arrays.asList(
      0x124FE3D6,
      0xA688379A,
      0xB49EC20C,
      0x0C2C8D99,
      0x32D1E1BB,
      0xCF7169FF,
      0x94D7D596,
      0xE3A962CD,
      0xA47FA154,
      0x20DB4BA5,
      0x27FA77BC,
      0x2DCEDF0D,
      0xC05ACE5A,
      0x4C871D86,
      0x29B4D423,
      0xFCB5EC65,
      0x1CAD4057,
      0x2EC8E1C8,
      0x251D315A,
      0x6D6C6021,
      0x3F58FF67,
      0xFB917B2E,
      0x51338D3E,
      0xE1D6695B,
      0x149D5142,
      0x51B6CFD1,
      0xABB61BA0,
      0x4E1FD4D4,
      0x0AB11279,
      0xEA8EE310,
      0x9C6C8B24,
      0x99DD3A07
  );

  @SuppressWarnings("unchecked") //Needed because type information lost in vargs.
  private static final List<List<Long>> LONGS_TEST_LISTS = Arrays.asList(
      BASIC_LONGS,
      SINGLE_BIT_LONGS,
      RANDOM_LONGS
  );

  @SuppressWarnings("unchecked") //Needed because type information lost in vargs.
  private static final List<List<Integer>> INTS_TEST_LISTS = Arrays.asList(
      BASIC_INTS,
      SINGLE_BIT_INTS,
      RANDOM_INTS
  );

  @Test
  public void testSetAndGetSingleLongBits() {
    for (List<Long> testList : LONGS_TEST_LISTS) {
      for (long testValue : testList) {
        for (int i = 0; i < 64; ++i) {
          long setOneBit = Bits.setBit(testValue, i);
          assertTrue(Bits.isBitSet(setOneBit, i));
          assertEquals(Bits.clearBit(testValue, i), Bits.clearBit(setOneBit, i));
          assertTrue(!Bits.isBitSet(Bits.clearBit(setOneBit, i), i));
        }
      }
    }
  }

  @Test
  public void testAllLongBits() {
    for (List<Long> testList : LONGS_TEST_LISTS) {
      for (long testValue : testList) {
        long inverseValue1 = 0;
        long inverseValue2 = 0xFFFFFFFFFFFFFFFFL;
        for (int i = 0; i < 64; ++i) {
          if (!Bits.isBitSet(testValue, i)) {
            inverseValue1 = Bits.setBit(inverseValue1, i);
          } else {
            inverseValue2 = Bits.clearBit(inverseValue2, i);
          }
        }
        assertThat(0xFFFFFFFFFFFFFFFFL, is(inverseValue1 | testValue));
        assertThat(0xFFFFFFFFFFFFFFFFL, is(inverseValue2 | testValue));
        assertThat(0xFFFFFFFFFFFFFFFFL, is(inverseValue1 ^ testValue));
        assertThat(0xFFFFFFFFFFFFFFFFL, is(inverseValue2 ^ testValue));
      }
    }
  }

  @Test
  public void testSetAndGetSingleIntBits() {
    for (List<Integer> testList : INTS_TEST_LISTS) {
      for (int testValue : testList) {
        for (int i = 0; i < 32; ++i) {
          int setOneBit = Bits.setBit(testValue, i);
          assertTrue(Bits.isBitSet(setOneBit, i));
          assertEquals(Bits.clearBit(testValue, i), Bits.clearBit(setOneBit, i));
          assertTrue(!Bits.isBitSet(Bits.clearBit(setOneBit, i), i));
        }
      }
    }
  }

  @Test
  public void testAllIntBits() {
    for (List<Integer> testList : INTS_TEST_LISTS) {
      for (int testValue : testList) {
        int inverseValue1 = 0;
        int inverseValue2 = 0xFFFFFFFF;
        for (int i = 0; i < 32; ++i) {
          if (!Bits.isBitSet(testValue, i)) {
            inverseValue1 = Bits.setBit(inverseValue1, i);
          } else {
            inverseValue2 = Bits.clearBit(inverseValue2, i);
          }
        }
        assertThat(0xFFFFFFFF, is(inverseValue1 | testValue));
        assertThat(0xFFFFFFFF, is(inverseValue2 | testValue));
        assertThat(0xFFFFFFFF, is(inverseValue1 ^ testValue));
        assertThat(0xFFFFFFFF, is(inverseValue2 ^ testValue));
      }
    }
  }
}
