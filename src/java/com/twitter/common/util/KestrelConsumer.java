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

package com.twitter.common.util;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.twitter.common.stats.Stats;
import com.twitter.common.memcached.Memcached;
import net.spy.memcached.MemcachedClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class responsible for pulling work items off of a kestrel queue.
 *
 * @author William Farner
 */
public class KestrelConsumer {
  private static Logger log = Logger.getLogger(KestrelConsumer.class.getName());

  private MemcachedClient queue;
  private final List<String> kestrelServers;
  private final String queueName;
  private final Function<String, Boolean> taskHandler;

  // Controls backoffs when there is no work available.
  private static final int MIN_DELAY_MS = 1;
  private static final int MAX_BACKOFF_DELAY_MS = 8192;
  private int backoffDelayMs = MIN_DELAY_MS;

  // Tracks rate at which items are being removed from the queue.
  private final AtomicLong stats = Stats.exportLong("kestrel_consumption");

  /**
   * Creates a new kestrel consumer that will communicate with the given kestrel servers (where
   * a server string is formatted as host:port).
   *
   * @param kestrelServers The kestrel servers to pull work from.
   * @param queueName The name of the kestrel queue to pull work from.
   * @param taskHandler The handler for new work retrieved from the kestrel queue. The handler
   *    should return {@code false} if the work item was not successfully handled.
   */
  public KestrelConsumer(List<String> kestrelServers, String queueName,
      Function<String, Boolean> taskHandler) {
    Preconditions.checkNotNull(kestrelServers);
    Preconditions.checkNotNull(queueName);
    Preconditions.checkNotNull(taskHandler);
    this.kestrelServers = kestrelServers;
    this.queueName = queueName;
    this.taskHandler = taskHandler;
  }

  public void initialize() {
    queue = Memcached.newKestrelClient(kestrelServers);
  }

  public void consumeForever() {
    Preconditions.checkNotNull(queue);

    log.info("Consuming items from the kestrel queue forever.");
    while(true) {
      Object task = null;
      while (task == null) {
        try {
          Thread.sleep(backoffDelayMs);
        } catch (InterruptedException e) {
          log.log(Level.INFO, "Interrupted while sleeping.", e);
        }

        task = queue.get(queueName);

        if (task == null) {
          // Back off exponentially (capped).
          backoffDelayMs = Math.min(backoffDelayMs * 2, MAX_BACKOFF_DELAY_MS);
          log.info("No work, backing off for " + backoffDelayMs + " ms.");
        } else {
          stats.incrementAndGet();
          backoffDelayMs = MIN_DELAY_MS;
        }
      }

      if (task instanceof String) {
        taskHandler.apply((String) task);
      } else {
        log.severe("Unexpected task " + task + " of type " + task.getClass().getName());
      }
    }
  }
}
