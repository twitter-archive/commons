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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import com.google.common.collect.Iterables;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;

/**
 * Convenience class to export statistics about the JVM.
 */
public class JvmStats {

  private static final long BYTES_PER_MB = Amount.of(1L, Data.MB).as(Data.BYTES);
  private static final double SECS_PER_NANO =
      ((double) 1) / Amount.of(1L, Time.SECONDS).as(Time.NANOSECONDS);

  private JvmStats() {
    // Utility class.
  }

  /**
   * Exports stats related to the JVM and runtime environment.
   */
  public static void export() {
    final OperatingSystemMXBean osMbean = ManagementFactory.getOperatingSystemMXBean();
    if (osMbean instanceof com.sun.management.OperatingSystemMXBean) {
      final com.sun.management.OperatingSystemMXBean sunOsMbean =
          (com.sun.management.OperatingSystemMXBean) osMbean;

      Stats.exportAll(
          ImmutableList.<Stat<? extends Number>>builder()
          .add(new StatImpl<Long>("system_free_physical_memory_mb") {
            @Override public Long read() {
              return sunOsMbean.getFreePhysicalMemorySize() / BYTES_PER_MB;
            }
          })
          .add(new StatImpl<Long>("system_free_swap_mb") {
            @Override public Long read() {
              return sunOsMbean.getFreeSwapSpaceSize() / BYTES_PER_MB;
            }
          })
          .add(
          Rate.of(
              new StatImpl<Long>("process_cpu_time_nanos") {
                @Override public Long read() {
                  return sunOsMbean.getProcessCpuTime();
                }
              }).withName("process_cpu_cores_utilized").withScaleFactor(SECS_PER_NANO).build())
          .build());
    }
    if (osMbean instanceof com.sun.management.UnixOperatingSystemMXBean) {
      final com.sun.management.UnixOperatingSystemMXBean unixOsMbean =
          (com.sun.management.UnixOperatingSystemMXBean) osMbean;

      Stats.exportAll(ImmutableList.<Stat<? extends Number>>builder()
          .add(new StatImpl<Long>("process_max_fd_count") {
            @Override public Long read() { return unixOsMbean.getMaxFileDescriptorCount(); }
          }).add(new StatImpl<Long>("process_open_fd_count") {
            @Override public Long read() { return unixOsMbean.getOpenFileDescriptorCount(); }
          }).build());
    }

    final Runtime runtime = Runtime.getRuntime();
    final ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

    Stats.exportAll(ImmutableList.<Stat<? extends Number>>builder()
      .add(new StatImpl<Long>("jvm_time_ms") {
        @Override public Long read() { return System.currentTimeMillis(); }
      })
      .add(new StatImpl<Integer>("jvm_available_processors") {
        @Override public Integer read() { return runtime.availableProcessors(); }
      })
      .add(new StatImpl<Long>("jvm_memory_free_mb") {
        @Override public Long read() { return runtime.freeMemory() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_max_mb") {
        @Override public Long read() { return runtime.maxMemory() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_mb_total") {
        @Override public Long read() { return runtime.totalMemory() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Integer>("jvm_class_loaded_count") {
        @Override public Integer read() { return classLoadingBean.getLoadedClassCount(); }
      })
      .add(new StatImpl<Long>("jvm_class_total_loaded_count") {
        @Override public Long read() { return classLoadingBean.getTotalLoadedClassCount(); }
      })
      .add(new StatImpl<Long>("jvm_class_unloaded_count") {
        @Override public Long read() { return classLoadingBean.getUnloadedClassCount(); }
      })
      .add(new StatImpl<Long>("jvm_gc_collection_time_ms") {
        @Override public Long read() {
          long collectionTimeMs = 0;
          for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectionTimeMs += bean.getCollectionTime();
          }
          return collectionTimeMs;
        }
      })
      .add(new StatImpl<Long>("jvm_gc_collection_count") {
        @Override public Long read() {
          long collections = 0;
          for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collections += bean.getCollectionCount();
          }
          return collections;
        }
      })
      .add(new StatImpl<Long>("jvm_memory_heap_mb_used") {
        @Override public Long read() {
          return memoryBean.getHeapMemoryUsage().getUsed() / BYTES_PER_MB;
        }
      })
      .add(new StatImpl<Long>("jvm_memory_heap_mb_committed") {
        @Override public Long read() {
          return memoryBean.getHeapMemoryUsage().getCommitted() / BYTES_PER_MB;
        }
      })
      .add(new StatImpl<Long>("jvm_memory_heap_mb_max") {
        @Override public Long read() {
          return memoryBean.getHeapMemoryUsage().getMax() / BYTES_PER_MB;
        }
      })
      .add(new StatImpl<Long>("jvm_memory_non_heap_mb_used") {
        @Override public Long read() {
          return memoryBean.getNonHeapMemoryUsage().getUsed() / BYTES_PER_MB;
        }
      })
      .add(new StatImpl<Long>("jvm_memory_non_heap_mb_committed") {
        @Override public Long read() {
          return memoryBean.getNonHeapMemoryUsage().getCommitted() / BYTES_PER_MB;
        }
      })
      .add(new StatImpl<Long>("jvm_memory_non_heap_mb_max") {
        @Override public Long read() {
          return memoryBean.getNonHeapMemoryUsage().getMax() / BYTES_PER_MB;
        }
      })
      .add(new StatImpl<Long>("jvm_uptime_secs") {
        @Override public Long read() { return runtimeMXBean.getUptime() / 1000; }
      })
      .add(new StatImpl<Double>("system_load_avg") {
        @Override public Double read() { return osMbean.getSystemLoadAverage(); }
      })
      .add(new StatImpl<Integer>("jvm_threads_peak") {
        @Override public Integer read() { return threads.getPeakThreadCount(); }
      })
      .add(new StatImpl<Long>("jvm_threads_started") {
        @Override public Long read() { return threads.getTotalStartedThreadCount(); }
      })
      .add(new StatImpl<Integer>("jvm_threads_daemon") {
        @Override public Integer read() { return threads.getDaemonThreadCount(); }
      })
      .add(new StatImpl<Integer>("jvm_threads_active") {
        @Override public Integer read() { return threads.getThreadCount(); }
      })
      .build());

    // Export per memory pool gc time and cycle count like Ostrich
    // This is based on code in Bridcage: https://cgit.twitter.biz/birdcage/tree/ \
    // ostrich/src/main/scala/com/twitter/ostrich/stats/StatsCollection.scala
    Stats.exportAll(Iterables.transform(ManagementFactory.getGarbageCollectorMXBeans(),
        new Function<GarbageCollectorMXBean, Stat<? extends Number>>(){
          @Override
          public Stat<? extends Number> apply(final GarbageCollectorMXBean gcMXBean) {
            return new StatImpl<Long>(
                "jvm_gc_" + Stats.normalizeName(gcMXBean.getName()) + "_collection_count") {
              @Override public Long read() {
                return gcMXBean.getCollectionCount();
              }
            };
          }
        }
    ));

    Stats.exportAll(Iterables.transform(ManagementFactory.getGarbageCollectorMXBeans(),
        new Function<GarbageCollectorMXBean, Stat<? extends Number>>(){
          @Override
          public Stat<? extends Number> apply(final GarbageCollectorMXBean gcMXBean) {
            return new StatImpl<Long>(
                "jvm_gc_" + Stats.normalizeName(gcMXBean.getName()) + "_collection_time_ms") {
              @Override public Long read() {
                return gcMXBean.getCollectionTime();
              }
            };
          }
        }
    ));

    Stats.exportString(
        new StatImpl<String>("jvm_input_arguments") {
          @Override public String read() {
            return runtimeMXBean.getInputArguments().toString();
          }
        }
    );

    for (final String property : System.getProperties().stringPropertyNames()) {
      Stats.exportString(
          new StatImpl<String>("jvm_prop_" + Stats.normalizeName(property)) {
            @Override public String read() { return System.getProperty(property); }
          });
    }

    for (final Map.Entry<String, String> environmentVariable : System.getenv().entrySet()) {
      Stats.exportString(
          new StatImpl<String>("system_env_" + Stats.normalizeName(environmentVariable.getKey())) {
            @Override public String read() { return environmentVariable.getValue(); }
          });
    }
  }
}
