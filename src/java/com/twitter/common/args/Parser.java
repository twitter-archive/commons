package com.twitter.common.args;

import java.lang.reflect.Type;

/**
 * An interface to a command line argument parser.
 *
 * @param <T> The base class this parser can parse all subtypes of.
 */
public interface Parser<T> {

  /**
   * Parses strings as arguments of a given subtype of {@code T}.
   *
   * @param parserOracle The registered parserOracle for delegation.
   * @param type The target type of the parsed argument.
   * @param raw The raw value of the argument.
   * @return A value of the given type parsed from the raw value.
   * @throws IllegalArgumentException if the raw value could not be parsed into a value of the
   *     given type.
   */
  T parse(ParserOracle parserOracle, Type type, String raw) throws IllegalArgumentException;
}
