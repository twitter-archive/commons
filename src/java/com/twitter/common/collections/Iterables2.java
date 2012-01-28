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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Utility functions for dealing with iterables.
 *
 * @author William Farner
 */
public final class Iterables2 {

  private Iterables2() {
    // Utility class.
  }

  /**
   * An iterator that zips multiple iterables into a single list iterator, filling missing values
   * with a provided default.
   *
   * @param <T> The value type for the iterator.
   */
  private static class ZippingIterator<T> implements Iterator<List<T>> {

    private final Iterable<Iterable<T>> iterables;
    private final T defaultValue;

    private List<Iterator<T>> iterators = null;
    private final LoadingCache<Iterator<T>, Boolean> overflowing = CacheBuilder.newBuilder().build(
        new CacheLoader<Iterator<T>, Boolean>() {
          @Override public Boolean load(Iterator<T> iterator) {
            return false;
          }
        });

    ZippingIterator(Iterable<Iterable<T>> iterables, T defaultValue) {
      this.iterables = iterables;
      this.defaultValue = defaultValue;
    }

    private void init() {
      if (iterators == null) {
        // Iterables -> Iterators.
        iterators = ImmutableList.copyOf(Iterables.transform(iterables,
            new Function<Iterable<T>, Iterator<T>>() {
              @Override public Iterator<T> apply(Iterable<T> it) { return it.iterator(); }
            }));
      }
    }

    @Override public boolean hasNext() {
      init();
      for (Iterator<T> it : iterators) {
        if (it.hasNext()) {
          return true;
        }
      }

      return false;
    }

    @Override public List<T> next() {
      init();
      List<T> data = new ArrayList<T>(iterators.size());

      for (Iterator<T> it : iterators) {
        if (it.hasNext()) {
          data.add(it.next());
        } else {
          overflowing.asMap().put(it, true);
          data.add(defaultValue);
        }
      }

      return data;
    }

    @Override public void remove() {
      init();
      for (Iterator<T> it : iterators) {
        if (!overflowing.getUnchecked(it)) {
          it.remove();
        }
      }
    }

    @Override public String toString() {
      return Lists.newArrayList(iterables).toString();
    }
  }

  /**
   * Zips multiple iterables into one iterable that will return iterators to step over
   * rows of the input iterators (columns).  The order of the returned values within each row will
   * match the ordering of the input iterables. The iterators will iterate the length of the longest
   * input iterable, filling other columns with {@code defaultValue}.
   * The returned iterator is lazy, in that 'rows' are constructed as they are requested.
   *
   * @param iterables Columns to iterate over.
   * @param defaultValue Default fill value when an input iterable is exhausted.
   * @param <T> Type of value being iterated over.
   * @return An iterator that iterates over rows of the input iterables.
   */
  public static <T> Iterable<List<T>> zip(final Iterable<Iterable<T>> iterables,
      final T defaultValue) {

    return new Iterable<List<T>>() {
      @Override public Iterator<List<T>> iterator() {
        return new ZippingIterator<T>(iterables, defaultValue);
      }
    };
  }

  /**
   * Varargs convenience function to call {@link #zip(Iterable, Object)}.
   *
   * @param defaultValue Default fill value when an input iterable is exhausted.
   * @param iterables Columns to iterate over.
   * @param <T> Type of value being iterated over.
   * @return An iterator that iterates over rows of the input iterables.
   */
  public static <T> Iterable<List<T>> zip(T defaultValue, Iterable<T>... iterables) {
    return zip(Arrays.asList(iterables), defaultValue);
  }
}
