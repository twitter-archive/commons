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

package com.twitter.common.webassets.bootstrap;

import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import com.google.inject.AbstractModule;

import com.twitter.common.application.http.Registration;

/**
 * A binding module to register bootstrap HTTP assets.
 */
public final class BootstrapModule extends AbstractModule {

  private void register(String mountPath, String resourcePath, String contentType) {
    Registration.registerHttpAsset(
        binder(),
        "/" + mountPath,
        Resources.getResource(BootstrapModule.class, resourcePath),
        contentType,
        true);
  }

  @Override
  protected void configure() {
    register(
        "css/bootstrap-responsive.min.css",
        "css/bootstrap-responsive-2.1.1.min.css",
        MediaType.CSS_UTF_8.toString());
    register(
        "css/bootstrap.min.css",
        "css/bootstrap-2.1.1.min.css",
        MediaType.CSS_UTF_8.toString());
    register(
        "img/glyphicons-halflings-white.png",
        "img/glyphicons-halflings-white-2.1.1.png",
        MediaType.PNG.toString());
    register(
        "img/glyphicons-halflings.png",
        "img/glyphicons-halflings-2.1.1.png",
        MediaType.PNG.toString());
    register(
        "js/bootstrap.min.js",
        "js/bootstrap-2.1.1.min.js",
        MediaType.JAVASCRIPT_UTF_8.toString());
  }
}
