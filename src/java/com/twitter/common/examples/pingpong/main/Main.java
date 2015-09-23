// =================================================================================================
// Copyright 2013 Twitter, Inc.
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

package com.twitter.common.examples.pingpong.main;

import java.net.InetSocketAddress;
import java.util.Arrays;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import com.twitter.common.application.AbstractApplication;
import com.twitter.common.application.AppLauncher;
import com.twitter.common.application.Lifecycle;
import com.twitter.common.application.http.Registration;
import com.twitter.common.application.modules.HttpModule;
import com.twitter.common.application.modules.LogModule;
import com.twitter.common.application.modules.StatsModule;
import com.twitter.common.args.Arg;
import com.twitter.common.args.CmdLine;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.base.Closure;
import com.twitter.common.examples.pingpong.handler.PingHandler;

/**
 * An application that serves HTTP requests to /ping/{message}/{ttl}, and
 * sends similar pings back to a pre-defined ping target.
 */
public class Main extends AbstractApplication {
  @NotNull
  @CmdLine(name = "ping_target", help = "Host to ping after starting up.")
  private static final Arg<InetSocketAddress> PING_TARGET = Arg.create();

  @Inject private Lifecycle lifecycle;

  @Override
  public void run() {
    lifecycle.awaitShutdown();
  }

  @Override
  public Iterable<? extends Module> getModules() {
    return Arrays.asList(
        new HttpModule(),
        new LogModule(),
        new StatsModule(),
        new AbstractModule() {
          @Override protected void configure() {
            bind(new TypeLiteral<Closure<String>>() { }).toInstance(
                new Closure<String>() {
                  private final Client http = Client.create();
                  @Override public void execute(String path) {
                    http.asyncResource("http://" + PING_TARGET.get() + path).get(String.class);
                  }
                });

            install(new JerseyServletModule() {
              @Override protected void configureServlets() {
                filter("/ping*").through(
                    GuiceContainer.class, ImmutableMap.<String, String>of());
                Registration.registerEndpoint(binder(), "/ping");
                bind(PingHandler.class);
              }
            });
          }
        }
    );
  }

  public static void main(String[] args) {
    AppLauncher.launch(Main.class, args);
  }
}
