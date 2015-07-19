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

__author__ = 'John Sirois, Brian Wickman'

import os
import shutil
import tarfile
import tempfile
import time
import sys
import uuid
import zipfile

from contextlib import closing, contextmanager

from twitter.common.dirutil import safe_delete
from twitter.common.lang import Compatibility


@contextmanager
def environment_as(**kwargs):
  """Update the environment to the supplied values, for example:

  >>> with environment_as(PYTHONPATH='foo:bar:baz', PYTHON='/usr/bin/python2.7'):
  ...  subprocess.Popen(foo).wait()

  """
  new_environment = kwargs
  old_environment = {}

  def setenv(key, val):
    if val is not None:
      os.environ[key] = val
    else:
      if key in os.environ:
        del os.environ[key]

  for key, val in new_environment.items():
    old_environment[key] = os.environ.get(key)
    setenv(key, val)
  try:
    yield
  finally:
    for key, val in old_environment.items():
      setenv(key, val)


@contextmanager
def temporary_dir(root_dir=None, cleanup=True):
  """
    A with-context that creates a temporary directory.

    You may specify the following keyword args:

    :param str root_dir: The parent directory to create the temporary directory.
    :param bool cleanup: Whether or not to clean up the temporary directory.

    For example:

    >>> with temporary_dir() as td:
    ...   with open(os.path.join(td, 'my_file.txt')) as fp:
    ...     fp.write(junk)


  """
  path = tempfile.mkdtemp(dir=root_dir)
  try:
    yield path
  finally:
    if cleanup:
      shutil.rmtree(path, ignore_errors=True)


@contextmanager
def temporary_file_path(root_dir=None, cleanup=True):
  """
    A with-context that creates a temporary file and returns its path.

    You may specify the following keyword args:

    :param str root_dir: The parent directory to create the temporary file.
    :param bool cleanup: Whether or not to clean up the temporary file.
  """
  with temporary_file(root_dir, cleanup) as fd:
    fd.close()
    yield fd.name


@contextmanager
def temporary_file(root_dir=None, cleanup=True):
  """
    A with-context that creates a temporary file and returns a writeable file descriptor to it.

    You may specify the following keyword args:

    :param str root_dir: The parent directory to create the temporary file.
    :param bool cleanup: Whether or not to clean up the temporary file.

    >>> with temporary_file() as fp:
    ...  fp.write('woot')
    ...  fp.sync()
    ...  # pass fp on to something else

  """
  with tempfile.NamedTemporaryFile(dir=root_dir, delete=False) as fd:
    try:
      yield fd
    finally:
      if cleanup:
        safe_delete(fd.name)


@contextmanager
def safe_file(path, suffix=None, cleanup=True):
  """A with-context that copies a file, and copies the copy back to the original file on success.

  This is useful for doing work on a file but only changing its state on success.

    - suffix: Use this suffix to create the copy. Otherwise use a random string.
    - cleanup: Whether or not to clean up the copy.
  """
  safe_path = path + '.%s' % suffix or uuid.uuid4()
  if os.path.exists(path):
    shutil.copy(path, safe_path)
  try:
    yield safe_path
    if cleanup:
      shutil.move(safe_path, path)
    else:
      shutil.copy(safe_path, path)
  finally:
    if cleanup:
      safe_delete(safe_path)


@contextmanager
def pushd(directory):
  """
    A with-context that encapsulates pushd/popd.

    >>> with pushd('subdir/data'):
    ...  glob.glob("*json") # run code in subdir

  """
  cwd = os.getcwd()
  os.chdir(directory)
  try:
    yield directory
  finally:
    os.chdir(cwd)


@contextmanager
def mutable_sys():
  """
    A with-context that does backup/restore of sys.argv, sys.path and
    sys.stderr/stdout/stdin following execution.
  """
  SAVED_ATTRIBUTES = [
    'stdin', 'stdout', 'stderr',
    'argv', 'path', 'path_importer_cache', 'path_hooks',
    'modules', '__egginsert'
  ]

  _sys_backup = dict((key, getattr(sys, key)) for key in SAVED_ATTRIBUTES if hasattr(sys, key))
  _sys_delete = set(filter(lambda key: not hasattr(sys, key), SAVED_ATTRIBUTES))

  try:
    yield sys
  finally:
    for attribute in _sys_backup:
      setattr(sys, attribute, _sys_backup[attribute])
    for attribute in _sys_delete:
      if hasattr(sys, attribute):
        delattr(sys, attribute)


@contextmanager
def open_zip(path_or_file, *args, **kwargs):
  """
    A with-context for zip files.  Passes through positional and kwargs to zipfile.ZipFile.
  """
  try:
    zf = zipfile.ZipFile(path_or_file, *args, **kwargs)
  except zipfile.BadZipfile as bze:
    raise zipfile.BadZipfile("Bad Zipfile %s: %s" % (path_or_file, bze))
  try:
    yield zf
  finally:
    zf.close()


@contextmanager
def open_tar(path_or_file, *args, **kwargs):
  """
    A with-context for tar files.  Passes through positional and kwargs to tarfile.open.

    If path_or_file is a file, caller must close it separately.
  """
  (path, fileobj) = ((path_or_file, None) if isinstance(path_or_file, Compatibility.string)
                     else (None, path_or_file))
  with closing(tarfile.open(path, *args, fileobj=fileobj, **kwargs)) as tar:
    yield tar


class Timer(object):
  """Very basic with-context to time operations

  Example usage:
    >>> from twitter.common.contextutil import Timer
    >>> with Timer() as timer:
    ...   time.sleep(2)
    ...
    >>> timer.elapsed
    2.0020849704742432

  """

  def __init__(self, clock=time):
    self._clock = clock

  def __enter__(self):
    self.start = self._clock.time()
    self.finish = None
    return self

  @property
  def elapsed(self):
    if self.finish:
      return self.finish - self.start
    else:
      return self._clock.time() - self.start

  def __exit__(self, typ, val, traceback):
    self.finish = self._clock.time()
