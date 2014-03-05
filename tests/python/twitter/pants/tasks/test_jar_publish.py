# ==================================================================================================
# Copyright 2014 Twitter, Inc.
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
import tempfile

from textwrap import dedent

from twitter.common.dirutil import safe_rmtree

from twitter.pants.base.context_utils import create_context
from twitter.pants.base_build_root_test import BaseBuildRootTest
from twitter.pants.targets.sources import SourceRoot
from twitter.pants.targets import JavaLibrary, ScalaLibrary
from twitter.pants.tasks import TaskError
from twitter.pants.tasks.jar_publish import JarPublish

from mock import call, MagicMock, mock_open, patch

import pytest


class JarPublishTest(BaseBuildRootTest):

  @staticmethod
  def create_options(**kwargs):
    options = dict(jar_publish_force=True,
                   jar_publish_local_snapshot=True,
                   jar_publish_dryrun=True,
                   jar_publish_transitive=True,
                   jar_publish_override=[],
                   jar_publish_restart_at=None)
    options.update(**kwargs)
    return options

  @classmethod
  def setUpClass(cls):
    super(JarPublishTest, cls).setUpClass()

    def get_source_root_fs_path(path):
      return os.path.realpath(os.path.join(cls.build_root, path))

    SourceRoot.register(get_source_root_fs_path('src/java'), JavaLibrary)
    SourceRoot.register(get_source_root_fs_path('src/scala'), ScalaLibrary)

    (cls.scala_lib_valid, cls.java_lib_without_publish) = cls.scala_lib_with_java_sources(
      'src/scala/com/twitter/valid',
      'scala_foo',
      ['scala_foo.scala'],
      'src/java/com/twitter/valid',
      'java_foo',
      ['java_foo.java'])

    cls.sl = cls.library('src/scala/com/twitter', 'scala_library', 'bar', ['c.scala'])

  def setUp(self):
    super(JarPublishTest, self).setUp()
    self.pants_workdir = tempfile.mkdtemp()
    self.jar_publish_local_dir = tempfile.mkdtemp()

  def tearDown(self):
    super(JarPublishTest, self).tearDown()
    safe_rmtree(self.pants_workdir)

  def context(self, config=None, **options):
    ini = dedent("""
          [DEFAULT]
          pants_workdir: %(pants_workdir)s
          pants_supportdir: /tmp/build-support
          """).strip() % dict(pants_workdir=self.pants_workdir)
    opts = dict(jar_publish_local=self.jar_publish_local_dir)
    opts.update(**options)
    return create_context(config=config or ini, options=self.create_options(**opts),
                          target_roots=[])

  def test_jar_publish_init(self):
    ini = dedent("""
          [DEFAULT]
          pants_workdir: /tmp/pants.d
          pants_supportdir: /tmp/build-support
          """).strip()
    jar_publish = JarPublish(self.context(config=ini))
    self.assertEquals(jar_publish.outdir, '/tmp/pants.d/publish')
    self.assertEquals(jar_publish._jvmargs, [])
    self.assertEquals(jar_publish.cachedir, '/tmp/pants.d/publish/cache')
    self.assertTrue(jar_publish.dryrun)
    self.assertTrue(jar_publish.force)
    self.assertTrue(jar_publish.transitive)

  def test_jar_publish_smoke_without_provides(self):
    with pytest.raises(TaskError):
      jar_publish = JarPublish(self.context())
      jar_publish.check_clean_master = MagicMock()
      jar_publish.scm = MagicMock()
      jar_publish.exported_targets = MagicMock(return_value=[self.sl])
      jar_publish.execute(self.context().targets())

  @patch('twitter.pants.tasks.jar_publish.PushDb')
  def test_jar_publish_smoke_with_provides(self, MagicMock):
    jar_publish = JarPublish(self.context(config=None))
    jar_publish.check_clean_master = MagicMock()
    jar_publish.scm = MagicMock()
    jar_publish.exported_targets = MagicMock(return_value=[self.scala_lib_valid])
    jar_publish.execute(self.context().targets())
