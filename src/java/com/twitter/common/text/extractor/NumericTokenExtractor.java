package com.twitter.common.text.extractor;

import java.util.regex.Pattern;


public class NumericTokenExtractor extends RegexExtractor {
  private static final String NUMERIC_CHARS = "[0-9０−９]+";
  private static final String NUMERIC_CONNECTOR_CHARS = "[.．,，/]";
  public static final Pattern NUMERIC_TOKEN_PATTERN =
      Pattern.compile(NUMERIC_CHARS + "(?:(" + NUMERIC_CONNECTOR_CHARS + ")" + NUMERIC_CHARS + ")"
          + "(?:\\1" + NUMERIC_CHARS + ")*");

  public NumericTokenExtractor() {
    setRegexPattern(NUMERIC_TOKEN_PATTERN);
  }
}
