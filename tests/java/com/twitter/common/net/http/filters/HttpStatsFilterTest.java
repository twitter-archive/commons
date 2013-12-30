package com.twitter.common.net.http.filters;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.SlidingStats;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.testing.FakeClock;

import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpStatsFilterTest extends EasyMockTest {
  private FakeClock clock;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;
  private HttpStatsFilter filter;

  @Before
  public void setUp() throws Exception {
    clock = new FakeClock();
    request = createMock(HttpServletRequest.class);
    response = createMock(HttpServletResponse.class);
    filterChain = createMock(FilterChain.class);
    filter = new HttpStatsFilter(clock);
  }

  @Test
  public void testStatGathering() throws Exception {
    final Amount<Long, Time> responseTime = Amount.of(100L, Time.NANOSECONDS);
    int numCalls = 2;

    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    expectLastCall().times(numCalls);

    filterChain.doFilter(
        EasyMock.anyObject(HttpServletRequest.class),
        EasyMock.anyObject(HttpServletResponse.class));
    expectLastCall().andAnswer(new IAnswer<Void>() {
      @Override
      public Void answer() throws Throwable {
        clock.advance(responseTime);
        HttpServletResponse responseArg = (HttpServletResponse) EasyMock.getCurrentArguments()[1];
        responseArg.setStatus(HttpServletResponse.SC_NOT_FOUND);

        return null;
      }
    }).times(numCalls);

    control.replay();

    for (int i = 0; i < numCalls; i++) {
      filter.doFilter(request, response, filterChain);
    }

    SlidingStats stat = filter.counters.get(HttpServletResponse.SC_NOT_FOUND);
    assertEquals(responseTime.getValue() * numCalls, stat.getTotalCounter().get());
    assertEquals(numCalls, stat.getEventCounter().get());
  }

  @Test
  public void testExceptionStatsCounting() throws Exception {
    filterChain.doFilter(
        EasyMock.anyObject(HttpServletRequest.class),
        EasyMock.anyObject(HttpServletResponse.class));
    expectLastCall().andThrow(new IOException());
    filterChain.doFilter(
        EasyMock.anyObject(HttpServletRequest.class),
        EasyMock.anyObject(HttpServletResponse.class));
    expectLastCall().andThrow(new ServletException());

    control.replay();

    try {
      filter.doFilter(request, response, filterChain);
      fail();
    } catch (IOException e) {
      // Exception is expected, but we still want to assert on the stat tracking, so we can't
      //  just use @Test(expected...)
      assertEquals(1, filter.exceptionCount.get());
    }

    try {
      filter.doFilter(request, response, filterChain);
      fail();
    } catch (ServletException e) {
      // See above.
      assertEquals(2, filter.exceptionCount.get());
    }
  }
}
