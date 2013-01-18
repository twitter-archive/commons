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

java_library(name = 'io',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'io',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:commons-io',
    '3rdparty:commons-lang',
    '3rdparty:guava',
    'src/java/com/twitter/common/base',
  ],
  sources = [
    'Codec.java',
    'CompatibilityCodec.java',
    'FileUtils.java'
  ]
)

java_library(name = 'streamer',
  dependencies = [
    '3rdparty:guava',
    'src/java/com/twitter/common/base',
  ],
  sources = [ 'Streamer.java' ]
)

java_library(name = 'thrift',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'io-thrift',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:guava',
    '3rdparty:thrift',
    'src/java/com/twitter/common/base',
    ':io',
  ],
  sources = [ 'ThriftCodec.java' ]
)

java_library(name = 'json',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'io-json',
    repo = 'build-support/commons/ivy:public',
  ),
  dependencies = [
    '3rdparty:gson',
    '3rdparty:guava',
    'src/java/com/twitter/common/base',
    ':io',
  ],
  sources = [ 'JsonCodec.java' ]
)
