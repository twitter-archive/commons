// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

package com.twitter.common.stats;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.base.Closure;
import com.twitter.common.base.Command;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stat exporter that extracts numeric {@link Stat}s from the {@link Stats} system, and exports them
 * via a caller-defined sink.
 *
 * @author William Farner
 */
public class NumericStatExporter {

  private static final Logger LOG = Logger.getLogger(NumericStatExporter.class.getName());

  private final ScheduledExecutorService executor;
  private final Amount<Long, Time> exportInterval;
  private final Closure<Map<String, ? extends Number>> exportSink;

  private final Runnable exporter;

  /**
   * Creates a new numeric stat exporter that will export to the specified sink.
   *
   * @param exportSink Consumes stats.
   * @param executor Executor to handle export thread.
   * @param exportInterval Export period.
   */
  public NumericStatExporter(final Closure<Map<String, ? extends Number>> exportSink,
      ScheduledExecutorService executor, Amount<Long, Time> exportInterval) {
    checkNotNull(exportSink);
    this.executor = checkNotNull(executor);
    this.exportInterval = checkNotNull(exportInterval);
    this.exportSink = exportSink;

    exporter = new Runnable() {
      @Override public void run() {
          exportSink.execute(Maps.transformValues(
            Maps.uniqueIndex(Stats.getNumericVariables(), GET_NAME), READ_STAT));
      }
    };
  }

  /**
   * Starts the stat exporter.
   *
   * @param shutdownRegistry Shutdown hook registry to allow the exporter to cleanly halt.
   */
  public void start(ShutdownRegistry shutdownRegistry) {
    long intervalSecs = exportInterval.as(Time.SECONDS);
    executor.scheduleAtFixedRate(exporter, intervalSecs, intervalSecs, TimeUnit.SECONDS);

    shutdownRegistry.addAction(new Command() {
      @Override public void execute() {
        stop();
        exportSink.execute(Maps.transformValues(
            Maps.uniqueIndex(Stats.getNumericVariables(), GET_NAME), SAMPLE_AND_READ_STAT));
      }
    });
  }

  /**
   * Stops the stat exporter.  Once stopped, it may be started again by calling
   * {@link #start(ShutdownRegistry)}.
   */
  public void stop() {
    try {
      if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
          LOG.severe("Failed to stop stat exporter.");
        }
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public static final Function<Stat<?>, String> GET_NAME = new Function<Stat<?>, String>() {
    @Override public String apply(Stat<?> stat) {
      return stat.getName();
    }
  };

  public static final Function<Stat<? extends Number>, Number> READ_STAT =
      new Function<Stat<? extends Number>, Number>() {
        @Override public Number apply(Stat<? extends Number> stat) {
          return stat.read();
        }
      };

  private static final Function<RecordingStat<? extends Number>, Number> SAMPLE_AND_READ_STAT =
      new Function<RecordingStat<? extends Number>, Number>() {
        @Override public Number apply(RecordingStat<? extends Number> stat) {
          return stat.sample();
        }
      };
}
