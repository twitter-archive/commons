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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

import com.twitter.common.application.http.Registration;
import com.twitter.common.net.http.handlers.ThriftServlet;
import com.twitter.common.net.monitoring.TrafficMonitor;

/**
 * Binding module for thrift traffic monitor servlets, to ensure an empty set is available for
 * the thrift traffic monitor servlet.
 *
 * @author William Farner
 */
public class ThriftModule extends AbstractModule {
  @Override
  protected void configure() {
    // Make sure that there is at least an empty set bound to client andserver monitors.
    Multibinder.newSetBinder(binder(), TrafficMonitor.class,
        Names.named(ThriftServlet.THRIFT_CLIENT_MONITORS));
    Multibinder.newSetBinder(binder(), TrafficMonitor.class,
        Names.named(ThriftServlet.THRIFT_SERVER_MONITORS));

    Registration.registerServlet(binder(), "/thrift", ThriftServlet.class, false);
  }
}
