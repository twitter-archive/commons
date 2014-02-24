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

from __future__ import print_function

import os
import sys

from twitter.common import log

from twitter.pants.version import VERSION as _VERSION

from .build_root import BuildRoot


def get_version():
  return _VERSION


def get_buildroot():
  """Returns the pants ROOT_DIR, calculating it if needed."""
  try:
    return BuildRoot().path
  except BuildRoot.NotFoundError as e:
    print(e.message, file=sys.stderr)
    sys.exit(1)


def set_buildroot(path):
  """Sets the pants ROOT_DIR.

  Generally only useful for tests.
  """
  BuildRoot().path = path


from twitter.pants.scm import Scm


_SCM = None


def get_scm():
  """Returns the pants Scm if any."""
  # TODO(John Sirois): Extract a module/class to carry the bootstrap logic.
  global _SCM
  if not _SCM:
    # We know about git, so attempt an auto-configure
    git_dir = os.path.join(get_buildroot(), '.git')
    if os.path.isdir(git_dir):
      from twitter.pants.scm.git import Git
      git = Git(worktree=get_buildroot())
      try:
        log.info('Detected git repository on branch %s' % git.branch_name)
        set_scm(git)
      except git.LocalException:
        pass
  return _SCM


def set_scm(scm):
  """Sets the pants Scm."""
  if scm is not None:
    if not isinstance(scm, Scm):
      raise ValueError('The scm must be an instance of Scm, given %s' % scm)
    global _SCM
    _SCM = scm

