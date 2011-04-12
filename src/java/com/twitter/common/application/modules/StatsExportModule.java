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

package com.twitter.common.application.modules;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import com.twitter.common.application.ActionRegistry;
import com.twitter.common.application.ShutdownStage;
import com.twitter.common.application.StartupStage;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.NumericStatExporter;

/**
 * Module to enable periodic exporting of registered stats to an external service.
 *
 * This modules supports a single command line argument, {@code stat_export_interval}, which
 * controls the export interval (defaulting to 1 minute).
 *
 * Bindings required by this module:
 * <ul>
 *   <li>{@code @StartupStage ActionRegistry} - Startup action registry.
 *   <li>{@code @ShutdownStage ActionRegistry} - Shutdown hook registry.
 * </ul>
 *
 * @author William Farner
 */
public class StatsExportModule extends AbstractModule {

  @CmdLine(name = "stat_export_interval",
           help = "Amount of time to wait between stat exports.")
  private static final Arg<Amount<Long, Time>> EXPORT_INTERVAL =
      Arg.create(Amount.of(1L, Time.MINUTES));

  @Override
  protected void configure() {
    requireBinding(Key.get(new TypeLiteral<Closure<Map<String, ? extends Number>>>() {}));
    requestStaticInjection(Init.class);
  }

  static class Init {
    @Inject private static void startExporter(
        Closure<Map<String, ? extends Number>> statSink,
        @StartupStage ActionRegistry startupRegistry,
        @ShutdownStage final ActionRegistry shutdownRegistry) {

      ThreadFactory threadFactory =
          new ThreadFactoryBuilder().setNameFormat("CuckooExporter-%d").setDaemon(true).build();

      final NumericStatExporter exporter = new NumericStatExporter(statSink,
          Executors.newScheduledThreadPool(1, threadFactory), EXPORT_INTERVAL.get());

      startupRegistry.addAction(new Command() {
        @Override public void execute() {
          exporter.start(shutdownRegistry);
        }
      });
    }
  }
}
