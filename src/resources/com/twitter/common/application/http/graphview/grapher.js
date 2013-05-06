// =================================================================================================
// Copyright 2012 Twitter, Inc.
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

// Whether there is currently an outbound request.
var awaitingResponse = false;

var allMetrics = [];     // Names of all available metrics.
var metrics = {};        // Time series names, mapped to their index in rawData.
var rawData;             // 2-d array of metric data from the server.
var evaluatedData = [];  // 2-d array of evaluated metric data.

// Expressions being plotted.
var expressions = {};

// Timestamp of the most-recent row.
var lastTimestamp = -1;

// Whether to continuously update the graph with new data..
var realTime = true;

// Store at most 3600 points per plot.
var maxPoints = 3600;

// Actual dygraph object.
var dygraph;

// Formatter for the x axis.
var xAxisFormatter = function(date, self) {
  return Dygraph.hmsString_(date);
}

// Dygraph graph options.
var options = {
  axes: { x: { valueFormatter: xAxisFormatter,
               axisLabelFormatter: xAxisFormatter
             }
        },
  labelsSeparateLines: true,
  hideOverlayOnMouseOut: false,
  showRangeSelector: true,
  labelsDiv: 'legend',
  legend: 'always',
};

/**
 * Issues a query to fetch graph data from the server.
 */
function fetchData() {
  var metrics = {};
  $.each(Object.keys(expressions), function(i, name) {
    $.each(expressions[name].getVars(), function(j, metric) {
      metrics[metric] = true;
    });
  });
  if ($('#errors').is(':empty') && Object.keys(metrics).length > 0) {
    sendQuery('?metrics=' + Object.keys(metrics).join(',') + '&since=' + lastTimestamp,
              handleDataResponse);
  }
}

/**
 * Clears error messages from the page.
 */
function clearErrors() {
  $('#errors').hide();
  $('#errors').empty();
}

function addErrorText(text) {
  $('#errors').append(text).show();
}

function addError(expr, error, pos) {
  throw expr + '\n' + Array(pos + 1).join(' ') + '^ ' + error;
}

/**
 * Issues a query to fetch time series data from the server.
 * If the request is successful, the provided response handler will be called.
 */
function sendQuery(query, responseHandler) {
  if (awaitingResponse) return;
  if (!realTime) return;
  awaitingResponse = true;

  var wrappedHandler = function(data) {
    awaitingResponse = false;
    responseHandler(data);
  };

  $.getJSON('/graphdata/' + query, wrappedHandler).error(
    function(xhr) {
      addErrorText('Query failed: ' + $.parseJSON(xhr.responseText).error);
      awaitingResponse = false;
    });
}

/**
 * Clears stored data and fetches all plot data.
 */
function refreshGraph() {
  append = false;
  rawData = null;
  evaluatedData = [];
  metrics = {};
  lastTimestamp = -1;
  fetchData();
}

/**
 * Redraws the graph with the current data and options.
 */
function redraw() {
  options.file = evaluatedData;
  options.labels = ['t'].concat(Object.keys(expressions));
  dygraph.updateOptions(options);
}

/**
 * Handles a data query response from the server.
 */
function handleDataResponse(resp) {
  var newData = resp.data;

  if (newData.length == 0) {
    return;
  }

  var append = false;
  if (!rawData) {
    rawData = newData;
    $.each(resp.names, function(i, name) {
      metrics[name] = i;
    });
  } else {
    // Append the new table to the old table.
    // TODO(William Farner): Make sure metricNames indices match up.
    rawData = rawData.concat(newData);
    append = true;
  }

  // Feed data into expressions.
  $.each(newData, function(j, point) {
    evaluatedData.push([point[metrics["time"]]].concat(
      $.map(Object.keys(expressions), function(name) {
        return expressions[name].evaluate(point);
      })));
  });

  // Figure out what the last timestamp is.
  lastTimestamp = rawData[rawData.length - 1][metrics["time"]];

  // Evict the oldest rows.
  if (rawData.length > maxPoints) {
    rawData.splice(0, rawData.length - maxPoints);
  }
  if (evaluatedData.length > maxPoints) {
    evaluatedData.splice(0, evaluatedData.length - maxPoints);
  }

  if (append) {
    redraw();
  } else {
    options.labels = ['t'].concat(Object.keys(expressions));
    dygraph = new Dygraph($('#dygraph')[0], evaluatedData, options);
  }
}

/**
 * Calls the apply function with the parsed value extracted from a text field.
 * If the value of the text field is not a valid number, the apply function will
 * not be called.
 */
function tryApply(textField, numberParser, applyFunction) {
  var number = numberParser(textField.value);
  if (!isNaN(number)) {
    applyFunction(number);
  }

  return false;
}

/**
 * Convenience function to call tryApply() if the provided key press event
 * was for the enter key.
 */
function applyOnEnter(event, textField, numberParser, applyFunction) {
  if (event.keyCode == 13) tryApply(textField, numberParser, applyFunction);
}

