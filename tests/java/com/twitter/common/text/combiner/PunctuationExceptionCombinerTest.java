package com.twitter.common.text.combiner;

import com.google.common.collect.ImmutableList;
import com.twitter.common.text.filter.PunctuationFilter;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.tokenizer.LatinTokenizer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PunctuationExceptionCombinerTest {
  private TokenStream tokenized;

  @Before
  public void setup() {
    tokenized = new LatinTokenizer.Builder().setKeepPunctuation(true).build();
  }

  @Test
  public void testPunctuationExceptions() {
    TokenStream stream = new PunctuationExceptionCombiner.Builder(tokenized).build();

    stream.reset("I .. exceptions!! ");
    assertEquals(ImmutableList.of("I", ".", ".", "exceptions", "!", "!"), stream.toStringList());

    stream.reset("I ♥♥ exceptions");
    assertEquals(ImmutableList.of("I", "♥♥", "exceptions"), stream.toStringList());

    stream.reset("I .♥♥. exceptions");
    assertEquals(ImmutableList.of("I", ".", "♥♥", ".", "exceptions"), stream.toStringList());
  }

  @Test
  public void testPunctuationFilterDoesNotRemoveExceptionChars() {
    TokenStream stream = new PunctuationFilter(new PunctuationExceptionCombiner.Builder(tokenized).build());

    stream.reset("I .. exceptions!! ");
    assertEquals(ImmutableList.of("I", "exceptions"), stream.toStringList());

    stream.reset("I ♥♥ exceptions!!");
    assertEquals(ImmutableList.of("I", "♥♥", "exceptions"), stream.toStringList());
  }

  @Test
  public void testAddingPunctuationExceptions() {
    TokenStream stream = new PunctuationExceptionCombiner.Builder(tokenized).addExceptionChars(".").build();
    stream.reset("I .. exceptions!! ");
    assertEquals(ImmutableList.of("I", "..", "exceptions", "!", "!"), stream.toStringList());

    stream = new PunctuationExceptionCombiner.Builder(tokenized).addExceptionChars(".!").build();
    stream.reset("I ..♥♥ exceptions!! ");
    assertEquals(ImmutableList.of("I", "..♥♥", "exceptions", "!!"), stream.toStringList());
  }
}
