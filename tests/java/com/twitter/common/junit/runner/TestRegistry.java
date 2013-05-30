package com.twitter.common.junit.runner;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * This is used by each of our mock tests to register the fact that it was called.
 * Unfortunately, we have to use a static API, and explicit reset() call is needed
 * every time before running a new real test. Trying to use a singleton object seems
 * to cause more problems than it fixes, since our MockTestX are called independently
 * by pants, and they may catch TestRegistry singleton in uninitialized state.
 */
final class TestRegistry {

  private static LinkedHashSet<String> testsCalled = new LinkedHashSet<String>();

  // No instances of this classes can be constructed
  private TestRegistry() { }

  static synchronized void registerTestCall(String testId) {
    testsCalled.add(testId);
  }

  static synchronized void reset() {
    testsCalled.clear();
  }

  /** Returns the called tests in sorted order as a single string */
  static String getCalledTests() {
    return getCalledTests(true);
  }

  static synchronized String getCalledTests(boolean sort) {
    String[] tests = testsCalled.toArray(new String[testsCalled.size()]);
    if (sort) {
      Arrays.sort(tests);
    }
    StringBuilder sb = new StringBuilder(50);
    sb.append(tests[0]);
    for (int i = 1; i < tests.length; i++) {
      sb.append(' ').append(tests[i]);
    }
    return sb.toString();
  }
}
