package com.twitter.common.stats;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.twitter.common.testing.easymock.EasyMockTest;
import com.twitter.common.util.Random;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Tests the Reservoir Sampler code
 *
 * @author Delip Rao
 */
public class ReservoirSamplerTest extends EasyMockTest {

  private Random random;

  @Before
  public void setUp() throws Exception {
    random = createMock(Random.class);
  }

  @Test
  public void testSampling() throws Exception {
    int mockValues[] = {3, 4, 5, 6, 7};
    for (int value : mockValues) {
      expect(random.nextInt(value + 1)).andReturn(value);
    }
    control.replay();

    ReservoirSampler<Integer> sampler = new ReservoirSampler<Integer>(3, random);
    List<Integer> stream = ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8);
    for (int i : stream) {
      sampler.sample(i);
    }
    List<Integer> expectedSamples = ImmutableList.of(1, 2, 3);
    assertEquals("The samples should be 1, 2, 3", expectedSamples,
        ImmutableList.copyOf(sampler.getSamples()));
  }

  @Test
  public void testNoSampling() throws Exception {
    // no calls to random.nextInt should happen in this test
    control.replay();
    List<Integer> stream = ImmutableList.of(1, 2, 3);
    // reservoir is larger than the stream. No sampling should happen here.
    ReservoirSampler<Integer> sampler = new ReservoirSampler<Integer>(20);
    for (int i : stream) {
      sampler.sample(i);
    }
    assertEquals("The samples should be same as the stream", stream,
        ImmutableList.copyOf(sampler.getSamples()));
  }
}
