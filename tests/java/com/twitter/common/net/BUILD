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



java_tests(name = 'net',
  dependencies = [
    '3rdparty/jvm/commons-codec',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/hamcrest:hamcrest-core',
    '3rdparty/jvm/org/mortbay/jetty',
    '3rdparty/jvm/junit',
    '3rdparty/jvm/javax/servlet:servlet-api',
    '3rdparty/jvm/org/apache/thrift',
    '3rdparty/jvm/com/google/testing:tl4j',
    '3rdparty/jvm/org/apache/zookeeper',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/collections',
    'src/java/com/twitter/common/net:pool',
    'src/java/com/twitter/common/net:url-resolver',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/testing/easymock',
    'src/java/com/twitter/common/thrift',
    'src/java/com/twitter/common/thrift:testing',
    'src/java/com/twitter/common/util',
    'src/java/com/twitter/common/util:system-mocks',
    'src/java/com/twitter/common/util:testing',
    'src/java/com/twitter/common/zookeeper',
    'src/java/com/twitter/common/zookeeper:testing',
    'src/thrift/com/twitter/thrift',
  ],
  sources = globs('*.java', 'pool/*.java', 'monitoring/*.java', 'loadbalancing/*.java')
)
