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

java_library(name = 'thrift',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'thrift',
    repo = pants('build-support/commons/ivy:public'),
  ),
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:jsr305'),
    pants('3rdparty:thrift'),
    pants('src/java/com/twitter/common/base'),
    pants('src/java/com/twitter/common/net:util'),
    pants('src/java/com/twitter/common/net:pool'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/stats'),
    pants('src/java/com/twitter/common/util'),
    pants('src/java/com/twitter/common/util:system-mocks'),
    pants('src/thrift/com/twitter/thrift'),
  ],
  sources = globs('*.java', 'callers/*.java', 'monitoring/*.java')
)

java_library(name = 'testing',
  dependencies = [
    pants('3rdparty:guava'),
    pants('3rdparty:thrift'),
  ],
  sources = globs('testing/*.java')
)
