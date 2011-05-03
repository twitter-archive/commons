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

package com.twitter.common.inject;

import com.google.inject.AbstractModule;

/**
 * A convenience base class for modules that do all their binding via provider methods.
 *
 * @author John Sirois
 */
public abstract class ProviderMethodModule extends AbstractModule {

  /**
   * Does no binding; subclasses should implement provider methods.
   */
  @Override
  protected final void configure() {
    // noop
  }
}
