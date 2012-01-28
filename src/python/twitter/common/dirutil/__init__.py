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
import stat
import errno

from tail import tail_f

def safe_mkdir(directory, clean=False):
  """
    Ensure a directory is present.  If it's not there, create it.  If it is,
    no-op. If clean is True, ensure the directory is empty.
  """
  if clean:
    safe_rmtree(directory)
  try:
    os.makedirs(directory)
  except OSError, e:
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
    Equivalent of chmod a+x path
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

__all__ = [
  'safe_mkdir',
  'safe_open',
  'chmod_plus_x',
  'tail_f',
]
