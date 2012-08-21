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

import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import java.util.Map;

/**
 * A HTTP server dispatcher. Supports registering handlers for different
 * URI paths, which will be called when a request is received.
 * @author Florian Leibert
 */
public interface HttpServerDispatch {

  /**
   * Opens the HTTP server on the given port.
   *
   * @param port The port to listen on.
   * @return {@code true} if the server started successfully on the port, {@code false} otherwise.
   */
  boolean listen(int port);

  /**
   * Opens the HTTP server on random port within the given range.
   *
   * @param minPort The minimum port number to listen on.
   * @param maxPort The maximum port number to listen on.
   * @return {@code true} if the server started successfully on the port, {@code false} otherwise.
   */
  boolean listen(int minPort, int maxPort);

  /**
   * @return true if the underlying HttpServer is started, false otherwise.
   */
  boolean isStarted();

  /**
   * @return the port the underlying HttpServer is listening on, which requires
   * the underlying HttpServer to be started and listening.
   */
  int getPort();

  /**
   * Stops the HTTP server.
   */
  void stop();

  /**
   * Registers a URI handler, replacing the existing handler if it exists.
   *
   * @param path The URI path that the handler should be called for.
   * @param handler The handler to call.
   * @param initParams An optional map of servlet init parameter names and their values.
   * @param silent Whether to display the registered handler in the root "/" response.
   *               Useful for handlers that you want to avoid accidental clicks on.
   */
  void registerHandler(String path, HttpServlet handler,
                       @Nullable Map<String, String> initParams, boolean silent);
}
