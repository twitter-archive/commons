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
 * A partial Gauge implementation.
 *
 * @param <T> Value type.
 */
public abstract class AbstractGauge<T extends Number> implements Gauge<T> {

  private final String name;

  /**
   * Creates an abstract gauge using the provided name.
   *
   * @param name Name of the gauge.
   */
  public AbstractGauge(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }
}
