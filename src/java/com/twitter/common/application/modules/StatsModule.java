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

import java.util.logging.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.twitter.common.application.ShutdownStage;
import com.twitter.common.application.StartupStage;
import com.twitter.common.base.Command;
import com.twitter.common.application.ActionRegistry;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.JvmStats;
import com.twitter.common.stats.StatImpl;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.TimeSeriesRepository;
import com.twitter.common.stats.TimeSeriesRepositoryImpl;
import com.twitter.common.util.BuildInfo;

/**
 * Binding module for injections related to the in-process stats system.
 *
 * @author William Farner
 */
public class StatsModule extends AbstractModule {

  @Override
  protected void configure() {
    requireBinding(Key.get(ActionRegistry.class, StartupStage.class));
    requireBinding(Key.get(ActionRegistry.class, ShutdownStage.class));
    requireBinding(BuildInfo.class);

    // Bindings for TimeSeriesRepositoryImpl.
    bind(new TypeLiteral<Amount<Long, Time>>() {})
        .annotatedWith(Names.named(TimeSeriesRepositoryImpl.SAMPLE_RETENTION_PERIOD))
        .toInstance(Amount.of(1L, Time.HOURS));
    bind(new TypeLiteral<Amount<Long, Time>>() {})
        .annotatedWith(Names.named(TimeSeriesRepositoryImpl.SAMPLE_PERIOD))
        .toInstance(Amount.of(1L, Time.SECONDS));
    bind(TimeSeriesRepository.class).to(TimeSeriesRepositoryImpl.class).in(Singleton.class);

    requestStaticInjection(Init.class);
  }

  static class Init {
    private static final Logger LOG = Logger.getLogger(Init.class.getName());

    @Inject
    private static void startStatsSystem(
        @StartupStage ActionRegistry startupRegistry,
        @ShutdownStage final ActionRegistry shutdownRegistry,
        final BuildInfo buildInfo,
        final TimeSeriesRepository timeSeriesRepository) {
      startupRegistry.addAction(new Command() {
        @Override public void execute() throws RuntimeException {
          LOG.info("Build information: " + buildInfo.getProperties());
          for (final BuildInfo.Key key : BuildInfo.Key.values()) {
            Stats.exportString(new StatImpl<String>(Stats.normalizeName(key.value)) {
              @Override public String read() {
                return buildInfo.getProperties().getProperty(key.value);
              }
            });
          }

          JvmStats.export();
          timeSeriesRepository.start(shutdownRegistry);
        }
      });
    }
  }
}
