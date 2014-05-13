package com.twitter.common.text.combiner;

import com.google.common.collect.ImmutableList;
import com.twitter.common.text.filter.PunctuationFilter;
import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.tokenizer.LatinTokenizer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PunctuationExceptionCombinerTest {
  private TwitterTokenStream tokenized;

  @Before
  public void setup() {
    tokenized = new LatinTokenizer.Builder().setKeepPunctuation(true).build();
  }

  @Test
  public void testPunctuationExceptions() {
    TwitterTokenStream stream = new PunctuationExceptionCombiner.Builder(tokenized).build();

    stream.reset("I .. exceptions!! ");
    assertEquals(ImmutableList.of("I", ".", ".", "exceptions", "!", "!"), stream.toStringList());

    stream.reset("I ♥♥ exceptions");
    assertEquals(ImmutableList.of("I", "♥♥", "exceptions"), stream.toStringList());

    stream.reset("I .♥♥. exceptions");
    assertEquals(ImmutableList.of("I", ".", "♥♥", ".", "exceptions"), stream.toStringList());
  }

  @Test
  public void testPunctuationFilterDoesNotRemoveExceptionChars() {
    TwitterTokenStream stream = new PunctuationFilter(new PunctuationExceptionCombiner.Builder(tokenized).build());

    stream.reset("I .. exceptions!! ");
    assertEquals(ImmutableList.of("I", "exceptions"), stream.toStringList());

    stream.reset("I ♥♥ exceptions!!");
    assertEquals(ImmutableList.of("I", "♥♥", "exceptions"), stream.toStringList());
  }

  @Test
  public void testAddingPunctuationExceptions() {
    TwitterTokenStream stream = new PunctuationExceptionCombiner.Builder(tokenized).addExceptionChars(".").build();
    stream.reset("I .. exceptions!! ");
    assertEquals(ImmutableList.of("I", "..", "exceptions", "!", "!"), stream.toStringList());

    stream = new PunctuationExceptionCombiner.Builder(tokenized).addExceptionChars(".!").build();
    stream.reset("I ..♥♥ exceptions!! ");
    assertEquals(ImmutableList.of("I", "..♥♥", "exceptions", "!!"), stream.toStringList());
  }
}
