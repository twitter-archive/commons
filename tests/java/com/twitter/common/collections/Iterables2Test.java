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

package com.twitter.common.collections;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class Iterables2Test {

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testZipSingleIterable() {
    assertValues(Iterables2.zip(0, list(1, 2, 3, 4)),
        list(1),
        list(2),
        list(3),
        list(4)
    );
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testZipDefaultValue() {
    assertValues(Iterables2.zip(10, list(1, 2, 3, 4), list(1)),
        list(1, 1),
        list(2, 10),
        list(3, 10),
        list(4, 10)
    );
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testZipNbyN() {
    assertValues(Iterables2.zip(10, list(1, 2, 3, 4), list(5, 6, 7, 8)),
        list(1, 5),
        list(2, 6),
        list(3, 7),
        list(4, 8)
    );
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testZipEmptyIterable() {
    assertValues(Iterables2.zip(10, list(1, 2, 3, 4), Arrays.<Integer>asList()),
        list(1, 10),
        list(2, 10),
        list(3, 10),
        list(4, 10)
    );
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testZipRemove() {
    final int DEFAULT = 10;
    Iterable<List<Integer>> meta = Iterables2.zip(DEFAULT,
        list(1, 2, 3, 4),
        list(5, 6, 7, 8),
        list(9));

    // Attempt to trim all rows that have the default value.
    Iterables.removeIf(meta, new Predicate<List<Integer>>() {
      @Override public boolean apply(List<Integer> input) {
        return Iterables.contains(input, DEFAULT);
      }
    });

    assertValues(meta, list(1, 5, 9));
  }

  private static List<Integer> list(Integer... ints) {
    return Lists.newArrayList(ints);
  }

  private static void assertValues(Iterable<List<Integer>> meta, List<Integer>... rows) {
    assertThat(Iterables.size(meta), is(rows.length));
    int i = 0;
    for (List<Integer> row : meta) {
      assertThat(row, is(rows[i++]));
    }
  }
}
