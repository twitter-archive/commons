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

package com.twitter.common.net;

/**
 * Represents a network environment at the granularity of a datacenter.
 *
 * @author John Sirois
 */
public interface Environment {

  /**
   * Returns the name of this network environment's datacenter.
   *
   * @return the name of this environment's datacenter
   */
  String dcName();

  /**
   * Creates a fully qualified hostname for a given unqualified hostname in the network
   * environment's datacenter.  Does not confirm that the host exists.
   *
   * @param hostname The simple hostname to qualify.
   * @return The fully qualified hostname.
   */
  String fullyQualify(String hostname);

  /**
   * Checks if a given {@code hostname} is a valid hostname for a host in this network environment;
   * does not guarantee that the host exists in this network environment.
   *
   * @param hostname The simple hostname to check for membership in this network environment.
   * @return {@code true} if the hostname is a valid hostname for this network environment.
   */
  boolean contains(String hostname);
}
