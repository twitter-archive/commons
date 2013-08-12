package com.twitter.common.text.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.lucene.util.AttributeSource;
import org.junit.Assert;
import org.junit.Test;

import com.twitter.common.text.token.attribute.TokenType;
import com.twitter.common.text.token.attribute.TokenTypeAttribute;

/**
 * Test for TokenTypeAttributeSerializer.
 * @author Ugo Di Girolamo
 */
public class TokenTypeAttributeSerializerTest {
  private byte[] serialize(TokenType tokenType) throws IOException {
    AttributeSource attributeSource = new AttributeSource();
    TokenTypeAttribute tokenTypeAttribute = attributeSource.addAttribute(
        TokenTypeAttribute.class);
    tokenTypeAttribute.setType(tokenType);
    TokenTypeAttributeSerializer serializer = new TokenTypeAttributeSerializer();
    serializer.initialize(attributeSource, TokenStreamSerializer.CURRENT_VERSION);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    TokenStreamSerializer.AttributeOutputStream outputStream =
        new TokenStreamSerializer.AttributeOutputStream(output);
    serializer.serialize(outputStream);

    return output.toByteArray();
  }

  private TokenType deserialize(byte[] serialized) throws IOException {
    AttributeSource attributeSource = new AttributeSource();
    TokenTypeAttribute tokenTypeAttribute = attributeSource.addAttribute(
        TokenTypeAttribute.class);
    TokenTypeAttributeSerializer serializer = new TokenTypeAttributeSerializer();
    serializer.initialize(attributeSource, TokenStreamSerializer.CURRENT_VERSION);

    ByteArrayInputStream input = new ByteArrayInputStream(serialized);
    TokenStreamSerializer.AttributeInputStream inputStream =
        new TokenStreamSerializer.AttributeInputStream(input);
    serializer.deserialize(inputStream, null);
    return tokenTypeAttribute.getType();
  }

  /**
   * Test that serializing and then deserializing a TokenType we get the original token back.
   */
  @Test
  public void testSerializeAndDeserialize() throws Exception {
    for (TokenType tokenType : TokenType.values()) {
      byte[] serialized = serialize(tokenType);
      Assert.assertEquals(tokenType, deserialize(serialized));
    }
  }

  /**
   * Test that the serialized token type uses exactly one byte.
   */
  @Test
  public void testTokenTypesFitInOneByte() throws Exception {
    for (TokenType tokenType : TokenType.values()) {
      byte[] serialized = serialize(tokenType);
      Assert.assertEquals(1, serialized.length);
    }
  }

  // CHECKSTYLE:OFF MagicNumber
  /**
   * Since we serialize the ordinal of the TokenTypes, we need to guarantee that ordinal numbers
   * don't change.
   * So if you add a new TokenType, come here and update this test to include your TokenType.
   * Do NOT remove TokenTypes.
   */
  @Test
  public void testKnownEnumNames() throws Exception {
    Assert.assertEquals(0, TokenType.TOKEN.ordinal());
    Assert.assertEquals(1, TokenType.PUNCTUATION.ordinal());
    Assert.assertEquals(2, TokenType.HASHTAG.ordinal());
    Assert.assertEquals(3, TokenType.USERNAME.ordinal());
    Assert.assertEquals(4, TokenType.EMOTICON.ordinal());
    Assert.assertEquals(5, TokenType.URL.ordinal());
    Assert.assertEquals(6, TokenType.STOCK.ordinal());
    Assert.assertEquals(7, TokenType.CONTRACTION.ordinal());
    Assert.assertEquals(8, TokenType.values().length);
  }
  // CHECKSTYLE:ON MagicNumber
}
