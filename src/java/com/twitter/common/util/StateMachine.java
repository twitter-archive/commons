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

package com.twitter.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.base.ExceptionalSupplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.twitter.common.base.MorePreconditions.checkNotBlank;

/**
 * Represents a state machine that is not necessarily a Finite State Machine.
 * The caller may configure the state machine to permit only known state transitions, or to only
 * disallow known state transitions (and permit unknown transitions).
 *
 * @param <T> THe type of objects that the caller uses to represent states.
 *
 * TODO(William Farner): Consider merging the stats-tracking ala PipelineStats into this.
 *
 * @author William Farner
 */
public class StateMachine<T> {
  private static final Logger LOG = Logger.getLogger(StateMachine.class.getName());

  private final String name;

  // Stores mapping from states to the states that the machine is allowed to transition into.
  private final Multimap<T, T> stateTransitions;

  private final Closure<Transition<T>> transitionCallback;
  private final boolean throwOnBadTransition;

  private volatile T currentState;
  private final Lock readLock;
  private final Lock writeLock;


  private StateMachine(String name,
      T initialState,
      Multimap<T, T> stateTransitions,
      Closure<Transition<T>> transitionCallback,
      boolean throwOnBadTransition) {
    this.name = name;
    this.currentState = initialState;
    this.stateTransitions = stateTransitions;
    this.transitionCallback = transitionCallback;
    this.throwOnBadTransition = throwOnBadTransition;

    ReadWriteLock stateLock = new ReentrantReadWriteLock(true /* fair */);
    readLock = stateLock.readLock();
    writeLock = stateLock.writeLock();
  }

  /**
   * Gets the name of this state machine.
   *
   * @return The state machine name.
   */
  public String getName() {
    return name;
  }

  /**
   * Fetches the state that the machine is currently in.
   *
   * @return Current state.
   */
  public T getState() {
    return currentState;
  }

