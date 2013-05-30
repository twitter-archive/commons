package com.twitter.common.junit.runner;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.twitter.common.junit.annotations.TestSerial;

/**
 * Tests several recently added features in ConsoleRunner.
 * TODO: cover the rest of ConsoleRunner functionality.
 */
@Ignore
@TestSerial
public class ConsoleRunnerTest {

  @Before
  public void setUp() {
    ConsoleRunner.setCallSystemExitOnFinish(false);
    ConsoleRunner.setExitStatus(0);
    TestRegistry.reset();
  }

  @After
  public void tearDown() {
    ConsoleRunner.setCallSystemExitOnFinish(true);
    ConsoleRunner.setExitStatus(0);
  }

  @Test
  public void testNormalTesting() throws Exception {
    ConsoleRunner.main(asArgsArray("MockTest1 MockTest2 MockTest3"));
    Assert.assertEquals("test11 test12 test13 test21 test22 test31 test32",
        TestRegistry.getCalledTests());
  }

  @Test
  public void testShardedTesting02() throws Exception {
    ConsoleRunner.main(asArgsArray("MockTest1 MockTest2 MockTest3 -test-shard 0/2"));
    Assert.assertEquals("test11 test13 test22 test32", TestRegistry.getCalledTests());
  }

  @Test
  public void testShardedTesting13() throws Exception {
    ConsoleRunner.main(asArgsArray("MockTest1 MockTest2 MockTest3 -test-shard 1/3"));
    Assert.assertEquals("test12 test22", TestRegistry.getCalledTests());
  }

  @Test
  public void testShardedTesting23() throws Exception {
    // This tests a corner case when no tests from MockTest2 are going to run.
    ConsoleRunner.main(asArgsArray(
        "MockTest1 MockTest2 MockTest3 -test-shard 2/3"));
    Assert.assertEquals("test13 test31", TestRegistry.getCalledTests());
  }

  @Test
  public void testShardedTesting12WithParallelThreads() throws Exception {
    ConsoleRunner.main(asArgsArray(
        "MockTest1 MockTest2 MockTest3 -test-shard 1/2 -parallel-threads 4 -default-parallel"));
    Assert.assertEquals("test12 test21 test31", TestRegistry.getCalledTests());
  }

  @Test
  public void testShardedTesting23WithParallelThreads() throws Exception {
    // This tests a corner case when no tests from MockTest2 are going to run.
    ConsoleRunner.main(asArgsArray(
        "MockTest1 MockTest2 MockTest3 -test-shard 2/3 -parallel-threads 3 -default-parallel"));
    Assert.assertEquals("test13 test31", TestRegistry.getCalledTests());
  }

  private String[] asArgsArray(String cmdLine) {
    String[] args = cmdLine.split(" ");
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("MockTest")) {
        args[i] = getClass().getPackage().getName() + '.' + args[i];
      }
    }
    return args;
  }
}
