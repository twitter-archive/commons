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

package com.twitter.common.util.concurrent;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An executor service that forwards all calls to another executor service. Subclasses should
 * override one or more methods to modify the behavior of the backing executor service as desired
 * per the <a href="http://en.wikipedia.org/wiki/Decorator_pattern">decorator pattern</a>.
 *
 * @author John Sirois
 */
public class ForwardingExecutorService<T extends ExecutorService> implements ExecutorService {
  protected final T delegate;

  public ForwardingExecutorService(T delegate) {
    Preconditions.checkNotNull(delegate);
    this.delegate = delegate;
  }

  public void shutdown() {
    delegate.shutdown();
  }

  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(task);
  }

  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(task, result);
  }

  public Future<?> submit(Runnable task) {
    return delegate.submit(task);
  }

  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {

    return delegate.invokeAll(tasks);
  }

  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
      TimeUnit unit) throws InterruptedException {

    return delegate.invokeAll(tasks, timeout, unit);
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {

    return delegate.invokeAny(tasks);
  }

  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {

    return delegate.invokeAny(tasks, timeout, unit);
  }

  public void execute(Runnable command) {
    delegate.execute(command);
  }
}
