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

import os
import unittest

from tempfile import mkdtemp
from textwrap import dedent

from twitter.common.dirutil import safe_mkdir, safe_open, safe_rmtree

from twitter.pants.base.build_root import BuildRoot
from twitter.pants.base.address import Address
from twitter.pants.base.target import Target
from twitter.pants.targets.sources import SourceRoot


class BaseBuildRootTest(unittest.TestCase):
  """A baseclass useful for tests requiring a temporary buildroot."""

  BUILD_ROOT = None

  @classmethod
  def build_path(cls, relpath):
    """Returns the canonical BUILD file path for the given relative build path."""
    if os.path.basename(relpath).startswith('BUILD'):
      return relpath
    else:
      return os.path.join(relpath, 'BUILD')

  @classmethod
  def create_dir(cls, relpath):
    """Creates a directory under the buildroot.

    relpath: The relative path to the directory from the build root.
    """
    safe_mkdir(os.path.join(cls.BUILD_ROOT, relpath))

  @classmethod
  def create_file(cls, relpath, contents='', mode='w'):
    """Writes to a file under the buildroot.

    relpath:  The relative path to the file from the build root.
    contents: A string containing the contents of the file - '' by default..
    mode:     The mode to write to the file in - over-write by default.
    """
    with safe_open(os.path.join(cls.BUILD_ROOT, relpath), mode=mode) as fp:
      fp.write(contents)

  @classmethod
  def create_target(cls, relpath, target):
    """Adds the given target specification to the BUILD file at relpath.

    relpath: The relative path to the BUILD file from the build root.
    target:  A string containing the target definition as it would appear in a BUILD file.
    """
    cls.create_file(cls.build_path(relpath), target, mode='a')

  @classmethod
  def setUpClass(cls):
    cls.BUILD_ROOT = mkdtemp(suffix='_BUILD_ROOT')
    BuildRoot().path = cls.BUILD_ROOT
    cls.create_file('pants.ini')
    Target._clear_all_addresses()

  @classmethod
  def tearDownClass(cls):
    BuildRoot().reset()
    SourceRoot.reset()
    safe_rmtree(cls.BUILD_ROOT)

  @classmethod
  def target(cls, address):
    """Resolves the given target address to a Target object.

    address: The BUILD target address to resolve.

    Returns the corresponding Target or else None if the address does not point to a defined Target.
    """
    return Target.get(Address.parse(cls.BUILD_ROOT, address, is_relative=False))

  @classmethod
  def create_files(cls, path, files):
    for f in files:
      cls.create_file(os.path.join(path, f), contents=f)

  @classmethod
  def library(cls, path, target_type, name, sources):
    cls.create_files(path, sources)

    cls.create_target(path, dedent('''
        %(target_type)s(name='%(name)s',
          sources=[%(sources)s],
        )
      ''' % dict(target_type=target_type, name=name, sources=repr(sources or []))))
    return cls.target('%s:%s' % (path, name))

  @classmethod
  def scala_lib_with_java_sources(cls, path, name, sources, java_path, java_name, java_sources,
                                  java_provides=False):
    cls.create_target('build/ivy',
                      dedent('''
                        repo(name = 'ivy',
                             url = 'https://art.twitter.biz/',
                             push_db = 'dummy.pushdb')
                      '''))
    cls.create_files(path, sources)
    cls.create_files(java_path, java_sources)

    if java_provides:
      cls.create_target(java_path, dedent('''
        java_library(name='%(name)s',
          sources=[%(sources)s],
          provides= artifact(
                            org = 'com.twitter',
                            name = 'java_lib',
                            repo = pants('build/ivy:ivy')),
        )
        ''' % dict(name=java_name, sources=repr(java_sources or []))))
    else:
      cls.create_target(java_path, dedent('''
        java_library(name='%(name)s',
          sources=[%(sources)s],
        )
        ''' % dict(name=java_name, sources=repr(java_sources or []))))

    cls.create_target(path, dedent('''
        scala_library(name='%(name)s',
          sources=[%(sources)s],
          java_sources=[pants('%(java_path)s:%(java_name)s')],
          provides= artifact(
                      org = 'com.twitter',
                      name = 'scala_lib',
                      repo = pants('build/ivy:ivy')),
        )
      ''' % dict(name=name, sources=repr(sources or []),
                 java_path=java_path, java_name=java_name)))

    return (cls.target('%s:%s' % (path, name)), cls.target('%s:%s' % (java_path, java_name)))

  @classmethod
  def resources(cls, path, name, *sources):
    return cls.library(path, 'resources', name, sources)
