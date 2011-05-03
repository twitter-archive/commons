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

package com.twitter.common.io;

import com.google.common.base.Predicate;
import com.twitter.common.base.Closure;

/**
 * Encapsulates iteration over a typed data stream that can be filtered.
 *
 * @author John Sirois
 */
public interface Streamer<T> {

  /**
   * Processes a stream fully.  This may cause a database query to be executed, a file to be read
   * or even just call {@link Iterable#iterator()} depending on the implementation.  Implementations
   * guaranty that any resources allocated opening the stream will be closed whether or not process
   * completes normally.
   *
   * @param work a closure over the work to be done for each item in the stream.
   */
  void process(Closure<T> work);

  /**
   * Returns a {@code Streamer} that will process the same stream as this streamer, but will stop
   * processing when encountering the first item for which {@code cond} is true.
   *
   * @param cond a predicate that returns {@code false} as long as the stream should keep being
   *     processed.
   * @return a streamer that will process items until the condition triggers.
   */
  Streamer<T> endOn(Predicate<T> cond);

  /**
   * Returns a {@code Streamer} that will process the same stream as this streamer, but with any
   * items failing the filter to be omitted from processing.
   * @param filter a predicate that returns {@code true} if an item in the stream should be
   *     processed
   * @return a filtered streamer
   */
  Streamer<T> filter(Predicate<T> filter);
}
