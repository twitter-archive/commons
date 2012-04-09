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

package com.twitter.common.stats;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import com.twitter.common.base.MorePreconditions;

/**
 * Manages {@link Stat}s that should be exported for monitoring.
 *
 * Statistic names may only contain {@code [A-Za-z0-9_]},
 * all other chars will be logged as a warning and replaced with underscore on export.
 *
 * @author John Sirois
 */
public class Stats {

  private static final Logger LOG = Logger.getLogger(Stats.class.getName());
  private static final Pattern NOT_NAME_CHAR = Pattern.compile("[^A-Za-z0-9_]");

  private static final Map<String, Stat> VAR_MAP =
      Collections.synchronizedMap(Maps.<String, Stat>newHashMap());

  // Store stats in the order they were registered, so that derived variables are
  // sampled after their inputs.
  private static final Map<String, RecordingStat<? extends Number>> NUMERIC_STATS =
      Collections.synchronizedMap(new LinkedHashMap<String, RecordingStat<? extends Number>>());

  public static String normalizeName(String name) {
    return NOT_NAME_CHAR.matcher(name).replaceAll("_");
  }

  static String validateName(String name) {
    String normalized = normalizeName(name);
    if (!name.equals(normalized)) {
      LOG.warning("Invalid stat name " + name + " exported as " + normalized);
    }
    return normalized;
  }

  /**
   * A {@link StatsProvider} that exports gauge-style stats to the global {@link Stat}s repository
   * for time series tracking.
   */
  public static final StatsProvider STATS_PROVIDER = new StatsProvider() {
    @Override public <T extends Number> Stat<T> makeGauge(String name, final Supplier<T> gauge) {
      return Stats.export(new StatImpl<T>(name) {
        @Override public T read() {
          return gauge.get();
        }
      });
    }

    @Override public AtomicLong makeCounter(String name) {
      return Stats.exportLong(name);
    }

    @Override public RequestTimer makeRequestTimer(String name) {
      return new RequestStats(name);
    }
  };

  /**
   * A {@link StatRegistry} that provides stats registered with the global {@link Stat}s repository.
   */
  public static final StatRegistry STAT_REGISTRY = new StatRegistry() {
    @Override public Iterable<RecordingStat<? extends Number>> getStats() {
      return Stats.getNumericVariables();
    }
  };

  /**
   * Exports a stat for tracking.
   * if the stat provided implements the internal {@link RecordingStat} interface, it will be
   * registered for time series collection and returned.  If a {@link RecordingStat} with the same
   * name as the provided stat has already been exported, the previously-exported stat will be
   * returned and no additional registration will be performed.
   *
   * @param var The variable to export.
   * @param <T> The value exported by the variable.
   * @return A reference to the stat that was stored.  The stat returned may not be equal to the
   *    stat provided.  If a variable was already returned with the same
   */
  public static synchronized <T extends Number> Stat<T> export(Stat<T> var) {
    Preconditions.checkNotNull(var);
    MorePreconditions.checkNotBlank(var.getName());

    if (var instanceof RecordingStat) {
      String validatedName = validateName(var.getName());
      @SuppressWarnings("unchecked")
      Stat<T> stat = (Stat<T>) NUMERIC_STATS.get(validatedName);
      if (stat != null) {
        LOG.warning("Re-using already registered variable for key " + validatedName);
        return stat;
      } else {
        NUMERIC_STATS.put(validatedName, (RecordingStat<T>) var);

        exportStatic(var);
        return var;
      }
    } else {
      return export(new RecordingStatImpl<T>(var));
    }
  }

  /**
   * Exports a string stat.
   * String-based statistics will not be registered for time series collection.
   *
   * @param var Stat to export.
   * @return A reference back to {@code var}, or the variable that was already registered under the
   *    same name as {@code var}.
   */
  public static Stat<String> exportString(Stat<String> var) {
    return exportStatic(var);
  }

  /**
   * Adds a collection of stats for export.
   *
   * @param vars The variables to add.
   */
  public static void exportAll(Iterable<Stat<? extends Number>> vars) {
    for (Stat<? extends Number> var : vars) {
      export(var);
    }
  }

  /**
   * Exports an {@link AtomicInteger}, which will be included in time series tracking.
   *
   * @param name The name to export the stat with.
   * @param intVar The variable to export.
   * @return A reference to the {@link AtomicInteger} provided.
   */
  public static AtomicInteger export(final String name, final AtomicInteger intVar) {
    export(new SampledStat<Integer>(name, 0) {
      @Override public Integer doSample() { return intVar.get(); }
    });

    return intVar;
  }

