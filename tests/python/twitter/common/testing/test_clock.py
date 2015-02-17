# ==================================================================================================
# Copyright 2015 Twitter, Inc.
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

from twitter.common.testing.clock import ThreadedClock

import pytest


@pytest.mark.parametrize('num_threads', (1, 10))
def test_with_events(num_threads):
  event = threading.Event()

  hits = []
  hits_before, hits_after = 0, 0

  clock = ThreadedClock(0)

  def hit_me():
    clock.sleep(0.1)
    hits.append(True)

  threads = []
  for _ in range(num_threads):
    th = threading.Thread(target=hit_me)
    th.daemon = True
    th.start()
    threads.append(th)

  clock.converge(threads=threads)
  for th in threads:
    clock.assert_waiting(th, 0.1)

  clock.tick(0.05)
  clock.converge(threads=threads)
  hits_before += len(hits)

  with pytest.raises(AssertionError):
    clock.assert_waiting(threads[0], 234)

  clock.tick(0.05)
  clock.converge(threads=threads)
  hits_after += len(hits)

  for th in threads:
    clock.assert_not_waiting(th)
    with pytest.raises(AssertionError):
      clock.assert_waiting(th, 0.1)

  assert hits_before == 0
  assert hits_after == num_threads


def test_not_converged():
  clock1 = ThreadedClock(0)
  clock2 = ThreadedClock(0)

  def run():
    clock1.sleep(1)
    clock2.sleep(1)

  th = threading.Thread(target=run)
  th.daemon = True
  th.start()

  assert clock1.converge(threads=[th])
  clock1.assert_waiting(th, 1)
  assert clock2.converge(threads=[th], timeout=0.1) is False
  clock2.assert_not_waiting(th)

  clock1.tick(1)
  clock2.tick(2)
  clock1.converge(threads=[th])
  clock2.converge(threads=[th])
  clock1.assert_not_waiting(th)
  clock2.assert_not_waiting(th)


def test_sleep_0():
  clock = ThreadedClock(0)
  event = threading.Event()

  def run():
    clock.sleep(0)
    event.set()

  th = threading.Thread(target=run)
  th.daemon = True
  th.start()

  assert clock.converge(threads=[th])
  assert event.is_set()


def test_sleep_negative():
  with pytest.raises(IOError):
    ThreadedClock(0).sleep(-1)
