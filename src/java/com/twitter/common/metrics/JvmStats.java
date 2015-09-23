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

package com.twitter.common.metrics;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Primitives;
import com.sun.management.UnixOperatingSystemMXBean;

/**
 * Export a number of standard statistics for the JVM and system.
 */
public final class JvmStats {

  private JvmStats() {
    // Utility class.
  }

  private static String normalizeName(String name) {
    return name.replaceAll("\\W+", "_");
  }

  /**
   * Use reflection to get optional gauges that only work in Java 1.7.
   */
  private static Multimap<String, AbstractGauge<Long>> buildJava17Gauges() {
    Multimap<String, AbstractGauge<Long>> gauges = ArrayListMultimap.create();

    try {
      Class<?> bufferPoolMXBean =
          ClassLoader.getSystemClassLoader().loadClass("java.lang.management.BufferPoolMXBean");
      Method getPlatformMxBeans =
          ManagementFactory.class.getMethod("getPlatformMXBeans", Class.class);
      @SuppressWarnings("unchecked")
      List<Object> beans = (List<Object>) getPlatformMxBeans.invoke(null, bufferPoolMXBean);
      for (Object pool : beans) {
        @SuppressWarnings("unchecked")
        String name = (String) bufferPoolMXBean.getMethod("getName").invoke(pool);

        gauges.put(name, reflectMethodToGauge("count", bufferPoolMXBean, "getCount", pool));
        gauges.put(name, reflectMethodToGauge("used", bufferPoolMXBean, "getMemoryUsed", pool));
        gauges.put(name, reflectMethodToGauge("max", bufferPoolMXBean, "getTotalCapacity", pool));
      }
    } catch (ClassNotFoundException ex) {
      // If any of the above reflection fails, it's ok.  These gauges just won't be present.
    } catch (NoSuchMethodException ex) {
      // ditto
    } catch (IllegalAccessException ex) {
      // same
    } catch (InvocationTargetException ex) {
      // etc.
    }

    return gauges;
  }

  /**
   * Helper method to add a gauge via reflection on an mx bean.
   */
  private static AbstractGauge<Long> reflectMethodToGauge(
      String gaugeName, Class<?> clazz, String methodName, final Object arg)
      throws NoSuchMethodException, IllegalArgumentException {
    final Method method = clazz.getMethod(methodName);
    if (!Long.class.isAssignableFrom(Primitives.wrap(method.getReturnType()))) {
      throw new IllegalArgumentException(
          "mx bean method " + methodName + " can't be stored as Long metric");
    }
    AbstractGauge<Long> gauge = new AbstractGauge<Long>(gaugeName) {
      @Override
      public Long read() {
        try {
          return (Long) method.invoke(arg);
        } catch (IllegalAccessException ex) {
          return 0L;
        } catch (InvocationTargetException ex) {
          return 0L;
        }
      }
    };
    return gauge;
  }

  private interface MemoryReporter {
    MemoryUsage getUsage();
  }

  private static void registerMemoryStats(final MetricRegistry stats, final MemoryReporter mem) {
    if (mem.getUsage() != null) {
      stats.register(new AbstractGauge<Long>("committed") {
        @Override public Long read() {
          return mem.getUsage().getCommitted();
        }
      });
      stats.register(new AbstractGauge<Long>("max") {
        @Override public Long read() {
          return mem.getUsage().getMax();
        }
      });
      stats.register(new AbstractGauge<Long>("used") {
        @Override public Long read() {
          return mem.getUsage().getUsed();
        }
      });
    }
  }

