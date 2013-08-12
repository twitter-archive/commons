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

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import com.google.common.base.Supplier

import com.twitter.ostrich.stats.{Stats => OstrichStats}
import com.twitter.common.stats.StatsProvider.RequestTimer

/**
 * Adapts Ostrich Stats to science commons StatsProvider.
 */
object OstrichStatsAdapter extends StatsProvider {
  // Science stats has a single global namespace - grab the ostrich equivalent.
  private[this] lazy val stats = OstrichStats.get("")

  /**
   * Returns this `OstrichStatsAdapter` since ostrich stats don't have an equivalent tracking mode
   * built-in but rely instead on attaching a [[com.twitter.ostrich.stats.StatsListener]].
   */
  def untracked() = this

  def makeCounter(name: String) = createCounter(name)

  def makeGauge[T <: Number](name: String, gauge: Supplier[T]) = {
    stats.addGauge(name) {
      gauge.get.doubleValue
    }
    new Stat[T] {
      def getName = name
      def read = gauge.get
    }
  }

  def makeRequestTimer(name: String) = {
    val requests = createCounter(name + "_total_requests")
    val errors = createCounter(name + "_errors")
    val reconnects = createCounter(name + "_reconnects")
    val timeouts = createCounter(name + "_timeouts")

    val timingStat = stats.getMetric(name + "_requests_ms")
    new RequestTimer {
      def requestComplete(timingMicros: Long) {
        requests.incrementAndGet()
        timingStat.add(TimeUnit.MICROSECONDS.toMillis(timingMicros).toInt)
      }
      def incErrors() {
        // science adds a timing of 0 here to bump the request count - we just use a separate
        // counter so that we don't under-report success timings
        requests.incrementAndGet()
        errors.incrementAndGet()
      }
      def incReconnects() { reconnects.incrementAndGet() }
      def incTimeouts() { timeouts.incrementAndGet() }
    }
  }

  /**
   * Subclasses can override to create custom counters.
   */
  protected def createCounter(name: String) = {
    val atomicLong = new AtomicLong
    stats.makeCounter(name, atomicLong)
    atomicLong
  }
}
