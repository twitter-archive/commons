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

package com.twitter.common.service.registration;

import java.util.Set;

/**
 * An interface to an object that is interested in receiving notification whenever the
 * membership of a {@link ServerSet} changes. See {@link ServerSet.setListener()}.
 * @author Patrick Chan
 */
public interface ServerSetListener {
  /**
   * After the listener is registered, the first call will contain the current server
   * set membership. The method is then called every time the membership changes.
   * The returned set is a copy of the server set's internal data. The initial
   * membership of a server set is empty. When disconnected, the membership won't change.
   *
   * The implementation of this method should return in a timely manner.
   * This method could be called more than once with the same value.
   * This method will not be called concurrently.
   *
   * @param serverSet  the non-null copy of the current set of available servers.
   */
  void onChange(Set<Server> serverSet);

  /**
   * Although a server set tries to mask connection failures, a server could become
   * disconnected from the other members. This method is called whenever there's a
   * change in the connection status of the server set. The initial state of a server
   * set is disconnected.
   *
   * The implementation of this method should return in a timely manner.
   * This method could be called more than once with the same value.
   * This method will not be called concurrently.
   */
  void onConnect(boolean connected);
}