  /**
   * Checks that the current state is the {@code expectedState} and throws if it is not.
   *
   * @param expectedState The expected state
   * @throws IllegalStateException if the current state is not the {@code expectedState}.
   */
  public void checkState(T expectedState) {
    checkNotNull(expectedState);

    readLock.lock();
    try {
      if (currentState != expectedState) {
        throw new IllegalStateException(
            String.format("In state %s, expected to be in %s.", expectedState, currentState));
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Executes the supplied {@code work} if the state machine is in the {@code expectedState},
   * postponing any concurrently requested {@link #transition(Object)} until after the execution of
   * the work.
   *
   * @param expectedState The expected state the work should be performed in.
   * @param work The work to perform in the {@code expectedState}.
   * @param <O> The type returned by the unit of work.
   * @param <E> The type of exception that may be thrown by the unit of work.
   * @return The result of the unit of work if the current state is the {@code expectedState}.
   * @throws IllegalStateException if the current state is not the {@code expectedState}.
   * @throws E if the unit of work throws.
   */
  public <O, E extends Exception> O doInState(T expectedState, ExceptionalSupplier<O, E> work)
      throws E {

    checkNotNull(expectedState);
    checkNotNull(work);

    readLock.lock();
    try {
      checkState(expectedState);
      return work.get();
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Transitions the machine into state {@code nextState}.
   *
   * @param nextState The state to move into.
   * @throws IllegalStateTransitionException If the state transition is not allowed.
   * @return {@code true} if the transition was allowed, {@code false} otherwise.
   */
  public boolean transition(T nextState) throws IllegalStateTransitionException {
    boolean transitionAllowed = false;

    T currentCopy = currentState;

    writeLock.lock();
    try {
      if (stateTransitions.containsEntry(currentState, nextState)) {
        currentState = nextState;
        transitionAllowed = true;
      } else if (throwOnBadTransition) {
        throw new IllegalStateTransitionException(
            String.format("State transition from %s to %s is not allowed.", currentState,
                nextState));
      }
    } finally {
      writeLock.unlock();
    }

    transitionCallback.execute(new Transition<T>(currentCopy, nextState, transitionAllowed));
    return transitionAllowed;
  }

  public static class IllegalStateTransitionException extends IllegalStateException {
    public IllegalStateTransitionException(String msg) {
      super(msg);
    }
  }

  /**
   * Convenience method to create a builder object.
   *
   * @param <T> Type of builder to create.
   * @param name Name of the state machine to create a builder for.
   * @return New builder.
   */
  public static <T> Builder<T> builder(String name) {
    return new Builder<T>(name);
  }

  /**
   * Builder to create a state machine.
   *
   * @param <T>
   */
  public static class Builder<T> {
    private final String name;
    private T initialState;
    private final Multimap<T, T> stateTransitions = HashMultimap.create();
    private final List<Closure<Transition<T>>> transitionCallbacks = Lists.newArrayList();
    private boolean throwOnBadTransition = true;

    public Builder(String name) {
      this.name = checkNotBlank(name);
    }

    /**
     * Sets the initial state for the state machine.
     *
     * @param state Initial state.
     * @return A reference to the builder.
     */
    public Builder<T> initialState(T state) {
      checkNotNull(state);
      initialState = state;
      return this;
    }

    /**
     * Adds a state and its allowed transitions.
     * At least one transition state must be added, it is not necessary to explicitly add states
     * that have no allowed transitions (terminal states).
     *
     * @param callback Callback to notify of any transition attempted from the state.
     * @param state State to add.
     * @param transitionStates Allowed transitions from {@code state}.
     * @return A reference to the builder.
     */
    public Builder<T> addState(Closure<Transition<T>> callback, T state,
        Set<T> transitionStates) {
      checkNotNull(callback);
      checkNotNull(state);

      Preconditions.checkArgument(Iterables.all(transitionStates, Predicates.notNull()));

      stateTransitions.putAll(state, transitionStates);

      @SuppressWarnings("unchecked")
      Predicate<Transition<T>> filter = Transition.from(state);
      onTransition(filter, callback);
      return this;
    }

    /**
     * Varargs version of {@link #addState(com.twitter.common.base.Closure, Object, java.util.Set)}.
     *
     * @param callback Callback to notify of any transition attempted from the state.
     * @param state State to add.
     * @param transitionStates Allowed transitions from {@code state}.
     * @return A reference to the builder.
     */
    public Builder<T> addState(Closure<Transition<T>> callback, T state,
        T... transitionStates) {
      Set<T> states = ImmutableSet.copyOf(transitionStates);
      Preconditions.checkArgument(Iterables.all(states, Predicates.notNull()));

      return addState(callback, state, states);
    }

    /**
     * Adds a state with no transitions.
     *
     * @param callback Callback to notify of any transition attempted from the state.
     * @param state State to add.
     * @return A reference to the builder.
     */
    public Builder<T> addState(Closure<Transition<T>> callback, T state) {
      return addState(callback, state, ImmutableSet.<T>of());
    }

    /**
     * Adds a state and its allowed transitions.
     * At least one transition state must be added, it is not necessary to explicitly add states
     * that have no allowed transitions (terminal states).
     *
     * @param state State to add.
     * @param transitionStates Allowed transitions from {@code state}.
     * @return A reference to the builder.
     */
    public Builder<T> addState(T state, T... transitionStates) {
      return addState(Closures.<Transition<T>>noop(), state, transitionStates);
    }

    private void onTransition(Predicate<Transition<T>> transitionFilter,
        Closure<Transition<T>> handler) {
      onAnyTransition(Closures.filter(transitionFilter, handler));
    }

    /**
     * Adds a callback to be executed for every state transition, including invalid transitions
     * that are attempted.
     *
     * @param handler Callback to notify of transition attempts.
     * @return A reference to the builder.
     */
    public Builder<T> onAnyTransition(Closure<Transition<T>> handler) {
      transitionCallbacks.add(handler);
      return this;
    }

    /**
     * Adds a log message for every state transition that is attempted.
     *
     * @return A reference to the builder.
     */
    public Builder<T> logTransitions() {
      return onAnyTransition(new Closure<Transition<T>>() {
        @Override public void execute(Transition<T> transition) {
          LOG.info(name + " state machine transition " + transition);
        }
      });
    }

    /**
     * Allows the caller to specify whether {@link IllegalStateTransitionException} should be thrown
     * when a bad state transition is attempted (the default behavior).
     *
     * @param throwOnBadTransition Whether an exception should be thrown when a bad state transition
     *     is attempted.
     * @return A reference to the builder.
     */
    public Builder<T> throwOnBadTransition(boolean throwOnBadTransition) {
      this.throwOnBadTransition = throwOnBadTransition;
      return this;
    }

    /**
     * Builds the state machine.
     *
     * @return A reference to the prepared state machine.
     */
    public StateMachine<T> build() {
      Preconditions.checkState(initialState != null, "Initial state must be specified.");
      checkArgument(!stateTransitions.isEmpty(), "No state transitions were specified.");
      return new StateMachine<T>(name,
          initialState,
          stateTransitions,
          Closures.combine(transitionCallbacks),
          throwOnBadTransition);
    }
  }

  /**
   * Representation of a state transition.
   *
   * @param <T> State type.
   */
  public static class Transition<T> {
    private final T from;
    private final T to;
    private final boolean allowed;

    public Transition(T from, T to, boolean allowed) {
      this.from = checkNotNull(from);
      this.to = checkNotNull(to);
      this.allowed = allowed;
    }

    private static <T> Function<Transition<T>, T> from() {
      return new Function<Transition<T>, T>() {
        @Override public T apply(Transition<T> transition) {
          return transition.from;
        }
      };
    }

    private static <T> Function<Transition<T>, T> to() {
      return new Function<Transition<T>, T>() {
        @Override public T apply(Transition<T> transition) {
          return transition.to;
        }
      };
    }

    private static <T> Predicate<Transition<T>> oneSideFilter(
        Function<Transition<T>, T> extractor, final T... states) {
      checkArgument(Iterables.all(Arrays.asList(states), Predicates.notNull()));

      return Predicates.compose(Predicates.in(ImmutableSet.copyOf(states)), extractor);
    }

    /**
     * Creates a predicate that returns {@code true} for transitions from the given states.
     *
     * @param states States to filter on.
     * @param <T> State type.
     * @return A from-state filter.
     */
    public static <T> Predicate<Transition<T>> from(final T... states) {
      return oneSideFilter(Transition.<T>from(), states);
    }

    /**
     * Creates a predicate that returns {@code true} for transitions to the given states.
     *
     * @param states States to filter on.
     * @param <T> State type.
     * @return A to-state filter.
     */
    public static <T> Predicate<Transition<T>> to(final T... states) {
      return oneSideFilter(Transition.<T>to(), states);
    }

    /**
     * Creates a predicate that returns {@code true} for a specific state transition.
     *
     * @param from From state.
     * @param to To state.
     * @param <T> State type.
     * @return A state transition filter.
     */
    public static <T> Predicate<Transition<T>> transition(final T from, final T to) {
      @SuppressWarnings("unchecked")
      Predicate<Transition<T>> fromFilter = from(from);
      @SuppressWarnings("unchecked")
      Predicate<Transition<T>> toFilter = to(to);
      return Predicates.and(fromFilter, toFilter);
    }

    public T getFrom() {
      return from;
    }

    public T getTo() {
      return to;
    }

    public boolean isAllowed() {
      return allowed;
    }

    /**
     * Checks whether this transition represents a state change, which means that the 'to' state is
     * not equal to the 'from' state, and the transition is allowed.
     *
     * @return {@code true} if the state was changed, {@code false} otherwise.
     */
    public boolean isValidStateChange() {
      return isAllowed() && !from.equals(to);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Transition)) {
        return false;
      }

      if (o == this) {
        return true;
      }

      Transition other = (Transition) o;
      return from.equals(other.from) && to.equals(other.to);
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(from)
          .append(to)
          .toHashCode();
    }

    @Override
    public String toString() {
      String str = from.toString() + " -> " + to.toString();
      if (!isAllowed()) {
        str += " (not allowed)";
      }
      return str;
    }
  }
}
