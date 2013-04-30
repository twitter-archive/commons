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

from twitter.common import app, options
from twitter.common.http import HttpServer
from twitter.common.quantity import Amount, Time
from twitter.common.metrics import (
  RootMetrics,
  MetricSampler,
  Label,
  LambdaGauge
)

from twitter.common.app.modules.http import RootServer

try:
  from twitter.common.python.pex import PEX
  HAS_PEX=True
except ImportError:
  HAS_PEX=False


class VarsSubsystem(app.Module):
  """
    Exports a /vars endpoint on the root http server bound to twitter.common.metrics.RootMetrics.
  """
  OPTIONS = {
    'sampling_delay':
      options.Option('--vars_sampling_delay_ms',
          default=1000,
          type='int',
          metavar='MILLISECONDS',
          dest='twitter_common_metrics_vars_sampling_delay_ms',
          help='How long between taking samples of the vars subsystem.')
  }

  def __init__(self):
    app.Module.__init__(self, __name__, description="Vars subsystem",
                        dependencies='twitter.common.app.modules.http')

  def setup_function(self):
    options = app.get_options()
    rs = RootServer()
    if rs:
      varz = VarsEndpoint(period = Amount(
        options.twitter_common_metrics_vars_sampling_delay_ms, Time.MILLISECONDS))
      rs.mount_routes(varz)
      register_diagnostics()
      register_build_properties()


class VarsEndpoint(object):
  """
    Wrap a MetricSampler to export the /vars endpoint for applications that register
    exported variables.
  """

  def __init__(self, period=None):
    self._metrics = RootMetrics()
    if period is not None:
      self._monitor = MetricSampler(self._metrics, period)
    else:
      self._monitor = MetricSampler(self._metrics)
    self._monitor.start()

  @HttpServer.route("/vars")
  @HttpServer.route("/vars/:var")
  def handle_vars(self, var=None):
    HttpServer.set_content_type('text/plain; charset=iso-8859-1')
    samples = self._monitor.sample()

    if var is None:
      return '\n'.join(
        '%s %s' % (key, val) for key, val in sorted(samples.items()))
    else:
      if var in samples:
        return samples[var]
      else:
        HttpServer.abort(404, 'Unknown exported variable')

  @HttpServer.route("/vars.json")
  def handle_vars_json(self, var=None, value=None):
    return self._monitor.sample()

  def shutdown(self):
    self._monitor.shutdown()
    self._monitor.join()


def register_diagnostics():
  import os, sys, time
  rm = RootMetrics().scope('sys')
  now = time.time()
  rm.register(LambdaGauge('uptime', lambda: time.time() - now))
  rm.register(Label('argv', repr(sys.argv)))
  rm.register(Label('path', repr(sys.path)))
  rm.register(LambdaGauge('modules', lambda: ', '.join(sys.modules.keys())))
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
