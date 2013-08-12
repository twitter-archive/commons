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

import os
import stat

from twitter.common.contextutil import temporary_file, temporary_dir
from twitter.common.dirutil import safe_size, safe_bsize, du


def create_files(tempdir, *filenames):
  for filename in filenames:
    with open(os.path.join(tempdir, filename), 'w') as fp:
      fp.close()


def test_size():
  with temporary_dir() as td:
    create_files(td, 'file1.txt')
    file1 = os.path.join(td, 'file1.txt')

    assert safe_size(file1) == 0
    with open(file1, 'w') as fp:
      fp.write('!' * 101)
    assert safe_size(file1) == 101

    f1stat = os.stat(file1)
    assert safe_bsize(file1) == 512 * f1stat.st_blocks
    assert du(td) == safe_bsize(file1)

    file2 = os.path.join(td, 'file2.txt')
    os.symlink('file1.txt', file2)
    assert safe_size(file2) == len('file1.txt')
    assert safe_bsize(file2) == len('file1.txt')
    assert du(td) == safe_bsize(file1) + len('file1.txt')

  assert safe_size(os.path.join(td, 'file3.txt')) == 0
  assert safe_bsize(os.path.join(td, 'file3.txt')) == 0

  errors = []
  def on_error(path, err):
    errors.append(path)

  safe_size(os.path.join(td, 'file3.txt'), on_error=on_error)
  assert errors == [os.path.join(td, 'file3.txt')]

