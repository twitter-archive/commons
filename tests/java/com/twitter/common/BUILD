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

jar_library(name = 'small',
  dependencies = [
    '3rdparty:junit',
    'tests/java/com/twitter/common/application',
    'tests/java/com/twitter/common/application/modules:all',
    'tests/java/com/twitter/common/args:small',
    'tests/java/com/twitter/common/base',
    'tests/java/com/twitter/common/collections',
    'tests/java/com/twitter/common/inject',
    'tests/java/com/twitter/common/io',
    'tests/java/com/twitter/common/logging',
    'tests/java/com/twitter/common/memcached',
    'tests/java/com/twitter/common/metrics',
    'tests/java/com/twitter/common/net',
    'tests/java/com/twitter/common/net/http/handlers',
    'tests/java/com/twitter/common/objectsize',
    'tests/java/com/twitter/common/quantity',
    'tests/java/com/twitter/common/stats',
    'tests/java/com/twitter/common/testing',
    'tests/java/com/twitter/common/text',
    'tests/java/com/twitter/common/thrift',
    'tests/java/com/twitter/common/util',
    'tests/java/com/twitter/common/util/caching',
    'tests/java/com/twitter/common/zookeeper:small',

    # TODO(jsirois): conflict with spiderduck -> finagle -> new ostrich here - upgrade dep.
    #'tests/scala/com/twitter/common/stats',

    # No tests for these, adding deps to support a single target for --ide builds with comprehensive
    # deps
    'src/java/com/twitter/common/checkstyle',
    'src/java/com/twitter/common/logging:scribe',
    'src/java/com/twitter/common/net/http',
  ],
)

jar_library(name = 'medium',
  dependencies = [
    'tests/java/com/twitter/common/args:medium',
    'tests/java/com/twitter/common/zookeeper:medium',
  ]
)

jar_library(name = 'large',
  dependencies = [
    'tests/java/com/twitter/common/zookeeper:large',
  ]
)

jar_library(name = 'all',
  dependencies = [
    ':small',
    ':medium',
    ':large',
  ]
)

# TODO(John Sirois): kill this legacy alias with a deprecation cycle
jar_library(name = 'all-tests', dependencies = [ ':all' ])
