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

package com.twitter.common.net.loadbalancing;

/**
 * Tracks requests made to a backend service.
 *
 * @author William Farner
 */
public interface RequestTracker<T> {

  /**
   * Informs the tracker of a completed request.
   *
   * @param key Key to identify the owner of the request.
   * @param result Result of the request.
   * @param requestTimeNanos Time duration spent waiting for the request to complete.
   */
  void requestResult(T key, RequestResult result, long requestTimeNanos);

  enum RequestResult {
    FAILED,
    TIMEOUT,
    SUCCESS
  }
}
