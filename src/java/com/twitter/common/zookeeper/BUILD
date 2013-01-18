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
    repo = 'build-support/commons/ivy:public',
  )

java_library(name = 'client',
  provides = zkjar('client'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/quantity',
  ],
  sources = [ 'ZooKeeperClient.java', 'ZooKeeperUtils.java' ]
)

java_library(name = 'node',
  provides = zkjar('node'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:jsr305',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/util',
    ':client',
  ],
  sources = [ 'ZooKeeperNode.java' ]
)

java_library(name = 'map',
  provides = zkjar('map'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/util',
    ':client',
  ],
  sources = [ 'ZooKeeperMap.java' ]
)

java_library(name = 'lock',
  provides = zkjar('lock'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:jsr305',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    ':client',
  ],
  sources = [ 'DistributedLock.java', 'DistributedLockImpl.java' ]
)

java_library(name = 'group',
  provides = zkjar('group'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:jsr305',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/util',
    ':client',
  ],
  sources = [ 'Group.java' ]
)

java_library(name = 'partitioner',
  provides = zkjar('partitioner'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:jsr305',
    '3rdparty:zookeeper',
    ':group',
  ],
  sources = [ 'Partitioner.java' ]
)

java_library(name = 'candidate',
  provides = zkjar('candidate'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    ':client',
    ':group',
  ],
  sources = [ 'Candidate.java', 'CandidateImpl.java' ]
)

java_library(name = 'server-set',
  provides = zkjar('server-set'),
  dependencies = [
    '3rdparty:gson',
    '3rdparty:guava',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/io',
    'src/java/com/twitter/common/io:json',
    'src/java/com/twitter/common/io:thrift',
    'src/java/com/twitter/common/net:dynamic-host-set',
    'src/java/com/twitter/common/util',
    'src/thrift/com/twitter/thrift',
    ':client',
    ':group',
  ],
  sources = [ 'ServerSet.java', 'ServerSetImpl.java' ]
)

java_library(name = 'singleton-service',
  provides = zkjar('singleton-service'),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:jsr305',
    '3rdparty:zookeeper',
    'src/thrift/com/twitter/thrift',
    ':candidate',
    ':client',
    ':server-set',
  ],
  sources = [ 'SingletonService.java' ]
)

# TODO(John Sirois): A legacy convenience target - adjust users and remove
jar_library(name = 'zookeeper',
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
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:commons-io',
    '3rdparty:guava',
    '3rdparty:junit',
    '3rdparty:tl4j',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/application:action',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/io',
    'src/java/com/twitter/common/quantity',
    ':client',
  ],
  sources = globs('testing/*.java'),
)
