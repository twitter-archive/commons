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

TIMED_SOURCES = ['TimedInterceptor.java']

java_library(name = 'inject',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'inject',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/commons-lang',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/com/google/inject/extensions:guice-multibindings',
  ],
  sources = globs('*.java', exclude = [TIMED_SOURCES])
)

java_library(name = 'timed',
  provides = artifact(
    org = 'com.twitter.common',
    name = 'inject-timed',
    repo = public,
  ),
  dependencies = [
    '3rdparty/jvm/aopalliance',
    '3rdparty/jvm/commons-lang',
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    'src/java/com/twitter/common/stats',
    'src/java/com/twitter/common/stats:time-series',
    ':inject',
  ],
  sources = TIMED_SOURCES
)
