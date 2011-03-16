// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.service.registration;

/**
 * A server set provides real-time membership status of a set of servers.
 * One use case is for all servers in a cluster to join a server set
 * enabling clients to connect to some server in the server set.
 *
 * The members of a server set are likely to be up but it is not guaranteed.
 *
 * A server set is constructed with a "location".
 * Any server joining a server set at location will show up in all other server sets
 * constructed with the same location. If two equal servers join the same server set,
 * a listener on the server set will see one or the other in the set.
 *
 * A server can unjoin a server set to remove itself from the server set.
 * However, if a server is killed or becomes unresponsive before it can unjoin,
 * it may automatically be removed from the server set.
 *
 * The server set can be "connected" or "disconnected". While disconnected, the server
 * set membership will remain unchanged.
 *
 * @author Patrick Chan
 */
public interface ServerSet {
  /**
   * Adds the supplied server to the server set.
   * This method will succeed and not block, even if this process is currently disconnected
   * from the network.
   *
   * This method cannot be called again, even with a different Server object, until
   * after unjoin() is called.
   *
   * This method is not thread-safe.
   *
   * This method tries to catch and handle all connection-related exceptions.
   * Any thrown runtime exceptions indicate a permanent configuration or programming error.
   *
   * @param server    non-null server object representing an endpoint.
   */
  void join(Server server);

  /**
   * Removes the server from the server set. The supplied server object must equal the
   * server object supplied to join().
   *
   * This method is not thread-safe.
   *
   * @param server   non-null server that must be equal to the server object supplied to join().
   */
  void unjoin(Server server);

  /**
   * Registers a listener to receive change notices for this server set.
   * The listener will be notified immediately with the current set and then
   * notified whenever the server set membership changes.
   *
   * This method can only be called once.
   *
   * @param listener  non-null listener that's notified of server set changes.
   */
  void setListener(ServerSetListener listener);
}
