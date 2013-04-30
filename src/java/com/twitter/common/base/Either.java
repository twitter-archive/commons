package com.twitter.common.base;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * A value of one of two possible types.
 *
 * <p>Often Either processing is used as an alternative exception flow control.  In these uses the
 * left type represents failure by convention and the right type the success path result.
 *
 * @param <L> The left type.
 * @param <R> The right type.
 */
public final class Either<L, R> {
  private final Optional<L> left;
  private final Optional<R> right;

  private Either(Optional<L> left, Optional<R> right) {
    this.left = left;
    this.right = right;
  }

  /**
   * Turns a left into a right and vice-versa.
   *
   * @return A new swapped either instance.
   */
  public Either<R, L> swap() {
    return new Either<R, L>(right, left);
  }

  /**
   * Returns an optional the will be {@link Optional#isPresent() present} is this is a left
   * instance.
   *
   * @return An optional value for the left.
   */
  public Optional<L> left() {
    return left;
  }

  /**
   * Returns an optional the will be {@link Optional#isPresent() present} is this is a right
   * instance.
   *
   * @return An optional value for the right.
   */
  public Optional<R> right() {
    return right;
  }

  /**
   * Returns {@code true} if this is a left instance.
   *
   * @return {@code true} if this is a left.
   */
  public boolean isLeft() {
    return left().isPresent();
  }

  /**
   * Returns {@code true} if this is a right instance.
   *
   * @return {@code true} if this is a right.
   */
  public boolean isRight() {
    return right().isPresent();
  }

  /**
   * Returns the underlying value if this is a left; otherwise, throws.
   *
   * @return The underlying value.
   * @throws IllegalStateException if this is a right instance.
   */
  public L getLeft() {
    return left().get();
  }

  /**
   * Returns the underlying value if this is a right; otherwise, throws.
   *
   * @return The underlying value.
   * @throws IllegalStateException if this is a right instance.
   */
  public R getRight() {
    return right().get();
  }

  /**
   * If this is a left, maps its value into a new left; otherwise just returns this right.
   *
   * @param transformer The transformation to apply to the left value.
   * @param <M> The type a left value will be mapped to.
   * @return The mapped left or else the right.
   */
  public <M> Either<M, R> mapLeft(Function<? super L, M> transformer) {
    if (isLeft()) {
      return left(transformer.apply(getLeft()));
    } else {
      @SuppressWarnings("unchecked") // I am a right so my left is never accessible
      Either<M, R> self = (Either<M, R>) this;
      return self;
    }
  }

  /**
   * If this is a right, maps its value into a new right; otherwise just returns this left.
   *
   * @param transformer The transformation to apply to the left value.
   * @param <M> The type a right value will be mapped to.
   * @return The mapped right or else the left.
   */
  public <M> Either<L, M> mapRight(Function<? super R, M> transformer) {
    if (isRight()) {
      return right(transformer.apply(getRight()));
    } else {
      @SuppressWarnings("unchecked") // I am a left so my right is never accessible
      Either<L, M> self = (Either<L, M>) this;
      return self;
    }
  }

  /**
   * Can transform either a left or a right into a result.
   *
   * @param <L> The left type.
   * @param <R> The right type.
   * @param <T> The transformation result type.
   */
  public abstract static class Transformer<L, R, T> implements Function<Either<L, R>, T> {

    /**
     * Maps left values to a result.
     *
     * @param left the left value to map.
     * @return The mapped value.
     */
    public abstract T mapLeft(L left);

    /**
     * Maps right values to a result.
     *
     * @param right the right value to map.
     * @return The mapped value.
     */
    public abstract T mapRight(R right);

    @Override
    public final T apply(Either<L, R> either) {
      return either.map(this);
    }
  }

  /**
   * Creates a transformer from left and right transformation functions.
   *
   * @param leftTransformer A transformer to process left values.
   * @param rightTransformer A transformer to process right values.
   * @param <L> The left type.
   * @param <R> The right type.
   * @param <T> The transformation result type.
   * @return A new transformer composed of left and right transformer functions.
   */
  public static <L, R, T> Transformer<L, R, T> transformer(
      final Function<? super L, T> leftTransformer,
      final Function<? super R, T> rightTransformer) {

    return new Transformer<L, R, T>() {
      @Override public T mapLeft(L item) {
        return leftTransformer.apply(item);
      }
      @Override public T mapRight(R item) {
        return rightTransformer.apply(item);
      }
    };
  }

