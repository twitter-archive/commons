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
    pants('3rdparty/jvm/org/easymock'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/junit'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/testing/easymock'),
    pants('src/java/com/twitter/common/zookeeper:candidate'),
    pants('src/java/com/twitter/common/zookeeper:server-set'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = globs('*.java') - MEDIUM - LARGE
)

java_tests(name = 'medium-main',
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/inject:guice'),
    pants('3rdparty/jvm/junit'),
    pants('3rdparty/jvm/org/mockito'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/testing/easymock'),
    pants('src/java/com/twitter/common/zookeeper:client'),
    pants('src/java/com/twitter/common/zookeeper:group'),
    pants('src/java/com/twitter/common/zookeeper:lock'),
    pants('src/java/com/twitter/common/zookeeper:partitioner'),
    pants('src/java/com/twitter/common/zookeeper:singleton-service'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
    pants('src/java/com/twitter/common/zookeeper/guice'),
  ],
  sources = MEDIUM
)

jar_library(name = 'medium',
  dependencies = [
    pants(':medium-main'),
    pants('tests/java/com/twitter/common/zookeeper/testing/angrybird')
  ]
)

java_tests(name = 'large',
  dependencies = [
    pants('3rdparty/jvm/org/easymock'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/junit'),
    pants('3rdparty/jvm/org/apache/thrift'),
    pants('3rdparty/jvm/com/google/testing:tl4j'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/net:dynamic-host-set'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/testing/easymock'),
    pants('src/java/com/twitter/common/thrift'),
    pants('src/java/com/twitter/common/zookeeper:candidate'),
    pants('src/java/com/twitter/common/zookeeper:client'),
    pants('src/java/com/twitter/common/zookeeper:group'),
    pants('src/java/com/twitter/common/zookeeper:map'),
    pants('src/java/com/twitter/common/zookeeper:node'),
    pants('src/java/com/twitter/common/zookeeper:server-set'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = LARGE
)

jar_library(name = 'all',
  dependencies = [
    pants(':large'),
    pants(':medium'),
    pants(':small'),
  ]
)

jar_library(name = 'zookeeper', dependencies = [ pants(':all') ])
