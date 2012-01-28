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

package com.twitter.common.text.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.apache.lucene.util.AttributeSource;

import com.twitter.common.text.token.TokenStream;

/**
 * Helper class to serialize a TokenStream into a byte array.
 *
 * A list of AttributeSerializers must be defined using the Builder, which serialize
 * and deserialize individual attributes.
 *
 * The same TokenStreamSerializer should be used for serialization/de-serialization, as the order
 * of the {@link AttributeSerializer}s must be consistent.
 */
public class TokenStreamSerializer {
  public static enum Version {
    VERSION_1
  }

  private static final Version CURRENT_VERSION = Version.VERSION_1;

  private final List<AttributeSerializer> attributeSerializers;

  private TokenStreamSerializer(List<AttributeSerializer> attributeSerializers) {
    this.attributeSerializers = attributeSerializers;
  }

  /**
   * Serialize the given TokenStream into a byte array using the provided AttributeSerializer(s).
   * Note that this method doesn't serialize the CharSequence of the TokenStream - the caller
   * has to take care of serializing this if necessary.
   */
  public final byte[] serialize(final TokenStream tokenStream) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    AttributeOutputStream output = new AttributeOutputStream(baos);

    for (AttributeSerializer serializer : attributeSerializers) {
      serializer.initialize(tokenStream, CURRENT_VERSION);
    }

    int numTokens = 0;

    while (tokenStream.incrementToken()) {
      serializeAttributes(output);
      numTokens++;
    }

    output.flush();

    byte[] data = baos.toByteArray();
    baos.close();
    baos = new ByteArrayOutputStream(8 + data.length);
    output = new AttributeOutputStream(baos);
    output.writeVInt(CURRENT_VERSION.ordinal());
    output.writeVInt(numTokens);
    output.write(data);
    output.flush();

    return baos.toByteArray();
  };


  /**
   * Deserializes the previously serialized TokenStream using the provided AttributeSerializer(s).
   *
   * This method only deserializes all Attributes; the CharSequence instance containing the text
   * must be provided separately.
   */
  public final TokenStream deserialize(final byte[] data,
                                       final CharSequence charSequence) throws IOException {
    Preconditions.checkNotNull(data);
    Preconditions.checkState(data.length > 0);

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    final AttributeInputStream input = new AttributeInputStream(bais);

    int ordinal = input.readVInt();
    if (ordinal > CURRENT_VERSION.ordinal()) {
      throw new IOException("Version of serialized data is newer than the version this serializer" +
                                "supports: " + ordinal + " > " + CURRENT_VERSION.ordinal());
    }

    final Version version = Version.values()[ordinal];
    final int numTokens = input.readVInt();

    TokenStream tokenStream = new TokenStream() {
      CharSequence chars;
      int token = 0;

      @Override public boolean incrementToken() {
        if (token < numTokens) {
          token++;
          try {
            deserializeAttributes(input, chars);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        }

        return false;
      }

      @Override
      public void reset(CharSequence input) {
        chars = input;
        token = 0;
      }
    };

    for (AttributeSerializer deserializer : attributeSerializers) {
      deserializer.initialize(tokenStream, version);
    }

    input.close();

    tokenStream.reset(charSequence);
    return tokenStream;
  };

  private void deserializeAttributes(AttributeInputStream input,
                                     CharSequence charSequence) throws IOException {
    for (AttributeSerializer serializer : attributeSerializers) {
      serializer.deserialize(input, charSequence);
    }
  }


  private void serializeAttributes(AttributeOutputStream output) throws IOException {
    for (AttributeSerializer serializer : attributeSerializers) {
      serializer.serialize(output);
    }
  }

  /**
   * Returns a new Builder to build a TokenStreamSerializer.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Defines how individual attributes a (de)serialized.
   */
  public interface AttributeSerializer {
    /**
     * Initialises this AttributeSerializer. This method should be used to get the attribute
     * instance from the TokenStream that this serializer handles. E.g.:
     *
     * CharSequenceTermAttribute termAtt =
     *                    attributeSource.addAttribute(CharSequenceTermAttribute.class);
     *
     */
    public abstract void initialize(AttributeSource attributeSource, Version version)
        throws IOException;

    /**
     * Serializes a single attribute.
     */
    public abstract void serialize(AttributeOutputStream output) throws IOException ;

    /**
     * Deserializes a single attribute.
     */
    public abstract void deserialize(AttributeInputStream input, CharSequence charSequence)
        throws IOException;
  }

  /**
   * Builds an TokenStreamSerializer.
   */
  public final static class Builder {
    private final List<AttributeSerializer> attributeSerializers = Lists.newLinkedList();

    /**
     * Adds an AttributeSerializer. The order in which the AttributeSerializers are added here
     * is the same order in which they will be called for serializing a Token.
     */
    public Builder add(AttributeSerializer serializer) {
      attributeSerializers.add(serializer);
      return this;
    }

    /**
     * Builds the TokenStreamSerializer.
     */
    public TokenStreamSerializer build() {
      return new TokenStreamSerializer(attributeSerializers);
    }
  }

  /**
   * A DataOutputStream that supports VInt-encoding.
   */
  public static class AttributeOutputStream extends DataOutputStream {
    @VisibleForTesting
    AttributeOutputStream(OutputStream output) {
      super(output);
    }

    /**
     * Writes a value using VInt encoding.
     */
    public final void writeVInt(int value) throws IOException {
      while ((value & ~0x7F) != 0) {
        writeByte((byte)((value & 0x7f) | 0x80));
        value >>>= 7;
      }
      writeByte((byte)value);
    }
  }

  /**
   * A DataInputStream that supports VInt-encoding.
   */
  public static class AttributeInputStream extends DataInputStream {
    @VisibleForTesting
    AttributeInputStream(InputStream input) {
      super(input);
    }

    /**
     * Reads a value using VInt encoding.
     */
    public final int readVInt() throws IOException {
      byte b = readByte();
      int value = b & 0x7F;
      for (int shift = 7; (b & 0x80) != 0; shift += 7) {
        b = readByte();
        value |= (b & 0x7F) << shift;
      }
      return value;
    }
  }
}
