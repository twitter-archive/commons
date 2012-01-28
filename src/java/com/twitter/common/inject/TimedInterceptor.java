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

package com.twitter.common.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Binder;
import com.google.inject.matcher.Matchers;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang.StringUtils;

import com.twitter.common.stats.SlidingStats;
import com.twitter.common.stats.TimeSeriesRepository;

/**
 * A method interceptor that exports timing information for methods annotated with
 * {@literal @Timed}.
 *
 * @author John Sirois
 */
public final class TimedInterceptor implements MethodInterceptor {

  /**
   * Marks a method as a target for timing.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Timed {

    /**
     * The base name to export timing data with; empty to use the annotated method's name.
     */
    String value() default "";
  }

  private final LoadingCache<Method, SlidingStats> stats =
      CacheBuilder.newBuilder().build(new CacheLoader<Method, SlidingStats>() {
        @Override public SlidingStats load(Method method) {
          return createStats(method);
        }
      });

  private TimedInterceptor() {
    // preserve for guice
  }

  private SlidingStats createStats(Method method) {
    Timed timed = method.getAnnotation(Timed.class);
    Preconditions.checkArgument(timed != null,
        "TimedInterceptor can only be applied to @Timed methods");

    String name = timed.value();
    String statName = !StringUtils.isEmpty(name) ? name : method.getName();
    return new SlidingStats(statName, "nanos");
  }

  @Override
  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    // TODO(John Sirois): consider including a SlidingRate tracking thrown exceptions
    SlidingStats stat = stats.get(methodInvocation.getMethod());
    long start = System.nanoTime();
    try {
      return methodInvocation.proceed();
    } finally {
      stat.accumulate(System.nanoTime() - start);
    }
  }

  /**
   * Installs an interceptor in a guice {@link com.google.inject.Injector}, enabling
   * {@literal @Timed} method interception in guice-provided instances.  Requires that a
   * {@link TimeSeriesRepository} is bound elsewhere.
   *
   * @param binder a guice binder to require bindings against
   */
  public static void bind(Binder binder) {
    Preconditions.checkNotNull(binder);

    Bindings.requireBinding(binder, TimeSeriesRepository.class);

    TimedInterceptor interceptor = new TimedInterceptor();
    binder.requestInjection(interceptor);
    binder.bindInterceptor(Matchers.any(), Matchers.annotatedWith(Timed.class), interceptor);
  }
}
