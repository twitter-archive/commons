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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.visualization.datasource.base.DataSourceException;
import com.google.visualization.datasource.base.InvalidQueryException;
import com.google.visualization.datasource.datatable.ColumnDescription;
import com.google.visualization.datasource.datatable.DataTable;
import com.google.visualization.datasource.datatable.TableCell;
import com.google.visualization.datasource.datatable.TableRow;
import com.google.visualization.datasource.datatable.value.NumberValue;
import com.google.visualization.datasource.query.Query;
import com.google.visualization.datasource.query.parser.ParseException;
import com.google.visualization.datasource.query.parser.QueryParser;
import com.twitter.common.collections.Iterables2;
import com.twitter.common.stats.TimeSeries;
import com.twitter.common.stats.TimeSeriesRepository;
import com.twitter.common.testing.EasyMockTest;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.easymock.EasyMock.expect;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author William Farner
 */
public class TimeSeriesDataSourceTest extends EasyMockTest {

  private static final String TIME_COLUMN = TimeSeriesDataSource.TIME_COLUMN;
  private static final String TIME_SERIES_1 = "time_series_1";
  private static final String TIME_SERIES_2 = "time_series_2";

  private static final List<Number> TIMESTAMPS = Arrays.<Number>asList(1d, 2d, 3d, 4d);
  private static final Map<String, TimeSeries> TS_DATA = ImmutableMap.of(
      TIME_SERIES_1, makeTimeSeries(TIME_SERIES_1, 1, 2, 3, 4),
      TIME_SERIES_2, makeTimeSeries(TIME_SERIES_2, 0, 0, 0, 0)
  );

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

    DataTable data = fetch("SELECT * LIMIT 0");
    assertThat(data.getNumberOfRows(), is(0));
    assertThat(data.getNumberOfColumns(), is(TS_DATA.keySet().size()));
    Set<String> colNames = Sets.newHashSet(Iterables.transform(data.getColumnDescriptions(),
        new Function<ColumnDescription, String>() {
          @Override public String apply(ColumnDescription col) { return col.getId(); }
        }));
    assertThat(colNames, is(TS_DATA.keySet()));
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

    DataTable data = fetch("SELECT  " + colString);
    assertThat(data.getNumberOfColumns(), is(TS_DATA.keySet().size() + 1));
    assertThat(data.getNumberOfRows(), is(TIMESTAMPS.size()));

    Iterable<List<Number>> expectedData = Iterables2.zip(0,
        getSamples(TIME_SERIES_1), getSamples(TIME_SERIES_2), TIMESTAMPS);

    checkRows(data.getRows(), expectedData);
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

    DataTable data = fetch("SELECT  " + colString + " WHERE time >= 3");
    assertThat(data.getNumberOfColumns(), is(TS_DATA.keySet().size() + 1));

    Iterable<List<Number>> expectedData = Iterables2.zip(0,
        getSamples(TIME_SERIES_1), getSamples(TIME_SERIES_2), TIMESTAMPS);
    expectedData = Iterables.filter(expectedData, new Predicate<List<Number>>() {
        @Override public boolean apply(List<Number> row) {
          return row.get(2).intValue() >= 3;
        }
      });

    assertThat(data.getNumberOfRows(), is(Iterables.size(expectedData)));
    checkRows(data.getRows(), expectedData);
  }

  private static void checkRows(List<TableRow> rows, Iterable<List<Number>> expectedValues) {
    Iterator<List<Number>> expectedValueIterator = expectedValues.iterator();

    for (TableRow row : rows) {
      List<Number> rowValues = Lists.transform(row.getCells(), new Function<TableCell, Number>() {
        @Override public Number apply(TableCell cell) {
          assertThat(cell.getValue() instanceof NumberValue, is(true));
          return ((NumberValue) cell.getValue()).getValue();
        }
      });

      assertThat(rowValues, is(expectedValueIterator.next()));
    }
  }

  private DataTable fetch(String query) throws DataSourceException, ParseException {
    return dataSource.generateDataTable(getQuery(query), null);
  }

  private static Query getQuery(String query) throws InvalidQueryException, ParseException {
    return QueryParser.parseString(query);
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
