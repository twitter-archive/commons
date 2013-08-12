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
import com.twitter.common.io.Codec;
import com.twitter.common.io.ThriftCodec;
import net.spy.memcached.CachedData;
import net.spy.memcached.transcoders.Transcoder;
import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A memcached value transcoder that can transcode for a given thrift type.
 *
 * @param <T> the type to transcode
 *
 * @author John Sirois
 */
public class ThriftTranscoder<T extends TBase> implements Transcoder<T> {

  /**
   * A convenience method that creates a new Transcoder for the given thrift type using the thrift
   * compact binary encoding.
   *
   * @param thriftStructType the type to transcode
   * @param <T> the type to transcode
   * @return a new Transcoder that can transcode the given thrift type
   */
  public static <T extends TBase> Transcoder<T> of(Class<T> thriftStructType) {
    return new ThriftTranscoder<T>(thriftStructType, ThriftCodec.COMPACT_PROTOCOL);
  }

  private final Codec<T> codec;

  /**
   * @param thriftStructType the type to transcode
   * @param encoder a factory that can produce a transport for a given protocol
   */
  public ThriftTranscoder(Class<T> thriftStructType, Function<TTransport, TProtocol> encoder) {
    this.codec = ThriftCodec.create(thriftStructType, encoder);
  }

  @Override
  public CachedData encode(T thriftStruct) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    if (thriftStruct != null) {
      try {
        codec.serialize(thriftStruct, buffer);
      } catch (IOException e) {
        throw new RuntimeException("Unable to encode value: " + thriftStruct, e);
      }
    }
    return new CachedData(0, buffer.toByteArray(), CachedData.MAX_SIZE);
  }

  @Override
  public T decode(CachedData cachedData) {
    byte[] data = cachedData.getData();
    if (data == null || data.length == 0) {
      return null;
    }

    try {
      return codec.deserialize(new ByteArrayInputStream(data));
    } catch (IOException e) {
      throw new RuntimeException("Unable to decode data: " + data, e);
    }
  }

  @Override
  public boolean asyncDecode(CachedData cachedData) {
    return false;
  }

  @Override
  public int getMaxSize() {
    return CachedData.MAX_SIZE;
  }
}
