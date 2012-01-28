package com.twitter.common.text.combiner;

import com.twitter.common.text.extractor.RegexExtractor;
import com.twitter.common.text.token.TokenStream;
import com.twitter.common.text.token.attribute.TokenType;

import java.util.regex.Pattern;

public class PunctuationExceptionCombiner extends ExtractorBasedTokenCombiner {

  private static final String PUNCTUATION_EXCEPTIONS_CHARS = "♥";
  private static final String PUNCTUATION_EXCEPTION_REGEX =
      "[" + PUNCTUATION_EXCEPTIONS_CHARS + "]+";
  private static final Pattern PUNCTUATION_EXCEPTIONS_PATTERN =
      Pattern.compile(PUNCTUATION_EXCEPTION_REGEX);

  protected PunctuationExceptionCombiner(TokenStream inputStream, Pattern exceptionsPattern) {
    super(inputStream);
    setExtractor(new RegexExtractor.Builder().setRegexPattern(exceptionsPattern, 0, 0)
        .build());
    setType(TokenType.TOKEN);
  }

  public static class Builder {
    private String exceptionChars = null;
    private TokenStream inputStream;

    public Builder(TokenStream inputStream) {
      this.inputStream = inputStream;
    }

    /**
     * Add additional exception chars. For example, to add . and ! to the list of
     * non-punctuation chars, additionalChars should be ".!"
     *
     * @param additionalChars Additional characters that should not be considered punctuation
     * @return PunctuationExceptionCombiner builder instance
     */
    public Builder addExceptionChars(String additionalChars) {
      if (exceptionChars == null) {
        exceptionChars = PUNCTUATION_EXCEPTIONS_CHARS + additionalChars;
      } else {
        exceptionChars += additionalChars;
      }
      return this;
    }

    public PunctuationExceptionCombiner build() {
      Pattern exceptionsPattern = PUNCTUATION_EXCEPTIONS_PATTERN;
      if (exceptionChars != null) {
        exceptionsPattern = Pattern.compile("[" + exceptionChars + "]+");
      }
      return new PunctuationExceptionCombiner(inputStream, exceptionsPattern);
    }
  }
}
