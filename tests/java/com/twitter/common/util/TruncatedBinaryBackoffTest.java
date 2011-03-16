// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.util;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author John Sirois
 */
public class TruncatedBinaryBackoffTest {
  @Test(expected = NullPointerException.class)
  public void testNullInitialBackoffRejected() {
    new TruncatedBinaryBackoff(null, Amount.of(1L, Time.SECONDS));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testZeroInitialBackoffRejected() {
    new TruncatedBinaryBackoff(Amount.of(0L, Time.SECONDS), Amount.of(1L, Time.SECONDS));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNegativeInitialBackoffRejected() {
    new TruncatedBinaryBackoff(Amount.of(-1L, Time.SECONDS), Amount.of(1L, Time.SECONDS));
  }

  @Test(expected = NullPointerException.class)
  public void testNullMaximumBackoffRejected() {
    new TruncatedBinaryBackoff(Amount.of(1L, Time.SECONDS), null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMaximumBackoffLessThanInitialBackoffRejected() {
    new TruncatedBinaryBackoff(Amount.of(2L, Time.SECONDS), Amount.of(1L, Time.SECONDS));
  }

  @Test
  public void testCalculateBackoffMs() {
    TruncatedBinaryBackoff backoff =
        new TruncatedBinaryBackoff(Amount.of(1L, Time.MILLISECONDS),
            Amount.of(6L, Time.MILLISECONDS));

    try {
      backoff.calculateBackoffMs(-1L);
    } catch (IllegalArgumentException e) {
      // expected
    }

    assertEquals(1, backoff.calculateBackoffMs(0));
    assertEquals(2, backoff.calculateBackoffMs(1));
    assertEquals(4, backoff.calculateBackoffMs(2));
    assertEquals(6, backoff.calculateBackoffMs(4));
    assertEquals(6, backoff.calculateBackoffMs(8));
  }
}
