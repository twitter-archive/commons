package com.twitter.common.text;

import com.google.common.base.Preconditions;
import com.twitter.common.text.combiner.PunctuationExceptionCombiner;
import com.twitter.common.text.token.TwitterTokenStream;
import com.twitter.common.text.token.TokenizedCharSequence;
import com.twitter.common.text.token.TokenizedCharSequenceStream;
import com.twitter.common.text.tokenizer.LatinTokenizer;

import java.util.List;

public abstract class TextTokenizer {
  protected TwitterTokenStream tokenizationStream =
      new TokenizedCharSequenceStream(applyDefaultChain(
        new PunctuationExceptionCombiner.Builder(
          new LatinTokenizer.Builder().build()).build()));

  public abstract TwitterTokenStream applyDefaultChain(TwitterTokenStream tokenizer);

  /**
   * Returns {@code TwitterTokenStream} to tokenize a text.
   *
   * @return {@code TwitterTokenStream} to tokenize the text
   */
  public TwitterTokenStream getDefaultTokenStream() {
    return tokenizationStream;
  }

  /**
   * Tokenizes a {@code CharSequence}, and returns a {@code TokenizedCharSequence} as a result.
   *
   * @param input text to be tokenized
   * @return {@code TokenizedCharSequence} instance
   */
  public TokenizedCharSequence tokenize(CharSequence input) {
    Preconditions.checkNotNull(input);
    return TokenizedCharSequence.createFrom(input, getDefaultTokenStream());
  }

  /**
   * Tokenizes a {@code CharSequence} into a list of Strings.
   *
   * @param input text to be tokenized
   * @return a list of tokens as String objects
   */
  public List<String> tokenizeToStrings(CharSequence input) {
    Preconditions.checkNotNull(input);
    TwitterTokenStream tokenizer = getDefaultTokenStream();
    tokenizer.reset(input);
    return tokenizer.toStringList();
  }
}
