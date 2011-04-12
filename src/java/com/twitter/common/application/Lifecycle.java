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

package com.twitter.common.application;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Application lifecycle manager, which coordinates orderly shutdown of an application.  This class
 * is responsible for executing shutodwn commands, and can also be used to allow threads to await
 * application shutdown.
 *
 * @author William Farner
 */
public class Lifecycle {

  private static final Logger LOG = Logger.getLogger(Lifecycle.class.getName());

  // Monitor and state for suspending and terminating execution.
  private final Object waitMonitor = new Object();
  private volatile boolean destroyed = false;

  private final ActionController shutdownController;

  @Inject
  public Lifecycle(@ShutdownStage ActionController shutdownController,
      UncaughtExceptionHandler exceptionHandler) {

    this.shutdownController = shutdownController;
    Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
  }

  /**
   * Checks whether this lifecycle is still considered alive.  The lifecycle is still alive until
   * {@link #shutdown()} has been called and all of the actions registered with the shutdown
   * controller have completed.
   *
   * @return {@code true} if the lifecycle is alive, {@code false} otherwise.
   *
   */
  public final boolean isAlive() {
    return !destroyed;
  }

  /**
   * Allows a caller to wait forever; typically used when all work is done in daemon threads.
   * Will exit on interrupts.
   */
  public final void awaitShutdown() {
    LOG.info("Awaiting shutdown");
    synchronized (waitMonitor) {
      while (!destroyed) {
        try {
          waitMonitor.wait();
        } catch (InterruptedException e) {
          LOG.info("Exiting on interrupt");
          shutdown();
          return;
        }
      }
    }
  }

  /**
   * Initiates an orderly shutdown of the lifecycle's registered shutdown hooks.
   */
  public final void shutdown() {
    LOG.info("Shutting down application");
    shutdownController.execute();
    destroyed = true;
    synchronized (waitMonitor) {
      waitMonitor.notifyAll();
    }
  }
}
