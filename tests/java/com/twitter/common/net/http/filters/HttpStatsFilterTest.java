package com.twitter.common.net.http.filters;

import javax.servlet.FilterChain;
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
}
