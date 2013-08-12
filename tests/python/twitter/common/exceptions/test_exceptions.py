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

import sys
import threading
from collections import namedtuple
from contextlib import contextmanager
from Queue import Queue

from twitter.common.exceptions import ExceptionalThread


class TestException(Exception):
  def __eq__(self, other):
    return (self.args == other.args and self.message == other.message)

class ExceptHookInfo(namedtuple('ExceptHookInfo', 'type value traceback')):
  def __eq__(self, other):
    return (self.type == other.type and self.value == other.value)


@contextmanager
def exception_handler():
  try:
    exception_queue = Queue()
    def queue_exceptions(type, value, traceback):
      exception_queue.put(ExceptHookInfo(type, value, traceback))
    sys.excepthook = queue_exceptions
    yield exception_queue
  finally:
    sys.excepthook = sys.__excepthook__


class TestExceptionalThread(ExceptionalThread):
  def __init__(self):
    super(TestExceptionalThread, self).__init__()
  def run(self):
    raise TestException('Something went wrong!')


class TestDefaultThread(threading.Thread):
  def run(self):
    raise TestException


def test_exception_caught():
  with exception_handler() as queue:
    thread = TestExceptionalThread()
    thread.start()
    thread.join()
    exception = queue.get()
    assert exception == ExceptHookInfo(TestException, TestException('Something went wrong!'), None)
  assert sys.excepthook == sys.__excepthook__


def test_exception_swallowed():
  with exception_handler() as queue:
    thread = TestDefaultThread()
    thread.start()
    thread.join()
    assert queue.empty()
  assert sys.excepthook == sys.__excepthook__
