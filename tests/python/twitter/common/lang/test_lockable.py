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

import threading
from twitter.common.lang import Lockable

def test_basic_mutual_exclusion():
  class Foo(Lockable):
    def __init__(self):
      self.counter = 0
      self.start_event = threading.Event()
      self.finish_event = threading.Event()
      Lockable.__init__(self)

    @Lockable.sync
    def pooping(self):
      self.counter += 1
      self.start_event.set()
      self.finish_event.wait()

  f = Foo()

  class FooSetter(threading.Thread):
    def run(self):
      f.pooping()

  fs1 = FooSetter()
  fs2 = FooSetter()
  fs1.start()
  fs2.start()

  # yield threads
  f.start_event.wait(timeout=1.0)
  assert f.start_event.is_set()

  # assert mutual exclusion
  assert f.counter == 1

  # unblock ==> other wakes up
  f.start_event.clear()
  f.finish_event.set()

  f.start_event.wait(timeout=1.0)
  assert f.start_event.is_set()
  assert f.counter == 2
