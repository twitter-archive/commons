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

import com.google.common.base.Predicate;
import org.easymock.IMocksControl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class CachingMethodProxyTest {

  private CachingMethodProxy<Math> proxyBuilder;
  private Math uncachedMath;
  private Math cachedMath;
  private Cache<List, Integer> intCache;
  private Predicate<Integer> intFilter;

  private IMocksControl control;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    control = createControl();
    uncachedMath = control.createMock(Math.class);
    intCache = control.createMock(Cache.class);
    intFilter = control.createMock(Predicate.class);

    proxyBuilder = CachingMethodProxy.proxyFor(uncachedMath, Math.class);
    cachedMath = proxyBuilder.getCachingProxy();
  }

  @After
  public void verifyControl() {
    control.verify();
  }

  @Test
  public void testCaches() throws Exception {
    expectUncachedAdd(1, 2, true);
    expectUncachedAdd(3, 4, true);
    expect(intCache.get(Arrays.asList(1, 2))).andReturn(3);
    expect(intCache.get(Arrays.asList(3, 4))).andReturn(7);

    control.replay();

    proxyBuilder.cache(cachedMath.sum(0, 0), intCache, intFilter)
        .prepare();
    assertThat(cachedMath.sum(1, 2), is(3));
    assertThat(cachedMath.sum(3, 4), is(7));
    assertThat(cachedMath.sum(1, 2), is(3));
    assertThat(cachedMath.sum(3, 4), is(7));
  }

  @Test
  public void testIgnoresUncachedMethod() throws Exception {
    expect(uncachedMath.sub(2, 1)).andReturn(1);
    expect(uncachedMath.sub(2, 1)).andReturn(1);

    control.replay();

    proxyBuilder.cache(cachedMath.sum(0, 0), intCache, intFilter)
        .prepare();
    assertThat(cachedMath.sub(2, 1), is(1));
    assertThat(cachedMath.sub(2, 1), is(1));
  }

  @Test
  public void testFilterValue() throws Exception {
    expectUncachedAdd(1, 2, true);
    expectUncachedAdd(3, 4, false);
    expect(intCache.get(Arrays.asList(1, 2))).andReturn(3);

    control.replay();

    proxyBuilder.cache(cachedMath.sum(0, 0), intCache, intFilter)
        .prepare();
    assertThat(cachedMath.sum(1, 2), is(3));
    assertThat(cachedMath.sum(3, 4), is(7));
    assertThat(cachedMath.sum(1, 2), is(3));
  }

  @Test(expected = IllegalStateException.class)
  public void testRequiresOneCache() throws Exception {
    control.replay();

    proxyBuilder.prepare();
  }

  @Test
  public void testExceptionThrown() throws Exception {
    List<Integer> args = Arrays.asList(1, 2);
    expect(intCache.get(args)).andReturn(null);

    Math.AddException thrown = new Math.AddException();
    expect(uncachedMath.sum(1, 2)).andThrow(thrown);

    control.replay();

    proxyBuilder.cache(cachedMath.sum(0, 0), intCache, intFilter)
            .prepare();
    try {
      cachedMath.sum(1, 2);
    } catch (Math.AddException e) {
      assertSame(e, thrown);
    }
  }

  /* TODO(William Farner): Re-enable once the TODO for checking return value/cache value types is done.
  @Test(expected = IllegalArgumentException.class)
  public void testCacheValueAndMethodReturnTypeMismatch() throws Exception {
    control.replay();

    cachedMath.addDouble(0, 0);
    proxyBuilder.cache(1, intCache, intFilter)
        .prepare();
  }
  */

  @Test(expected = IllegalStateException.class)
  public void testRejectsCacheSetupAfterPrepare() throws Exception {
    control.replay();

    proxyBuilder.cache(cachedMath.sum(0, 0), intCache, intFilter)
        .prepare();
    proxyBuilder.cache(null, intCache, intFilter);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testIgnoresNullValues() throws Exception {
    // Null return values should not even be considered for entry into the cache, and therefore
    // should not be passed to the filter.

    Cache<List, Math> crazyCache = control.createMock(Cache.class);
    Predicate<Math> crazyFilter = control.createMock(Predicate.class);

    expect(crazyCache.get(Arrays.asList(null, null))).andReturn(null);
    expect(uncachedMath.crazyMath(null, null)).andReturn(null);

    control.replay();

    proxyBuilder.cache(cachedMath.crazyMath(null, null), crazyCache, crazyFilter)
        .prepare();

    cachedMath.crazyMath(null, null);
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("unchecked")
  public void testRejectsVoidReturn() throws Exception {
    Cache<List, Void> voidCache = control.createMock(Cache.class);
    Predicate<Void> voidFilter = control.createMock(Predicate.class);

    control.replay();

    cachedMath.doSomething(null);
    proxyBuilder.cache(null, voidCache, voidFilter);
  }

  @Test(expected = IllegalStateException.class)
  @SuppressWarnings("unchecked")
  public void testFailsNoCachedCall() throws Exception {
    Cache<List, Void> voidCache = control.createMock(Cache.class);
    Predicate<Void> voidFilter = control.createMock(Predicate.class);

    control.replay();

    // No method call was recorded on the proxy, so the builder doesn't know what to cache.
    proxyBuilder.cache(null, voidCache, voidFilter);
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("unchecked")
  public void testRejectsZeroArgMethods() throws Exception {
    Cache<List, Math> mathCache = control.createMock(Cache.class);
    Predicate<Math> mathFilter = control.createMock(Predicate.class);

    control.replay();

    proxyBuilder.cache(cachedMath.doNothing(), mathCache, mathFilter);
  }

  @Test
  public void testAllowsSuperclassMethod() throws Exception {
    SubMath subMath = control.createMock(SubMath.class);

    List<Integer> args = Arrays.asList(1, 2);
    expect(intCache.get(args)).andReturn(null);
    expect(subMath.sum(1, 2)).andReturn(3);
    expect(intFilter.apply(3)).andReturn(true);
    intCache.put(args, 3);

    control.replay();

    Method add = SubMath.class.getMethod("sum", int.class, int.class);

    CachingMethodProxy<SubMath> proxyBuilder = CachingMethodProxy.proxyFor(subMath, SubMath.class);
    SubMath cached = proxyBuilder.getCachingProxy();
    proxyBuilder.cache(cached.sum(0, 0), intCache, intFilter)
        .prepare();

    cached.sum(1, 2);
  }

  private void expectUncachedAdd(int a, int b, boolean addToCache) throws Math.AddException {
    List<Integer> args = Arrays.asList(a, b);
    expect(intCache.get(args)).andReturn(null);
    expect(uncachedMath.sum(a, b)).andReturn(a + b);
    expect(intFilter.apply(a + b)).andReturn(addToCache);
    if (addToCache) intCache.put(args, a  + b);
  }

  private interface Math {
    public int sum(int a, int b) throws AddException;

    public double addDouble(double a, double b) throws AddException;

    public int sub(int a, int b);

    public Math crazyMath(Math a, Math b);

    public Math doNothing();

    public void doSomething(Math a);

    class AddException extends Exception {}
  }

  private interface SubMath extends Math {
    public int otherSum(int a, int b);
  }
}
