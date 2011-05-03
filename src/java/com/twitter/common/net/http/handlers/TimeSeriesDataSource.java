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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.visualization.datasource.Capabilities;
import com.google.visualization.datasource.DataSourceServlet;
import com.google.visualization.datasource.base.DataSourceException;
import com.google.visualization.datasource.base.ReasonType;
import com.google.visualization.datasource.datatable.ColumnDescription;
import com.google.visualization.datasource.datatable.DataTable;
import com.google.visualization.datasource.datatable.TableRow;
import com.google.visualization.datasource.datatable.value.ValueType;
import com.google.visualization.datasource.query.AbstractColumn;
import com.google.visualization.datasource.query.Query;
import com.google.visualization.datasource.query.QueryFilter;
import com.google.visualization.datasource.query.QueryLabels;
import com.google.visualization.datasource.query.QuerySelection;
import com.google.visualization.datasource.query.SimpleColumn;
import com.twitter.common.collections.Iterables2;
import com.twitter.common.stats.TimeSeries;
import com.twitter.common.stats.TimeSeriesRepository;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * A servlet that implements the Google Visuaization Data Source API and provides time series data.
 *
 * Queries supported:
 *   'SELECT * LIMIT 0': Retrieve a listing of all available time series columns.
 *   'SELECT $col': Select a specific column or set of columns.
 *   'WHERE $filter': Row filter.
 *   'LIMIT N': Limit the number of rows returned.
 *   'OFFSET N': Skips the first N columns that would otherwise be returend.
 *
 * @author William Farner
 */
public class TimeSeriesDataSource extends DataSourceServlet {

  @VisibleForTesting
  static final String TIME_COLUMN = "time";

  private final TimeSeriesRepository timeSeriesRepo;

  @Inject
  public TimeSeriesDataSource(TimeSeriesRepository timeSeriesRepo) {
    this.timeSeriesRepo = Preconditions.checkNotNull(timeSeriesRepo);
  }

  @Override
  protected boolean isRestrictedAccessMode() {
    // Allow requests from other hosts, WARNING: this exposes us to XSRF.
    return false;
  }

  @Override
  public Capabilities getCapabilities() {
    return Capabilities.SQL;
  }

  @Override
  public DataTable generateDataTable(Query query, HttpServletRequest request)
      throws DataSourceException {
    Preconditions.checkNotNull(query);

    int offset = query.getRowOffset();
    int limit = query.getRowLimit() == -1 ? Integer.MAX_VALUE : query.getRowLimit();
    QueryFilter queryFilter = query.getFilter();

    QuerySelection select = query.getSelection();
    // We only allow the selection to be null (which is equivalent to SELECT *) if no rows are
    // being returned.  This allows the caller to get the available columns.
    if (select == null && limit != 0) {
      throw new DataSourceException(ReasonType.INVALID_REQUEST, "Selection must be specified.");
    }

    // Gather query attributes.
    final QueryLabels labels = query.getLabels() == null ? new QueryLabels() : query.getLabels();

    List<AbstractColumn> columns = getColumns(select);
    DataTable table = new DataTable();

    // Create columns.
    table.addColumns(Lists.transform(columns, new Function<AbstractColumn, ColumnDescription>() {
      @Override public ColumnDescription apply(AbstractColumn column) {
        return new ColumnDescription(column.getId(), ValueType.NUMBER,
            labels.getLabel(column) != null ? labels.getLabel(column) : column.getId());
      }
    }));

    if (limit != 0) {
      List<Iterable<Number>> columnData = Lists.newArrayList();
      for (AbstractColumn column : columns) {
        columnData.add(getData(column));
      }

      // Build table rows.
      for (List<Number> rowData : Iterables.skip(Iterables2.zip(columnData, 0), offset)) {
        TableRow row = new TableRow();
        for (Number number : rowData) {
          row.addCell(number.doubleValue());
        }

        if (queryFilter == null || queryFilter.isMatch(table, row)) table.addRow(row);

        if (table.getNumberOfRows() >= limit) break;
      }
    }

    return table;
  }

  private static final Function<String, AbstractColumn> TO_SIMPLE_COLUMN =
      new Function<String, AbstractColumn>() {
        @Override public AbstractColumn apply(String colId) {
          return new SimpleColumn(colId);
        }
      };

  private List<AbstractColumn> getColumns(QuerySelection select) {
    if (select != null) return select.getColumns();

    // No columns specified, default to all columns.
    return Lists.newArrayList(
        Iterables.transform(timeSeriesRepo.getAvailableSeries(), TO_SIMPLE_COLUMN));
  }

  private Iterable<Number> getData(AbstractColumn column) throws DataSourceException {
    if (column.getId().equals(TIME_COLUMN)) return timeSeriesRepo.getTimestamps();

    TimeSeries series = timeSeriesRepo.get(column.getId());
    if (series == null) {
      throw new DataSourceException(ReasonType.INVALID_REQUEST, "Unknown column " + column);
    }

    return series.getSamples();
  }
}
