package com.twitter.common.net.http.filters;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.sun.jersey.api.core.ExtendedUriInfo;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

import com.twitter.common.collections.Pair;
import com.twitter.common.stats.SlidingStats;
import com.twitter.common.stats.Stats;
import com.twitter.common.util.Clock;

/**
 * An HTTP filter that exports counts and timing for requests based on response code.
 */
public class HttpStatsFilter extends AbstractHttpFilter implements ContainerResponseFilter {
  /**
   * Methods tagged with this annotation will be intercepted and stats will be tracked accordingly.
   */
  @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
  public @interface TrackRequestStats {
    /**
     * Indicates the identifier to use when tracking requests with this annotation.
     */
    String value();
  }

  private static final Logger LOG = Logger.getLogger(HttpStatsFilter.class.getName());

  @VisibleForTesting
  static final String REQUEST_START_TIME = "request_start_time";

  private final Clock clock;
  @Context private ExtendedUriInfo extendedUriInfo;

  @VisibleForTesting
  final LoadingCache<Pair<String, Integer>, SlidingStats> requestCounters =
      CacheBuilder.newBuilder()
          .build(new CacheLoader<Pair<String, Integer>, SlidingStats>() {
            @Override
            public SlidingStats load(Pair<String, Integer> identifierAndStatus) {
              return new SlidingStats("http_" + identifierAndStatus.getFirst() + "_"
                  + identifierAndStatus.getSecond() + "_responses", "nanos");
            }
          });

  @Context private HttpServletRequest servletRequest;

  @VisibleForTesting
  final LoadingCache<Integer, SlidingStats> statusCounters = CacheBuilder.newBuilder()
      .build(new CacheLoader<Integer, SlidingStats>() {
        @Override
        public SlidingStats load(Integer status) {
          return new SlidingStats("http_" + status + "_responses", "nanos");
        }
      });

  @VisibleForTesting
  final AtomicLong exceptionCount = Stats.exportLong("http_request_exceptions");

  @Inject
  public HttpStatsFilter(Clock clock) {
    this.clock = Preconditions.checkNotNull(clock);
  }

  private void trackStats(int status) {
    long endTime = clock.nowNanos();

    Object startTimeAttribute = servletRequest.getAttribute(REQUEST_START_TIME);
    if (startTimeAttribute == null) {
      LOG.fine("No start time attribute was found on the request, this filter should be wired"
          + " as both a servlet filter and a container filter.");
      return;
    }

    long elapsed = endTime - ((Long) startTimeAttribute).longValue();
    statusCounters.getUnchecked(status).accumulate(elapsed);

    AbstractResourceMethod matchedMethod =  extendedUriInfo.getMatchedMethod();
    // It's possible for no method to have matched, e.g. in the case of a 404, don't let those
    // cases lead to an exception and a 500 response.
    if (matchedMethod == null) {
      return;
    }

    TrackRequestStats trackRequestStats = matchedMethod.getAnnotation(TrackRequestStats.class);

    if (trackRequestStats == null) {
      Method method = matchedMethod.getMethod();
      LOG.fine("The method that handled this request (" + method.getDeclaringClass() + "#"
          + method.getName() + ") is not annotated with " + TrackRequestStats.class.getSimpleName()
          + ". No request stats will recorded.");
      return;
    }

    requestCounters.getUnchecked(Pair.of(trackRequestStats.value(), status)).accumulate(elapsed);
  }

  @Override
  public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
    trackStats(response.getStatus());

    return response;
  }

  @Override
  public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    long startTime = clock.nowNanos();
    request.setAttribute(REQUEST_START_TIME, startTime);

    try {
      chain.doFilter(request, response);
    } catch (IOException e) {
      exceptionCount.incrementAndGet();
      throw e;
    } catch (ServletException e) {
      exceptionCount.incrementAndGet();
      throw e;
    }
  }
}