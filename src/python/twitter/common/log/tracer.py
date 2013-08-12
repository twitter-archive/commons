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

from contextlib import contextmanager
import os
import sys
import threading
import time


__all__ = ('Tracer',)


class Trace(object):
  __slots__ = ('msg', 'verbosity', 'parent', 'children', '_clock', '_start', '_stop')
  def __init__(self, msg, parent=None, verbosity=1, clock=time):
    self.msg = msg
    self.verbosity = verbosity
    self.parent = parent
    if parent is not None:
      parent.children.append(self)
    self.children = []
    self._clock = clock
    self._start = self._clock.time()
    self._stop = None

  def stop(self):
    self._stop = self._clock.time()

  def duration(self):
    assert self._stop is not None
    return self._stop - self._start


class Tracer(object):
  """
    A multi-threaded tracer.
  """
  @classmethod
  def env_filter(cls, env_variable):
    def predicate(verbosity):
      try:
        env_verbosity = int(os.environ.get(env_variable, -1))
      except ValueError:
        env_verbosity = -1
      return verbosity <= env_verbosity
    return predicate

  def __init__(self, predicate=None, output=sys.stderr, clock=time):
    """
      If predicate specified, it should take a "verbosity" integer and determine whether
      or not to log, e.g.

        def predicate(verbosity):
          try:
            return verbosity < int(os.environ.get('APP_VERBOSITY', 0))
          except ValueError:
            return False

      output defaults to sys.stderr, but can take any file-like object.
    """
    self._predicate = predicate or (lambda verbosity: True)
    self._length = None
    self._output = output
    self._isatty = getattr(output, 'isatty', False) and output.isatty()
    self._lock = threading.RLock()
    self._local = threading.local()
    self._clock = clock

  def should_log(self, V):
    return self._predicate(V)

  def log(self, msg, V=0, end='\n'):
    if not self.should_log(V):
      return
    if not self._isatty and end == '\r':
      # force newlines if we're not a tty
      end = '\n'
    trailing_whitespace = ''
    with self._lock:
      if self._length and self._length > len(msg):
        trailing_whitespace = ' ' * (self._length - len(msg))
      self._output.write(msg + trailing_whitespace + end)
      self._output.flush()
      self._length = len(msg) if end == '\r' else 0

  def print_trace_snippet(self):
    parent = self._local.parent
    parent_verbosity = parent.verbosity
    if not self.should_log(parent_verbosity):
      return
    traces = []
    while parent:
      if self.should_log(parent.verbosity):
        traces.append(parent.msg)
      parent = parent.parent
    self.log(' :: '.join(reversed(traces)), V=parent_verbosity, end='\r')

  def print_trace(self, indent=0, node=None):
    node = node or self._local.parent
    with self._lock:
      self.log(' ' * indent + ('%s: %.1fms' % (node.msg, 1000.0 * node.duration())),
               V=node.verbosity)
      for child in node.children:
        self.print_trace(indent=indent + 2, node=child)

  @contextmanager
  def timed(self, msg, V=0):
    if getattr(self._local, 'parent', None) is None:
      self._local.parent = Trace(msg, verbosity=V, clock=self._clock)
    else:
      parent = self._local.parent
      self._local.parent = Trace(msg, parent=parent, verbosity=V, clock=self._clock)
    self.print_trace_snippet()
    yield
    self._local.parent.stop()
    if self._local.parent.parent is not None:
      self._local.parent = self._local.parent.parent
    else:
      self.print_trace()
      self._local.parent = None


def main(args):
  import random

  tracer = Tracer(output=open(args[0], 'w')) if len(args) > 0 else Tracer()

  def process(name):
    with tracer.timed(name):
      with tracer.timed('acquiring'):
        with tracer.timed('downloading'):
          time.sleep(3 * random.random())
          if random.random() > 0.66:
            tracer.log('%s failed downloading!' % name)
            return
        with tracer.timed('unpacking'):
          time.sleep(1 * random.random())
      with tracer.timed('configuring'):
        time.sleep(0.5 * random.random())
      with tracer.timed('building'):
        time.sleep(5.0 * random.random())
        if random.random() > 0.66:
          tracer.log('%s failed building!' % name)
          return
      with tracer.timed('installing'):
        time.sleep(2.0 * random.random())

  workers = [threading.Thread(target=process, args=('worker %d' % k,)) for k in range(5)]
  for worker in workers:
    worker.start()
  for worker in workers:
    worker.join()


if __name__ == '__main__':
  main(sys.argv[1:])
