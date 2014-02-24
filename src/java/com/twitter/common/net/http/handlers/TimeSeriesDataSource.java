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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;

import com.twitter.common.collections.Iterables2;
import com.twitter.common.stats.TimeSeries;
import com.twitter.common.stats.TimeSeriesRepository;

/**
 * A servlet that provides time series data in JSON format.
 */
public class TimeSeriesDataSource extends HttpServlet {

  @VisibleForTesting static final String TIME_METRIC = "time";

  private static final String METRICS = "metrics";
  private static final String SINCE = "since";

  private final TimeSeriesRepository timeSeriesRepo;
  private final Gson gson = new Gson();

  @Inject
  public TimeSeriesDataSource(TimeSeriesRepository timeSeriesRepo) {
    this.timeSeriesRepo = Preconditions.checkNotNull(timeSeriesRepo);
  }

  @VisibleForTesting
  String getResponse(
      @Nullable String metricsQuery,
      @Nullable String sinceQuery) throws MetricException {

    if (metricsQuery == null) {
      // Return metric listing.
      return gson.toJson(ImmutableList.copyOf(timeSeriesRepo.getAvailableSeries()));
    }

    List<Iterable<Number>> tsData = Lists.newArrayList();
    tsData.add(timeSeriesRepo.getTimestamps());
    // Ignore requests for "time" since it is implicitly returned.
    Iterable<String> names = Iterables.filter(
        Splitter.on(",").split(metricsQuery),
        Predicates.not(Predicates.equalTo(TIME_METRIC)));
    for (String metric : names) {
      TimeSeries series = timeSeriesRepo.get(metric);
      if (series == null) {
        JsonObject response = new JsonObject();
        response.addProperty("error", "Unknown metric " + metric);
        throw new MetricException(gson.toJson(response));
      }
      tsData.add(series.getSamples());
    }

    final long since = Long.parseLong(Optional.fromNullable(sinceQuery).or("0"));
    Predicate<List<Number>> sinceFilter = new Predicate<List<Number>>() {
      @Override public boolean apply(List<Number> next) {
        return next.get(0).longValue() > since;
      }
    };

    ResponseStruct response = new ResponseStruct(
        ImmutableList.<String>builder().add(TIME_METRIC).addAll(names).build(),
        FluentIterable.from(Iterables2.zip(tsData, 0)).filter(sinceFilter).toList());
    return gson.toJson(response);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    resp.setContentType(MediaType.JSON_UTF_8.toString());
    PrintWriter out = resp.getWriter();
    try {
      out.write(getResponse(req.getParameter(METRICS), req.getParameter(SINCE)));
    } catch (MetricException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.write(e.getMessage());
    }
  }

  @VisibleForTesting
  static class ResponseStruct {
    // Fields must be non-final for deserialization.
    List<String> names;
    List<List<Number>> data;

    ResponseStruct(List<String> names, List<List<Number>> data) {
      this.names = names;
      this.data = data;
    }
  }

  @VisibleForTesting
  static class MetricException extends Exception {
    MetricException(String message) {
      super(message);
    }
  }
}
