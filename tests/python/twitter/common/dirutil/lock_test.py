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

__author__ = 'John Sirois'

import os
import signal
import time

from twitter.common.contextutil import temporary_file, temporary_dir
from twitter.common.dirutil import Lock
from twitter.common.lang import Compatibility

if Compatibility.PY3:
  import unittest
else:
  import unittest2 as unittest

class LockTest(unittest.TestCase):
  def test_unlocked(self):
    lock1 = Lock.unlocked()
    lock2 = Lock.unlocked()
    self.assertFalse(lock1.release())
    self.assertFalse(lock1.release())
    self.assertFalse(lock2.release())

  def test_acquire_nowait(self):
    with temporary_file() as fd:
      def throw(pid):
        self.fail('Did not expect to wait for the 1st lock, held by %d' % pid)
      lock = Lock.acquire(fd.name, onwait=throw)
      try:
        def onwait(pid):
          self.assertEquals(os.getpid(), pid, "This process should hold the lock.")
          return False
        self.assertFalse(Lock.acquire(fd.name, onwait=onwait))
      finally:
        self.assertTrue(lock.release())
        self.assertFalse(lock.release())

  def test_acquire_wait(self):
    with temporary_dir() as path:
      lockfile = os.path.join(path, 'lock')

      childpid = os.fork()
      if childpid == 0:
        lock = Lock.acquire(lockfile)
        try:
          while True:
            time.sleep(1)
        except KeyboardInterrupt:
          lock.release()

      else:
        def childup():
          if not os.path.exists(lockfile):
            return False
          else:
            with open(lockfile) as fd:
              pid = fd.read().strip()
              return pid and pid == str(childpid)

        while not childup():
          time.sleep(0.1)

        # We should be blocked by the forked child lock owner
        def onwait(pid):
          self.assertEquals(childpid, pid)
          return False
        self.assertFalse(Lock.acquire(lockfile, onwait=onwait))

        # We should unblock after we 'kill' the forked child owner
        os.kill(childpid, signal.SIGINT)
        lock = Lock.acquire(lockfile)
        try:
          self.assertTrue(lock)
        finally:
          self.assertTrue(lock.release())
