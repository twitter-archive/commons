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
    VERSION_1,
    VERSION_2
  }

  protected static final Version CURRENT_VERSION = Version.VERSION_2;

  private final List<AttributeSerializer> attributeSerializers;

  private final int attributeSerializersFingerprint;

  public TokenStreamSerializer(List<AttributeSerializer> attributeSerializers) {
    this.attributeSerializers = attributeSerializers;
    this.attributeSerializersFingerprint = computeFingerprint(attributeSerializers);
  }

  public static int computeFingerprint(List<AttributeSerializer> attributeSerializers) {
    int result = 0;
    int i = 0;
    for (AttributeSerializer attributeSerializer : attributeSerializers) {
      int hashCode = attributeSerializer.getClass().getName().hashCode();
      result = result ^ ((hashCode << i) | (hashCode >> i));
      i++;
    }
    return result;
  }

  /**
   * The fingerprint of the attribute serializers that are attached to this TokenStreamSerializer.
   */
  public int attributeSerializersFingerprint() {
    return attributeSerializersFingerprint;
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
    output.writeInt(attributeSerializersFingerprint);
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
   * @param data  the byte-serialized representation of the tokenstream.
   * @param charSequence  the text that was tokenized.
   * @return  a TokenStream object. Notice that, in order to support lucene-like TokenStream
   *          behavior, this object's reset method must only be used as reset(null) and will reset
   *          the TokenStream to its starting point.
   */
  public final TokenStream deserialize(final byte[] data,
                                       final CharSequence charSequence) throws IOException {
    return deserialize(data, 0, data.length, charSequence);
  }

  /**
   * Other form of deserialize that reads data from a "slice" in a byte array.
   */
  public final TokenStream deserialize(final byte[] data, int offset, int length,
                                       final CharSequence charSequence) throws IOException {
    Preconditions.checkNotNull(data);
    Preconditions.checkState(length > 0);
    Preconditions.checkState(data.length >= length);

    ByteArrayInputStream bais = new ByteArrayInputStream(data, offset, length);
    return deserialize(bais, charSequence);
  }

  /**
   * An unckecked Exception that we throw for version mismatch between the
   * serializer and the deserializer.
   */
  public static class VersionMismatchException extends RuntimeException {
    public VersionMismatchException(String msg) {
      super(msg);
    }
  }

  public static Version readVersionAndCheckFingerprint(
      AttributeInputStream input, int attributeSerializersFingerprint) throws IOException {
    int ordinal = input.readVInt();
    if (ordinal > CURRENT_VERSION.ordinal()) {
      throw new VersionMismatchException(
          "Version of serialized data is newer than the version this serializer" +
          "supports: " + ordinal + " > " + CURRENT_VERSION.ordinal());
    }
    if (ordinal >= Version.VERSION_2.ordinal()) {
      int fp = input.readInt();
      if (fp != attributeSerializersFingerprint) {
        throw new VersionMismatchException(
            "Attributes of serialized data are different than attributes of " +
            "this serializer: " + fp + " != " + attributeSerializersFingerprint);
      }
    }
    return Version.values()[ordinal];
  }

  /**
   * Other form of deserialize for a ByteArrayInputStream.
   */
  public final TokenStream deserialize(final ByteArrayInputStream bais, final CharSequence charSequence)
      throws IOException {
    final AttributeInputStream input = new AttributeInputStream(bais);

    TokenStream tokenStream = new TokenStream() {
      CharSequence chars = charSequence;
      // All other members are initialized in reset.
      int token;
      Version version;
      int numTokens;

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
      public void reset(CharSequence newChars) {
        Preconditions.checkArgument(newChars == null || newChars == chars,
            "this TokenStream does not do actual tokenization and only supports reset(null)");
        try {
          input.reset();
          bais.reset();
          version = readVersionAndCheckFingerprint(input, attributeSerializersFingerprint);
          numTokens = input.readVInt();
          for (AttributeSerializer deserializer : attributeSerializers) {
            deserializer.initialize(this, version);
          }
        } catch (IOException e) {
          throw new IllegalStateException("Unexpected exception, but...", e);
        }
        token = 0;
      }
    };

    tokenStream.reset(null);
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
    public AttributeOutputStream(OutputStream output) {
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
    public AttributeInputStream(InputStream input) {
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
