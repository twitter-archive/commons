package com.twitter.common.args.parsers;

import java.lang.reflect.Type;
import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

import com.twitter.common.args.ArgParser;
import com.twitter.common.args.Parser;
import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.Parsers;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Multimap parser.
 */
@ArgParser
public class MultimapParser extends TypeParameterizedParser<Multimap<?, ?>> {

  private static final Splitter KEY_VALUE_SPLITTER =
      Splitter.on("=").trimResults().omitEmptyStrings();

  public MultimapParser() {
    super(2);
  }

  @SuppressWarnings("unchecked")
  @Override
  Multimap<?, ?> doParse(ParserOracle parserOracle, String raw, List<Type> typeParams) {
    Type keyType = typeParams.get(0);
    Parser<?> keyParser = parserOracle.get(TypeToken.of(keyType));

    Type valueType = typeParams.get(1);
    Parser<?> valueParser = parserOracle.get(TypeToken.of(valueType));

    ImmutableMultimap.Builder<Object, Object> multimapBuilder = ImmutableMultimap.builder();
    for (String keyAndValue : Parsers.MULTI_VALUE_SPLITTER.split(raw)) {
      List<String> fields = ImmutableList.copyOf(KEY_VALUE_SPLITTER.split(keyAndValue));
      checkArgument(fields.size() == 2, "Failed to parse key/value pair: " + keyAndValue);
      multimapBuilder.put(
          keyParser.parse(parserOracle, keyType, fields.get(0)),
          valueParser.parse(parserOracle, valueType, fields.get(1)));
    }

    return multimapBuilder.build();
  }
}
