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

import com.google.common.collect.ImmutableSet;
import com.twitter.common.base.Command;
import com.twitter.common.base.Commands;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.Supplier;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the functionality of StateMachine.
 *
 * @author William Farner
 */
public class StateMachineTest {
  private static final String NAME = "State machine.";

  private static final String A = "A";
  private static final String B = "B";
  private static final String C = "C";
  private static final String D = "D";

  @Test
  public void testEmptySM() {
    try {
      StateMachine.builder(NAME).build();
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void testMachineNoInit() {
    try {
      StateMachine.<String>builder(NAME)
          .addState(A, B)
          .build();
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void testBasicFSM() {
    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .addState(B, C)
        .addState(C, D)
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeState(fsm, D);
  }

  @Test
  public void testLoopingFSM() {
    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .addState(B, C)
        .addState(C, B, D)
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeState(fsm, B);
    changeState(fsm, C);
    changeState(fsm, B);
    changeState(fsm, C);
    changeState(fsm, D);
  }

  @Test
  public void testMachineUnknownState() {
    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .addState(B, C)
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeStateFail(fsm, D);
  }

  @Test
  public void testMachineBadTransition() {
    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .addState(B, C)
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeStateFail(fsm, B);
  }

  @Test
  public void testMachineSelfTransitionAllowed() {
    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, A)
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, A);
    changeState(fsm, A);
  }

  @Test
  public void testMachineSelfTransitionDisallowed() {
    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build();

    assertThat(fsm.getState(), is(A));
    changeStateFail(fsm, A);
    changeStateFail(fsm, A);
  }

  @Test
  public void testCheckStateMatches() {
    StateMachine<String> stateMachine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build();
    stateMachine.checkState(A);
    stateMachine.transition(B);
    stateMachine.checkState(B);
  }

  @Test(expected = IllegalStateException.class)
  public void testCheckStateFails() {
    StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build()
        .checkState(B);
  }

  @Test
  public void testDoInStateMatches() {
    StateMachine<String> stateMachine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build();

    int amount = stateMachine.doInState(A, new Supplier<Integer>() {
      @Override public Integer get() {
        return 42;
      }
    });
    assertThat(amount, is(42));

    stateMachine.transition(B);

    String name = stateMachine.doInState(B, new Supplier<String>() {
      @Override public String get() {
        return "jake";
      }
    });
    assertThat(name, is("jake"));
  }

  @Test
  public void testDoInStateConcurrently() throws InterruptedException {
    final StateMachine<String> stateMachine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build();

    final BlockingQueue<Integer> results = new LinkedBlockingQueue<Integer>();

    final CountDownLatch supplier1Proceed = new CountDownLatch(1);
    final ExceptionalSupplier<Void, RuntimeException> supplier1 =
        Commands.asSupplier(new Command() {
          @Override public void execute() {
            results.offer(1);
            try {
              supplier1Proceed.await();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        });

    final CountDownLatch supplier2Proceed = new CountDownLatch(1);
    final ExceptionalSupplier<Void, RuntimeException> supplier2 =
        Commands.asSupplier(new Command() {
          @Override public void execute() {
            results.offer(2);
            try {
              supplier2Proceed.await();
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        });

    Thread thread1 = new Thread(new Runnable() {
      @Override public void run() {
        stateMachine.doInState(A, supplier1);
      }
    });

    Thread thread2 = new Thread(new Runnable() {
      @Override public void run() {
        stateMachine.doInState(A, supplier2);
      }
    });

    Thread thread3 = new Thread(new Runnable() {
      @Override public void run() {
        stateMachine.transition(B);
      }
    });

    thread1.start();
    thread2.start();

    Integer result1 = results.take();
    Integer result2 = results.take();
    // we know 1 and 2 have the read lock held

    thread3.start(); // should be blocked by read locks in place

    assertThat(ImmutableSet.of(result1, result2), is(ImmutableSet.of(1, 2)));
    assertTrue(results.isEmpty());

    supplier1Proceed.countDown();
    supplier2Proceed.countDown();

    thread1.join();
    thread2.join();
    thread3.join();

    assertThat(B, is(stateMachine.getState()));
  }

  @Test(expected = IllegalStateException.class)
  public void testDoInStateFails() {
    StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build()
        .doInState(B, Commands.asSupplier(Commands.NOOP));
  }

  private static void changeState(StateMachine<String> machine, String to, boolean expectAllowed) {
    boolean allowed = true;
    try {
      machine.transition(to);
      assertThat(machine.getState(), is(to));
    } catch (StateMachine.IllegalStateTransitionException e) {
      allowed = false;
    }

    assertThat(allowed, is(expectAllowed));
  }

  private static void changeState(StateMachine<String> machine, String to) {
    changeState(machine, to, true);
  }

  private static void changeStateFail(StateMachine<String> machine, String to) {
    changeState(machine, to, false);
  }
}
