// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one or more contributor license
// agreements.  See the NOTICE file distributed with this work for additional information regarding
// copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with the License.  You may
// obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express or implied.  See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.MorePreconditions;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

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
  private final Multimap<T, T> stateTransitions = HashMultimap.create();

  private volatile T currentState = null;
  private final Lock readLock;
  private final Lock writeLock;

  private StateMachine(String name) {
    this.name = MorePreconditions.checkNotBlank(name);

    ReadWriteLock stateLock = new ReentrantReadWriteLock(true /* fair */);
    readLock = stateLock.readLock();
    writeLock = stateLock.writeLock();
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
    Preconditions.checkNotNull(expectedState);

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

    Preconditions.checkNotNull(expectedState);
    Preconditions.checkNotNull(work);

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
   */
  public void transition(T nextState) throws IllegalStateTransitionException {
    writeLock.lock();
    try {
      if (stateTransitions.containsEntry(currentState, nextState)) {
        LOG.info(String.format("%s state machine transition %s -> %s",
            name, currentState, nextState));
        currentState = nextState;
      } else {
        throw new IllegalStateTransitionException(
            String.format("State transition from %s to %s is not allowed.", currentState,
                nextState));
      }
    } finally {
      writeLock.unlock();
    }
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
    private final StateMachine<T> instance;

    public Builder(String name) {
      instance = new StateMachine<T>(name);
    }

    /**
     * Sets the initial state for the state machine.
     *
     * @param state Initial state.
     * @return A reference to the builder.
     */
    public Builder<T> initialState(T state) {
      Preconditions.checkNotNull(state);
      instance.currentState = state;
      return this;
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
      Preconditions.checkNotNull(state);
      for (T transition : transitionStates) Preconditions.checkNotNull(transition);

      instance.stateTransitions.putAll(state, Arrays.asList(transitionStates));
      return this;
    }

    /**
     * Builds the state machine.
     *
     * @return A reference to the prepared state machine.
     */
    public StateMachine<T> build() {
      Preconditions.checkState(instance.currentState != null);
      Preconditions.checkState(!instance.stateTransitions.isEmpty());
      return instance;
    }
  }
}
