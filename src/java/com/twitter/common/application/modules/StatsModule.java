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

package com.twitter.common.application.modules;

import java.util.Properties;
import java.util.logging.Logger;

import com.google.common.base.Supplier;
import com.google.common.primitives.Longs;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.JvmStats;
import com.twitter.common.stats.Stat;
import com.twitter.common.stats.StatImpl;
import com.twitter.common.stats.StatRegistry;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.TimeSeriesRepository;
import com.twitter.common.stats.TimeSeriesRepositoryImpl;
import com.twitter.common.util.BuildInfo;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Binding module for injections related to the in-process stats system.
 *
 * This modules supports two command line arguments:
 * <ul>
 *   <li>{@code stat_sampling_interval} - Statistic value sampling interval.
 *   <li>{@code stat_retention_period} - Time for a stat to be retained in memory before expring.
 * </ul>
 *
 * Bindings required by this module:
 * <ul>
 *   <li>{@code ShutdownRegistry} - Shutdown hook registry.
 *   <li>{@code BuildInfo} - Build information for the application.
 * </ul>
 *
 * @author William Farner
 */
public class StatsModule extends AbstractModule {

  @CmdLine(name = "stat_sampling_interval", help = "Statistic value sampling interval.")
  private static final Arg<Amount<Long, Time>> SAMPLING_INTERVAL =
      Arg.create(Amount.of(1L, Time.SECONDS));

  @CmdLine(name = "stat_retention_period",
      help = "Time for a stat to be retained in memory before expiring.")
  private static final Arg<Amount<Long, Time>> RETENTION_PERIOD =
      Arg.create(Amount.of(1L, Time.HOURS));

  public static Amount<Long, Time> getSamplingInterval() {
    return SAMPLING_INTERVAL.get();
  }

  @Override
  protected void configure() {
    requireBinding(ShutdownRegistry.class);
    requireBinding(BuildInfo.class);

    // Bindings for TimeSeriesRepositoryImpl.
    bind(StatRegistry.class).toInstance(Stats.STAT_REGISTRY);
    bind(new TypeLiteral<Amount<Long, Time>>() { })
        .annotatedWith(Names.named(TimeSeriesRepositoryImpl.SAMPLE_RETENTION_PERIOD))
        .toInstance(RETENTION_PERIOD.get());
    bind(new TypeLiteral<Amount<Long, Time>>() { })
        .annotatedWith(Names.named(TimeSeriesRepositoryImpl.SAMPLE_PERIOD))
        .toInstance(SAMPLING_INTERVAL.get());
    bind(TimeSeriesRepository.class).to(TimeSeriesRepositoryImpl.class).in(Singleton.class);

    bind(new TypeLiteral<Supplier<Iterable<Stat<?>>>>() { }).toInstance(
        new Supplier<Iterable<Stat<?>>>() {
          @Override public Iterable<Stat<?>> get() {
            return Stats.getVariables();
          }
        }
    );

    LifecycleModule.bindStartupAction(binder(), StartStatPoller.class);
  }

  public static final class StartStatPoller implements Command {
    private static final Logger LOG = Logger.getLogger(StartStatPoller.class.getName());
    private final ShutdownRegistry shutdownRegistry;
    private final BuildInfo buildInfo;
    private final TimeSeriesRepository timeSeriesRepository;

    @Inject StartStatPoller(
        ShutdownRegistry shutdownRegistry,
        BuildInfo buildInfo,
        TimeSeriesRepository timeSeriesRepository) {

      this.shutdownRegistry = checkNotNull(shutdownRegistry);
      this.buildInfo = checkNotNull(buildInfo);
      this.timeSeriesRepository = checkNotNull(timeSeriesRepository);
    }

    @Override public void execute() {
      Properties properties = buildInfo.getProperties();
      LOG.info("Build information: " + properties);
      for (String name : properties.stringPropertyNames()) {
        final String stringValue = properties.getProperty(name);
        if (stringValue == null) {
          continue;
        }
        final Long longValue = Longs.tryParse(stringValue);
        if (longValue != null) {
          Stats.exportStatic(new StatImpl<Long>(Stats.normalizeName(name)) {
            @Override public Long read() {
              return longValue;
            }
          });
        } else {
          Stats.exportString(new StatImpl<String>(Stats.normalizeName(name)) {
            @Override public String read() {
              return stringValue;
            }
          });
        }
      }

      JvmStats.export();
      timeSeriesRepository.start(shutdownRegistry);
    }
  }
}
