// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

package com.twitter.common.webassets.jquery;

import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import com.google.inject.AbstractModule;

import com.twitter.common.application.http.Registration;

/**
 * A binding module to register jQuery HTTP assets.
 */
public final class JQueryModule extends AbstractModule {

  @Override
  protected void configure() {
    Registration.registerHttpAsset(
        binder(),
        "/js/jquery.min.js",
        Resources.getResource(JQueryModule.class, "js/jquery-1.8.2.min.js"),
        MediaType.JAVASCRIPT_UTF_8.toString(),
        true);
  }
}