  /**
   * Transforms this either instance to a value regardless of whether it is a left or a right.
   *
   * @param transformer The transformer to map this either instance.
   * @param <T> The type the transformer produces.
   * @return A value mapped by the transformer from this left or right instance.
   */
  public <T> T map(final Transformer<? super L, ? super R, T> transformer) {
    if (isLeft()) {
      return transformer.mapLeft(getLeft());
    } else {
      return transformer.mapRight(getRight());
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof Either)) {
      return false;
    }
    Either<?, ?> other = (Either<?, ?>) o;
    return Objects.equal(left, other.left)
        && Objects.equal(right, other.right);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(left, right);
  }

  @Override
  public String toString() {
    if (isLeft()) {
      return String.format("Left(%s)", getLeft());
    } else {
      return String.format("Right(%s)", getRight());
    }
  }

  /**
   * Creates a left either instance.
   *
   * @param value The left value to wrap - may not be null.
   * @param <L> The left type.
   * @param <R> The right type.
   * @return A left either instance wrapping {@code value}.
   */
  public static <L, R> Either<L, R> left(L value) {
    return new Either<L, R>(Optional.of(value), Optional.<R>absent());
  }

  /**
   * Creates a right either instance.
   *
   * @param value The right value to wrap - may not be null.
   * @param <L> The left type.
   * @param <R> The right type.
   * @return A right either instance wrapping {@code value}.
   */
  public static <L, R> Either<L, R> right(R value) {
    return new Either<L, R>(Optional.<L>absent(), Optional.of(value));
  }

  /**
   * Extracts all the lefts from a sequence of eithers lazily.
   *
   * @param results A sequence of either's to process.
   * @param <L> The left type.
   * @param <R> The right type.
   * @return A lazy iterable that will produce the lefts present in results in order.
   */
  public static <L, R> Iterable<L> lefts(Iterable<Either<L, R>> results) {
    return Optional.presentInstances(Iterables.transform(results,
        new Function<Either<L, R>, Optional<L>>() {
          @Override public Optional<L> apply(Either<L, R> item) {
            return item.left();
          }
        }));
  }

  /**
   * Extracts all the rights from a sequence of eithers lazily.
   *
   * @param results A sequence of either's to process.
   * @param <L> The left type.
   * @param <R> The right type.
   * @return A lazy iterable that will produce the rights present in results in order.
   */
  public static <L, R> Iterable<R> rights(Iterable<Either<L, R>> results) {
    return Optional.presentInstances(Iterables.transform(results,
        new Function<Either<L, R>, Optional<R>>() {
          @Override public Optional<R> apply(Either<L, R> item) {
            return item.right();
          }
        }));
  }

  /**
   * A convenience method equivalent to calling {@code guard(work, exceptionType)}.
   */
  public static <X extends Exception, R> Either<X, R> guard(
      Class<X> exceptionType,
      ExceptionalSupplier<R, X> work) {

    @SuppressWarnings("unchecked")
    Either<X, R> either = guard(work, exceptionType);
    return either;
  }

  /**
   * A convenience method equivalent to calling
   * {@code guard(Lists.asList(execpetionType, rest), work)}.
   */
  public static <X extends Exception, R> Either<X, R> guard(
      ExceptionalSupplier<R, X> work,
      Class<? extends X> exceptionType,
      Class<? extends X>... rest) {

    return guard(Lists.asList(exceptionType, rest), work);
  }

  /**
   * Thrown when guarded work throws an unguarded exception.  The {@link #getCause() cause} will
   * contain the original unguarded exception.
   */
  public static class UnguardedException extends RuntimeException {
    public UnguardedException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Converts work that can throw exceptions into an either with a left exception base type.  This
   * can be useful to fold an exception throwing library call into an either processing style
   * pipeline.
   *
   * @param exceptionTypes The expected exception types.
   * @param work The work to perform to get a result produce an error.
   * @param <X> The base error type.
   * @param <R> The success type.
   * @return An either wrapping the result of performing {@code work}.
   * @throws UnguardedException if work throws an unguarded exception type.
   */
  public static <X extends Exception, R> Either<X, R> guard(
      Iterable<Class<? extends X>> exceptionTypes,
      ExceptionalSupplier<R, X> work) {

    try {
      return right(work.get());
    // We're explicitly dealing with generic exception types here by design.
    // SUPPRESS CHECKSTYLE RegexpSinglelineJava
    } catch (Exception e) {
      for (Class<? extends X> exceptionType : exceptionTypes) {
        if (exceptionType.isInstance(e)) {
          X exception = exceptionType.cast(e);
          return left(exception);
        }
      }
      throw new UnguardedException(e);
    }
  }
}
