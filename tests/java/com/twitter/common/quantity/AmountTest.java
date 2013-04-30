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

package com.twitter.common.quantity;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import org.junit.Test;

import static com.google.common.testing.junit4.JUnitAsserts.assertNotEqual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class AmountTest {

  @Test
  public void testEquals() {
    assertEquals("expected value equality semantics",
        Amount.of(1L, Time.DAYS), Amount.of(1L, Time.DAYS));

    assertEquals("expected equality to be calculated from amounts converted to a common unit",
        Amount.of(1L, Time.DAYS), Amount.of(24L, Time.HOURS));

    assertNotEqual("expected unit conversions for equality tests to not lose precision",
        Amount.of(25L, Time.HOURS), Amount.of(1L, Time.DAYS));
    assertNotEqual("expected unit conversions for equality tests to not lose precision",
        Amount.of(1L, Time.DAYS), Amount.of(25L, Time.HOURS));

    assertFalse("expected value equality to work only for the same Number types",
        Amount.of(1L, Time.DAYS).equals(Amount.of(1.0, Time.DAYS)));
    assertFalse("expected value equality to work only for the same Number types",
        Amount.of(1L, Time.DAYS).equals(Amount.of(1, Time.DAYS)));

    assertFalse("amounts with incompatible units should never be equal even if their values are",
        Amount.of(1L, Time.NANOSECONDS).equals(Amount.of(1L, Data.BITS)));
  }

  @Test
  public void testComparisonMixedUnits() {
    assertTrue(Amount.of(1, Time.MINUTES).compareTo(Amount.of(59, Time.SECONDS)) > 0);
    assertTrue(Amount.of(1, Time.MINUTES).compareTo(Amount.of(60, Time.SECONDS)) == 0);
    assertTrue(Amount.of(1, Time.MINUTES).compareTo(Amount.of(61, Time.SECONDS)) < 0);

    assertTrue(Amount.of(59, Time.SECONDS).compareTo(Amount.of(1, Time.MINUTES)) < 0);
    assertTrue(Amount.of(60, Time.SECONDS).compareTo(Amount.of(1, Time.MINUTES)) == 0);
    assertTrue(Amount.of(61, Time.SECONDS).compareTo(Amount.of(1, Time.MINUTES)) > 0);
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testOrderingMixedUnits() {
    assertEquals(
        Lists.newArrayList(
            Amount.of(1, Data.BITS),
            Amount.of(1, Data.KB),
            Amount.of(1, Data.MB),
            Amount.of(1, Data.MB)),
        Ordering.natural().sortedCopy(Lists.newArrayList(
            Amount.of(1, Data.KB),
            Amount.of(1024, Data.KB),
            Amount.of(1, Data.BITS),
            Amount.of(1, Data.MB))));
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testOrderingSameUnits() {
    assertEquals(
        Lists.newArrayList(
            Amount.of(1, Time.MILLISECONDS),
            Amount.of(2, Time.MILLISECONDS),
            Amount.of(3, Time.MILLISECONDS),
            Amount.of(4, Time.MILLISECONDS)),
        Ordering.natural().sortedCopy(Lists.newArrayList(
            Amount.of(3, Time.MILLISECONDS),
            Amount.of(2, Time.MILLISECONDS),
            Amount.of(1, Time.MILLISECONDS),
            Amount.of(4, Time.MILLISECONDS))));
  }

  @Test
  public void testConvert() {
    Amount<Long, Time> integralDuration = Amount.of(15L, Time.MINUTES);
    assertEquals(Long.valueOf(15 * 60 * 1000), integralDuration.as(Time.MILLISECONDS));

    assertEquals("expected conversion losing precision to use truncation",
        Long.valueOf(0), integralDuration.as(Time.HOURS));

    assertEquals("expected conversion losing precision to use truncation",
        Long.valueOf(0), Amount.of(45L, Time.MINUTES).as(Time.HOURS));

    Amount<Double, Time> decimalDuration = Amount.of(15.0, Time.MINUTES);
    assertEquals(Double.valueOf(15 * 60 * 1000), decimalDuration.as(Time.MILLISECONDS));
    assertEquals(Double.valueOf(0.25), decimalDuration.as(Time.HOURS));
  }

  @Test(expected = Amount.TypeOverflowException.class)
  public void testAmountThrowsTypeOverflowException() {
    Amount.of(1000, Time.DAYS).asChecked(Time.MILLISECONDS);
  }
}
