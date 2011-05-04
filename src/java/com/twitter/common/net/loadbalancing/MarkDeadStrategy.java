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

package com.twitter.common.net.loadbalancing;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.twitter.common.base.Closure;
import com.twitter.common.net.pool.ResourceExhaustedException;
import com.twitter.common.net.loadbalancing.RequestTracker.RequestResult;
import com.twitter.common.util.BackoffDecider;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A load balancer that serves as a layer above another load balancer to mark hosts as dead, and
 * prevent them from being visible to the wrapped load balancer.
 * If all backends become marked as dead, they will all be unmarked.
 *
 * @author William Farner
 */
public class MarkDeadStrategy<S> implements LoadBalancingStrategy<S> {
  private static final Logger LOG = Logger.getLogger(MarkDeadStrategy.class.getName());

  private final LoadBalancingStrategy<S> wrappedStrategy;
  private final Map<S, BackoffDecider> targets = Maps.newHashMap();
  private final Function<S, BackoffDecider> backoffFactory;
  protected final Predicate<S> hostChecker;

  private Set<S> liveBackends = null;
  private Closure<Collection<S>> onBackendsChosen = null;

  // Flipped when we are in "forced live" mode, where all backends are considered dead and we
  // send them all traffic as a last-ditch effort.
  private boolean forcedLive = false;

  /**
   * Creates a mark dead strategy with a wrapped strategy, backoff decider factory
   * and a predicate host checker. Use this constructor if you want to pass in the
   * your own implementation of the host checker.
   *
   * @param wrappedStrategy one of the implementations of the load balancing strategy.
   * @param backoffFactory backoff decider factory per host.
   * @param hostChecker predicate that returns {@code true} if the host is alive, otherwise returns {@code false}.
   */
  public MarkDeadStrategy(LoadBalancingStrategy<S> wrappedStrategy,
      Function<S, BackoffDecider> backoffFactory, Predicate<S> hostChecker) {
    this.wrappedStrategy = Preconditions.checkNotNull(wrappedStrategy);
    this.backoffFactory = Preconditions.checkNotNull(backoffFactory);
    this.hostChecker = Preconditions.checkNotNull(hostChecker);
  }

  /**
   * Constructor that uses a default predicate host checker that always returns true.
   * This is the default constructor that all consumers of MarkDeadStrategy currently use.
   *
   * @param wrappedStrategy one of the implementations of the load balancing strategy.
   * @param backoffFactory backoff decider factory per host.
   */
  public MarkDeadStrategy(LoadBalancingStrategy<S> wrappedStrategy,
      Function<S, BackoffDecider> backoffFactory) {
    this(wrappedStrategy, backoffFactory, Predicates.<S>alwaysTrue());
  }

  @Override
  public void offerBackends(Set<S> offeredBackends, Closure<Collection<S>> onBackendsChosen) {
    this.onBackendsChosen = onBackendsChosen;
    targets.keySet().retainAll(offeredBackends);
    for (S backend : offeredBackends) {
      if (!targets.containsKey(backend)) {
        targets.put(backend, backoffFactory.apply(backend));
      }
    }

    adjustBackends();
  }

  @Override
  public void addConnectResult(S backendKey, ConnectionResult result, long connectTimeNanos) {
    Preconditions.checkNotNull(backendKey);
    Preconditions.checkNotNull(result);

    BackoffDecider decider = targets.get(backendKey);
    Preconditions.checkNotNull(decider);

    addResult(decider, result);
    if (shouldNotifyFor(backendKey)) {
      wrappedStrategy.addConnectResult(backendKey, result, connectTimeNanos);
    }
  }

  @Override
  public void connectionReturned(S backendKey) {
    Preconditions.checkNotNull(backendKey);

    if (shouldNotifyFor(backendKey)) {
      wrappedStrategy.connectionReturned(backendKey);
    }
  }

  @Override
  public void addRequestResult(S requestKey, RequestResult result,
      long requestTimeNanos) {
    Preconditions.checkNotNull(requestKey);
    Preconditions.checkNotNull(result);

    BackoffDecider decider = targets.get(requestKey);
    Preconditions.checkNotNull(decider);

    addResult(decider, result);
    if (shouldNotifyFor(requestKey)) {
      wrappedStrategy.addRequestResult(requestKey, result, requestTimeNanos);
    }
  }

  private void addResult(BackoffDecider decider, ConnectionResult result) {
    switch (result) {
      case FAILED:
      case TIMEOUT:
        addResult(decider, false);
        break;
      case SUCCESS:
        addResult(decider, true);
        break;
      default:
        throw new UnsupportedOperationException("Unhandled result type " + result);
    }
  }

  private void addResult(BackoffDecider decider, RequestTracker.RequestResult result) {
    switch (result) {
      case FAILED:
      case TIMEOUT:
        addResult(decider, false);
        break;
      case SUCCESS:
        addResult(decider, true);
        break;
      default:
        throw new UnsupportedOperationException("Unhandled result type " + result);
    }
  }

  private void addResult(BackoffDecider decider, boolean success) {
    if (success) {
      decider.addSuccess();
    } else {
      decider.addFailure();
    }

    // Check if any of the backends have moved into or out of dead state.
    for (Map.Entry<S, BackoffDecider> entry : targets.entrySet()) {
      boolean dead = entry.getValue().shouldBackOff();
      boolean markedDead = !liveBackends.contains(entry.getKey());

      // only check the servers that were marked dead before and see if we can
      // connect to them, otherwise set dead to true.
      if (markedDead && !dead) {
        boolean alive = hostChecker.apply(entry.getKey());
        if (!alive) {
          entry.getValue().transitionToBackOff(0, true);
        }
        dead = !alive;
      }

      if (dead && !markedDead && forcedLive) {
        // Do nothing here.  Since we have forced all backends to be live, we don't want to
        // continually advertise the backend list to the wrapped strategy.
      } else if (dead != markedDead || !dead && forcedLive) {
        adjustBackends();
        break;
      }
    }
  }

  private boolean shouldNotifyFor(S backend) {
    return liveBackends.contains(backend);
  }

  private final Predicate<S> deadTargetFilter = new Predicate<S>() {
      @Override public boolean apply(S backend) {
        return !targets.get(backend).shouldBackOff();
      }
    };

  private void adjustBackends() {
    liveBackends = Sets.newHashSet(Iterables.filter(targets.keySet(), deadTargetFilter));
    if (liveBackends.isEmpty()) {
      liveBackends = targets.keySet();
      forcedLive = true;
    } else {
      forcedLive = false;
    }
    LOG.info("Observed backend state change, changing live backends to " + liveBackends);
    wrappedStrategy.offerBackends(liveBackends, onBackendsChosen);
  }

  @Override
  public S nextBackend() throws ResourceExhaustedException {
    return wrappedStrategy.nextBackend();
  }
}
