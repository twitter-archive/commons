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

/**
 * A metric that has a name and a variable number value.
 *
 * @param <T> Value type.
 */
public interface Gauge<T extends Number> {

  /**
   * Gets the name of this stat. For sake of convention, variable names should be alphanumeric, and
   * use underscores.
   *
   * @return The variable name.
   */
  String getName();

  /**
   * Reads the latest value of the metric.
   * Must never return {@code null}.
   *
   * @return The metric value.
   */
  T read();
}
