package com.twitter.common.args.parsers;

import java.io.IOException;
import java.lang.reflect.Type;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.args.ParserOracle;
import com.twitter.common.args.Parsers;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Time;
import com.twitter.common.quantity.Unit;

import static org.junit.Assert.assertEquals;

public class MultimapParserTest {
  private MultimapParser parser = new MultimapParser();
  private ParserOracle parserOracle;

  @Before
  public void init() throws IOException {
    parserOracle =
        new Parsers(
            ImmutableMap.of(
                String.class, new StringParser(),
                Integer.class, new IntegerParser(),
                Unit.class, new UnitParser(),
                Amount.class, new AmountParser()));
  }

  private Multimap<?, ?> parse(Type multimapType, String raw) {
    return parser.parse(parserOracle, multimapType, raw);
  }

  @Test
  public void testParseSingletonMultimap() {
    assertEquals(
        ImmutableMultimap.of("k", "v"),
        parse(new TypeToken<Multimap<String, String>>() { }.getType(), "k=v"));
    assertEquals(
        ImmutableMultimap.of("k", 1),
        parse(new TypeToken<Multimap<String, Integer>>() { }.getType(), "k=1"));
    assertEquals(
        ImmutableMultimap.of("k", Amount.of(2, Time.SECONDS)),
        parse(new TypeToken<Multimap<String, Amount<Integer, Time>>>() { }.getType(), "k=2secs"));
  }

  @Test
  public void testParseMultivaluedSingletonMultimap() {
    assertEquals(
        ImmutableMultimap.of("k", "v1", "k", "v2"),
        parse(new TypeToken<Multimap<String, String>>() { }.getType(), "k=v1,k=v2"));
  }

  @Test
  public void testParseMultivaluedMultimap() {
    assertEquals(
        ImmutableMultimap.of(
            "k1", "v1.1",
            "k1", "v1.2",
            "k2", "v2.1",
            "k2", "v2.2",
            "k2", "v2.3"),
        parse(
            new TypeToken<Multimap<String, String>>() { }.getType(),
            "k1=v1.1,k1=v1.2,k2=v2.1,k2=v2.2,k2=v2.3"));
  }
}
