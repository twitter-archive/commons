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

def zkjar(name):
  return artifact(
    org = 'com.twitter.common.zookeeper',
    name = name,
    repo = public,
  )

java_library(name = 'client',
  provides = zkjar('client'),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/quantity',
  ],
  sources = [ 'ZooKeeperClient.java', 'ZooKeeperUtils.java' ]
)

java_library(name = 'node',
  provides = zkjar('node'),
  dependencies = [
    ':client',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/util',
  ],
  sources = [ 'ZooKeeperNode.java' ]
)

java_library(name = 'map',
  provides = zkjar('map'),
  dependencies = [
    ':client',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/util',
  ],
  sources = [ 'ZooKeeperMap.java' ]
)

java_library(name = 'lock',
  provides = zkjar('lock'),
  dependencies = [
    ':client',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
  ],
  sources = [ 'DistributedLock.java', 'DistributedLockImpl.java' ]
)

java_library(name = 'group',
  provides = zkjar('group'),
  dependencies = [
    ':client',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/util',
  ],
  sources = [ 'Group.java' ]
)

java_library(name = 'partitioner',
  provides = zkjar('partitioner'),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/org/apache/zookeeper',
    ':group',
  ],
  sources = [ 'Partitioner.java' ]
)

java_library(name = 'candidate',
  provides = zkjar('candidate'),
  dependencies = [
    ':client',
    ':group',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
  ],
  sources = [ 'Candidate.java', 'CandidateImpl.java' ]
)

java_library(name = 'server-set',
  provides = zkjar('server-set'),
  dependencies = [
    ':client',
    ':group',
    '3rdparty/jvm/com/google/code/gson',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/io',
    'src/java/com/twitter/common/io:json',
    'src/java/com/twitter/common/io:thrift',
    'src/java/com/twitter/common/net:dynamic-host-set',
    'src/java/com/twitter/common/util',
    'src/thrift/com/twitter/thrift',
  ],
  sources = [ 'CompoundServerSet.java',
              'ServerSet.java',
              'ServerSets.java',
              'ServerSetImpl.java',
              'StaticServerSet.java',
  ]
)

java_library(name = 'singleton-service',
  provides = zkjar('singleton-service'),
  dependencies = [
    ':candidate',
    ':client',
    ':server-set',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/thrift/com/twitter/thrift',
  ],
  sources = [ 'SingletonService.java' ]
)

# TODO(John Sirois): A legacy convenience target - adjust users and remove
target(name = 'zookeeper',
  dependencies = [
    ':candidate',
    ':client',
    ':group',
    ':lock',
    ':map',
    ':node',
    ':partitioner',
    ':server-set',
    ':singleton-service',
  ]
)

java_library(name = 'testing',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'zookeeper-testing',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/commons-io',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/junit',
    '3rdparty/jvm/com/google/testing:tl4j',
    '3rdparty/jvm/org/apache/zookeeper',
    ':client',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/io',
    'src/java/com/twitter/common/quantity',
  ],
  sources = globs('testing/*.java'),
)
