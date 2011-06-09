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
    pants('3rdparty:easymock'),
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/testing'),
    pants('src/java/com/twitter/common/zookeeper'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = globs('*.java') - MEDIUM - LARGE
)

java_tests(name = 'medium',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/testing'),
    pants('src/java/com/twitter/common/zookeeper'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
  ],
  sources = MEDIUM
)

java_tests(name = 'large',
  dependencies = [
    pants('3rdparty:easymock'),
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
    pants('3rdparty:thrift'),
    pants('3rdparty:tl4j'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/net:dynamic-host-set'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/testing'),
    pants('src/java/com/twitter/common/thrift'),
    pants('src/java/com/twitter/common/zookeeper'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = LARGE
)

jar_library(name = 'all',
  dependencies = [
    pants(':small'),
    pants(':medium'),
    pants(':large'),
  ]
)

jar_library(name = 'zookeeper', dependencies = [ pants(':all') ])