  /**
   * Add a series of system and jvm-level stats to the given registry.
   */
  public static void register(MetricRegistry registry) {
    final MetricRegistry stats = registry.scope("jvm");
    final MemoryMXBean mem = ManagementFactory.getMemoryMXBean();

    // memory stats
    final MetricRegistry heapRegistry = stats.scope("heap");
    registerMemoryStats(heapRegistry, new MemoryReporter() {
      @Override public MemoryUsage getUsage() {
        return mem.getHeapMemoryUsage();
      }
    });
    final MetricRegistry nonHeapRegistry = stats.scope("nonheap");
    registerMemoryStats(nonHeapRegistry, new MemoryReporter() {
      @Override public MemoryUsage getUsage() {
        return mem.getNonHeapMemoryUsage();
      }
    });

    // threads
    final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    final MetricRegistry threadRegistry = stats.scope("thread");
    threadRegistry.register(new AbstractGauge<Integer>("daemon_count") {
      @Override public Integer read() {
        return threads.getDaemonThreadCount();
      }
    });
    threadRegistry.register(new AbstractGauge<Integer>("count") {
      @Override public Integer read() {
        return threads.getThreadCount();
      }
    });
    threadRegistry.register(new AbstractGauge<Integer>("peak_count") {
      @Override public Integer read() {
        return threads.getPeakThreadCount();
      }
    });

    // class loading bean
    final ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    stats.register(new AbstractGauge<Integer>("classes_loaded") {
      @Override public Integer read() {
        return classLoadingBean.getLoadedClassCount();
      }
    });
    stats.register(new AbstractGauge<Long>("total_classes_loaded") {
      @Override public Long read() {
        return classLoadingBean.getTotalLoadedClassCount();
      }
    });
    stats.register(new AbstractGauge<Long>("classes_unloaded") {
      @Override public Long read() {
        return classLoadingBean.getUnloadedClassCount();
      }
    });

    // runtime
    final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    stats.register(new AbstractGauge<Long>("start_time") {
      @Override public Long read() {
        return runtime.getStartTime();
      }
    });
    stats.register(new AbstractGauge<Long>("uptime") {
      @Override public Long read() {
        return runtime.getUptime();
      }
    });
    //stats.register(new AbstractGauge<String>)

    // os
    final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    stats.register(new AbstractGauge<Integer>("num_cpus") {
      @Override public Integer read() {
        return os.getAvailableProcessors();
      }
    });
    if (os instanceof com.sun.management.OperatingSystemMXBean) {
      final com.sun.management.OperatingSystemMXBean sunOsMbean =
          (com.sun.management.OperatingSystemMXBean) os;

      // if this is indeed an operating system
      stats.register(new AbstractGauge<Long>("free_physical_memory") {
        @Override public Long read() {
          return sunOsMbean.getFreePhysicalMemorySize();
        }
      });
      stats.register(new AbstractGauge<Long>("free_swap") {
        @Override public Long read() {
          return sunOsMbean.getFreeSwapSpaceSize();
        }
      });
      stats.register(new AbstractGauge<Long>("process_cpu_time") {
        @Override public Long read() {
          return sunOsMbean.getProcessCpuTime();
        }
      });
    }
    if (os instanceof com.sun.management.UnixOperatingSystemMXBean) {
      // it's a unix system... I know this!
      final UnixOperatingSystemMXBean unix = (UnixOperatingSystemMXBean) os;
      stats.register(new AbstractGauge<Long>("fd_count") {
        @Override public Long read() {
          return unix.getOpenFileDescriptorCount();
        }
      });
      stats.register(new AbstractGauge<Long>("fd_limit") {
        @Override public Long read() {
          return unix.getMaxFileDescriptorCount();
        }
      });
    }

    // mem
    final List<MemoryPoolMXBean> memPool = ManagementFactory.getMemoryPoolMXBeans();
    final MetricRegistry memRegistry = stats.scope("mem");
    final MetricRegistry currentMem = memRegistry.scope("current");
    final MetricRegistry postGCRegistry = memRegistry.scope("postGC");
    for (final MemoryPoolMXBean pool : memPool) {
      String name = normalizeName(pool.getName());
      registerMemoryStats(currentMem.scope(name), new MemoryReporter() {
        @Override public MemoryUsage getUsage() {
          return pool.getUsage();
        }
      });
      registerMemoryStats(postGCRegistry.scope(name), new MemoryReporter() {
        @Override public MemoryUsage getUsage() {
          return pool.getCollectionUsage();
        }
      });
    }
    currentMem.register(new AbstractGauge<Long>("used") {
      @Override
      public Long read() {
        long sum = 0;
        for (MemoryPoolMXBean pool : memPool) {
          MemoryUsage usage = pool.getUsage();
          if (usage != null) {
            sum += usage.getUsed();
          }
        }
        return sum;
      }
    });
    AbstractGauge<Long> totalPostGCGauge = new AbstractGauge<Long>("used") {
      @Override
      public Long read() {
        long sum = 0;
        for (MemoryPoolMXBean pool : memPool) {
          MemoryUsage usage = pool.getCollectionUsage();
          if (usage != null) {
            sum += usage.getUsed();
          }
        }
        return sum;
      }
    };
    postGCRegistry.register(totalPostGCGauge);

    // java 1.7 specific buffer pool gauges
    Multimap<String, AbstractGauge<Long>> java17gauges = buildJava17Gauges();
    if (!java17gauges.isEmpty()) {
      MetricRegistry bufferRegistry = stats.scope("buffer");
      for (String scope : java17gauges.keySet()) {
        MetricRegistry pool = bufferRegistry.scope(scope);
        for (AbstractGauge<Long> gauge : java17gauges.get(scope)) {
          pool.register(gauge);
        }
      }
    }

    // gc
    final List<GarbageCollectorMXBean> gcPool = ManagementFactory.getGarbageCollectorMXBeans();
    MetricRegistry gcRegistry = stats.scope("gc");
    for (final GarbageCollectorMXBean gc : gcPool) {
      String name = normalizeName(gc.getName());
      MetricRegistry scoped = memRegistry.scope(name);
      scoped.register(new AbstractGauge<Long>("cycles") {
        @Override public Long read() {
          return gc.getCollectionCount();
        }
      });
      scoped.register(new AbstractGauge<Long>("msec") {
        @Override public Long read() {
          return gc.getCollectionTime();
        }
      });
    }

    gcRegistry.register(new AbstractGauge<Long>("cycles") {
      @Override
      public Long read() {
        long sum = 0;
        for (GarbageCollectorMXBean pool : gcPool) {
          long count = pool.getCollectionCount();
          if (count > 0) {
            sum += count;
          }
        }
        return sum;
      }
    });
    gcRegistry.register(new AbstractGauge<Long>("msec") {
      @Override
      public Long read() {
        long sum = 0;
        for (GarbageCollectorMXBean pool : gcPool) {
          long msec = pool.getCollectionTime();
          if (msec > 0) {
            sum += msec;
          }
        }
        return sum;
      }
    });
  }
}
