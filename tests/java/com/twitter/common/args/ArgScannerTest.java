// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.args;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.args.ArgScannerTest.StandardArgs.Optimizations;
import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.args.constraints.NotNegative;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.constraints.Positive;
import com.twitter.common.args.constraints.Range;
import com.twitter.common.args.parsers.NonParameterizedTypeParser;
import com.twitter.common.base.Command;
import com.twitter.common.base.Function;
import com.twitter.common.base.MorePreconditions;
import com.twitter.common.collections.Pair;
import com.twitter.common.quantity.Amount;
import com.twitter.common.quantity.Data;
import com.twitter.common.quantity.Time;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author William Farner
 */
public class ArgScannerTest {

  private static final Function<Class<?>, Predicate<Field>> TO_SCOPE_PREDICATE =
      new Function<Class<?>, Predicate<Field>>() {
        @Override public Predicate<Field> apply(final Class<?> cls) {
          return new Predicate<Field>() {
            @Override public boolean apply(Field field) {
              return field.getDeclaringClass() == cls;
            }
          };
        }
      };

  @Before
  public void setUp() {
    // Reset args in all classes before each test.
    for (Class<?> cls : this.getClass().getDeclaredClasses()) {
      resetArgs(cls);
    }
  }

  public static class StandardArgs {
    enum Optimizations { NONE, MINIMAL, ALL }
    @CmdLine(name = "enum", help = "help")
    static final Arg<Optimizations> ENUM_VAL = Arg.create(Optimizations.MINIMAL);
    @CmdLine(name = "string", help = "help")
    static final Arg<String> STRING_VAL = Arg.create("string");
    @CmdLine(name = "char", help = "help")
    static final Arg<Character> CHAR_VAL = Arg.create('c');
    @CmdLine(name = "byte", help = "help")
    static final Arg<Byte> BYTE_VAL = Arg.create((byte) 0);
    @CmdLine(name = "short", help = "help")
    static final Arg<Short> SHORT_VAL = Arg.create((short) 0);
    @CmdLine(name = "int", help = "help")
    static final Arg<Integer> INT_VAL = Arg.create(0);
    @CmdLine(name = "long", help = "help")
    static final Arg<Long> LONG_VAL = Arg.create(0L);
    @CmdLine(name = "float", help = "help")
    static final Arg<Float> FLOAT_VAL = Arg.create(0F);
    @CmdLine(name = "double", help = "help")
    static final Arg<Double> DOUBLE_VAL = Arg.create(0D);
    @CmdLine(name = "bool", help = "help")
    static final Arg<Boolean> BOOL = Arg.create(false);
    @CmdLine(name = "regex", help = "help")
    static final Arg<Pattern> REGEX = Arg.create(null);
    @CmdLine(name = "time_amount", help = "help")
    static final Arg<Amount<Long, Time>> TIME_AMOUNT = Arg.create(Amount.of(1L, Time.SECONDS));
    @CmdLine(name = "data_amount", help = "help")
    static final Arg<Amount<Long, Data>> DATA_AMOUNT = Arg.create(Amount.of(1L, Data.MB));
    @CmdLine(name = "range", help = "help")
    static final Arg<com.google.common.collect.Range<Integer>> RANGE =
        Arg.create(com.google.common.collect.Range.closed(1, 5));
    @Positional(help = "help")
    static final Arg<List<Amount<Long, Time>>> POSITIONAL =
        Arg.<List<Amount<Long, Time>>>create(ImmutableList.<Amount<Long, Time>>of());
  }

