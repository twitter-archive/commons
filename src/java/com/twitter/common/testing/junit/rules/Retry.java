// =================================================================================================
// Copyright 2015 Twitter, Inc.
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

package com.twitter.common.testing.junit.rules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * A test method annotation useful for smoking out flaky behavior in tests.
 *
 * @see Retry.Rule RetryRule needed to enable this annotation in a test class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Retry {

  /**
   * The number of times to retry the test.
   *
   * When a {@link Retry.Rule} is installed and a test method is annotated for {@literal @Retry},
   * it will be retried 0 to N times.  If times is negative, it is treated as 0 and no retries are
   * performed.  If times is &gt;= 1 then a successful execution of the annotated test method is
   * retried until the 1st error, failure or otherwise up to {@code times} times.
   */
  int times() default 1;

  /**
   * Enables {@link Retry @Retry}able tests.
   */
  class Rule implements MethodRule {
    private interface ThrowableFactory {
      Throwable create(String message, Throwable cause);
    }

    private static Throwable annotate(
        int tryNumber,
        final int maxRetries,
        Throwable cause,
        String prefix,
        ThrowableFactory throwableFactory) {

      Throwable annotated =
          throwableFactory.create(
              String.format("%s on try %d of %d: %s", prefix, tryNumber, maxRetries + 1,
                  Objects.firstNonNull(cause.getMessage(), "")), cause);
      annotated.setStackTrace(cause.getStackTrace());
      return annotated;
    }

    static class RetriedAssertionError extends AssertionError {
      private final int tryNumber;
      private final int maxRetries;

      RetriedAssertionError(int tryNumber, int maxRetries, String message, Throwable cause) {
        // We do a manual initCause here to be compatible with the Java 1.6 AssertionError
        // constructors.
        super(message);
        initCause(cause);

        this.tryNumber = tryNumber;
        this.maxRetries = maxRetries;
      }

      @VisibleForTesting
      int getTryNumber() {
        return tryNumber;
      }

      @VisibleForTesting
      int getMaxRetries() {
        return maxRetries;
      }
    }

    private static Throwable annotate(final int tryNumber, final int maxRetries, AssertionError e) {
      return annotate(tryNumber, maxRetries, e, "Failure", new ThrowableFactory() {
        @Override public Throwable create(String message, Throwable cause) {
          return new RetriedAssertionError(tryNumber, maxRetries, message, cause);
        }
      });
    }

    static class RetriedException extends Exception {
      private final int tryNumber;
      private final int maxRetries;

      RetriedException(int tryNumber, int maxRetries, String message, Throwable cause) {
        super(message, cause);
        this.tryNumber = tryNumber;
        this.maxRetries = maxRetries;
      }

      @VisibleForTesting
      int getTryNumber() {
        return tryNumber;
      }

      @VisibleForTesting
      int getMaxRetries() {
        return maxRetries;
      }
    }

    private static Throwable annotate(final int tryNumber, final int maxRetries, Exception e) {
      return annotate(tryNumber, maxRetries, e, "Error", new ThrowableFactory() {
        @Override public Throwable create(String message, Throwable cause) {
          return new RetriedException(tryNumber, maxRetries, message, cause);
        }
      });
    }

    @Override
    public Statement apply(final Statement statement, FrameworkMethod method, Object receiver) {
      Retry retry = method.getAnnotation(Retry.class);
      if (retry == null || retry.times() <= 0) {
        return statement;
      } else {
        final int times = retry.times();
        return new Statement() {
          @Override public void evaluate() throws Throwable {
            for (int i = 0; i <= times; i++) {
              try {
                statement.evaluate();
              } catch (AssertionError e) {
                throw annotate(i + 1, times, e);
              // We purposefully catch any non-assertion exceptions in order to tag the try count
              // for erroring (as opposed to failing) tests.
              // SUPPRESS CHECKSTYLE RegexpSinglelineJava
              } catch (Exception e) {
                throw annotate(i + 1, times, e);
              }
            }
          }
        };
      }
    }
  }
}
