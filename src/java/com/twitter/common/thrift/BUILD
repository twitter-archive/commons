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



java_library(name = 'thrift',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'thrift',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/code/findbugs:jsr305',
    '3rdparty/jvm/org/apache/thrift',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/net:pool',
    'src/java/com/twitter/common/net:util',
    'src/java/com/twitter/common/quantity',
    'src/java/com/twitter/common/stats',
    'src/java/com/twitter/common/util',
    'src/java/com/twitter/common/util:system-mocks',
    'src/thrift/com/twitter/thrift',
  ],
  sources = globs('*.java', 'callers/*.java', 'monitoring/*.java')
)

java_library(name = 'testing',
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/org/apache/thrift',
  ],
  sources = globs('testing/*.java')
)
