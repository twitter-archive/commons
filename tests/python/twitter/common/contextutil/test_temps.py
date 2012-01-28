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
from twitter.common.contextutil import temporary_file, temporary_dir

def test_temporary_file_no_args():
  with temporary_file() as fp:
    assert os.path.exists(fp.name), 'temporary file should exist within the context.'
  assert os.path.exists(fp.name) == False, 'temporary file should not exist outside of the context.'

def test_temporary_file_without_cleanup():
  with temporary_file(cleanup=False) as fp:
    assert os.path.exists(fp.name), 'temporary file should exist within the context.'
  assert os.path.exists(fp.name), 'temporary file should exist outside of context if cleanup=False.'
  os.unlink(fp.name)

def test_temporary_file_within_other_dir():
  with temporary_dir() as path:
    with temporary_file(root_dir=path) as f:
      assert os.path.realpath(f.name).startswith(os.path.realpath(path)), \
        'file should be created in root_dir if specified.'

def test_temporary_dir_no_args():
  with temporary_dir() as path:
    assert os.path.exists(path), 'temporary dir should exist within the context.'
    assert os.path.isdir(path), 'temporary dir should be a dir and not a file'
  assert os.path.exists(path) == False, 'temporary dir should not exist outside of the context.'

def test_temporary_dir_without_cleanup():
  with temporary_dir(cleanup=False) as path:
    assert os.path.exists(path), 'temporary dir should exist within the context.'
  assert os.path.exists(path), 'temporary dir should exist outside of context if cleanup=False.'
  shutil.rmtree(path)

def test_temporary_dir_with_root_dir():
  with temporary_dir() as path1:
    with temporary_dir(root_dir=path1) as path2:
      assert os.path.realpath(path2).startswith(os.path.realpath(path1)), \
        'nested temporary dir should be created within outer dir.'
