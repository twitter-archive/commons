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
    pants('3rdparty:guava'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net:util'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = [ 'ZooKeeperClient.java', 'ZooKeeperUtils.java' ]
)

java_library(name = 'node',
  provides = zkjar('node'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/util'),
    pants(':client'),
  ],
  sources = [ 'ZooKeeperNode.java' ]
)

java_library(name = 'map',
  provides = zkjar('map'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/util'),
    pants(':client'),
  ],
  sources = [ 'ZooKeeperMap.java' ]
)

java_library(name = 'lock',
  provides = zkjar('lock'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants(':client'),
  ],
  sources = [ 'DistributedLock.java', 'DistributedLockImpl.java' ]
)

java_library(name = 'group',
  provides = zkjar('group'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/util'),
    pants(':client'),
  ],
  sources = [ 'Group.java' ]
)

java_library(name = 'partitioner',
  provides = zkjar('partitioner'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:zookeeper'),
    pants(':group'),
  ],
  sources = [ 'Partitioner.java' ]
)

java_library(name = 'candidate',
  provides = zkjar('candidate'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants(':client'),
    pants(':group'),
  ],
  sources = [ 'Candidate.java', 'CandidateImpl.java' ]
)

java_library(name = 'server-set',
  provides = zkjar('server-set'),
  dependencies = [
    pants('3rdparty:gson'),
    pants('3rdparty:guava'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/args'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/io:json'),
    pants('src/java/com/twitter/common/io:thrift'),
    pants('src/java/com/twitter/common/net:dynamic-host-set'),
    pants('src/java/com/twitter/common/util'),
    pants('src/thrift/com/twitter/thrift'),
    pants(':client'),
    pants(':group'),
  ],
  sources = [ 'ServerSet.java', 'ServerSetImpl.java' ]
)

java_library(name = 'singleton-service',
  provides = zkjar('singleton-service'),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:zookeeper'),
    pants('src/thrift/com/twitter/thrift'),
    pants(':candidate'),
    pants(':client'),
    pants(':server-set'),
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
    pants('3rdparty:commons-io'),
    pants('3rdparty:guava'),
    pants('3rdparty:junit'),
    pants('3rdparty:tl4j'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/application:action'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/io'),
    pants('src/java/com/twitter/common/quantity'),
    pants(':client'),
  ],
  sources = globs('testing/*.java'),
)
