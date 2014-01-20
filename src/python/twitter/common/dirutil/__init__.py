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

import atexit
from collections import defaultdict
import errno
import os
import shutil
import stat
import tempfile
import threading

try:
  import fcntl
  HAS_FCNTL = True
except ImportError:
  HAS_FCNTL = False


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


def safe_mkdir_for(path, clean=False):
  """
    Ensure that the parent directory for a file is present.  If it's not there, create it.
    If it is, no-op. If clean is True, ensure the directory is empty.
  """
  safe_mkdir(os.path.dirname(path), clean)


_MKDTEMP_CLEANER = None
_MKDTEMP_DIRS = defaultdict(set)
_MKDTEMP_LOCK = threading.RLock()


def _mkdtemp_atexit_cleaner():
  for td in _MKDTEMP_DIRS.pop(os.getpid(), []):
    safe_rmtree(td)


def _mkdtemp_unregister_cleaner():
  global _MKDTEMP_CLEANER
  _MKDTEMP_CLEANER = None


def _mkdtemp_register_cleaner(cleaner):
  global _MKDTEMP_CLEANER
  if not cleaner:
    return
  assert callable(cleaner)
  if _MKDTEMP_CLEANER is None:
    atexit.register(cleaner)
    _MKDTEMP_CLEANER = cleaner


def safe_mkdtemp(cleaner=_mkdtemp_atexit_cleaner, **kw):
  """
    Given the parameters to standard tempfile.mkdtemp, create a temporary directory
    that is cleaned up on process exit.
  """
  # proper lock sanitation on fork [issue 6721] would be desirable here.
  with _MKDTEMP_LOCK:
    return register_rmtree(tempfile.mkdtemp(**kw), cleaner=cleaner)


def register_rmtree(directory, cleaner=_mkdtemp_atexit_cleaner):
  """
    Register an existing directory to be cleaned up at process exit.
  """
  with _MKDTEMP_LOCK:
    _mkdtemp_register_cleaner(cleaner)
    _MKDTEMP_DIRS[os.getpid()].add(directory)
  return directory


def safe_rmtree(directory):
  """
    Delete a directory if it's present. If it's not present, no-op.
  """
  if os.path.exists(directory):
    shutil.rmtree(directory, True)


def safe_open(filename, *args, **kwargs):
  """
    Open a file safely (ensuring that the directory components leading up to it
    have been created first.)
  """
  safe_mkdir(os.path.dirname(filename))
  return open(filename, *args, **kwargs)


def safe_delete(filename):
  """
    Delete a file safely. If it's not present, no-op.
  """
  try:
    os.unlink(filename)
  except OSError as e:
    if e.errno != errno.ENOENT:
      raise


def _calculate_bsize(stat):
  """
    Calculate the actual disk allocation for a file.  This works at least on OS X and
    Linux, but may not work on other systems with 1024-byte blocks (apparently HP-UX?)

    From pubs.opengroup.org:

    The unit for the st_blocks member of the stat structure is not defined
    within IEEE Std 1003.1-2001 / POSIX.1-2008.  In some implementations it
    is 512 bytes.  It may differ on a file system basis.  There is no
    correlation between values of the st_blocks and st_blksize, and the
    f_bsize (from <sys/statvfs.h>) structure members.
  """
  return 512 * stat.st_blocks


def _calculate_size(stat):
  return min(_calculate_bsize(stat), stat.st_size)


def _size_base(path, on_error=None, calculate_usage=_calculate_size):
  assert on_error is None or callable(on_error), 'on_error must be a callable!'
  assert callable(calculate_usage)
  try:
    stat_result = os.lstat(path)
    stat_mode = stat_result.st_mode
    if stat.S_ISREG(stat_mode):
      return calculate_usage(stat_result)
    elif stat.S_ISDIR(stat_mode):
      return stat_result.st_size
    elif stat.S_ISLNK(stat_mode):
      return len(os.readlink(path))
    else:
      return 0
  except OSError as e:
    if on_error:
      on_error(path, e)
    return 0


def safe_size(path, on_error=None):
  """
    Safely get the size of a file:
      - the size of symlinks are treated as the length of the symlink
      - the size of regular files / directories are treated as such
      - the size of all other files are zero (sockets, dev, etc.)
      - the size of sparse files are estimated based upon st_blocks * st_blksize

    A callable on_error may be supplied that will be called with
    on_error(path, exception) if an OSError is raised by the stat (e.g.
    permission denied or file does not exist.)  In this case, safe_size will
    return with zero.
  """
  return _size_base(path, on_error=on_error, calculate_usage=_calculate_size)


def safe_bsize(path):
  """
    Safely return the space a file consumes on disk. Returns 0 if an OSError is
    raised.
  """
  return _size_base(path, calculate_usage=_calculate_bsize)


def safe_mtime(filename):
  """
    Safely return the mtime of a file. Returns 0 if an OSError is raised.
  """
  try:
    return os.path.getmtime(filename)
  except OSError:
    return 0


def du(directory):
  size = 0
  for root, _, files in os.walk(directory):
    size += sum(safe_bsize(os.path.join(root, filename)) for filename in files)
  return size


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


def chmod_plus_w(path):
  """
    Equivalent of unix `chmod +w path`
  """
  path_mode = os.stat(path).st_mode
  path_mode &= int('777', 8)
  path_mode |= stat.S_IWRITE
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
  # TODO(wickman) We should probably adopt the lockfile project here as has
  # a platform-independent file locking implementation.
  if not HAS_FCNTL:
    raise RuntimeError('Interpreter does not support fcntl!')

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


def unlock_file(fp, close=False):
  """
    Unlock a file pointer.

    If close=True the file pointer will be closed after unlocking.

    Always returns True.
  """
  if not HAS_FCNTL:
    raise RuntimeError('Interpreter does not support fcntl!')

  try:
    fcntl.flock(fp, fcntl.LOCK_UN)
  finally:
    if close:
      fp.close()
  return True


from twitter.common.dirutil.lock import Lock
from twitter.common.dirutil.tail import tail_f
from twitter.common.dirutil.fileset import Fileset

__all__ = (
  'chmod_plus_x',
  'du',
  'lock_file',
  'safe_bsize',
  'safe_delete',
  'safe_mkdir',
  'safe_mtime',
  'safe_open',
  'safe_size',
  'tail_f',
  'unlock_file',
  'Fileset',
  'Lock',

  # @visible for testing
  '_mkdtemp_atexit_cleaner',
  '_mkdtemp_register_cleaner',
  '_mkdtemp_unregister_cleaner',
  '_MKDTEMP_DIRS',
)
