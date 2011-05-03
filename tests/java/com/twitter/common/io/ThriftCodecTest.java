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

package com.twitter.common.io;

import com.google.common.base.Function;
import com.twitter.common.thrift.testing.TestThriftTypes.Struct;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author John Sirois
 */
public class ThriftCodecTest {

  @Test
  public void testRoundTripJSON() throws IOException {
    testRoundTrip(ThriftCodec.JSON_PROTOCOL);
  }

  @Test
  public void testRoundTripBinary() throws IOException {
    testRoundTrip(ThriftCodec.BINARY_PROTOCOL);
  }

  @Test
  public void testRoundTripCompact() throws IOException {
    testRoundTrip(ThriftCodec.COMPACT_PROTOCOL);
  }

  private void testRoundTrip(Function<TTransport, TProtocol> protocolFactory) throws IOException {
    Codec<Struct> codec = new ThriftCodec<Struct>(Struct.class, protocolFactory);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    codec.serialize(new Struct("jake", "jones"), sink);
    Struct struct = codec.deserialize(new ByteArrayInputStream(sink.toByteArray()));
    assertEquals("jake", struct.getName());
    assertEquals("jones", struct.getValue());
  }
}
