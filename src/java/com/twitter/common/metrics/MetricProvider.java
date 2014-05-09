// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

package com.twitter.common.metrics;

import java.util.Map;

/**
 * A provider of metric samples.
 */
public interface MetricProvider {

  /**
   * Obtains a snapshot of all available metric values.
   *
   * @return Metric samples.
   */
  Map<String, Number> sample();

  /**
   * Obtains a snapshot of all available gauges.
   *
   * @return Metric samples.
   */
  Map<String, Number> sampleGauges();

  /**
   * Obtains a snapshot of all available counters.
   *
   * @return Metric samples.
   */
  Map<String, Number> sampleCounters();

  /**
   * Obtains a snapshot of all available histograms.
   *
   * @return Metric samples.
   */
  Map<String, Snapshot> sampleHistograms();
}
