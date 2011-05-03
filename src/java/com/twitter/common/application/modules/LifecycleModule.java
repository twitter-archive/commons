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

package com.twitter.common.application.modules;

import java.lang.annotation.Annotation;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Singleton;

import com.twitter.common.application.ActionRegistry;
import com.twitter.common.application.StartupStage;
import com.twitter.common.application.Lifecycle;
import com.twitter.common.application.ActionController;
import com.twitter.common.application.ShutdownStage;

/**
 * Binding module for startup and shutdown controller and registries.
 *
 * Bindings provided by this module:
 * <ul>
 *   <li>{@code @StartupStage LifecycleActionController} - Action controller for application
 *       startup.
 *   <li>{@code @StartupStage ActionRegistry} - Action registry for application startup.
 *   <li>{@code @ShutdownStage LifecycleActionController} - Action controller for application
 *       shutdown.
 *   <li>{@code @ShutdownStage ActionRegistry} - Action registry for application shutdown.
 * </ul>
 *
 * @author William Farner
 */
public class LifecycleModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Lifecycle.class).in(Singleton.class);
    bindController(StartupStage.class, binder());
    bindController(ShutdownStage.class, binder());

    requestStaticInjection(Init.class);
  }

  private static void bindController(Class<? extends Annotation> annotationType, Binder binder) {
    Key<ActionController> controllerKey =
        Key.get(ActionController.class, annotationType);
    binder.bind(controllerKey).to(ActionController.class).in(Singleton.class);
    binder.bind(Key.get(ActionRegistry.class, annotationType)).to(controllerKey);
  }

  public static class Init {
    @Inject private static void setupShutdownHook(
        @ShutdownStage final ActionController actionController) {

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override public void run() {
          actionController.execute();
        }
      }, actionController.getClass().getSimpleName() + "-ShutdownHook"));
    }
  }
}
