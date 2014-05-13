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

import com.twitter.common.base.ExceptionalSupplier;
import com.twitter.common.base.Supplier;
import com.twitter.common.testing.easymock.EasyMockTest;

import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;

/**
 * @author John Sirois
 */
public class BackoffHelperTest extends EasyMockTest {
  private Clock clock;
  private BackoffStrategy backoffStrategy;
  private BackoffHelper backoffHelper;

  @Before
  public void setUp() {
    clock = createMock(Clock.class);
    backoffStrategy = createMock(BackoffStrategy.class);
    backoffHelper = new BackoffHelper(clock, backoffStrategy);
  }

  @Test
  public void testDoUntilSuccess() throws Exception {
    Supplier<Boolean> task = createMock(new Clazz<Supplier<Boolean>>() { });

    expect(task.get()).andReturn(false);
    expect(backoffStrategy.shouldContinue(0L)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(0L)).andReturn(42L);
    clock.waitFor(42L);
    expect(task.get()).andReturn(true);

    control.replay();

    backoffHelper.doUntilSuccess(task);

    control.verify();
  }

  @Test
  public void testDoUntilResult() throws Exception {
    Supplier<String> task = createMock(new Clazz<Supplier<String>>() { });

    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(0)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(0)).andReturn(42L);
    clock.waitFor(42L);
    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(42L)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(42L)).andReturn(37L);
    clock.waitFor(37L);
    expect(task.get()).andReturn("jake");

    control.replay();

    assertEquals("jake", backoffHelper.doUntilResult(task));

    control.verify();
  }

  @Test
  public void testDoUntilResultTransparentException() throws Exception {
    ExceptionalSupplier<String, IOException> task =
        createMock(new Clazz<ExceptionalSupplier<String, IOException>>() { });

    IOException thrown = new IOException();
    expect(task.get()).andThrow(thrown);

    control.replay();

    try {
      backoffHelper.doUntilResult(task);
      fail("Expected exception to be bubbled");
    } catch (IOException e) {
      assertSame(thrown, e);
    }

    control.verify();
  }

  @Test
  public void testDoUntilResultMaxSuccess() throws Exception {
    Supplier<String> task = createMock(new Clazz<Supplier<String>>() { });

    BackoffHelper maxBackoffHelper = new BackoffHelper(clock, backoffStrategy);

    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(0L)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(0)).andReturn(42L);
    clock.waitFor(42L);
    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(42L)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(42L)).andReturn(37L);
    clock.waitFor(37L);
    expect(task.get()).andReturn("jake");

    control.replay();

    assertEquals("jake", maxBackoffHelper.doUntilResult(task));

    control.verify();
  }

  @Test
  public void testDoUntilResultMaxReached() throws Exception {
    Supplier<String> task = createMock(new Clazz<Supplier<String>>() { });

    BackoffHelper maxBackoffHelper = new BackoffHelper(clock, backoffStrategy);

    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(0L)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(0)).andReturn(42L);
    clock.waitFor(42L);
    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(42L)).andReturn(true);
    expect(backoffStrategy.calculateBackoffMs(42L)).andReturn(37L);
    clock.waitFor(37L);
    expect(task.get()).andReturn(null);
    expect(backoffStrategy.shouldContinue(37L)).andReturn(false);

    control.replay();

    try {
      maxBackoffHelper.doUntilResult(task);
      fail("Expected max retry failure");
    } catch (BackoffHelper.BackoffStoppedException e) {
      // expected
    }

    control.verify();
  }

  @Test
  public void testDoUntilSuccessTransparentException() throws Exception {
    Supplier<Boolean> task = createMock(new Clazz<Supplier<Boolean>>() { });

    IllegalArgumentException thrown = new IllegalArgumentException();
    expect(task.get()).andThrow(thrown);

    control.replay();

    try {
      backoffHelper.doUntilSuccess(task);
      fail("Expected exception to be bubbled");
    } catch (IllegalArgumentException e) {
      assertSame(thrown, e);
    }

    control.verify();
  }
}
