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

import errno
import os
import shutil
import tarfile
import tempfile
import sys
import zipfile

from contextlib import closing, contextmanager


@contextmanager
def environment_as(**kwargs):
  """
    Update the environment to the supplied values, for example:

    with environment_as(PYTHONPATH = 'foo:bar:baz',
                        PYTHON = '/usr/bin/python2.6'):
      subprocess.Popen(foo).wait()
  """
  new_environment = kwargs
  old_environment = {}

  def setenv(key, val):
    if val is not None:
      os.putenv(key, val)
    else:
      os.unsetenv(key)

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
      root_dir [path]: The parent directory to create the temporary directory.
      cleanup [True/False]: Whether or not to clean up the temporary directory.

    Important note: If you fork inside the context, make sure only one tine
    performs cleanup (e.g., by calling os._exit() in the child).
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
      root_dir [path]: The parent directory to create the temporary file.
      cleanup [True/False]: Whether or not to clean up the temporary file.

    Important note: If you fork inside the context, make sure only one tine
    performs cleanup (e.g., by calling os._exit() in the child).
  """
  fh, path = tempfile.mkstemp(dir=root_dir)
  os.close(fh)
  try:
    yield path
  finally:
    if cleanup:
      try:
        os.unlink(path)
      except OSError, e:
        if e.errno == errno.ENOENT:
          pass
        else:
          raise e

@contextmanager
def temporary_file(root_dir=None, cleanup=True):
  """
    A with-context that creates a temporary file and returns a writeable file descriptor to it.

    You may specify the following keyword args:
      root_dir [path]: The parent directory to create the temporary file.
      cleanup [True/False]: Whether or not to clean up the temporary file.

    Important note: If you fork inside the context, make sure only one tine
    performs cleanup (e.g., by calling os._exit() in the child).
  """
  # argh, I would love to use os.fdopen here but then fp.name == '<fdopen>'
  # and that's unacceptable behavior for most cases where I want to use temporary_file
  fh, path = tempfile.mkstemp(dir=root_dir)
  os.close(fh)
  # Note that there's a race condition here. Another process could open the file at this point.
  # This is potentially a security hole. TODO: Why not just yield fh here?
  fd = open(path, 'w+')
  try:
    yield fd
  finally:
    if not fd.closed:
      fd.close()
    if cleanup:
      os.unlink(path)


@contextmanager
def pushd(directory):
  """
    A with-context that encapsulates pushd/popd.
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
  with closing(zipfile.ZipFile(path_or_file, *args, **kwargs)) as zip:
    yield zip


@contextmanager
def open_tar(path_or_file, *args, **kwargs):
  """
    A with-context for tar files.  Passes through positional and kwargs to tarfile.open.

    If path_or_file is a file, caller must close it separately.
  """
  (path, fileobj) = (path_or_file, None) if isinstance(path_or_file, basestring) else (None, path_or_file)
  with closing(tarfile.open(path, *args, fileobj=fileobj, **kwargs)) as tar:
    yield tar