  @Test
  public void testStandardArgs() {
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.ENUM_VAL.get(), is(Optimizations.ALL));
          }
        }, "enum", "ALL");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.STRING_VAL.get(), is("newstring"));
          }
        },
        "string", "newstring");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.CHAR_VAL.get(), is('x')); }
        },
        "char", "x");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.BYTE_VAL.get(), is((byte) 10));
          }
        },
        "byte", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.SHORT_VAL.get(), is((short) 10));
          }
        },
        "short", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.INT_VAL.get(), is(10)); }
        },
        "int", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.LONG_VAL.get(), is(10L)); }
        },
        "long", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.FLOAT_VAL.get(), is(10f)); }
        },
        "float", "10.0");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.DOUBLE_VAL.get(), is(10d)); }
        },
        "double", "10.0");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.BOOL.get(), is(true)); }
        },
        "bool", "true");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.BOOL.get(), is(true)); }
        },
        "bool", "");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.REGEX.get().matcher("jack").matches(), is(true));
          }
        },
        "regex", ".*ack$");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.BOOL.get(), is(false)); }
        },
        "no_bool", "");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.BOOL.get(), is(true)); }
        },
        "no_bool", "false");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.TIME_AMOUNT.get(), is(Amount.of(100L, Time.SECONDS)));
          }
        },
        "time_amount", "100secs");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.DATA_AMOUNT.get(), is(Amount.of(1L, Data.Gb)));
          }
        },
        "data_amount", "1Gb");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.RANGE.get(), is(com.google.common.collect.Range.closed(1, 5)));
          }
        },
        "range", "1-5");

    resetArgs(StandardArgs.class);
    assertTrue(parse(StandardArgs.class, "1mins", "2secs"));
    assertEquals(ImmutableList.builder()
        .add(Amount.of(60L, Time.SECONDS))
        .add(Amount.of(2L, Time.SECONDS)).build(), StandardArgs.POSITIONAL.get());
  }

  public static class Name {
    private final String name;

    public Name(String name) {
      this.name = MorePreconditions.checkNotBlank(name);
    }

    public String getName() {
      return name;
    }

    @Override
    public int hashCode() {
      return this.name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof Name) && name.equals(((Name) obj).name);
    }
  }

  @ArgParser
  public static class NameParser extends NonParameterizedTypeParser<Name> {
    @Override public Name doParse(String raw) {
      return new Name(raw);
    }
  }

  public static class MeaningOfLife {
    private final Long answer;

    public MeaningOfLife(Long answer) {
      this.answer = Preconditions.checkNotNull(answer);
    }

    @Override
    public int hashCode() {
      return this.answer.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof MeaningOfLife) && answer.equals(((MeaningOfLife) obj).answer);
    }
  }

  public static class Monty extends NonParameterizedTypeParser<MeaningOfLife> {
    @Override public MeaningOfLife doParse(String raw) {
      return new MeaningOfLife(42L);
    }
  }

  public static class CustomArgs {
    @CmdLine(name = "custom1", help = "help")
    static final Arg<Name> NAME_VAL = Arg.create(new Name("jim"));

    @CmdLine(name = "custom2", help = "help", parser = Monty.class)
    static final Arg<MeaningOfLife> MEANING_VAL = Arg.create(new MeaningOfLife(13L));
  }

  @Test
  public void testCustomArgs() {
    test(CustomArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CustomArgs.NAME_VAL.get(), is(new Name("jane")));
          }
        }, "custom1", "jane");
    test(CustomArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CustomArgs.MEANING_VAL.get(), is(new MeaningOfLife(42L)));
          }
        }, "custom2", "jim");
  }

  @Test
  public void testHelp() {
    assertFalse(parse(StandardArgs.class, "-h"));
    assertFalse(parse(StandardArgs.class, "-help"));
  }

  @Test
  public void testAllowsEmptyString() {
    parse(StandardArgs.class, "-string=");
    assertThat(StandardArgs.STRING_VAL.get(), is(""));

    resetArgs(StandardArgs.class);

    parse(StandardArgs.class, "-string=''");
    assertThat(StandardArgs.STRING_VAL.get(), is(""));

    resetArgs(StandardArgs.class);

    parse(StandardArgs.class, "-string=\"\"");
    assertThat(StandardArgs.STRING_VAL.get(), is(""));
  }

  public static class CollectionArgs {
    @CmdLine(name = "stringList", help = "help")
    static final Arg<List<String>> STRING_LIST = Arg.create(null);
    @CmdLine(name = "intList", help = "help")
    static final Arg<List<Integer>> INT_LIST = Arg.create(null);
    @CmdLine(name = "stringSet", help = "help")
    static final Arg<Set<String>> STRING_SET = Arg.create(null);
    @CmdLine(name = "intSet", help = "help")
    static final Arg<Set<Integer>> INT_SET = Arg.create(null);
    @CmdLine(name = "stringStringMap", help = "help")
    static final Arg<Map<String, String>> STRING_STRING_MAP = Arg.create(null);
    @CmdLine(name = "intIntMap", help = "help")
    static final Arg<Map<Integer, Integer>> INT_INT_MAP = Arg.create(null);
    @CmdLine(name = "stringIntMap", help = "help")
    static final Arg<Map<String, Integer>> STRING_INT_MAP = Arg.create(null);
    @CmdLine(name = "intStringMap", help = "help")
    static final Arg<Map<Integer, String>> INT_STRING_MAP = Arg.create(null);
    @CmdLine(name = "stringStringPair", help = "help")
    static final Arg<Pair<String, String>> STRING_STRING_PAIR = Arg.create(null);
    @CmdLine(name = "intIntPair", help = "help")
    static final Arg<Pair<Integer, Integer>> INT_INT_PAIR = Arg.create(null);
    @CmdLine(name = "stringTimeAmountPair", help = "help")
    static final Arg<Pair<String, Amount<Long, Time>>> STRING_TIME_AMOUNT_PAIR = Arg.create(null);
  }

  @Test
  public void testCollectionArgs() {
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.STRING_LIST.get(), is(Arrays.asList("a", "b", "c", "d")));
          }
        },
        "stringList", "a,b,c,d");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.INT_LIST.get(), is(Arrays.asList(1, 2, 3, 4)));
          }
        },
        "intList", "1, 2, 3, 4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Set<String> expected = ImmutableSet.of("a", "b", "c", "d");
            assertThat(CollectionArgs.STRING_SET.get(), is(expected));
          }
        },
        "stringSet", "a,b,c,d");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Set<Integer> expected = ImmutableSet.of(1, 2, 3, 4);
            assertThat(CollectionArgs.INT_SET.get(), is(expected));
          }
        },
        "intSet", "1, 2, 3, 4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<String, String> expected = ImmutableMap.of("a", "b", "c", "d", "e", "f", "g", "h");
            assertThat(CollectionArgs.STRING_STRING_MAP.get(), is(expected));
          }
        },
        "stringStringMap", "a=b, c=d, e=f, g=h");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<Integer, Integer> expected = ImmutableMap.of(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(CollectionArgs.INT_INT_MAP.get(), is(expected));
          }
        },
        "intIntMap", "1 = 2,3=4, 5=6 ,7=8");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<String, Integer> expected = ImmutableMap.of("a", 1, "b", 2, "c", 3, "d", 4);
            assertThat(CollectionArgs.STRING_INT_MAP.get(), is(expected));
          }
        },
        "stringIntMap", "a=1  , b=2, c=3   ,d=4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<Integer, String> expected = ImmutableMap.of(1, "1", 2, "2", 3, "3", 4, "4");
            assertThat(CollectionArgs.INT_STRING_MAP.get(), is(expected));
          }
        },
        "intStringMap", "  1=1  , 2=2, 3=3,4=4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.STRING_STRING_PAIR.get(), is(Pair.of("foo", "bar")));
          }
        },
        "stringStringPair", "foo , bar");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.INT_INT_PAIR.get(), is(Pair.of(10, 20)));
          }
        },
        "intIntPair", "10    ,20");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.STRING_TIME_AMOUNT_PAIR.get(),
                       is(Pair.of("fred", Amount.of(42L, Time.MINUTES))));
          }
        },
        "stringTimeAmountPair", "fred    ,42mins");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            CollectionArgs.STRING_TIME_AMOUNT_PAIR.get();
          }
        },
        true, "stringTimeAmountPair", "george,1MB");

  }

  static class Serializable1 implements Serializable { }
  static class Serializable2 implements Serializable { }

  public static class WildcardArgs {
    @CmdLine(name = "class", help = "help")
    static final Arg<? extends Class<? extends Serializable>> CLAZZ =
        Arg.create(Serializable1.class);
    @CmdLine(name = "classList1", help = "help")
    static final Arg<List<Class<? extends Serializable>>> CLASS_LIST_1 = Arg.create(null);
    @CmdLine(name = "classList2", help = "help")
    static final Arg<List<? extends Class<? extends Serializable>>> CLASS_LIST_2 = Arg.create(null);
  }

  @Test
  public void testWildcardArgs() {
    test(WildcardArgs.class,
        new Command() {
          @Override public void execute() {
            assertSame(Serializable2.class, WildcardArgs.CLAZZ.get());
          }
        },
        "class", Serializable2.class.getName());

    test(WildcardArgs.class,
        new Command() {
          @Override public void execute() {
            WildcardArgs.CLAZZ.get();
          }
        },
        true, "class", Runnable.class.getName());

    test(WildcardArgs.class,
        new Command() {
          @Override public void execute() {
            assertEquals(ImmutableList.of(Serializable1.class, Serializable2.class),
                         WildcardArgs.CLASS_LIST_1.get());
          }
        },
        "classList1", Serializable1.class.getName() + "," + Serializable2.class.getName());

    test(WildcardArgs.class,
        new Command() {
          @Override public void execute() {
            assertEquals(ImmutableList.of(Serializable2.class), WildcardArgs.CLASS_LIST_2.get());
          }
        },
        "classList2", Serializable2.class.getName());

    test(WildcardArgs.class,
        new Command() {
          @Override public void execute() {
            WildcardArgs.CLASS_LIST_2.get();
          }
        },
        true, "classList2", Serializable1.class.getName() + "," + Runnable.class.getName());
  }

  @Target(FIELD)
  @Retention(RUNTIME)
  public static @interface Equals {
    String value();
  }

  @VerifierFor(Equals.class)
  public static class SameName implements Verifier<Name> {
    @Override
    public void verify(Name value, Annotation annotation) {
      Preconditions.checkArgument(getValue(annotation).equals(value.getName()));
    }

    @Override
    public String toString(Class<? extends Name> argType, Annotation annotation) {
      return "name = " + getValue(annotation);
    }

    private String getValue(Annotation annotation) {
      return ((Equals) annotation).value();
    }
  }

  public static class VerifyArgs {
    @Equals("jake") @CmdLine(name = "custom", help = "help")
    static final Arg<Name> CUSTOM_VAL = Arg.create(new Name("jake"));
    @NotEmpty @CmdLine(name = "string", help = "help")
    static final Arg<String> STRING_VAL = Arg.create("string");
    @NotEmpty @CmdLine(name = "optional_string", help = "help")
    static final Arg<String> OPTIONAL_STRING_VAL = Arg.create(null);
    @Positive @CmdLine(name = "int", help = "help")
    static final Arg<Integer> INT_VAL = Arg.create(1);
    @NotNegative @CmdLine(name = "long", help = "help")
    static final Arg<Long> LONG_VAL = Arg.create(0L);
    @Range(lower = 10, upper = 20) @CmdLine(name = "float", help = "help")
    static final Arg<Float> FLOAT_VAL = Arg.create(10F);
    @CmdLine(name = "double", help = "help")
    static final Arg<Double> DOUBLE_VAL = Arg.create(0D);
    @CmdLine(name = "bool", help = "help")
    static final Arg<Boolean> BOOL = Arg.create(false);
    @CmdLine(name = "arg_without_default", help = "help")
    static final Arg<Boolean> ARG_WITHOUT_DEFAULT = Arg.create();
  }

  @Test
  public void testEnforcesConstraints() {
    test(VerifyArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(VerifyArgs.STRING_VAL.get(), is("newstring"));
            assertThat(VerifyArgs.OPTIONAL_STRING_VAL.get(), nullValue(String.class));
          }
        },
        "string", "newstring");

    testFails(VerifyArgs.class, "custom", "jane");
    testFails(VerifyArgs.class, "string", "");
    testFails(VerifyArgs.class, "optional_string", "");
    testFails(VerifyArgs.class, "int", "0");
    testFails(VerifyArgs.class, "long", "-1");

    test(VerifyArgs.class,
        new Command() {
          @Override public void execute() {
           assertThat(VerifyArgs.FLOAT_VAL.get(), is(10.5f));
          }
        },
        "float", "10.5");
    testFails(VerifyArgs.class, "float", "9");
  }

  @Test
  public void testJoinKeysToValues() {
    assertThat(ArgScanner.joinKeysToValues(Arrays.asList("")), is(Arrays.asList("")));
    assertThat(ArgScanner.joinKeysToValues(Arrays.asList("-a", "b", "-c", "-d")),
        is(Arrays.asList("-a=b", "-c", "-d")));
    assertThat(ArgScanner.joinKeysToValues(Arrays.asList("-a='b'", "-c", "-d", "'e'")),
        is(Arrays.asList("-a='b'", "-c", "-d='e'")));
    assertThat(ArgScanner.joinKeysToValues(Arrays.asList("-a=-b", "c", "-d", "\"e\"")),
        is(Arrays.asList("-a=-b", "c", "-d=\"e\"")));
  }

  public static class ShortHelpArg {
    @CmdLine(name = "h", help = "help")
    static final Arg<String> SHORT_HELP = Arg.create("string");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testShortHelpReserved() {
    parse(ShortHelpArg.class);
  }

  public static class LongHelpArg {
    @CmdLine(name = "help", help = "help")
    static final Arg<String> LONG_HELP = Arg.create("string");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testLongHelpReserved() {
    parse(LongHelpArg.class);
  }

  public static class DuplicateNames {
    @CmdLine(name = "string", help = "help") static final Arg<String> STRING_1 = Arg.create();
    @CmdLine(name = "string", help = "help") static final Arg<String> STRING_2 = Arg.create();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsDuplicates() {
    parse(DuplicateNames.class, "-string-str");
  }

  public static class OneRequired {
    @CmdLine(name = "string1", help = "help")
    static final Arg<String> STRING_1 = Arg.create(null);
    @NotNull @CmdLine(name = "string2", help = "help")
    static final Arg<String> STRING_2 = Arg.create(null);
  }

  @Test
  public void testRequiredProvided() {
    parse(OneRequired.class, "-string2=blah");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMissingRequired() {
    parse(OneRequired.class, "-string1=blah");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnrecognizedArg() {
    parse(OneRequired.class, "-string2=blah", "-string3=blah");
  }

  public static class NameClashA {
    @CmdLine(name = "string", help = "help")
    static final Arg<String> STRING = Arg.create(null);
    @CmdLine(name = "boolean", help = "help")
    static final Arg<Boolean> BOOLEAN = Arg.create(true);
  }

  public static class NameClashB {
    @CmdLine(name = "string", help = "help")
    static final Arg<String> STRING_1 = Arg.create(null);
    @CmdLine(name = "boolean", help = "help")
    static final Arg<Boolean> BOOLEAN_1 = Arg.create(true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDisallowsShortNameOnArgCollision() {
    parse(ImmutableList.of(NameClashA.class, NameClashB.class), "-string=blah");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDisallowsShortNegNameOnArgCollision() {
    parse(ImmutableList.of(NameClashA.class, NameClashB.class), "-no_boolean");
  }

  @Test
  public void testAllowsCanonicalNameOnArgCollision() {
    // TODO(William Farner): Fix.
    parse(ImmutableList.of(NameClashA.class, NameClashB.class),
        "-" + NameClashB.class.getCanonicalName() + ".string=blah");
  }

  @Test
  public void testAllowsCanonicalNegNameOnArgCollision() {
    parse(ImmutableList.of(NameClashA.class, NameClashB.class),
        "-" + NameClashB.class.getCanonicalName() + ".no_boolean");
  }

  public static class AmountContainer {
    @CmdLine(name = "time_amount", help = "help")
    static final Arg<Amount<Integer, Time>> TIME_AMOUNT = Arg.create(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadUnitType() {
    parse(ImmutableList.of(AmountContainer.class), "-time_amount=1Mb");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnrecognizedUnitType() {
    parse(ImmutableList.of(AmountContainer.class), "-time_amount=1abcd");
  }

  static class Main1 {
    @Positional(help = "halp")
    static final Arg<List<String>> NAMES = Arg.create(null);
  }

  static class Main2 {
    @Positional(help = "halp")
    static final Arg<List<List<String>>> ROSTERS = Arg.create(null);
  }

  static class Main3 {
    @Positional(help = "halp")
    static final Arg<List<Double>> PERCENTILES = Arg.create(null);

    @Positional(help = "halp")
    static final Arg<List<File>> FILES = Arg.create(null);
  }

  private void resetMainArgs() {
    resetArgs(Main1.class);
    resetArgs(Main2.class);
    resetArgs(Main3.class);
  }

  @Test
  public void testMultiplePositionalsFails() {
    // Indivdually these should work.

    resetMainArgs();
    assertTrue(parse(Main1.class, "jack,jill", "laurel,hardy"));
    assertEquals(ImmutableList.of("jack,jill", "laurel,hardy"),
        ImmutableList.copyOf(Main1.NAMES.get()));

    resetMainArgs();
    assertTrue(parse(Main2.class, "jack,jill", "laurel,hardy"));
    assertEquals(
        ImmutableList.of(
            ImmutableList.of("jack", "jill"),
            ImmutableList.of("laurel", "hardy")),
        ImmutableList.copyOf(Main2.ROSTERS.get()));

    // But if combined in the same class or across classes the @Positional is ambiguous and we
    // should fail fast.

    resetMainArgs();
    try {
      parse(ImmutableList.of(Main1.class, Main2.class), "jack,jill", "laurel,hardy");
      fail("Expected more than 1 in-scope @Positional Arg List to trigger a failure.");
    } catch (IllegalArgumentException e) {
      // expected
    }

    resetMainArgs();
    try {
      parse(Main3.class, "50", "90", "99", "99.9");
      fail("Expected more than 1 in-scope @Positional Arg List to trigger a failure.");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  // TODO(William Farner): Do we want to support nested parameterized args?  If so, need to define a
  // syntax for that and build it in.
  //    e.g. List<List<Integer>>, List<Pair<String, String>>

  private static void testFails(Class<?> scope, String arg, String value) {
    test(scope, null, true, arg, value);
  }

  private static void test(Class<?> scope, Command validate, String arg, String value) {
    test(scope, validate, false, arg, value);
  }

  private static void test(Class<?> scope, Command validate, boolean expectFails, String arg,
      String value) {
    String canonicalName = scope.getCanonicalName() + "." + arg;

    if (value.isEmpty()) {
      testValidate(scope, validate, expectFails, String.format("-%s", arg));
      testValidate(scope, validate, expectFails, String.format("-%s", canonicalName));
    } else {
      testValidate(scope, validate, expectFails, String.format("-%s=%s", arg, value));
      testValidate(scope, validate, expectFails, String.format("-%s=%s", canonicalName, value));
      testValidate(scope, validate, expectFails, String.format("-%s='%s'", arg, value));
      testValidate(scope, validate, expectFails, String.format("-%s='%s'", canonicalName, value));
      testValidate(scope, validate, expectFails, String.format("-%s=\"%s\"", arg, value));
      testValidate(scope, validate, expectFails, String.format("-%s=\"%s\"", canonicalName, value));
      testValidate(scope, validate, expectFails, String.format("-%s", arg), value);
      testValidate(scope, validate, expectFails, String.format("-%s", canonicalName), value);
      testValidate(scope, validate, expectFails,
          String.format("-%s", arg), String.format("'%s'", value));
      testValidate(scope, validate, expectFails,
          String.format("-%s", canonicalName), String.format("'%s'", value));
      testValidate(scope, validate, expectFails, String.format("-%s \"%s\"", arg, value));
      testValidate(scope, validate, expectFails, String.format("-%s \"%s\"", canonicalName, value));
      testValidate(scope, validate, expectFails,
          String.format("-%s", arg), String.format("%s", value));
      testValidate(scope, validate, expectFails,
          String.format("-%s", canonicalName), String.format("%s", value));
    }
  }

  private static void testValidate(Class<?> scope, Command validate, boolean expectFails,
      String... args) {
    resetArgs(scope);
    IllegalArgumentException exception = null;
    try {
      assertTrue(parse(scope, args));
    } catch (IllegalArgumentException e) {
      exception = e;
    }

    if (!expectFails && exception != null) {
      throw exception;
    }
    if (expectFails && exception == null) {
      fail("Expected exception.");
    }

    if (validate != null) {
      validate.execute();
    }
    resetArgs(scope);
  }

  private static void resetArgs(Class<?> scope) {
    for (Field field : scope.getDeclaredFields()) {
      if (Arg.class.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())) {
        try {
          ((Arg) field.get(null)).reset();
        } catch (IllegalAccessException e) {
          fail(e.getMessage());
        }
      }
    }
  }

  private static boolean parse(final Class<?> scope, String... args) {
    return parse(ImmutableList.of(scope), args);
  }

  private static boolean parse(Iterable<? extends Class<?>> scopes, String... args) {
    Predicate<Field> filter = Predicates.or(Iterables.transform(scopes, TO_SCOPE_PREDICATE));
    PrintStream devNull = new PrintStream(ByteStreams.nullOutputStream());
    return new ArgScanner(devNull).parse(filter, Arrays.asList(args));
  }
}
