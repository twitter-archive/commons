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
    repo = pants('build-support/commons/ivy:public'),
  )

java_library(name = 'client',
  provides = zkjar('client'),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net:util'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = [ 'ZooKeeperClient.java', 'ZooKeeperUtils.java' ]
)

java_library(name = 'node',
  provides = zkjar('node'),
  dependencies = [
    pants(':client'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = [ 'ZooKeeperNode.java' ]
)

java_library(name = 'map',
  provides = zkjar('map'),
  dependencies = [
    pants(':client'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = [ 'ZooKeeperMap.java' ]
)

java_library(name = 'lock',
  provides = zkjar('lock'),
  dependencies = [
    pants(':client'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources = [ 'DistributedLock.java', 'DistributedLockImpl.java' ]
)

java_library(name = 'group',
  provides = zkjar('group'),
  dependencies = [
    pants(':client'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/util'),
  ],
  sources = [ 'Group.java' ]
)

java_library(name = 'partitioner',
  provides = zkjar('partitioner'),
  dependencies = [
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants(':group'),
  ],
  sources = [ 'Partitioner.java' ]
)

java_library(name = 'candidate',
  provides = zkjar('candidate'),
  dependencies = [
    pants(':client'),
    pants(':group'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
  ],
  sources = [ 'Candidate.java', 'CandidateImpl.java' ]
)

java_library(name = 'server-set',
  provides = zkjar('server-set'),
  dependencies = [
    pants(':client'),
    pants(':group'),
    pants('3rdparty/jvm/com/google/code/gson'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/io:json'),
    pants('src/java/com/twitter/common/io:thrift'),
    pants('src/java/com/twitter/common/net:dynamic-host-set'),
    pants('src/java/com/twitter/common/util'),
    pants('src/thrift/com/twitter/thrift'),
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
    pants(':candidate'),
    pants(':client'),
    pants(':server-set'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/com/google/code/findbugs:jsr305'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = [ 'SingletonService.java' ]
)

# TODO(John Sirois): A legacy convenience target - adjust users and remove
jar_library(name = 'zookeeper',
  dependencies = [
    pants(':candidate'),
    pants(':client'),
    pants(':group'),
    pants(':lock'),
    pants(':map'),
    pants(':node'),
    pants(':partitioner'),
    pants(':server-set'),
    pants(':singleton-service'),
  ]
)

java_library(name = 'testing',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'zookeeper-testing',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty/jvm/commons-io'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/junit'),
    pants('3rdparty/jvm/com/google/testing:tl4j'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants(':client'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = globs('testing/*.java'),
)
