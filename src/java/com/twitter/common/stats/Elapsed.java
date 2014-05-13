package com.twitter.common.stats;

import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;

import com.twitter.common.base.MorePreconditions;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.util.Clock;

/**
 * A stat that exports the amount of time since it was last reset.
 *
 * @author William Farner
 */
public class Elapsed {

  private final Ticker ticker;
  private final AtomicLong lastEventNs = new AtomicLong();

  /**
   * Calls {@link #Elapsed(String, Time)} using a default granularity of nanoseconds.
   *
   * @param name Name of the stat to export.
   */
  public Elapsed(String name) {
    this(name, Time.NANOSECONDS);
  }

  /**
   * Equivalent to calling {@link #Elapsed(String, Time, Ticker)} passing {@code name},
   * {@code granularity} and {@link com.google.common.base.Ticker#systemTicker()}.
   * <br/>
   * @param name Name of the stat to export.
   * @param granularity Time unit granularity to export.
   */
  public Elapsed(String name, Time granularity) {
    this(name, granularity, Ticker.systemTicker());
  }

   /**
    * Creates and exports a new stat that maintains the difference between the tick time
    * and the time since it was last reset.  Upon export, the counter will act as though it were just
    * reset.
    * <br/>
    * @param name Name of stat to export
    * @param granularity Time unit granularity to export.
    * @param ticker Ticker implementation
    */
  public Elapsed(String name, final Time granularity, final Ticker ticker) {
    MorePreconditions.checkNotBlank(name);
    Preconditions.checkNotNull(granularity);
    this.ticker = Preconditions.checkNotNull(ticker);

    reset();

    Stats.export(new StatImpl<Long>(name) {
      @Override public Long read() {
        return Amount.of(ticker.read() - lastEventNs.get(), Time.NANOSECONDS).as(granularity);
      }
    });
  }

  public void reset() {
    lastEventNs.set(ticker.read());
  }
}
