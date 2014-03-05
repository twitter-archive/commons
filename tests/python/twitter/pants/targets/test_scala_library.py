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

from textwrap import dedent

from twitter.pants.base_build_root_test import BaseBuildRootTest
from twitter.pants.base.target import TargetDefinitionException

import pytest

class ScalaLibraryTest(BaseBuildRootTest):
  @classmethod
  def setUpClass(cls):
    super(ScalaLibraryTest, cls).setUpClass()

    cls.create_target('build/ivy',
                      dedent('''
                        repo(name = 'ivy',
                             url = 'https://art.twitter.biz/',
                             push_db = 'dummy.pushdb')
                             '''))

    cls.create_target('src/java',
                      dedent('''
                        java_library(
                          name='java_lib',
                          sources=globs('*.java'),
                          provides= artifact(
                            org = 'com.twitter',
                            name = 'java_lib',
                            repo = pants('build/ivy:ivy')),
                        )'''))

    cls.create_target('src/java',
                      dedent('''
                        java_library(
                          name='java_lib1',
                          sources=globs('*.java'),
                        )'''))
    cls.create_target('src/scala/valid',
                       dedent('''
                        scala_library(
                          name='scala_valid',
                          sources=globs('*.scala'),
                          provides= artifact(
                            org = 'com.twitter',
                            name = 'scala_lib',
                            repo = pants('build/ivy:ivy')),
                          java_sources=[pants('src/java:java_lib1')]
                        )'''))

    cls.create_target('src/scala/invalid',
                       dedent('''
                        scala_library(
                          name='scala_invalid',
                          sources=globs('*.scala'),
                          provides=artifact(
                            org = 'com.twitter',
                            name = 'scala_lib',
                           repo = pants('build/ivy:ivy')),
                          java_sources=[pants('src/java:java_lib')]
                        )'''))

  def test_validation(self):
    with pytest.raises(TargetDefinitionException):
      self.target('src/scala/invalid:scala_invalid')

  def test_valid_sources(self):
    scala_lib = self.target('src/scala/valid:scala_valid')
    java_lib = self.target('src/java:java_lib1')
    self.assertEquals(java_lib.dependencies, [scala_lib])
