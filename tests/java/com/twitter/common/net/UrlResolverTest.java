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

package com.twitter.common.net;

import com.twitter.common.base.ExceptionalFunction;
import com.twitter.common.net.UrlResolver.ResolvedUrl;
import com.twitter.common.net.UrlResolver.ResolvedUrl.EndState;
import com.twitter.common.util.BackoffStrategy;
import com.twitter.common.util.Clock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static com.google.common.testing.junit4.JUnitAsserts.assertContentsInOrder;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author John Sirois
 */
public class UrlResolverTest {
  private IMocksControl control;
  private ExceptionalFunction<String, String, IOException> resolver;
  private Clock clock;
  private BackoffStrategy backoffStrategy;

  @Before
  public void setUp() throws Exception {
    control = createControl();

    @SuppressWarnings("unchecked")
    ExceptionalFunction<String, String, IOException> resolver =
        control.createMock(ExceptionalFunction.class);
    this.resolver = resolver;
    this.clock = control.createMock(Clock.class);
    this.backoffStrategy = control.createMock(BackoffStrategy.class);
  }

  @Test
  public void testResolveUrlResolved() throws Exception {
    expect(resolver.apply("jake")).andReturn("jake");
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(3).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder("Expected no intermediate urls", resolvedUrl.getIntermediateUrls());
    assertNull(resolvedUrl.getEndUrl());
    assertEquals(EndState.REACHED_LANDING, resolvedUrl.getEndState());

    control.verify();
  }

  @Test
  public void testResolveUrlSingleRedirect() throws Exception {
    expect(resolver.apply("jake")).andReturn("joe");
    expect(resolver.apply("joe")).andReturn("joe");
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(3).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder("Expected no intermediate urls", resolvedUrl.getIntermediateUrls());
    assertEquals("joe", resolvedUrl.getEndUrl());
    assertEquals(EndState.REACHED_LANDING, resolvedUrl.getEndState());

    control.verify();
  }

  @Test
  public void testResolveUrlMultipleRedirects() throws Exception {
    expect(resolver.apply("jake")).andReturn("joe");
    expect(resolver.apply("joe")).andReturn("bill");
    expect(resolver.apply("bill")).andReturn("bob");
    expect(resolver.apply("bob")).andReturn("fred");
    expect(resolver.apply("fred")).andReturn("fred");
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(5).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder(resolvedUrl.getIntermediateUrls(), "joe", "bill", "bob");
    assertEquals("fred", resolvedUrl.getEndUrl());
    assertEquals(EndState.REACHED_LANDING, resolvedUrl.getEndState());

    control.verify();
  }

  @Test
  public void testResolveUrlRedirectLimit() throws Exception {
    expect(resolver.apply("jake")).andReturn("joe");
    expect(resolver.apply("joe")).andReturn("bill");
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(2).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder(resolvedUrl.getIntermediateUrls(), "joe");
    assertEquals("bill", resolvedUrl.getEndUrl());
    assertEquals(EndState.REDIRECT_LIMIT, resolvedUrl.getEndState());

    control.verify();
  }

  @Test
  public void testResolveUrlResolveError() throws Exception {
    expect(resolver.apply("jake")).andThrow(new IOException());
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(3).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder("Expected no intermediate urls", resolvedUrl.getIntermediateUrls());
    assertNull(resolvedUrl.getEndUrl());
    assertEquals(EndState.ERROR, resolvedUrl.getEndState());

    control.verify();
  }

  @Test
  public void testResolveUrlResolveErrorCode() throws Exception {
    expect(resolver.apply("jake")).andReturn(null);
    expect(backoffStrategy.calculateBackoffMs(0L)).andReturn(1L);
    clock.waitFor(1L);

    expect(resolver.apply("jake")).andReturn(null);
    expect(backoffStrategy.calculateBackoffMs(1L)).andReturn(2L);
    clock.waitFor(2L);

    expect(resolver.apply("jake")).andReturn(null);
    // we shouldn't back off after the last attempt
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(3).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder("Expected no intermediate urls", resolvedUrl.getIntermediateUrls());
    assertNull(resolvedUrl.getEndUrl());
    assertEquals(EndState.ERROR, resolvedUrl.getEndState());

    control.verify();
  }

  @Test
  public void testResolveStepsToPermanentError() throws Exception {
    expect(resolver.apply("jake")).andReturn("joe");
    expect(resolver.apply("joe")).andReturn("fred");
    expect(resolver.apply("fred")).andReturn(null);
    // we shouldn't back off after the last attempt
    control.replay();

    ResolvedUrl resolvedUrl = createResolver(3).resolveUrl("jake");
    assertEquals("jake", resolvedUrl.getStartUrl());
    assertContentsInOrder(resolvedUrl.getIntermediateUrls(), "joe");
    assertEquals("fred", resolvedUrl.getEndUrl());
    assertEquals(EndState.ERROR, resolvedUrl.getEndState());
  }

  private UrlResolver createResolver(int maxRedirects) {
    return new UrlResolver(clock, backoffStrategy, resolver, maxRedirects);
  }
}
