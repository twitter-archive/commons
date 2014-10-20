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
import pytest

from contextlib import contextmanager

from twitter.common.contextutil import temporary_dir
from twitter.common.dirutil import Fileset as RealFileset, touch


class Fileset(RealFileset):
  FILELIST = []

  @classmethod
  @contextmanager
  def over(cls, L):
    old, cls.FILELIST = cls.FILELIST, L
    yield
    cls.FILELIST = old

  @classmethod
  def walk(cls, path=None, allow_dirs=False, follow_links=False):
    for filename in cls.FILELIST:
      if isinstance(filename, Exception):
        raise filename
      elif filename.endswith('/'):
        path = os.path.normpath(filename)
        if allow_dirs:
          yield path
          yield path + '/'
      else:
        yield os.path.normpath(filename)


def ll(foo):
  return len(list(foo))


def leq(fs_foo, *bar):
  return set(fs_foo) == set(bar)


def test_add():
  with Fileset.over(['a', 'b']):
    assert leq(Fileset.rglobs('a') + ['b'], 'a', 'b')

  with Fileset.over(['a', 'b']):
    assert leq(Fileset.rglobs('a') + Fileset.rglobs('b'), 'a', 'b')


def test_subtract():
  with Fileset.over(['a', 'b']):
    assert leq(Fileset.rglobs('*') - ['b'], 'a')

  with Fileset.over(['a', 'b']):
    assert leq(Fileset.rglobs('*') - Fileset.rglobs('a'), 'b')


def test_lazy_raise():
  with pytest.raises(KeyError):
    with Fileset.over(['a', KeyError()]):
      apply(Fileset.rglobs('*'))


def test_zglobs():
  FILELIST = [
    'foo.txt',
    '.hidden_file',
    'a/',
    'a/foo.txt',
    'a/.hidden_file',
    'a/b/',
    'a/b/foo.txt',
    'a/b/.hidden_file',
    'foo/bar/baz.txt',
    'foo/',
    'foo/bar/',
    'a/b/c/',
  ]

  with Fileset.over(FILELIST):
    assert ll(Fileset.zglobs('')) == 0
    assert ll(Fileset.zglobs('*.txt')) == 1
    assert ll(Fileset.zglobs('.*')) == 1
    assert ll(Fileset.zglobs('*/*.txt')) == 1
    assert ll(Fileset.zglobs('*/.*')) == 1
    assert ll(Fileset.zglobs('*/*/*.txt')) == 2
    assert ll(Fileset.zglobs('*/*/.*')) == 1
    assert ll(Fileset.zglobs('???.txt')) == 1
    assert ll(Fileset.zglobs('?/*.txt')) == 1
    assert ll(Fileset.zglobs('?/?/*.txt')) == 1
    assert ll(Fileset.zglobs('a/*.txt')) == 1
    assert ll(Fileset.zglobs('a/b/*.txt')) == 1
    assert ll(Fileset.zglobs('a/???.txt')) == 1
    assert ll(Fileset.zglobs('a/?/*.txt')) == 1
    assert ll(Fileset.zglobs('*.txt', '*/.*')) == 2

  with Fileset.over(FILELIST):
    assert leq(Fileset.zglobs('*'), 'foo.txt', 'a', 'foo')
    assert leq(Fileset.zglobs('.*'), '.hidden_file')
    assert leq(Fileset.zglobs('**'), 'foo.txt', 'a', 'foo')
    assert leq(Fileset.zglobs('**/'), 'a/', 'a/b/', 'a/b/c/', 'foo/', 'foo/bar/')
    assert leq(Fileset.zglobs('**/*'),
        'foo.txt',
        'a',
        'a/foo.txt',
        'a/b',
        'a/b/foo.txt',
        'foo/bar/baz.txt',
        'foo',
        'foo/bar',
        'a/b/c'
    )
    assert leq(Fileset.zglobs('**/*.txt'), 'foo.txt', 'a/foo.txt', 'a/b/foo.txt',
        'foo/bar/baz.txt')
    assert leq(Fileset.zglobs('**/foo.txt'), 'foo.txt', 'a/foo.txt', 'a/b/foo.txt')
    assert leq(Fileset.zglobs('**/.*'),
        '.hidden_file', 'a/.hidden_file', 'a/b/.hidden_file')
    assert leq(Fileset.zglobs('*', 'a/*.txt'), 'foo.txt', 'a', 'foo', 'a/foo.txt')


def test_fnmatch():
  with Fileset.over(['.txt']):
    assert leq(Fileset.zglobs('*.txt'))
    assert leq(Fileset.zglobs('?.txt'))
    assert leq(Fileset.zglobs('[].txt'))
    assert leq(Fileset.zglobs('.*'), '.txt')
    assert leq(Fileset.zglobs('*.py', '.*'), '.txt')
    assert leq(Fileset.rglobs(''))
    assert leq(Fileset.rglobs('*.txt'))
    assert leq(Fileset.rglobs('?.txt'))
    assert leq(Fileset.rglobs('[].txt'))
    assert leq(Fileset.rglobs('.*'), '.txt')
    assert leq(Fileset.rglobs('*.py', '.*'), '.txt')
    assert leq(Fileset.rglobs('.*', '.*'), '.txt')

  with Fileset.over(['a.txt']):
    for operation in (Fileset.rglobs, Fileset.zglobs):
      assert leq(operation('*.txt'), 'a.txt')
      assert leq(operation('?.txt'), 'a.txt')
      assert leq(operation('[abcd].txt'), 'a.txt')

  with temporary_dir() as tempdir:
    touch(os.path.join(tempdir, '.txt'))
    assert leq(Fileset.globs('.txt', root=tempdir), '.txt')
    assert leq(Fileset.globs('*.txt', root=tempdir))
    assert leq(Fileset.globs('', root=tempdir))


def test_walk_altdir():
  files = []
  with temporary_dir() as td:
    for k in range(10):
      filename = os.path.join(td, '%010d' % k)
      with open(filename, 'w') as fp:
        fp.write('booyeah')
      files.append(filename)
    assert set(RealFileset.zglobs('*', root=td)) == set(os.path.basename(fn) for fn in files)
