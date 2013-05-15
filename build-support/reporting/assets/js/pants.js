
pants = {
  collapsible: {
    toggle: function(id) {
      $("#" + id + "-content").toggle();
      $("#" + id + "-icon").toggleClass("icon-caret-right icon-caret-down")
    },

    expand: function(id) {
      $("#" + id + "-content").show();
      $("#" + id + "-icon").removeClass("icon-caret-right").addClass("icon-caret-down")
    },

    collapse: function(id) {
      $("#" + id + "-content").hide();
      $("#" + id + "-icon").removeClass("icon-caret-down").addClass("icon-caret-right")
    },

    hasContent: function(id) {
      $('#' + id + '-header').children().removeClass('greyed-header-text');
      $('#' + id + '-icon').removeClass('hidden');
    }
  },

  append: function(fromSelector, toSelector) {
    $(fromSelector).appendTo($(toSelector)).show();
  },

  appendString: function(str, toSelector) {
    $(toSelector).append(str);
  },

  // Creates an object that knows how to manage multiple timers, and periodically emit them.
  createTimerManager: function() {
    // The start time (in ms since the epoch) of each timer.
    // We emit each timer to the element(s) selected by the appropriate selector.
    // id -> {startTime: ..., selector: ...}
    var timers = {};

    // A handle to the polling event, so we can cancel it if needed.
    var timingEvent = undefined;

    function updateTimers() {
      var now = $.now();
      $.each(timers, function(id, timer) {
        $(timer.selector).html('' + Math.round((now - timer.startTime) / 1000 - 0.5) + 's');
      });
    }

    return {
      startTimer: function(id, selector, init) {
        timers[id] = { 'startTime': init ? init : $.now(), 'selector': selector };
        if (!timingEvent) {
          timingEvent = window.setInterval(updateTimers, 1000);
        }
      },

      stopTimer: function(id) {
        delete timers[id];
        var numTimers = 0;
        $.each(timers, function(k,v) { numTimers++ });
        if (numTimers == 0) {
          window.clearInterval(timingEvent);
          timingEvent = undefined;
        }
      }
    }
  },

  // Creates an object that knows how to poll multiple files by periodically hitting the server.
  // Each polled file is associated with an id, so we can multiplex multiple pollings on
  // on a single server request.
  createPoller: function() {

    // State of each file we're polling.
    // id -> state object.
    var polledFileStates = {};

    // A handle to the polling event, so we can cancel it if needed.
    var pollingEvent = undefined;

    function pollOnce() {
      function forgetId(id) {
        delete polledFileStates[id];
        var n = 0;
        $.each(polledFileStates, function(k, v) { n += 1; });
        if (!n) {
          window.clearInterval(pollingEvent);
          pollingEvent = undefined;
        }
      }

      var polledStates = [];

      function createRequestEntry(state, id) {
        if (state.inFlight) {
          return null;
        } else {
          state.inFlight = true;
          polledStates.push(state);
          return { id: id, path: state.path, pos: state.pos };
        }
      }

      $.ajax({
        url: '/poll',
        type: 'GET',
        data: { q: JSON.stringify($.map(polledFileStates, createRequestEntry))},
        dataType: 'json',
        success: function(data, textStatus, jqXHR) {
          function appendNewData() {
            $.each(data, function(id, val) {
              if (id in polledFileStates) {
                var state = polledFileStates[id];
                if (!state.hasBeenPolledAtLeastOnce) {
                  if (state.initFunc) {
                    state.initFunc();
                  }
                  state.hasBeenPolledAtLeastOnce = true;
                }
                if (state.predicate ? state.predicate(val) : true) {
                  if (state.replace) {
                    // Replacing can reset state, so only do it if we have to.
                    if (val != state.currentVal) {
                      $(state.selector).html(val);
                    }
                  } else {
                    $(state.selector).append(val);
                    state.pos += val.length;
                  }
                  state.currentVal = val;
                }
              }
            });
          }

          function checkForStopped() {
            var toDelete = [];
            $.each(polledFileStates, function(id, state) {
              if (state.toBeStopped && state.hasBeenPolledAtLeastOnce) {
                toDelete.push(id);
              }
            });
            $.each(toDelete, function(idx, id) { forgetId(id); });
          }
          appendNewData();
          checkForStopped();
        },
        error: function(jqXHR, textStatus, errorThrown) {
          // TODO: Something?
        },
        complete: function(jqXHR, textStatus) {
          $.each(polledStates, function(idx, state) { state.inFlight = false; });
          polledStates.length = 0;
        }
      });
    }

    function doStartPolling(id, path, targetSelector, initFunc, predicate, replace) {
      polledFileStates[id] = {
        path: path,
        pos: 0,
        inFlight: false,
        replace: replace,
        currentVal: '',
        selector: targetSelector,
        initFunc: initFunc,
        predicate: predicate,
        hasBeenPolledAtLeastOnce: false,
        toBeStopped: false
      };
      if (!pollingEvent) {
        pollingEvent = window.setInterval(pollOnce, 200);
      }
    }

    // Stop the specified polling.
    function doStopPolling(id) {
      if (id in polledFileStates) {
        polledFileStates[id].toBeStopped = true;
      }
    }

    return {
      // Call this to start polling the specified file, assigning its content to the element(s)
      // selected by the selector. You must assign some unique id to the request.
      // If initFunc is provided, it is called the first time any content is assigned.
      startPolling: function(id, path, targetSelector, initFunc, predicate) {
        doStartPolling(id, path, targetSelector, initFunc, predicate, true);
      },

      // Call this to start tailing the specified file, appending its content to the element(s)
      // selected by the selector. You must assign some unique id to the request.
      // If initFunc is provided, it is called the first time any content is appended.
      startTailing: function(id, path, targetSelector, initFunc, predicate) {
        doStartPolling(id, path, targetSelector, initFunc, predicate, false);
      },

      // Stop the specified polling.
      stopPolling: doStopPolling,

      // Stop the specified tailing.
      stopTailing: doStopPolling
    }
  }
};

// We really only need one global one of each of these. So here they are.
pants.timerManager = pants.createTimerManager();
pants.poller = pants.createPoller();
