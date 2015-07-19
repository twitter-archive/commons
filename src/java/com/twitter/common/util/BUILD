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

EXECUTOR_SERVICE_SHUTDOWN_SOURCES = [
  'concurrent/ExecutorServiceShutdown.java'
]

SYSTEM_MOCKS_SOURCES = [
  'Clock.java',
  'Random.java',
]

SAMPLER_SOURCES = [
  'Sampler.java'
]

java_library(name = 'logging',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'jdk-logging',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
  ],
  sources = globs('logging/*.java'),
)

java_library(name = 'executor-service-shutdown',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-executor-service-shutdown',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/quantity',
  ],
  sources = EXECUTOR_SERVICE_SHUTDOWN_SOURCES,
)

java_library(name = 'util',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util',
    repo = public,
  ),
  dependencies = [
    ':executor-service-shutdown',
    '3rdparty/jvm/commons-lang',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    # TODO(William Farner): Untangle this false dependency, there are likely people relying
    #   on the util target just to get logging - fix.
    ':logging',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
  ],
  sources = globs('*.java', 'concurrent/*.java',
                  exclude = [EXECUTOR_SERVICE_SHUTDOWN_SOURCES,
                             SYSTEM_MOCKS_SOURCES,
                             SAMPLER_SOURCES]),
)

java_library(name = 'system-mocks',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-system-mocks',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
  ],
  sources = SYSTEM_MOCKS_SOURCES,
)

java_library(name = 'sampler',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-sampler',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    ':system-mocks',
  ],
  sources = SAMPLER_SOURCES,
)

java_library(name = 'testing',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'util-testing',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    ':system-mocks',
    'src/java/com/twitter/common/quantity',
  ],
  sources = globs('testing/*.java'),
)
