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

SCALA_REV = '2.10'


def make_lib(name, rev, scala_rev=None, excludes=None, deps=None):
  artifact_name=name if scala_rev is None else '%s_%s' % (name, scala_rev)
  jar_dep=jar(org='com.twitter', name=artifact_name, rev=rev)
  if excludes:
    for exclude in excludes:
      jar_dep.exclude(exclude.org, exclude.name)
  jar_library(name=name, jars=[jar_dep], dependencies=deps or [])


make_lib('scrooge-core', '3.12.1', scala_rev=SCALA_REV)


# Common revs for finagle/util artifacts: these need to be bumped in sync
FINAGLE_REV = '6.12.2'
UTIL_REV = '6.12.1'


def make_finagle_lib(name, excludes=None, deps=None):
  make_lib(name, FINAGLE_REV, scala_rev=SCALA_REV, excludes=excludes, deps=deps)

make_finagle_lib('finagle-core')
make_finagle_lib('finagle-http')

make_finagle_lib('finagle-serversets',
  excludes=[
    exclude('com.twitter.common.zookeeper', 'client'),
    exclude('com.twitter.common.zookeeper', 'group'),
    exclude('com.twitter.common.zookeeper', 'server-set'),
  ],
  deps=[
    'src/java/com/twitter/common/zookeeper:client',
    'src/java/com/twitter/common/zookeeper:group',
    'src/java/com/twitter/common/zookeeper:server-set',
  ]
)

make_finagle_lib('finagle-stats',
  excludes=[
    exclude('com.twitter.common', 'metrics'),
  ],
  deps=[
    'src/java/com/twitter/common/metrics',
  ]
)

make_finagle_lib('finagle-thrift')


def make_util_lib(name):
  make_lib(name, UTIL_REV, scala_rev=SCALA_REV)

make_util_lib('util-core')
make_util_lib('util-jvm')


# Extracted from the jsr166e proposal
# See http://github.com/twitter/jsr166e
make_lib('jsr166e', '1.0.0')


make_lib('ostrich', '9.1.2', scala_rev=SCALA_REV)


make_lib('twitter-text', '1.5.0')
