package com.twitter.common.net.http.filters;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;

import com.twitter.common.stats.SlidingStats;
import com.twitter.common.util.Clock;

/**
 * An HTTP filter that exports counts and timing for requests based on response code.
 */
public class HttpStatsFilter extends AbstractHttpFilter {

  private final Clock clock;

  @VisibleForTesting
  final LoadingCache<Integer, SlidingStats> counters = CacheBuilder.newBuilder()
      .build(new CacheLoader<Integer, SlidingStats>() {
        @Override public SlidingStats load(Integer status) {
          return new SlidingStats("http_" + status + "_responses", "nanos");
        }
      });

  private static class ResponseWithStatus extends HttpServletResponseWrapper {
    // 200 response code is the default if none is explicitly set.
    private int wrappedStatus = HttpServletResponse.SC_OK;

    ResponseWithStatus(HttpServletResponse resp) {
      super(resp);
    }

    @Override public void setStatus(int sc) {
      super.setStatus(sc);
      wrappedStatus = sc;
    }

    @Override public void setStatus(int sc, String sm) {
      super.setStatus(sc, sm);
      wrappedStatus = sc;
    }
  }

  @Inject
  public HttpStatsFilter(Clock clock) {
    this.clock = Preconditions.checkNotNull(clock);
  }

  @Override
  public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    long start = clock.nowNanos();
    ResponseWithStatus wrapper = new ResponseWithStatus(response);
    // TODO(jcohen): Trap exceptions thrown by the request and increment a counter
    chain.doFilter(request, wrapper);
    counters.getUnchecked(wrapper.wrappedStatus).accumulate(clock.nowNanos() - start);
  }
}