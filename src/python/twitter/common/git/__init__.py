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

from __future__ import print_function

import contextlib
from twitter.common.lang import Compatibility

import git

__all__ = (
  'DirtyRepositoryError',
  'branch',
  'checkout',
)


class DirtyRepositoryError(Exception):
  def __init__(self, branch=None):
    super(DirtyRepositoryError, self).__init__('%s must not be dirty!' % (
      'Current branch (%s)' % branch if branch else 'Current branch'))


def validate_args(sha, project, repo=None):
  """Validates arguments and returns head, repo, branch_name"""
  repo = repo or git.Repo()
  active_head = repo.active_branch

  if repo.is_dirty():
    raise DirtyRepositoryError(active_head)
  else:
    print('Active head: %s' % active_head)

  branch_name = '_%s_' % sha
  if project:
    if not isinstance(project, Compatibility.string):
      raise ValueError('project must be a string, got %r' % (project,))
    branch_name = '_' + project + branch_name

  return active_head, repo, branch_name


def checkout_branch(repo, sha, branch_name):
  print('Creating head %s' % branch_name)
  head = repo.create_head(branch_name)
  head.commit = repo.commit(sha) if isinstance(sha, Compatibility.string) else sha
  head.checkout()


@contextlib.contextmanager
def branch(sha, project=None, repo=None):
  """
    Perform actions at a given sha in a repository.  Implemented as a context manager.
    Must be run in the CWD of a git repository.

    :param sha: A fully-qualified revision as specified in
      http://www.kernel.org/pub/software/scm/git/docs/git-rev-parse.html
    :param project: (optional) A label to prepend to the temporary branch.
    :param repo: (optional) The location of the .git repository (by default current working directory.)

    Example:
      >>> import subprocess
      >>> from twitter.common.git import branch
      >>> with branch('master@{yesterday}'):
      ...  subprocess.check_call('./pants tests/python/twitter/common:all')


  """

  active_head, repo, branch_name = validate_args(sha, project, repo)

  try:
    checkout_branch(repo, sha, branch_name)
    yield
  finally:
    print('Resetting head: %s' % active_head)
    active_head.checkout()
    print('Deleting temporary head: %s' % branch_name)
    repo.delete_head(branch_name, force=True)


def checkout(sha, project=None, repo=None):
  """
    Checkout a sha in a given repository.

    :param sha: A fully-qualified revision as specified in
      http://www.kernel.org/pub/software/scm/git/docs/git-rev-parse.html
    :param project: (optional) A label to prepend to the temporary branch.
    :param repo: (optional) The location of the .git repository (by default current working directory.)


    If project is supplied, generate a more readable branch name based upon
    the project name.

    If repo is supplied, it should be the location of the git repository.
  """
  _, repo, branch_name = validate_args(sha, project, repo)
  checkout_branch(repo, sha, branch_name)
