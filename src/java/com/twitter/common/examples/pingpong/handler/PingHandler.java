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

package com.twitter.common.examples.pingpong.handler;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import com.twitter.common.base.Closure;
import com.twitter.common.stats.Stats;

@Path("/ping")
public class PingHandler {
  private static final Logger LOG = Logger.getLogger(PingHandler.class.getName());
  private static final AtomicLong PINGS = Stats.exportLong("pings");

  @VisibleForTesting
  static final int DEFAULT_TTL = 60;

  private final Closure<String> client;

  @Inject
  PingHandler(Closure<String> client) {
    this.client = Preconditions.checkNotNull(client);
  }

  /**
   * Services an incoming ping request with a default TTL.
   */
  @GET
  @Path("/{message}")
  public String incoming(@PathParam("message") String message) {
    return incoming(message, DEFAULT_TTL);
  }

  /**
   * Services an incoming ping request.
   */
  @GET
  @Path("/{message}/{ttl}")
  public String incoming(
      @PathParam("message") final String message,
      @PathParam("ttl") int ttl) {

    LOG.info("Got ping, ttl=" + ttl);
    PINGS.incrementAndGet();
    if (ttl > 1) {
      client.execute("/ping/" + message + "/" + (ttl - 1));
    }
    return "pong\n";
  }
}
