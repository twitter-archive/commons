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

package com.twitter.common.args.parsers;

import java.net.InetSocketAddress;

import com.twitter.common.net.InetSocketAddressHelper;

/**
 * InetSocketAddress parser.
 *
 * @author William Farner
 */
public class InetSocketAddressParser extends NonParameterizedTypeParser<InetSocketAddress> {

  public InetSocketAddressParser() {
    super(InetSocketAddress.class);
  }

  @Override
  public InetSocketAddress doParse(String raw) {
    return InetSocketAddressHelper.parse(raw);
  }
}
