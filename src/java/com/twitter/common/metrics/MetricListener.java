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
 * A listener that receives updated metric samples.
 */
public interface MetricListener {

  /**
   * Notifies the listener of updated samples.
   *
   * @param samples Updated samples.  Samples associated with the same metric will use a consistent
   *     key, and keys may be added or removed over the lifetime of the listener.
   */
  void updateStats(Map<String, Number> samples);
}
