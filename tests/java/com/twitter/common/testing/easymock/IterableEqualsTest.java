package com.twitter.common.testing.easymock;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.expect;

import static com.twitter.common.testing.easymock.IterableEquals.eqCollection;
import static com.twitter.common.testing.easymock.IterableEquals.eqIterable;
import static com.twitter.common.testing.easymock.IterableEquals.eqList;

public class IterableEqualsTest extends EasyMockTest {
  private static final List<Integer> TEST = ImmutableList.of(1, 2, 3, 2);
  private static final String OK = "ok";
  private Thing thing;

  public interface Thing {
    String testIterable(Iterable<Integer> input);
    String testCollection(Collection<Integer> input);
    String testList(List<Integer> input);
  }

  @Before
  public void setUp() {
    thing = createMock(Thing.class);
  }

  @Test
  public void testIterableEquals() {
    expect(thing.testIterable(eqIterable(TEST))).andReturn(OK);
    control.replay();
    thing.testIterable(ImmutableList.of(3, 2, 2, 1));
  }

  @Test
  public void testCollectionEquals() {
    expect(thing.testCollection(eqCollection(TEST))).andReturn(OK);
    control.replay();
    thing.testCollection(ImmutableList.of(3, 2, 2, 1));
  }

  @Test
  public void testListEquals() {
    expect(thing.testList(eqList(TEST))).andReturn(OK);
    control.replay();
    thing.testList(ImmutableList.of(3, 2, 2, 1));
  }
}
