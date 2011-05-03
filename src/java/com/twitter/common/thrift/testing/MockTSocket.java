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

package com.twitter.common.thrift.testing;

import org.apache.thrift.transport.TSocket;

/**
 * @author William Farner
 */
public class MockTSocket extends TSocket {
  public static final String HOST = "dummyHost";
  public static final int PORT = 1000;

  private boolean connected = false;

  public MockTSocket() {
    super(HOST, PORT);
  }

  @Override
  public void open() {
    connected = true;
    // TODO(William Farner): Allow for failure injection here by throwing TTransportException.
  }

  @Override
  public boolean isOpen() {
    return connected;
  }

  public void close() {
    connected = false;
  }
}
