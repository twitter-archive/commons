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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.args.constraints.NotEmpty;
import com.twitter.common.args.constraints.NotNegative;
import com.twitter.common.args.constraints.NotNull;
import com.twitter.common.args.constraints.Positive;
import com.twitter.common.args.constraints.Range;
import com.twitter.common.base.Command;
import com.twitter.common.collections.Pair;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author William Farner
 */
public class ArgScannerTest {

  @Before
  public void setUp() {
    // Reset args in all classes before each test.
    for (Class cls : this.getClass().getDeclaredClasses()) {
      resetArgs(cls);
    }
  }

  public static class StandardArgs {
    @CmdLine(name = "string", help = "help")
    static final Arg<String> stringVal = Arg.create("string");
    @CmdLine(name = "char", help = "help")
    static final Arg<Character> charVal = Arg.create('c');
    @CmdLine(name = "byte", help = "help")
    static final Arg<Byte> byteVal = Arg.create((byte) 0);
    @CmdLine(name = "short", help = "help")
    static final Arg<Short> shortVal = Arg.create((short) 0);
    @CmdLine(name = "int", help = "help")
    static Arg<Integer> intVal = Arg.create(0);
    @CmdLine(name = "long", help = "help")
    static Arg<Long> longVal = Arg.create(0L);
    @CmdLine(name = "float", help = "help")
    static Arg<Float> floatVal = Arg.create(0F);
    @CmdLine(name = "double", help = "help")
    static Arg<Double> doubleVal = Arg.create(0D);
    @CmdLine(name = "bool", help = "help")
    static Arg<Boolean> bool = Arg.create(false);
  }

