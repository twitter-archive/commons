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

from . import lock_file, touch, unlock_file


class Lock(object):
  """A co-operative inter-process file lock."""

  @staticmethod
  def unlocked():
    """Creates a Lock that is already released."""
    return Lock(None)

  @staticmethod
  def acquire(path, onwait=None):
    """Attempts to lock the given path which need not exist ahead of time.

    By default acquire blocks as long as needed for the lock to be released if already held.

    If an onwait function is supplied, it will be passed the lock owner's pid when the lock cannot
    be acquired immediately.  In this case the onwait function should return True if it wishes to
    block on acquisition of the Lock.  Otherwise None will be returned as a signal to acquire's
    caller that the lock failed.
    """

    touch(path)
    lock_fd = lock_file(path, blocking=False)
    if not lock_fd:
      blocking = True
      with open(path, 'r') as fd:
        pid = int(fd.read().strip())
        if onwait:
          blocking = onwait(pid)
      if not blocking:
        return None
      lock_fd = lock_file(path, blocking=blocking)

    lock_fd.truncate(0)
    lock_fd.write('%d\n' % os.getpid())
    lock_fd.flush()
    return Lock(lock_fd)

  def __init__(self, lock_fd):
    self._lock_fd = lock_fd

  def is_unlocked(self):
    """Checks whether or not this lock object is currently actively holding a lock."""
    return self._lock_fd is None

  def release(self):
    """Releases this lock if held and returns True; otherwise, returns False to indicate the lock
    was already released.
    """

    if self._lock_fd:
      unlock_file(self._lock_fd, close=True)
      self._lock_fd = None
      return True
    else:
      return False
