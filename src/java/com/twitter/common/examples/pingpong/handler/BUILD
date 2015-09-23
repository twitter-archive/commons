# ==================================================================================================
# Copyright 2013 Twitter, Inc.
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

# java_library: most source and dependencies

java_library(name = 'handler',
  dependencies = [
    '3rdparty/jvm/com/google/guava',
    '3rdparty/jvm/com/google/inject:guice',
    '3rdparty/jvm/com/sun/jersey:jersey-client',
    '3rdparty/jvm/com/sun/jersey:jersey-core',
    '3rdparty/jvm/com/sun/jersey:jersey-server',
    '3rdparty/jvm/com/sun/jersey:jersey-servlet',
    '3rdparty/jvm/com/sun/jersey/contribs:jersey-guice',
    'src/java/com/twitter/common/application',
    'src/java/com/twitter/common/application/http',
    'src/java/com/twitter/common/application/modules:http',
    'src/java/com/twitter/common/application/modules:lifecycle',
    'src/java/com/twitter/common/application/modules:log',
    'src/java/com/twitter/common/application/modules:stats',
    'src/java/com/twitter/common/args',
    'src/java/com/twitter/common/base',
    'src/java/com/twitter/common/stats',
  ],
  sources = globs('*.java')
)
