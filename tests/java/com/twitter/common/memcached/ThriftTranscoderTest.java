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

package com.twitter.common.memcached;

import com.google.common.base.Function;
import com.twitter.common.io.ThriftCodec;
import com.twitter.common.thrift.testing.TestThriftTypes.Struct;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author John Sirois
 */
public class ThriftTranscoderTest {

  @Test
  public void testRoundTripJSON() {
    testRoundTrip(ThriftCodec.JSON_PROTOCOL);
  }

  @Test
  public void testRoundTripBinary() {
    testRoundTrip(ThriftCodec.BINARY_PROTOCOL);
  }

  @Test
  public void testRoundTripCompact() {
    testRoundTrip(ThriftCodec.COMPACT_PROTOCOL);
  }

  private void testRoundTrip(Function<TTransport, TProtocol> protocolFactory) {
    ThriftTranscoder<Struct> transcoder =
        new ThriftTranscoder<Struct>(Struct.class, protocolFactory);
    Struct struct = transcoder.decode(transcoder.encode(new Struct("jake", "jones")));
    assertEquals("jake", struct.getName());
    assertEquals("jones", struct.getValue());
  }
}
