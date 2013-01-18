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

MEDIUM = [
  'DistributedLockTest.java',
  'PartitionerTest.java',
  'ZooKeeperClientTest.java',
  'ZooKeeperUtilsTest.java',
  'guice/ServerSetModuleTest.java',
]

LARGE = [
  'CandidateImplTest.java',
  'GroupTest.java',
  'ServerSetImplTest.java',
  'ZooKeeperMapTest.java',
  'ZooKeeperNodeTest.java',
]

java_tests(name = 'small',
  dependencies = [
    '3rdparty:easymock',
    '3rdparty:guava',
    '3rdparty:junit',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/testing',
    'src/java/com/twitter/common/zookeeper:candidate',
    'src/java/com/twitter/common/zookeeper:server-set',
    'src/java/com/twitter/common/zookeeper:singleton-service',
    'src/thrift/com/twitter/thrift',
  ],
  sources = globs('*.java') - MEDIUM - LARGE
)

java_tests(name = 'medium-main',
  dependencies = [
    '3rdparty:guava',
    '3rdparty:guice',
    '3rdparty:junit',
    '3rdparty:mockito',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/testing',
    'src/java/com/twitter/common/zookeeper/guice',
    'src/java/com/twitter/common/zookeeper:client',
    'src/java/com/twitter/common/zookeeper:group',
    'src/java/com/twitter/common/zookeeper:lock',
    'src/java/com/twitter/common/zookeeper:partitioner',
    'src/java/com/twitter/common/zookeeper:testing',
  ],
  sources = MEDIUM
)

jar_library(name = 'medium',
  dependencies = [
    'tests/java/com/twitter/common/zookeeper/testing/angrybird',
    ':medium-main',
  ]
)

java_tests(name = 'large',
  dependencies = [
    '3rdparty:easymock',
    '3rdparty:guava',
    '3rdparty:junit',
    '3rdparty:thrift',
    '3rdparty:tl4j',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/net:dynamic-host-set',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/testing',
    'src/java/com/twitter/common/thrift',
    'src/java/com/twitter/common/zookeeper:candidate',
    'src/java/com/twitter/common/zookeeper:client',
    'src/java/com/twitter/common/zookeeper:group',
    'src/java/com/twitter/common/zookeeper:map',
    'src/java/com/twitter/common/zookeeper:node',
    'src/java/com/twitter/common/zookeeper:server-set',
    'src/java/com/twitter/common/zookeeper:testing',
    'src/thrift/com/twitter/thrift',
  ],
  sources = LARGE
)

jar_library(name = 'all',
  dependencies = [
    ':large',
    ':medium',
    ':small',
  ]
)

jar_library(name = 'zookeeper', dependencies = [ ':all' ])