  /**
   * Creates and exports an {@link AtomicInteger}.
   *
   * @param name The name to export the stat with.
   * @return A reference to the {@link AtomicInteger} created.
   */
  public static AtomicInteger exportInt(String name) {
    return exportInt(name, 0);
  }

  /**
   * Creates and exports an {@link AtomicInteger} with initial value.
   *
   * @param name The name to export the stat with.
   * @param initialValue The initial stat value.
   * @return A reference to the {@link AtomicInteger} created.
   */
  public static AtomicInteger exportInt(String name, int initialValue) {
    return export(name, new AtomicInteger(initialValue));
  }

  /**
   * Exports an {@link AtomicLong}, which will be included in time series tracking.
   *
   * @param name The name to export the stat with.
   * @param longVar The variable to export.
   * @return A reference to the {@link AtomicLong} provided.
   */
  public static AtomicLong export(String name, final AtomicLong longVar) {
    export(new StatImpl<Long>(name) {
      @Override public Long read() { return longVar.get(); }
    });

    return longVar;
  }

  /**
   * Creates and exports an {@link AtomicLong}.
   *
   * @param name The name to export the stat with.
   * @return A reference to the {@link AtomicLong} created.
   */
  public static AtomicLong exportLong(String name) {
    return exportLong(name, 0L);
  }

  /**
   * Creates and exports an {@link AtomicLong} with initial value.
   *
   * @param name The name to export the stat with.
   * @param initialValue The initial stat value.
   * @return A reference to the {@link AtomicLong} created.
   */
  public static AtomicLong exportLong(String name, long initialValue) {
    return export(name, new AtomicLong(initialValue));
  }

  /**
   * Exports a metric that tracks the size of a collection.
   *
   * @param name Name of the stat to export.
   * @param collection Collection whose size should be tracked.
   */
  public static void exportSize(String name, final Collection<?> collection) {
    export(new StatImpl<Integer>(name) {
      @Override public Integer read() {
        return collection.size();
      }
    });
  }

  /**
   * Exports a metric that tracks the size of a map.
   *
   * @param name Name of the stat to export.
   * @param map Map whose size should be tracked.
   */
  public static void exportSize(String name, final Map<?, ?> map) {
    export(new StatImpl<Integer>(name) {
      @Override public Integer read() {
        return map.size();
      }
    });
  }

  /**
   * Exports a metric that tracks the size of a cache.
   *
   * @param name Name of the stat to export.
   * @param cache Cache whose size should be tracked.
   */
  public static void exportSize(String name, final Cache<?, ?> cache) {
    export(new StatImpl<Long>(name) {
      @Override public Long read() {
        return cache.size();
      }
    });
  }

  /**
   * Exports a 'static' statistic, which will not be registered for time series tracking.
   *
   * @param var Variable to statically export.
   * @return A reference back to the provided {@link Stat}.
   */
  public static synchronized <T> Stat<T> exportStatic(Stat<T> var) {
    Preconditions.checkNotNull(var);
    MorePreconditions.checkNotBlank(var.getName());

    String validatedName = validateName(var.getName());
    @SuppressWarnings("unchecked")
    Stat<T> displaced = (Stat<T>) VAR_MAP.put(validatedName, var);
    if (displaced != null) {
      LOG.warning("Warning - exported variable collision on " + validatedName);
    }

    return var;
  }

  /**
   * Fetches all registered stat.
   *
   * @return An iterable of all registered stats.
   */
  public static Iterable<Stat> getVariables() {
    synchronized(VAR_MAP) {
      return ImmutableList.copyOf(VAR_MAP.values());
    }
  }

  static Iterable<RecordingStat<? extends Number>> getNumericVariables() {
    synchronized(NUMERIC_STATS) {
      return ImmutableList.copyOf(NUMERIC_STATS.values());
    }
  }

  @VisibleForTesting
  public static void flush() {
    VAR_MAP.clear();
    NUMERIC_STATS.clear();
  }

  @SuppressWarnings("unchecked")
  public static <T> Stat<T> getVariable(String name) {
    MorePreconditions.checkNotBlank(name);
    return VAR_MAP.get(name);
  }
}