  @Test
  public void testStandardArgs() {
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.stringVal.get(), is("newstring"));
          }
        },
        "string", "newstring");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.charVal.get(), is('x')); }
        },
        "char", "x");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.byteVal.get(), is((byte) 10)); }
        },
        "byte", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(StandardArgs.shortVal.get(), is((short) 10));
          }
        },
        "short", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.intVal.get(), is(10)); }
        },
        "int", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.longVal.get(), is(10L)); }
        },
        "long", "10");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.floatVal.get(), is(10f)); }
        },
        "float", "10.0");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.doubleVal.get(), is(10d)); }
        },
        "double", "10.0");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.bool.get(), is(true)); }
        },
        "bool", "true");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.bool.get(), is(true)); }
        },
        "bool", "");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.bool.get(), is(false)); }
        },
        "no_bool", "");
    test(StandardArgs.class,
        new Command() {
          @Override public void execute() { assertThat(StandardArgs.bool.get(), is(true)); }
        },
        "no_bool", "false");
  }

  @Test
  public void testAllowsEmptyString() {
    parse(StandardArgs.class, "-string=");
    assertThat(StandardArgs.stringVal.get(), is(""));

    resetArgs(StandardArgs.class);

    parse(StandardArgs.class, "-string=''");
    assertThat(StandardArgs.stringVal.get(), is(""));

    resetArgs(StandardArgs.class);

    parse(StandardArgs.class, "-string=\"\"");
    assertThat(StandardArgs.stringVal.get(), is(""));
  }

  public static class CollectionArgs {
    @CmdLine(name = "stringList", help = "help")
    static final Arg<List<String>> stringList = Arg.create(null);
    @CmdLine(name = "intList", help = "help")
    static final Arg<List<Integer>> intList = Arg.create(null);
    @CmdLine(name = "stringSet", help = "help")
    static final Arg<Set<String>> stringSet = Arg.create(null);
    @CmdLine(name = "intSet", help = "help")
    static final Arg<Set<Integer>> intSet = Arg.create(null);
    @CmdLine(name = "stringStringMap", help = "help")
    static final Arg<Map<String, String>> stringStringMap = Arg.create(null);
    @CmdLine(name = "intIntMap", help = "help")
    static final Arg<Map<Integer, Integer>> intIntMap = Arg.create(null);
    @CmdLine(name = "stringIntMap", help = "help")
    static final Arg<Map<String, Integer>> stringIntMap = Arg.create(null);
    @CmdLine(name = "intStringMap", help = "help")
    static Arg<Map<Integer, String>> intStringMap = Arg.create(null);
    @CmdLine(name = "stringStringPair", help = "help")
    static final Arg<Pair<String, String>> stringStringPair = Arg.create(null);
    @CmdLine(name = "intIntPair", help = "help")
    static final Arg<Pair<Integer, Integer>> intIntPair = Arg.create(null);
  }

  @Test
  public void testCollectionArgs() {
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.stringList.get(), is(Arrays.asList("a", "b", "c", "d")));
          }
        },
        "stringList", "a,b,c,d");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.intList.get(), is(Arrays.asList(1, 2, 3, 4)));
          }
        },
        "intList", "1, 2, 3, 4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Set<String> expected = ImmutableSet.of("a", "b", "c", "d");
            assertThat(CollectionArgs.stringSet.get(), is(expected));
          }
        },
        "stringSet", "a,b,c,d");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Set<Integer> expected = ImmutableSet.of(1, 2, 3, 4);
            assertThat(CollectionArgs.intSet.get(), is(expected));
          }
        },
        "intSet", "1, 2, 3, 4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<String, String> expected = ImmutableMap.of("a", "b", "c", "d", "e", "f", "g", "h");
            assertThat(CollectionArgs.stringStringMap.get(), is(expected));
          }
        },
        "stringStringMap", "a=b, c=d, e=f, g=h");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<Integer, Integer> expected = ImmutableMap.of(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(CollectionArgs.intIntMap.get(), is(expected));
          }
        },
        "intIntMap", "1 = 2,3=4, 5=6 ,7=8");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<String, Integer> expected = ImmutableMap.of("a", 1, "b", 2, "c", 3, "d", 4);
            assertThat(CollectionArgs.stringIntMap.get(), is(expected));
          }
        },
        "stringIntMap", "a=1  , b=2, c=3   ,d=4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            Map<Integer, String> expected = ImmutableMap.of(1, "1", 2, "2", 3, "3", 4, "4");
            assertThat(CollectionArgs.intStringMap.get(), is(expected));
          }
        },
        "intStringMap", "  1=1  , 2=2, 3=3,4=4");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.stringStringPair.get(), is(Pair.of("foo", "bar")));
          }
        },
        "stringStringPair", "foo , bar");
    test(CollectionArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(CollectionArgs.intIntPair.get(), is(Pair.of(10, 20)));
          }
        },
        "intIntPair", "10    ,20");
  }

  public static class VerifyArgs {
    @NotEmpty @CmdLine(name = "string", help = "help")
    static final Arg<String> stringVal = Arg.create("string");
    @NotEmpty @CmdLine(name = "optional_string", help = "help")
    static final Arg<String> optionalStringVal = Arg.create(null);
    @Positive @CmdLine(name = "int", help = "help")
    static final Arg<Integer> intVal = Arg.create(1);
    @NotNegative @CmdLine(name = "long", help = "help")
    static final Arg<Long> longVal = Arg.create(0L);
    @Range(lower = 10, upper = 20) @CmdLine(name = "float", help = "help")
    static final Arg<Float> floatVal = Arg.create(10F);
    @CmdLine(name = "double", help = "help")
    static final Arg<Double> doubleVal = Arg.create(0D);
    @CmdLine(name = "bool", help = "help")
    static final Arg<Boolean> bool = Arg.create(false);
  }

  @Test
  public void testEnforcesConstraints() {
    test(VerifyArgs.class,
        new Command() {
          @Override public void execute() {
            assertThat(VerifyArgs.stringVal.get(), is("newstring"));
            assertThat(VerifyArgs.optionalStringVal.get(), nullValue(String.class));
          }
        },
        "string", "newstring");

    testFails(VerifyArgs.class, "string", "");
    testFails(VerifyArgs.class, "optional_string", "");
    testFails(VerifyArgs.class, "int", "0");
    testFails(VerifyArgs.class, "long", "-1");

    test(VerifyArgs.class,
        new Command() {
          @Override public void execute() {
           assertThat(VerifyArgs.floatVal.get(), is(10.5f));
          }
        },
        "float", "10.5");
    testFails(VerifyArgs.class, "float", "9");
  }

  @Test
  public void testJoinKeysToValues() {
    assertThat(ArgScanner.joinKeysToValues(""), is(Arrays.asList("")));
    assertThat(ArgScanner.joinKeysToValues("-a", "b", "-c", "-d"),
        is(Arrays.asList("-a=b", "-c", "-d")));
    assertThat(ArgScanner.joinKeysToValues("-a='b'", "-c", "-d", "'e'"),
        is(Arrays.asList("-a='b'", "-c", "-d='e'")));
    assertThat(ArgScanner.joinKeysToValues("-a=-b", "c", "-d", "\"e\""),
        is(Arrays.asList("-a=-b", "c", "-d=\"e\"")));
  }

  public static class NonStaticArg {
    @CmdLine(name = "string", help = "help")
    final Arg<String> string = Arg.create();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFailsNonStatic() {
    parse(NonStaticArg.class, "-string=str");
  }

  public static class NonArgArg {
    @CmdLine(name = "string", help = "help")
    static final String string = "string";
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFailsNonArgArg() {
    parse(NonArgArg.class, "-string=str");
  }

  public static class DuplicateNames {
    @CmdLine(name = "string", help = "help") static String string1 = null;
    @CmdLine(name = "string", help = "help") static String string2 = null;
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRejectsDuplicates() {
    parse(DuplicateNames.class, "-string-str");
  }

  public static class OneRequired {
    @CmdLine(name = "string1", help = "help")
    static final Arg<String> string1 = Arg.create(null);
    @NotNull @CmdLine(name = "string2", help = "help")
    static final Arg<String> string2 = Arg.create(null);
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
    static final Arg<String> string = Arg.create(null);
  }

  public static class NameClashB {
    @CmdLine(name = "string", help = "help")
    static final Arg<String> string1 = Arg.create(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDisallowsShortNameOnArgCollision() {
    parse(ImmutableList.<Class>of(NameClashA.class, NameClashB.class), "-string=blah");
  }

  @Test
  public void testAllowsCanonicalNameOnArgCollision() {
    // TODO(William Farner): Fix.
    parse(ImmutableList.<Class>of(NameClashA.class, NameClashB.class),
        "-" + NameClashB.class.getCanonicalName() + ".string=blah");
  }

  // TODO(William Farner): Do we want to support nested parameterized args?  If so, need to define a syntax
  //    for that and build it in.
  //    e.g. List<List<Integer>>, List<Pair<String, String>>

  private static void testFails(Class scope, String arg, String value) {
    test(scope, null, true, arg, value);
  }

  private static void test(Class scope, Command validate, String arg, String value) {
    test(scope, validate, false, arg, value);
  }

  private static void test(Class scope, Command validate, boolean expectFails, String arg,
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

  private static void testValidate(Class scope, Command validate, boolean expectFails,
      String... args) {
    resetArgs(scope);
    IllegalArgumentException exception = null;
    try {
      parse(scope, args);
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

  private static void resetArgs(Class scope) {
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

  private static void parse(Class scope, String... args) {
    ArgScanner.process(ImmutableSet.copyOf(scope.getDeclaredFields()),
        ArgScanner.mapArguments(args));
  }

  private static void parse(Iterable<Class> scope, String... args) {
    ImmutableSet.Builder<Field> fields = ImmutableSet.builder();
    for (Class cls : scope) {
      fields.addAll(ImmutableSet.copyOf(cls.getDeclaredFields()));
    }

    ArgScanner.process(fields.build(), ArgScanner.mapArguments(args));
  }
}
