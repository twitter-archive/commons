package com.twitter.common.testing.runner;

import java.util.List;

import com.google.common.collect.Lists;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import com.twitter.common.base.Closure;
import com.twitter.common.base.ExceptionalClosure;

/**
 * A run listener that forwards all events to a sequence of registered listeners.
 */
class ForwardingListener extends RunListener implements ListenerRegistry {
  private final List<RunListener> listeners = Lists.newArrayList();

  @Override
  public void addListener(RunListener listener) {
    listeners.add(listener);
  }

  private <E extends Exception> void fire(ExceptionalClosure<RunListener, E> dispatcher) throws E {
    for (RunListener listener : listeners) {
      dispatcher.execute(listener);
    }
  }

  @Override
  public void testRunStarted(final Description description) throws Exception {
    fire(new ExceptionalClosure<RunListener, Exception>() {
      @Override public void execute(RunListener listener) throws Exception {
        listener.testRunStarted(description);
      }
    });
  }

  @Override
  public void testRunFinished(final Result result) throws Exception {
    fire(new ExceptionalClosure<RunListener, Exception>() {
      @Override public void execute(RunListener listener) throws Exception {
        listener.testRunFinished(result);
      }
    });
  }

  @Override
  public void testStarted(final Description description) throws Exception {
    fire(new ExceptionalClosure<RunListener, Exception>() {
      @Override public void execute(RunListener listener) throws Exception {
        listener.testStarted(description);
      }
    });
  }

  @Override
  public void testIgnored(final Description description) throws Exception {
    fire(new ExceptionalClosure<RunListener, Exception>() {
      @Override public void execute(RunListener listener) throws Exception {
        listener.testIgnored(description);
      }
    });
  }

  @Override
  public void testFailure(final Failure failure) throws Exception {
    fire(new ExceptionalClosure<RunListener, Exception>() {
      @Override public void execute(RunListener listener) throws Exception {
        listener.testFailure(failure);
      }
    });
  }

  @Override
  public void testFinished(final Description description) throws Exception {
    fire(new ExceptionalClosure<RunListener, Exception>() {
      @Override public void execute(RunListener listener) throws Exception {
        listener.testFinished(description);
      }
    });
  }

  @Override
  public void testAssumptionFailure(final Failure failure) {
    fire(new Closure<RunListener>() {
      @Override public void execute(RunListener listener) {
        listener.testAssumptionFailure(failure);
      }
    });
  }
}
