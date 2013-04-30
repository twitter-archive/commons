package com.twitter.common.util.testing;


import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;

import org.omg.CORBA.PUBLIC_MEMBER;

import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;

/**
 * A ticker for use in testing with a configurable value for {@link #Ticker#read()}.
 */
public class FakeTicker extends Ticker{
  private long nowNanos;

  /**
   * Sets what {@link #read()} will return until this method is called again with a new value
   * for {@code now}.
   *
   * @param nowNanos the current time in nanoseconds
   */
  public void setNowNanos(long nowNanos) {
    this.nowNanos = nowNanos;
  }

  @Override
  public long read(){
    return nowNanos;
  }

  /**
   * Advances the current time by the given {@code period}.  Time can be retarded by passing a
   * negative value.
   *
   * @param period the amount of time to advance the current time by
   */
  public void advance(Amount<Long, Time> period) {
    Preconditions.checkNotNull(period);
    nowNanos = nowNanos + period.as(Time.NANOSECONDS);
  }

  /**
   * Waits in fake time, immediately returning in real time; however a check of {@link #Ticker#read()}
   * after this method completes will consistently reveal that {@code nanos} did in fact pass while
   * waiting.
   *
   * @param nanos the amount of time to wait in nanoseconds
   */
  public void waitNanos(long nanos) {
    advance(Amount.of(nanos, Time.NANOSECONDS));
  }
}
