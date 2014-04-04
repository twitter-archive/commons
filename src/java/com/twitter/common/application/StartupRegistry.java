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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import com.twitter.common.base.ExceptionalCommand;

/**
 * A registry that executes a set of commands.  The registry will synchronously execute commands
 * when {@link #execute()} is invoked, returning early if any action throws an exception.
 * Only one call to {@link #execute()} will have an effect, all subsequent calls will be ignored.
 */
public class StartupRegistry implements ExceptionalCommand<Exception> {

  private static final Logger LOG = Logger.getLogger(StartupRegistry.class.getName());

  private final Set<ExceptionalCommand> startupActions;
  private final AtomicBoolean started = new AtomicBoolean(false);

  @Inject
  public StartupRegistry(@StartupStage Set<ExceptionalCommand> startupActions) {
    this.startupActions = Preconditions.checkNotNull(startupActions);
  }

  @Override
  public void execute() throws Exception {
    if (!started.compareAndSet(false, true)) {
      LOG.warning("Startup actions cannot be executed more than once, ignoring.");
    }

    for (ExceptionalCommand<?> startupAction : startupActions) {
      startupAction.execute();
    }
  }
}
