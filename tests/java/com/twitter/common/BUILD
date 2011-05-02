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

from pants import *

java_tests(name = 'all-tests',
  dependencies = [
    pants('3rdparty:junit'),
    pants('tests/java/com/twitter/common/application'),
    pants('tests/java/com/twitter/common/args'),
    pants('tests/java/com/twitter/common/base'),
    pants('tests/java/com/twitter/common/collections'),
    pants('tests/java/com/twitter/common/inject'),
    pants('tests/java/com/twitter/common/io'),
    pants('tests/java/com/twitter/common/logging'),
    pants('tests/java/com/twitter/common/memcached'),
    pants('tests/java/com/twitter/common/net'),
    pants('tests/java/com/twitter/common/net/http/handlers'),
    pants('tests/java/com/twitter/common/objectsize'),
    pants('tests/java/com/twitter/common/quantity'),
    pants('tests/java/com/twitter/common/service/registration'),
    pants('tests/java/com/twitter/common/stats'),
    pants('tests/java/com/twitter/common/testing'),
    pants('tests/java/com/twitter/common/thrift'),
    pants('tests/java/com/twitter/common/util'),
    pants('tests/java/com/twitter/common/util/caching'),
    pants('tests/java/com/twitter/common/zookeeper'),
    pants('tests/scala/com/twitter/common/stats'),

    # No tests for these, adding deps to support a single target for --ide builds with comprehensive
    # deps
    pants('src/java/com/twitter/common/checkstyle'),
    pants('src/java/com/twitter/common/logging:scribe'),
    pants('src/java/com/twitter/common/net/http'),
  ],
  sources = [],
)
