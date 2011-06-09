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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

/**
 * A codec that composes two codecs: a primary and a compatibility codec. It always serializes with
 * the primary codec, but can make a decision on deserialization based on the first few bytes of the
 * serialized format whether to use the compatibility codec. This allows for easier transition
 * between storage formats as the codec remains able to read the old serialized format.
 *
 * @author Attila Szegedi
 *
 * @param <T> the type of objects this codec is for.
 */
public class CompatibilityCodec<T> implements Codec<T> {
  private final Codec<T> primaryCodec;
  private final Codec<T> secondaryCodec;
  private final int prefixLength;
  private final Predicate<byte[]> discriminator;

  private CompatibilityCodec(Codec<T> primaryCodec, Codec<T> secondaryCodec, int prefixLength,
      Predicate<byte[]> discriminator) {
    Preconditions.checkNotNull(primaryCodec);
    Preconditions.checkNotNull(secondaryCodec);
    this.primaryCodec = primaryCodec;
    this.secondaryCodec = secondaryCodec;
    this.prefixLength = prefixLength;
    this.discriminator = discriminator;
  }

  /**
   * Creates a new compatibility codec instance.
   *
   * @param primaryCodec the codec used to serialize objects, as well as deserialize them when the
   *          first byte of the serialized format matches the discriminator.
   * @param secondaryCodec the codec used to deserialize objects when the first byte of the
   *          serialized format does not match the discriminator.
   * @param prefixLength the length, in bytes, of the prefix of the message that is inspected for
   *          determining the format.
   * @param discriminator a predicate that will receive an array of at most prefixLength bytes
   *          (it can receive less if the serialized format is shorter) and has to return true
   *          if the primary codec should be used for deserialization, otherwise false.
   */
  public static <T> CompatibilityCodec<T> create(Codec<T> primaryCodec, Codec<T> secondaryCodec,
      int prefixLength, Predicate<byte[]> discriminator) {
    return new CompatibilityCodec<T>(primaryCodec, secondaryCodec, prefixLength, discriminator);
  }

  @Override
  public T deserialize(InputStream source) throws IOException {
    final PushbackInputStream in = new PushbackInputStream(source, prefixLength);
    final byte[] prefix = readAtMostPrefix(in);
    in.unread(prefix);
    return (discriminator.apply(prefix) ? primaryCodec : secondaryCodec).deserialize(in);
  }

  private byte[] readAtMostPrefix(InputStream in) throws IOException {
    final byte[] prefix = new byte[prefixLength];
    int read = 0;
    do {
      final int readNow = in.read(prefix, read, prefixLength - read);
      if (readNow == -1) {
        byte[] newprefix = new byte[read];
        System.arraycopy(prefix, 0, newprefix, 0, read);
        return newprefix;
      }
      read += readNow;
    } while (read < prefixLength);
    return prefix;
  }

  @Override
  public void serialize(T item, OutputStream sink) throws IOException {
    primaryCodec.serialize(item, sink);
  }
}
