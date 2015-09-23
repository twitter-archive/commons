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
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.twitter.common.base.MoreSuppliers;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@code Codec} that can encode and decode thrift structs.
 */
public class ThriftCodec<T extends TBase> implements Codec<T> {

  public static final Function<TTransport, TProtocol> JSON_PROTOCOL =
        new Function<TTransport, TProtocol>() {
          @Override public TProtocol apply(TTransport transport) {
            return new TJSONProtocol(transport);
          }
        };

  public static final Function<TTransport, TProtocol> BINARY_PROTOCOL =
        new Function<TTransport, TProtocol>() {
          @Override public TProtocol apply(TTransport transport) {
            return new TBinaryProtocol(transport);
          }
        };

  public static final Function<TTransport, TProtocol> COMPACT_PROTOCOL =
      new Function<TTransport, TProtocol>() {
        @Override public TProtocol apply(TTransport transport) {
          return new TCompactProtocol(transport);
        }
      };

  private final Supplier<T> templateSupplier;
  private final Function<TTransport, TProtocol> protocolFactory;

  public static <T extends TBase> ThriftCodec<T> create(final Class<T> thriftStructType,
      Function<TTransport, TProtocol> protocolFactory) {
    return new ThriftCodec<T>(MoreSuppliers.of(thriftStructType), protocolFactory);
  }

  /**
   * @deprecated use {@link ThriftCodec#create(Class, Function)} instead.
   */
  @Deprecated
  public ThriftCodec(final Class<T> thriftStructType,
      Function<TTransport, TProtocol> protocolFactory) {
    this(MoreSuppliers.of(thriftStructType), protocolFactory);
  }

  public ThriftCodec(Supplier<T> templateSupplier,
      Function<TTransport, TProtocol> protocolFactory) {
    this.templateSupplier = Preconditions.checkNotNull(templateSupplier);
    this.protocolFactory = Preconditions.checkNotNull(protocolFactory);
  }

  @Override
  public void serialize(T item, OutputStream sink) throws IOException {
    Preconditions.checkNotNull(item);
    Preconditions.checkNotNull(sink);
    try {
      item.write(protocolFactory.apply(new TIOStreamTransport(null, sink)));
    } catch (TException e) {
      throw new IOException("Problem serializing thrift struct: " + item, e);
    }
  }

  @Override
  public T deserialize(InputStream source) throws IOException {
    Preconditions.checkNotNull(source);
    T template = templateSupplier.get();
    try {
      template.read(protocolFactory.apply(new TIOStreamTransport(source, null)));
    } catch (TException e) {
      throw new IOException("Problem de-serializing thrift struct from stream", e);
    }
    return template;
  }
}
