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

package com.twitter.common.stats

import com.google.common.base.Supplier
import com.twitter.ostrich.{Stats => OstrichStats}
import com.twitter.ostrich.TimingStat
import java.lang.{Integer => JInt}
import org.specs.mock.EasyMock
import org.specs.Specification
import java.util.concurrent.TimeUnit

/**
 * @author jsirois
 */
class OstrichStatsAdapterSpec extends Specification with EasyMock {
  "An OstrichStatsAdapter" should {

    def getOstrichCounter(name: String): Long = OstrichStats.getCounterStats()(name)
    def getOstrichGuage(name: String): Double = OstrichStats.getGaugeStats(false)(name)
    def getOstrichTiming(name: String): TimingStat = OstrichStats.getTimingStats(false)(name)

    "create an ostrich counter" in {
      getOstrichCounter("fred") must throwA[NoSuchElementException]

      val counter = OstrichStatsAdapter.makeCounter("fred")
      getOstrichCounter("fred") mustEqual 0L

      val value = counter.incrementAndGet
      value mustEqual getOstrichCounter("fred")
    }

    "create an ostrich guage" in {
      val gauge = mock[Supplier[JInt]]
      gauge.get returns 42
      gauge.get returns 1137
      replay(gauge)

      getOstrichGuage("bob") must throwA[NoSuchElementException]

      val stat = OstrichStatsAdapter.makeGauge("bob", gauge)
      stat.read mustEqual 42

      getOstrichGuage("bob") mustEqual 1137.0

      verify(gauge)
    }

    "create an ostrich stats for a request timer" in {
      getOstrichTiming("joe_requests_ms") must throwA[NoSuchElementException]

      val requestTimer = OstrichStatsAdapter.makeRequestTimer("joe")
      getOstrichTiming("joe_requests_ms").count mustEqual 0

      requestTimer.requestComplete(TimeUnit.MILLISECONDS.toMicros(42L))
      requestTimer.requestComplete(TimeUnit.MILLISECONDS.toMicros(1137L))

      val timing = getOstrichTiming("joe_requests_ms")
      timing.count mustEqual 2
      timing.minimum mustEqual 42L
      timing.maximum mustEqual 1137L
      timing.average mustEqual 589

      getOstrichCounter("joe_total_requests") mustEqual 2L
      getOstrichCounter("joe_errors") mustEqual 0L
      getOstrichCounter("joe_reconnects") mustEqual 0L
      getOstrichCounter("joe_timeouts") mustEqual 0L

      requestTimer.incErrors
      getOstrichCounter("joe_total_requests") mustEqual 3L
      getOstrichCounter("joe_errors") mustEqual 1L
      getOstrichCounter("joe_reconnects") mustEqual 0L
      getOstrichCounter("joe_timeouts") mustEqual 0L

      requestTimer.incReconnects
      getOstrichCounter("joe_total_requests") mustEqual 3L
      getOstrichCounter("joe_errors") mustEqual 1L
      getOstrichCounter("joe_reconnects") mustEqual 1L
      getOstrichCounter("joe_timeouts") mustEqual 0L

      requestTimer.incTimeouts
      getOstrichCounter("joe_total_requests") mustEqual 3L
      getOstrichCounter("joe_errors") mustEqual 1L
      getOstrichCounter("joe_reconnects") mustEqual 1L
      getOstrichCounter("joe_timeouts") mustEqual 1L
    }
  }
}
