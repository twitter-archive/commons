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

package com.twitter.common.stats;

import java.util.Set;

import com.twitter.common.application.ShutdownRegistry;

/**
 * A repository for time series data.
 *
 * @author William Farner
 */
public interface TimeSeriesRepository {

  /**
   * Starts the time series sampler.
   *
   * @param shutdownRegistry An action registry that the repository can use to register a shutdown
   *    for the sampler.
   */
  public void start(ShutdownRegistry shutdownRegistry);

  /**
   * Fetches the names of all available time series.
   *
   * @return Available time series, which can then be obtained by calling {@link #get(String)}.
   */
  public Set<String> getAvailableSeries();

  /**
   * Fetches a time series by name.
   *
   * @param name The name of the time series to fetch.
   * @return The time series registered with the given name, or {@code null} if no such time series
   *     has been registered.
   */
  public TimeSeries get(String name);

  /**
   * Gets an ordered iterable of the timestamps that all timeseries were sampled at.
   *
   * @return All current timestamps.
   */
  public Iterable<Number> getTimestamps();
}
