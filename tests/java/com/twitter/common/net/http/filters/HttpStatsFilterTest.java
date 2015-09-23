package com.twitter.common.net.http.filters;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;

import com.google.common.collect.Lists;
import com.sun.jersey.api.core.ExtendedUriInfo;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.collections.Pair;
import com.twitter.common.net.http.filters.HttpStatsFilter.TrackRequestStats;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.SlidingStats;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpStatsFilterTest extends EasyMockTest {
  private FakeClock clock;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;
  private HttpStatsFilter filter;
  private ContainerRequest containerRequest;
  private ContainerResponse containerResponse;
  private ExtendedUriInfo extendedUriInfo;
  private HttpServletRequest servletRequest;

  private static final Amount<Long, Time> REQUEST_TIME =  Amount.of(1000L, Time.NANOSECONDS);

  private void injectContextVars() throws Exception {
    extendedUriInfo = createMock(ExtendedUriInfo.class);
    servletRequest = createMock(HttpServletRequest.class);

    List<Object> injectables = Lists.newArrayList(extendedUriInfo, servletRequest);

    for (Field f : filter.getClass().getDeclaredFields()) {
      if (f.isAnnotationPresent(Context.class)) {
        for (Object injectable : injectables) {
          if (f.getType().isInstance(injectable)) {
            f.setAccessible(true);
            f.set(filter, injectable);
          }
        }
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    clock = new FakeClock();
    request = createMock(HttpServletRequest.class);
    response = createMock(HttpServletResponse.class);
    filterChain = createMock(FilterChain.class);
    filter = new HttpStatsFilter(clock);

    containerRequest = createMock(ContainerRequest.class);
    containerResponse = createMock(ContainerResponse.class);

    injectContextVars();
  }

  @Test
  public void testStartTimeIsSetAsRequestAttribute() throws Exception {
    request.setAttribute(HttpStatsFilter.REQUEST_START_TIME, REQUEST_TIME.getValue());
    filterChain.doFilter(request, response);

    control.replay();

    clock.advance(REQUEST_TIME);
    filter.doFilter(request, response, filterChain);
  }

  @Test
  public void testExceptionStatsCounting() throws Exception {
    request.setAttribute(HttpStatsFilter.REQUEST_START_TIME, REQUEST_TIME.getValue());
    expectLastCall().times(2);
    clock.advance(REQUEST_TIME);

    filterChain.doFilter(anyObject(HttpServletRequest.class), anyObject(HttpServletResponse.class));
    expectLastCall().andThrow(new IOException());

    filterChain.doFilter(anyObject(HttpServletRequest.class), anyObject(HttpServletResponse.class));
    expectLastCall().andThrow(new ServletException());

    control.replay();

    try {
      filter.doFilter(request, response, filterChain);
      fail("Filter should have re-thrown the exception.");
    } catch (IOException e) {
      // Exception is expected, but we still want to assert on the stat tracking, so we can't
      //  just use @Test(expected...)
      assertEquals(1, filter.exceptionCount.get());
    }

    try {
      filter.doFilter(request, response, filterChain);
      fail("Filter should have re-thrown the exception.");
    } catch (ServletException e) {
      // See above.
      assertEquals(2, filter.exceptionCount.get());
    }
  }

  private void expectAnnotationValue(String value, int times) {
    AbstractResourceMethod matchedMethod = createMock(AbstractResourceMethod.class);
    expect(extendedUriInfo.getMatchedMethod()).andReturn(matchedMethod).times(times);

    TrackRequestStats annotation = createMock(TrackRequestStats.class);
    expect(matchedMethod.getAnnotation(TrackRequestStats.class)).andReturn(annotation).times(times);

    expect(annotation.value()).andReturn(value).times(times);
  }

  private void expectAnnotationValue(String value) {
    expectAnnotationValue(value, 1);
  }

  @Test
  public void testBasicStatsCounting() throws Exception {
    expect(containerResponse.getStatus()).andReturn(HttpServletResponse.SC_OK);

    expect(servletRequest.getAttribute(HttpStatsFilter.REQUEST_START_TIME))
        .andReturn(clock.nowNanos());

    String value = "some_value";
    expectAnnotationValue(value);

    control.replay();

    clock.advance(REQUEST_TIME);
    assertEquals(containerResponse, filter.filter(containerRequest, containerResponse));

    SlidingStats stat = filter.requestCounters.get(Pair.of(value, HttpServletResponse.SC_OK));
    assertEquals(1, stat.getEventCounter().get());
    assertEquals(REQUEST_TIME.getValue().longValue(), stat.getTotalCounter().get());
    assertEquals(1, filter.statusCounters.get(HttpServletResponse.SC_OK).getEventCounter().get());
  }

  @Test
  public void testMultipleRequests() throws Exception {
    int numCalls = 2;

    expect(containerResponse.getStatus()).andReturn(HttpServletResponse.SC_OK).times(numCalls);

    expect(servletRequest.getAttribute(HttpStatsFilter.REQUEST_START_TIME))
        .andReturn(clock.nowNanos()).times(numCalls);

    String value = "some_value";
    expectAnnotationValue(value, numCalls);

    control.replay();

    clock.advance(REQUEST_TIME);
    for (int i = 0; i < numCalls; i++) {
      filter.filter(containerRequest, containerResponse);
    }

    SlidingStats stat = filter.requestCounters.get(Pair.of(value, HttpServletResponse.SC_OK));
    assertEquals(numCalls, stat.getEventCounter().get());
    assertEquals(REQUEST_TIME.getValue() * numCalls, stat.getTotalCounter().get());
    assertEquals(numCalls,
        filter.statusCounters.get(HttpServletResponse.SC_OK).getEventCounter().get());
  }

  @Test
  public void testNoStartTime() throws Exception {
    expect(servletRequest.getAttribute(HttpStatsFilter.REQUEST_START_TIME))
        .andReturn(null);

    expect(containerResponse.getStatus()).andReturn(HttpServletResponse.SC_OK);

    control.replay();

    assertEquals(containerResponse, filter.filter(containerRequest, containerResponse));

    assertEquals(0, filter.statusCounters.asMap().keySet().size());
  }

  @Test
  public void testNoMatchedMethod() throws Exception {
    expect(containerResponse.getStatus()).andReturn(HttpServletResponse.SC_NOT_FOUND);

    expect(servletRequest.getAttribute(HttpStatsFilter.REQUEST_START_TIME))
        .andReturn(clock.nowNanos());

    expect(extendedUriInfo.getMatchedMethod()).andReturn(null);

    control.replay();

    clock.advance(REQUEST_TIME);
    assertEquals(containerResponse, filter.filter(containerRequest, containerResponse));
    assertEquals(1,
        filter.statusCounters.get(HttpServletResponse.SC_NOT_FOUND).getEventCounter().get());
  }
}
