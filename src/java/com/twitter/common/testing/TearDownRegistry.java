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

import com.google.common.base.Preconditions;
import com.google.common.testing.TearDown;
import com.google.common.testing.TearDownAccepter;

import com.twitter.common.application.ShutdownRegistry;
import com.twitter.common.base.ExceptionalCommand;

/**
 * An action registry suitable for use as a shutdownRegistry in tests that extend
 * {@link com.google.common.testing.junit4.TearDownTestCase}.
 *
 * @author John Sirois
 */
public class TearDownRegistry implements ShutdownRegistry {
  private final TearDownAccepter tearDownAccepter;

  /**
   * Creates a new tear down registry that delegates execution of shutdown actions to a
   * {@code tearDownAccepter}.
   *
   * @param tearDownAccepter A tear down accepter that will be used to register shutdown actions
   *     with.
   */
  public TearDownRegistry(TearDownAccepter tearDownAccepter) {
    this.tearDownAccepter = Preconditions.checkNotNull(tearDownAccepter);
  }

  @Override
  public <E extends Exception, T extends ExceptionalCommand<E>> void addAction(final T action) {
    tearDownAccepter.addTearDown(new TearDown() {
      @Override public void tearDown() throws Exception {
        action.execute();
      }
    });
  }
}
