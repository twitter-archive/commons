# ==================================================================================================
# Copyright 2011 Twitter, Inc.
# --------------------------------------------------------------------------------------------------
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this work except in compliance with the License.
# You may obtain a copy of the License in the LICENSE file, or at:
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==================================================================================================

# NOTE, this is called "varz" instead of "vars" because "vars" is a reserved
# word in Python.  All characters appearing in this work are fictitious.
# Any resemblance to real persons, living or dead, is purely coincidental.

from functools import wraps
import os
import re
import sys
import time

from twitter.common import app, options
from twitter.common.http import HttpServer, Plugin
from twitter.common.http.server import request
from twitter.common.metrics import (
  AtomicGauge,
  Label,
  LambdaGauge,
  MetricSampler,
  MutatorGauge,
  Observable,
  RootMetrics,
)
from twitter.common.quantity import Amount, Time

from .http import RootServer


try:
  from twitter.common.python.pex import PEX
  HAS_PEX = True
except ImportError:
  HAS_PEX = False


def set_bool(option, opt_str, value, parser):
  setattr(parser.values, option.dest, not opt_str.startswith('--no'))



class VarsSubsystem(app.Module):
  """
    Exports a /vars endpoint on the root http server bound to twitter.common.metrics.RootMetrics.
  """
  OPTIONS = {
    'sampling_delay':
      options.Option('--vars-sampling-delay-ms',
          default=1000,
          type='int',
          metavar='MILLISECONDS',
          dest='twitter_common_metrics_vars_sampling_delay_ms',
          help='How long between taking samples of the vars subsystem.'),

    'trace_endpoints':
      options.Option('--vars-trace-endpoints', '--no-vars-trace-endpoints',
          default=True,
          action='callback',
          callback=set_bool,
          dest='twitter_common_app_modules_varz_trace_endpoints',
          help='Trace all registered http endpoints in this application.'),

    'trace_namespace':
      options.Option('--trace-namespace',
          default='http',
          dest='twitter_common_app_modules_varz_trace_namespace',
          help='The prefix for http request metrics.'),

    'stats_filter':
      options.Option('--vars-stats-filter',
          default=[],
          action='append',
          dest='twitter_common_app_modules_varz_stats_filter',
          help='Full-match regexes to filter metrics on-demand when requested '
               'with `filtered=1`.')
  }

  def __init__(self):
    app.Module.__init__(self, __name__, description='Vars subsystem',
                        dependencies='twitter.common.app.modules.http')

  def setup_function(self):
    options = app.get_options()
    rs = RootServer()
    if rs:
      varz = VarsEndpoint(period = Amount(
        options.twitter_common_metrics_vars_sampling_delay_ms, Time.MILLISECONDS),
        stats_filter = self.compile_stats_filters(options.twitter_common_app_modules_varz_stats_filter)
      )
      rs.mount_routes(varz)
      register_diagnostics()
      register_build_properties()
      if options.twitter_common_app_modules_varz_trace_endpoints:
        plugin = EndpointTracePlugin()
        rs.install(plugin)
        RootMetrics().register_observable(
            options.twitter_common_app_modules_varz_trace_namespace,
            plugin)

  def compile_stats_filters(self, regexes_list):
    if len(regexes_list) > 0:
      # safeguard against partial matches
      full_regexes = ['^' + regex + '$' for regex in regexes_list]
      return re.compile('(' + ")|(".join(full_regexes) + ')')
    else:
      return None


class VarsEndpoint(object):
  """
    Wrap a MetricSampler to export the /vars endpoint for applications that register
    exported variables.
  """

  def __init__(self, period=None, stats_filter=None):
    self._metrics = RootMetrics()
    self._stats_filter = stats_filter
    if period is not None:
      self._monitor = MetricSampler(self._metrics, period)
    else:
      self._monitor = MetricSampler(self._metrics)
    self._monitor.start()

  @HttpServer.route("/vars")
  @HttpServer.route("/vars/:var")
  def handle_vars(self, var=None):
    HttpServer.set_content_type('text/plain; charset=iso-8859-1')
    filtered = self._parse_filtered_arg()
    samples = self._monitor.sample()

    if var is None and filtered and self._stats_filter:
      return '\n'.join(
        '%s %s' % (key, val) for key, val in sorted(samples.items())
                  if not self._stats_filter.match(key))
    elif var is None:
      return '\n'.join(
        '%s %s' % (key, val) for key, val in sorted(samples.items()))
    else:
      if var in samples:
        return samples[var]
      else:
        HttpServer.abort(404, 'Unknown exported variable')

  @HttpServer.route("/vars.json")
  def handle_vars_json(self, var=None, value=None):
    filtered = self._parse_filtered_arg()
    sample = self._monitor.sample()
    if filtered and self._stats_filter:
      return dict((key, val) for key, val in sample.items() if not self._stats_filter.match(key))
    else:
      return sample

  def shutdown(self):
    self._monitor.shutdown()
    self._monitor.join()

  def _parse_filtered_arg(self):
    return request.GET.get('filtered', '') in ('true', '1')


class StatusStats(Observable):
  def __init__(self):
    self._count = AtomicGauge('count')
    self._ns = AtomicGauge('total_ns')
    self.metrics.register(self._count)
    self.metrics.register(self._ns)

  def increment(self, ns):
    self._count.increment()
    self._ns.add(ns)


class EndpointTracePlugin(Observable, Plugin):
  def setup(self, app):
    self._stats = dict((k, StatusStats()) for k in (1, 2, 3, 4, 5))
    for code_prefix, observable in self._stats.items():
      self.metrics.register_observable('%dxx' % code_prefix, observable)

  def apply(self, callback, route):
    @wraps(callback)
    def wrapped_callback(*args, **kw):
      start = time.time()
      body = callback(*args, **kw)
      ns = int((time.time() - start) * 1e9)
      observable = self._stats.get(HttpServer.response.status_code / 100)
      if observable:
        observable.increment(ns)
      return body
    return wrapped_callback


def register_diagnostics():
  rm = RootMetrics().scope('sys')
  now = time.time()
  rm.register(LambdaGauge('uptime', lambda: time.time() - now))
  rm.register(Label('argv', repr(sys.argv)))
  rm.register(Label('path', repr(sys.path)))
  rm.register(Label('version', sys.version))
  rm.register(Label('platform', sys.platform))
  rm.register(Label('executable', sys.executable))
  rm.register(Label('prefix', sys.prefix))
  rm.register(Label('exec_prefix', sys.exec_prefix))
  rm.register(Label('uname', ' '.join(os.uname())))


def register_build_properties():
  if not HAS_PEX:
    return
  rm = RootMetrics().scope('build')
  try:
    build_properties = PEX().info.build_properties
  except PEX.NotFound:
    return
  for key, value in build_properties.items():
    rm.register(Label(str(key), str(value)))
