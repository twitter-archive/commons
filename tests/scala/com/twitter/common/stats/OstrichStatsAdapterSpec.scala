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

package com.twitter.common.stats

import java.lang.{Integer => JInt}
import java.util.concurrent.TimeUnit

import com.google.common.base.Supplier

import org.junit.runner.RunWith

import org.specs.mock.Mockito
import org.specs.runner.JUnitSuiteRunner
import org.specs.SpecificationWithJUnit

import com.twitter.ostrich.stats.{Stats => OstrichStats, Distribution}

@RunWith(classOf[JUnitSuiteRunner])
class OstrichStatsAdapterSpec extends SpecificationWithJUnit with Mockito {
  "An OstrichStatsAdapter" should {

    val stats = OstrichStats.get("")
    def getOstrichCounter(name: String): Long = stats.getCounters().get(name).get
    def getOstrichGuage(name: String): Double = stats.getGauges().get(name).get
    def getOstrichTiming(name: String): Distribution = stats.getMetrics().get(name).get

    "create an ostrich counter" in {
      getOstrichCounter("fred") must throwA[NoSuchElementException]

      val counter = OstrichStatsAdapter.makeCounter("fred")
      getOstrichCounter("fred") mustEqual 0L

      val value = counter.incrementAndGet
      value mustEqual getOstrichCounter("fred")
    }

    "create an ostrich guage" in {
      val gauge = mock[Supplier[JInt]]
      gauge.get returns 42 thenReturns 1137

      getOstrichGuage("bob") must throwA[NoSuchElementException]

      val stat = OstrichStatsAdapter.makeGauge("bob", gauge)
      stat.read mustEqual 42

      getOstrichGuage("bob") mustEqual 1137.0

      there was two(gauge).get
    }

    "create an ostrich stats for a request timer" in {
      getOstrichTiming("joe_requests_ms") must throwA[NoSuchElementException]

      val requestTimer = OstrichStatsAdapter.makeRequestTimer("joe")
      getOstrichTiming("joe_requests_ms").count mustEqual 0

      requestTimer.requestComplete(TimeUnit.MILLISECONDS.toMicros(42L))
      requestTimer.requestComplete(TimeUnit.MILLISECONDS.toMicros(1137L))

      val timing = getOstrichTiming("joe_requests_ms")
      timing.count mustEqual 2

      // Ostrich only guarantees min and max within 5% per docs
      def beAbout(value: Int) = beCloseTo(value, (value * 0.05).toInt)

      timing.minimum must beAbout(42)
      timing.maximum must beAbout(1137)

      timing.average mustEqual 589

      getOstrichCounter("joe_total_requests") mustEqual 2L
      getOstrichCounter("joe_errors") mustEqual 0L
      getOstrichCounter("joe_reconnects") mustEqual 0L
      getOstrichCounter("joe_timeouts") mustEqual 0L

      requestTimer.incErrors()
      getOstrichCounter("joe_total_requests") mustEqual 3L
      getOstrichCounter("joe_errors") mustEqual 1L
      getOstrichCounter("joe_reconnects") mustEqual 0L
      getOstrichCounter("joe_timeouts") mustEqual 0L

      requestTimer.incReconnects()
      getOstrichCounter("joe_total_requests") mustEqual 3L
      getOstrichCounter("joe_errors") mustEqual 1L
      getOstrichCounter("joe_reconnects") mustEqual 1L
      getOstrichCounter("joe_timeouts") mustEqual 0L

      requestTimer.incTimeouts()
      getOstrichCounter("joe_total_requests") mustEqual 3L
      getOstrichCounter("joe_errors") mustEqual 1L
      getOstrichCounter("joe_reconnects") mustEqual 1L
      getOstrichCounter("joe_timeouts") mustEqual 1L
    }
  }
}
