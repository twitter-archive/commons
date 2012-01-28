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

import os
import shutil
import tempfile
import sys

class environment_as(object):
  """
    Update the environment to the supplied values, for example:

    with environment_as(PYTHONPATH = 'foo:bar:baz',
                        PYTHON = '/usr/bin/python2.6'):
      subprocess.Popen(foo).wait()
  """
  def __init__(self, **kwargs):
    self.new_environment = kwargs
    self.old_environment = {}

  @staticmethod
  def setenv(key, val):
    if val is not None:
      os.putenv(key, val)
    else:
      os.unsetenv(key)

  def __enter__(self):
    for key in self.new_environment:
      self.old_environment[key] = os.environ.get(key, None)
      environment_as.setenv(key, self.new_environment[key])

  def __exit__(self, exctype, value, traceback):
    for key in self.old_environment:
      environment_as.setenv(key, self.old_environment[key])


class temporary_dir(object):
  """
    A with-context that creates a temporary directory.

    You may specify the following keyword args:
      root_dir [path]: The parent directory to create the temporary directory.
      cleanup [True/False]: Whether or not to clean up the temporary directory.
  """
  def __init__(self, root_dir=None, cleanup=True):
    self.root_dir = root_dir
    self.cleanup = cleanup

  def __enter__(self):
    self.path = tempfile.mkdtemp(dir=self.root_dir)
    return self.path

  def __exit__(self, exctype, value, traceback):
    if self.cleanup:
      shutil.rmtree(self.path)


class temporary_file(object):
  """
    A with-context that creates a temporary file.

    You may specify the following keyword args:
      root_dir [path]: The parent directory to create the temporary file.
      cleanup [True/False]: Whether or not to clean up the temporary file.
  """
  def __init__(self, root_dir=None, cleanup=True):
    self.root_dir = root_dir
    self.cleanup = cleanup

  def __enter__(self):
    # argh, I would love to use os.fdopen here but then fp.name == '<fdopen>'
    # and that's unacceptable behavior for most cases where I want to use temporary_file
    fh, self.path = tempfile.mkstemp(dir=self.root_dir)
    os.close(fh)
    self.fd = open(self.path, 'w+')
    return self.fd

  def __exit__(self, exctype, value, traceback):
    if not self.fd.closed:
      self.fd.close()
    if self.cleanup:
      os.unlink(self.path)


class pushd(object):
  """
    A with-context that encapsulates pushd/popd.
  """
  def __init__(self, directory):
    self.directory = directory

  def __enter__(self):
    self.cwd = os.getcwd()
    os.chdir(self.directory)
    return self.directory

  def __exit__(self, exctype, value, traceback):
    os.chdir(self.cwd)


class mutable_sys(object):
  """
    A with-context that does backup/restore of sys.argv, sys.path and
    sys.stderr/stdout/stdin following execution.
  """
  SAVED_ATTRIBUTES = [
    'stdin', 'stdout', 'stderr',
    'argv', 'path', 'path_importer_cache', 'path_hooks',
    '__egginsert'
  ]

  def __init__(self):
    self._sys_backup = dict((key, getattr(sys, key))
      for key in mutable_sys.SAVED_ATTRIBUTES if hasattr(sys, key))
    self._sys_delete = set(filter(lambda key: not hasattr(sys, key), mutable_sys.SAVED_ATTRIBUTES))

  def __enter__(self):
    return sys

  def __exit__(self, exctype, value, traceback):
    for attribute in self._sys_backup:
      setattr(sys, attribute, self._sys_backup[attribute])
    for attribute in self._sys_delete:
      if hasattr(sys, attribute):
        delattr(sys, attribute)
