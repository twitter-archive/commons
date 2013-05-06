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

import platform
import pytest
import threading

from twitter.common.decorators.threads import __gettid
from twitter.common.decorators.threads import identify_thread


SUPPORTED_PLATFORMS = (
  ('Linux', 'i386'),
  ('Linux', 'x86_64'),
)

PLATFORM_SUPPORTED = (platform.system(), platform.machine()) in SUPPORTED_PLATFORMS


class TestThread(threading.Thread):
  def __init__(self):
    threading.Thread.__init__(self)
    self.start_event = threading.Event()
    self.stop_event = threading.Event()
    self.daemon = True
  @identify_thread
  def run(self):
    self.start_event.set()
    self.stop_event.wait()


class TestNonthreadObject(object):
  @identify_thread
  def __init__(self):
    pass


def test_identified_nonthread_object():
  obj = TestNonthreadObject()
  assert hasattr(obj, '__thread_id')
  assert isinstance(obj.__thread_id, int) or obj.__thread_id == 'UNKNOWN'


@pytest.mark.skipif("not PLATFORM_SUPPORTED")
def test_gettid_supported_platform():
  assert __gettid() != -1


@pytest.mark.skipif("not PLATFORM_SUPPORTED")
def test_identified_thread_supported_platform():
  thread = TestThread()
  thread.start()
  # Non-zero delay between when a thread is started & when it's running; hence, we need to gate this
  thread.start_event.wait()
  assert isinstance(thread.__thread_id, int)
  assert thread.__thread_id != -1
  thread.stop_event.set()


@pytest.mark.skipif("PLATFORM_SUPPORTED")
def test_gettid_unsupported_platform():
  assert __gettid() == -1


@pytest.mark.skipif("PLATFORM_SUPPORTED")
def test_identified_thread_unsupported_platform():
  thread = TestThread()
  thread.start()
  # Non-zero delay between when a thread is started & when it's running; hence, we need to gate this
  thread.start_event.wait()
  assert thread.__thread_id == 'UNKNOWN'
  thread.stop_event.set()
