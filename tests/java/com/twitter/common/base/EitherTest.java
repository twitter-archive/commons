package com.twitter.common.base;

import java.io.IOException;
import java.net.MalformedURLException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Test;

import com.twitter.common.base.Either.Transformer;
import com.twitter.common.base.Either.UnguardedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EitherTest {

  @Test
  public void testLeft() {
    Exception left = new Exception();
    assertLeft(left, Either.<Exception, String>left(left));
  }

  @Test
  public void testRight() {
    assertRight("jake", Either.<Exception, String>right("jake"));
  }

  @Test
  public void testSwap() {
    assertLeft("jake", Either.right("jake").swap());
    assertRight("jake", Either.left("jake").swap());

    Either<String, Object> left = Either.left("jake");
    assertEquals(left, left.swap().swap());

    Either<Object, String> right = Either.right("jake");
    assertEquals(right, right.swap().swap());
  }

  public static final Function<CharSequence, Integer> LENGTH =
      new Function<CharSequence, Integer>() {
        @Override public Integer apply(CharSequence item) {
          return item.length();
        }
      };

  public static final Function<Object, Integer> HASHCODE = new Function<Object, Integer>() {
    @Override public Integer apply(Object item) {
      return item.hashCode();
    }
  };

  @Test
  public void testMapLeft() {
    RuntimeException left = new RuntimeException() {
      @Override public int hashCode() {
        return 42;
      }
    };
    assertLeft(42, Either.left(left).mapLeft(HASHCODE));

    assertRight("jake", Either.right("jake").mapLeft(HASHCODE));
  }

  @Test
  public void testMapRight() {
    assertRight(4, Either.right("jake").mapRight(LENGTH));

    RuntimeException left = new RuntimeException();
    assertLeft(left, Either.left(left).mapRight(HASHCODE));
  }

  @Test
  public void testMap() {
    Transformer<Object, CharSequence, Integer> transformer = Either.transformer(HASHCODE, LENGTH);

    Object left = new Object() {
      @Override public int hashCode() {
        return 1137;
      }
    };
    Either<Object, CharSequence> either = Either.left(left);
    assertEquals(1137, either.map(transformer).intValue());

    assertEquals(19, Either.right("The Meaning of Life").map(transformer).intValue());
  }

  private static final ImmutableList<Either<String, String>> LEFT_RESULTS =
      ImmutableList.of(
          Either.<String, String>left("jack"),
          Either.<String, String>left("jill"));

  private static final ImmutableList<Either<String, String>> RIGHT_RESULTS =
      ImmutableList.of(
          Either.<String, String>right("jack"),
          Either.<String, String>right("jill"));

  private static final ImmutableList<Either<String, String>> MIXED_RESULTS =
      ImmutableList.of(
          Either.<String, String>left("jack"),
          Either.<String, String>right("jane"),
          Either.<String, String>left("jill"));

  @Test
  public void testLefts() {
    assertEquals(ImmutableList.of("jack", "jill"),
        ImmutableList.copyOf(Either.lefts(LEFT_RESULTS)));
    assertEquals(ImmutableList.of(), ImmutableList.copyOf(Either.lefts(RIGHT_RESULTS)));
    assertEquals(ImmutableList.of("jack", "jill"),
        ImmutableList.copyOf(Either.lefts(MIXED_RESULTS)));
  }

  @Test
  public void testRights() {
    assertEquals(ImmutableList.of(), ImmutableList.copyOf(Either.rights(LEFT_RESULTS)));
    assertEquals(ImmutableList.of("jack", "jill"),
        ImmutableList.copyOf(Either.rights(RIGHT_RESULTS)));
    assertEquals(ImmutableList.of("jane"), ImmutableList.copyOf(Either.rights(MIXED_RESULTS)));
  }

  @Test
  public void testTransformer() {
    assertEquals(ImmutableList.of("jackjack", "4", "jilljill"),
        ImmutableList.copyOf(Iterables.transform(MIXED_RESULTS,
            new Transformer<String, String, String>() {
              @Override public String mapLeft(String left) {
                return left + left;
              }
              @Override public String mapRight(String right) {
                return String.valueOf(right.length());
              }
            })));
  }

  static <T, X extends Exception> ExceptionalSupplier<T, X> constantSupplier(final T value) {
    return new ExceptionalSupplier<T, X>() {
      @Override public T get() {
        return value;
      }
    };
  }

  static <T, X extends Exception> ExceptionalSupplier<T, X> failedSupplier(final X failure) {
    return new ExceptionalSupplier<T, X>() {
      @Override public T get() throws X {
        throw failure;
      }
    };
  }

  @Test
  public void testGuard() {
    assertRight("jake",
        Either.guard(IOException.class, EitherTest.<String, IOException>constantSupplier("jake")));

    IOException left = new IOException();
    assertLeft(left, Either.guard(IOException.class, failedSupplier(left)));

    try {
      Either.guard(IOException.class, new ExceptionalSupplier<Object, IOException>() {
        @Override public String get() {
          throw new ArithmeticException();
        }
      });
      fail("Expected an unguarded exception type to fail fast.");
    } catch (UnguardedException e) {
      assertTrue(e.getCause() instanceof ArithmeticException);
    }

    Either<Exception, String> result =
        Either.guard(ImmutableList.of(IOException.class, InterruptedException.class),
            new SupplierE<String>() {
              @Override public String get() throws InterruptedException {
                throw new InterruptedException();
              }
            });
    assertTrue(result.getLeft() instanceof InterruptedException);

    result = Either.guard(ImmutableList.of(IOException.class, InterruptedException.class),
        new SupplierE<String>() {
          @Override public String get() throws IOException {
            throw new MalformedURLException();
          }
        });
    assertTrue(result.getLeft() instanceof IOException);

    class MyException extends Exception { }
    try {
      Either.guard(ImmutableList.of(IOException.class, InterruptedException.class),
          new SupplierE<String>() {
            @Override public String get() throws Exception {
              throw new MyException();
            }
          });
      fail("Expected an unguarded exception type to fail fast.");
    } catch (UnguardedException e) {
      assertTrue(e.getCause() instanceof MyException);
    }
  }

  private static <L, R> void assertLeft(L left, Either<L, R> either) {
    assertEquals(Either.left(left), either);

    assertTrue(either.isLeft());
    assertTrue(either.left().isPresent());
    assertSame(left, either.getLeft());
    assertSame(left, either.left().get());

    assertFalse(either.isRight());
    assertFalse(either.right().isPresent());
    try {
      either.getRight();
      fail("Expected a a left to throw when accessing its right.");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      either.right().get();
      fail("Expected a a left to throw when accessing its right.");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  private static <L, R> void assertRight(R right, Either<L, R> either) {
    assertEquals(Either.right(right), either);

    assertTrue(either.isRight());
    assertTrue(either.right().isPresent());
    assertSame(right, either.getRight());
    assertSame(right, either.right().get());

    assertFalse(either.isLeft());
    assertFalse(either.left().isPresent());
    try {
      either.getLeft();
      fail("Expected a a right to throw when accessing its left.");
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      either.left().get();
      fail("Expected a a right to throw when accessing its left.");
    } catch (IllegalStateException e) {
      // expected
    }
  }
}