function applyQuery() {
  var query = $('#query').val();
  clearErrors();
  $('#links').empty();
  expressions = {};
  $.each($('#query').val().replace(/[ \t]/g, '').split('\n'), function(i, query) {
    if (query) {
      try {
        expressions[query] = parser.parse(query);
      } catch (err) {
        addErrorText(err);
      }
    }
  });
  refreshGraph();
  $('#links')
    .append($('<a>', {
      text: 'link',
      href: '?query=' + encodeURIComponent($('#query').val()),
      target: 'none'}))
    .append($('<br />'))
    .append($('<a>', {
      text: 'img',
      href: '#',
      id: 'download'}));
  $('#download').click(function() {
    window.location = Dygraph.Export.asCanvas(dygraph).toDataURL('image/png');
  });
}

$(document).ready(function() {
  $('#submit').click(applyQuery);
  var fieldApplier = function(selector, verify, apply) {
    $(selector).keypress(function(e) {
      applyOnEnter(e, this, verify, apply);
    });
    $(selector).blur(function() {
      tryApply(this, verify, apply);
    });
  }
  fieldApplier('#yMin', parseFloat, function(ymin) {
    dygraph.updateOptions({'valueRange': [ymin, dygraph.yAxisRange(0)[1]]});
  });
  fieldApplier('#yMax', parseFloat, function(ymax) {
    dygraph.updateOptions({'valueRange': [dygraph.yAxisRange(0)[0], ymax]});
  });
  fieldApplier('#smoothing', parseInt, function(value) {
    options.rollPeriod = value;
    redraw();
  });
  $('#realTime').click(function() {
    realTime = !realTime;
    redraw();
  });

  sendQuery('', function(metrics) {
    metrics.sort();
    allMetrics = metrics;
    $.each(metrics, function(i, metric) {
      $('#availableMetrics')
          .append($('<option></option>')
          .attr('value', metric)
          .text(metric));
    });
    $('#availableMetrics').change(function() {
      var q = $('#query');
      var pos = q[0].selectionStart;
      var adjustedPos;
      var val = q.val();
      var metric = $('#availableMetrics').val();
      if (pos == val.length) {
        // The cursor is at the end.  For convenience, append the selection
        // and insert a newline.  This simplifies selection of multiple plots.
        q.val(val + metric + '\n');
        adjustedPos = pos + metric.length + 1;
      } else {
        q.val(val.substring(0, pos) + metric + val.substring(pos));
        adjustedPos = val.substring(0, pos).length + metric.length;
      }
      q[0].selectionStart = adjustedPos;
      q[0].selectionEnd = adjustedPos;
    });

    var termBounds = function() {
      var isIdChar = function(c) {
        return /[\w\-]/g.exec(c);
      };
      var pos = $('#query')[0].selectionStart;
      var fullQuery = $('#query').val();
      if (pos > 0 && isIdChar(fullQuery.charAt(pos - 1))) {
        if (fullQuery.length > pos && isIdChar(fullQuery.charAt(pos))) {
          // Break out if this is editing within a word.
          return;
        }

        // Walk backwards to the beginning of the word.
        var wordStart = pos;
        while (wordStart > 0 && isIdChar(fullQuery.charAt(wordStart - 1))) {
          wordStart--;
        }
        return {
          wordStart: wordStart,
          pos: pos
        };
      }
    };
    $('#query').autocomplete({
      delay: 0,
      source: function(request, response) {
        var results = [];
        var term = termBounds();
        if (term) {
          var corpus = $.map(allMetrics, function(metric) {
            return {
              label: metric,
              value: metric
            };
          });
          corpus = corpus.concat($.map(Object.keys(parser.functions), function(f) {
            return {
              label: parser.functions[f].help, value: f
            };
          }));
          var text = $('#query').val().substring(term.wordStart, term.pos);
          response($.ui.autocomplete.filter(corpus, text));
        } else {
          response([]);
        }
      },
      select: function(event, ui) {
        var query = $('#query').val()
        var bounds = termBounds();
        if (bounds) {
          this.value = query.substring(0, bounds.wordStart)
              + ui.item.value
              + query.substring(bounds.pos);
          var adjustedPos = bounds.wordStart + ui.item.value.length;
          // Note: This is not likely to work in IE.
          $('#query')[0].selectionStart = adjustedPos;
          $('#query')[0].selectionEnd = adjustedPos;
        }
        return false;
      },
      focus: function() { return false; }
    });
  });
  // Submit on alt/option-enter.
  $('#query').keypress(function(e) {
    if (e.which == 13 && e.altKey) {
      applyQuery();
      return false;
    }
  });
  var getUrlParam = function(name) {
    var match = RegExp(name + '=' + '(.+?)(&|$)').exec(location.search);
    return match ? decodeURIComponent(match[1]) : null;
  };
  var urlQuery = getUrlParam('query');
  if (urlQuery != null) {
    $('#query').val(urlQuery);
    applyQuery();
  }

  setInterval(fetchData, 1000);
});
