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

import com.google.common.base.Supplier
import com.twitter.ostrich.{Stats => OstrichStats}
import com.twitter.common.stats.StatsProvider.RequestTimer
import java.util.concurrent.TimeUnit

/**
 * Adapts Ostrich Stats to science commons StatsProvider.
 *
 * @author jsirois
 */
object OstrichStatsAdapter extends StatsProvider {
  def makeCounter(name: String) = createCounter(name).value

  def makeGauge[T <: Number](name: String, gauge: Supplier[T]) = {
    OstrichStats.makeGauge(name) {
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

    val timingStat = OstrichStats.getTiming(name + "_requests_ms")
    new RequestTimer {
      def requestComplete(timingMicros: Long) = {
        requests.incr()
        timingStat.add(TimeUnit.MICROSECONDS.toMillis(timingMicros).toInt)
      }
      def incErrors = {
        // science adds a timing of 0 here to bump the request count - we just use a separate
        // counter so that we don't under-report success timings
        requests.incr()
        errors.incr()
      }
      def incReconnects = reconnects.incr()
      def incTimeouts = timeouts.incr()
    }
  }

  /**
   * Subclasses can override to create custom {@link Counter}s.
   */
  protected def createCounter(name: String) = {
    OstrichStats.getCollection.getCounter(name)
  }
}
