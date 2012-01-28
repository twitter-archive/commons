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

package com.twitter.common.testing;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.testing.junit4.TearDownTestCase;

import org.junit.Test;

import com.twitter.common.base.Command;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author John Sirois
 */
public class TearDownRegistryTest extends TearDownTestCase {

  @Test
  public void testTearDown() {
    TearDownRegistry tearDownRegistry = new TearDownRegistry(this);
    final AtomicBoolean actionExecuted = new AtomicBoolean(false);
    tearDownRegistry.addAction(new Command() {
      @Override public void execute() {
        actionExecuted.set(true);
      }
    });

    assertFalse(actionExecuted.get());
    tearDown();
    assertTrue(actionExecuted.get());
  }
}
