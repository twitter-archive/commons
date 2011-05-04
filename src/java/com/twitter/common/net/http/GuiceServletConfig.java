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

package com.twitter.common.net.http;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;

import javax.servlet.ServletContextEvent;
import java.util.logging.Logger;

/**
 * A wrapper around the GuiceServletContextListener that has access to the injector.
 *
 * @author Florian Leibert
 */
public class GuiceServletConfig extends GuiceServletContextListener {
  private final Injector injector;

  @Inject
  public GuiceServletConfig(Injector injector) {
    this.injector = Preconditions.checkNotNull(injector);
  }

  @Override
  protected Injector getInjector() {
    return injector;
  }
}
