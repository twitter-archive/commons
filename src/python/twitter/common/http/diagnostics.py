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

import pstats
import sys
import threading
import traceback

try:
  import cStringIO as StringIO
except ImportError:
  import StringIO

try:
  from twitter.common import app
  HAS_APP = True
except ImportError:
  HAS_APP = False

from .server import HttpServer, route


class DiagnosticsEndpoints(object):
  """
    Export the thread stacks of the running process.
  """
  UNHEALTHY = threading.Event()

  @classmethod
  def generate_stacks(cls):
    threads = dict((th.ident, th) for th in threading.enumerate())
    tb = []
    for thread_id, stack in sys._current_frames().items():
      tb.append("\n\n# Thread%s: %s (%s, %d)" % (
        ' (daemon)' if threads[thread_id].daemon else '',
        threads[thread_id].__class__.__name__, threads[thread_id].name, thread_id))
      for filename, lineno, name, line in traceback.extract_stack(stack):
        tb.append('  File: "%s", line %d, in %s' % (filename, lineno, name))
        if line:
          tb.append("    %s" % (line.strip()))
    return "\n".join(tb)

  @route("/threads")
  def handle_threads(self):
    HttpServer.set_content_type('text/plain; charset=iso-8859-1')
    return self.generate_stacks()

  @route("/profile")
  def handle_profile(self):
    HttpServer.set_content_type('text/plain; charset=iso-8859-1')
    if HAS_APP and app.profiler() is not None:
      output_stream = StringIO.StringIO()
      stats = pstats.Stats(app.profiler(), stream=output_stream)
      stats.sort_stats('time', 'name')
      stats.print_stats()
      return output_stream.getvalue()
    else:
      return 'Profiling is disabled'

  @route("/health")
  def handle_health(self):
    return 'UNHEALTHY' if self.UNHEALTHY.is_set() else 'OK'
