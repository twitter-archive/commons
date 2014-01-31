package com.twitter.common.args.parsers;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

import com.twitter.common.args.ArgParser;

/**
 * A parser that handles closed ranges. For the input "4-6", it will capture [4, 5, 6].
 */
@ArgParser
public class RangeParser extends NonParameterizedTypeParser<Range<Integer>> {
  @Override
  public Range<Integer> doParse(String raw) throws IllegalArgumentException {
    ImmutableList<String> numbers =
        ImmutableList.copyOf(Splitter.on('-').omitEmptyStrings().split(raw));
    try {
      int from = Integer.parseInt(numbers.get(0));
      int to = Integer.parseInt(numbers.get(1));
      if (numbers.size() != 2) {
        throw new IllegalArgumentException("Failed to parse the range:" + raw);
      }
      if (to < from) {
        return Range.closed(to, from);
      } else {
        return Range.closed(from, to);
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Failed to parse the range:" + raw, e);
    }
  }
}
