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



java_tests(name = 'thrift',
  dependencies = [
    '3rdparty:easymock',
    '3rdparty:guava',
    '3rdparty:guice',
    '3rdparty:jsr305',
    '3rdparty:junit',
    '3rdparty:servlet-api',
    '3rdparty:thrift',
    '3rdparty:tl4j',
    '3rdparty:zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
    'src/java/com/twitter/common/testing',
    'src/java/com/twitter/common/thrift',
    'src/java/com/twitter/common/thrift/text',
    'src/java/com/twitter/common/thrift:testing',
    'src/java/com/twitter/common/util',
    'src/java/com/twitter/common/util:system-mocks',
    'src/java/com/twitter/common/util:testing',
    'src/java/com/twitter/common/zookeeper',
    'src/java/com/twitter/common/zookeeper:testing',
    'src/thrift/com/twitter/common/thrift/text',
    'src/thrift/com/twitter/thrift',
  ],
  sources = globs('*.java', 'callers/*.java', 'loadbalancing/*.java', 'monitoring/*.java', 'text/*.java')
)
