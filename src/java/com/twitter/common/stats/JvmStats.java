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

import com.google.common.collect.ImmutableList;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.Arrays;

/**
 * Convenience class to export statistics about the JVM.
 *
 * TODO(William Farner): Some of these are fixed values, make sure to export them so that they are not
 * collected as time series.
 *
 * @author William Farner
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

    final Runtime runtime = Runtime.getRuntime();
    final ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    final MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    final MemoryUsage nonHeapUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

    Stats.exportAll(ImmutableList.<Stat<? extends Number>>builder()
      .add(new StatImpl<Long>("jvm_time_ms") {
        @Override public Long read() { return System.currentTimeMillis(); }
      })
      .add(new StatImpl<Integer>("jvm_threads_active") {
        @Override public Integer read() { return Thread.activeCount(); }
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
        @Override public Long read() { return heapUsage.getUsed() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_heap_mb_committed") {
        @Override public Long read() { return heapUsage.getCommitted() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_heap_mb_max") {
        @Override public Long read() { return heapUsage.getMax() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_non_heap_mb_used") {
        @Override public Long read() { return nonHeapUsage.getUsed() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_non_heap_mb_committed") {
        @Override public Long read() { return nonHeapUsage.getCommitted() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_memory_non_heap_mb_max") {
        @Override public Long read() { return nonHeapUsage.getMax() / BYTES_PER_MB; }
      })
      .add(new StatImpl<Long>("jvm_uptime_secs") {
        @Override public Long read() {
          return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        }
      })
      .add(new StatImpl<Double>("system_load_avg") {
        @Override public Double read() { return osMbean.getSystemLoadAverage(); }
      })
    .build());

    Stats.exportString(
        new StatImpl<String>("jvm_input_arguments") {
          @Override public String read() {
            return ManagementFactory.getRuntimeMXBean().getInputArguments().toString();
          }
        }
    );

    for (final String property : System.getProperties().stringPropertyNames()) {
      Stats.exportString(
          new StatImpl<String>("jvm_prop_" + Stats.normalizeName(property)) {
            @Override public String read() { return System.getProperty(property); }
          });
    }
  }
}
