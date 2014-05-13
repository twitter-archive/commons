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

package com.twitter.common.net.http.handlers;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Before;
import org.junit.Test;

import com.twitter.common.collections.Iterables2;
import com.twitter.common.net.http.handlers.TimeSeriesDataSource.ResponseStruct;
import com.twitter.common.stats.TimeSeries;
import com.twitter.common.stats.TimeSeriesRepository;
import com.twitter.common.testing.easymock.EasyMockTest;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

public class TimeSeriesDataSourceTest extends EasyMockTest {

  private static final String TIME_COLUMN = TimeSeriesDataSource.TIME_METRIC;
  private static final String TIME_SERIES_1 = "time_series_1";
  private static final String TIME_SERIES_2 = "time_series_2";

  private static final List<Number> TIMESTAMPS = Arrays.<Number>asList(1d, 2d, 3d, 4d);
  private static final Map<String, TimeSeries> TS_DATA = ImmutableMap.of(
      TIME_SERIES_1, makeTimeSeries(TIME_SERIES_1, 1, 2, 3, 4),
      TIME_SERIES_2, makeTimeSeries(TIME_SERIES_2, 0, 0, 0, 0)
  );

  private final Gson gson = new Gson();

  private TimeSeriesDataSource dataSource;
  private TimeSeriesRepository timeSeriesRepo;

  @Before
  public void setUp() {
    timeSeriesRepo = createMock(TimeSeriesRepository.class);
    dataSource = new TimeSeriesDataSource(timeSeriesRepo);
  }

  @Test
  public void testGetColumns() throws Exception {
    expect(timeSeriesRepo.getAvailableSeries()).andReturn(TS_DATA.keySet());

    control.replay();

    List<String> columns = gson.fromJson(
        dataSource.getResponse(null, null),
        new TypeToken<List<String>>() { }.getType());
    assertEquals(ImmutableList.copyOf(TS_DATA.keySet()), columns);
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testGetAllData() throws Exception {
    expect(timeSeriesRepo.getTimestamps()).andReturn(TIMESTAMPS);
    expect(timeSeriesRepo.get(TIME_SERIES_1)).andReturn(TS_DATA.get(TIME_SERIES_1));
    expect(timeSeriesRepo.get(TIME_SERIES_2)).andReturn(TS_DATA.get(TIME_SERIES_2));

    control.replay();

    String colString = Joiner.on(',').join(
        Arrays.asList(TIME_SERIES_1, TIME_SERIES_2, TIME_COLUMN));

    ResponseStruct response = gson.fromJson(
        dataSource.getResponse(colString, null),
        ResponseStruct.class);

    assertEquals(ImmutableList.of(TIME_COLUMN, TIME_SERIES_1, TIME_SERIES_2), response.names);
    Iterable<List<Number>> expectedData = Iterables2.zip(0,
        TIMESTAMPS, getSamples(TIME_SERIES_1), getSamples(TIME_SERIES_2));
    checkRows(expectedData, response.data);
  }

  @Test
  @SuppressWarnings("unchecked") // Needed because type information lost in vargs.
  public void testFilterByTime() throws Exception {
    expect(timeSeriesRepo.getTimestamps()).andReturn(TIMESTAMPS);
    expect(timeSeriesRepo.get(TIME_SERIES_1)).andReturn(TS_DATA.get(TIME_SERIES_1));
    expect(timeSeriesRepo.get(TIME_SERIES_2)).andReturn(TS_DATA.get(TIME_SERIES_2));

    control.replay();

    String colString = Joiner.on(',').join(
        Arrays.asList(TIME_SERIES_1, TIME_SERIES_2, TIME_COLUMN));

    ResponseStruct response = gson.fromJson(
        dataSource.getResponse(colString, "2"),
        ResponseStruct.class);

    Iterable<List<Number>> expectedData = Iterables2.zip(0,
        TIMESTAMPS, getSamples(TIME_SERIES_1), getSamples(TIME_SERIES_2));
    expectedData = Iterables.filter(expectedData, new Predicate<List<Number>>() {
        @Override public boolean apply(List<Number> row) {
          return row.get(0).intValue() >= 3;
        }
      });

    checkRows(expectedData, response.data);
  }

  private void checkRows(Iterable<List<Number>> expected, List<List<Number>> actual) {
    assertEquals(Iterables.size(expected), actual.size());
    Iterator<List<Number>> actualIterator = actual.iterator();
    for (List<Number> expectedRow : expected) {
      Iterator<Number> actualValueIterator = actualIterator.next().iterator();
      for (Number expectedValue : expectedRow) {
        assertEquals("Expected row data " + expected + ", found " + actual,
            expectedValue.doubleValue(),
            actualValueIterator.next().doubleValue(),
            1e-9);
      }
    }
  }

  private static Iterable<Number> getSamples(String tsName) {
    return TS_DATA.get(tsName).getSamples();
  }

  private static TimeSeries makeTimeSeries(final String name, final Number... values) {
    final List<Number> samples = Lists.newArrayList();
    for (Number value : values) samples.add(value.doubleValue());

    return new TimeSeries() {
      @Override public String getName() { return name; }

      @Override public Iterable<Number> getSamples() {
        return samples;
      }
    };
  }
}
