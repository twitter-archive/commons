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

package com.twitter.common.thrift;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.Stats;
import com.twitter.common.stats.StatsProvider;

/**
 * Represents the configuration for a thrift call.  Use {@link #builder()} to create a new one or
 * or {@link #builder(Config)} to create a new config based on another config.
 *
 * <p>If a deadline is specified, it acts as a global timeout for each thrift call made.
 * Obtaining connections, performing the remote call and executing retries are all expected to
 * complete within this deadline.  When the specified deadline is not met, an
 * {@link TTimeoutException} will be thrown.
 *
 * <p>If max retries is specified as zero (never retry), then the list of retryable exceptions are
 * ignored.  It is only when max retries is greater than zero that list of retryable exceptions is
 * used to determine if a particular failed call should be retried.
 *
 * @author John Sirois
 */
public class Config {

  /**
   * Created a builder for a new {@link Config}.  Default values are as follows:
   * <ul>
   * <li>{@link #getRequestTimeout()} 0
   * <li>{@link #getMaxRetries()} 0
   * <li>{@link #getRetryableExceptions()} []
   * <li>{@link #isDebug()} ()} false
   * </ul>
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   *
   * @param config the builder configuration to use
   */
  public static Builder builder(Config config) {
    Preconditions.checkNotNull(config);
    return new Builder(config);
  }

  private static final Amount<Long,Time> DEADLINE_BLOCKING = Amount.of(0L, Time.MILLISECONDS);

  @VisibleForTesting
  static final Amount<Long,Time> DEFAULT_CONNECT_TIMEOUT = Amount.of(5L, Time.SECONDS);

  private Amount<Long, Time> requestTimeout = DEADLINE_BLOCKING;
  private Amount<Long, Time> connectTimeout = DEFAULT_CONNECT_TIMEOUT;
  private int maxRetries;
  private ImmutableSet<Class<? extends Exception>> retryableExceptions = ImmutableSet.of();
  private boolean debug = false;
  private boolean enableStats = true;
  private StatsProvider statsProvider = Stats.STATS_PROVIDER;

  private Config() {
    // defaults
  }

  private Config(Config copyFrom) {
    requestTimeout = copyFrom.requestTimeout;
    maxRetries = copyFrom.maxRetries;
    retryableExceptions = copyFrom.retryableExceptions;
    debug = copyFrom.debug;
    statsProvider = copyFrom.statsProvider;
  }

  /**
   * Returns the maximum time to wait for any thrift call to complete.  A deadline of 0 means to
   * wait forever
   */
  public Amount<Long, Time> getRequestTimeout() {
    return requestTimeout;
  }

