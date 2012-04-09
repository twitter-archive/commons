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

package com.twitter.common.application.http;

import java.util.logging.Logger;

import com.google.inject.Inject;

import com.twitter.common.application.Lifecycle;

/**
 * The default quit handler to use, which invokes {@link Lifecycle#shutdown()}.
 *
 * @author William Farner
 */
public class DefaultQuitHandler implements Runnable {

  private static final Logger LOG = Logger.getLogger(DefaultQuitHandler.class.getName());

  private final Lifecycle lifecycle;

  @Inject
  public DefaultQuitHandler(Lifecycle lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Override
  public void run() {
    LOG.info("Instructing lifecycle to destroy.");
    lifecycle.shutdown();
  }
}
