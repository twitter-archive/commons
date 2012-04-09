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

package com.twitter.common.application;

import java.lang.reflect.Field;

import com.google.common.base.Predicates;

import org.junit.Test;

import com.twitter.common.args.Arg;
import com.twitter.common.args.ArgFilters;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class AppLauncherTest {

  public static class TestApp1 extends AbstractApplication {
    @NotNull
    @CmdLine(name = "user", help = "a username")
    static final Arg<String> USER = Arg.create();

    private static boolean hasRun;

    @Override public void run() {
      hasRun = true;
    }
  }

  @Test
  public void testLaunch1() {
    AppLauncher.launch(TestApp1.class, ArgFilters.selectClass(TestApp1.class), "-user", "jake");
    assertTrue(TestApp1.hasRun);
    assertEquals("jake", TestApp1.USER.get());
  }

  public static class TestApp2 extends AbstractApplication {
    @NotNull
    @CmdLine(name = "user", help = "a username")
    static final Arg<String> USER = Arg.create(null);

    private static boolean hasRun;

    @Override public void run() {
      hasRun = true;
    }
  }

  @Test
  public void testLaunch2() {
    // We filter out the NotNull Arg so we should be able to launch without specifying it.
    AppLauncher.launch(TestApp2.class, Predicates.<Field>alwaysFalse());
    assertTrue(TestApp2.hasRun);
    assertNull(TestApp2.USER.get());
  }
}
