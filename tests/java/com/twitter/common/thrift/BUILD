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
    pants('3rdparty:easymock'),
    pants('3rdparty:guava'),
    pants('3rdparty:guice'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:junit'),
    pants('3rdparty:servlet-api'),
    pants('3rdparty:thrift'),
    pants('3rdparty:tl4j'),
    pants('3rdparty:zookeeper'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/collections'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/stats'),
    pants('src/java/com/twitter/common/testing'),
    pants('src/java/com/twitter/common/thrift'),
    pants('src/java/com/twitter/common/thrift:testing'),
    pants('src/java/com/twitter/common/thrift/text'),
    pants('src/java/com/twitter/common/util'),
    pants('src/java/com/twitter/common/util:system-mocks'),
    pants('src/java/com/twitter/common/util:testing'),
    pants('src/java/com/twitter/common/zookeeper'),
    pants('src/java/com/twitter/common/zookeeper:testing'),
    pants('src/thrift/com/twitter/common/thrift/text'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = globs('*.java', 'callers/*.java', 'loadbalancing/*.java', 'monitoring/*.java', 'text/*.java')
)
