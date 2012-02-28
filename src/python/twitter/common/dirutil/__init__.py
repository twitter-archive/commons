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

import fcntl
import os
import shutil
import stat
import errno

from twitter.common.dirutil.tail import tail_f
from twitter.common.dirutil.du import du

def safe_mkdir(directory, clean=False):
  """
    Ensure a directory is present.  If it's not there, create it.  If it is,
    no-op. If clean is True, ensure the directory is empty.
  """
  if clean:
    safe_rmtree(directory)
  try:
    os.makedirs(directory)
  except OSError as e:
    if e.errno != errno.EEXIST:
      raise


def safe_rmtree(directory):
  """
    Delete a directory if its present. If its not present its a no-op.
  """
  if os.path.exists(directory):
    shutil.rmtree(directory, True)


def safe_open(filename, *args, **kwargs):
  """
    Open a file safely (assuring that the directory components leading up to it
    have been created first.)
  """
  safe_mkdir(os.path.dirname(filename))
  return open(filename, *args, **kwargs)


def chmod_plus_x(path):
  """
    Equivalent of unix `chmod a+x path`
  """
  path_mode = os.stat(path).st_mode
  path_mode &= int('777', 8)
  if path_mode & stat.S_IRUSR:
    path_mode |= stat.S_IXUSR
  if path_mode & stat.S_IRGRP:
    path_mode |= stat.S_IXGRP
  if path_mode & stat.S_IROTH:
    path_mode |= stat.S_IXOTH
  os.chmod(path, path_mode)


def touch(file, times=None):
  """
    Equivalent of unix `touch path`.

    :file The file to touch.
    :times Either a tuple of (atime, mtime) or else a single time to use for both.  If not
           specified both atime and mtime are updated to the current time.
  """
  if times:
    if len(times) > 2:
      raise ValueError('times must either be a tuple of (atime, mtime) or else a single time value '
                       'to use for both.')

    if len(times) == 1:
      times = (times, times)

  with safe_open(file, 'a'):
    os.utime(file, times)


def lock_file(filename, mode='r+', blocking=False):
  """
    Lock a file (exclusively.)
    Requires that the file mode be a writable mode.

    If blocking=True, return once the lock is held.
    If blocking=False, return the file pointer if the lock succeeded, None if not.

    Returns:
      None if no file exists or could not access the file
      False if could not acquire the lock
      file object if the lock was acquired
  """

  try:
    fp = open(filename, mode)
  except IOError:
    return None

  try:
    fcntl.flock(fp, fcntl.LOCK_EX | fcntl.LOCK_NB if not blocking else fcntl.LOCK_EX)
  except IOError as e:
    if e.errno in (errno.EACCES, errno.EAGAIN):
      fp.close()
      return False

  return fp


def unlock_file(fp):
  """
    Unlock a file pointer.

    Always returns True.
  """
  fcntl.flock(fp, fcntl.LOCK_UN)
  return True


__all__ = [
  'chmod_plus_x',
  'du',
  'lock_file',
  'safe_mkdir',
  'safe_open',
  'tail_f',
  'unlock_file',
]
