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

package com.twitter.common.util.caching;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A proxy class that handles caching of return values for method calls to a wrapped object.
 *
 * Example usage:
 *
 *   Foo uncached = new Foo();
 *   CachingMethodProxy<Foo> methodProxy = CachingMethodProxy.proxyFor(uncached, Foo.class);
 *   Foo foo = methodProxy.getCachingProxy();
 *   methodProxy.cache(foo.doBar(), lruCache1)
 *              .cache(foo.doBaz(), lruCache2)
 *              .prepare();
 *
 * @author William Farner
 */
public class CachingMethodProxy<T> {

  // Dummy return values to return when in recording state.
  private static final Map<Class<?>, Object> EMPTY_RETURN_VALUES =
      ImmutableMap.<Class<?>, Object>builder()
      .put(Boolean.TYPE, Boolean.FALSE)
      .put(Byte.TYPE, Byte.valueOf((byte) 0))
      .put(Short.TYPE, Short.valueOf((short) 0))
      .put(Character.TYPE, Character.valueOf((char)0))
      .put(Integer.TYPE, Integer.valueOf(0))
      .put(Long.TYPE, Long.valueOf(0))
      .put(Float.TYPE, Float.valueOf(0))
      .put(Double.TYPE, Double.valueOf(0))
      .build();
  private static final Map<Class<?>, Class<?>> AUTO_BOXING_MAP =
      ImmutableMap.<Class<?>, Class<?>>builder()
      .put(Boolean.TYPE, Boolean.class)
      .put(Byte.TYPE, Byte.class)
      .put(Short.TYPE, Short.class)
      .put(Character.TYPE, Character.class)
      .put(Integer.TYPE, Integer.class)
      .put(Long.TYPE, Long.class)
      .put(Float.TYPE, Float.class)
      .put(Double.TYPE, Double.class)
      .build();

  // The uncached resource, whose method calls are deemed to be expensive and cacheable.
  private final T uncached;

  // The methods that are cached, and the caches themselves.
  private final Map<Method, MethodCache> methodCaches = Maps.newHashMap();
  private final Class<T> type;

  private Method lastMethodCall = null;
  private boolean recordMode = true;

  /**
   * Creates a new caching method proxy that will wrap an object and cache for the provided methods.
   *
   * @param uncached The uncached object that will be reverted to when a cache entry is not present.
   */
  private CachingMethodProxy(T uncached, Class<T> type) {
    this.uncached = Preconditions.checkNotNull(uncached);
    this.type = Preconditions.checkNotNull(type);
    Preconditions.checkArgument(type.isInterface(), "The proxied type must be an interface.");
  }

  private static Object invokeMethod(Object subject, Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(subject, args);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot access " + subject.getClass() + "." + method, e);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /**
   * A cached method and its caching control structures.
   *
   * @param <K> Cache key type.
   * @param <V> Cache value type, expected to match the return type of the method.
   */
  private static class MethodCache<K, V> {
    private final Method method;
    private final Cache<K, V> cache;
    private final Function<Object[], K> keyBuilder;
    private final Predicate<V> entryFilter;

    MethodCache(Method method, Cache<K, V> cache, Function<Object[], K> keyBuilder,
        Predicate<V> entryFilter) {
      this.method = method;
      this.cache = cache;
      this.keyBuilder = keyBuilder;
      this.entryFilter = entryFilter;
    }

    V doInvoke(Object uncached, Object[] args) throws Throwable {
      K key = keyBuilder.apply(args);

      V cachedValue = cache.get(key);

      if (cachedValue != null) return cachedValue;

      Object fetched = invokeMethod(uncached, method, args);

      if (fetched == null) return null;

      @SuppressWarnings("unchecked")
      V typedValue = (V) fetched;

      if (entryFilter.apply(typedValue)) cache.put(key, typedValue);

      return typedValue;
    }
  }

  /**
   * Creates a new builder for the given type.
   *
   * @param uncached The uncached object that should be insulated by caching.
   * @param type The interface that a proxy should be created for.
   * @param <T> Type parameter to the proxied class.
   * @return A new builder.
   */
  public static <T> CachingMethodProxy<T> proxyFor(T uncached, Class<T> type) {
    return new CachingMethodProxy<T>(uncached, type);
  }

  @SuppressWarnings("unchecked")
  public T getCachingProxy() {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[] { type },
        new InvocationHandler() {
          @Override public Object invoke(Object proxy, Method method, Object[] args)
              throws Throwable {
            return doInvoke(method, args);
          }
        });
  }

