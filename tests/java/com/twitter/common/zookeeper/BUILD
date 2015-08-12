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
  'SingletonServiceTest.java',
  'ZooKeeperClientTest.java',
  'ZooKeeperUtilsTest.java',
  'guice/ServerSetModuleTest.java',
]

LARGE = [
  'CandidateImplTest.java',
  'GroupTest.java',
  'ServerSetsTest.java',
  'ServerSetImplTest.java',
  'StaticServerSetTest.java',
  'ZooKeeperMapTest.java',
  'ZooKeeperNodeTest.java',
]

java_tests(name = 'small',
  dependencies = [
    '3rdparty/jvm/org/easymock',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/testing/easymock',
    'src/java/com/twitter/common/zookeeper:candidate',
    'src/java/com/twitter/common/zookeeper:server-set',
    'src/thrift/com/twitter/thrift',
  ],
  sources = globs('*.java', exclude=[MEDIUM, LARGE])
)

java_tests(name = 'medium-main',
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/junit',
    '3rdparty/jvm/org/mockito',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/testing/easymock',
    'src/java/com/twitter/common/zookeeper:client',
    'src/java/com/twitter/common/zookeeper:group',
    'src/java/com/twitter/common/zookeeper:lock',
    'src/java/com/twitter/common/zookeeper:partitioner',
    'src/java/com/twitter/common/zookeeper:singleton-service',
    'src/java/com/twitter/common/zookeeper:testing',
    'src/java/com/twitter/common/zookeeper/guice',
  ],
  sources = MEDIUM
)

target(name = 'medium',
  dependencies = [
    ':medium-main',
    'tests/java/com/twitter/common/zookeeper/testing/angrybird'
  ]
)

java_tests(name = 'large',
  dependencies = [
    '3rdparty/jvm/org/easymock',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
    '3rdparty/jvm/org/apache/thrift',
    '3rdparty/jvm/com/google/testing:tl4j',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/net:dynamic-host-set',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/testing/easymock',
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

target(name = 'all',
  dependencies = [
    ':large',
    ':medium',
    ':small',
  ]
)

target(name = 'zookeeper', dependencies = [ ':all' ])
