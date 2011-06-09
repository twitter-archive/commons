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



SYSTEM_MOCKS_SOURCES = [
  'Clock.java',
  'Random.java',
]

SAMPLER_SOURCES = [
  'Sampler.java'
]

KESTREL_SOURCES = [
  'KestrelConsumer.java',
]

java_library(name = 'logging',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'jdk-logging',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
  ],
  sources = globs('logging/*.java'),
)

java_library(name = 'util',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:commons-lang'),
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    # TODO(William Farner): Untangle this false dependency, there are likely people relying
    #   on the util target just to get logging - fix.
    pants(':logging'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/stats'),
  ],
  sources = globs('*.java', 'concurrent/*.java')
    - SYSTEM_MOCKS_SOURCES - SAMPLER_SOURCES - KESTREL_SOURCES,
)

java_library(name = 'system-mocks',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-system-mocks',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
  ],
  sources = SYSTEM_MOCKS_SOURCES,
)

java_library(name = 'sampler',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-sampler',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants(':system-mocks'),
  ],
  sources = SAMPLER_SOURCES,
)

java_library(name = 'kestrel',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:spy-memcached'),
    pants('src/java/com/twitter/common/memcached'),
    pants('src/java/com/twitter/common/stats'),
  ],
  sources = KESTREL_SOURCES,
)

java_library(name = 'testing',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-testing',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants(':system-mocks'),
    pants('src/java/com/twitter/common/quantity'),
  ],
  sources = globs('testing/*.java'),
)
