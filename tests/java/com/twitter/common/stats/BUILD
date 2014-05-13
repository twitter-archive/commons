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

java_tests(name = 'stats',
  dependencies = [
    pants('3rdparty/jvm/org/easymock'),
    pants('3rdparty/jvm/com/google/guava'),
    pants('3rdparty/jvm/junit'),
    pants('3rdparty/jvm/org/mockito'),
    pants('src/java/com/twitter/common/junit/annotations'),
    pants('src/java/com/twitter/common/quantity'),
    pants('src/java/com/twitter/common/objectsize'),
    pants('src/java/com/twitter/common/stats'),
    pants('src/java/com/twitter/common/stats:time-series'),
    pants('src/java/com/twitter/common/stats:util'),
    pants('src/java/com/twitter/common/stats/testing:histogram'),
    pants('src/java/com/twitter/common/testing/easymock'),
    pants('src/java/com/twitter/common/testing/mockito'),
    pants('src/java/com/twitter/common/util:system-mocks'),
    pants('src/java/com/twitter/common/util:testing'),
  ],
  sources = globs('*.java')
)
