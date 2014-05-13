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
    pants('3rdparty/jvm/commons-codec'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/org/hamcrest:hamcrest-core'),
    pants('3rdparty/jvm/org/mortbay/jetty'),
    pants('3rdparty/jvm/junit'),
    pants('3rdparty/jvm/javax/servlet:servlet-api'),
    pants('3rdparty/jvm/org/apache/thrift'),
    pants('3rdparty/jvm/com/google/testing:tl4j'),
    pants('3rdparty/jvm/org/apache/zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/net:pool'),
    pants('src/java/com/twitter/common/net:url-resolver'),
    pants('src/java/com/twitter/common/net:util'),
    pants('src/java/com/twitter/common/testing/easymock'),
    pants('src/java/com/twitter/common/thrift'),
    pants('src/java/com/twitter/common/thrift:testing'),
    pants('src/java/com/twitter/common/util'),
    pants('src/java/com/twitter/common/util:system-mocks'),
    pants('src/java/com/twitter/common/util:testing'),
    pants('src/java/com/twitter/common/zookeeper'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = globs('*.java', 'pool/*.java', 'monitoring/*.java', 'loadbalancing/*.java')
)
