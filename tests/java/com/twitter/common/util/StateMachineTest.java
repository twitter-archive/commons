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

import com.google.common.collect.ImmutableSet;

import com.twitter.common.base.Closure;
import com.twitter.common.base.Closures;
import com.twitter.common.base.Command;
import com.twitter.common.base.Commands;
import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.Supplier;
import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.StateMachine.Transition;
import com.twitter.common.util.StateMachine.Rule;

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
public class StateMachineTest extends EasyMockTest {
  private static final String NAME = "State machine.";

  private static final String A = "A";
  private static final String B = "B";
  private static final String C = "C";
  private static final String D = "D";

  @Test
  public void testEmptySM() {
    control.replay();

    try {
      StateMachine.builder(NAME).build();
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void testMachineNoInit() {
    control.replay();

    try {
      StateMachine.<String>builder(NAME)
          .addState(Rule.from(A).to(B))
          .build();
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  @Test
  public void testBasicFSM() {
    control.replay();

    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .addState(Rule.from(B).to(C))
        .addState(Rule.from(C).to(D))
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeState(fsm, D);
  }

  @Test
  public void testLoopingFSM() {
    control.replay();

    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .addState(Rule.from(B).to(C))
        .addState(Rule.from(C).to(B, D))
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
    control.replay();

    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .addState(Rule.from(B).to(C))
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeStateFail(fsm, D);
  }

  @Test
  public void testMachineBadTransition() {
    control.replay();

    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .addState(Rule.from(B).to(C))
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, B);
    changeState(fsm, C);
    changeStateFail(fsm, B);
  }

  @Test
  public void testMachineSelfTransitionAllowed() {
    control.replay();

    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(A))
        .build();

    assertThat(fsm.getState(), is(A));
    changeState(fsm, A);
    changeState(fsm, A);
  }

  @Test
  public void testMachineSelfTransitionDisallowed() {
    control.replay();

    StateMachine<String> fsm = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .build();

    assertThat(fsm.getState(), is(A));
    changeStateFail(fsm, A);
    changeStateFail(fsm, A);
  }

  @Test
  public void testCheckStateMatches() {
    control.replay();

    StateMachine<String> stateMachine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .build();
    stateMachine.checkState(A);
    stateMachine.transition(B);
    stateMachine.checkState(B);
  }

  @Test(expected = IllegalStateException.class)
  public void testCheckStateFails() {
    control.replay();

    StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
        .build()
        .checkState(B);
  }

  @Test
  public void testDoInStateMatches() {
    control.replay();

    StateMachine<String> stateMachine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B))
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
    control.replay();

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
    control.replay();

    StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .build()
        .doInState(B, Commands.asSupplier(Commands.NOOP));
  }

  @Test
  public void testNoThrowOnInvalidTransition() {
    control.replay();

    StateMachine<String> machine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(A, B)
        .throwOnBadTransition(false)
        .build();

    machine.transition(C);
    assertThat(machine.getState(), is(A));
  }

  private static final Clazz<Closure<Transition<String>>> TRANSITION_CLOSURE_CLZ =
      new Clazz<Closure<Transition<String>>>() {};

  @Test
  public void testTransitionCallbacks() {
    Closure<Transition<String>> anyTransition = createMock(TRANSITION_CLOSURE_CLZ);
    Closure<Transition<String>> fromA = createMock(TRANSITION_CLOSURE_CLZ);
    Closure<Transition<String>> fromB = createMock(TRANSITION_CLOSURE_CLZ);

    Transition<String> aToB = new Transition<String>(A, B, true);
    anyTransition.execute(aToB);
    fromA.execute(aToB);

    Transition<String> bToB = new Transition<String>(B, B, false);
    anyTransition.execute(bToB);
    fromB.execute(bToB);

    Transition<String> bToC = new Transition<String>(B, C, true);
    anyTransition.execute(bToC);
    fromB.execute(bToC);

    anyTransition.execute(new Transition<String>(C, B, true));

    Transition<String> bToD = new Transition<String>(B, D, true);
    anyTransition.execute(bToD);
    fromB.execute(bToD);

    control.replay();

    StateMachine<String> machine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule.from(A).to(B).withCallback(fromA))
        .addState(Rule.from(B).to(C, D).withCallback(fromB))
        .addState(Rule.from(C).to(B))
        .addState(Rule.from(D).noTransitions())
        .onAnyTransition(anyTransition)
        .throwOnBadTransition(false)
        .build();

    machine.transition(B);
    machine.transition(B);
    machine.transition(C);
    machine.transition(B);
    machine.transition(D);
  }

  @Test
  public void testFilteredTransitionCallbacks() {
    Closure<Transition<String>> aToBHandler = createMock(TRANSITION_CLOSURE_CLZ);
    Closure<Transition<String>> impossibleHandler = createMock(TRANSITION_CLOSURE_CLZ);

    aToBHandler.execute(new Transition<String>(A, B, true));

    control.replay();

    StateMachine<String> machine = StateMachine.<String>builder(NAME)
        .initialState(A)
        .addState(Rule
            .from(A).to(B, C)
            .withCallback(Closures.filter(Transition.to(B), aToBHandler)))
        .addState(Rule.from(B).to(A)
            .withCallback(Closures.filter(Transition.to(B), impossibleHandler)))
        .addState(Rule.from(C).noTransitions())
        .build();

    machine.transition(B);
    machine.transition(A);
    machine.transition(C);
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
