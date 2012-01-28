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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.twitter.common.base.ExceptionalFunction;
import com.twitter.common.net.UrlResolver.ResolvedUrl.EndState;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.PrintableHistogram;
import com.twitter.common.util.BackoffStrategy;
import com.twitter.common.util.Clock;
import com.twitter.common.util.TruncatedBinaryBackoff;
import com.twitter.common.util.caching.Cache;
import com.twitter.common.util.caching.LRUCache;

/**
 * Class to aid in resolving URLs by following redirects, which can optionally be performed
 * asynchronously using a thread pool.
 *
 * @author William Farner
 */
public class UrlResolver {
  private static final Logger LOG = Logger.getLogger(UrlResolver.class.getName());

  private static final String TWITTER_UA = "Twitterbot/0.1";
  private static final UrlResolverUtil URL_RESOLVER =
      new UrlResolverUtil(Functions.constant(TWITTER_UA));

  private static final ExceptionalFunction<String, String, IOException> RESOLVER =
      new ExceptionalFunction<String, String, IOException>() {
        @Override public String apply(String url) throws IOException {
          return URL_RESOLVER.getEffectiveUrl(url, null);
        }
      };

  private static ExceptionalFunction<String, String, IOException>
      getUrlResolver(final @Nullable ProxyConfig proxyConfig) {
    if (proxyConfig != null) {
      return new ExceptionalFunction<String, String, IOException>() {
        @Override public String apply(String url) throws IOException {
          return URL_RESOLVER.getEffectiveUrl(url, proxyConfig);
        }
      };
    } else {
      return RESOLVER;
    }
  }

  private final ExceptionalFunction<String, String, IOException> resolver;
  private final int maxRedirects;

  // Tracks the number of active tasks (threads in use).
  private final Semaphore poolEntrySemaphore;
  private final Integer threadPoolSize;

  // Helps with signaling the handler.
  private final Executor handlerExecutor;

  // Manages the thread pool and task execution.
  private ExecutorService executor;

  // Cache to store resolved URLs.
  private final Cache<String, String> urlCache = LRUCache.<String, String>builder()
      .maxSize(10000)
      .makeSynchronized(true)
      .build();

  // Variables to track connection/request stats.
  private AtomicInteger requestCount = new AtomicInteger(0);
  private AtomicInteger cacheHits = new AtomicInteger(0);
  private AtomicInteger failureCount = new AtomicInteger(0);
  // Tracks the time (in milliseconds) required to resolve URLs.
  private final PrintableHistogram urlResolutionTimesMs = new PrintableHistogram(
      1, 5, 10, 25, 50, 75, 100, 150, 200, 250, 300, 500, 750, 1000, 1500, 2000);

  private final Clock clock;
  private final BackoffStrategy backoffStrategy;

  @VisibleForTesting
  UrlResolver(Clock clock, BackoffStrategy backoffStrategy,
      ExceptionalFunction<String, String, IOException> resolver, int maxRedirects) {
    this(clock, backoffStrategy, resolver, maxRedirects, null);
  }

  /**
   * Creates a new asynchronous URL resolver.  A thread pool will be used to resolve URLs, and
   * resolved URLs will be announced via {@code handler}.
   *
   * @param maxRedirects The maximum number of HTTP redirects to follow.
   * @param threadPoolSize The number of threads to use for resolving URLs.
   * @param proxyConfig The proxy settings with which to make the HTTP request, or null for the
   *    default configured proxy.
   */
  public UrlResolver(int maxRedirects, int threadPoolSize, @Nullable ProxyConfig proxyConfig) {
    this(Clock.SYSTEM_CLOCK,
        new TruncatedBinaryBackoff(Amount.of(100L, Time.MILLISECONDS), Amount.of(1L, Time.SECONDS)),
        getUrlResolver(proxyConfig), maxRedirects, threadPoolSize);
  }

  public UrlResolver(int maxRedirects, int threadPoolSize) {
    this(maxRedirects, threadPoolSize, null);
  }