  private Object doInvoke(Method method, Object[] args) throws Throwable {
    return recordMode ? recordCall(method) : cacheRequest(method, args);
  }

  private Object recordCall(Method method) {
    Preconditions.checkArgument(method.getReturnType() != Void.TYPE,
        "Void return methods cannot be cached: " + method);
    Preconditions.checkArgument(method.getParameterTypes().length > 0,
        "Methods with zero arguments cannot be cached: " + method);
    Preconditions.checkState(lastMethodCall == null,
        "No cache instructions provided for call to: " + lastMethodCall);

    lastMethodCall = method;

    Class<?> returnType = method.getReturnType();
    return returnType.isPrimitive() ? EMPTY_RETURN_VALUES.get(returnType) : null;
  }

  private Object cacheRequest(Method method, Object[] args) throws Throwable {
    MethodCache cache = methodCaches.get(method);

    // Check if we are caching for this method.
    if (cache == null) return invokeMethod(uncached, method, args);

    return cache.doInvoke(uncached, args);
  }

  /**
   * Instructs the proxy that cache setup is complete, and the proxy instance should begin caching
   * and delegating uncached calls.  After this is called, any subsequent calls to any of the
   * cache setup methods will result in an {@link IllegalStateException}.
   */
  public void prepare() {
    Preconditions.checkState(!methodCaches.isEmpty(), "At least one method must be cached.");
    Preconditions.checkState(recordMode, "prepare() may only be invoked once.");

    recordMode = false;
  }

  public <V> CachingMethodProxy<T> cache(V value, Cache<List, V> cache) {
    return cache(value, cache, Predicates.<V>alwaysTrue());
  }

  public <V> CachingMethodProxy<T> cache(V value, Cache<List, V> cache,
      Predicate<V> valueFilter) {
    return cache(value, cache, DEFAULT_KEY_BUILDER, valueFilter);
  }

  public <K, V> CachingMethodProxy<T> cache(V value, Cache<K, V> cache,
      Function<Object[], K> keyBuilder) {
    // Get the last method call and declare it the cached method.
    return cache(value, cache, keyBuilder, Predicates.<V>alwaysTrue());
  }

  public <K, V> CachingMethodProxy<T> cache(V value, Cache<K, V> cache,
      Function<Object[], K> keyBuilder, Predicate<V> valueFilter) {
    Preconditions.checkNotNull(cache);
    Preconditions.checkNotNull(keyBuilder);
    Preconditions.checkNotNull(valueFilter);

    Preconditions.checkState(recordMode, "Cache setup is not allowed after prepare() is called.");

    // Get the last method call and declare it the cached method.
    Preconditions.checkState(lastMethodCall != null, "No method call captured to be cached.");

    Class<?> returnType = lastMethodCall.getReturnType();

    Preconditions.checkArgument(returnType != Void.TYPE,
        "Cannot cache results from void method: " + lastMethodCall);

    if (returnType.isPrimitive()) {
      // If a primitive type is returned, we need to make sure that the cache holds the boxed
      // type for the primitive.
      returnType = AUTO_BOXING_MAP.get(returnType);
    }

    // TODO(William Farner): Figure out a simple way to make this possible.  Right now, since the proxy
    //    objects return null, we get a null here and can't check the type.
    //Preconditions.checkArgument(value.getClass() == returnType,
    //    String.format("Cache value type '%s' does not match method return type '%s'",
    //        value.getClass(), lastMethodCall.getReturnType()));

    methodCaches.put(lastMethodCall, new MethodCache<K, V>(lastMethodCall, cache, keyBuilder,
        valueFilter));

    lastMethodCall = null;

    return this;
  }

  private static final Function<Object[], List> DEFAULT_KEY_BUILDER =
      new Function<Object[], List>() {
        @Override public List apply(Object[] args) {
          return Arrays.asList(args);
        }
      };
}
