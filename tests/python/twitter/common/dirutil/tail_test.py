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

from __future__ import print_function

import os
import copy
import threading
import tempfile
import time
import sys
from twitter.common.lang import Compatibility
from twitter.common.dirutil import tail_f

if Compatibility.PY3:
  import unittest
else:
  import unittest2 as unittest

class TestClock(object):
  def __init__(self):
    self._cond = threading.Condition()
    self.reset()

  def time(self):
    return self._ticks

  def reset(self):
    self._ticks = 0

  def tick(self, ticks=1):
    for k in range(ticks):
      with self._cond:
        self._cond.notifyAll()

  def sleep(self, amount):
    with self._cond:
      for k in range(amount):
        self._cond.wait()
        self._ticks += 1

class TailThread(threading.Thread):
  def __init__(self, filename):
    self._filename = filename
    self._clock = TestClock()
    self._lines = []
    self._terminate = threading.Event()
    threading.Thread.__init__(self)

  def run(self):
    for line in tail_f(self._filename, clock=self._clock):
      self._lines.append(line)
      if self._terminate.is_set():
        break

  def clear(self):
    time.sleep(0.10)  # yield the thread.
    rc = copy.copy(self._lines)
    self._lines = []
    return rc

  def clock(self):
    return self._clock

  def terminate(self):
    self._terminate.set()

class TestTail(unittest.TestCase):
  @classmethod
  def write_to_fp(cls, msg):
    cls._fp.write(msg)
    cls._fp.flush()

  @classmethod
  def reset_fp(cls):
    filename = cls._fp.name
    cls._fp.close()
    os.unlink(filename)
    cls._fp = open(filename, 'w')

  @classmethod
  def setUpClass(cls):
    cls._fp = open(tempfile.mktemp(), 'w')
    cls._thread = TailThread(cls._fp.name)
    cls._thread.start()

  @classmethod
  def tearDownClass(cls):
    cls._thread.terminate()
    cls.write_to_fp('whee!')
    cls._thread.clock().tick()
    cls._thread.join()
    os.unlink(cls._fp.name)
    cls._fp.close()

  def test_simple_tail(self):
    self.write_to_fp('hello')
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello']
    self.write_to_fp('hello 2')
    assert self._thread.clear() == []
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello 2']

  def test_tail_through_reset(self):
    self._thread.clock().reset()
    assert self._thread.clock().time() == 0
    self.write_to_fp('hello')
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello']
    self.reset_fp()
    self.write_to_fp('hello 2')
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello 2']

  def test_tail_through_truncation(self):
    self.write_to_fp('hello')
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello']

    self.write_to_fp('hello 2')
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello 2']

    self._fp.truncate(0)
    self.write_to_fp('hello 3')
    self._thread.clock().tick()
    assert self._thread.clear() == ['hello 3']