  /**
   * Returns the maximum time to wait for a connection to be established.  A deadline of 0 means to
   * wait forever
   */
  public Amount<Long, Time> getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Returns the maximum number of retries to perform for each thrift call.  A value of 0 means to
   * never retry and in this case {@link #getRetryableExceptions()} will be an empty set.
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Returns the set of exceptions to retry calls for.  The returned set will only be empty if
   * {@link #getMaxRetries()} is 0.
   */
  public ImmutableSet<Class<? extends Exception>> getRetryableExceptions() {
    return retryableExceptions;
  }

  /**
   * Returns {@code true} if the client should log extra debugging information.  Currently this
   * includes method call arguments when RPCs fail with exceptions.
   */
  public boolean isDebug() {
    return debug;
  }

  /**
   * Returns {@code true} if the client should track request statistics.
   */
  public boolean enableStats() {
    return enableStats;
  }

  /**
   * Returns the stats provider to use to record Thrift client stats.
   */
  public StatsProvider getStatsProvider() {
    return statsProvider;
  }

  // This was made public because it seems to be causing problems for scala users when it is not
  // public.
  public static abstract class AbstractBuilder<T extends AbstractBuilder> {
    private final Config config;

    AbstractBuilder() {
      this.config = new Config();
    }

    AbstractBuilder(Config template) {
      Preconditions.checkNotNull(template);
      this.config = new Config(template);
    }

    protected abstract T getThis();

    // TODO(John Sirois): extra validation or design ... can currently do strange things like:
    // builder.blocking().withDeadline(1, TimeUnit.MILLISECONDS)
    // builder.noRetries().retryOn(TException.class)

    /**
     * Specifies that all calls be blocking calls with no inherent deadline.  It may be the
     * case that underlying transports will eventually deadline, but {@link Thrift} will not
     * enforce a deadline.
     */
    public final T blocking() {
      config.requestTimeout = DEADLINE_BLOCKING;
      return getThis();
    }

    /**
     * Specifies that all calls be subject to a global timeout.  This deadline includes all call
     * activities, including obtaining a free connection and any automatic retries.
     */
    public final T withRequestTimeout(Amount<Long, Time> timeout) {
      Preconditions.checkNotNull(timeout);
      Preconditions.checkArgument(timeout.getValue() >= 0,
          "A negative deadline is invalid: %s", timeout);
      config.requestTimeout = timeout;
      return getThis();
    }

    /**
     * Assigns the timeout for all connections established with the blocking client.
     * On an asynchronous client this timeout is only used for the connection pool lock
     * acquisition on initial calls (not retries, @see withRetries).  The actual network
     * connection timeout for the asynchronous client is governed by socketTimeout.
     *
     * @param timeout Connection timeout.
     * @return A reference to the builder.
     */
    public final T withConnectTimeout(Amount<Long, Time> timeout) {
      Preconditions.checkNotNull(timeout);
      Preconditions.checkArgument(timeout.getValue() >= 0,
          "A negative deadline is invalid: %s", timeout);
      config.connectTimeout = timeout;
      return getThis();
    }

    /**
     * Specifies that no calls be automatically retried.
     */
    public final T noRetries() {
      config.maxRetries = 0;
      config.retryableExceptions = ImmutableSet.of();
      return getThis();
    }

    /**
     * Specifies that failing calls meeting {@link #retryOn retry} criteria be retried up to a
     * maximum of {@code retries} times before failing.  On an asynchronous client, these retries
     * will be forced to be non-blocking, failing fast if they cannot immediately acquire the
     * connection pool locks, so they only provide a best-effort retry strategy there.
     */
    public final T withRetries(int retries) {
      Preconditions.checkArgument(retries >= 0, "A negative retry count is invalid: %d", retries);
      config.maxRetries = retries;
      return getThis();
    }

    /**
     * Specifies the set of exception classes that are to be considered retryable (if retries are
     * enabled).  Any exceptions thrown by the underlying thrift call will be considered retryable
     * if they are an instance of any one of the specified exception classes.  The set of exception
     * classes must contain at least exception class.  To specify no retries either use
     * {@link #noRetries()} or pass zero to {@link #withRetries(int)}.
     */
    public final T retryOn(Iterable<? extends Class<? extends Exception>> retryableExceptions) {
      Preconditions.checkNotNull(retryableExceptions);
      ImmutableSet<Class<? extends Exception>> classes =
          ImmutableSet.copyOf(Iterables.filter(retryableExceptions, Predicates.notNull()));
      Preconditions.checkArgument(!classes.isEmpty(),
          "Must provide at least one retryable exception class");
      config.retryableExceptions = classes;
      return getThis();
    }

    /**
     * Specifies the set of exception classes that are to be considered retryable (if retries are
     * enabled).  Any exceptions thrown by the underlying thrift call will be considered retryable
     * if they are an instance of any one of the specified exception classes.  The set of exception
     * classes must contain at least exception class.  To specify no retries either use
     * {@link #noRetries()} or pass zero to {@link #withRetries(int)}.
     */
    public final T retryOn(Class<? extends Exception> exception) {
      Preconditions.checkNotNull(exception);
      config.retryableExceptions =
          ImmutableSet.<Class<? extends Exception>>builder().add(exception).build();
      return getThis();
    }

    /**
     * When {@code debug == true}, specifies that extra debugging information should be logged.
     */
    public final T withDebug(boolean debug) {
      config.debug = debug;
      return getThis();
    }

    /**
     * Disables stats collection on the client (enabled by default).
     */
    public T disableStats() {
      config.enableStats = false;
      return getThis();
    }

    /**
     * Registers a custom stats provider to use to track various client stats.
     */
    public T withStatsProvider(StatsProvider statsProvider) {
      config.statsProvider = Preconditions.checkNotNull(statsProvider);
      return getThis();
    }

    protected final Config getConfig() {
      return config;
    }
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private Builder() {
      super();
    }

    private Builder(Config template) {
      super(template);
    }

    @Override
    protected Builder getThis() {
      return this;
    }

    public Config create() {
      return getConfig();
    }
  }
}
