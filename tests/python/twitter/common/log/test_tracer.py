# ==================================================================================================
# Copyright 2012 Twitter, Inc.
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

from twitter.common.lang import Compatibility
from twitter.common.log.tracer import Tracer, Trace
from twitter.common.testing.clock import ThreadedClock


def test_tracing_timed():
  sio = Compatibility.StringIO()
  clock = ThreadedClock()
  final_trace = []

  class PrintTraceInterceptor(Tracer):
    def print_trace(self, *args, **kw):
      final_trace.append(self._local.parent)

  tracer = PrintTraceInterceptor(output=sio, clock=clock, predicate=lambda v: False)
  assert not hasattr(tracer._local, 'parent')

  with tracer.timed('hello'):
    clock.tick(1.0)
    with tracer.timed('world 1'):
      clock.tick(1.0)
    with tracer.timed('world 2'):
      clock.tick(1.0)

  assert len(final_trace) == 1
  final_trace = final_trace[0]
  assert final_trace._start == 0
  assert final_trace._stop == 3
  assert final_trace.duration() == 3
  assert final_trace.msg == 'hello'
  assert len(final_trace.children) == 2
  child = final_trace.children[0]
  assert child._start == 1
  assert child._stop == 2
  assert child.parent is final_trace
  assert child.msg == 'world 1'
  child = final_trace.children[1]
  assert child._start == 2
  assert child._stop == 3
  assert child.parent is final_trace
  assert child.msg == 'world 2'

  # should not log if verbosity low
  assert sio.getvalue() == ''


def test_tracing_filter():
  sio = Compatibility.StringIO()
  tracer = Tracer(output=sio)
  tracer.log('hello world')
  assert sio.getvalue() == 'hello world\n'

  sio = Compatibility.StringIO()
  tracer = Tracer(output=sio, predicate=lambda v: v >= 1)
  tracer.log('hello world')
  assert sio.getvalue() == ''
  tracer.log('hello world', V=1)
  assert sio.getvalue() == 'hello world\n'
  tracer.log('ehrmagherd', V=2)
  assert sio.getvalue() == 'hello world\nehrmagherd\n'

  sio = Compatibility.StringIO()
  tracer = Tracer(output=sio, predicate=lambda v: (v % 2 == 0))
  tracer.log('hello world', V=0)
  assert sio.getvalue() == 'hello world\n'
  tracer.log('morf gorf', V=1)
  assert sio.getvalue() == 'hello world\n'
  tracer.log('ehrmagherd', V=2)
  assert sio.getvalue() == 'hello world\nehrmagherd\n'
