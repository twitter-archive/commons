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

package com.twitter.common.logging;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.stats.StatImpl;
import com.twitter.common.stats.Stats;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Log that buffers requests before sending them to a wrapped log.
 *
 * @author William Farner
 */
public class BufferedLog<T, R> implements Log<T, Void> {
  private static final Logger LOG = Logger.getLogger(BufferedLog.class.getName());

  private static final ExecutorService DEFAULT_EXECUTOR_SERVICE =
      Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("Log Pusher-%d").build());
  private static final int DEFAULT_MAX_BUFFER_SIZE = 100000;

  // TODO(William Farner): Change to use a ScheduledExecutorService instead of a timer.
  private final TimerTask logPusher = new TimerTask() {
          @Override public void run() {
            flush();
          }
        };

  // Local buffer of log messages.
  private final List<T> localBuffer = Lists.newLinkedList();

  // The log that is being buffered.
  private Log<T, R> bufferedLog;

  // Filter to determine when a log request should be retried.
  private Predicate<R> retryFilter = null;

  // Maximum number of log entries that can be buffered before truncation (lost messages).
  private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;

  // Maximum buffer length before attempting to submit.
  private int chunkLength;

  // Maximum time for a message to sit in the buffer before attempting to flush.
  private Amount<Integer, Time> flushInterval;

  // Service to handle flushing the log.
  private ExecutorService logSubmitService = DEFAULT_EXECUTOR_SERVICE;

  private BufferedLog() {
    // Created through builder.

    Stats.export(new StatImpl<Integer>("scribe_buffer_size") {
        public Integer read() { return getBacklog(); }
      });
  }

  public static <T, R> Builder<T, R> builder() {
    return new Builder<T, R>();
  }

  /**
   * Starts the log submission service by scheduling a timer to periodically submit messages.
   */
  private void start() {
    long flushIntervalMillis = flushInterval.as(Time.MILLISECONDS);

    new Timer(true).scheduleAtFixedRate(logPusher, flushIntervalMillis, flushIntervalMillis);
  }

  /**
   * Gets the current number of messages in the local buffer.
   *
   * @return The number of backlogged messages.
   */
  protected int getBacklog() {
    synchronized (localBuffer) {
      return localBuffer.size();
    }
  }

  /**
   * Stores a log entry, flushing immediately if the buffer length limit is exceeded.
   *
   * @param entry Entry to log.
   */
  @Override
  public Void log(T entry) {
    synchronized (localBuffer) {
      localBuffer.add(entry);

      if (localBuffer.size() >= chunkLength) {
        logSubmitService.submit(logPusher);
      }
    }

    return null;
  }

  @Override
  public Void log(List<T> entries) {
    for (T entry : entries) log(entry);

    return null;
  }

  @Override
  public void flush() {
    List<T> buffer = copyBuffer();
    if (buffer.isEmpty()) return;

    R result = bufferedLog.log(buffer);

    // Restore the buffer if the write was not successful.
    if (retryFilter != null && retryFilter.apply(result)) {
      LOG.warning("Log request failed, restoring spooled messages.");
      restoreToLocalBuffer(buffer);
    }
  }

  /**
   * Creats a snapshot of the local buffer and clears the local buffer.
   *
   * @return A snapshot of the local buffer.
   */
  private List<T> copyBuffer() {
    synchronized (localBuffer) {
      List<T> bufferCopy = ImmutableList.copyOf(localBuffer);
      localBuffer.clear();
      return bufferCopy;
    }
  }

  /**
   * Restores log entries back to the local buffer.  This can be used to commit entries back to the
   * buffer after a flush operation failed.
   *
   * @param buffer The log entries to restore.
   */
  private void restoreToLocalBuffer(List<T> buffer) {
    synchronized (localBuffer) {
      int restoreRecords = Math.min(buffer.size(), maxBufferSize - localBuffer.size());

      if (restoreRecords != buffer.size()) {
        LOG.severe((buffer.size() - restoreRecords) + " log records truncated!");

        if (restoreRecords == 0) return;
      }

      localBuffer.addAll(0, buffer.subList(buffer.size() - restoreRecords, buffer.size()));
    }
  }

  /**
   * Configures a BufferedLog object.
   *
   * @param <T> Log message type.
   * @param <R> Log result type.
   */
  public static class Builder<T, R> {
    private final BufferedLog<T, R> instance;

    public Builder() {
      instance = new BufferedLog<T, R>();
    }

    /**
     * Specifies the log that should be buffered.
     *
     * @param bufferedLog Log to buffer requests to.
     * @return A reference to the builder.
     */
    public Builder<T, R> buffer(Log<T, R> bufferedLog) {
      instance.bufferedLog = bufferedLog;
      return this;
    }

    /**
     * Adds a custom retry filter that will be used to determine whether a log result {@code R}
     * should be used to indicate that a log request should be retried.  Log submit retry behavior
     * is not defined when the filter throws uncaught exceptions.
     *
     * @param retryFilter Filter to determine whether to retry.
     * @return A reference to the builder.
     */
    public Builder<T, R> withRetryFilter(Predicate<R> retryFilter) {
      instance.retryFilter = retryFilter;
      return this;
    }

    /**
     * Specifies the maximum allowable buffer size, after which log records will be dropped to
     * conserve memory.
     *
     * @param maxBufferSize Maximum buffer size.
     * @return A reference to the builder.
     */
    public Builder<T, R> withMaxBuffer(int maxBufferSize) {
      instance.maxBufferSize = maxBufferSize;
      return this;
    }

    /**
     * Specifies the desired number of log records to submit in each request.
     *
     * @param chunkLength Maximum number of records to accumulate before trying to submit.
     * @return A reference to the builder.
     */
    public Builder<T, R> withChunkLength(int chunkLength) {
      instance.chunkLength = chunkLength;
      return this;
    }

    /**
     * Specifies the maximum amount of time that a log entry may wait in the buffer before an
     * attempt is made to flush the buffer.
     *
     * @param flushInterval Log flush interval.
     * @return A reference to the builder.
     */
    public Builder<T, R> withFlushInterval(Amount<Integer, Time> flushInterval) {
      instance.flushInterval = flushInterval;
      return this;
    }

    /**
     * Specifies the executor service to use for (synchronously or asynchronously) sending
     * log entries.
     *
     * @param logSubmitService Log submit executor service.
     * @return A reference to the builder.
     */
    public Builder<T, R> withExecutorService(ExecutorService logSubmitService) {
      instance.logSubmitService = logSubmitService;
      return this;
    }

    /**
     * Creates the buffered log.
     *
     * @return The prepared buffered log.
     */
    public BufferedLog<T, R> build() {
      Preconditions.checkArgument(instance.chunkLength > 0);
      Preconditions.checkArgument(instance.flushInterval.as(Time.MILLISECONDS) > 0);
      Preconditions.checkNotNull(instance.logSubmitService);
      Preconditions.checkArgument(instance.chunkLength <= instance.maxBufferSize);

      instance.start();

      return instance;
    }
  }
}
