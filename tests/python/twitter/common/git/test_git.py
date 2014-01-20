# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

import subprocess

from twitter.common.contextutil import pushd
from twitter.common.git import branch, git

import git
import pytest


first_commit_file_content = 'first commit content'


@pytest.fixture
def repo(tmpdir):
  with pushd(tmpdir.strpath):
    repo = git.Repo.init(tmpdir.strpath)
    filename = 'test'

    tmpdir.join(filename).write(first_commit_file_content)
    repo.index.add([filename])
    repo.index.commit('initial commit')
    repo.create_head('a')

    tmpdir.join(filename).write('more content')
    repo.index.add([filename])
    repo.index.commit('second commit')
    return repo


def test_branch(tmpdir, repo):
  with branch('a', repo=repo):
    with pushd(tmpdir.strpath):
      with open('test') as f:
        assert f.read() == first_commit_file_content


def test_branch_throw(tmpdir, repo):
  with pytest.raises(ValueError):
    with branch('a', repo=repo):
      raise ValueError