  private UrlResolver(Clock clock, BackoffStrategy backoffStrategy,
      ExceptionalFunction<String, String, IOException> resolver, int maxRedirects,
      @Nullable Integer threadPoolSize) {
    this.clock = clock;
    this.backoffStrategy = backoffStrategy;
    this.resolver = resolver;
    this.maxRedirects = maxRedirects;

    if (threadPoolSize != null) {
      this.threadPoolSize = threadPoolSize;
      Preconditions.checkState(threadPoolSize > 0);
      poolEntrySemaphore = new Semaphore(threadPoolSize);

      // Start up the thread pool.
      reset();

      // Executor to send notifications back to the handler.  This also needs to be
      // a daemon thread.
      handlerExecutor =
          Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).build());
    } else {
      this.threadPoolSize = null;
      poolEntrySemaphore = null;
      handlerExecutor = null;
    }
  }

  public Future<ResolvedUrl> resolveUrlAsync(final String url, final ResolvedUrlHandler handler) {
    Preconditions.checkNotNull(
        "Asynchronous URL resolution cannot be performed without a valid handler.", handler);

    try {
      poolEntrySemaphore.acquire();
    } catch (InterruptedException e) {
      LOG.log(Level.SEVERE, "Interrupted while waiting for thread to resolve URL: " + url, e);
      return null;
    }
    final ListenableFutureTask<ResolvedUrl> future =
        ListenableFutureTask.create(
          new Callable<ResolvedUrl>() {
            @Override public ResolvedUrl call() {
              return resolveUrl(url);
            }
          });

    future.addListener(new Runnable() {
      @Override public void run() {
        try {
          handler.resolved(future);
        } finally {
          poolEntrySemaphore.release();
        }
      }
    }, handlerExecutor);

    executor.execute(future);
    return future;
  }

  private void logThreadpoolInfo() {
    LOG.info("Shutting down thread pool, available permits: "
             + poolEntrySemaphore.availablePermits());
    LOG.info("Queued threads? " + poolEntrySemaphore.hasQueuedThreads());
    LOG.info("Queue length: " + poolEntrySemaphore.getQueueLength());
  }

  public void reset() {
    Preconditions.checkState(threadPoolSize != null);
    if (executor != null) {
      Preconditions.checkState(executor.isShutdown(),
          "The thread pool must be shut down before resetting.");
      Preconditions.checkState(executor.isTerminated(), "There may still be pending async tasks.");
    }

    // Create a thread pool with daemon threads, so that they may be terminated when no
    // application threads are running.
    executor = Executors.newFixedThreadPool(threadPoolSize,
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("UrlResolver[%d]").build());
  }

  /**
   * Terminates the thread pool, waiting at most {@code waitSeconds} for active threads to complete.
   * After this method is called, no more URLs may be submitted for resolution.
   *
   * @param waitSeconds The number of seconds to wait for active threads to complete.
   */
  public void clearAsyncTasks(int waitSeconds) {
    Preconditions.checkState(threadPoolSize != null,
        "finish() should not be called on a synchronous URL resolver.");

    logThreadpoolInfo();
    executor.shutdown(); // Disable new tasks from being submitted.
    try {
      // Wait a while for existing tasks to terminate
      if (!executor.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
        LOG.info("Pool did not terminate, forcing shutdown.");
        logThreadpoolInfo();
        List<Runnable> remaining = executor.shutdownNow();
        LOG.info("Tasks still running: " + remaining);
        // Wait a while for tasks to respond to being cancelled
        if (!executor.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
          LOG.warning("Pool did not terminate.");
          logThreadpoolInfo();
        }
      }
    } catch (InterruptedException e) {
      LOG.log(Level.WARNING, "Interrupted while waiting for threadpool to finish.", e);
      // (Re-)Cancel if current thread also interrupted
      executor.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Resolves a URL synchronously.
   *
   * @param url The URL to resolve.
   * @return The resolved URL.
   */
  public ResolvedUrl resolveUrl(String url) {
    ResolvedUrl resolvedUrl = new ResolvedUrl();
    resolvedUrl.setStartUrl(url);

    String cached = urlCache.get(url);
    if (cached != null) {
      cacheHits.incrementAndGet();
      resolvedUrl.setNextResolve(cached);
      resolvedUrl.setEndState(EndState.CACHED);
      return resolvedUrl;
    }

    String currentUrl = url;
    long backoffMs = 0L;
    String next = null;
    for (int i = 0; i < maxRedirects; i++) {
      try {
        next = resolveOnce(currentUrl);

        // If there was a 4xx or a 5xx, we''ll get a null back, so we pretend like we never advanced
        // to allow for a retry within the redirect limit.
        // TODO(John Sirois): we really need access to the return code here to do the right thing; ie:
        // retry for internal server errors but probably not for unauthorized
        if (next == null) {
          if (i < maxRedirects - 1) { // don't wait if we're about to exit the loop
            backoffMs = backoffStrategy.calculateBackoffMs(backoffMs);
            try {
              clock.waitFor(backoffMs);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException(
                  "Interrupted waiting to retry a failed resolution for: " + currentUrl, e);
            }
          }
          continue;
        }

        backoffMs = 0L;
        if (next.equals(currentUrl)) {
          // We've reached the end of the redirect chain.
          resolvedUrl.setEndState(EndState.REACHED_LANDING);
          urlCache.put(url, currentUrl);
          for (String intermediateUrl : resolvedUrl.getIntermediateUrls()) {
            urlCache.put(intermediateUrl, currentUrl);
          }
          return resolvedUrl;
        } else if (!url.equals(next)) {
          resolvedUrl.setNextResolve(next);
        }
        currentUrl = next;
      } catch (IOException e) {
        LOG.log(Level.INFO, "Failed to resolve url: " + url, e);
        resolvedUrl.setEndState(EndState.ERROR);
        return resolvedUrl;
      }
    }

    resolvedUrl.setEndState(next == null || url.equals(currentUrl) ? EndState.ERROR
        : EndState.REDIRECT_LIMIT);
    return resolvedUrl;
  }

  /**
   * Resolves a url, following at most one redirect.  Thread-safe.
   *
   * @param url The URL to resolve.
   * @return The result of following the URL through at most one redirect or null if the url could
   *     not be followed
   * @throws IOException If an error occurs while resolving the URL.
   */
  private String resolveOnce(String url) throws IOException {
    requestCount.incrementAndGet();

    String resolvedUrl = urlCache.get(url);
    if (resolvedUrl != null) {
      cacheHits.incrementAndGet();
      return resolvedUrl;
    }

    try {
      long startTimeMs = System.currentTimeMillis();
      resolvedUrl = resolver.apply(url);
      if (resolvedUrl == null) {
        return null;
      }

      urlCache.put(url, resolvedUrl);

      synchronized (urlResolutionTimesMs) {
        urlResolutionTimesMs.addValue(System.currentTimeMillis() - startTimeMs);
      }
      return resolvedUrl;
    } catch (IOException e) {
      failureCount.incrementAndGet();
      throw e;
    }
  }

  @Override
  public String toString() {
    return String.format("Cache: %s\nFailed requests: %d,\nResolution Times: %s",
        urlCache, failureCount.get(),
        urlResolutionTimesMs.toString());
  }

  /**
   * Class to wrap the result of a URL resolution.
   */
  public static class ResolvedUrl {
    public enum EndState {
      REACHED_LANDING,
      ERROR,
      CACHED,
      REDIRECT_LIMIT
    }

    private String startUrl;
    private final List<String> resolveChain;
    private EndState endState;

    public ResolvedUrl() {
      resolveChain = Lists.newArrayList();
    }

    @VisibleForTesting
    public ResolvedUrl(EndState endState, String startUrl, String... resolveChain) {
      this.endState = endState;
      this.startUrl = startUrl;
      this.resolveChain = Lists.newArrayList(resolveChain);
    }

    public String getStartUrl() {
      return startUrl;
    }

    void setStartUrl(String startUrl) {
      this.startUrl = startUrl;
    }

    /**
     * Returns the last URL resolved following a redirect chain, or null if the startUrl is a
     * landing URL.
     */
    public String getEndUrl() {
      return resolveChain.isEmpty() ? null : Iterables.getLast(resolveChain);
    }

    void setNextResolve(String endUrl) {
      this.resolveChain.add(endUrl);
    }

    /**
     * Returns any immediate URLs encountered on the resolution chain.  If the startUrl redirects
     * directly to the endUrl or they are the same the imtermediate URLs will be empty.
     */
    public Iterable<String> getIntermediateUrls() {
      return resolveChain.size() <= 1 ? ImmutableList.<String>of()
          : resolveChain.subList(0, resolveChain.size() - 1);
    }

    public EndState getEndState() {
      return endState;
    }

    void setEndState(EndState endState) {
      this.endState = endState;
    }

    public String toString() {
      return String.format("%s -> %s [%s, %d redirects]",
          startUrl, Joiner.on(" -> ").join(resolveChain), endState, resolveChain.size());
    }
  }

  /**
   * Interface to use for notifying the caller of resolved URLs.
   */
  public interface ResolvedUrlHandler {
    /**
     * Signals that a URL has been resolved to its target.  The implementation of this method must
     * be thread safe.
     *
     * @param future The future that has finished resolving a URL.
     */
    public void resolved(Future<ResolvedUrl> future);
  }
}
