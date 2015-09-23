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

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.pantsbuild.junit.annotations.TestSerial;

// SUPPRESS CHECKSTYLE:OFF IllegalThrows
public class RetryTest {

  @TestSerial
  public abstract static class RetryTrackingTestBase {
    private static int tries;

    @BeforeClass
    public static void resetTries() {
      tries = 0;
    }

    enum Result {
      FAILURE() {
        @Override void execute() throws Throwable {
          Assert.fail("Simulated assertion failure.");
        }
      },
      ERROR() {
        @Override void execute() throws Throwable {
          throw new IOException("Simulated unexpected error.");
        }
      },
      SUCCESS() {
        @Override void execute() throws Throwable {
          Assert.assertTrue("Simulated successful assertion.", true);
        }
      };

      abstract void execute() throws Throwable;
    }

    @Rule public Retry.Rule retry = new Retry.Rule();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface AssertRetries {
      int expectedTries();
      int expectedMaxRetries();
      Result expectedResult();
    }

    @Rule
    public MethodRule testRetries = new MethodRule() {
      @Override
      public Statement apply(final Statement statement, FrameworkMethod method, Object receiver) {
        final AssertRetries assertRetries = method.getAnnotation(AssertRetries.class);
        Assert.assertNotNull(assertRetries);
        return new Statement() {
          @Override public void evaluate() throws Throwable {
            try {
              statement.evaluate();
              if (assertRetries.expectedResult() == Result.SUCCESS) {
                Assert.assertEquals(assertRetries.expectedTries(), tries);
              } else {
                Assert.fail("Expected success, found " + assertRetries.expectedResult());
              }
            } catch (Retry.Rule.RetriedAssertionError e) {
              if (assertRetries.expectedResult() == Result.FAILURE) {
                Assert.assertEquals(assertRetries.expectedTries(), tries);
                Assert.assertEquals(assertRetries.expectedMaxRetries(), e.getMaxRetries());
                Assert.assertEquals(assertRetries.expectedTries(), e.getTryNumber());
              } else {
                Assert.fail("Expected failure, found " + assertRetries.expectedResult());
              }
            } catch (Retry.Rule.RetriedException e) {
              if (assertRetries.expectedResult() == Result.ERROR) {
                Assert.assertEquals(assertRetries.expectedTries(), tries);
                Assert.assertEquals(assertRetries.expectedMaxRetries(), e.getMaxRetries());
                Assert.assertEquals(assertRetries.expectedTries(), e.getTryNumber());
              } else {
                Assert.fail("Expected error, found " + assertRetries.expectedResult());
              }
            }
          }
        };
      }
    };

    protected void doTest(int successfulTries) throws Throwable {
      doTest(successfulTries, null);
    }

    protected void doTest(int successfulTries, @Nullable Result lastResult) throws Throwable {
      tries++;
      if (lastResult != null && tries > successfulTries) {
        lastResult.execute();
      }
    }
  }

  public static class DefaultRetrySuccessTest extends RetryTrackingTestBase {
    @Test
    @Retry
    @AssertRetries(expectedTries = 2, expectedMaxRetries = 1, expectedResult = Result.SUCCESS)
    public void test() throws Throwable {
      doTest(2);
    }
  }

  public static class DefaultRetryFailFastTest extends RetryTrackingTestBase {
    @Test
    @Retry
    @AssertRetries(expectedTries = 1, expectedMaxRetries = 1, expectedResult = Result.FAILURE)
    public void test() throws Throwable {
      doTest(0, Result.FAILURE);
    }
  }

  public static class DefaultRetryFailLastTest extends RetryTrackingTestBase {
    @Test
    @Retry
    @AssertRetries(expectedTries = 2, expectedMaxRetries = 1, expectedResult = Result.FAILURE)
    public void test() throws Throwable {
      doTest(1, Result.FAILURE);
    }
  }

  public static class DefaultRetryErrorFastTest extends RetryTrackingTestBase {
    @Test
    @Retry
    @AssertRetries(expectedTries = 1, expectedMaxRetries = 1, expectedResult = Result.ERROR)
    public void test() throws Throwable {
      doTest(0, Result.ERROR);
    }
  }

  public static class DefaultRetryErrorLastTest extends RetryTrackingTestBase {
    @Test
    @Retry
    @AssertRetries(expectedTries = 2, expectedMaxRetries = 1, expectedResult = Result.ERROR)
    public void test() throws Throwable {
      doTest(1, Result.ERROR);
    }
  }

  public static class ZeroRetrySuccessTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = 0)
    @AssertRetries(expectedTries = 1, expectedMaxRetries = 0, expectedResult = Result.SUCCESS)
    public void test() throws Throwable {
      doTest(1, Result.SUCCESS);
    }
  }

  public static class NegativeRetrySuccessTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = -1)
    @AssertRetries(expectedTries = 1, expectedMaxRetries = 0, expectedResult = Result.SUCCESS)
    public void test() throws Throwable {
      doTest(1, Result.SUCCESS);
    }
  }

  public static class PositiveRetrySuccessTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = 2)
    @AssertRetries(expectedTries = 3, expectedMaxRetries = 2, expectedResult = Result.SUCCESS)
    public void test() throws Throwable {
      doTest(3, Result.SUCCESS);
    }
  }

  public static class PositiveRetryFailFastTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = 2)
    @AssertRetries(expectedTries = 1, expectedMaxRetries = 2, expectedResult = Result.FAILURE)
    public void test() throws Throwable {
      doTest(0, Result.FAILURE);
    }
  }

  public static class PositiveRetryFailLastTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = 2)
    @AssertRetries(expectedTries = 2, expectedMaxRetries = 2, expectedResult = Result.FAILURE)
    public void test() throws Throwable {
      doTest(1, Result.FAILURE);
    }
  }

  public static class PositiveRetryErrorFastTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = 2)
    @AssertRetries(expectedTries = 1, expectedMaxRetries = 2, expectedResult = Result.ERROR)
    public void test() throws Throwable {
      doTest(0, Result.ERROR);
    }
  }

  public static class PositiveRetryErrorLastTest extends RetryTrackingTestBase {
    @Test
    @Retry(times = 2)
    @AssertRetries(expectedTries = 2, expectedMaxRetries = 2, expectedResult = Result.ERROR)
    public void test() throws Throwable {
      doTest(1, Result.ERROR);
    }
  }
}
// SUPPRESS CHECKSTYLE:ON IllegalThrows
