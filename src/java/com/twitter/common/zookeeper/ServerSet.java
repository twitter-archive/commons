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

package com.twitter.common.zookeeper;

import com.twitter.common.net.pool.DynamicHostSet;
import com.twitter.common.zookeeper.Group.JoinException;
import com.twitter.thrift.ServiceInstance;
import com.twitter.thrift.Status;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * A logical set of servers registered in ZooKeeper.  Intended to be used by both servers in a
 * common service and their clients.
 *
 * TODO(William Farner): Explore decoupling this from thrift.
 *
 * @author William Farner
 */
public interface ServerSet extends DynamicHostSet<ServiceInstance> {

  /**
   * Attempts to join a server set for this logical service group.
   *
   * @param endpoint the primary service endpoint
   * @param additionalEndpoints and additional endpoints keyed by their logical name
   * @param status the current service status
   * @return an EndpointStatus object that allows the endpoint to adjust its status
   * @throws JoinException if there was a problem joining the server set
   * @throws InterruptedException if interrupted while waiting to join the server set
   */
  public EndpointStatus join(InetSocketAddress endpoint,
      Map<String, InetSocketAddress> additionalEndpoints, Status status)
      throws JoinException, InterruptedException;

  /**
   * A handle to a service endpoint's status data that allows updating it to track current events.
   */
  public interface EndpointStatus {

    /**
     * Attempts to update the status of the service endpoint associated with this endpoint.  If the
     * {@code status} is {@link Status#DEAD} then the endpoint will be removed from the server set.
     *
     * @param status the current status of the endpoint
     * @throws UpdateException if there was a problem writing the update
     */
    void update(Status status) throws UpdateException;
  }

  /**
   * Indicates an error updating a service's status information.
   */
  public static class UpdateException extends Exception {
    public UpdateException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
