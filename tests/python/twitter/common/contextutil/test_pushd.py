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
from twitter.common.contextutil import pushd, temporary_dir

def test_simple_pushd():
  pre_cwd = os.getcwd()
  with temporary_dir() as tempdir:
    with pushd(tempdir) as path:
      assert path == tempdir
      assert os.getcwd() == os.path.realpath(tempdir)
    assert os.getcwd() == pre_cwd
  assert os.getcwd() == pre_cwd

def test_nested_pushd():
  pre_cwd = os.getcwd()
  with temporary_dir() as tempdir1:
    with pushd(tempdir1) as path1:
      assert os.getcwd() == os.path.realpath(tempdir1)
      with temporary_dir(root_dir=tempdir1) as tempdir2:
        with pushd(tempdir2) as path2:
          assert os.getcwd() == os.path.realpath(tempdir2)
        assert os.getcwd() == os.path.realpath(tempdir1)
      assert os.getcwd() == os.path.realpath(tempdir1)
    assert os.getcwd() == pre_cwd
  assert os.getcwd() == pre_cwd
